package dev.bee.beecode.app

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * The activity outbox, against LDB-007's acceptance criteria one at a time.
 *
 * Each of those criteria is a way to lose or double-count a learner's work, and every one
 * is a state-transition question rather than a networking question. So they are answered
 * here, exhaustively, against the pure state machine — which is why a later HTTP transport
 * can be thin enough to read in one sitting.
 *
 * The scenarios the plan names as evidence: airplane mode, an outage, an expired token, a
 * process kill mid-upload, and a manual retry.
 */
class ActivityOutboxTest {

    // ---- Enqueueing ----------------------------------------------------------

    @Test
    fun enqueueingIsIdempotentOnTheEventId() {
        // The caller is the review transaction, and a retry after an uncertain commit
        // cannot know whether its previous attempt already enqueued. Two rows would be two
        // Leaderboard counts for one solve.
        val event = event("e1")
        val once = ActivityOutbox.enqueue(emptyList(), event, NOW)
        val twice = ActivityOutbox.enqueue(once, event, NOW + 1.hours)

        assertEquals(1, twice.size)
        assertEquals(OutboxState.PENDING, twice.single().state)
    }

    @Test
    fun distinctEventsAreAllQueued() {
        var rows = emptyList<OutboxRow>()
        rows = ActivityOutbox.enqueue(rows, event("e1"), NOW)
        rows = ActivityOutbox.enqueue(rows, event("e2"), NOW)
        assertEquals(2, rows.size)
    }

    @Test
    fun anActivityEventCannotCarryForbiddenFields() {
        // The plan forbids source, source hash, stdout, expected/actual values, and every
        // FSRS field. Asserted structurally rather than by review: the type has nowhere to
        // put them, and this fails if a future field is added.
        val names = ActivityEvent::class.java.declaredFields
            .map { it.name }
            .filterNot { it == "\$stable" }
            .toSet()
        assertEquals(
            setOf("eventId", "problemId", "occurredAt", "localDate", "countsAsSolved"),
            names,
        )
        val forbidden = listOf(
            "source", "sourceHash", "stdout", "stderr", "output", "expected", "actual",
            "stability", "difficulty", "interval", "dueAt", "rating", "parameters",
        )
        forbidden.forEach { field ->
            assertTrue(
                names.none { it.contains(field, ignoreCase = true) },
                "ActivityEvent must not carry '$field' — see the Forbidden list in goals/08",
            )
        }
    }

    // ---- Batching and backoff ------------------------------------------------

    @Test
    fun aBatchTakesOldestFirstAndIsCapped() {
        // Oldest first so a long offline stretch uploads in the order it happened, and
        // capped so one failure re-sends little.
        var rows = emptyList<OutboxRow>()
        repeat(ActivityOutbox.MAX_BATCH + 10) { index ->
            rows = ActivityOutbox.enqueue(rows, event("e$index", occurredAt = NOW + index.hours), NOW)
        }
        val batch = ActivityOutbox.nextBatch(rows, NOW)
        assertEquals(ActivityOutbox.MAX_BATCH, batch.size)
        assertEquals("e0", batch.first().event.eventId)
    }

    @Test
    fun airplaneModeQueuesAndChangesNothingElse() {
        // "Review finalization never waits for network." With no upload attempted at all,
        // events simply accumulate as pending — there is no failure state to enter.
        var rows = emptyList<OutboxRow>()
        repeat(5) { rows = ActivityOutbox.enqueue(rows, event("e$it"), NOW) }
        val status = ActivityOutbox.status(rows)
        assertEquals(5, status.pending)
        assertEquals(0, status.parked + status.rejected + status.inFlight)
        assertTrue(status.hasWorkWaiting)
        assertTrue(!status.needsAttention)
    }

    @Test
    fun anInFlightRowIsNotHandedOutTwice() {
        // Two overlapping uploads must not send one event twice. The transport is not
        // assumed to be single-threaded.
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        val batch = ActivityOutbox.nextBatch(rows, NOW)
        val marked = ActivityOutbox.markInFlight(rows, batch, NOW)
        assertEquals(emptyList(), ActivityOutbox.nextBatch(marked, NOW))
    }

    @Test
    fun aRetryableFailureBacksOffAndIsNotImmediatelyEligible() {
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        val failed = ActivityOutbox.applyVerdict(rows, "e1", UploadVerdict.Retryable("outage"), NOW)

        assertEquals(OutboxState.PENDING, failed.single().state)
        // Not due yet, so a tight loop cannot hammer a server that is already struggling.
        assertEquals(emptyList(), ActivityOutbox.nextBatch(failed, NOW))
        assertEquals(1, ActivityOutbox.nextBatch(failed, NOW + 1.hours).size)
    }

    @Test
    fun backoffGrowsAndCarriesPerEventJitter() {
        // Growth so an outage is not hammered; jitter so every device that studied during
        // one does not retry in the same instant against a self-hosted server.
        val first = ActivityOutbox.backoffFor(1, "e1")
        val later = ActivityOutbox.backoffFor(4, "e1")
        assertTrue(later > first, "backoff must grow: $first then $later")

        val a = ActivityOutbox.backoffFor(3, "event-a")
        val b = ActivityOutbox.backoffFor(3, "event-b")
        assertTrue(a != b, "two events must not retry in lockstep")
        // Jitter only ever delays, never shortens, so the growth guarantee holds.
        assertTrue(a >= ActivityOutbox.backoffFor(2, "event-a"))
    }

    // ---- Verdicts ------------------------------------------------------------

    @Test
    fun aDuplicateIsTreatedExactlyLikeAnAcceptance() {
        // "Timeout after server commit safely retries as duplicate." A client that
        // uploaded, timed out, and retried cannot distinguish "accepted" from "already
        // had it" — and either means counted exactly once. Retrying a duplicate would loop
        // forever; surfacing it as an error would alarm a learner over nothing.
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        val accepted = ActivityOutbox.applyVerdict(rows, "e1", UploadVerdict.Accepted, NOW)
        val duplicate = ActivityOutbox.applyVerdict(rows, "e1", UploadVerdict.Duplicate, NOW)

        assertEquals(OutboxState.ACKNOWLEDGED, accepted.single().state)
        assertEquals(OutboxState.ACKNOWLEDGED, duplicate.single().state)
        assertEquals(accepted.single().state, duplicate.single().state)
        assertEquals(null, duplicate.single().lastReason)
    }

    @Test
    fun anAcknowledgedRowIsNeverUploadedAgain() {
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        val done = ActivityOutbox.applyVerdict(rows, "e1", UploadVerdict.Accepted, NOW)
        assertEquals(emptyList(), ActivityOutbox.nextBatch(done, NOW + 30.days))
    }

    @Test
    fun aRejectionIsTerminalAndKeepsOnlyTheDecision() {
        // "Rejection cannot undo local review, FSRS, or local achievement." Nothing here
        // can: the row is an end state carrying a reason and nothing else, and the review
        // it refers to was committed long before.
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        val rejected = ActivityOutbox.applyVerdict(
            rows,
            "e1",
            UploadVerdict.Rejected("unknown Problem revision"),
            NOW,
        )
        assertEquals(OutboxState.REJECTED, rejected.single().state)
        assertEquals("unknown Problem revision", rejected.single().lastReason)
        // Never retried: the server already decided permanently.
        assertEquals(emptyList(), ActivityOutbox.nextBatch(rejected, NOW + 30.days))
        // And a manual retry must not resurrect a decision either.
        assertEquals(
            OutboxState.REJECTED,
            ActivityOutbox.retryParked(rejected, NOW).single().state,
        )
    }

    @Test
    fun repeatedRetryableFailuresParkRatherThanRetryForever() {
        // "Infinite retry/battery use" is a named risk. After MAX_ATTEMPTS the problem is
        // not transient, so the row parks and stops costing anything.
        var rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        repeat(ActivityOutbox.MAX_ATTEMPTS) {
            rows = ActivityOutbox.applyVerdict(rows, "e1", UploadVerdict.Retryable("token expired"), NOW)
        }
        assertEquals(OutboxState.PARKED, rows.single().state)
        assertEquals(emptyList(), ActivityOutbox.nextBatch(rows, NOW + 365.days))
        assertTrue(ActivityOutbox.status(rows).needsAttention)
    }

    @Test
    fun parkingIsDistinctFromRejectionBecauseNoServerDecided() {
        // The distinction is the point: a parked row can be retried by hand, a rejected
        // one cannot. Collapsing them either retries a decision forever or abandons work
        // over an outage.
        var parked = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        repeat(ActivityOutbox.MAX_ATTEMPTS) {
            parked = ActivityOutbox.applyVerdict(parked, "e1", UploadVerdict.Retryable("no route"), NOW)
        }
        val retried = ActivityOutbox.retryParked(parked, NOW)
        assertEquals(OutboxState.PENDING, retried.single().state)
        assertEquals(0, retried.single().attempts, "a manual retry resets the budget")
        assertEquals(1, ActivityOutbox.nextBatch(retried, NOW).size)
    }

    @Test
    fun averdictForAnUnknownEventChangesNothing() {
        // A late response for a row already pruned must not resurrect or corrupt anything.
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        assertEquals(rows, ActivityOutbox.applyVerdict(rows, "gone", UploadVerdict.Accepted, NOW))
    }

    // ---- Restart and retention -----------------------------------------------

    @Test
    fun aProcessKillMidUploadRecoversTheStrandedRow() {
        // "App restart preserves pending events." A death between sent and acknowledged
        // leaves a row in flight with no verdict coming. Dropping it loses a count;
        // leaving it strands it forever. Recovering is safe because the server
        // de-duplicates — the worst case is a Duplicate, which counts as success.
        val rows = ActivityOutbox.enqueue(emptyList(), event("e1"), NOW)
        val inFlight = ActivityOutbox.markInFlight(rows, ActivityOutbox.nextBatch(rows, NOW), NOW)
        val recovered = ActivityOutbox.recoverAfterRestart(inFlight, NOW + 1.hours)

        assertEquals(OutboxState.PENDING, recovered.single().state)
        assertEquals(1, ActivityOutbox.nextBatch(recovered, NOW + 1.hours).size)
        // And the duplicate that likely comes back resolves cleanly.
        val resolved = ActivityOutbox.applyVerdict(recovered, "e1", UploadVerdict.Duplicate, NOW + 2.hours)
        assertEquals(OutboxState.ACKNOWLEDGED, resolved.single().state)
    }

    @Test
    fun restartDoesNotDisturbSettledRows() {
        var rows = ActivityOutbox.enqueue(emptyList(), event("done"), NOW)
        rows = ActivityOutbox.enqueue(rows, event("refused"), NOW)
        rows = ActivityOutbox.applyVerdict(rows, "done", UploadVerdict.Accepted, NOW)
        rows = ActivityOutbox.applyVerdict(rows, "refused", UploadVerdict.Rejected("no"), NOW)

        val recovered = ActivityOutbox.recoverAfterRestart(rows, NOW + 1.hours)
        assertEquals(rows, recovered)
    }

    @Test
    fun onlyAcknowledgedRowsArePruned() {
        // "Acknowledged rows prune only under documented retention." A pending row is
        // unfinished work; a rejected or parked row is the record of a decision a learner
        // may ask about. Losing any of those to a cleanup would be the bug.
        var rows = emptyList<OutboxRow>()
        rows = ActivityOutbox.enqueue(rows, event("acked"), NOW)
        rows = ActivityOutbox.enqueue(rows, event("waiting"), NOW)
        rows = ActivityOutbox.enqueue(rows, event("refused"), NOW)
        rows = ActivityOutbox.applyVerdict(rows, "acked", UploadVerdict.Accepted, NOW)
        rows = ActivityOutbox.applyVerdict(rows, "refused", UploadVerdict.Rejected("no"), NOW)

        val kept = ActivityOutbox.prune(rows, NOW + ActivityOutbox.ACKNOWLEDGED_RETENTION + 1.hours)
        assertEquals(setOf("waiting", "refused"), kept.map { it.event.eventId }.toSet())
    }

    @Test
    fun anAcknowledgedRowSurvivesUntilItsRetentionElapses() {
        // The delay exists so a server asking "did you send this?" can still be answered.
        val rows = ActivityOutbox.applyVerdict(
            ActivityOutbox.enqueue(emptyList(), event("e1"), NOW),
            "e1",
            UploadVerdict.Accepted,
            NOW,
        )
        assertEquals(1, ActivityOutbox.prune(rows, NOW + 1.days).size)
    }

    // ---- The full offline journey --------------------------------------------

    @Test
    fun aWeekOfflineThenAnOutageThenSuccessCountsEachSolveExactlyOnce() {
        // The scenario the plan cares about: study offline for days, come back to a server
        // that is briefly down, and end up with every solve counted once and nothing lost.
        var rows = emptyList<OutboxRow>()
        repeat(7) { day ->
            rows = ActivityOutbox.enqueue(rows, event("day-$day", occurredAt = NOW + (day * 24).hours), NOW)
        }

        // Connectivity returns, but the server is down for the first two attempts.
        var clock = NOW + 7.days
        repeat(2) {
            val batch = ActivityOutbox.nextBatch(rows, clock)
            assertEquals(7, batch.size)
            rows = ActivityOutbox.markInFlight(rows, batch, clock)
            batch.forEach { row ->
                rows = ActivityOutbox.applyVerdict(rows, row.event.eventId, UploadVerdict.Retryable("502"), clock)
            }
            clock += 1.hours
        }

        // Then it accepts, and one of the seven comes back as a duplicate from an earlier
        // attempt that actually landed before the timeout.
        val batch = ActivityOutbox.nextBatch(rows, clock)
        assertEquals(7, batch.size)
        rows = ActivityOutbox.markInFlight(rows, batch, clock)
        batch.forEachIndexed { index, row ->
            val verdict = if (index == 3) UploadVerdict.Duplicate else UploadVerdict.Accepted
            rows = ActivityOutbox.applyVerdict(rows, row.event.eventId, verdict, clock)
        }

        val status = ActivityOutbox.status(rows)
        assertEquals(7, status.acknowledged)
        assertEquals(0, status.pending + status.inFlight + status.parked + status.rejected)
        assertTrue(!status.hasWorkWaiting)
        // Seven solves, seven rows, no duplicates and nothing dropped.
        assertEquals(7, rows.map { it.event.eventId }.toSet().size)
    }

    // ---- Durability ----------------------------------------------------------

    @Test
    fun theQueueSurvivesAProcessDeathThroughRealSqlite() {
        // "App restart preserves pending events." The state machine is pure and was
        // exhaustively correct while living entirely in memory, which is a way of saying it
        // was exhaustively correct and completely lost on every process death.
        val file = kotlin.io.path.createTempFile("beecode-outbox-shared-", ".db").toFile()
        file.delete()
        try {
            var rows = emptyList<OutboxRow>()
            rows = ActivityOutbox.enqueue(rows, event("e1"), NOW)
            rows = ActivityOutbox.enqueue(rows, event("e2"), NOW)
            rows = ActivityOutbox.applyVerdict(rows, "e1", UploadVerdict.Retryable("502"), NOW)

            dev.bee.beecode.persistence.BeeCodeDatabase.open(file.absolutePath).use { database ->
                OutboxStorage.save(
                    dev.bee.beecode.persistence.ActivityOutboxRepository(database),
                    rows,
                )
            }

            // A new process, a new database handle, the same queue.
            dev.bee.beecode.persistence.BeeCodeDatabase.open(file.absolutePath).use { database ->
                val reloaded = OutboxStorage.load(
                    dev.bee.beecode.persistence.ActivityOutboxRepository(database),
                )
                assertEquals(rows, reloaded, "the reloaded queue must equal what was saved")
                // And the backoff survived, so a retry does not restart its schedule — which
                // is what would let a restart loop hammer a server that is already down.
                val retried = reloaded.single { it.event.eventId == "e1" }
                assertEquals(1, retried.attempts)
                assertEquals("502", retried.lastReason)
                // e2 was never retried, so it is still due immediately; e1 carries the
                // backoff from its failure and is not.
                assertEquals(listOf("e2"), ActivityOutbox.nextBatch(reloaded, NOW).map { it.event.eventId })
                assertEquals(2, ActivityOutbox.nextBatch(reloaded, NOW + 1.hours).size)
            }
        } finally {
            listOf("", "-wal", "-shm").forEach { java.io.File(file.absolutePath + it).delete() }
        }
    }

    @Test
    fun anUnrecognizedStateIsDroppedRatherThanCrashingTheApp() {
        // The realistic cause is a downgrade: a newer BeeCode wrote a state this build has
        // no name for. Dropping the row loses at most one Leaderboard count — recoverable,
        // because ActivityProjection derives events from the review log — while throwing
        // would make the app unopenable.
        val unknown = dev.bee.beecode.persistence.StoredOutboxRow(
            eventId = "e1",
            problemId = "two-sum",
            occurredAtEpochMillis = NOW.toEpochMilliseconds(),
            localDate = "2026-07-29",
            countsAsSolved = true,
            state = "TRANSMOGRIFYING",
            attempts = 0,
            nextAttemptAtEpochMillis = NOW.toEpochMilliseconds(),
            lastReason = null,
        )
        assertEquals(null, OutboxStorage.fromStored(unknown))
    }

    @Test
    fun everyStateNameRoundTripsThroughStorage() {
        // A state whose name did not survive would be silently dropped by the reader above,
        // so the mapping is checked for all of them rather than the ones a test happens to
        // produce.
        OutboxState.entries.forEach { state ->
            val row = OutboxRow(
                event = event("e-${state.name}"),
                state = state,
                attempts = 3,
                nextAttemptAt = NOW,
                lastReason = "because",
            )
            assertEquals(row, OutboxStorage.fromStored(OutboxStorage.toStored(row)))
        }
    }

    private fun event(
        id: String,
        occurredAt: Instant = NOW,
        countsAsSolved: Boolean = true,
    ) = ActivityEvent(
        eventId = id,
        problemId = "two-sum",
        occurredAt = occurredAt,
        localDate = "2026-07-29",
        countsAsSolved = countsAsSolved,
    )

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T12:00:00Z")
    }
}
