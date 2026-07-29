package dev.bee.beecode.app

import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.IdGenerator
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemSchedule
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewRatingPolicy
import dev.bee.beecode.domain.ReviewSession
import dev.bee.beecode.domain.SolutionDraft
import dev.bee.beecode.persistence.DraftRepository
import dev.bee.beecode.persistence.FinalizeOutcome
import dev.bee.beecode.persistence.ReviewRepository
import dev.bee.beecode.persistence.SettingsRepository
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

/**
 * The study loop: open a Problem, edit, run, finalize.
 *
 * This is the seam both clients drive. It is UI-free on purpose — the entire
 * answer/run/retry/finalize journey the plan gates on is therefore testable
 * without a UI toolkit, and desktop and Android cannot diverge in review
 * semantics because there is only one implementation of them.
 *
 * Sessions are held in memory and keyed by Problem, because a session is the
 * *current* attempt. Everything durable — the draft, the finalized review, the
 * schedule — is written through the repositories immediately, so a process death
 * loses at most the in-flight session, never typed source or a recorded review.
 */
class StudyService(
    private val catalogue: ProblemCatalogue,
    private val drafts: DraftRepository,
    private val reviews: ReviewRepository,
    private val settings: SettingsRepository,
    private val runner: PythonRunner,
    private val ids: IdGenerator,
    private val clock: Clock = Clock.System,
) {
    private val sessions = mutableMapOf<ProblemId, ReviewSession>()

    private val deviceId: DeviceId by lazy {
        settings.deviceId(generate = { ids.newDeviceId() }, now = clock.now())
    }

    /**
     * The queue of what to study next.
     *
     * Due Problems come first, soonest first, then Problems never attempted. They
     * are separate lists rather than one merged queue so a large backlog does not
     * block starting something new, and a learner who wants only reviews is not
     * forced into new material.
     */
    fun queue(limit: Int = DEFAULT_QUEUE_LIMIT): StudyQueue {
        val now = clock.now()
        val dailyLimit = settings.dailyReviewLimit()
        val effectiveLimit = dailyLimit?.coerceAtMost(limit) ?: limit

        val dueSchedules = reviews.dueSchedules(now, effectiveLimit)
        val due = dueSchedules.mapNotNull { schedule ->
            catalogue.problem(schedule.problemId)?.let { DueProblem(it, schedule) }
        }

        val attempted = reviews.scheduledProblemIds()
        val fresh = catalogue.allProblems()
            .filter { it.id !in attempted }
            .sortedWith(compareBy({ it.difficulty.ordinal }, { it.id.value }))

        return StudyQueue(due = due, new = fresh, dailyLimit = dailyLimit)
    }

    /** Everything the UI needs to show a Problem, including the learner's draft. */
    fun open(problemId: ProblemId): OpenProblem? {
        val problem = catalogue.problem(problemId) ?: return null
        val now = clock.now()
        val draft = drafts.loadOrStart(problem, now)

        // Reuse an in-flight session for this Problem: navigating away and back
        // must not discard the runs already performed, or a learner loses their
        // passing attempt by tapping the wrong thing.
        val session = sessions.getOrPut(problemId) {
            ReviewSession.start(ids.newReviewSessionId(), problem, now)
        }

        return OpenProblem(
            problem = problem,
            draft = draft,
            session = session,
            schedule = reviews.schedule(problemId),
            history = reviews.reviewHistory(problemId),
        )
    }

    /**
     * Persist the learner's source.
     *
     * Returns the stored draft, or the existing one when this save was stale. A
     * stale save is dropped rather than applied, so an autosave from an earlier
     * keystroke cannot resurrect old text over a newer edit.
     */
    fun saveDraft(draft: SolutionDraft): SolutionDraft =
        drafts.save(draft, clock.now()) ?: drafts.draft(draft.problemId) ?: draft

    /**
     * Run the learner's current source and record the attempt in the session.
     *
     * The source is snapshotted into the request, so the result is bound to
     * exactly what was executed even though the learner keeps typing. Saving the
     * draft first means a crash during a run still preserves the code.
     */
    suspend fun run(problemId: ProblemId, source: String): RunOutcome {
        val problem = catalogue.problem(problemId)
            ?: return RunOutcome.UnknownProblem(problemId)
        val session = sessions[problemId]
            ?: return RunOutcome.NoSession(problemId)
        if (session.state == dev.bee.beecode.domain.ReviewSessionState.FINALIZED) {
            return RunOutcome.AlreadyFinalized(problemId)
        }

        // Persist before running. A worker crash or a process kill during
        // execution must not cost the learner their code.
        val existing = drafts.loadOrStart(problem, clock.now())
        saveDraft(existing.copy(source = source))

        val runId = ids.newExecutionRunId()
        val startedAt = clock.now()
        val result = runner.execute(RunRequest.from(runId, problem, source))

        val run = result.toExecutionRun(problem, source, startedAt)
        val updated = session.recordRun(run)
        sessions[problemId] = updated

        return RunOutcome.Completed(run, updated)
    }

    /**
     * Reveal the packaged explanation.
     *
     * Latches the session as aided, which caps the permissible rating at Hard and
     * means the attempt cannot count as solved. Returns null when the Problem has
     * no explanation, rather than silently marking the session aided for nothing.
     */
    fun reveal(problemId: ProblemId): RevealOutcome? {
        val problem = catalogue.problem(problemId) ?: return null
        val explanation = problem.explanationMarkdown ?: return null
        val session = sessions[problemId] ?: return null
        if (session.state == dev.bee.beecode.domain.ReviewSessionState.FINALIZED) return null

        val updated = session.reveal()
        sessions[problemId] = updated
        return RevealOutcome(explanation, updated)
    }

    /** Which ratings the current evidence permits, for enabling the rating buttons. */
    fun permittedRatings(problemId: ProblemId, runId: ExecutionRunId): Set<ReviewRating> {
        val session = sessions[problemId] ?: return emptySet()
        val run = session.runs.firstOrNull { it.id == runId } ?: return emptySet()
        return ReviewRatingPolicy.permittedRatings(run, session.aided)
    }

    fun defaultRating(problemId: ProblemId, runId: ExecutionRunId): ReviewRating? {
        val session = sessions[problemId] ?: return null
        val run = session.runs.firstOrNull { it.id == runId } ?: return null
        return ReviewRatingPolicy.defaultRating(run, session.aided)
    }

    /**
     * Finalize the review and advance the schedule.
     *
     * The domain decides whether the rating is permissible; persistence guarantees
     * the effect happens exactly once. A retry returns the recorded outcome rather
     * than reviewing the Problem again.
     */
    fun finalize(
        problemId: ProblemId,
        runId: ExecutionRunId,
        rating: ReviewRating,
    ): FinalizeResult {
        val session = sessions[problemId]
            ?: return FinalizeResult.NoSession(problemId)

        val plan = try {
            session.planFinalization(runId, rating)
        } catch (e: IllegalArgumentException) {
            // The learner picked a rating the evidence does not support. Tell them
            // why rather than silently downgrading it.
            return FinalizeResult.Rejected(e.message ?: "That rating is not permitted")
        } catch (e: IllegalStateException) {
            return FinalizeResult.Rejected(e.message ?: "This review is already finalized")
        }

        val now = clock.now()
        val outcome = reviews.finalizeReview(
            plan = plan,
            eventId = ids.newDomainEventId(),
            deviceId = deviceId,
            finalizedAt = now,
            streakZone = settings.streakZone(),
        )

        // The session is done either way, including when it was already finalized:
        // the durable record is authoritative, and keeping a live session would
        // let the learner try to finalize it twice.
        sessions[problemId] = session.finalize(plan, now)

        return when (outcome) {
            is FinalizeOutcome.Finalized -> FinalizeResult.Finalized(
                review = outcome.review,
                schedule = outcome.schedule,
                wasAlreadyFinalized = false,
            )
            is FinalizeOutcome.AlreadyFinalized -> FinalizeResult.Finalized(
                review = outcome.review,
                schedule = reviews.schedule(problemId),
                wasAlreadyFinalized = true,
            )
        }
    }

    /**
     * Abandon the in-flight session without recording anything.
     *
     * The draft is untouched: closing a Problem without finalizing must not
     * discard the learner's code, only the attempt.
     */
    fun abandon(problemId: ProblemId) {
        sessions.remove(problemId)
    }

    /** Reset the learner's source back to the Problem's current starter. */
    fun resetToStarter(problemId: ProblemId): SolutionDraft? {
        val problem = catalogue.problem(problemId) ?: return null
        val now = clock.now()
        val current = drafts.loadOrStart(problem, now)
        return saveDraft(
            current.copy(source = problem.starterSource, starterBaseline = problem.starterSource),
        )
    }

    /** Whether Python is usable, and at what honest containment level. */
    suspend fun runnerStatus(): RunnerStatus {
        val probe = runner.probe()
        return RunnerStatus(
            available = probe.available,
            pythonVersion = probe.pythonVersion,
            capability = probe.capability,
            unavailableReason = probe.unavailableReason,
            runnerId = runner.runnerId,
        )
    }

    private fun RunResult.toExecutionRun(
        problem: ProblemDefinition,
        source: String,
        startedAt: Instant,
    ): ExecutionRun {
        // ExecutionRun's invariants require test results consistent with the
        // outcome. A misbehaving runner that claims PASSED with a failing test
        // would otherwise throw here, so it is normalised to a worker failure —
        // which the domain refuses to record as the learner's mistake.
        val consistent = when (outcome) {
            ExecutionOutcome.PASSED -> testResults.isNotEmpty() && testResults.all { it.passed }
            ExecutionOutcome.FAILED -> testResults.any { !it.passed }
            else -> true
        }
        val effectiveOutcome = if (consistent) outcome else ExecutionOutcome.WORKER_FAILURE
        return ExecutionRun(
            id = runId,
            problemId = problem.id,
            problemRevisionId = problem.revisionId,
            source = source,
            outcome = effectiveOutcome,
            testResults = if (consistent) testResults else emptyList(),
            output = output,
            outputTruncated = outputTruncated,
            durationMillis = durationMillis,
            startedAt = startedAt,
            runnerId = runnerId,
            pythonVersion = pythonVersion,
        )
    }

    companion object {
        const val DEFAULT_QUEUE_LIMIT: Int = 50
    }
}

/** What to study next. */
data class StudyQueue(
    val due: List<DueProblem>,
    val new: List<ProblemDefinition>,
    val dailyLimit: Int?,
) {
    val isEmpty: Boolean get() = due.isEmpty() && new.isEmpty()

    val totalAvailable: Int get() = due.size + new.size
}

data class DueProblem(
    val problem: ProblemDefinition,
    val schedule: ProblemSchedule,
)

/** Everything needed to render a Problem. */
data class OpenProblem(
    val problem: ProblemDefinition,
    val draft: SolutionDraft,
    val session: ReviewSession,
    val schedule: ProblemSchedule?,
    val history: List<dev.bee.beecode.domain.ProblemReviewFinalized>,
) {
    val isFirstAttempt: Boolean get() = schedule == null
}

sealed interface RunOutcome {
    data class Completed(val run: ExecutionRun, val session: ReviewSession) : RunOutcome

    data class UnknownProblem(val problemId: ProblemId) : RunOutcome

    data class NoSession(val problemId: ProblemId) : RunOutcome

    data class AlreadyFinalized(val problemId: ProblemId) : RunOutcome
}

data class RevealOutcome(
    val explanationMarkdown: String,
    val session: ReviewSession,
)

sealed interface FinalizeResult {
    data class Finalized(
        val review: dev.bee.beecode.domain.ProblemReviewFinalized,
        val schedule: ProblemSchedule?,
        /** True when a previous call had already recorded this review. */
        val wasAlreadyFinalized: Boolean,
    ) : FinalizeResult

    data class Rejected(val reason: String) : FinalizeResult

    data class NoSession(val problemId: ProblemId) : FinalizeResult
}

data class RunnerStatus(
    val available: Boolean,
    val pythonVersion: String?,
    val capability: dev.bee.beecode.python.RunnerCapability,
    val unavailableReason: String?,
    val runnerId: String,
)
