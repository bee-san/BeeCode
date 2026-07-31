package dev.bee.beecode.desktop

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.runComposeUiTest
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The editor keeps most of its height when results appear.
 *
 * Measures pixels rather than asserting a modifier, because the defect this covers was
 * invisible in the source: `CodeEditor` and the result block were siblings of one
 * `Column` and *both* claimed `Modifier.weight(1f)`. Equal weights split the available
 * space equally, so pressing Run halved the editor — 519px to 201px, a 61% loss — and
 * pushed the solution the learner was about to rate off the top of the screen. Neither
 * `weight(1f)` is wrong on its own, which is exactly why reading the code did not
 * reveal it and why this test measures the laid-out result instead.
 *
 * The thresholds are deliberately loose. This is a guard against a collapse, not a
 * pin on a particular layout: tightening it to the current numbers would make every
 * future spacing change a test failure and teach the next person to update the
 * constant rather than to look.
 */
@OptIn(ExperimentalTestApi::class)
class EditorHeightTest {

    @Test
    fun aPassingRunLeavesTheEditorMostOfItsHeight() {
        withProblemOpen { before ->
            onNodeWithContentDescription(EDITOR).performTextReplacement(PASSING_SOURCE)
            onNodeWithText("Run tests").performClick()
            waitForIdle()
            val after = editorHeight()

            // A pass collapses its test rows, since a column of identical ticks says
            // nothing the "All tests passed" headline has not. What remains — the
            // headline and the rating buttons — is the irreducible cost, and measures
            // 331px of 519px.
            assertTrue(
                after >= before * 3 / 5,
                "a passing run must leave the editor at least 60% of its height, " +
                    "but it went from $before to $after",
            )
        }
    }

    @Test
    fun aFailingRunStillLeavesTheEditorTheLargerShare() {
        withProblemOpen { before ->
            onNodeWithContentDescription(EDITOR).performTextReplacement(FAILING_SOURCE)
            onNodeWithText("Run tests").performClick()
            waitForIdle()
            val after = editorHeight()

            // A failure shows its rows, since they carry the expected and actual values
            // and that is the whole reason to look. The cap is what keeps this bounded:
            // without it the block was unbounded and a long traceback would leave
            // nothing. The editor must still hold the majority — debugging happens in
            // the editor, not in the result panel.
            assertTrue(
                after > before / 2,
                "a failing run must leave the editor more than half its height, " +
                    "but it went from $before to $after",
            )
        }
    }

    @Test
    fun aPassingRunsTestRowsAreAvailableOnRequest() {
        withProblemOpen {
            onNodeWithContentDescription(EDITOR).performTextReplacement(PASSING_SOURCE)
            onNodeWithText("Run tests").performClick()
            waitForIdle()

            // Collapsed by default, but not withheld: a learner who passed may still
            // want to know which tests ran. Hiding them outright would be a different
            // defect from the one being fixed.
            //
            // Asserted on the rows rather than on editor height. Expanding does *not*
            // shrink the editor — the block is capped and scrolls inside it, which is the
            // point of the cap — so a height assertion here would only re-test the cap
            // and would read as a pass whether the rows appeared or not.
            //
            // The exact label, with the count read from the catalogue. Matching on
            // "Show the" as a substring instead hit "Show the explanation" in the left
            // pane, and `onFirst()` then clicked *that* — a green assertion about a
            // completely different control.
            val tests = ProblemCatalogue.fromResource(PACK_RESOURCE)
                .allProblems().first { it.title == TWO_SUM_TITLE }.tests.size
            onNodeWithText("Show the $tests tests").performClick()
            waitForIdle()
            onNodeWithText("Hide the $tests tests").assertExists()
            // Matched on the rows' spoken verdict rather than on the "\u2713" character. The
            // glyph no longer reaches the tree at all \u2014 every row replaces it with a
            // description, because the character is the only verdict a row carries and a
            // screen reader names it badly or skips it. Worth recording that the old
            // assertion had already stopped testing this: the *headline* is also a "\u2713" on
            // a passing run, so it passed whether the rows appeared or not.
            assertTrue(
                onAllNodesWithContentDescription("Passed").fetchSemanticsNodes().isNotEmpty(),
                "expanding must reveal the per-test rows",
            )
        }
    }

    /**
     * Open Two Sum and hand the callback the editor's height before any run.
     *
     * Takes the "before" measurement itself so no test can accidentally compare
     * against a height captured after a run had already shrunk it.
     */
    private fun withProblemOpen(body: ComposeUiTest.(before: Int) -> Unit) {
        val profile = BeeCodeProfile.inMemory(
            catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE),
            runner = ScriptedPythonRunner(),
        )
        try {
            runComposeUiTest {
                setContent { DesktopApp(profile) }
                // Scrolled to rather than assumed on screen: the queue is a lazy list
                // and Two Sum sits below the fold in a catalogue this size, so the row
                // has no semantics to click until it composes.
                onNodeWithTag(QUEUE_LIST_TAG)
                    .performScrollToNode(hasTestTag(BROWSE_ALL_NEW_TAG))
                onNodeWithTag(BROWSE_ALL_NEW_TAG).performClick()
                onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(TWO_SUM_TITLE))
                onAllNodesWithText(TWO_SUM_TITLE).onFirst().performClick()
                waitForIdle()
                body(editorHeight())
            }
        } finally {
            profile.close()
        }
    }

    private fun ComposeUiTest.editorHeight(): Int =
        onNodeWithContentDescription(EDITOR).fetchSemanticsNode().size.height

    private companion object {
        const val EDITOR = "Python solution editor"

        /** The Problem these tests drive. Solvable in a few lines and stable content. */
        const val TWO_SUM_TITLE = "Two Sum"

        /** The scripted runner passes on this marker; see [ScriptedPythonRunner]. */
        val PASSING_SOURCE = """
            def two_sum(nums, target):
                ${ScriptedPythonRunner.PASS_MARKER}
                return []
        """.trimIndent()

        val FAILING_SOURCE = "def two_sum(nums, target):\n    return []\n"
    }
}
