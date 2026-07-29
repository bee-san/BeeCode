package dev.bee.beecode.app

import dev.bee.beecode.domain.ProblemReviewFinalized
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * Turns finalized reviews into Leaderboard activity events.
 *
 * The bridge between the local truth — an append-only review log — and
 * [ActivityOutbox]. Kept separate from both because it answers a different question:
 * *which* reviews the Leaderboard is entitled to know about, and that is a policy
 * question with two rules the plan states outright.
 *
 * ### Rule one: only unaided passes count
 *
 * `countsAsSolved` is already the domain's answer to "was this recall rather than
 * recognition" — a failure permits only *Again*, and a pass after revealing the
 * explanation is capped at *Hard*. Reusing it here rather than re-deriving means the
 * Leaderboard and the 5am Club cannot disagree about what solving is, which is what stops
 * a board being farmed by reading answers.
 *
 * ### Rule two: no pre-link backfill
 *
 * "Reviews completed before account linking are not silently backfilled." A learner who
 * has studied for months and then joins a board does not arrive with months of activity —
 * that would be indistinguishable from cheating to everyone else on it, and nobody
 * consented to uploading a history that predates the decision to share.
 *
 * [linkedAt] is therefore a hard cutoff, and it is a *parameter* rather than a filter the
 * caller might forget: producing events at all requires stating when linking happened.
 *
 * ### Why this is a projection and not a hook
 *
 * Events are derived from the log rather than emitted at finalization. That makes the
 * whole path replayable: an outbox lost to a reinstall rebuilds from history, and the
 * result is identical because the derivation is pure and the log is append-only. A hook
 * that fired once and dropped its event would lose it forever.
 */
object ActivityProjection {

    /**
     * Every event the Leaderboard is entitled to, from [reviews].
     *
     * @param linkedAt when this profile was linked to an account. Reviews finalized
     *   strictly before this are excluded and never uploaded.
     * @param zone the profile timezone, used to derive the local date a streak is
     *   measured in. Passed in rather than read from the system because a learner who
     *   travels must not have their streak recomputed in a new zone.
     */
    fun eventsFor(
        reviews: List<ProblemReviewFinalized>,
        linkedAt: Instant,
        zone: TimeZone,
    ): List<ActivityEvent> = reviews.asSequence()
        // Not `>=` by accident: a review finalized in the same instant as linking is
        // included, because it happened after the learner chose to share.
        .filter { it.finalizedAt >= linkedAt }
        // Recognition is not recall, and a failed attempt is not a solve.
        .filter { it.countsAsSolved }
        .map { review ->
            ActivityEvent(
                // The review's own session id, so the idempotency key is stable across
                // replays. Minting a fresh one here would make every rebuild look like new
                // activity and double every count.
                eventId = review.sessionId.value,
                problemId = review.problemId.value,
                occurredAt = review.finalizedAt,
                localDate = review.finalizedAt.dateIn(zone).toString(),
                countsAsSolved = true,
            )
        }
        .sortedBy { it.occurredAt }
        .toList()

    /**
     * Enqueue everything not already queued, and report what was added.
     *
     * Safe to run repeatedly — on launch, after a sync, after a restore — because
     * [ActivityOutbox.enqueue] is idempotent on the event id and the projection derives
     * that id from the review session. Running it twice adds nothing the second time.
     *
     * This is also what makes a *restored* profile behave correctly: a backup brings its
     * review log, the projection derives the same events, and rows already acknowledged
     * are not resurrected because they are still in the outbox by the same key.
     */
    fun enqueueNew(
        rows: List<OutboxRow>,
        reviews: List<ProblemReviewFinalized>,
        linkedAt: Instant,
        zone: TimeZone,
        now: Instant,
    ): ProjectionResult {
        val events = eventsFor(reviews, linkedAt, zone)
        var current = rows
        var added = 0
        for (event in events) {
            val before = current.size
            current = ActivityOutbox.enqueue(current, event, now)
            if (current.size != before) added++
        }
        return ProjectionResult(rows = current, eventsAdded = added, eventsConsidered = events.size)
    }
}

/** What one projection pass did, so the caller can report rather than guess. */
data class ProjectionResult(
    val rows: List<OutboxRow>,
    val eventsAdded: Int,
    val eventsConsidered: Int,
)
