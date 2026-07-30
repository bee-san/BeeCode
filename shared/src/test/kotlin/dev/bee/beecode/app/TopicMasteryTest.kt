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
import dev.bee.beecode.domain.ProblemTest
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSessionId
import dev.bee.beecode.domain.TopicSchedule
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The numbers put in front of the learner about each technique.
 *
 * These exist because this projection is the one place in the app where a true
 * number can be turned into a lie. Three specific lies are what the tests below
 * forbid:
 *
 * - **"0% at dynamic programming"** after a single lapse in a topic the learner has
 *   barely touched. A rate over one trial is noise, so none is reported.
 * - **"90% at greedy"** when the figure is almost entirely the prior showing through
 *   a thin sample.
 * - **"you have no history of stacks"** after the topic is retagged out of the pack,
 *   which would make the learner's own practice appear to have been deleted.
 *
 * A pure fold with an injected `now`, like [Statistics] and [Achievements]: nothing
 * here reads a clock, so every assertion is exact rather than approximate.
 */
class TopicMasteryTest {

    /** 2026-07-30T12:00:00Z. Fixed, so "is it due" has one answer. */
    private val now = Instant.parse("2026-07-30T12:00:00Z")

    @Test
    fun oneReviewReportsNoRecallRateRatherThanAPlausibleNumber() {
        // The lie this forbids: "0% at dynamic programming" from a single lapse. The
        // counts are still shown, because "1 review, 1 lapse" is honest.
        val projection = compute(
            problems = listOf(problem("climbing-stairs", "dynamic-programming")),
            reviews = listOf(review("s1", "climbing-stairs", ReviewRating.AGAIN)),
        )

        val dp = projection.ability("dynamic-programming")
        assertNull(dp.recallRate, "one review cannot support a rate")
        assertEquals(false, dp.hasEnoughEvidence)
        assertEquals(1, dp.reviews)
        assertEquals(1, dp.lapses)
    }

    @Test
    fun theRateAppearsOnlyOnceThereIsEnoughEvidence() {
        val problems = listOf(problem("climbing-stairs", "dynamic-programming"))
        for (n in 1 until TopicMastery.MIN_TOPIC_REVIEWS) {
            val short = compute(problems, (1..n).map { review("s$it", "climbing-stairs") })
            assertNull(short.ability("dynamic-programming").recallRate, "$n reviews is too few")
        }

        val enough = compute(
            problems,
            (1..TopicMastery.MIN_TOPIC_REVIEWS).map { review("s$it", "climbing-stairs") },
        )
        assertNotNull(
            enough.ability("dynamic-programming").recallRate,
            "${TopicMastery.MIN_TOPIC_REVIEWS} reviews is the stated threshold",
        )
    }

    @Test
    fun aPerfectTopicIsPulledBelowOneByALapseAnywhereElse() {
        // Five out of five on arrays is not proof of mastery, and 100% would say it is.
        // One lapse on an unrelated topic is enough evidence that the learner is
        // fallible for the figure to come off certainty.
        val problems = listOf(problem("two-sum", "arrays"), problem("climbing-stairs", "dp"))
        val projection = compute(
            problems,
            (1..5).map { review("a$it", "two-sum") } +
                review("d1", "climbing-stairs", ReviewRating.AGAIN),
        )

        val rate = assertNotNull(projection.ability("arrays").recallRate)
        assertTrue(rate < 1.0, "a perfect five must not read as certainty: $rate")
        assertTrue(rate > 0.9, "nor should shrinkage bury a perfect five: $rate")
    }

    @Test
    fun aTotalFailureIsPulledAboveZeroByARecallAnywhereElse() {
        // The other end of the same argument. Five lapses out of five is a real problem
        // and the number should be low — but 0.0 asserts the learner recalls this topic
        // never, and a single success elsewhere is evidence they recall things at all.
        val problems = listOf(problem("climbing-stairs", "dp"), problem("two-sum", "arrays"))
        val projection = compute(
            problems,
            (1..5).map { review("d$it", "climbing-stairs", ReviewRating.AGAIN) } +
                review("a1", "two-sum"),
        )

        val rate = assertNotNull(projection.ability("dp").recallRate)
        assertTrue(rate > 0.0, "five lapses must not read as impossibility: $rate")
        assertTrue(rate < 0.5, "nor should shrinkage flatter it: $rate")
    }

    @Test
    fun anExtremeIsReportedOnlyWhenTheWholeHistoryIsUnanimous() {
        // The degenerate case of shrinking toward the learner's own average: when a
        // topic *is* their entire history, the prior equals the topic's own rate and
        // pulls it nowhere. That is not a gap — 100% for a learner who has never once
        // lapsed at anything is a true statement about the evidence, and inventing a
        // second prior to hide it would make the number less honest, not more.
        //
        // Asserted rather than left implicit because a UI author reading only
        // [aPerfectTopicIsPulledBelowOneByALapseAnywhereElse] would conclude the
        // extremes are unreachable and lay out the screen accordingly.
        val perfect = compute(
            problems = listOf(problem("two-sum", "arrays")),
            reviews = (1..5).map { review("s$it", "two-sum") },
        )
        assertEquals(1.0, perfect.ability("arrays").recallRate)

        val hopeless = compute(
            problems = listOf(problem("climbing-stairs", "dp")),
            reviews = (1..5).map { review("s$it", "climbing-stairs", ReviewRating.AGAIN) },
        )
        assertEquals(0.0, hopeless.ability("dp").recallRate)
    }

    @Test
    fun theShrinkagePriorIsTheLearnersOwnAverageNotAConstant() {
        // Two learners, identical evidence on `arrays`: five clean reviews. They differ
        // only in how they do everywhere else. The one who lapses often elsewhere must
        // not have their thin topic flattered up to the other's figure.
        val problems = listOf(problem("two-sum", "arrays"), problem("valid-parentheses", "stack"))
        val arrays = (1..5).map { review("a$it", "two-sum") }

        val strong = compute(problems, arrays + (1..10).map { review("o$it", "valid-parentheses") })
        val weak = compute(
            problems,
            arrays + (1..10).map { review("o$it", "valid-parentheses", ReviewRating.AGAIN) },
        )

        val strongRate = assertNotNull(strong.ability("arrays").recallRate)
        val weakRate = assertNotNull(weak.ability("arrays").recallRate)
        assertTrue(
            weakRate < strongRate,
            "the prior must be the learner's own average: $weakRate vs $strongRate",
        )
        assertEquals(1.0, strong.globalRecallRate)
        assertTrue(weak.globalRecallRate < 1.0)
    }

    @Test
    fun withNoHistoryAtAllThePriorIsTheStatedRetentionTarget() {
        // Nothing to average, so the fallback has to be a number with a source. The
        // scheduler's own target is that number; an invented 0.5 would not be.
        val projection = compute(problems = listOf(problem("two-sum", "arrays")), reviews = emptyList())

        assertEquals(0.9, projection.globalRecallRate)
        val arrays = projection.ability("arrays")
        assertNull(arrays.recallRate, "an unpractised topic reports no rate at all")
        assertEquals(0, arrays.reviews)
        assertEquals(1, arrays.memberProblems)
        assertEquals(0, arrays.attemptedMemberProblems)
    }

    @Test
    fun aMultiTaggedProblemContributesToEveryOneOfItsTopics() {
        // The fan-out that makes topic scheduling work at all: one review of
        // median-two-sorted is evidence about three techniques, not a third of one.
        val projection = compute(
            problems = listOf(problem("median-two-sorted", "arrays", "binary-search", "two-pointers")),
            reviews = listOf(review("s1", "median-two-sorted", ReviewRating.AGAIN)),
        )

        for (topic in listOf("arrays", "binary-search", "two-pointers")) {
            val ability = projection.ability(topic)
            assertEquals(1, ability.reviews, topic)
            assertEquals(1, ability.lapses, topic)
            assertEquals(now, ability.lastPractisedAt, topic)
        }
    }

    @Test
    fun aTopicTaggedTwiceOnOneProblemCountsOnce() {
        // The pack loader rejects a repeated tag and `.distinct()`s the union, so this
        // shape cannot come out of `content/`. Asserted anyway because the fan-out is what
        // makes double-counting possible at all: `Problem` itself permits a repeated topic,
        // and one review inflating a topic's evidence would corrupt the recall rate rather
        // than fail loudly.
        val duplicated = problem("two-sum", "array", "array")
        val projection = compute(listOf(duplicated), listOf(review("s1", "two-sum")))

        assertEquals(1, projection.ability("array").reviews)
        assertEquals(1, projection.ability("array").memberProblems)
    }

    @Test
    fun coverageIsReportedBesideTheRateAndNeverFoldedIntoIt() {
        // The distinction the whole design turns on: "weak at arrays" and "has barely
        // done any arrays" are different facts. Here the learner is perfect on the one
        // Problem they have touched out of four, so the rate must stay high while
        // coverage stays visibly low.
        val problems = (1..4).map { problem("arrays-$it", "arrays") }
        val projection = compute(problems, (1..5).map { review("s$it", "arrays-1") })

        val arrays = projection.ability("arrays")
        assertTrue(assertNotNull(arrays.recallRate) > 0.9)
        assertEquals(4, arrays.memberProblems)
        assertEquals(1, arrays.attemptedMemberProblems)
        assertEquals(0.25f, arrays.coverageFraction)
    }

    @Test
    fun solvedAndAttemptedAreCountedSeparately() {
        // An aided pass is attempted-but-not-solved. Collapsing the two would let
        // revealing the answer read as coverage.
        val problems = listOf(problem("two-sum", "arrays"), problem("peak-element", "arrays"))
        val projection = compute(
            problems,
            listOf(
                review("s1", "two-sum", countsAsSolved = true),
                review("s2", "peak-element", ReviewRating.HARD, countsAsSolved = false),
            ),
        )

        val arrays = projection.ability("arrays")
        assertEquals(2, arrays.attemptedMemberProblems)
        assertEquals(1, arrays.solvedMemberProblems)
    }

    @Test
    fun durabilityComesFromTheTopicsOwnScheduleAndIsAbsentWithoutOne() {
        // The interval is FSRS's own output, read straight off the topic card rather
        // than derived here. A topic with reviews but no card yet — the state right
        // after a restore, before the rebuild — reports its counts with no durability
        // instead of vanishing from the list.
        val problems = listOf(problem("two-sum", "arrays"), problem("valid-parentheses", "stack"))
        val projection = compute(
            problems,
            listOf(review("s1", "two-sum"), review("s2", "valid-parentheses")),
            schedules = mapOf("arrays" to schedule("arrays", intervalDays = 9.5, dueInDays = -1.0)),
        )

        val arrays = projection.ability("arrays")
        assertEquals(9.5, arrays.intervalDays)
        assertEquals(4.0, arrays.stability)
        assertTrue(arrays.isDue, "a card whose due date has passed is due")

        val stack = projection.ability("stack")
        assertNull(stack.intervalDays, "no card means no durability figure")
        assertNull(stack.stability)
        assertNull(stack.dueAt)
        assertEquals(false, stack.isDue)
        assertEquals(1, stack.reviews, "but the practice is still counted")
    }

    @Test
    fun aTopicRetaggedOutOfThePackKeepsItsCard() {
        // If a pack update drops `stack` from every Problem, the learner's practice of
        // it does not become untrue. It reads as a topic with a card and no members
        // rather than disappearing from their history.
        val projection = compute(
            problems = listOf(problem("valid-parentheses", "strings")),
            reviews = listOf(review("s1", "valid-parentheses")),
            schedules = mapOf("stack" to schedule("stack", intervalDays = 3.0, dueInDays = 2.0)),
        )

        val stack = projection.ability("stack")
        assertEquals(0, stack.memberProblems)
        assertEquals(3.0, stack.intervalDays)
        // Retagging rewrites the fold, so the review now counts toward `strings` only.
        assertEquals(0, stack.reviews)
        assertEquals(1, projection.ability("strings").reviews)
    }

    @Test
    fun topicsAreOrderedBySlugSoTheListDoesNotReshuffle() {
        // A list that reorders between refreshes reads as changing data. Slug order is
        // arbitrary but stable, which is the property that matters.
        val projection = compute(
            problems = listOf(
                problem("valid-parentheses", "stack", "strings"),
                problem("two-sum", "hash-map", "arrays"),
            ),
            reviews = listOf(review("s1", "two-sum")),
        )

        assertEquals(
            listOf("arrays", "hash-map", "stack", "strings"),
            projection.topics.map { it.topic },
        )
    }

    @Test
    fun weakestFirstOmitsUnevidencedTopicsRatherThanSortingThemToOneEnd() {
        // "What should I work on" is a question about topics with evidence. A topic
        // with no rate has no place in an ordering by rate, at either end.
        val problems = listOf(
            problem("climbing-stairs", "dynamic-programming"),
            problem("two-sum", "arrays"),
            problem("valid-parentheses", "stack"),
        )
        val projection = compute(
            problems,
            (1..6).map { review("d$it", "climbing-stairs", ReviewRating.AGAIN) } +
                (1..6).map { review("a$it", "two-sum") } +
                listOf(review("s1", "valid-parentheses")),
        )

        assertEquals(
            listOf("dynamic-programming", "arrays"),
            projection.weakestFirst.map { it.topic },
            "weakest first, and the one-review topic is not in the ordering at all",
        )
        assertEquals(3, projection.practised.size, "though it is still counted as practised")
    }

    @Test
    fun dueListsOnlyTheTopicsWhoseCardHasComeRound() {
        val projection = compute(
            problems = listOf(problem("two-sum", "arrays", "hash-map")),
            reviews = listOf(review("s1", "two-sum")),
            schedules = mapOf(
                "arrays" to schedule("arrays", intervalDays = 2.0, dueInDays = -0.5),
                "hash-map" to schedule("hash-map", intervalDays = 2.0, dueInDays = 0.5),
            ),
        )

        assertEquals(listOf("arrays"), projection.due.map { it.topic })
    }

    @Test
    fun displayNamesAreHumanisedFromTheSlugInSentenceCase() {
        assertEquals("Dynamic programming", TopicMastery.displayName("dynamic-programming"))
        assertEquals("Hash map", TopicMastery.displayName("hash_map"))
        assertEquals("Array", TopicMastery.displayName("array"))
        assertEquals("Sliding window", TopicMastery.displayName("sliding-window"))
        // Total by construction: this must name a card the pack no longer explains, so
        // a degenerate slug degrades to itself rather than throwing.
        assertEquals("-", TopicMastery.displayName("-"))
        assertEquals("", TopicMastery.displayName(""))
    }

    // ---- Fixtures -------------------------------------------------------

    private fun TopicMasteryProjection.ability(topic: String): TopicAbility =
        assertNotNull(topics.firstOrNull { it.topic == topic }, "no ability for '$topic'")

    private fun compute(
        problems: List<ProblemDefinition>,
        reviews: List<ProblemReviewFinalized>,
        schedules: Map<String, TopicSchedule> = emptyMap(),
    ) = TopicMastery.compute(
        reviews = reviews,
        topicSchedules = schedules,
        problems = problems,
        now = now,
        // The scheduler's default target, which is what BeeCodeProfile passes.
        desiredRetention = 0.9,
    )

    private fun schedule(topic: String, intervalDays: Double, dueInDays: Double) = TopicSchedule(
        topic = topic,
        stability = 4.0,
        difficulty = 5.0,
        dueAt = now.plus(kotlin.time.Duration.parse("${dueInDays * 24}h")),
        lastReviewedAt = now.minus(kotlin.time.Duration.parse("${intervalDays * 24}h")),
        intervalDays = intervalDays,
        reviewCount = 3,
        lapseCount = 0,
        version = 1,
        updatedAt = now,
    )

    private fun problem(id: String, vararg topics: String) = ProblemDefinition(
        id = ProblemId(id),
        revisionId = ProblemRevisionId("a".repeat(64)),
        title = id,
        difficulty = ProblemDifficulty.EASY,
        topics = topics.toList(),
        statementMarkdown = "Do the thing.",
        starterSource = "def solve():\n    pass\n",
        entryPoint = "solve",
        examples = listOf(ProblemExample("[]", "[]", null)),
        tests = listOf(ProblemTest("example", "[]", "[]", ComparatorId.EXACT)),
        limits = ExecutionLimits.DEFAULT,
        explanationMarkdown = null,
    )

    private fun review(
        session: String,
        problemId: String,
        rating: ReviewRating = ReviewRating.GOOD,
        countsAsSolved: Boolean = rating != ReviewRating.AGAIN,
    ) = ProblemReviewFinalized(
        eventId = DomainEventId("evt-$session"),
        sessionId = ReviewSessionId(session),
        problemId = ProblemId(problemId),
        problemRevisionId = ProblemRevisionId("a".repeat(64)),
        executionRunId = ExecutionRunId("run-$session"),
        outcome = if (rating == ReviewRating.AGAIN) {
            ExecutionOutcome.FAILED
        } else {
            ExecutionOutcome.PASSED
        },
        rating = rating,
        aided = !countsAsSolved && rating != ReviewRating.AGAIN,
        countsAsSolved = countsAsSolved,
        finalizedAt = now,
        streakZoneId = "UTC",
        deviceId = DeviceId("device-1"),
        transition = FsrsTransitionRecord(
            algorithmId = "FSRS-7 35-parameter",
            engineVersion = "bee-fsrs-0.2.0",
            parametersHash = "0".repeat(16),
            previousStateHash = "none",
            previousStability = null,
            previousDifficulty = null,
            elapsedDays = 0.0,
            ratingValue = rating.ordinal + 1,
            desiredRetention = 0.9,
            maximumIntervalDays = 36_500.0,
            nextStability = 2.3,
            nextDifficulty = 5.0,
            nextIntervalDays = 2.0,
            retrievability = 1.0,
            dueAt = now,
        ),
    )
}
