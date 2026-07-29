package dev.bee.beecode.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The desktop UI, asserted headlessly.
 *
 * `DesktopWiringTest` covers everything below the UI — pack loading, profile opening,
 * the study loop through the real runner — and its comment used to say the UI itself
 * "needs a display". It does not: Compose Desktop's [runComposeUiTest] composes, lays
 * out, and dispatches input on the JVM with no window and no X11, so the desktop client
 * had no UI coverage for no good reason.
 *
 * This is the desktop counterpart to `BeeCodeUiRobolectricTest`, and the two assert
 * deliberately similar things. Where the clients are supposed to agree — a failed run
 * permits only *Again*, an unaided pass permits everything, Settings does not call the
 * runner a sandbox — a divergence should fail on one platform and not the other, which
 * is how conformance gets checked rather than assumed.
 *
 * Python is [ScriptedPythonRunner]; real interpreter behaviour is `:content-tools`'s and
 * `:python-api`'s job. The database is real SQLite via JDBC.
 */
@OptIn(ExperimentalTestApi::class)
class DesktopUiTest {

    /**
     * Open an in-memory profile over the packaged pack, run [body] against the composed
     * desktop UI, and close the profile.
     *
     * In-memory so no test leaves a database file behind, and the real packaged resource
     * rather than a fixture so these fail if the pack stops loading.
     */
    private fun withUi(body: (ui: ComposeUiTest, profile: BeeCodeProfile) -> Unit) {
        val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)
        val profile = BeeCodeProfile.inMemory(
            catalogue = catalogue,
            runner = ScriptedPythonRunner(),
        )
        try {
            runComposeUiTest {
                setContent { DesktopApp(profile) }
                body(this, profile)
            }
        } finally {
            profile.close()
        }
    }

    @Test
    fun theQueueListsTheBundledProblems() = withUi { ui, _ ->
        ui.onNodeWithText("New Problems").assertIsDisplayed()
        ui.onAllNodesWithText("Two Sum").onFirst().assertIsDisplayed()
    }

    @Test
    fun theNavigationRailReachesEveryPane() = withUi { ui, _ ->
        ui.onNodeWithText("Progress").performClick()
        ui.onNodeWithText("Achievements").assertIsDisplayed()

        ui.onNodeWithText("Settings").performClick()
        ui.onNodeWithText("Python execution").assertIsDisplayed()

        ui.onNodeWithText("Study").performClick()
        ui.onNodeWithText("New Problems").assertIsDisplayed()
    }

    @Test
    fun openingAProblemShowsItsStatementAndEditor() = withUi { ui, _ ->
        ui.onAllNodesWithText("Two Sum").onFirst().performClick()
        ui.onNodeWithText("Your solution").assertIsDisplayed()
        ui.onNodeWithText("Run tests").assertIsDisplayed()
        // Named for a screen reader, and the same identifier the Android editor uses.
        ui.onNodeWithContentDescription("Python solution editor").assertIsDisplayed()
    }

    @Test
    fun aFailingRunOffersOnlyAgain() = withUi { ui, _ ->
        ui.onAllNodesWithText("Two Sum").onFirst().performClick()
        ui.onNodeWithContentDescription("Python solution editor")
            .performTextReplacement("def two_sum(nums, target):\n    return [9, 9]\n")
        ui.onNodeWithText("Run tests").performClick()

        ui.waitUntil(timeoutMillis = 10_000) {
            ui.ratingButtons("Again").isNotEmpty()
        }

        // Absent rather than disabled, which states the rule instead of inviting an
        // argument with it. The Android client must agree.
        assertTrue(
            ui.ratingButtons("Good").isEmpty(),
            "Good must not be offered for a failing run",
        )
        assertTrue(
            ui.ratingButtons("Easy").isEmpty(),
            "Easy must not be offered for a failing run",
        )
    }

    @Test
    fun theFullAnswerRunFinalizeJourneyWorksThroughTheUi() = withUi { ui, profile ->
        ui.onAllNodesWithText("Two Sum").onFirst().performClick()
        ui.onNodeWithContentDescription("Python solution editor").performTextReplacement(
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
        ui.onNodeWithText("Run tests").performClick()

        ui.waitUntil(timeoutMillis = 10_000) {
            ui.onAllNodesWithText("All tests passed").fetchSemanticsNodes().isNotEmpty()
        }

        // An unaided pass offers every rating.
        assertTrue(ui.ratingButtons("Good").isNotEmpty(), "Good must be offered")
        assertTrue(ui.ratingButtons("Easy").isNotEmpty(), "Easy must be offered")
        ui.onNode(isRatingButton("Good")).performClick()

        // Read back through the profile: this proves the click reached persistence and
        // the scheduler, not merely the UI's own state.
        ui.waitUntil(timeoutMillis = 10_000) { profile.allReviews().isNotEmpty() }
        val review = profile.allReviews().single()
        assertTrue(review.countsAsSolved, "an unaided pass must count as solved")
        assertEquals(1, profile.statistics().distinctProblemsSolved)
    }

    @Test
    fun settingsStatesTheRunnerLimitationPlainly() = withUi { ui, _ ->
        // The plan forbids calling the runner a sandbox. Asserted in the UI rather than
        // trusted to a code comment, on both clients.
        ui.onNodeWithText("Settings").performClick()
        ui.onNode(hasText("not a security sandbox", substring = true)).assertIsDisplayed()
    }

    private companion object {
        /**
         * Matches a *rating button* labelled [label], not merely any node with that text.
         *
         * The distinction matters and cost a debugging round: "Easy" is also a
         * difficulty badge, and Two Sum is an Easy Problem — so a plain text query finds
         * two nodes and "Easy is not offered" can never be proven. Rating buttons are
         * clickable; the badge is inert text.
         */
        fun isRatingButton(label: String): SemanticsMatcher =
            hasText(label) and hasClickAction()

        fun ComposeUiTest.ratingButtons(label: String) =
            onAllNodes(isRatingButton(label)).fetchSemanticsNodes()
    }
}
