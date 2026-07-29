package dev.bee.beecode.app

import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.DomainEventId
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.FsrsTransitionRecord
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSessionId
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The 5am Club boundary suite.
 *
 * The plan names this achievement as normative and lists the exact cases it must
 * survive: the 05:59:59/06:00 edge, seven consecutive days, a gap, DST, travel, a
 * late-arriving event, a duplicate, a reveal, and a failure. Each is a real way a
 * naive streak counter goes wrong, and each has its own test here.
 *
 * The reason they all pass is architectural rather than defensive: projection is a
 * pure fold over the review log, so a late or out-of-order event recomputes
 * correctly instead of corrupting an increment-only counter. And each review carries
 * the timezone it was finalized in, so travelling cannot retroactively change a day.
 */
class FiveAmClubTest {

    @Test
    fun aSolveAtFiveFiftyNineQualifies() {
        val projection = Achievements.project(
            listOf(solved("2026-07-01T05:59:59Z", zone = "UTC")),
        )
        assertEquals(1, projection.state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun aSolveAtSixOClockDoesNotQualify() {
        // The window is [00:00, 06:00). 06:00:00 is outside it, and being off by one
        // second here would silently include an hour of ordinary morning study.
        val projection = Achievements.project(
            listOf(solved("2026-07-01T06:00:00Z", zone = "UTC")),
        )
        assertEquals(0, projection.state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun aSolveAtMidnightQualifies() {
        val projection = Achievements.project(
            listOf(solved("2026-07-01T00:00:00Z", zone = "UTC")),
        )
        assertEquals(1, projection.state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun sevenConsecutiveEarlyDaysEarnsIt() {
        val reviews = (1..7).map { day ->
            solved("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        val state = Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!
        assertTrue(state.earned)
        assertEquals(7, state.progress)
        assertEquals(LocalDate.parse("2026-07-07"), state.completedOn)
    }

    @Test
    fun sixConsecutiveEarlyDaysDoesNotEarnIt() {
        val reviews = (1..6).map { day ->
            solved("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        val state = Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!
        assertFalse(state.earned)
        assertEquals(6, state.progress)
    }

    @Test
    fun aGapResetsTheStreak() {
        // Days 1-3, nothing on day 4, then 5-7. The longest run is 3, not 6.
        val days = listOf(1, 2, 3, 5, 6, 7)
        val reviews = days.map { day ->
            solved("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        val state = Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!
        assertFalse(state.earned)
        assertEquals(3, state.progress)
    }

    @Test
    fun severalSolvesInOneMorningCountAsOneDay() {
        // One qualifying contribution per date. Solving three Problems at 5am is
        // admirable and is still one day of a seven-day streak.
        val reviews = listOf(
            solved("2026-07-01T04:00:00Z", zone = "UTC", session = "a"),
            solved("2026-07-01T05:00:00Z", zone = "UTC", session = "b"),
            solved("2026-07-01T05:30:00Z", zone = "UTC", session = "c"),
        )
        assertEquals(1, Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun aLateArrivingEventIsPlacedCorrectlyRatherThanAppended() {
        // The case an increment-only counter gets wrong. Days 1, 2, 4, 5, 6, 7 are
        // recorded, then day 3 arrives last — from a clock correction, or a future
        // sync merge. Folding the log places it correctly and completes the streak;
        // a counter would have already reset and would end at 4.
        val outOfOrder = listOf(1, 2, 4, 5, 6, 7, 3).map { day ->
            solved("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        val state = Achievements.project(outOfOrder).state(Achievements.FIVE_AM_CLUB)!!
        assertTrue(state.earned, "a late event must complete the streak: ${state.detail}")
        assertEquals(7, state.progress)
    }

    @Test
    fun projectionIsIndependentOfInputOrder() {
        // A merged sync payload arrives in arbitrary order, so the same set of
        // reviews must always produce the same result.
        val reviews = (1..7).map { day ->
            solved("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        assertEquals(
            Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB),
            Achievements.project(reviews.reversed()).state(Achievements.FIVE_AM_CLUB),
        )
        assertEquals(
            Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB),
            Achievements.project(reviews.shuffled(kotlin.random.Random(7)))
                .state(Achievements.FIVE_AM_CLUB),
        )
    }

    @Test
    fun aDuplicateReviewCannotInflateTheStreak() {
        // Same session recorded twice. Persistence prevents this, but projection must
        // not depend on that for correctness.
        val one = solved("2026-07-01T05:00:00Z", zone = "UTC", session = "dup")
        val reviews = listOf(one, one, one)
        assertEquals(1, Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun aRevealedSolveDoesNotQualify() {
        // This is what stops the achievement being farmed by reading the answer at
        // 5am for a week.
        val reviews = (1..7).map { day ->
            solved(
                "2026-07-%02dT05:00:00Z".format(day),
                zone = "UTC",
                session = "s$day",
                aided = true,
            )
        }
        val state = Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!
        assertFalse(state.earned)
        assertEquals(0, state.progress)
    }

    @Test
    fun aFailedReviewDoesNotQualify() {
        val reviews = (1..7).map { day ->
            failed("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        assertEquals(0, Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun theWindowIsEvaluatedInTheZoneRecordedWithTheReview() {
        // 09:00 UTC is 05:00 in New York, so a New York learner's early morning
        // qualifies even though the UTC hour does not. Evaluating in UTC would deny
        // the achievement to most of the world.
        val newYork = solved("2026-07-01T09:00:00Z", zone = "America/New_York")
        assertEquals(1, Achievements.project(listOf(newYork)).state(Achievements.FIVE_AM_CLUB)!!.progress)

        // The same instant recorded by a UTC learner is 09:00 local and does not.
        val utc = solved("2026-07-01T09:00:00Z", zone = "UTC")
        assertEquals(0, Achievements.project(listOf(utc)).state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun travellingDoesNotRetroactivelyChangeEarlierDays() {
        // Six days in London, then the learner flies to Tokyo and studies there.
        // Each day is judged in the zone recorded at the time, so the London days
        // stay qualifying — re-evaluating them in Tokyo's zone would silently
        // rewrite history.
        val london = (1..6).map { day ->
            solved("2026-07-%02dT04:00:00Z".format(day), zone = "Europe/London", session = "l$day")
        }
        // 2026-07-06 20:00 UTC is 2026-07-07 05:00 in Tokyo: a qualifying seventh
        // consecutive local date.
        val tokyo = solved("2026-07-06T20:00:00Z", zone = "Asia/Tokyo", session = "t1")

        val state = Achievements.project(london + tokyo).state(Achievements.FIVE_AM_CLUB)!!
        assertTrue(state.earned, "travel should not break a legitimate streak: ${state.detail}")
    }

    @Test
    fun aSpringForwardDstDayStillCounts() {
        // 2026-03-29 is the UK spring-forward date: 01:00 local jumps to 02:00, so
        // the local day is 23 hours long. A streak computed by adding 86,400 seconds
        // would drift off the date; using local dates does not.
        val reviews = listOf(
            solved("2026-03-27T04:00:00Z", zone = "Europe/London", session = "a"),
            solved("2026-03-28T04:00:00Z", zone = "Europe/London", session = "b"),
            // 04:00 UTC on the 29th is 05:00 BST, still inside the window.
            solved("2026-03-29T04:00:00Z", zone = "Europe/London", session = "c"),
            solved("2026-03-30T04:00:00Z", zone = "Europe/London", session = "d"),
        )
        val state = Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!
        assertEquals(4, state.progress, "DST must not break consecutive local dates: ${state.detail}")
    }

    @Test
    fun anAutumnFallBackDstDayStillCounts() {
        // 2026-10-25 is the UK fall-back date: the local day is 25 hours long.
        val reviews = listOf(
            solved("2026-10-23T04:00:00Z", zone = "Europe/London", session = "a"),
            solved("2026-10-24T04:00:00Z", zone = "Europe/London", session = "b"),
            solved("2026-10-25T04:00:00Z", zone = "Europe/London", session = "c"),
            solved("2026-10-26T04:00:00Z", zone = "Europe/London", session = "d"),
        )
        assertEquals(4, Achievements.project(reviews).state(Achievements.FIVE_AM_CLUB)!!.progress)
    }

    @Test
    fun anEarnedAchievementSurvivesALaterGap() {
        // Awards are immutable. Earning the streak and then missing a week must not
        // take the achievement away.
        val streak = (1..7).map { day ->
            solved("2026-07-%02dT05:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        val later = solved("2026-08-01T05:00:00Z", zone = "UTC", session = "late")
        val state = Achievements.project(streak + later).state(Achievements.FIVE_AM_CLUB)!!
        assertTrue(state.earned, "an earned achievement must not be revocable")
    }

    @Test
    fun anUnrecognisedZoneFallsBackRatherThanThrowing() {
        // A profile restored from a device with a different timezone database could
        // carry a zone this platform does not know. Falling back to UTC is auditable;
        // crashing on the statistics screen is not.
        val review = solved("2026-07-01T05:00:00Z", zone = "Mars/Olympus_Mons")
        val state = Achievements.project(listOf(review)).state(Achievements.FIVE_AM_CLUB)!!
        assertEquals(1, state.progress, "should have fallen back to UTC")
    }

    @Test
    fun theOrdinaryStreakIgnoresTheTimeOfDay() {
        // The seven-day streak achievement shares the fold but not the window, so
        // afternoon study counts for it and not for the 5am Club.
        val afternoons = (1..7).map { day ->
            solved("2026-07-%02dT14:00:00Z".format(day), zone = "UTC", session = "s$day")
        }
        val projection = Achievements.project(afternoons)
        assertTrue(projection.state(Achievements.WEEK_STREAK)!!.earned)
        assertFalse(projection.state(Achievements.FIVE_AM_CLUB)!!.earned)
    }

    @Test
    fun currentStreakTreatsYesterdayAsStillAlive() {
        // A learner who has not studied yet today has not broken anything, and
        // showing zero before their first review of the day would be both wrong and
        // discouraging.
        val reviews = listOf(
            solved("2026-07-05T10:00:00Z", zone = "UTC", session = "a"),
            solved("2026-07-06T10:00:00Z", zone = "UTC", session = "b"),
        )
        assertEquals(2, Achievements.currentStreak(reviews, LocalDate.parse("2026-07-07")))
        // Two days stale is a broken streak.
        assertEquals(0, Achievements.currentStreak(reviews, LocalDate.parse("2026-07-08")))
    }

    @Test
    fun currentStreakIsZeroWithNoSolves() {
        assertEquals(0, Achievements.currentStreak(emptyList(), LocalDate.parse("2026-07-07")))
    }

    // ---- Fixtures -------------------------------------------------------

    private fun solved(
        instant: String,
        zone: String,
        session: String = "s1",
        aided: Boolean = false,
    ): ProblemReviewFinalized = review(
        instant = instant,
        zone = zone,
        session = session,
        aided = aided,
        // An aided pass never counts as solved; that rule lives in the domain and is
        // reproduced here because the fixture must not contradict it.
        countsAsSolved = !aided,
        outcome = ExecutionOutcome.PASSED,
        rating = if (aided) ReviewRating.HARD else ReviewRating.GOOD,
    )

    private fun failed(
        instant: String,
        zone: String,
        session: String = "s1",
    ): ProblemReviewFinalized = review(
        instant = instant,
        zone = zone,
        session = session,
        aided = false,
        countsAsSolved = false,
        outcome = ExecutionOutcome.FAILED,
        rating = ReviewRating.AGAIN,
    )

    private fun review(
        instant: String,
        zone: String,
        session: String,
        aided: Boolean,
        countsAsSolved: Boolean,
        outcome: ExecutionOutcome,
        rating: ReviewRating,
    ): ProblemReviewFinalized {
        val at = Instant.parse(instant)
        return ProblemReviewFinalized(
            eventId = DomainEventId("evt-$session"),
            sessionId = ReviewSessionId(session),
            problemId = ProblemId("two-sum"),
            problemRevisionId = ProblemRevisionId("a".repeat(64)),
            executionRunId = ExecutionRunId("run-$session"),
            outcome = outcome,
            rating = rating,
            aided = aided,
            countsAsSolved = countsAsSolved,
            finalizedAt = at,
            streakZoneId = zone,
            deviceId = DeviceId("device-1"),
            transition = FsrsTransitionRecord(
                algorithmId = "FSRS-6.x 21-parameter snapshot",
                engineVersion = "bee-fsrs-0.1.0",
                parametersHash = "0".repeat(16),
                previousStateHash = "none",
                previousStability = null,
                previousDifficulty = null,
                elapsedDays = 0.0,
                ratingValue = 3,
                desiredRetention = 0.9,
                maximumIntervalDays = 36_500.0,
                nextStability = 2.3,
                nextDifficulty = 5.0,
                nextIntervalDays = 2.0,
                retrievability = 0.0,
                dueAt = at,
            ),
        )
    }
}
