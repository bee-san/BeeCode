package dev.bee.beecode.desktop

import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * BeeCode is drivable from the keyboard alone.
 *
 * Written after probing what Tab *actually* reaches on each pane rather than reasoning
 * about traversal order from the source, which is how the defect these tests pin was
 * found: on the Problem pane, forward Tab went `← Back` → `Show the explanation` →
 * `Reset` → the editor, and then stopped. `CodeEditor` claims `Key.Tab` for Python
 * indentation — it has to, indentation is syntax — so every subsequent Tab indented the
 * buffer instead of advancing. `Run tests` sits *after* the editor in traversal order, so
 * the pane's primary action was unreachable going forwards. Escape released focus, which
 * escaped the field but did not help: with nothing focused, Tab restarts at `← Back`. The
 * only path to `Run tests` was Shift+Tab backwards from the top of the pane, and no
 * learner discovers that.
 *
 * So these tests assert reachability by walking the UI the way a person does, one forward
 * keystroke at a time, instead of asserting that some modifier is present. A traversal
 * order is an emergent property of the whole tree; every modifier can be individually
 * correct and the walk still dead-end.
 *
 * Walking also matters for a reason beyond traversal: Compose tracks an input mode, and
 * focus indication is only *drawn* in `Keyboard` mode. Pressing a key switches to it;
 * clicking switches back to `Touch`; `requestFocus()` — being a test affordance rather
 * than an input event — switches nothing. So a focus-appearance assertion driven by
 * `requestFocus()` alone reads zero changed pixels against a UI that highlights correctly
 * for real users. Keys, not helpers.
 */
@OptIn(ExperimentalTestApi::class)
class KeyboardFocusTest {

    @Test
    fun forwardKeystrokesAloneReachRunTests() {
        withProblemOpen {
            onNodeWithText(BACK).requestFocus()

            // Bounded rather than `while (true)`: an unbounded walk on a UI with a focus
            // cycle never terminates, and a hanging test reports nothing. The Problem pane
            // has a handful of stops, so 12 is generous and still finite.
            val visited = walkForward(steps = 12)

            assertTrue(
                RUN_TESTS in visited,
                "a keyboard-only learner must reach \"$RUN_TESTS\" using forward keys " +
                    "alone, but 12 keystrokes from \"$BACK\" only reached: $visited",
            )
        }
    }

    @Test
    fun escapeLeavesTheEditorForTheNextControlRatherThanForNothing() {
        withProblemOpen {
            val editor = onNodeWithContentDescription(EDITOR)
            editor.requestFocus()
            editor.performKeyInput { pressKey(Key.Escape) }
            waitForIdle()

            // The specific behaviour the walk above depends on, asserted on its own so a
            // regression names its cause instead of just failing the traversal.
            //
            // Not "focus is somewhere else" — that was true of `clearFocus()` too, and
            // that was the bug. It must be *this* control: the editor is the last thing
            // before Run tests, and a learner who has finished typing wants to run.
            assertEquals(
                RUN_TESTS,
                focusedLabel(),
                "Escape must hand focus to the next control, not merely drop it",
            )
        }
    }

    @Test
    fun theFocusedControlLooksDifferentFromTheUnfocusedOne() {
        withProblemOpen {
            // WCAG 2.4.7: a keyboard user has to be able to see where they are. Compose
            // gives buttons a focus indication by default, which is exactly the kind of
            // thing that silently disappears when someone passes a custom `indication` or
            // `interactionSource`, so it is worth a pixel.
            //
            // Compared as pixels because the visible difference is drawn, not modelled:
            // there is no semantics property for "has a focus highlight", and asserting
            // `Focused == true` would pass on a control that renders identically either
            // way, which is the failure mode being guarded.
            //
            // Focus is taken by *pressing keys*, not by `requestFocus()`. Compose tracks an
            // input mode and only draws focus indication in `Keyboard` mode; a real key
            // event is what switches it, and a click switches it back to `Touch`. Written
            // the direct way, this test reported 0 changed pixels on a button that
            // highlights perfectly well in the running app — `requestFocus()` moved focus
            // while the scene was still in touch mode, so nothing drew. That is a test-only
            // path no learner can take, and asserting through it would have measured the
            // harness rather than the product.
            val unfocused = onNodeWithText(RUN_TESTS).captureToImage().toPixelMap()
            onNodeWithText(BACK).requestFocus()
            val visited = walkForward(steps = 12, until = RUN_TESTS)
            assertEquals(
                RUN_TESTS,
                focusedLabel(),
                "the walk must land on \"$RUN_TESTS\" before its pixels mean anything, " +
                    "but it ended on \"${focusedLabel()}\" having visited: $visited",
            )
            val focused = onNodeWithText(RUN_TESTS).captureToImage().toPixelMap()

            assertEquals(unfocused.width to unfocused.height, focused.width to focused.height)
            val changed = (0 until unfocused.height).sumOf { y ->
                (0 until unfocused.width).count { x -> unfocused[x, y] != focused[x, y] }
            }
            assertTrue(
                changed > 0,
                "focusing \"$RUN_TESTS\" must change how it is drawn, but all " +
                    "${unfocused.width * unfocused.height} pixels were identical",
            )
        }
    }

    @Test
    fun settingsTraversalWrapsRatherThanTrapping() {
        withUi {
            // Settings is the densest pane — the theme family and mode pickers, two
            // switches, the daily-limit chips, the backup buttons, and the WebDAV fields —
            // so it is where a trap would most likely hide. The claim is not a particular
            // order: it is that tabbing through it comes back out, which is what "not a
            // trap" means. 40 keystrokes covers the pane's ~20 stops with room to spare.
            // Two nodes say "Settings": the rail item and the pane heading. The rail
            // one is first, and is the one that opens the pane.
            onAllNodesWithText("Settings").onFirst().performClick()
            waitForIdle()
            onAllNodesWithText("Settings").onFirst().requestFocus()
            val keystrokes = 40
            val visited = List(keystrokes) {
                onAllNodesWithText("Settings").onFirst()
                    .performKeyInput { pressKey(Key.Tab) }
                waitForIdle()
                focusedLabel()
            }

            assertTrue(
                visited.distinct().size > 10,
                "tabbing through Settings must move through its controls, but " +
                    "$keystrokes keystrokes only reached ${visited.distinct().size} " +
                    "distinct stops: ${visited.distinct()}",
            )
            assertTrue(
                visited.count { it == visited.first() } > 1,
                "traversal must wrap back round rather than stopping at the last " +
                    "control, but \"${visited.first()}\" was never reached again in: " +
                    visited,
            )
        }
    }

    /**
     * Press the keys a learner presses to go forwards, and report every stop reached.
     *
     * Tab, except in the editor, where Tab indents and Escape is the documented exit. That
     * pairing is the whole claim: those two keys, and no backwards traversal, no mouse.
     *
     * Key events go to whatever holds focus, so the node the input is anchored on only has
     * to resolve — it is not the target. Anchored on the pane's heading, which is present
     * throughout and matches exactly once.
     */
    private fun ComposeUiTest.walkForward(steps: Int, until: String? = null): List<String> {
        val visited = mutableListOf<String>()
        repeat(steps) {
            if (until != null && visited.lastOrNull() == until) return visited
            val leavingEditor = focusedLabel() == EDITOR
            onNodeWithContentDescription(EDITOR).performKeyInput {
                pressKey(if (leavingEditor) Key.Escape else Key.Tab)
            }
            waitForIdle()
            visited += focusedLabel()
        }
        return visited
    }

    /** The text or content description of whatever currently holds focus. */
    private fun ComposeUiTest.focusedLabel(): String {
        val focused = onAllNodes(
            SemanticsMatcher.expectValue(SemanticsProperties.Focused, true),
        ).fetchSemanticsNodes()
        if (focused.isEmpty()) return NOTHING_FOCUSED
        return focused.joinToString(" | ") { node ->
            node.config.getOrNull(SemanticsProperties.Text)?.joinToString(" ")
                ?: node.config.getOrNull(SemanticsProperties.ContentDescription)?.joinToString(" ")
                ?: "<unlabelled>"
        }
    }

    /** Compose the app over an in-memory profile and the real packaged pack. */
    private fun withUi(body: ComposeUiTest.() -> Unit) {
        val profile = BeeCodeProfile.inMemory(
            catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE),
            runner = ScriptedPythonRunner(),
        )
        try {
            runComposeUiTest {
                setContent { DesktopApp(profile) }
                body()
            }
        } finally {
            profile.close()
        }
    }

    /** As [withUi], with Two Sum open — scrolled to, since the queue is a lazy list. */
    private fun withProblemOpen(body: ComposeUiTest.() -> Unit) = withUi {
        onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(TWO_SUM_TITLE))
        onNodeWithText(TWO_SUM_TITLE).performClick()
        waitForIdle()
        body()
    }

    private companion object {
        const val EDITOR = "Python solution editor"
        const val RUN_TESTS = "Run tests"
        const val BACK = "← Back"
        const val TWO_SUM_TITLE = "Two Sum"
        const val NOTHING_FOCUSED = "<nothing focused>"
    }
}
