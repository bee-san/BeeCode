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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import android.content.Context
import android.util.DisplayMetrics
import android.view.WindowManager
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Android UI, driven through Compose's test harness.
 *
 * These assert the composed tree — that the queue lists Problems, that opening one
 * shows an editor, that a failing run offers only *Again*, and that Settings states
 * the runner limitation in plain words. They cover the wiring between the UI and the
 * shared study service that [AndroidStudyJourneyTest] exercises directly.
 *
 * ### These require a rendering-capable emulator
 *
 * Compose's `assertIsDisplayed` needs real layout bounds and `performClick` needs
 * touch injection, so both need a display. Google's automated-test-device ("atd" /
 * "gslim") images have none: they fail with "Failed to inject touch input" and
 * produce an all-black framebuffer for every app including system ones.
 *
 * A rendering image needs hardware acceleration, so a host without `/dev/kvm` can
 * only boot ATD. Rather than fail misleadingly there, these tests **skip** when no
 * display is available, and CI enables KVM so they run for real. [
 * AndroidStudyJourneyTest] carries no such requirement and is the behavioural gate
 * that always runs.
 */
@RunWith(AndroidJUnit4::class)
class BeeCodeUiTest {

    // createAndroidComposeRule with our own debug-only host activity, rather than
    // createComposeRule's default. The debug build type suffixes the application id,
    // so the default host lands in the test package and launching it fails with
    // "Intent in process ... resolved to different process".
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHostActivity>()

    // Nullable rather than lateinit: when the display assumption fails, setUp never
    // reaches the assignment, and a lateinit access from tearDown would turn a clean
    // skip into a spurious failure.
    private var databaseFile: File? = null
    private var profile: BeeCodeProfile? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assumeTrue(
            "This emulator image has no display, so Compose touch input and layout " +
                "bounds are unavailable. Run on a rendering-capable image.",
            hasUsableDisplay(context),
        )
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)

        val application = context.applicationContext as BeeCodeApplication
        val file = File(context.cacheDir, "beecode-ui-${System.nanoTime()}.db")
        databaseFile = file
        profile = BeeCodeProfile.open(
            databasePath = file.absolutePath,
            catalogue = application.catalogue,
            runner = application.runner,
        )
    }

    @After
    fun tearDown() {
        profile?.close()
        databaseFile?.let { file ->
            file.delete()
            File(file.absolutePath + "-wal").delete()
            File(file.absolutePath + "-shm").delete()
        }
    }

    private fun launch() {
        val opened = requireNotNull(profile) { "the profile should have been opened in setUp" }
        compose.setContent {
            BeeCodeTheme {
                BeeCodeApp(StudyViewModel(opened))
            }
        }
    }

    @Test
    fun theQueueShowsTheBundledProblems() {
        launch()
        compose.onNodeWithText("BeeCode").assertIsDisplayed()
        compose.onNodeWithText("New Problems").assertIsDisplayed()
        // The pack's Problems are offered by title.
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

        // The Problem view replaces the whole screen, navigation bar included, so the
        // learner cannot lose an attempt to a stray tab tap.
        compose.onNodeWithText("Problem").assertIsDisplayed()
        compose.onNodeWithText("Your solution").assertIsDisplayed()
        compose.onNodeWithText("Run tests").assertIsDisplayed()
        // The editor is reachable by its accessibility label.
        compose.onNodeWithContentDescription("Python solution editor").assertIsDisplayed()
    }

    @Test
    fun theSymbolRowIsPresentForPythonPunctuation() {
        // A phone keyboard buries or omits the colon and brackets Python needs, and
        // indentation is syntactically significant.
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()
        compose.onNodeWithContentDescription("Insert indent").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert :").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert (").assertIsDisplayed()
    }

    @Test
    fun theRevealPromptStatesTheCostBeforeTheLearnerCommits() {
        // Revealing is legitimate when genuinely stuck, but it must be an informed
        // choice: it forfeits the solve and caps the rating at Hard.
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()
        compose.onNodeWithText("Stuck?").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("will not count", substring = true)).assertIsDisplayed()

        compose.onNodeWithText("Show the explanation").performScrollTo().performClick()
        compose.onNodeWithText("Explanation").performScrollTo().assertIsDisplayed()
        // And the UI says the ceiling has dropped rather than silently disabling
        // buttons.
        compose.onNodeWithText("Answer revealed").assertIsDisplayed()
    }

    @Test
    fun runningAWrongSolutionShowsAFailureAndOnlyOffersAgain() {
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()

        compose.onNodeWithContentDescription("Python solution editor")
            .performTextReplacement("def two_sum(nums, target):\n    return [9, 9]\n")
        compose.onNodeWithText("Run tests").performClick()

        // Chaquopy's first entry into CPython imports the standard library out of the
        // APK, which takes tens of seconds on an emulator.
        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithText("Again").fetchSemanticsNodes().isNotEmpty()
        }

        // A failure permits only Again. Good and Easy are absent rather than
        // disabled, which states the rule instead of inviting an argument with it.
        compose.onNodeWithText("Again").assertIsDisplayed()
        compose.onAllNodesWithText("Good").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Good must not be offered for a failing run" }
        }
        compose.onAllNodesWithText("Easy").fetchSemanticsNodes().let {
            assert(it.isEmpty()) { "Easy must not be offered for a failing run" }
        }
    }

    @Test
    fun theFullAnswerRunFinalizeJourneyWorksThroughTheUi() {
        launch()
        compose.onAllNodesWithText("Two Sum").onFirst().performClick()

        compose.onNodeWithContentDescription("Python solution editor").performTextReplacement(
            """
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

        compose.waitUntil(timeoutMillis = 180_000) {
            compose.onAllNodesWithText("All tests passed").fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText("All tests passed").assertIsDisplayed()

        // An unaided pass offers every rating.
        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Good").performClick()

        // And the outcome tells the learner when they will see it again.
        compose.onNodeWithText("Solved").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Next review in", substring = true)).assertIsDisplayed()

        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        // The queue reflects the solve.
        compose.onNode(hasText("1 of 12 solved", substring = true)).assertIsDisplayed()
    }

    @Test
    fun settingsStatesTheAndroidRunnerLimitationPlainly() {
        // The plan requires that same-process execution is not called a sandbox. This
        // asserts the UI actually says so, rather than only a code comment.
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
        // Reading it back through the profile proves the click reached persistence and
        // not merely the UI's own state.
        val settings = requireNotNull(profile).settings
        assert(settings.dailyReviewLimit() == 10) {
            "expected a stored daily limit of 10, got ${settings.dailyReviewLimit()}"
        }
    }

    private companion object {
        /**
         * Whether this device can actually render and accept touch input.
         *
         * ATD images report a display of zero size, which is what makes
         * `assertIsDisplayed` and `performClick` fail there.
         */
        fun hasUsableDisplay(context: Context): Boolean = runCatching {
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager)
                .defaultDisplay
                .getMetrics(metrics)
            metrics.widthPixels > 0 && metrics.heightPixels > 0 &&
                // ATD images are identifiable by name and render nothing.
                !android.os.Build.PRODUCT.contains("atd") &&
                !android.os.Build.PRODUCT.contains("gslim")
        }.getOrDefault(false)
    }
}
