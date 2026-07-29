package dev.bee.beecode.fsrs

import dev.bee.beecode.domain.FsrsTransitionRecord
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemSchedule
import dev.bee.beecode.domain.ReviewRating
import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsEngine
import dev.bee.fsrs.FsrsMemoryState
import dev.bee.fsrs.FsrsParameters
import dev.bee.fsrs.FsrsRating
import kotlinx.datetime.Instant
import kotlin.math.floor
import kotlin.math.roundToLong

/**
 * The single boundary between BeeCode and FSRS memory mathematics.
 *
 * The engine is pure: given a previous state, an elapsed day count, and a
 * rating, it returns the next state. It has no clock and no storage. This
 * adapter supplies everything the engine refuses to know:
 *
 * - converting instants into the whole elapsed days FSRS expects;
 * - translating BeeCode's [ReviewRating] into the engine's rating;
 * - turning an interval into an absolute due instant;
 * - recording a complete audit of the transition.
 *
 * It is deliberately *not* a service with state. Every call takes its inputs and
 * returns a value, so a schedule computed inside a write transaction is
 * reproducible outside one.
 */
class BeeCodeScheduler(
    private val policy: SchedulerPolicy = SchedulerPolicy.DEFAULT,
) {
    private val parameters: FsrsParameters =
        policy.parameters?.let { FsrsParameters.of(it) } ?: FsrsParameters.latestDefault()

    private val engine: FsrsEngine = FsrsEngine.create(parameters)

    /** Hash of the parameter set in use, recorded on every transition. */
    private val parametersHash: String = hashDoubles(parameters.toArray())

    /**
     * Compute the next schedule for a Problem.
     *
     * @param previous the authoritative current schedule, or null for a first
     *   review. The caller must read this inside the same transaction that
     *   commits the result, or a concurrent review could be lost.
     * @param reviewedAt when the review was finalized. Also the origin the next
     *   due date is measured from.
     */
    fun schedule(
        problemId: ProblemId,
        previous: ProblemSchedule?,
        rating: ReviewRating,
        reviewedAt: Instant,
    ): ScheduleTransition {
        val engineRating = rating.toEngineRating()

        // Elapsed days floor, not round: a Problem reviewed 23 hours early has
        // elapsed 0 days, and crediting it with 1 would inflate stability for
        // reviews the learner did ahead of schedule.
        val elapsedDays = previous
            ?.let { elapsedDaysBetween(it.lastReviewedAt, reviewedAt) }
            ?: 0

        val previousMemory = previous?.let { FsrsMemoryState(it.stability, it.difficulty) }

        val nextMemory: FsrsMemoryState
        val retrievability: Double
        if (previousMemory == null) {
            nextMemory = engine.initialState(engineRating)
            // A Problem never seen before has no retained memory to retrieve.
            retrievability = 0.0
        } else {
            retrievability = engine.retrievability(previousMemory, elapsedDays)
            nextMemory = engine.nextState(previousMemory, engineRating, elapsedDays)
        }

        val intervalDays = engine.nextIntervalDays(
            nextMemory.stability,
            policy.desiredRetention,
            policy.maximumIntervalDays,
        )

        val dueAt = reviewedAt.plusDays(intervalDays)

        val record = FsrsTransitionRecord(
            algorithmId = FsrsAlgorithmInfo.ALGORITHM_LABEL,
            engineVersion = ENGINE_VERSION,
            parametersHash = parametersHash,
            previousStateHash = hashState(previousMemory),
            previousStability = previousMemory?.stability,
            previousDifficulty = previousMemory?.difficulty,
            elapsedDays = elapsedDays,
            ratingValue = engineRating.value(),
            desiredRetention = policy.desiredRetention,
            maximumIntervalDays = policy.maximumIntervalDays,
            nextStability = nextMemory.stability,
            nextDifficulty = nextMemory.difficulty,
            nextIntervalDays = intervalDays,
            retrievability = retrievability,
            dueAt = dueAt,
        )

        val schedule = ProblemSchedule(
            problemId = problemId,
            stability = nextMemory.stability,
            difficulty = nextMemory.difficulty,
            dueAt = dueAt,
            lastReviewedAt = reviewedAt,
            intervalDays = intervalDays,
            reviewCount = (previous?.reviewCount ?: 0) + 1,
            // A lapse counter that only ever increases is the honest one: it
            // records how often this Problem has been forgotten, which is what
            // makes a leech visible. Resetting it on success would erase that.
            lapseCount = (previous?.lapseCount ?: 0) + if (rating == ReviewRating.AGAIN) 1 else 0,
            // Optimistic-concurrency counter. The caller commits only if the
            // stored version still equals previous.version.
            version = (previous?.version ?: 0) + 1,
            updatedAt = reviewedAt,
        )

        return ScheduleTransition(schedule, record, previousVersion = previous?.version)
    }

    /**
     * Recompute the schedule by folding a review history from scratch.
     *
     * Used to verify that incremental scheduling and a full replay agree, and to
     * rebuild after a sync merge (ADR 0002): because the review log is
     * append-only, merging two devices' histories is a set union, and replaying
     * the union is more obviously correct than picking a winning schedule row by
     * timestamp.
     *
     * @param history reviews for one Problem, oldest first.
     */
    fun replay(problemId: ProblemId, history: List<ReplayEntry>): ProblemSchedule? {
        var schedule: ProblemSchedule? = null
        for (entry in history.sortedBy { it.reviewedAt }) {
            schedule = schedule(problemId, schedule, entry.rating, entry.reviewedAt).schedule
        }
        return schedule
    }

    private fun ReviewRating.toEngineRating(): FsrsRating = when (this) {
        ReviewRating.AGAIN -> FsrsRating.AGAIN
        ReviewRating.HARD -> FsrsRating.HARD
        ReviewRating.GOOD -> FsrsRating.GOOD
        ReviewRating.EASY -> FsrsRating.EASY
    }

    companion object {
        /**
         * Version of the pinned bee-fsrs artifact, recorded in every transition.
         *
         * Hand-maintained rather than read from the jar manifest, because a
         * vendored module has no published version to read and a wrong-but-quiet
         * value is worse than an explicit constant.
         */
        const val ENGINE_VERSION: String = "bee-fsrs-0.1.0"

        /** Whole days elapsed, truncated toward zero. */
        internal fun elapsedDaysBetween(from: Instant, to: Instant): Int {
            val seconds = to.epochSeconds - from.epochSeconds
            // Clocks move backwards: NTP corrections, timezone-naive edits,
            // restored backups. A negative elapsed count would make the engine
            // throw, so it is clamped to 0 — treated as a same-day review.
            if (seconds <= 0) return 0
            val days = floor(seconds.toDouble() / SECONDS_PER_DAY)
            return days.coerceAtMost(Int.MAX_VALUE.toDouble()).roundToLong().toInt()
        }

        private const val SECONDS_PER_DAY = 86_400L

        private fun Instant.plusDays(days: Int): Instant =
            Instant.fromEpochSeconds(epochSeconds + days.toLong() * SECONDS_PER_DAY, nanosecondsOfSecond)

        /**
         * A short stable digest used to detect a broken fold, not for security.
         *
         * Formatting the doubles first means the hash is stable across platforms
         * in a way raw bit patterns of computed doubles are not guaranteed to be.
         */
        private fun hashState(state: FsrsMemoryState?): String =
            if (state == null) NO_PREVIOUS_STATE_HASH else hashDoubles(doubleArrayOf(state.stability, state.difficulty))

        internal const val NO_PREVIOUS_STATE_HASH: String = "none"

        private fun hashDoubles(values: DoubleArray): String {
            val text = values.joinToString(",") { formatForHash(it) }
            // FNV-1a, 64-bit. Cheap, dependency-free, and adequate for spotting
            // an accidental mismatch. Not a cryptographic commitment.
            var hash = -0x340d631b7bdddcdbL
            for (byte in text.encodeToByteArray()) {
                hash = hash xor (byte.toLong() and 0xff)
                hash *= 0x100000001b3L
            }
            return hash.toULong().toString(16).padStart(16, '0')
        }

        private fun formatForHash(value: Double): String {
            // Round to a fixed precision so a difference far below the engine's
            // own tolerance does not change the hash.
            val scaled = (value * HASH_PRECISION).roundToLong()
            return scaled.toString()
        }

        private const val HASH_PRECISION = 1_000_000_000.0
    }
}

/**
 * The full result of one scheduling decision.
 *
 * [previousVersion] is carried so the persistence layer can perform its
 * compare-and-swap without re-reading, and so a stale session is rejected rather
 * than silently overwriting a schedule another review already advanced.
 */
data class ScheduleTransition(
    val schedule: ProblemSchedule,
    val record: FsrsTransitionRecord,
    val previousVersion: Long?,
)

/** One historical review, reduced to what a replay needs. */
data class ReplayEntry(
    val rating: ReviewRating,
    val reviewedAt: Instant,
)
