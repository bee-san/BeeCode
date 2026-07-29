package dev.bee.beecode.app

import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone

/**
 * The one entry point a client needs for Leaderboard activity.
 *
 * Everything underneath is already built and tested in isolation — [ActivityProjection]
 * decides which reviews may be shared, [ActivityOutbox] owns every delivery transition,
 * [OutboxStorage] persists them. This composes those three and nothing more, so a UI does
 * not have to know the order to call them in or remember to save afterwards.
 *
 * ### Why the queue is loaded and saved around every operation
 *
 * Rather than held in memory. The state machine is a pure function over a list, so a
 * "current" list cached here would be a second source of truth that a crash could
 * desynchronize from the database. Loading is a small indexed read and the queue is bounded
 * — a learner solves a few Problems a day, and acknowledged rows prune after a week — so
 * the simplicity is worth more than the saved I/O.
 *
 * ### What this is not
 *
 * There is no upload here. Delivery needs a server and an auth token, and neither exists
 * yet; [uploadWith] takes the transport as a parameter so the loop is testable and the
 * server is a later, separable piece of work.
 */
class LeaderboardService(
    private val profile: BeeCodeProfile,
) {

    /**
     * Bring the queue up to date with the review log.
     *
     * Safe to call on every launch, after a sync, and after a restore — the projection
     * derives its event ids from review sessions, so re-running adds nothing already
     * present. That is what makes a queue lost to a reinstall recoverable rather than a
     * permanent hole in someone's Leaderboard count.
     *
     * @param linkedAt when this profile was linked to an account. Reviews finalized before
     *   it are never uploaded, and passing it explicitly is what stops that rule being
     *   forgotten at a call site.
     */
    fun refresh(linkedAt: Instant, now: Instant, zone: TimeZone = profile.settings.streakZone()): ProjectionResult {
        val existing = OutboxStorage.load(profile.activityOutbox)
        // Recover anything stranded in flight by a process death first: a row left in flight
        // has no verdict coming, and leaving it there would strand it forever while dropping
        // it would lose a count.
        val recovered = ActivityOutbox.recoverAfterRestart(existing, now)
        val projected = ActivityProjection.enqueueNew(
            rows = recovered,
            reviews = profile.allReviews(),
            linkedAt = linkedAt,
            zone = zone,
            now = now,
        )
        val pruned = ActivityOutbox.prune(projected.rows, now)
        OutboxStorage.save(profile.activityOutbox, pruned)
        return projected.copy(rows = pruned)
    }

    /**
     * Upload one batch through [upload], applying each verdict and persisting the result.
     *
     * The transport is a parameter rather than a constructor dependency because there is no
     * server yet, and because a caller that wants to sync opportunistically — say, right
     * after a WebDAV sync — should not have to construct one to find out the queue is empty.
     *
     * Persists **after** the batch rather than per verdict. A process death mid-batch leaves
     * the rows in flight, which [refresh] recovers as pending, and the server de-duplicates
     * — so the worst case is a redundant upload that returns `Duplicate`, which counts as
     * success. Saving per verdict would be more writes for a weaker guarantee, because the
     * rows would still be individually mid-transition.
     */
    suspend fun uploadWith(
        now: Instant,
        upload: suspend (List<ActivityEvent>) -> Map<String, UploadVerdict>,
    ): UploadReport {
        val rows = OutboxStorage.load(profile.activityOutbox)
        val batch = ActivityOutbox.nextBatch(rows, now)
        if (batch.isEmpty()) {
            return UploadReport(attempted = 0, accepted = 0, deferred = 0, refused = 0)
        }

        val inFlight = ActivityOutbox.markInFlight(rows, batch, now)
        OutboxStorage.save(profile.activityOutbox, inFlight)

        val verdicts = try {
            upload(batch.map { it.event })
        } catch (e: Exception) {
            // A transport that throws instead of returning is a bug in the transport, but it
            // must not become a lost queue. Treat the whole batch as retryable, which is what
            // an outage would have looked like anyway.
            batch.associate { it.event.eventId to UploadVerdict.Retryable(e.message ?: "upload failed") }
        }

        var updated = inFlight
        for (row in batch) {
            val verdict = verdicts[row.event.eventId]
                // A server that answered for only some of the batch has effectively not
                // answered for the rest. Retryable rather than accepted: assuming success
                // from silence is how a count goes missing.
                ?: UploadVerdict.Retryable("the server did not answer for this event")
            updated = ActivityOutbox.applyVerdict(updated, row.event.eventId, verdict, now)
        }
        OutboxStorage.save(profile.activityOutbox, updated)

        // Counted by classifying each event once, rather than by subtracting one count from
        // another: an arithmetic definition of "deferred" is the kind that quietly stops
        // adding up when a verdict type is added.
        var accepted = 0
        var refused = 0
        var deferred = 0
        for (row in batch) {
            when (verdicts[row.event.eventId]) {
                is UploadVerdict.Accepted, is UploadVerdict.Duplicate -> accepted++
                is UploadVerdict.Rejected -> refused++
                // Retryable, or absent because the server did not answer for this event.
                is UploadVerdict.Retryable, null -> deferred++
            }
        }
        return UploadReport(
            attempted = batch.size,
            accepted = accepted,
            deferred = deferred,
            refused = refused,
        )
    }

    /**
     * Counts for a status line.
     *
     * Read from SQL rather than by loading the queue, because a settings screen shows this
     * far more often than an upload runs and should not deserialize a week of acknowledged
     * rows to display a number.
     */
    fun status(): OutboxStatus {
        val counts = profile.activityOutbox.countsByState()
        return OutboxStatus(
            pending = counts[OutboxState.PENDING.name] ?: 0,
            inFlight = counts[OutboxState.IN_FLIGHT.name] ?: 0,
            acknowledged = counts[OutboxState.ACKNOWLEDGED.name] ?: 0,
            parked = counts[OutboxState.PARKED.name] ?: 0,
            rejected = counts[OutboxState.REJECTED.name] ?: 0,
        )
    }

    /** Return parked rows to pending, for the learner retrying after an outage. */
    fun retryParked(now: Instant): Int {
        val rows = OutboxStorage.load(profile.activityOutbox)
        val parked = rows.count { it.state == OutboxState.PARKED }
        if (parked > 0) OutboxStorage.save(profile.activityOutbox, ActivityOutbox.retryParked(rows, now))
        return parked
    }

    /**
     * Discard the queue entirely.
     *
     * For unlinking an account. Local reviews, schedules, and achievements are untouched —
     * the Leaderboard is a view of study, never its source, so leaving a board cannot cost a
     * learner their history.
     */
    fun forget() = profile.activityOutbox.clear()
}

/** What one upload attempt did, in terms a learner can be shown. */
data class UploadReport(
    val attempted: Int,
    val accepted: Int,
    /** Not decided: an outage, a timeout, a server that went quiet. Will retry. */
    val deferred: Int,
    /** Refused permanently. The local review is unaffected. */
    val refused: Int,
) {
    val didAnything: Boolean get() = attempted > 0
}
