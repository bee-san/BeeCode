package dev.bee.beecode.android

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
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.QUEUE_LIST_TAG
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import android.os.SystemClock
import android.view.MotionEvent
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
 * The same is true of a `-no-window` emulator, including the one CI runs. So these
 * tests **skip** unless the device actually accepts injected touch input, and that is
 * verified by attempting an injection rather than inferred from display metrics or the
 * build product name — inferring it was wrong once already, and the consequence was
 * nine tests that had never run anywhere while looking merely skipped.
 *
 * [AndroidStudyJourneyTest] carries no such requirement and is the behavioural gate
 * that always runs, on this host and in CI.
 *
 * ### These are not the only run of these assertions
 *
 * [BeeCodeUiRobolectricTest] asserts the same tree on the JVM, with no emulator, and
 * runs on every push. That is where these assertions are actually verified today; this
 * class is the stronger version for when a rendering-capable emulator exists, because
 * it exercises real layout, real touch dispatch, and real Chaquopy.
 *
 * Several assertions here were *wrong* until the Robolectric run exposed them — nodes
 * asserted as displayed while below the fold, and a `performScrollTo` on a node with no
 * scrollable ancestor. Keep the two files in step: a fix found in one almost certainly
 * applies to the other.
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
            "This device refuses injected touch input, so Compose performClick and " +
                "assertIsDisplayed cannot work. Run on a rendering-capable emulator.",
            canInjectTouchInput(),
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

    private fun scrollQueueTo(title: String) = run {
        compose.onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(title))
        compose.onAllNodesWithText(title).onFirst()
    }

    private fun openTwoSum() {
        scrollQueueTo(TWO_SUM_TITLE).performClick()
    }

    @Test
    fun theQueueShowsTheBundledProblems() {
        launch()
        compose.onNodeWithText("BeeCode").assertIsDisplayed()
        compose.onNodeWithText("New Problems").assertIsDisplayed()
        // The pack's Problems are offered by title.
        scrollQueueTo(TWO_SUM_TITLE).assertIsDisplayed()
    }

    @Test
    fun theNavigationBarReachesEveryDestination() {
        launch()
        compose.onNodeWithText("Progress").performClick()
        compose.onAllNodesWithText("Progress").onFirst().assertIsDisplayed()
        compose.onNodeWithText("Achievements").assertIsDisplayed()

        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Daily review limit").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Python execution").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Backup").performScrollTo().assertIsDisplayed()

        compose.onNodeWithText("Study").performClick()
        compose.onNodeWithText("New Problems").assertIsDisplayed()
    }

    @Test
    fun openingAProblemShowsItsStatementAndEditor() {
        launch()
        openTwoSum()

        // The Problem view replaces the whole screen, navigation bar included, so the
        // learner cannot lose an attempt to a stray tab tap.
        compose.onNodeWithText("Problem").assertIsDisplayed()
        compose.onNodeWithText("Your solution").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Run tests").assertIsDisplayed()
        // The editor is reachable by its accessibility label.
        compose.onNodeWithContentDescription("Python solution editor").assertIsDisplayed()
    }

    @Test
    fun theSymbolRowIsPresentForPythonPunctuation() {
        // A phone keyboard buries or omits the colon and brackets Python needs, and
        // indentation is syntactically significant.
        launch()
        openTwoSum()
        // Below the fold of the Problem screen's scrolling column on a phone, so it
        // needs scrolling to before it has layout bounds. Found by the Robolectric
        // equivalent — this assertion was wrong for as long as it had never run.
        compose.onNodeWithText("Your solution").performScrollTo()
        compose.onNodeWithContentDescription("Insert indent")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert :").assertIsDisplayed()
        compose.onNodeWithContentDescription("Insert (").assertIsDisplayed()
    }

    @Test
    fun theRevealPromptStatesTheCostBeforeTheLearnerCommits() {
        // Revealing is legitimate when genuinely stuck, but it must be an informed
        // choice: it forfeits the solve and caps the rating at Hard.
        launch()
        openTwoSum()
        compose.onNodeWithText("Stuck?").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("will not count", substring = true))
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithText("Show the explanation").performScrollTo().performClick()
        compose.onNodeWithText("Explanation").performScrollTo().assertIsDisplayed()
        // And the UI says the ceiling has dropped rather than silently disabling
        // buttons. The badge is in the header, so after scrolling down to the reveal
        // control it is *above* the fold — scroll back to it.
        compose.onNodeWithText("Answer revealed").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun runningAWrongSolutionShowsAFailureAndOnlyOffersAgain() {
        launch()
        openTwoSum()

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
        openTwoSum()

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
        compose.onNodeWithText("All tests passed").performScrollTo().assertIsDisplayed()

        // An unaided pass offers every rating. These live in the Scaffold's bottomBar,
        // always on screen and outside the scroll — so unlike the banner above they
        // must NOT be scrolled to; performScrollTo fails without a scrollable ancestor.
        compose.onNodeWithText("Good").assertIsDisplayed()
        compose.onNodeWithText("Easy").assertIsDisplayed()
        compose.onNodeWithText("Good").performClick()

        // And the outcome tells the learner when they will see it again.
        compose.onNodeWithText("Solved").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("Next review in", substring = true))
            .performScrollTo()
            .assertIsDisplayed()

        compose.onNodeWithText("Back to queue").performScrollTo().performClick()

        // The queue reflects the solve.
        // Derived from the catalogue rather than hard-coded: a literal "1 of 12" turns
        // adding a Problem into a UI test failure, which teaches the wrong lesson.
        val total = requireNotNull(profile).catalogue.allProblems().size
        compose.onNode(hasText("1 of $total solved", substring = true)).assertIsDisplayed()
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
        compose.onNode(hasText("In this app's process", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
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
        const val TWO_SUM_TITLE = "Two Sum"

        /**
         * Whether this device can actually accept injected touch input.
         *
         * Tested by **doing it**, not inferred. An earlier version checked display
         * metrics and the build product name, which was a proxy for the real
         * capability and got it wrong: CI's `-no-window` emulator reports a non-zero
         * display and is not named "atd", so the check passed and every test then
         * failed with "Failed to inject touch input". The result was nine tests that
         * had never run anywhere while appearing to be merely skipped.
         *
         * `injectInputEvent` needs the INJECT_EVENTS permission, which instrumentation
         * holds only when the platform allows it. If the call is refused here, it will
         * be refused by `performClick` too.
         */
        fun canInjectTouchInput(): Boolean = runCatching {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            val now = SystemClock.uptimeMillis()
            // A no-op event at the origin: nothing to interact with, so it cannot
            // disturb whatever is on screen, but it exercises the same path.
            val event = MotionEvent.obtain(
                now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0,
            )
            try {
                instrumentation.uiAutomation.injectInputEvent(event, true)
            } finally {
                event.recycle()
            }
        }.getOrDefault(false)
    }
}
