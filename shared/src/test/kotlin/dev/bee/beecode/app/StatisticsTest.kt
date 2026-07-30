package dev.bee.beecode.app

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.DomainEventId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.FsrsTransitionRecord
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemExample
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ProblemSchedule
import dev.bee.beecode.domain.ProblemTest
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSessionId
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The forward-looking statistics.
 *
 * There was no test for [Statistics] at all, which is how `dueTomorrow` shipped
 * measuring the wrong quantity: it computed `dueAt - lastReviewedAt`, the length of
 * the whole interval, and added that to *today*. Nothing displayed the number and
 * nothing asserted it, so a mature Problem genuinely due tomorrow was projected its
 * whole interval into the future and never counted, while a Problem on a one-day
 * interval was counted as due tomorrow whenever it had just been reviewed —
 * regardless of when it was actually due.
 *
 * These assert against `now`, which is the only reference that can answer "tomorrow".
 */
class StatisticsTest {

    private val today = LocalDate(2026, 7, 30)

    /** 2026-07-30T12:00:00Z — midday, so a ±12h shift stays inside the day. */
    private val now = Instant.parse("2026-07-30T12:00:00Z")

    /**
     * A schedule due [dueInDays] from [now] that was last reviewed
     * [intervalDays] before that — i.e. a mature Problem partway through a long
     * interval, which is the case the old arithmetic got wrong.
     */
    private fun schedule(
        id: String,
        dueInDays: Double,
        intervalDays: Double,
    ): ProblemSchedule {
        val dueAt = now.plus(kotlin.time.Duration.parse("${dueInDays * 24}h"))
        return ProblemSchedule(
            problemId = ProblemId(id),
            stability = 10.0,
            difficulty = 5.0,
            dueAt = dueAt,
            lastReviewedAt = dueAt.minus(kotlin.time.Duration.parse("${intervalDays * 24}h")),
            intervalDays = intervalDays,
            reviewCount = 3,
            lapseCount = 0,
            version = 1,
            updatedAt = now,
        )
    }

    private fun statisticsFor(vararg schedules: ProblemSchedule) = Statistics.compute(
        reviews = emptyList(),
        schedules = schedules.associateBy { it.problemId },
        problems = emptyList(),
        today = today,
        now = now,
    )

    @Test
    fun aMatureProblemDueTomorrowIsCountedAsDueTomorrow() {
        // Reviewed 20 days ago on a 21-day interval, so due in one day. The old code
        // projected 21 days from today and counted nothing.
        val stats = statisticsFor(schedule("mature", dueInDays = 1.0, intervalDays = 21.0))
        assertEquals(1, stats.dueTomorrow)
    }

    @Test
    fun aProblemDueTodayIsNotCountedAsDueTomorrow() {
        // Already overdue: it belongs to dueNow, and counting it twice would overstate
        // the day ahead.
        val stats = statisticsFor(schedule("overdue", dueInDays = -2.0, intervalDays = 7.0))
        assertEquals(0, stats.dueTomorrow)
        assertEquals(1, stats.dueNow)
    }

    @Test
    fun aProblemDueNextWeekIsNotCountedAsDueTomorrow() {
        val stats = statisticsFor(schedule("later", dueInDays = 7.0, intervalDays = 30.0))
        assertEquals(0, stats.dueTomorrow)
        assertEquals(0, stats.dueNow)
    }

    @Test
    fun aFreshlyReviewedProblemOnAShortIntervalIsNotDueTomorrowUnlessItIs() {
        // The false positive the old arithmetic produced: a one-day interval reviewed
        // just now is due in one day here, so it *is* due tomorrow — but the same
        // interval reviewed 20 hours ago is due in 4 hours, which is today.
        val dueSoon = schedule("soon", dueInDays = 0.17, intervalDays = 1.0)
        assertEquals(0, statisticsFor(dueSoon).dueTomorrow)
    }

    @Test
    fun dueTomorrowCountsOnlyTheProblemsThatFallOnTomorrowsDate() {
        val stats = statisticsFor(
            schedule("a", dueInDays = 1.0, intervalDays = 15.0),
            schedule("b", dueInDays = 1.2, intervalDays = 40.0),
            schedule("c", dueInDays = 3.0, intervalDays = 4.0),
            schedule("d", dueInDays = -1.0, intervalDays = 2.0),
        )
        assertEquals(2, stats.dueTomorrow)
        assertEquals(1, stats.dueNow)
    }

    @Test
    fun periodWindowsAreInclusiveAndThePreviousWindowIsAdjacent() {
        val reviews = listOf(
            review(today, "today"),
            review(today.minus(DatePeriod(days = 6)), "current-start"),
            review(today.minus(DatePeriod(days = 7)), "previous-end"),
            review(today.minus(DatePeriod(days = 13)), "previous-start"),
            review(today.minus(DatePeriod(days = 14)), "too-old"),
            review(today.plus(DatePeriod(days = 1)), "future"),
        )

        val comparison = Statistics.periodComparison(reviews, today, StatisticsPeriod.SEVEN_DAYS)

        assertEquals(today.minus(DatePeriod(days = 6)), comparison.current.startDate)
        assertEquals(today, comparison.current.endDate)
        assertEquals(2, comparison.current.reviews)
        assertEquals(today.minus(DatePeriod(days = 13)), comparison.previous.startDate)
        assertEquals(today.minus(DatePeriod(days = 7)), comparison.previous.endDate)
        assertEquals(2, comparison.previous.reviews)
    }

    @Test
    fun successRateUsesAllReviewsAndActiveDaysAreDeduplicated() {
        val reviews = listOf(
            review(today, "again", rating = ReviewRating.AGAIN),
            review(today, "good", rating = ReviewRating.GOOD),
            review(today.minus(DatePeriod(days = 2)), "hard", rating = ReviewRating.HARD),
        )

        val metrics = Statistics.periodComparison(
            reviews,
            today,
            StatisticsPeriod.SEVEN_DAYS,
        ).current

        assertEquals(3, metrics.reviews)
        assertEquals(2, metrics.successfulReviews)
        assertEquals(2.0 / 3.0, metrics.successRate)
        assertEquals(2, metrics.activeDays)
    }

    @Test
    fun comparisonsReportAbsoluteAndPercentagePointChanges() {
        val reviews = listOf(
            review(today, "current-success"),
            review(today.minus(DatePeriod(days = 1)), "current-lapse", ReviewRating.AGAIN),
            review(today.minus(DatePeriod(days = 7)), "previous-success"),
        )

        val comparison = Statistics.periodComparison(
            reviews,
            today,
            StatisticsPeriod.SEVEN_DAYS,
        )

        assertEquals(1, comparison.reviewChange)
        assertEquals(0, comparison.successfulReviewChange)
        assertEquals(1, comparison.activeDayChange)
        assertEquals(-50.0, comparison.successRatePercentagePointChange)
        assertTrue(comparison.hasEarlierActivity)
    }

    @Test
    fun aWindowWithoutEarlierActivityHasNoRateComparison() {
        val comparison = Statistics.periodComparison(
            listOf(review(today, "only")),
            today,
            StatisticsPeriod.SEVEN_DAYS,
        )

        assertFalse(comparison.hasEarlierActivity)
        assertEquals(1, comparison.reviewChange)
        assertNull(comparison.successRatePercentagePointChange)
    }

    @Test
    fun dailyActivityPreservesZeroDaysAndSuccessCounts() {
        val buckets = Statistics.activityBuckets(
            listOf(
                review(today, "success"),
                review(today, "lapse", ReviewRating.AGAIN),
            ),
            today,
            StatisticsPeriod.SEVEN_DAYS,
        )

        assertEquals(7, buckets.size)
        assertEquals(today.minus(DatePeriod(days = 6)), buckets.first().startDate)
        assertEquals(today, buckets.last().endDate)
        assertTrue(buckets.dropLast(1).all { it.reviews == 0 && it.successfulReviews == 0 })
        assertEquals(2, buckets.last().reviews)
        assertEquals(1, buckets.last().successfulReviews)
    }

    @Test
    fun ninetyDaysUseThirteenContiguousBucketsWithoutAddingADay() {
        val reviews = listOf(
            review(today.minus(DatePeriod(days = 90)), "outside"),
            review(today.minus(DatePeriod(days = 89)), "oldest-start"),
            review(today.minus(DatePeriod(days = 84)), "oldest-end"),
            review(today.minus(DatePeriod(days = 83)), "second-start"),
            review(today.minus(DatePeriod(days = 6)), "recent-start"),
            review(today, "recent-end"),
        )

        val buckets = Statistics.activityBuckets(reviews, today, StatisticsPeriod.NINETY_DAYS)

        assertEquals(13, buckets.size)
        assertEquals(today.minus(DatePeriod(days = 89)), buckets.first().startDate)
        assertEquals(today.minus(DatePeriod(days = 84)), buckets.first().endDate)
        assertEquals(2, buckets.first().reviews)
        assertEquals(today.minus(DatePeriod(days = 83)), buckets[1].startDate)
        assertEquals(today.minus(DatePeriod(days = 77)), buckets[1].endDate)
        assertEquals(1, buckets[1].reviews)
        assertEquals(today.minus(DatePeriod(days = 6)), buckets.last().startDate)
        assertEquals(today, buckets.last().endDate)
        assertEquals(2, buckets.last().reviews)
        assertTrue(buckets.drop(2).dropLast(1).all { it.reviews == 0 })
    }

    @Test
    fun topicCoverageUsesDistinctCurrentCatalogueProblemsOnSeparateAxes() {
        val arraySearch = problem(
            id = "array-search",
            dataStructures = listOf("Array"),
            algorithms = listOf("Binary search"),
        )
        val arrayScan = problem(
            id = "array-scan",
            dataStructures = listOf("Array"),
            algorithms = listOf("Two pointers"),
        )
        val treeSearch = problem(
            id = "tree-search",
            dataStructures = listOf("Binary tree"),
            algorithms = listOf("Binary search"),
        )
        val reviews = listOf(
            review(today, "solve-1", problemId = arraySearch.id, countsAsSolved = true),
            review(
                today.minus(DatePeriod(days = 1)),
                "solve-1-again",
                problemId = arraySearch.id,
                countsAsSolved = true,
            ),
            review(today, "removed", problemId = ProblemId("removed"), countsAsSolved = true),
        )

        val stats = Statistics.compute(
            reviews = reviews,
            schedules = emptyMap(),
            problems = listOf(treeSearch, arrayScan, arraySearch),
            today = today,
            now = now,
        )

        assertEquals(
            listOf(
                TopicProgress("Array", solved = 1, total = 2),
                TopicProgress("Binary tree", solved = 0, total = 1),
            ),
            stats.dataStructureProgress,
        )
        assertEquals(
            listOf(
                TopicProgress("Binary search", solved = 1, total = 2),
                TopicProgress("Two pointers", solved = 0, total = 1),
            ),
            stats.techniqueProgress,
        )
    }

    @Test
    fun intervalDistributionIncludesEveryBoundaryAndZeroFilledRange() {
        val intervals = listOf(0.999, 1.0, 6.999, 7.0, 29.999, 30.0, 179.999, 180.0)
        val stats = statisticsFor(
            *intervals.mapIndexed { index, interval ->
                schedule("interval-$index", dueInDays = 1.0, intervalDays = interval)
            }.toTypedArray(),
        )

        assertEquals(
            listOf(1, 2, 2, 2, 1),
            stats.intervalDistribution.map { it.count },
        )
        assertTrue(stats.intervalDistribution.all { it.total == intervals.size })

        val empty = statisticsFor().intervalDistribution
        assertEquals(IntervalRange.entries.toList(), empty.map { it.range })
        assertTrue(empty.all { it.count == 0 && it.fraction == 0f })
    }

    private fun review(
        date: LocalDate,
        session: String,
        rating: ReviewRating = ReviewRating.GOOD,
        problemId: ProblemId = ProblemId("two-sum"),
        countsAsSolved: Boolean = rating != ReviewRating.AGAIN,
    ): ProblemReviewFinalized {
        val at = Instant.parse("${date}T12:00:00Z")
        return ProblemReviewFinalized(
            eventId = DomainEventId("event-$session"),
            sessionId = ReviewSessionId(session),
            problemId = problemId,
            problemRevisionId = ProblemRevisionId("a".repeat(64)),
            executionRunId = ExecutionRunId("run-$session"),
            outcome = if (rating == ReviewRating.AGAIN) {
                ExecutionOutcome.FAILED
            } else {
                ExecutionOutcome.PASSED
            },
            rating = rating,
            aided = false,
            countsAsSolved = countsAsSolved,
            finalizedAt = at,
            streakZoneId = "UTC",
            transition = FsrsTransitionRecord(
                algorithmId = "test",
                engineVersion = "test",
                parametersHash = "test",
                previousStateHash = "none",
                previousStability = null,
                previousDifficulty = null,
                elapsedDays = 0.0,
                ratingValue = rating.ordinal + 1,
                desiredRetention = 0.9,
                maximumIntervalDays = 36_500.0,
                nextStability = 1.0,
                nextDifficulty = 5.0,
                nextIntervalDays = 1.0,
                retrievability = 0.0,
                dueAt = at,
            ),
            deviceId = DeviceId("device"),
        )
    }

    private fun problem(
        id: String,
        dataStructures: List<String>,
        algorithms: List<String>,
    ): ProblemDefinition = ProblemDefinition(
        id = ProblemId(id),
        revisionId = ProblemRevisionId("a".repeat(64)),
        title = id,
        difficulty = ProblemDifficulty.EASY,
        topics = (dataStructures + algorithms).distinct(),
        dataStructures = dataStructures,
        algorithms = algorithms,
        statementMarkdown = "Statement",
        starterSource = "def solve():\n    pass\n",
        entryPoint = "solve",
        examples = listOf(ProblemExample(input = "[]", output = "0", explanation = null)),
        tests = listOf(
            ProblemTest(
                name = "example",
                argumentsJson = "[]",
                expectedJson = "0",
                comparatorId = ComparatorId.EXACT,
            ),
        ),
        limits = ExecutionLimits.DEFAULT,
        explanationMarkdown = null,
    )
}
