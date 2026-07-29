package dev.bee.beecode.app

import dev.bee.beecode.persistence.ActivityOutboxRepository
import dev.bee.beecode.persistence.StoredOutboxRow
import kotlinx.datetime.Instant

/**
 * The durable queue of Leaderboard activity waiting to be uploaded (LDB-007).
 *
 * A **pure state machine over a list of rows**, deliberately: no HTTP, no clock, no
 * database, no coroutines. Everything that makes an outbox dangerous — double-counting a
 * review, losing one, retrying forever, blocking finalization — is a state-transition
 * question, and answering those against a fake server proves nothing about the ones that
 * matter. Answering them here, exhaustively, is what makes a later transport boring.
 *
 * This is the *Leaderboard* path, and it is not the sync path. ADR 0002 draws the line and
 * it matters:
 *
 * | | This outbox | [SnapshotMerge] |
 * |---|---|---|
 * | Audience | other people | the same learner's other devices |
 * | Payload | one counter's worth of metadata | the whole profile, source included |
 * | Storage | a shared service | storage the learner already owns |
 * | Semantics | append-only idempotent ingestion | per-entity merge + CAS |
 *
 * Conflating them is how source code ends up on someone else's server. [ActivityEvent]
 * therefore cannot carry source, output, test values, or FSRS state — not by convention
 * but because the type has nowhere to put them.
 *
 * ### Why finalization never waits
 *
 * A review commits locally and *then* an event is enqueued. The acceptance criterion is
 * "review finalization never waits for network", and the structural guarantee is that
 * nothing in this file can fail in a way a review can observe: enqueueing appends a row,
 * and a row that is never uploaded costs a Leaderboard count, never a schedule.
 *
 * ### Why rejection is terminal but harmless
 *
 * A server can refuse an event permanently — an unknown Problem, a stale manifest. That
 * must not undo the local review, so [OutboxState.REJECTED] is an end state that records
 * the decision and nothing else. The learner solved the Problem; the Leaderboard simply
 * will not count it.
 */
object ActivityOutbox {

    /**
     * Enqueue an event for later upload, ignoring one already present.
     *
     * Idempotent on [ActivityEvent.eventId] because the caller is the review transaction:
     * a retry after an uncertain commit must not enqueue twice, and the transaction cannot
     * know whether its previous attempt got this far.
     */
    fun enqueue(rows: List<OutboxRow>, event: ActivityEvent, now: Instant): List<OutboxRow> =
        if (rows.any { it.event.eventId == event.eventId }) {
            rows
        } else {
            rows + OutboxRow(
                event = event,
                state = OutboxState.PENDING,
                attempts = 0,
                nextAttemptAt = now,
                lastReason = null,
            )
        }

    /**
     * The next batch to upload, or empty when nothing is due.
     *
     * Only [OutboxState.PENDING] rows whose backoff has elapsed, oldest first, capped at
     * [MAX_BATCH]. In-flight rows are excluded so two overlapping uploads cannot send the
     * same event — the transport is not assumed to be single-threaded.
     */
    fun nextBatch(rows: List<OutboxRow>, now: Instant, limit: Int = MAX_BATCH): List<OutboxRow> =
        rows.asSequence()
            .filter { it.state == OutboxState.PENDING && it.nextAttemptAt <= now }
            .sortedBy { it.event.occurredAt }
            .take(limit)
            .toList()

    /** Mark a batch in flight, so a concurrent caller cannot pick the same rows. */
    fun markInFlight(rows: List<OutboxRow>, batch: List<OutboxRow>, now: Instant): List<OutboxRow> {
        val ids = batch.mapTo(HashSet()) { it.event.eventId }
        return rows.map { row ->
            if (row.event.eventId in ids && row.state == OutboxState.PENDING) {
                row.copy(state = OutboxState.IN_FLIGHT, nextAttemptAt = now)
            } else {
                row
            }
        }
    }

    /**
     * Apply a server's verdict for one event.
     *
     * [UploadVerdict.Accepted] and [UploadVerdict.Duplicate] are treated identically on
     * purpose. A client that uploaded, timed out, and retried has no way to distinguish
     * "you accepted it" from "you already had it", and the server saying *either* means
     * the event is durably counted exactly once. Treating a duplicate as a failure would
     * retry forever; treating it as an error would tell the learner something is wrong
     * when nothing is.
     */
    fun applyVerdict(
        rows: List<OutboxRow>,
        eventId: String,
        verdict: UploadVerdict,
        now: Instant,
    ): List<OutboxRow> = rows.map { row ->
        if (row.event.eventId != eventId) {
            row
        } else {
            when (verdict) {
                is UploadVerdict.Accepted, is UploadVerdict.Duplicate -> row.copy(
                    state = OutboxState.ACKNOWLEDGED,
                    attempts = row.attempts + 1,
                    nextAttemptAt = now,
                    lastReason = null,
                )

                is UploadVerdict.Rejected -> row.copy(
                    // Terminal, and only the decision is retained — never the payload's
                    // detail. Retrying would be dishonest about a decision the server has
                    // already made permanently.
                    state = OutboxState.REJECTED,
                    attempts = row.attempts + 1,
                    nextAttemptAt = now,
                    lastReason = verdict.reason,
                )

                is UploadVerdict.Retryable -> {
                    val attempts = row.attempts + 1
                    if (attempts >= MAX_ATTEMPTS) {
                        // Not rejected: the server never made a decision. Parked so a
                        // learner can retry by hand rather than the app draining a battery
                        // against an outage that may last days.
                        row.copy(
                            state = OutboxState.PARKED,
                            attempts = attempts,
                            nextAttemptAt = now,
                            lastReason = verdict.reason,
                        )
                    } else {
                        row.copy(
                            state = OutboxState.PENDING,
                            attempts = attempts,
                            nextAttemptAt = now + backoffFor(attempts, row.event.eventId),
                            lastReason = verdict.reason,
                        )
                    }
                }
            }
        }
    }

    /**
     * Return in-flight rows to pending after a restart.
     *
     * A process death between "sent" and "acknowledged" leaves a row stranded in flight
     * with no verdict coming. Recovering it as pending is safe *because* the server
     * de-duplicates: the worst case is a [UploadVerdict.Duplicate], which
     * [applyVerdict] already treats as success. Dropping the row instead would silently
     * lose a Leaderboard count; leaving it in flight would strand it forever.
     */
    fun recoverAfterRestart(rows: List<OutboxRow>, now: Instant): List<OutboxRow> =
        rows.map { row ->
            if (row.state == OutboxState.IN_FLIGHT) {
                row.copy(state = OutboxState.PENDING, nextAttemptAt = now)
            } else {
                row
            }
        }

    /** Manual retry for a parked row, so an outage does not need an app reinstall. */
    fun retryParked(rows: List<OutboxRow>, now: Instant): List<OutboxRow> =
        rows.map { row ->
            if (row.state == OutboxState.PARKED) {
                row.copy(state = OutboxState.PENDING, attempts = 0, nextAttemptAt = now)
            } else {
                row
            }
        }

    /**
     * Drop acknowledged rows older than [ACKNOWLEDGED_RETENTION].
     *
     * Only acknowledged ones: a pending row is unfinished work and a rejected or parked
     * row is the record of a decision the learner may ask about. The delay exists so a
     * server that asks "did you send this?" can still be answered.
     */
    fun prune(rows: List<OutboxRow>, now: Instant): List<OutboxRow> = rows.filterNot { row ->
        row.state == OutboxState.ACKNOWLEDGED && row.nextAttemptAt + ACKNOWLEDGED_RETENTION <= now
    }

    /** What to show the learner, so a stuck queue is visible rather than mysterious. */
    fun status(rows: List<OutboxRow>): OutboxStatus = OutboxStatus(
        pending = rows.count { it.state == OutboxState.PENDING },
        inFlight = rows.count { it.state == OutboxState.IN_FLIGHT },
        acknowledged = rows.count { it.state == OutboxState.ACKNOWLEDGED },
        parked = rows.count { it.state == OutboxState.PARKED },
        rejected = rows.count { it.state == OutboxState.REJECTED },
    )

    /**
     * Exponential backoff with deterministic per-event jitter.
     *
     * Jitter matters because every device that studied during an outage retries when
     * connectivity returns, and identical schedules turn that into a thundering herd
     * against a self-hosted server on someone's home connection.
     *
     * It is derived from the event id rather than a random source so the whole state
     * machine stays a pure function — a test can assert an exact delay, which a random
     * jitter would make impossible without injecting a seed nobody else needs.
     */
    internal fun backoffFor(attempts: Int, eventId: String): kotlin.time.Duration {
        val exponent = (attempts - 1).coerceIn(0, MAX_BACKOFF_EXPONENT)
        val base = BASE_BACKOFF_SECONDS shl exponent
        // Up to a quarter of the interval, spreading retries without ever shortening one.
        val jitter = (eventId.hashCode().toLong() and 0x7fffffff) % (base / 4 + 1)
        return kotlin.time.Duration.parse("${base + jitter}s")
    }

    /** Rows per upload. Small enough that one failure re-sends little. */
    const val MAX_BATCH: Int = 50

    /**
     * Retries before parking.
     *
     * Six attempts with the backoff below spans roughly half an hour, which covers a
     * restart or a brief outage. Beyond that the problem is not transient and burning
     * battery on it serves nobody.
     */
    const val MAX_ATTEMPTS: Int = 6

    private const val BASE_BACKOFF_SECONDS = 30L
    private const val MAX_BACKOFF_EXPONENT = 5

    /** How long an acknowledged row is kept for auditability. */
    val ACKNOWLEDGED_RETENTION: kotlin.time.Duration = kotlin.time.Duration.parse("7d")
}

/**
 * One activity event, carrying the least the Leaderboard can work with.
 *
 * Every forbidden field from `goals/08-leaderboards.md` is absent **by construction**:
 * there is no source, no source hash, no stdout, no expected or actual test value, no
 * FSRS stability, difficulty, interval, due date, rating, or parameter. A future field
 * cannot be added by accident either, because adding one means editing this type and
 * `ActivityOutboxTest` asserts the shape.
 *
 * @param eventId a device-minted UUID, and the idempotency key end to end.
 * @param problemId present for *request-only* manifest validation. The plan is explicit
 *   that the durable accepted row discards specific Problem identity unless a separately
 *   approved metric needs it, so this travels and is then dropped server-side.
 * @param occurredAt the completion instant in UTC.
 * @param localDate the profile-zone date, because a streak is a local-calendar question
 *   and recomputing it from UTC server-side would get travel and DST wrong.
 * @param countsAsSolved whether this was an unaided pass. The Leaderboard counts only
 *   those, which is the same rule that stops the 5am Club being farmed by reading an
 *   explanation.
 */
data class ActivityEvent(
    val eventId: String,
    val problemId: String,
    val occurredAt: Instant,
    val localDate: String,
    val countsAsSolved: Boolean,
) {
    init {
        require(eventId.isNotBlank()) { "an activity event must carry an idempotency key" }
        require(problemId.isNotBlank()) { "an activity event must name its Problem" }
    }
}

/** One queued event and its delivery state. */
data class OutboxRow(
    val event: ActivityEvent,
    val state: OutboxState,
    val attempts: Int,
    /** When this row becomes eligible again; also the acknowledgement instant. */
    val nextAttemptAt: Instant,
    /** The server's stated reason, for a rejection or a park. Never payload detail. */
    val lastReason: String?,
)

/**
 * Delivery state.
 *
 * [PARKED] and [REJECTED] are separate because the difference is whether the *server*
 * decided. A parked row failed to reach one and is retryable by hand; a rejected row was
 * refused and never will be. Collapsing them would either retry a decision forever or
 * abandon work over a flat tyre.
 */
enum class OutboxState { PENDING, IN_FLIGHT, ACKNOWLEDGED, PARKED, REJECTED }

/** A server's answer for one uploaded event. */
sealed interface UploadVerdict {

    data object Accepted : UploadVerdict

    /** Already counted. Success from the client's point of view — see `applyVerdict`. */
    data object Duplicate : UploadVerdict

    /** Refused permanently. Retrying cannot change it. */
    data class Rejected(val reason: String) : UploadVerdict

    /** Nothing was decided: an outage, a timeout, an expired token. */
    data class Retryable(val reason: String) : UploadVerdict
}

/**
 * Maps between the pure outbox rows and the primitives `:persistence` stores.
 *
 * Lives here rather than in `:persistence` because the dependency runs `:shared` →
 * `:persistence`: the storage layer cannot see [OutboxRow] and should not learn to. This is
 * the layer that already knows both shapes.
 */
object OutboxStorage {

    fun toStored(row: OutboxRow): StoredOutboxRow = StoredOutboxRow(
        eventId = row.event.eventId,
        problemId = row.event.problemId,
        occurredAtEpochMillis = row.event.occurredAt.toEpochMilliseconds(),
        localDate = row.event.localDate,
        countsAsSolved = row.event.countsAsSolved,
        state = row.state.name,
        attempts = row.attempts,
        nextAttemptAtEpochMillis = row.nextAttemptAt.toEpochMilliseconds(),
        lastReason = row.lastReason,
    )

    /**
     * Read one stored row back, or null if its state is unrecognized.
     *
     * A null rather than a throw, because the realistic cause is a *downgrade*: a newer
     * BeeCode wrote a state this build has no name for. Dropping that row loses at most one
     * Leaderboard count, while throwing would make the app unopenable — and the count is
     * recoverable, because [ActivityProjection] derives events from the review log rather
     * than from this queue.
     */
    fun fromStored(stored: StoredOutboxRow): OutboxRow? {
        val state = OutboxState.entries.firstOrNull { it.name == stored.state } ?: return null
        return OutboxRow(
            event = ActivityEvent(
                eventId = stored.eventId,
                problemId = stored.problemId,
                occurredAt = Instant.fromEpochMilliseconds(stored.occurredAtEpochMillis),
                localDate = stored.localDate,
                countsAsSolved = stored.countsAsSolved,
            ),
            state = state,
            attempts = stored.attempts,
            nextAttemptAt = Instant.fromEpochMilliseconds(stored.nextAttemptAtEpochMillis),
            lastReason = stored.lastReason,
        )
    }

    fun load(repository: ActivityOutboxRepository): List<OutboxRow> =
        repository.all().mapNotNull(::fromStored)

    fun save(repository: ActivityOutboxRepository, rows: List<OutboxRow>) {
        repository.replaceAll(rows.map(::toStored))
    }
}

/** Counts for a status line, so a stuck queue is visible. */
data class OutboxStatus(
    val pending: Int,
    val inFlight: Int,
    val acknowledged: Int,
    val parked: Int,
    val rejected: Int,
) {
    val hasWorkWaiting: Boolean get() = pending > 0 || inFlight > 0
    val needsAttention: Boolean get() = parked > 0
}
