package dev.bee.beecode.domain

import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/**
 * The materialized scheduling state for one Problem.
 *
 * Stability and difficulty are FSRS's memory state, stored here rather than
 * recomputed on read so the due queue is a cheap indexed query.
 *
 * [version] is an optimistic-concurrency counter, and it is the reason two
 * concurrent finalizations cannot silently both advance the same Problem. The
 * write transaction reads this version, computes the transition, and commits only
 * if the version is unchanged. It is intentionally a plain counter rather than a
 * timestamp: counters cannot tie, and clocks can move backwards.
 */
data class ProblemSchedule(
    val problemId: ProblemId,
    val stability: Double,
    val difficulty: Double,
    /** When this Problem next becomes due. */
    val dueAt: Instant,
    val lastReviewedAt: Instant,
    /**
     * The interval FSRS chose, retained for display and audit.
     *
     * Fractional, because FSRS-7 schedules in fractional days: a same-day review
     * can legitimately be due in ten minutes. An `Int` here would floor exactly
     * the case the algorithm exists to handle.
     */
    val intervalDays: Double,
    val reviewCount: Int,
    /** Consecutive non-lapse reviews; reset to zero by an AGAIN. */
    val lapseCount: Int,
    val version: Long,
    /**
     * Last mutation time. ADR 0002 property 2: per-entity merge is last-write-wins
     * over this column, so a mutable syncable row without it could only be
     * clobbered, never merged.
     */
    val updatedAt: Instant,
) {
    init {
        require(stability.isFinite() && stability > 0.0) { "stability must be finite and positive" }
        require(difficulty.isFinite() && difficulty in MIN_DIFFICULTY..MAX_DIFFICULTY) {
            "difficulty must be finite and in [$MIN_DIFFICULTY, $MAX_DIFFICULTY]"
        }
        // Positive, not at-least-one: a sub-day interval is valid under FSRS-7.
        require(intervalDays.isFinite() && intervalDays > 0.0) {
            "intervalDays must be finite and positive"
        }
        require(reviewCount >= 0) { "reviewCount must not be negative" }
        require(lapseCount >= 0) { "lapseCount must not be negative" }
        require(version >= 0) { "version must not be negative" }
    }

    fun isDueAt(now: Instant): Boolean = dueAt <= now

    companion object {
        const val MIN_DIFFICULTY: Double = 1.0
        const val MAX_DIFFICULTY: Double = 10.0
    }
}

/**
 * A learner-facing description of an interval, e.g. "10 minutes" or "3 days".
 *
 * Lives in the domain, and is shared by the desktop and Android clients, because
 * FSRS-7's intervals are fractional and a raw one reads as nonsense: "next review
 * in 0.00694 days" is technically the schedule and tells nobody anything. Two
 * clients formatting it independently would drift, and the unit choice is a
 * property of the number rather than of either UI.
 *
 * Chooses the largest unit that leaves the value legible, and rounds rather than
 * truncating — an interval of 0.99 days is "1 day", not "23 hours", because that is
 * what a learner would say.
 */
fun formatIntervalDays(intervalDays: Double): String {
    require(intervalDays.isFinite() && intervalDays > 0.0) {
        "intervalDays must be finite and positive"
    }

    // Each unit is rounded *before* being compared against its own ceiling. Comparing
    // the unrounded value would let a unit's top edge round up into a quantity of
    // itself that the next unit already names: 0.99 days is 23.76 hours, which rounds
    // to 24, and "24 hours" is a worse way to say "1 day".
    val minutes = Math.round(intervalDays * MINUTES_PER_DAY)
    if (minutes < 1L) {
        // Not "0 minutes", which would read as a bug rather than a schedule. The
        // honest statement about a sub-minute interval is that it is due now.
        return "less than a minute"
    }
    if (minutes < MINUTES_PER_HOUR) {
        return pluralize(minutes, "minute")
    }

    val hours = Math.round(intervalDays * HOURS_PER_DAY)
    if (hours < HOURS_PER_DAY) {
        return pluralize(hours, "hour")
    }

    val days = Math.round(intervalDays)
    if (days < DAYS_PER_MONTH) {
        return pluralize(days, "day")
    }

    val months = Math.round(intervalDays / DAYS_PER_MONTH)
    if (months < MONTHS_PER_YEAR) {
        return pluralize(months, "month")
    }
    return pluralize(Math.round(intervalDays / DAYS_PER_YEAR), "year")
}

private const val MINUTES_PER_DAY = 1_440.0
private const val MINUTES_PER_HOUR = 60L
private const val HOURS_PER_DAY = 24L
private const val DAYS_PER_MONTH = 30.0
private const val DAYS_PER_YEAR = 365.0
private const val MONTHS_PER_YEAR = 12L

private fun pluralize(count: Long, unit: String): String =
    if (count == 1L) "1 $unit" else "$count ${unit}s"

/**
 * An immutable record of one finalized review.
 *
 * This log is **append-only**, and that is a deliberate sync decision as much as
 * an audit one (ADR 0002 property 3): merging two append-only logs keyed by
 * [sessionId] is a set union, which is always correct and needs no timestamp
 * comparison. It also means the merged FSRS state can be *recomputed* from the
 * merged log rather than guessed at by last-write-wins.
 *
 * The FSRS fields are recorded redundantly on purpose. Storing the resulting
 * state, not just the inputs, means operational state can be rebuilt by folding
 * outputs with no historical engine binary present. Recomputing from inputs is
 * then an integrity *check*, available only while the exact old implementation
 * still is.
 */
data class ProblemReviewFinalized(
    val eventId: DomainEventId,
    /** The idempotency key. One review per session, forever. */
    val sessionId: ReviewSessionId,
    val problemId: ProblemId,
    val problemRevisionId: ProblemRevisionId,
    val executionRunId: ExecutionRunId,
    val outcome: ExecutionOutcome,
    val rating: ReviewRating,
    val aided: Boolean,
    val countsAsSolved: Boolean,
    val finalizedAt: Instant,
    /**
     * The timezone in which this review's local date was determined.
     *
     * Part of the recorded fact, not a display preference. Streaks and the 5am
     * Club are defined in local dates, so the zone that was active when the
     * learner finalized is what makes their streak reproducible. Interpreting old
     * reviews in the learner's *current* zone would silently rewrite streak
     * history every time they travelled.
     *
     * Stored as an id string rather than a `TimeZone` so the domain stays free of
     * platform timezone-database differences, and so an unrecognised historical
     * zone can be surfaced rather than crashing.
     */
    val streakZoneId: String,
    val transition: FsrsTransitionRecord,
    /** ADR 0002 property 4: which installation recorded this. */
    val deviceId: DeviceId,
) {
    /**
     * The local date this review counted for.
     *
     * Derived from [streakZoneId], so it reproduces the date the learner saw even
     * if their zone has since changed. Falls back to UTC for an unrecognised zone,
     * which is stable and auditable rather than a crash.
     */
    fun localDate(): LocalDate = finalizedAt.localDateIn(resolvedZone())

    /** The local hour, used by the 5am Club's `[00:00, 06:00)` window. */
    fun localHour(): Int = finalizedAt.localHourIn(resolvedZone())

    private fun resolvedZone(): TimeZone =
        runCatching { TimeZone.of(streakZoneId) }.getOrDefault(TimeZone.UTC)
}

/**
 * A complete audit of one FSRS transition.
 *
 * Every field here exists so that a future BeeCode can explain, or rebuild, a
 * due date it did not compute. Changing the engine or its parameters silently
 * rewrites every learner's future schedule, so the identity of the mathematics
 * is recorded alongside its result.
 */
data class FsrsTransitionRecord(
    /**
     * Which mathematics produced this row, e.g. "FSRS-7 35-parameter snapshot".
     *
     * The reason a row stays interpretable after the engine changes. Stability and
     * difficulty computed under FSRS-6's 21 parameters do not mean the same thing
     * under FSRS-7's 35, so a stored transition is only explainable if it names the
     * algorithm alongside its numbers.
     */
    val algorithmId: String,
    /** Version of the pinned bee-fsrs artifact. */
    val engineVersion: String,
    /** Hash of the parameter set actually used. */
    val parametersHash: String,
    /** Hash of the previous memory state, to detect a broken fold. */
    val previousStateHash: String,
    val previousStability: Double?,
    val previousDifficulty: Double?,
    /** Fractional days since the previous review, as FSRS-7 takes them. */
    val elapsedDays: Double,
    val ratingValue: Int,
    val desiredRetention: Double,
    val maximumIntervalDays: Double,
    val nextStability: Double,
    val nextDifficulty: Double,
    val nextIntervalDays: Double,
    val retrievability: Double,
    val dueAt: Instant,
) {
    init {
        require(elapsedDays.isFinite() && elapsedDays >= 0.0) {
            "elapsedDays must be finite and non-negative"
        }
        require(nextIntervalDays.isFinite() && nextIntervalDays > 0.0) {
            "nextIntervalDays must be finite and positive"
        }
        require(retrievability.isFinite() && retrievability in 0.0..1.0) {
            "retrievability must be finite and in [0, 1]"
        }
        // Either both previous values are present (a repeat review) or neither
        // is (a first review). A half-populated record would corrupt a fold.
        require((previousStability == null) == (previousDifficulty == null)) {
            "previous stability and difficulty must both be present or both absent"
        }
    }

    val isFirstReview: Boolean get() = previousStability == null
}

/**
 * The learner's in-progress source for one Problem.
 *
 * A draft is mutable and therefore carries [updatedAt] for merge, and [version]
 * so a slow autosave cannot overwrite a newer edit. It survives process death:
 * losing typed source is treated as data loss, not an inconvenience.
 */
data class SolutionDraft(
    val problemId: ProblemId,
    /** The revision this draft was started from, to detect content updates. */
    val problemRevisionId: ProblemRevisionId,
    val source: String,
    /**
     * The starter the draft began from. Retained so "reset to starter" works even
     * after the Problem's content is updated underneath the learner.
     */
    val starterBaseline: String,
    val version: Long,
    val updatedAt: Instant,
) {
    init {
        require(version >= 0) { "version must not be negative" }
    }

    /** True when the learner has not yet changed anything. */
    val isPristine: Boolean get() = source == starterBaseline
}

/**
 * A local date derived from an instant in a stated zone.
 *
 * Streaks and the 5am Club are defined in *local* dates, but events are stored
 * as UTC instants. Every conversion must therefore name the zone it used, and
 * this function is the only place that conversion happens — so a timezone bug
 * has exactly one home.
 */
fun Instant.localDateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date

/** The local hour, used by the 5am Club's `[00:00, 06:00)` window. */
fun Instant.localHourIn(zone: TimeZone): Int = toLocalDateTime(zone).hour
