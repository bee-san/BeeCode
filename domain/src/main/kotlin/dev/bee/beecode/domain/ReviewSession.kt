package dev.bee.beecode.domain

import kotlinx.datetime.Instant

/**
 * One scheduled attempt at a Problem, from opening it to finalizing it.
 *
 * The session is an explicit state machine rather than a bag of flags because
 * two things must be impossible: finalizing twice, and finalizing with a rating
 * the evidence does not support. Both are enforced here, in pure code, so they
 * are testable without a database — and then enforced *again* by the persistence
 * layer's compare-and-swap, because a state machine in memory cannot survive a
 * process kill.
 *
 * The session is immutable. Each transition returns a new instance, so an
 * in-flight UI never mutates state a background finalize is reading.
 */
data class ReviewSession(
    val id: ReviewSessionId,
    val problemId: ProblemId,
    val problemRevisionId: ProblemRevisionId,
    val state: ReviewSessionState,
    val startedAt: Instant,
    /**
     * Every run performed in this session, oldest first.
     *
     * Kept in full rather than reduced to a best result, because finalization
     * binds to one *chosen* run and the learner may deliberately finalize an
     * earlier passing attempt.
     */
    val runs: List<ExecutionRun>,
    /**
     * True once the learner has revealed the packaged explanation or a previous
     * solution. Latched: it can never go back to false, because knowledge cannot
     * be un-seen. This is what caps the permissible rating.
     */
    val aided: Boolean,
    val finalizedAt: Instant?,
) {
    init {
        require(runs.all { it.problemId == problemId }) {
            "Session $id contains a run for a different Problem"
        }
        require(runs.all { it.problemRevisionId == problemRevisionId }) {
            "Session $id contains a run against a different Problem revision"
        }
        when (state) {
            ReviewSessionState.FINALIZED -> require(finalizedAt != null) {
                "A FINALIZED session must record when it was finalized"
            }
            else -> require(finalizedAt == null) {
                "Only a FINALIZED session may record a finalization time"
            }
        }
    }

    /** The most recent run, or null if the learner has not run anything yet. */
    val latestRun: ExecutionRun? get() = runs.lastOrNull()

    /** True when any run in this session passed the official suite. */
    val hasPassingRun: Boolean get() = runs.any { it.isPass }

    /**
     * The runs a learner is allowed to finalize with.
     *
     * Cancelled runs and worker failures are excluded: neither is evidence about
     * the learner's recall. A timeout, a syntax error, and a genuine wrong answer
     * *are* evidence, so they remain selectable and rate as [ReviewRating.AGAIN].
     */
    val selectableRuns: List<ExecutionRun>
        get() = runs.filter { it.outcome.isEvidence() }

    fun recordRun(run: ExecutionRun): ReviewSession {
        check(state != ReviewSessionState.FINALIZED) {
            "Cannot record a run against finalized session $id"
        }
        require(run.problemId == problemId && run.problemRevisionId == problemRevisionId) {
            "Run ${run.id} does not belong to session $id"
        }
        require(runs.none { it.id == run.id }) {
            "Run ${run.id} was already recorded in session $id"
        }
        val nextState = when {
            run.isPass -> ReviewSessionState.PASSED
            // A run that is not evidence about recall must not knock the session
            // out of a PASSED state it has already legitimately reached.
            !run.outcome.isEvidence() && state == ReviewSessionState.PASSED -> ReviewSessionState.PASSED
            else -> ReviewSessionState.WORKING
        }
        return copy(state = nextState, runs = runs + run)
    }

    /**
     * Latch this session as aided.
     *
     * Permitted even after a pass: a learner may pass and then read the
     * explanation out of curiosity, and honesty requires that this still cap the
     * rating. Refusing the reveal, or silently not recording it, would both be
     * worse.
     */
    fun reveal(): ReviewSession {
        check(state != ReviewSessionState.FINALIZED) {
            "Cannot reveal on finalized session $id"
        }
        return if (aided) this else copy(aided = true)
    }

    /**
     * Decide what may be recorded for a chosen run, without committing anything.
     *
     * Returns a [FinalizationPlan] so the caller can show the learner their
     * options before writing. Rejects rather than silently downgrading, because a
     * learner who picked "Easy" deserves to be told why they cannot have it.
     */
    fun planFinalization(selectedRunId: ExecutionRunId, rating: ReviewRating): FinalizationPlan {
        check(state != ReviewSessionState.FINALIZED) {
            "Session $id is already finalized"
        }
        val selected = runs.firstOrNull { it.id == selectedRunId }
            ?: throw IllegalArgumentException("Run $selectedRunId is not part of session $id")
        require(selected.outcome.isEvidence()) {
            "Run $selectedRunId ended as ${selected.outcome} and cannot be finalized"
        }

        val permitted = ReviewRatingPolicy.permittedRatings(selected, aided)
        require(rating in permitted) {
            "Rating $rating is not permitted for a ${selected.outcome} run " +
                "(aided=$aided); permitted: ${permitted.joinToString()}"
        }

        return FinalizationPlan(
            sessionId = id,
            problemId = problemId,
            problemRevisionId = problemRevisionId,
            selectedRun = selected,
            rating = rating,
            aided = aided,
            // A solve is credited only for an unaided pass. This is the single
            // definition of "solved" used by statistics and achievements alike,
            // so the 5am Club cannot be farmed by revealing the answer.
            countsAsSolved = selected.isPass && !aided,
        )
    }

    fun finalize(plan: FinalizationPlan, at: Instant): ReviewSession {
        check(state != ReviewSessionState.FINALIZED) {
            "Session $id is already finalized"
        }
        require(plan.sessionId == id) { "Plan belongs to session ${plan.sessionId}, not $id" }
        return copy(state = ReviewSessionState.FINALIZED, finalizedAt = at)
    }

    companion object {
        fun start(
            id: ReviewSessionId,
            problem: ProblemDefinition,
            startedAt: Instant,
        ): ReviewSession = ReviewSession(
            id = id,
            problemId = problem.id,
            problemRevisionId = problem.revisionId,
            state = ReviewSessionState.STARTED,
            startedAt = startedAt,
            runs = emptyList(),
            aided = false,
            finalizedAt = null,
        )
    }
}

enum class ReviewSessionState {
    /** Opened; nothing run yet. */
    STARTED,

    /** At least one run happened and none has passed. */
    WORKING,

    /** A run passed the official suite. The learner may keep going or finalize. */
    PASSED,

    /** Terminal. An outcome was recorded and the schedule advanced. */
    FINALIZED,
}

/**
 * True when an outcome says something about the learner's recall.
 *
 * Cancellation is the learner's choice, and a worker failure is BeeCode's bug.
 * Neither may become a review, because both would corrupt the schedule with
 * something the learner did not actually get wrong.
 */
fun ExecutionOutcome.isEvidence(): Boolean = when (this) {
    ExecutionOutcome.PASSED,
    ExecutionOutcome.FAILED,
    ExecutionOutcome.SYNTAX_ERROR,
    ExecutionOutcome.RUNTIME_ERROR,
    ExecutionOutcome.TIMEOUT,
    -> true

    ExecutionOutcome.CANCELLED,
    ExecutionOutcome.WORKER_FAILURE,
    -> false
}

/**
 * What BeeCode intends to record. Produced by [ReviewSession.planFinalization]
 * and consumed by the persistence layer's single write transaction.
 */
data class FinalizationPlan(
    val sessionId: ReviewSessionId,
    val problemId: ProblemId,
    val problemRevisionId: ProblemRevisionId,
    val selectedRun: ExecutionRun,
    val rating: ReviewRating,
    val aided: Boolean,
    val countsAsSolved: Boolean,
)

/**
 * The rating recorded for a review.
 *
 * Deliberately mirrors FSRS's four grades, but is a BeeCode type: the domain
 * decides which grades the evidence permits, and only the FSRS adapter maps this
 * onto the engine's rating. Keeping them separate is what lets BeeCode change
 * its evidence policy without touching vendored memory mathematics.
 */
enum class ReviewRating {
    AGAIN,
    HARD,
    GOOD,
    EASY,
}

/**
 * Which ratings the evidence supports.
 *
 * This is the honesty rule of the product. A learner cannot mark a Problem
 * "Easy" if their code never passed, and cannot claim an unaided grade after
 * reading the answer. It is pure and total so the full matrix is covered by
 * tests rather than by hope.
 */
object ReviewRatingPolicy {
    fun permittedRatings(run: ExecutionRun, aided: Boolean): Set<ReviewRating> = when {
        !run.outcome.isEvidence() -> emptySet()

        // Anything short of a pass is a lapse. Offering HARD here would let a
        // learner soften a genuine failure and stop the schedule from reacting.
        !run.isPass -> setOf(ReviewRating.AGAIN)

        // A pass after revealing the answer is recognition, not recall. HARD is
        // the honest ceiling: it advances the schedule, but far less than GOOD.
        aided -> setOf(ReviewRating.AGAIN, ReviewRating.HARD)

        // An unaided pass is the learner's own judgement of effort.
        else -> setOf(ReviewRating.AGAIN, ReviewRating.HARD, ReviewRating.GOOD, ReviewRating.EASY)
    }

    /** The rating BeeCode preselects, so the common path is one tap. */
    fun defaultRating(run: ExecutionRun, aided: Boolean): ReviewRating? {
        val permitted = permittedRatings(run, aided)
        return when {
            permitted.isEmpty() -> null
            ReviewRating.GOOD in permitted -> ReviewRating.GOOD
            ReviewRating.HARD in permitted -> ReviewRating.HARD
            else -> ReviewRating.AGAIN
        }
    }
}
