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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
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
        // Derived from the catalogue rather than hard-coded: a literal "1 of 12" turns
        // adding a Problem into a UI test failure, which teaches the wrong lesson.
        val total = profile.catalogue.allProblems().size
        compose.onNode(hasText("1 of $total solved", substring = true)).assertIsDisplayed()
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
    fun syncIsOffByDefaultAndStatesItsPrivacyCostFirst() {
        // Sync is opt-in because it writes source code to storage BeeCode does not
        // control. The default must be off, the UI must say off rather than showing a
        // dead button, and the consequence must be stated *before* the learner picks a
        // file. Mirrors the desktop assertion so the two clients cannot drift.
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Sync between devices").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not set — sync is off").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("contains your solutions", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(null, profile.settings.syncFilePath())
    }

    @Test
    fun theSyncCardSaysBeeCodeNeedsNoStoragePermission() {
        // Worth asserting in the UI rather than trusting the manifest: the no-permission
        // property is a claim BeeCode makes to the learner, and adding sync is exactly
        // the kind of change that would quietly break it.
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNode(hasText("no storage permission", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun theWebDavOptionIsOfferedAndStatesItsRequirements() {
        // Mirrors the desktop assertion deliberately: where the two clients make the same
        // promise, a divergence should fail on one and not the other. Both constraints must
        // be visible before a learner types a credential, not discovered afterwards.
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Or a WebDAV server").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("WebDAV file URL").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("https is required", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        // Android encrypts the credential with a keystore key, so the card must say that
        // rather than the "unencrypted" warning the desktop still correctly shows. The two
        // clients differ here because the platforms genuinely differ, and claiming uniform
        // pessimism would understate what Android does.
        compose.onNode(hasText("encrypted with a key held in this device", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("cannot overwrite each other", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun webDavSettingsArePersistedSoTheyAreNotRetyped() {
        // Written through the ViewModel rather than by driving three text fields: the
        // assertion worth making is that blank clears to null instead of storing an empty
        // string, which would read as "configured" everywhere else in the app.
        launch()
        val model = StudyViewModel(profile)
        model.setWebDav("https://cloud.example.com/beecode-sync.json", "someone", "hunter2")
        assertEquals("https://cloud.example.com/beecode-sync.json", profile.settings.syncWebDavUrl())
        assertEquals("someone", profile.settings.syncWebDavUsername())

        model.setWebDav("https://cloud.example.com/beecode-sync.json", "", "")
        assertEquals(null, profile.settings.syncWebDavUsername())
        assertEquals(null, profile.settings.syncWebDavPassword())
    }

    @Test
    fun theLeaderboardIsOffByDefaultAndSaysWhatABoardWouldSee() {
        // Mirrors the desktop assertion. Where two clients make the same promise about what
        // is shared, saying it differently is how they drift — and this is a privacy promise,
        // so the wording is the product.
        launch()
        compose.onNodeWithText("Settings").performClick()
        compose.onNodeWithText("Leaderboard").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Not joined").performScrollTo().assertIsDisplayed()
        compose.onNode(hasText("counts and streaks", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("Never your code", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("before you join is never shared", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNode(hasText("does not exist yet", substring = true))
            .performScrollTo()
            .assertIsDisplayed()
        assertEquals(null, profile.settings.leaderboardLinkedAt())
    }

    @Test
    fun joiningRecordsNowAsTheCutoffSoEarlierWorkStaysPrivate() {
        // Through the ViewModel rather than the button: the assertion worth making is that
        // the cutoff is *now*, which is the single thing keeping a learner's back catalogue
        // out of a board.
        launch()
        val model = StudyViewModel(profile)
        val before = kotlinx.datetime.Clock.System.now()
        model.joinLeaderboard()

        val linkedAt = requireNotNull(profile.settings.leaderboardLinkedAt())
        // A one-second window rather than `>= before`. Settings store epoch *millis*, so the
        // read-back value is truncated and can land microseconds below the instant captured
        // above — which made the strict comparison fail for a reason that has nothing to do
        // with the rule being tested.
        //
        // What matters is that the cutoff is *now* and not the beginning of time: a
        // backdated cutoff is what would share a learner's whole back catalogue.
        assertTrue(
            "the cutoff must be the moment of joining, was $linkedAt",
            linkedAt >= before - kotlin.time.Duration.parse("1s"),
        )
        assertTrue(
            "the cutoff must not be backdated, was $linkedAt",
            linkedAt > kotlinx.datetime.Instant.fromEpochMilliseconds(0),
        )
        assertTrue(model.leaderboardJoined())
        assertEquals(0, model.leaderboardStatus().pending)
    }

    @Test
    fun leavingDiscardsTheQueueAndKeepsEveryReview() {
        launch()
        val model = StudyViewModel(profile)
        model.joinLeaderboard()
        model.leaveLeaderboard()

        assertEquals(null, profile.settings.leaderboardLinkedAt())
        assertTrue(!model.leaderboardJoined())
        assertEquals(0, model.leaderboardStatus().pending)
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
