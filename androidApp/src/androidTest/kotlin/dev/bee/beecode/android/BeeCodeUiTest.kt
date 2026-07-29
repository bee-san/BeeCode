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
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Android UI, driven through Compose's test harness.
 *
 * This asserts the **composed tree**, not pixels, which is deliberately stronger
 * evidence on a headless device: the automated-test-device emulator image renders no
 * pixels at all, so a screenshot proves nothing, while the semantics tree is fully
 * present and can be queried and clicked.
 *
 * These tests cover the wiring between the UI and the shared study service that
 * `AndroidStudyJourneyTest` exercises directly. Together they mean a real learner's
 * path through the app is verified end to end.
 */
@RunWith(AndroidJUnit4::class)
class BeeCodeUiTest {

    // createAndroidComposeRule with our own debug-only host activity, rather than
    // createComposeRule's default. The debug build type suffixes the application id,
    // so the default host lands in the test package and launching it fails with
    // "Intent in process ... resolved to different process".
    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHostActivity>()

    private lateinit var databaseFile: File
    private lateinit var profile: BeeCodeProfile

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)

        val application = context.applicationContext as BeeCodeApplication
        databaseFile = File(context.cacheDir, "beecode-ui-${System.nanoTime()}.db")
        profile = BeeCodeProfile.open(
            databasePath = databaseFile.absolutePath,
            catalogue = application.catalogue,
            runner = application.runner,
        )
    }

    @After
    fun tearDown() {
        profile.close()
        databaseFile.delete()
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
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
        assert(profile.settings.dailyReviewLimit() == 10) {
            "expected a stored daily limit of 10, got ${profile.settings.dailyReviewLimit()}"
        }
    }
}
