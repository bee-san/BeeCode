package dev.bee.beecode.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * [formatIntervalDays] turns FSRS-7's fractional intervals into something a learner
 * can read.
 *
 * This exists because FSRS-7 schedules in fractional days and a raw interval is not a
 * sentence: "next review in 0.00694 days" is the literal schedule and communicates
 * nothing. The desktop and Android clients share this function rather than each
 * formatting the number, so the unit choice cannot drift between them.
 */
class IntervalFormatTest {

    @Test
    fun subDayIntervalsReadInMinutesAndHours() {
        // The FSRS-7 cases. Under FSRS-6 none of these existed: every interval was at
        // least one whole day, so "10 minutes" had no way to occur.
        assertEquals("10 minutes", formatIntervalDays(10.0 / 1_440.0))
        assertEquals("1 minute", formatIntervalDays(1.0 / 1_440.0))
        assertEquals("30 minutes", formatIntervalDays(30.0 / 1_440.0))
        assertEquals("2 hours", formatIntervalDays(2.0 / 24.0))
        assertEquals("1 hour", formatIntervalDays(1.0 / 24.0))
        assertEquals("12 hours", formatIntervalDays(0.5))
    }

    @Test
    fun anIntervalTooShortToNameSaysSo() {
        // Rounding to "0 minutes" would read as a bug rather than a schedule.
        assertEquals("less than a minute", formatIntervalDays(1.0 / 86_400.0))
        assertEquals("less than a minute", formatIntervalDays(1.0e-6))
    }

    @Test
    fun wholeDayIntervalsReadAsDays() {
        assertEquals("1 day", formatIntervalDays(1.0))
        assertEquals("3 days", formatIntervalDays(3.0))
        assertEquals("29 days", formatIntervalDays(29.0))
    }

    @Test
    fun largeIntervalsCollapseIntoMonthsAndYears() {
        // "547 days" is technically the schedule and nobody thinks in those units.
        assertEquals("1 month", formatIntervalDays(30.0))
        assertEquals("2 months", formatIntervalDays(61.0))
        assertEquals("1 year", formatIntervalDays(365.0))
        assertEquals("2 years", formatIntervalDays(730.0))
        assertEquals("10 years", formatIntervalDays(3_650.0))
    }

    @Test
    fun theUnitBoundariesRoundTheWayALearnerWouldSayThem() {
        // Just under a day is "1 day", not "23 hours": rounding to the unit a learner
        // would use matters more than truncating to the one that is literally true.
        assertEquals("1 day", formatIntervalDays(0.99))
        // Just under an hour stays in minutes, because "1 hour" would overstate it
        // by a whole unit at that scale.
        assertEquals("59 minutes", formatIntervalDays(59.0 / 1_440.0))
        // And just under a minute is still nameable in minutes.
        assertEquals("1 minute", formatIntervalDays(59.0 / 86_400.0))
    }

    @Test
    fun singularAndPluralAreBothCorrect() {
        // A stray "1 days" is the kind of thing a learner notices immediately.
        for (formatted in listOf(
            formatIntervalDays(1.0 / 1_440.0),
            formatIntervalDays(1.0 / 24.0),
            formatIntervalDays(1.0),
            formatIntervalDays(30.0),
            formatIntervalDays(365.0),
        )) {
            assertTrue(formatted.startsWith("1 "), formatted)
            assertTrue(!formatted.endsWith("s"), "singular must not be pluralised: $formatted")
        }
        for (formatted in listOf(
            formatIntervalDays(2.0 / 1_440.0),
            formatIntervalDays(2.0 / 24.0),
            formatIntervalDays(2.0),
            formatIntervalDays(61.0),
            formatIntervalDays(730.0),
        )) {
            assertTrue(formatted.endsWith("s"), "plural must be pluralised: $formatted")
        }
    }

    @Test
    fun everyIntervalTheEngineCanProduceFormatsWithoutThrowing() {
        // The engine's range is roughly 1e-7 to 36500 days, and a formatter that threw
        // anywhere inside it would crash the screen that shows a completed review.
        var interval = 1.0e-7
        while (interval < 40_000.0) {
            val formatted = formatIntervalDays(interval)
            assertTrue(formatted.isNotBlank(), "blank at $interval")
            interval *= 1.5
        }
    }

    @Test
    fun anImpossibleIntervalIsRejectedRatherThanFormatted() {
        // A non-positive or non-finite interval is a bug upstream of here, and a
        // plausible-looking string would hide it.
        for (invalid in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            runCatching { formatIntervalDays(invalid) }
                .onSuccess { error("interval $invalid must be rejected, formatted as $it") }
        }
    }
}
