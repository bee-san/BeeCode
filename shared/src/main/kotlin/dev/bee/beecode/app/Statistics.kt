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

        return StudyStatistics(
            totalReviews = reviews.size,
            totalSolved = solved.size,
            distinctProblemsSolved = solvedProblemIds.size,
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

    /** Threshold at which a Problem is worth flagging as a leech. */
    const val LEECH_LAPSE_THRESHOLD: Int = 4

    const val DEFAULT_HISTORY_DAYS: Int = 30

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
    val averageIntervalDays: Double?,
    /** Problems lapsed often enough to be worth attention. */
    val leeches: List<ProblemId>,
) {
    val completionFraction: Float
        get() = if (totalProblems == 0) 0f else distinctProblemsSolved.toFloat() / totalProblems

    val hasActivity: Boolean get() = totalReviews > 0
}

data class DifficultyProgress(
    val difficulty: ProblemDifficulty,
    val total: Int,
    val solved: Int,
) {
    val fraction: Float get() = if (total == 0) 0f else solved.toFloat() / total
}

data class DailyActivity(
    val date: LocalDate,
    val reviews: Int,
    val solved: Int,
)
