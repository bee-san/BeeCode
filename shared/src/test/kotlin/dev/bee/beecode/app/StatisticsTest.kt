package dev.bee.beecode.app

import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemSchedule
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals

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
}
