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
import dev.bee.beecode.domain.TopicSchedule
import dev.bee.beecode.persistence.DraftRepository
import dev.bee.beecode.persistence.FinalizeOutcome
import dev.bee.beecode.persistence.ReviewRepository
import dev.bee.beecode.persistence.SettingsRepository
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunDiagnostic
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
     * Reviews are keyed by *topic*, not by Problem. A learner does not forget
     * two-sum; they forget dynamic programming, so what falls due is the technique
     * and the Problem is the exercise that rehearses it. Due order is FSRS's own
     * order, which is the whole reason the card sits on the topic: a topic the
     * learner keeps forgetting has low stability, so it gets a short interval and
     * arrives more often without any weakness score in front of it.
     *
     * New Problems stay a separate list, as they were: a large backlog must not
     * block starting something new, and a learner who wants only reviews is not
     * forced into fresh material.
     *
     * Two indexed reads plus two whole-table reads of tables that hold one row per
     * reviewed Problem or topic. This is on the study path, so it must stay cheap —
     * it is deliberately not as expensive as [BeeCodeProfile.statistics].
     */
    fun queue(limit: Int = DEFAULT_QUEUE_LIMIT): StudyQueue {
        val now = clock.now()
        val dailyLimit = settings.dailyReviewLimit()
        val effectiveLimit = dailyLimit?.coerceAtMost(limit) ?: limit

        val schedules = reviews.schedules()
        // Inverted from Problems to topics rather than read from a topic index,
        // because tags live on the Problem and the pack is the authority on current
        // membership — a topic row whose members have all been retagged away must
        // find no candidates rather than a stale one.
        val membersByTopic = buildMap<String, MutableList<ProblemDefinition>> {
            for (problem in catalogue.allProblems()) {
                for (topic in problem.topics.distinct()) {
                    getOrPut(topic) { mutableListOf() } += problem
                }
            }
        }

        val dueTopics = reviews.dueTopicSchedules(now, effectiveLimit).mapNotNull { schedule ->
            val members = membersByTopic[schedule.topic].orEmpty()
            val attemptedMembers = members.filter { it.id in schedules }
            // A topic with nothing attempted is not schedulable: there is no Problem
            // the learner has solved before, so there is nothing to *review*. Its
            // members are offered as new work instead.
            val problem = chooseMember(attemptedMembers, schedules) ?: return@mapNotNull null
            DueTopic(
                topic = schedule.topic,
                displayName = TopicMastery.displayName(schedule.topic),
                schedule = schedule,
                problem = problem,
                memberProblems = members.size,
                attemptedMemberProblems = attemptedMembers.size,
            )
        }

        val fresh = catalogue.allProblems()
            .filter { it.id !in schedules }
            .sortedWith(compareBy({ it.difficulty.ordinal }, { it.id.value }))

        return StudyQueue(dueTopics = dueTopics, new = fresh, dailyLimit = dailyLimit)
    }

    /**
     * Which member Problem to practise for a due topic.
     *
     * This is what delivers "a DP problem, not specifically one problem", and it is
     * where the retained per-Problem schedules earn their keep. Least recently
     * practised first, so the choice rotates on its own: practising a member updates
     * its `lastReviewedAt`, which puts it last next time.
     *
     * Then most-often-forgotten, then least stable, then by id. The tail of that
     * comparator is about determinism as much as pedagogy — two members with
     * identical history must not depend on map iteration order, or the same learner
     * would see a different Problem on each refresh with nothing having changed.
     */
    private fun chooseMember(
        attempted: List<ProblemDefinition>,
        schedules: Map<ProblemId, ProblemSchedule>,
    ): ProblemDefinition? = attempted.minWithOrNull(
        compareBy(
            { schedules.getValue(it.id).lastReviewedAt },
            { -schedules.getValue(it.id).lapseCount },
            { schedules.getValue(it.id).stability },
            { it.id.value },
        ),
    )

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
     * Persist [source] as the learner's draft for [problemId].
     *
     * The single call both clients use when leaving a Problem, and the reason it
     * exists: they each did `drafts.draft(id)?.let { save(it.copy(source)) }`, and
     * [DraftRepository.draft] returns null until something has been saved. [open]
     * only ever *constructs* a draft, so on a first visit that `?.let` short-circuited
     * and every character the learner had typed was discarded on Back — silently, with
     * no error, for anyone who had not pressed Run first. [DraftRepository]'s own
     * header calls losing typed source data loss rather than an inconvenience.
     *
     * Going through [DraftRepository.loadOrStart] means the baseline is created on
     * demand, so there is no first-visit special case to remember at a call site.
     */
    fun saveSource(problemId: ProblemId, source: String): SolutionDraft? {
        val problem = catalogue.problem(problemId) ?: return null
        return saveDraft(drafts.loadOrStart(problem, clock.now()).copy(source = source))
    }

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

        // The UI can abandon and replace a session while a runner is still winding down.
        // Never let that late completion overwrite the replacement session.
        if (sessions[problemId]?.id != session.id) {
            return RunOutcome.NoSession(problemId)
        }

        val run = result.toExecutionRun(problem, source, startedAt)
        val updated = session.recordRun(run)
        sessions[problemId] = updated

        return RunOutcome.Completed(run, updated, result.diagnostic)
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
            finalizedAtInstant = now,
            streakZone = settings.streakZone(),
            // The Problem's *current* tags. Read here rather than from the session,
            // because a session opened before a pack update would rehearse the topics
            // the Problem used to have.
            topics = catalogue.problem(problemId)?.topics ?: emptyList(),
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
    /** Techniques due for review, in FSRS due order. */
    val dueTopics: List<DueTopic>,
    val new: List<ProblemDefinition>,
    val dailyLimit: Int?,
) {
    val isEmpty: Boolean get() = dueTopics.isEmpty() && new.isEmpty()

    val totalAvailable: Int get() = dueTopics.size + new.size
}

/**
 * A technique that is due, and the Problem to rehearse it with.
 *
 * [problem] rotates between the topic's members across reviews rather than being
 * fixed, which is the difference between "review DP" and "review this one DP
 * Problem again". [memberProblems] and [attemptedMemberProblems] are carried so a
 * client can say "3 of 10 practised" without a second query — and so a thin topic
 * reads as thin instead of quietly repeating its single member.
 */
data class DueTopic(
    val topic: String,
    /** Humanised from the slug, e.g. `dynamic-programming` → "Dynamic programming". */
    val displayName: String,
    val schedule: TopicSchedule,
    val problem: ProblemDefinition,
    val memberProblems: Int,
    val attemptedMemberProblems: Int,
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
    data class Completed(
        val run: ExecutionRun,
        val session: ReviewSession,
        val diagnostic: RunDiagnostic?,
    ) : RunOutcome

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
