package dev.bee.beecode.android

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
import android.app.Application
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Android UI, asserted on the JVM.
 *
 * ### Why this exists
 *
 * [BeeCodeUiTest] contains the same assertions as instrumented tests, and they had
 * never executed anywhere. Compose's `performClick` needs injected touch input and
 * `assertIsDisplayed` needs real layout bounds, and no emulator available to this
 * project provides them: this dev host has no `/dev/kvm`, so only Google's
 * automated-test-device images boot and they render nothing at all, while CI's
 * `-no-window` emulator refuses touch injection. So those tests skipped in both
 * places — written, compiled, and never run.
 *
 * Robolectric removes the emulator from the equation. It provides a real Android
 * runtime on the JVM with working layout and input, so these run on every
 * `./gradlew test`, on this host and in CI.
 *
 * ### What this does and does not prove
 *
 * It proves the composed tree: that the queue lists Problems, that navigation reaches
 * every destination, that a failing run offers only *Again*, that revealing states its
 * cost first, and that Settings does not call the runner a sandbox. Those are the
 * assertions that were unverified.
 *
 * It does not prove real rendering or a real interpreter. Robolectric's canvas is a
 * no-op, so it cannot catch a pixel-level regression, and Python here is
 * [ScriptedPythonRunner]. Both are covered elsewhere: [AndroidStudyJourneyTest] runs the
 * journey against Chaquopy and real SQLite on a device, and the instrumented
 * [BeeCodeUiTest] remains for when a rendering-capable emulator is available.
 *
 * The database is real SQLite via JDBC — the same persistence both clients use.
 */
@RunWith(RobolectricTestRunner::class)
// SDK 35 rather than the compileSdk: Robolectric ships prebuilt Android runtimes and
// pinning one keeps the suite from silently changing behaviour when compileSdk moves.
//
// The qualifier is load-bearing. Robolectric's default window is far smaller than any
// real phone, so items below the fold of a lazy list have no layout bounds and
// `assertIsDisplayed` fails even though the node exists — which is exactly how this
// suite failed first. 411x891dp is a typical modern phone in portrait, matching the
// form factor the UI is designed for.
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class BeeCodeUiRobolectricTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHostActivity>()

    private lateinit var profile: BeeCodeProfile
    private lateinit var runner: ScriptedPythonRunner

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        // sqlite-jdbc extracts its native library into java.io.tmpdir. On the JVM that
        // is already writable, but pointing it at the app cache keeps this identical to
        // what the real Application does — see ADR 0003.
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)

        // The real compiled Problem pack out of the APK's assets, not a fixture, so
        // these tests fail if the pack stops loading.
        val catalogue = context.assets.open(BeeCodeApplication.PACK_ASSET)
            .bufferedReader()
            .use { ProblemCatalogue.fromPackJson(it.readText()) }

        runner = ScriptedPythonRunner()
        // In-memory: every test gets a clean profile with no file to clean up, and the
        // schema is created by the same migrations the shipped app runs.
        profile = BeeCodeProfile.inMemory(catalogue = catalogue, runner = runner)
    }

    @After
    fun tearDown() {
        profile.close()
    }

    private fun launch() {
        compose.setContent {
            BeeCodeTheme {
                BeeCodeApp(StudyViewModel(profile))
            }
        }
    }

    @Test
    fun theQueueShowsTheBundledProblems() {
        launch()
        compose.onNodeWithText("BeeCode").assertIsDisplayed()
        compose.onNodeWithText("New Problems").assertIsDisplayed()
        compose.onAllNodesWithText("Two Sum").onFirst().assertIsDisplayed()
    }

    @Test
    fun theNavigationBarReachesEveryDestination() {
        launch()
        compose.onNodeWithText("Progress").performClick()
        compose.onAllNodesWithText("Progress").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Achievements").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Daily review limit").assertIsDisplayed()
        compose.onNodeWithText("Python execution").assertIsDisplayed()
        compose.onNodeWithText("Backup").assertIsDisplayed()

        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithText("New Problems").assertIsDisplayed()
    }

    @Test
    fun openingAProblemShowsItsStatementAndEditor() {
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()

        // The Problem view replaces the whole screen, navigation bar included, so a
        // stray tab tap cannot lose an attempt in progress.
        compose.onNodeWithText("Problem").assertIsDisplayed()
        compose.onNodeWithText("Your solution").assertIsDisplayed()
        compose.onNodeWithText("Run tests").assertIsDisplayed()
        compose.onNodeWithContentDescription("Python solution editor").assertIsDisplayed()
    }

    @Test
    fun theSymbolRowIsPresentForPythonPunctuation() {
        // A phone keyboard buries or omits the colon and brackets Python needs, and
        // indentation is syntactically significant.
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()
        // Asserted by existence rather than by display, deliberately.
        //
        // The symbol row is a horizontally scrolling Row nested inside the screen's
        // vertical scroll, and under Robolectric its children compose with a real size
        // (50x80) but are never placed — `boundsInRoot` stays 0x0, so
        // `assertIsDisplayed` cannot pass no matter how the test scrolls. Robolectric's
        // canvas is a no-op and it does not resolve placement for this nested-scroll
        // case; that is a limitation of the harness, not a defect in the row.
        //
        // Existence still carries the claim worth making here — that BeeCode offers the
        // punctuation a phone keyboard buries, and an indent key because indentation is
        // syntactically significant in Python. Whether those keys are visibly on screen
        // is left to the instrumented [BeeCodeUiTest] on a rendering-capable emulator.
        compose.onNodeWithText("Your solution").performScrollTo()
        listOf("Insert indent", "Insert :", "Insert (", "Insert [").forEach { key ->
            compose.onNodeWithContentDescription(key)
                .assertExists("the symbol row must offer $key")
        }
    }

    @Test
    fun theRevealPromptStatesTheCostBeforeTheLearnerCommits() {
        // Revealing is legitimate when genuinely stuck, but it must be an informed
        // choice: it forfeits the solve and caps the rating at Hard.
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()
        compose.onNodeWithText("Stuck?").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("will not count", substring = true))
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithText("Show the explanation").performScrollTo().performClick()
        compose.onNodeWithText("Explanation").performScrollTo().assertIsDisplayed()
        // The "Answer revealed" badge lives in the header, so after scrolling down to
        // the reveal control it is above the fold rather than below it. Scroll back.
        compose.onNodeWithText("Answer revealed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun runningAWrongSolutionShowsAFailureAndOnlyOffersAgain() {
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()

        compose.onNodeWithContentDescription("Python solution editor")
            .performTextReplacement("def two_sum(nums, target):\n    return [9, 9]\n")
        compose.onNodeWithText("Run tests").performClick()

        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onAllNodesWithText("Again").fetchSemanticsNodes().isNotEmpty()
        }

        // A failure permits only Again. Good and Easy are absent rather than disabled,
        // which states the rule instead of inviting an argument with it.
        compose.onNodeWithText("Again").assertIsDisplayed()
        assert(compose.onAllNodesWithText("Good").fetchSemanticsNodes().isEmpty()) {
            "Good must not be offered for a failing run"
        }
        assert(compose.onAllNodesWithText("Easy").fetchSemanticsNodes().isEmpty()) {
            "Easy must not be offered for a failing run"
        }
    }

    @Test
    fun theFullAnswerRunFinalizeJourneyWorksThroughTheUi() {
        launch()
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
        compose.onNodeWithText("All tests passed").performScrollTo().assertIsDisplayed()

        // An unaided pass offers every rating. The rating buttons live in the Scaffold's
        // bottomBar, outside the scrolling column and always on screen, so scrolling to
        // them is not just unnecessary but fails outright — they have no scrollable
        // ancestor.
        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Good").performClick()

        // And the outcome tells the learner when they will see it again. The finalized
        // card is appended to the scrolling column rather than replacing the screen, so
        // it lands below the fold and needs scrolling to — unlike the rating buttons
        // above, which sit in the Scaffold's always-visible bottomBar.
        compose.onNodeWithText("Solved").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Next review in", substring = true))
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        // The queue reflects the solve, which means the click reached persistence.
        compose.onNode(hasText("1 of 12 solved", substring = true)).assertIsDisplayed()
    }

    @Test
    fun settingsStatesTheAndroidRunnerLimitationPlainly() {
        // The plan requires that same-process execution is never called a sandbox.
        // This asserts the UI actually says so, rather than only a code comment.
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNode(hasText("not a security sandbox", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("In this app's process", substring = true)).assertIsDisplayed()
    }

    @Test
    fun theDailyLimitCanBeChangedFromSettings() {
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("10").performScrollTo().performClick()
        // Read back through the profile, so this proves the click reached persistence
        // and not merely the UI's own state.
        assert(profile.settings.dailyReviewLimit() == 10) {
            "expected a stored daily limit of 10, got ${profile.settings.dailyReviewLimit()}"
        }
    }
}
