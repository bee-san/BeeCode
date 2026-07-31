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
private const val TOPIC = "dynamic-programming"
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
        assertEquals(0.0, t.record.elapsedDays)
        assertEquals(BeeCodeScheduler.NO_PREVIOUS_STATE_HASH, t.record.previousStateHash)
        // Nothing was retained, so nothing could be retrieved.
        assertEquals(0.0, t.record.retrievability)
    }

    @Test
    fun theDueDateIsTheIntervalAfterTheReview() {
        val t = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0)
        // Millisecond arithmetic, because a fractional interval does not land on a
        // whole second and the schedule must equal what persistence reads back.
        val expected = Instant.fromEpochMilliseconds(
            T0.toEpochMilliseconds() + Math.round(t.schedule.intervalDays * 86_400_000.0),
        )
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

        val lapsed = scheduler.schedule(PROBLEM, first, ReviewRating.AGAIN, T0.plusDays(10.0)).schedule
        assertEquals(1, lapsed.lapseCount)

        // Lapses accumulate rather than reset, so a leech stays visible.
        val recovered = scheduler.schedule(PROBLEM, lapsed, ReviewRating.GOOD, T0.plusDays(11.0)).schedule
        assertEquals(1, recovered.lapseCount)
    }

    @Test
    fun versionIncrementsAndCarriesThePreviousValueForCompareAndSwap() {
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val second = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusDays(3.0))

        assertEquals(2L, second.schedule.version)
        assertEquals(1L, second.previousVersion)
        assertEquals(2, second.schedule.reviewCount)
    }

    @Test
    fun aRepeatReviewRecordsThePreviousStateItTransitionedFrom() {
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val second = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusDays(5.0))

        assertEquals(first.stability, second.record.previousStability)
        assertEquals(first.difficulty, second.record.previousDifficulty)
        assertEquals(5.0, second.record.elapsedDays)
        assertNotEquals(BeeCodeScheduler.NO_PREVIOUS_STATE_HASH, second.record.previousStateHash)
        assertTrue(second.record.retrievability > 0.0)
    }

    @Test
    fun everyTransitionRecordsTheIdentityOfTheMathematics() {
        // This audit is what lets a future BeeCode rebuild a due date it did not
        // compute, without the historical engine binary present.
        val record = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).record

        assertEquals("FSRS-7 35-parameter snapshot", record.algorithmId)
        assertEquals("bee-fsrs-0.2.0", record.engineVersion)
        assertEquals(16, record.parametersHash.length)
        assertEquals(0.9, record.desiredRetention)
        assertEquals(36_500.0, record.maximumIntervalDays)
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
    fun aReviewDoneEarlyElapsesTheRealFractionOfADay() {
        // Under FSRS-6 this floored: 23 hours elapsed "0 days" and 25 hours elapsed
        // "1 day", so two reviews two hours apart were credited differently while
        // everything within a day was identical. FSRS-7 takes the real duration, and
        // that is the point of the revision rather than an incidental change.
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule

        val early = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusHours(23))
        assertEquals(23.0 / 24.0, early.record.elapsedDays, 1.0e-12)

        val justOver = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusHours(25))
        assertEquals(25.0 / 24.0, justOver.record.elapsedDays, 1.0e-12)

        // A same-day review is a real elapsed duration, not zero.
        val tenMinutes = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusMinutes(10))
        assertEquals(10.0 / 1_440.0, tenMinutes.record.elapsedDays, 1.0e-12)
        assertTrue(tenMinutes.record.elapsedDays > 0.0)
    }

    @Test
    fun aBackwardsClockDoesNotThrow() {
        // NTP corrections, timezone-naive edits, and restored backups all move a
        // clock backwards. The engine rejects negative elapsed days, so the
        // adapter clamps rather than crashing mid-finalization.
        val first = scheduler.schedule(PROBLEM, null, ReviewRating.GOOD, T0).schedule
        val backwards = scheduler.schedule(PROBLEM, first, ReviewRating.GOOD, T0.plusDays(-5.0))
        assertEquals(0.0, backwards.record.elapsedDays)
        assertTrue(backwards.schedule.intervalDays > 0.0)
    }

    @Test
    fun intervalsAreCappedByPolicy() {
        val capped = BeeCodeScheduler(SchedulerPolicy(maximumIntervalDays = 7.0))
        var schedule = capped.schedule(PROBLEM, null, ReviewRating.EASY, T0).schedule
        var at = T0
        repeat(20) {
            at = at.plusDays(schedule.intervalDays)
            schedule = capped.schedule(PROBLEM, schedule, ReviewRating.EASY, at).schedule
            assertTrue(schedule.intervalDays <= 7.0, "interval ${schedule.intervalDays} exceeded the cap")
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
            ReplayEntry(ReviewRating.AGAIN, T0.plusDays(2.0)),
            ReplayEntry(ReviewRating.GOOD, T0.plusDays(3.0)),
            ReplayEntry(ReviewRating.EASY, T0.plusDays(9.0)),
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
            assertTrue(schedule.intervalDays > 0.0)
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
        runCatching { SchedulerPolicy(maximumIntervalDays = 0.0) }
            .onSuccess { error("maximumIntervalDays 0 must be rejected") }
        runCatching { SchedulerPolicy(parameters = DoubleArray(3)) }
            .onSuccess { error("a 3-value parameter set must be rejected") }
    }

    // ---- Topic cards -----------------------------------------------------
    //
    // A topic is scheduled by the same mathematics as a Problem, and these tests
    // exist to keep it that way. The two paths share a private core precisely so a
    // rounding rule cannot be fixed in one and missed in the other; if that core is
    // ever forked, `topicSchedulingIsTheSameMathematicsAsProblemScheduling` is the
    // test that notices.

    @Test
    fun topicSchedulingIsTheSameMathematicsAsProblemScheduling() {
        val ratings = listOf(ReviewRating.GOOD, ReviewRating.AGAIN, ReviewRating.HARD, ReviewRating.EASY)

        var problem = scheduler.schedule(PROBLEM, null, ratings[0], T0).schedule
        var topic = scheduler.scheduleTopic(TOPIC, null, ratings[0], T0).schedule
        assertScheduleStatesAgree(problem, topic)

        for ((index, rating) in ratings.drop(1).withIndex()) {
            val at = T0.plusDays(3.0 * (index + 1))
            problem = scheduler.schedule(PROBLEM, problem, rating, at).schedule
            topic = scheduler.scheduleTopic(TOPIC, topic, rating, at).schedule
            assertScheduleStatesAgree(problem, topic)
        }
    }

    @Test
    fun aFirstTopicReviewCreatesStateWithNoPreviousValues() {
        val t = scheduler.scheduleTopic(TOPIC, previous = null, rating = ReviewRating.GOOD, reviewedAt = T0)

        assertEquals(TOPIC, t.schedule.topic)
        assertEquals(1, t.schedule.reviewCount)
        assertEquals(1L, t.schedule.version)
        assertNull(t.previousVersion)
        assertTrue(t.record.isFirstReview)
        assertEquals(0.0, t.record.retrievability)
    }

    @Test
    fun replayingATopicReproducesIncrementalScheduling() {
        // The guarantee the whole rebuild path rests on: a topic's state can be
        // recovered from the review log, so it never has to be merged (ADR 0002).
        val history = listOf(
            ReplayEntry(ReviewRating.GOOD, T0),
            ReplayEntry(ReviewRating.AGAIN, T0.plusDays(4.0)),
            ReplayEntry(ReviewRating.GOOD, T0.plusDays(5.0)),
            ReplayEntry(ReviewRating.EASY, T0.plusDays(12.0)),
        )

        var incremental: dev.bee.beecode.domain.TopicSchedule? = null
        for (entry in history) {
            incremental = scheduler.scheduleTopic(TOPIC, incremental, entry.rating, entry.reviewedAt).schedule
        }

        assertEquals(incremental, scheduler.replayTopic(TOPIC, history))
    }

    @Test
    fun replayingATopicSortsOutOfOrderHistory() {
        // Reviews arrive interleaved from several Problems, and after a sync merge
        // they arrive in whatever order the union produced.
        val ordered = listOf(
            ReplayEntry(ReviewRating.GOOD, T0),
            ReplayEntry(ReviewRating.HARD, T0.plusDays(6.0)),
            ReplayEntry(ReviewRating.GOOD, T0.plusDays(9.0)),
        )
        assertEquals(
            scheduler.replayTopic(TOPIC, ordered),
            scheduler.replayTopic(TOPIC, ordered.reversed()),
        )
    }

    @Test
    fun replayingNoTopicHistoryLeavesNoSchedule() {
        assertNull(scheduler.replayTopic(TOPIC, emptyList()))
    }

    @Test
    fun forgettingATopicRepeatedlyShortensItsInterval() {
        // The product claim in one test: if a learner keeps forgetting dynamic
        // programming, dynamic programming must come back sooner. Nothing else in
        // this change has to decide that — FSRS does, once the card is the topic.
        val remembered = scheduler.replayTopic(
            TOPIC,
            (0..4).map { ReplayEntry(ReviewRating.GOOD, T0.plusDays(it * 7.0)) },
        )!!
        val forgotten = scheduler.replayTopic(
            TOPIC,
            (0..4).map { ReplayEntry(ReviewRating.AGAIN, T0.plusDays(it * 7.0)) },
        )!!

        assertTrue(
            forgotten.intervalDays < remembered.intervalDays,
            "forgotten ${forgotten.intervalDays} < remembered ${remembered.intervalDays}",
        )
        assertTrue(forgotten.dueAt < remembered.dueAt, "a forgotten topic must fall due sooner")
        assertEquals(5, forgotten.lapseCount)
        assertEquals(0, remembered.lapseCount)
    }

    @Test
    fun aBurstOfTopicReviewsInOneSittingBarelyMovesStability() {
        // Five `arrays` Problems in one sitting all advance the `arrays` card with
        // elapsed ~0. FSRS's own gain term scales with (1 - retrievability), and
        // retrievability is ~1 at zero elapsed, so cramming cannot inflate a topic.
        // This is why fanning one review out to every tag needs no extra rule.
        val single = scheduler.scheduleTopic(TOPIC, null, ReviewRating.GOOD, T0).schedule
        var burst = single
        repeat(4) { burst = scheduler.scheduleTopic(TOPIC, burst, ReviewRating.GOOD, T0).schedule }

        val spaced = scheduler.replayTopic(
            TOPIC,
            (0..4).map { ReplayEntry(ReviewRating.GOOD, T0.plusDays(it * 10.0)) },
        )!!

        assertTrue(
            burst.stability < spaced.stability,
            "burst ${burst.stability} < spaced ${spaced.stability}",
        )
        // The review count still rises: the reviews happened, they just did not buy
        // much memory. Hiding them would misreport the learner's own history.
        assertEquals(5, burst.reviewCount)
    }

    @Test
    fun aTopicCarriesItsOwnElapsedTimeIndependently() {
        // Two topics reviewed on different days must not borrow each other's
        // elapsed time, which is what makes per-topic intervals meaningful.
        val early = scheduler.scheduleTopic("arrays", null, ReviewRating.GOOD, T0).schedule
        val late = scheduler.scheduleTopic("graphs", null, ReviewRating.GOOD, T0.plusDays(30.0)).schedule

        val earlyNext = scheduler.scheduleTopic("arrays", early, ReviewRating.GOOD, T0.plusDays(31.0))
        val lateNext = scheduler.scheduleTopic("graphs", late, ReviewRating.GOOD, T0.plusDays(31.0))

        assertEquals(31.0, earlyNext.record.elapsedDays, 1e-9)
        assertEquals(1.0, lateNext.record.elapsedDays, 1e-9)
    }

    @Test
    fun theDesiredRetentionIsTheOneThePolicyHolds() {
        // Read by the mastery projection as its fallback prior. It must be the same
        // number the scheduler actually schedules toward, or the displayed figure
        // would disagree with the mathematics that produced the intervals.
        assertEquals(SchedulerPolicy.DEFAULT_DESIRED_RETENTION, scheduler.desiredRetention)
        assertEquals(
            0.85,
            BeeCodeScheduler(SchedulerPolicy(desiredRetention = 0.85)).desiredRetention,
        )
    }

    /**
     * Assert a Problem card and a topic card hold identical FSRS state.
     *
     * Compares the memory numbers and the schedule, not the key — the keys differ by
     * design and everything else must not.
     */
    private fun assertScheduleStatesAgree(
        problem: dev.bee.beecode.domain.ProblemSchedule,
        topic: dev.bee.beecode.domain.TopicSchedule,
    ) {
        assertEquals(problem.stability, topic.stability)
        assertEquals(problem.difficulty, topic.difficulty)
        assertEquals(problem.intervalDays, topic.intervalDays)
        assertEquals(problem.dueAt, topic.dueAt)
        assertEquals(problem.lastReviewedAt, topic.lastReviewedAt)
        assertEquals(problem.reviewCount, topic.reviewCount)
        assertEquals(problem.lapseCount, topic.lapseCount)
        assertEquals(problem.version, topic.version)
    }
}

private fun Instant.plusDays(days: Double): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + Math.round(days * 86_400_000.0))

private fun Instant.plusMinutes(minutes: Long): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds() + minutes * 60_000L)

private fun Instant.plusHours(hours: Int): Instant =
    Instant.fromEpochSeconds(epochSeconds + hours.toLong() * 3_600L)
