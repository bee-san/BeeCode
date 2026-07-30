package dev.bee.beecode.domain

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds

/**
 * How a due time is described to a learner.
 *
 * This is the function that makes FSRS *visible*, so its wording is not cosmetic: the
 * complaint it answers was "I'm not sure fsrs / studying is hooked up?", asked about an
 * app whose scheduler was working perfectly and said nothing. A badge that says the
 * wrong thing is worse than no badge, because it is evidence for a false conclusion.
 *
 * The urgency is asserted alongside every label, since the clients colour by urgency and
 * a label that reads right with the wrong urgency ships a red "Due in 3 days".
 */
class DueDescriptionTest {

    @Test
    fun aFutureDueTimeCountsDown() {
        assertEquals(
            DueDescription("Due in 3 days", DueUrgency.UPCOMING),
            describeDue(dueAt = NOW + days(3.0), now = NOW),
        )
    }

    @Test
    fun aFutureDueTimeUsesTheLargestLegibleUnit() {
        // Delegated to formatIntervalDays, which IntervalFormatTest covers in depth.
        // Asserted here anyway because the *prefix* is this function's: "Due in 2 hours"
        // has to read as English, and "Due in 0.083 days" is what the raw schedule says.
        assertEquals("Due in 2 hours", describeDue(NOW + days(2.0 / 24.0), NOW).label)
        assertEquals("Due in 10 minutes", describeDue(NOW + days(10.0 / 1_440.0), NOW).label)
        assertEquals("Due in 3 months", describeDue(NOW + days(90.0), NOW).label)
    }

    @Test
    fun aSubMinuteFutureReadsAsDueNowRatherThanAsLessThanAMinute() {
        // FSRS-7 schedules sub-day intervals, so this is a real state and not a rounding
        // curiosity: an AGAIN can put a Problem twenty seconds out. "Due in less than a
        // minute" is a worse thing to say than the plain truth, and it is DUE rather than
        // UPCOMING because the learner should act on it now.
        assertEquals(
            DueDescription("Due now", DueUrgency.DUE),
            describeDue(dueAt = NOW + 20.seconds, now = NOW),
        )
    }

    @Test
    fun theExactDueInstantIsDueNow() {
        // The boundary the `> 0.0` test decides. Neither "Due in ..." nor "Overdue by ..."
        // is true here, and both would look like a bug.
        assertEquals(
            DueDescription("Due now", DueUrgency.DUE),
            describeDue(dueAt = NOW, now = NOW),
        )
    }

    @Test
    fun recentlyPastDueIsStillOnlyDue() {
        // The ordinary state of today's queue for anyone who studies daily. Calling this
        // overdue would mark the normal case as a problem and make the signal worthless.
        assertEquals(
            DueDescription("Due now", DueUrgency.DUE),
            describeDue(dueAt = NOW - days(0.5), now = NOW),
        )
    }

    @Test
    fun theOverdueThresholdIsInclusiveOfExactlyOneDay() {
        // `elapsedDays <= OVERDUE_AFTER_DAYS`. Exactly at the threshold is still DUE, so
        // a learner returning after precisely a day is not told off. Written against the
        // constant rather than a literal 1.0 so changing the policy moves this test with
        // it instead of breaking it.
        assertEquals(
            DueUrgency.DUE,
            describeDue(dueAt = NOW - days(OVERDUE_AFTER_DAYS), now = NOW).urgency,
        )
        // One second past it tips over, which is what makes the comparison a boundary
        // rather than a suggestion.
        assertEquals(
            DueUrgency.OVERDUE,
            describeDue(dueAt = NOW - days(OVERDUE_AFTER_DAYS) - 1.seconds, now = NOW).urgency,
        )
    }

    @Test
    fun aBadlyOverdueProblemSaysHowLateItIs() {
        // The state spaced repetition is most easily wrecked by, and the reason urgency
        // has three values instead of being a boolean: three weeks late and an hour late
        // are two different situations for the learner.
        assertEquals(
            DueDescription("Overdue by 21 days", DueUrgency.OVERDUE),
            describeDue(dueAt = NOW - days(21.0), now = NOW),
        )
        assertEquals(
            DueDescription("Overdue by 4 months", DueUrgency.OVERDUE),
            describeDue(dueAt = NOW - days(120.0), now = NOW),
        )
    }

    @Test
    fun theWordingDistinguishesTheThreeStatesFromEachOther() {
        // A badge is only useful if the three states cannot be confused at a glance, and
        // that is a property of the set of labels rather than of any one of them. Asserted
        // as a set so a future rewording that collapses two states fails here.
        val labels = listOf(
            describeDue(NOW + days(3.0), NOW),
            describeDue(NOW, NOW),
            describeDue(NOW - days(21.0), NOW),
        )
        assertEquals(3, labels.map { it.label }.distinct().size, "each state needs its own wording")
        assertEquals(3, labels.map { it.urgency }.distinct().size, "each state needs its own urgency")
    }

    private companion object {
        /** A fixed instant: nothing here may depend on when the suite runs. */
        val NOW: Instant = Instant.parse("2026-07-29T12:00:00Z")

        /**
         * Fractional days as a Duration.
         *
         * Fractional on purpose — `describeDue` divides by whole seconds, and a helper
         * taking `Int` could not express the sub-day spans FSRS-7 actually schedules.
         */
        fun days(count: Double): Duration = (count * 24.0).hours
    }
}
