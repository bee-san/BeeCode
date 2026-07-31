package dev.bee.beecode.app

import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ProblemSchedule
import dev.bee.beecode.domain.ReviewRating
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/**
 * Local study statistics, computed from the review log.
 *
 * All local, all offline, no account. Everything here is a pure fold over reviews
 * and schedules, so the numbers cannot disagree with the history they summarise —
 * which is a real risk with maintained counters and the reason none are used.
 */
object Statistics {

    /**
     * Compute the full statistics view.
     *
     * @param today the learner's local date, supplied rather than read from a clock
     *   so the result is testable and so "today" means the learner's day, not UTC's.
     */
    fun compute(
        reviews: List<ProblemReviewFinalized>,
        schedules: Map<ProblemId, ProblemSchedule>,
        problems: List<ProblemDefinition>,
        today: LocalDate,
        now: Instant,
    ): StudyStatistics {
        val solved = reviews.filter { it.countsAsSolved }
        val solvedProblemIds = solved.map { it.problemId }.toSet()

        val reviewsToday = reviews.count { it.localDate() == today }
        val solvedToday = solved.count { it.localDate() == today }

        // Accuracy over reviews that were actually attempts at recall. A review is
        // either a lapse (Again) or a success; there is no third category, because
        // cancellations and worker failures never become reviews at all.
        val lapses = reviews.count { it.rating == ReviewRating.AGAIN }
        val accuracy = if (reviews.isEmpty()) null else (reviews.size - lapses).toDouble() / reviews.size

        val byDifficulty = ProblemDifficulty.entries.associateWith { difficulty ->
            val inDifficulty = problems.filter { it.difficulty == difficulty }
            DifficultyProgress(
                difficulty = difficulty,
                total = inDifficulty.size,
                solved = inDifficulty.count { it.id in solvedProblemIds },
            )
        }

        val topicCounts = mutableMapOf<String, Int>()
        for (problem in problems.filter { it.id in solvedProblemIds }) {
            for (topic in problem.topics) topicCounts[topic] = (topicCounts[topic] ?: 0) + 1
        }
        val periodComparisons = StatisticsPeriod.entries.associateWith { period ->
            periodComparison(reviews, today, period)
        }
        val activityByPeriod = StatisticsPeriod.entries.associateWith { period ->
            activityBuckets(reviews, today, period)
        }

        return StudyStatistics(
            totalReviews = reviews.size,
            totalSolved = solved.size,
            distinctProblemsSolved = solvedProblemIds.size,
            catalogueProblemsSolved = problems.count { it.id in solvedProblemIds },
            totalProblems = problems.size,
            reviewsToday = reviewsToday,
            solvedToday = solvedToday,
            currentStreakDays = Achievements.currentStreak(reviews, today),
            accuracy = accuracy,
            lapses = lapses,
            dueNow = schedules.values.count { it.isDueAt(now) },
            dueTomorrow = schedules.values.count {
                it.dueAt > now && it.localDueDate(today, now) == today.plus(DatePeriod(days = 1))
            },
            notYetAttempted = problems.count { it.id !in schedules.keys },
            byDifficulty = byDifficulty,
            solvedByTopic = topicCounts.toSortedMap(),
            reviewsPerDay = reviewsPerDay(reviews, today, DEFAULT_HISTORY_DAYS),
            activityCalendar = reviewsPerDay(reviews, today, ACTIVITY_CALENDAR_DAYS),
            periodComparisons = periodComparisons,
            activityByPeriod = activityByPeriod,
            dataStructureProgress = topicProgress(problems, solvedProblemIds) { it.dataStructures },
            techniqueProgress = topicProgress(problems, solvedProblemIds) { it.algorithms },
            intervalDistribution = intervalDistribution(schedules.values),
            averageIntervalDays = schedules.values
                .takeIf { it.isNotEmpty() }
                // Averaged as fractional days, not truncated to whole ones: FSRS-7
                // schedules sub-day intervals, and summing them as Longs would floor
                // every one of them to zero.
                ?.let { s -> s.sumOf { it.intervalDays } / s.size },
            leeches = schedules.values
                .filter { it.lapseCount >= LEECH_LAPSE_THRESHOLD }
                .sortedByDescending { it.lapseCount }
                .map { it.problemId },
        )
    }

    /**
     * Current and immediately preceding metrics for [period].
     *
     * Both windows are inclusive at each end and contain exactly [StatisticsPeriod.days]
     * recorded local dates. Reviews outside those dates, including future-dated rows, do
     * not leak into either side.
     */
    fun periodComparison(
        reviews: List<ProblemReviewFinalized>,
        today: LocalDate,
        period: StatisticsPeriod,
    ): PeriodComparison {
        val currentStart = today.minus(DatePeriod(days = period.days - 1))
        val previousEnd = currentStart.minus(DatePeriod(days = 1))
        val previousStart = previousEnd.minus(DatePeriod(days = period.days - 1))
        return PeriodComparison(
            period = period,
            current = periodMetrics(reviews, currentStart, today),
            previous = periodMetrics(reviews, previousStart, previousEnd),
        )
    }

    /**
     * Activity over [period], oldest first, including buckets with no reviews.
     *
     * Seven- and thirty-day ranges are daily. The ninety-day range is thirteen
     * rolling, non-calendar buckets: the oldest covers six days and the remaining
     * twelve cover seven days each. That keeps the recent bucket a full seven days
     * while representing exactly ninety dates rather than silently adding a 91st.
     */
    fun activityBuckets(
        reviews: List<ProblemReviewFinalized>,
        today: LocalDate,
        period: StatisticsPeriod,
    ): List<ActivityBucket> {
        val start = today.minus(DatePeriod(days = period.days - 1))
        val boundaries = if (period == StatisticsPeriod.NINETY_DAYS) {
            (0 until NINETY_DAY_BUCKET_COUNT).map { index ->
                val end = today.minus(
                    DatePeriod(days = (NINETY_DAY_BUCKET_COUNT - 1 - index) * DAYS_PER_ROLLING_BUCKET),
                )
                maxOf(start, end.minus(DatePeriod(days = DAYS_PER_ROLLING_BUCKET - 1))) to end
            }
        } else {
            (period.days - 1 downTo 0).map { offset ->
                val date = today.minus(DatePeriod(days = offset))
                date to date
            }
        }

        return boundaries.map { (bucketStart, bucketEnd) ->
            val inBucket = reviews.filter { it.localDate() in bucketStart..bucketEnd }
            ActivityBucket(
                startDate = bucketStart,
                endDate = bucketEnd,
                reviews = inBucket.size,
                successfulReviews = inBucket.count { it.rating != ReviewRating.AGAIN },
            )
        }
    }

    /**
     * Reviews per local date over a recent window, oldest first.
     *
     * Includes days with zero reviews so a chart does not silently compress gaps —
     * a week off should look like a week off.
     */
    fun reviewsPerDay(
        reviews: List<ProblemReviewFinalized>,
        today: LocalDate,
        days: Int,
    ): List<DailyActivity> {
        val counts = reviews.groupingBy { it.localDate() }.eachCount()
        val solvedCounts = reviews.filter { it.countsAsSolved }.groupingBy { it.localDate() }.eachCount()
        return (days - 1 downTo 0).map { offset ->
            val date = today.minus(DatePeriod(days = offset))
            DailyActivity(
                date = date,
                reviews = counts[date] ?: 0,
                solved = solvedCounts[date] ?: 0,
            )
        }
    }

    private fun periodMetrics(
        reviews: List<ProblemReviewFinalized>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): PeriodMetrics {
        val inWindow = reviews.filter { it.localDate() in startDate..endDate }
        return PeriodMetrics(
            startDate = startDate,
            endDate = endDate,
            reviews = inWindow.size,
            successfulReviews = inWindow.count { it.rating != ReviewRating.AGAIN },
            activeDays = inWindow.map { it.localDate() }.toSet().size,
        )
    }

    private fun topicProgress(
        problems: List<ProblemDefinition>,
        solvedProblemIds: Set<ProblemId>,
        topics: (ProblemDefinition) -> List<String>,
    ): List<TopicProgress> = problems
        .flatMap(topics)
        .distinct()
        .sorted()
        .map { topic ->
            val carryingTopic = problems.filter { topic in topics(it) }
            TopicProgress(
                topic = topic,
                solved = carryingTopic.count { it.id in solvedProblemIds },
                total = carryingTopic.size,
            )
        }

    private fun intervalDistribution(
        schedules: Collection<ProblemSchedule>,
    ): List<IntervalBucket> = IntervalRange.entries.map { range ->
        IntervalBucket(
            range = range,
            count = schedules.count { range.contains(it.intervalDays) },
            total = schedules.size,
        )
    }

    /** Threshold at which a Problem is worth flagging as a leech. */
    const val LEECH_LAPSE_THRESHOLD: Int = 4

    const val DEFAULT_HISTORY_DAYS: Int = 30
    const val ACTIVITY_CALENDAR_DAYS: Int = 371
    private const val NINETY_DAY_BUCKET_COUNT: Int = 13
    private const val DAYS_PER_ROLLING_BUCKET: Int = 7

    /**
     * The local date a schedule falls due, as an offset from [reference] (the
     * learner's today) measured from [now].
     *
     * Measured from *now*, not from `lastReviewedAt`. It used to subtract
     * `lastReviewedAt` from `dueAt`, which is the length of the whole interval rather
     * than the time still to run, and then added that to today — so a Problem reviewed
     * 20 days ago on a 21-day interval and due tomorrow was projected 21 days out and
     * dropped from `dueTomorrow`, while a Problem on a one-day interval counted as due
     * tomorrow whenever it had just been reviewed. Both directions were wrong and
     * neither was visible, because nothing rendered or asserted the number.
     *
     * Approximated against the reference date's own offset. Statistics are a
     * summary, not an audit, so a one-day edge at a DST boundary is acceptable
     * here in a way it explicitly is not for the 5am Club.
     */
    private fun ProblemSchedule.localDueDate(reference: LocalDate, now: Instant): LocalDate {
        // Floor, so any moment later today is "0 days from now" and only a due time
        // that has genuinely crossed into tomorrow counts as tomorrow.
        val secondsRemaining = dueAt.epochSeconds - now.epochSeconds
        val daysFromNow = Math.floorDiv(secondsRemaining, 86_400L).toInt()
        return reference.plus(DatePeriod(days = daysFromNow.coerceAtLeast(0)))
    }
}

data class StudyStatistics(
    val totalReviews: Int,
    val totalSolved: Int,
    val distinctProblemsSolved: Int,
    /** Distinct solved Problems that still exist in the current catalogue. */
    val catalogueProblemsSolved: Int,
    val totalProblems: Int,
    val reviewsToday: Int,
    val solvedToday: Int,
    val currentStreakDays: Int,
    /** Fraction of reviews that were not lapses, or null with no reviews yet. */
    val accuracy: Double?,
    val lapses: Int,
    val dueNow: Int,
    val dueTomorrow: Int,
    val notYetAttempted: Int,
    val byDifficulty: Map<ProblemDifficulty, DifficultyProgress>,
    val solvedByTopic: Map<String, Int>,
    val reviewsPerDay: List<DailyActivity>,
    /** A little over 52 weeks, so clients can align the heatmap to whole weeks. */
    val activityCalendar: List<DailyActivity>,
    val periodComparisons: Map<StatisticsPeriod, PeriodComparison>,
    val activityByPeriod: Map<StatisticsPeriod, List<ActivityBucket>>,
    val dataStructureProgress: List<TopicProgress>,
    val techniqueProgress: List<TopicProgress>,
    val intervalDistribution: List<IntervalBucket>,
    val averageIntervalDays: Double?,
    /** Problems lapsed often enough to be worth attention. */
    val leeches: List<ProblemId>,
) {
    val completionFraction: Float
        get() = if (totalProblems == 0) 0f else distinctProblemsSolved.toFloat() / totalProblems

    val catalogueCompletionFraction: Float
        get() = if (totalProblems == 0) 0f else catalogueProblemsSolved.toFloat() / totalProblems

    val hasActivity: Boolean get() = totalReviews > 0

    fun comparison(period: StatisticsPeriod): PeriodComparison =
        requireNotNull(periodComparisons[period]) { "Missing comparison for $period" }

    fun activity(period: StatisticsPeriod): List<ActivityBucket> =
        requireNotNull(activityByPeriod[period]) { "Missing activity for $period" }
}

enum class StatisticsPeriod(val days: Int) {
    SEVEN_DAYS(7),
    THIRTY_DAYS(30),
    NINETY_DAYS(90),
}

data class PeriodMetrics(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reviews: Int,
    val successfulReviews: Int,
    val activeDays: Int,
) {
    /** Successful reviews divided by all reviews, or null when there was no activity. */
    val successRate: Double?
        get() = if (reviews == 0) null else successfulReviews.toDouble() / reviews
}

data class PeriodComparison(
    val period: StatisticsPeriod,
    val current: PeriodMetrics,
    val previous: PeriodMetrics,
) {
    val reviewChange: Int get() = current.reviews - previous.reviews

    val successfulReviewChange: Int
        get() = current.successfulReviews - previous.successfulReviews

    val activeDayChange: Int get() = current.activeDays - previous.activeDays

    /** Percentage-point change, or null when either window has no rate denominator. */
    val successRatePercentagePointChange: Double?
        get() {
            val currentRate = current.successRate ?: return null
            val previousRate = previous.successRate ?: return null
            return (currentRate - previousRate) * 100.0
        }

    val hasEarlierActivity: Boolean get() = previous.reviews > 0
}

data class ActivityBucket(
    val startDate: LocalDate,
    val endDate: LocalDate,
    val reviews: Int,
    val successfulReviews: Int,
) {
    init {
        require(endDate >= startDate) { "Activity bucket must not end before it starts" }
        require(reviews >= 0) { "Review count must not be negative" }
        require(successfulReviews in 0..reviews) {
            "Successful reviews must be between zero and all reviews"
        }
    }

    val isSingleDay: Boolean get() = startDate == endDate
}

data class DifficultyProgress(
    val difficulty: ProblemDifficulty,
    val total: Int,
    val solved: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else solved.toFloat() / total
}

data class TopicProgress(
    val topic: String,
    val solved: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else solved.toFloat() / total
}

enum class IntervalRange {
    LESS_THAN_ONE_DAY,
    ONE_TO_SIX_DAYS,
    ONE_TO_FOUR_WEEKS,
    ONE_TO_SIX_MONTHS,
    SIX_MONTHS_OR_MORE,
    ;

    internal fun contains(intervalDays: Double): Boolean = when (this) {
        LESS_THAN_ONE_DAY -> intervalDays < 1.0
        ONE_TO_SIX_DAYS -> intervalDays >= 1.0 && intervalDays < 7.0
        ONE_TO_FOUR_WEEKS -> intervalDays >= 7.0 && intervalDays < 30.0
        ONE_TO_SIX_MONTHS -> intervalDays >= 30.0 && intervalDays < 180.0
        SIX_MONTHS_OR_MORE -> intervalDays >= 180.0
    }
}

data class IntervalBucket(
    val range: IntervalRange,
    val count: Int,
    val total: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else count.toFloat() / total
}

data class DailyActivity(
    val date: LocalDate,
    val reviews: Int,
    val solved: Int,
)
