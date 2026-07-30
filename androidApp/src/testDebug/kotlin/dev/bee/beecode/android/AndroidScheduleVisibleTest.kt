package dev.bee.beecode.android

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.QUEUE_LIST_TAG
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.design.ThemeChoice
import dev.bee.beecode.design.themeChoice
import dev.bee.beecode.domain.formatIntervalDays
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.minutes
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * FSRS is visible on the phone, not merely running on it.
 *
 * ### The complaint this answers
 *
 * "I'm not sure fsrs / studying is hooked up?" — asked about an app whose scheduler was
 * working perfectly. Every number here was already computed and stored on every review, and
 * on Android none of it was rendered anywhere: no interval, no due badge, no memory
 * strength, no schedule card. The scheduler was invisible, so from outside it was
 * indistinguishable from absent.
 *
 * That makes these assertions load-bearing rather than cosmetic. A test that only checks
 * *that* a review was scheduled can pass on an app that shows the learner nothing, which is
 * exactly the state that prompted the question.
 *
 * Asserted on the wording rather than on layout, since Robolectric's canvas is a no-op and
 * cannot speak to appearance. What it can prove is that the numbers reach the screen.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class AndroidScheduleVisibleTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHostActivity>()

    private lateinit var profile: BeeCodeProfile

    /**
     * The profile's clock, movable.
     *
     * FSRS-7 reschedules a lapse *seconds* out, so any assertion about a Problem being due
     * again has to cross a boundary the test cannot wait for. Only the profile's clock is
     * injectable — the queue rows read `Clock.System.now()` directly — so this moves the
     * stored schedule relative to real time rather than the other way round.
     */
    private val clock = MovableClock()

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)
        val catalogue = context.assets.open(BeeCodeApplication.PACK_ASSET)
            .bufferedReader()
            .use { ProblemCatalogue.fromPackJson(it.readText()) }
        profile = BeeCodeProfile.inMemory(
            catalogue = catalogue,
            runner = ScriptedPythonRunner(),
            clock = clock,
        )
    }

    /** A clock the test moves, so a schedule boundary can be crossed without waiting. */
    private class MovableClock : Clock {
        private var now: Instant = Clock.System.now()
        override fun now(): Instant = now
        fun set(instant: Instant) { now = instant }
        fun advanceBy(duration: Duration) { now += duration }
    }

    @After
    fun tearDown() {
        profile.close()
    }

    @Test
    fun anUnattemptedProblemSaysSoRatherThanShowingAnEmptySchedule() {
        // The baseline, and the reason the header states "first attempt" instead of leaving
        // the line blank: a missing schedule is a fact about this Problem, not an absence of
        // information. Without it the header simply lost a line and looked like a rendering
        // bug.
        launch()
        openTwoSum()
        compose.onNode(hasText("first attempt", substring = true)).assertIsDisplayed()
    }

    @Test
    fun finalizingShowsTheIntervalAndTheMemoryStrengthBehindIt() {
        // "Next review in 6 days" alone does not say whether the review *helped*. Stability
        // moving from one span to another does, and it is the quantity FSRS actually
        // optimises — so it is the evidence that answers the complaint.
        launch()
        solveTwoSum()

        compose.onNodeWithText("Good").performClick()

        compose.onNode(hasText("Next review in", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("Memory strength", substring = true))
            .performScrollTo()
            .assertIsDisplayed()

        // And what is displayed is what was stored, not a recomputation. Read the transition
        // back out of the review and require the rendered text to contain its value, so a
        // card that showed a plausible-but-different number would fail.
        val twoSum = profile.catalogue.allProblems().first { it.title == TWO_SUM_TITLE }
        val review = profile.history(twoSum.id).last()
        val nextStability = formatIntervalDays(review.transition.nextStability)
        compose.onNode(hasText(nextStability, substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aReviewedTechniquesQueueCardCarriesItsIntervalAndReviewCount() {
        // The queue is where the learner chooses what to work on, and before this every due
        // card looked identical — so the ordering the queue had already computed, soonest due
        // first, was information the UI threw away.
        //
        // A lapse is what returns a technique to the queue in one step: FSRS-7 reschedules it
        // *seconds* out rather than days, which is also the case a whole-day interval format
        // would render as "0 days". Those seconds are still the future, so the clock moves
        // rather than the test sleeping — sleeping would be slow when it worked and flaky
        // when it did not.
        launch()
        solveTwoSum()
        compose.onNodeWithText("Again").performClick()
        clock.advanceBy(1.minutes)
        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        // Read the interval back out of the stored topic schedule rather than naming a span,
        // so a card showing a plausible-but-different number fails. There is one card per
        // technique the Problem is tagged with and they were all advanced by the same review,
        // so several may carry the same wording — the first is enough.
        val schedule = requireNotNull(profile.reviews.topicSchedule(twoSumTopics().first())) {
            "the review did not fan out to its techniques"
        }
        compose.onAllNodesWithText(
            "Memory lasts about ${formatIntervalDays(schedule.intervalDays)}",
            substring = true,
        ).onFirst().performScrollTo().assertIsDisplayed()
        compose.onAllNodesWithText("reviewed 1×", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()

        // Due, and saying so on the card itself. A technique a minute past due *is* simply
        // due — the badge earns its stronger wording on the overdue case below, so nothing
        // here may claim to be overdue.
        assertTrue(
            "the due badge should be on the card",
            compose.onAllNodesWithText("Due now").fetchSemanticsNodes().isNotEmpty(),
        )
        assertEquals(
            0,
            compose.onAllNodesWithText("Overdue by", substring = true)
                .fetchSemanticsNodes()
                .size,
        )
    }

    @Test
    fun aBadlyOverdueTechniqueSaysSoRatherThanLookingLikeTheRest() {
        // What the badge is *for*. A technique three days late and one a minute late are both
        // due, and treating them the same is how a backlog stops being legible.
        //
        // Backdated rather than fast-forwarded, and that direction is forced: the queue cards
        // describe due times against `Clock.System.now()` while only the profile's clock is
        // injectable. Moving the profile into the past is the one arrangement where both
        // clocks agree about what happened.
        clock.set(Clock.System.now() - 3.days)
        launch()
        solveTwoSum()
        compose.onNodeWithText("Again").performClick()
        clock.advanceBy(1.minutes)
        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        // `onAllNodes` rather than `onNode`: one review advances every technique the Problem
        // is tagged with, so three cards are overdue together. That is the fan-out working,
        // not an ambiguous match.
        compose.onAllNodesWithText("Overdue by", substring = true)
            .onFirst()
            .performScrollTo()
            .assertIsDisplayed()
        // And no card is *also* claiming to be merely due, which would be two contradictory
        // verdicts on the same schedule.
        assertEquals(
            0,
            compose.onAllNodesWithText("Due now").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun theProgressScreenExplainsWhatTheSchedulerIsDoing() {
        // The numbers on this card were all computed by `Statistics` and rendered nowhere on
        // Android. Naming FSRS-7 explicitly is deliberate: the learner asked whether it was
        // hooked up, and the answer should be somewhere they can look.
        launch()
        solveTwoSum()
        compose.onNodeWithText("Good").performClick()
        compose.onNodeWithText("Back to queue").performScrollTo().performClick()
        compose.onNodeWithText("Progress").performClick()

        compose.onNodeWithText("Your schedule").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("FSRS-7", substring = true)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Due tomorrow").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Average interval").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not yet attempted").performScrollTo().assertIsDisplayed()

        // A real average, not an em dash: one solved Problem is enough to have one, and "—"
        // here would mean the card is showing a placeholder where the scheduler has an
        // answer.
        val average = requireNotNull(profile.statistics().averageIntervalDays)
        assertTrue("a solved Problem must have an interval, was $average", average > 0.0)
    }

    @Test
    fun theThemeChoiceIsStoredAndTakesEffectImmediately() {
        // `BeeCodeTheme` accepted a choice that nothing ever passed, so the stored preference
        // was written and then ignored until the next launch — a setting that looks broken
        // while working correctly. Asserted through the profile as well as the UI, because
        // the UI alone cannot distinguish "applied" from "applied and forgotten".
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Appearance").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Light").performScrollTo().performClick()
        assertEquals(ThemeChoice.LIGHT, profile.settings.themeChoice())

        compose.onNodeWithText("Dark").performScrollTo().performClick()
        assertEquals(ThemeChoice.DARK, profile.settings.themeChoice())
    }

    /** Run a passing solution for Two Sum, leaving the rating buttons on screen. */
    /**
     * Scroll the queue until [title] is composed, and return it.
     *
     * The catalogue grows, so a Problem that was once the first row ends up below the
     * fold — and a lazy list does not compose what is off screen, so a node that is
     * merely present in the data has no semantics to assert against.
     */
    private fun scrollQueueTo(title: String) = run {
        compose.onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(title))
        compose.onAllNodesWithText(title).onFirst()
    }

    private fun openTwoSum() {
        scrollQueueTo(TWO_SUM_TITLE).performClick()
    }

    /**
     * The techniques Two Sum rehearses, read out of the pack rather than named.
     *
     * The pack's `taxonomy.yaml` owns these slugs and has already renamed one, so a literal
     * here would turn a reviewed content change into a failure of the schedule rendering.
     */
    private fun twoSumTopics(): List<String> =
        profile.catalogue.allProblems().first { it.title == TWO_SUM_TITLE }.topics

    private fun solveTwoSum() {
        openTwoSum()
        compose.onNodeWithContentDescription("Python solution editor").performTextReplacement(
            """
            ${ScriptedPythonRunner.PASS_MARKER}
            def two_sum(nums, target):
                seen = {}
                for index, value in enumerate(nums):
                    if target - value in seen:
                        return [seen[target - value], index]
                    seen[value] = index
                return []
            """.trimIndent(),
        )
        compose.onNodeWithText("Run tests").performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("All tests passed").fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun launch() {
        compose.setContent {
            BeeCodeTheme {
                BeeCodeApp(StudyViewModel(profile))
            }
        }
    }

    private companion object {
        /** The Problem these tests drive. Solvable in a few lines and stable content. */
        const val TWO_SUM_TITLE = "Two Sum"
    }
}
