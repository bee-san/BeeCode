package dev.bee.beecode.android

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.core.app.ApplicationProvider
import dev.bee.beecode.android.ui.BeeCodeApp
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
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()
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
        val twoSum = profile.catalogue.allProblems().first { it.title == "Two Sum" }
        val review = profile.history(twoSum.id).last()
        val nextStability = formatIntervalDays(review.transition.nextStability)
        compose.onNode(hasText(nextStability, substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aReviewedProblemsQueueRowCarriesItsIntervalAndReviewCount() {
        // The queue is where the learner chooses what to work on, and before this every due
        // row looked identical — so the ordering the queue had already computed, soonest due
        // first, was information the UI threw away.
        //
        // A lapse is what returns a Problem to the queue in one step: FSRS-7 reschedules it
        // *seconds* out rather than days, which is also the case a whole-day interval format
        // would render as "0 days". Those seconds are still the future, so the clock moves
        // rather than the test sleeping — sleeping would be slow when it worked and flaky
        // when it did not.
        launch()
        solveTwoSum()
        compose.onNodeWithText("Again").performClick()
        clock.advanceBy(1.minutes)
        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        compose.onNode(hasText("interval", substring = true)).assertIsDisplayed()
        compose.onNode(hasText("Reviewed 1×", substring = true)).assertIsDisplayed()
        // Two: the section header and the row's own badge. Both say "Due now" because a
        // Problem a minute past due *is* simply due — the badge earns its place on the
        // overdue case below, not on this one.
        assertEquals(
            2,
            compose.onAllNodesWithText("Due now").fetchSemanticsNodes().size,
        )
    }

    @Test
    fun aBadlyOverdueProblemSaysSoRatherThanLookingLikeTheRest() {
        // What the badge is *for*. A Problem three days late and one a minute late are both
        // in "Due now", and treating them the same is how a backlog stops being legible.
        //
        // Backdated rather than fast-forwarded, and that direction is forced: the queue rows
        // describe due times against `Clock.System.now()` while only the profile's clock is
        // injectable. Moving the profile into the past is the one arrangement where both
        // clocks agree about what happened.
        clock.set(Clock.System.now() - 3.days)
        launch()
        solveTwoSum()
        compose.onNodeWithText("Again").performClick()
        clock.advanceBy(1.minutes)
        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        compose.onNode(hasText("Overdue by", substring = true)).assertIsDisplayed()
        // And it is not *also* claiming to be merely due: only the section header should say
        // that, so a row cannot carry two contradictory verdicts.
        assertEquals(
            1,
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
    private fun solveTwoSum() {
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()
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
}
