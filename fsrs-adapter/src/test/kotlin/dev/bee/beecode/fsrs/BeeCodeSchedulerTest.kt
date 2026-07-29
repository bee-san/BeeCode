package dev.bee.beecode.fsrs

import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val PROBLEM = ProblemId("two-sum")
private val T0 = Instant.parse("2026-07-29T12:00:00Z")

class BeeCodeSchedulerTest {
    private val scheduler = BeeCodeScheduler()

    @Test
    fun aFirstReviewCreatesStateWithNoPreviousValues() {
        val t = scheduler.schedule(PROBLEM, previous = null, rating = ReviewRating.GOOD, reviewedAt = T0)

        assertEquals(1, t.schedule.reviewCount)
        assertEquals(0, t.schedule.lapseCount)
        assertEquals(1L, t.schedule.version)
        assertNull(t.previousVersion)
        assertTrue(t.record.isFirstReview)
        assertNull(t.record.previousStability)
        assertNull(t.record.previousDifficulty)
        assertEquals(0, t.record.elapsedDays)
        assertEquals(BeeCodeScheduler.NO_PREVIOUS_STATE_HASH, t.record.previousStateHash)
        // Nothing was retained, so nothing could be retrieved.
        assertEquals(0.0, t.record.retrievability)
    }

    @Test
    fun theDueDateIsTheIntervalAfterTheReview() {
        val t = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0)
        val expected = Instant.fromEpochSeconds(T0.epochSeconds + t.schedule.intervalDays * 86_400L)
        assertEquals(expected, t.schedule.dueAt)
        assertEquals(t.schedule.dueAt, t.record.dueAt)
    }

    @Test
    fun betterRatingsProduceLongerIntervals() {
        // The central promise of the scheduler. If this ordering ever breaks, the
        // rating buttons are lying to the learner.
        val again = scheduler.schedule(PROBLEM, null, ReviewRating.AGAIN, T0).schedule
        val hard = scheduler.schedule(PROBLEM, null, ReviewRating.HARD, T0).schedule
        val good = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val easy = scheduler.schedule(PROBLEM, null, ReviewRating.EASY, T0).schedule

        assertTrue(again.intervalDays <= hard.intervalDays, "again ${again.intervalDays} <= hard ${hard.intervalDays}")
        assertTrue(hard.intervalDays <= good.intervalDays, "hard ${hard.intervalDays} <= good ${good.intervalDays}")
        assertTrue(good.intervalDays < easy.intervalDays, "good ${good.intervalDays} < easy ${easy.intervalDays}")
    }

    @Test
    fun anAgainRatingIncrementsTheLapseCount() {
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        assertEquals(0, first.lapseCount)

        val lapsed = scheduler.schedule(PROBLEM, first, ReviewRating.AGAIN, T0.plusDays(10)).schedule
        assertEquals(1, lapsed.lapseCount)

        // Lapses accumulate rather than reset, so a leech stays visible.
        val recovered = scheduler.schedule(PROBLEM, lapsed, ReviewRating.GOOD, T0.plusDays(11)).schedule
        assertEquals(1, recovered.lapseCount)
    }

    @Test
    fun versionIncrementsAndCarriesThePreviousValueForCompareAndSwap() {
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val second = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusDays(3))

        assertEquals(2L, second.schedule.version)
        assertEquals(1L, second.previousVersion)
        assertEquals(2, second.schedule.reviewCount)
    }

    @Test
    fun aRepeatReviewRecordsThePreviousStateItTransitionedFrom() {
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val second = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusDays(5))

        assertEquals(first.stability, second.record.previousStability)
        assertEquals(first.difficulty, second.record.previousDifficulty)
        assertEquals(5, second.record.elapsedDays)
        assertNotEquals(BeeCodeScheduler.NO_PREVIOUS_STATE_HASH, second.record.previousStateHash)
        assertTrue(second.record.retrievability > 0.0)
    }

    @Test
    fun everyTransitionRecordsTheIdentityOfTheMathematics() {
        // This audit is what lets a future BeeCode rebuild a due date it did not
        // compute, without the historical engine binary present.
        val record = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).record

        assertEquals("FSRS-6.x 21-parameter snapshot", record.algorithmId)
        assertEquals("bee-fsrs-0.1.0", record.engineVersion)
        assertEquals(16, record.parametersHash.length)
        assertEquals(0.9, record.desiredRetention)
        assertEquals(36_500, record.maximumIntervalDays)
        assertEquals(3, record.ratingValue, "GOOD must map to the engine's rating 3")
    }

    @Test
    fun differentParametersProduceDifferentHashes() {
        // A silent parameter change rewrites every learner's future schedule, so
        // the recorded hash must actually distinguish them.
        val tweaked = FsrsDefaults.parameters().copyOf().also { it[0] = it[0] + 0.5 }
        val other = BeeCodeScheduler(SchedulerPolicy(parameters = tweaked))

        assertNotEquals(
            scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).record.parametersHash,
            other.schedule(PROBLEM, null, ReviewRating.GOOD, T0).record.parametersHash,
        )
    }

    @Test
    fun aReviewDoneEarlyElapsesZeroDays() {
        // 23 hours is not a day. Rounding up would inflate stability for reviews
        // the learner did ahead of schedule.
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val early = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusHours(23))
        assertEquals(0, early.record.elapsedDays)

        val justOver = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusHours(25))
        assertEquals(1, justOver.record.elapsedDays)
    }

    @Test
    fun aBackwardsClockDoesNotThrow() {
        // NTP corrections, timezone-naive edits, and restored backups all move a
        // clock backwards. The engine rejects negative elapsed days, so the
        // adapter clamps rather than crashing mid-finalization.
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val backwards = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusDays(-5))
        assertEquals(0, backwards.record.elapsedDays)
        assertTrue(backwards.schedule.intervalDays >= 1)
    }

    @Test
    fun intervalsAreCappedByPolicy() {
        val capped = BeeCodeScheduler(SchedulerPolicy(maximumIntervalDays = 7))
        var schedule = capped.schedule(PROBLEM, null, ReviewRating.EASY, T0).schedule
        var at = T0
        repeat(20) {
            at = at.plusDays(schedule.intervalDays)
            schedule = capped.schedule(PROBLEM, schedule, ReviewRating.EASY, at).schedule
            assertTrue(schedule.intervalDays <= 7, "interval ${schedule.intervalDays} exceeded the cap")
        }
    }

    @Test
    fun lowerRetentionProducesLongerIntervals() {
        // Lower target retention means accepting more forgetting for less work.
        val relaxed = BeeCodeScheduler(SchedulerPolicy(desiredRetention = 0.8))
        val strict = BeeCodeScheduler(SchedulerPolicy(desiredRetention = 0.95))

        val relaxedInterval = relaxed.schedule(PROBLEM, null, ReviewRating.EASY, T0).schedule.intervalDays
        val strictInterval = strict.schedule(PROBLEM, null, ReviewRating.EASY, T0).schedule.intervalDays
        assertTrue(relaxedInterval > strictInterval, "relaxed $relaxedInterval > strict $strictInterval")
    }

    @Test
    fun replayEqualsIncrementalScheduling() {
        // The property that makes sync-merge recomputation trustworthy: folding
        // the append-only log must reach exactly the state incremental
        // scheduling did.
        val ratings = listOf(
            ReviewRating.GOOD,
            ReviewRating.AGAIN,
            ReviewRating.HARD,
            ReviewRating.GOOD,
            ReviewRating.EASY,
            ReviewRating.GOOD,
        )

        var incremental = scheduler.schedule(PROBLEM, null, ratings[0], T0).schedule
        val history = mutableListOf(ReplayEntry(ratings[0], T0))
        var at = T0
        for (rating in ratings.drop(1)) {
            at = at.plusDays(incremental.intervalDays)
            incremental = scheduler.schedule(PROBLEM, incremental, rating, at).schedule
            history += ReplayEntry(rating, at)
        }

        assertEquals(incremental, scheduler.replay(PROBLEM, history))
    }

    @Test
    fun replayIsOrderIndependentOfInputListOrder() {
        // A merged sync payload arrives in arbitrary order. Replay sorts by time,
        // so a shuffled union must still produce the same schedule.
        val history = listOf(
            ReplayEntry(ReviewRating.GOOD, T0),
            ReplayEntry(ReviewRating.AGAIN, T0.plusDays(2)),
            ReplayEntry(ReviewRating.GOOD, T0.plusDays(3)),
            ReplayEntry(ReviewRating.EASY, T0.plusDays(9)),
        )
        assertEquals(scheduler.replay(PROBLEM, history), scheduler.replay(PROBLEM, history.reversed()))
    }

    @Test
    fun replayOfAnEmptyHistoryIsNull() {
        assertNull(scheduler.replay(PROBLEM, emptyList()))
    }

    @Test
    fun scheduleInstantsCarryNoSubMillisecondPrecision() {
        // Persistence stores epoch milliseconds. A schedule carrying nanoseconds
        // would not survive a round trip: the due date shown before a restart would
        // differ from the one after it, and comparing against a reloaded schedule
        // would fail with no visible cause.
        val precise = Instant.fromEpochSeconds(T0.epochSeconds, nanosecondAdjustment = 123_456_789)
        val transition = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, precise)

        for ((label, instant) in listOf(
            "dueAt" to transition.schedule.dueAt,
            "lastReviewedAt" to transition.schedule.lastReviewedAt,
            "updatedAt" to transition.schedule.updatedAt,
            "record.dueAt" to transition.record.dueAt,
        )) {
            assertEquals(
                instant,
                Instant.fromEpochMilliseconds(instant.toEpochMilliseconds()),
                "$label must be millisecond-precise so it survives storage",
            )
        }
    }

    @Test
    fun schedulingIsDeterministic() {
        // No clock, no randomness: the same inputs must always give the same
        // schedule, or the audit record means nothing.
        val a = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0)
        val b = BeeCodeScheduler().schedule(PROBLEM, null, ReviewRating.GOOD, T0)
        assertEquals(a.schedule, b.schedule)
        assertEquals(a.record, b.record)
    }

    @Test
    fun aLongHistoryKeepsStateWithinDomainBounds() {
        // ProblemSchedule enforces difficulty in [1, 10] and positive stability.
        // Constructing it at all is the assertion; this proves a long realistic
        // history never leaves those bounds.
        var schedule = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        var at = T0
        val cycle = listOf(ReviewRating.GOOD, ReviewRating.AGAIN, ReviewRating.HARD, ReviewRating.EASY)
        repeat(200) { i ->
            at = at.plusDays(schedule.intervalDays)
            schedule = scheduler.schedule(PROBLEM, schedule, cycle[i % cycle.size], at).schedule
            assertTrue(schedule.stability > 0.0)
            assertTrue(schedule.difficulty in 1.0..10.0)
            assertTrue(schedule.intervalDays >= 1)
        }
        assertEquals(201, schedule.reviewCount)
    }

    @Test
    fun policyEqualityComparesParameterContents() {
        // The generated data-class equals would compare DoubleArray identity,
        // making the policy useless as a cache key.
        val a = SchedulerPolicy(parameters = FsrsDefaults.parameters())
        val b = SchedulerPolicy(parameters = FsrsDefaults.parameters())
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun anInvalidPolicyIsRejectedAtConstruction() {
        for (retention in listOf(0.0, 1.0, -0.5, 1.5, Double.NaN)) {
            runCatching { SchedulerPolicy(desiredRetention = retention) }
                .onSuccess { error("desiredRetention $retention must be rejected") }
        }
        runCatching { SchedulerPolicy(maximumIntervalDays = 0) }
            .onSuccess { error("maximumIntervalDays 0 must be rejected") }
        runCatching { SchedulerPolicy(parameters = DoubleArray(3)) }
            .onSuccess { error("a 3-value parameter set must be rejected") }
    }
}

private fun Instant.plusDays(days: Int): Instant =
    Instant.fromEpochSeconds(epochSeconds + days.toLong() * 86_400L)

private fun Instant.plusHours(hours: Int): Instant =
    Instant.fromEpochSeconds(epochSeconds + hours.toLong() * 3_600L)
