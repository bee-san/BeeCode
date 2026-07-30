package dev.bee.beecode.desktop

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.runComposeUiTest
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * The caret's behaviour across recomposition.
 *
 * [EditorEditsTest] covers the pure transformations, and every one of those tests
 * passed while the editor was visibly broken: the defect was not in the arithmetic
 * but in the composable's state, so only a test that actually composes the editor
 * and presses keys could catch it. That is what this is for.
 *
 * The bug: `remember` was keyed on `source`, which the caller echoes straight back
 * from this editor's own `onSourceChange`. Every keystroke therefore re-ran the
 * initialiser and rebuilt the selection as `TextRange(source.length)`, moving the
 * caret to the end of the buffer. Only the first edit after a caret move landed
 * where the learner had put it.
 *
 * These tests drive the editor exactly as the app wires it — `source` hoisted in
 * Compose state and fed back — because with a plain `var` the recomposition that
 * triggers the bug never happens and the tests pass against broken code.
 */
@OptIn(ExperimentalTestApi::class)
class CodeEditorCaretTest {

    @Test
    fun typingRepeatedlyMidDocumentKeepsTheCaretInPlace() = runComposeUiTest {
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performTextInput("ab")
        waitForIdle()

        field.performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()

        // Three separate insertions, each recomposing. One character cannot tell a
        // surviving caret from a reset one, because the first insert lands correctly
        // either way — the second is where the old code diverged, giving "aXbY".
        field.performTextInput("X")
        waitForIdle()
        field.performTextInput("Y")
        waitForIdle()
        field.performTextInput("Z")
        waitForIdle()

        assertEquals("aXYZb", observed)
    }

    @Test
    fun tabIndentsAtTheCaretRatherThanTheEndOfTheBuffer() = runComposeUiTest {
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("ab") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performKeyInput { pressKey(Key.DirectionLeft) }
        waitForIdle()

        field.performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals("a${EditorEdits.INDENT}b", observed)

        // The second Tab is the one that used to append: it ran after a recomposition
        // carrying the echoed source, so the caret had already jumped past "b".
        field.performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals("a${EditorEdits.INDENT}${EditorEdits.INDENT}b", observed)
    }

    @Test
    fun enterIndentsTheNewLineAndKeepsTypingThere() = runComposeUiTest {
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("def f():") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        assertEquals("def f():\n${EditorEdits.INDENT}", observed)

        // Typing after the Enter must continue on the indented line. Under the bug the
        // caret was at the buffer end, which here coincides — so type twice, since the
        // divergence only shows on the second insertion.
        field.performTextInput("pa")
        waitForIdle()
        field.performTextInput("ss")
        waitForIdle()
        assertEquals("def f():\n${EditorEdits.INDENT}pass", observed)
    }

    @Test
    fun backspaceDedentsAndLeavesTheCaretOnTheSameLine() = runComposeUiTest {
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("def f():") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        field.performKeyInput { pressKey(Key.Enter) }
        waitForIdle()
        // Two Enters after a colon: the second carries the indent without adding one.
        assertEquals("def f():\n${EditorEdits.INDENT}\n${EditorEdits.INDENT}", observed)

        field.performKeyInput { pressKey(Key.Backspace) }
        waitForIdle()
        assertEquals("def f():\n${EditorEdits.INDENT}\n", observed)
    }

    @Test
    fun anExternalResetReplacesTheBuffer() = runComposeUiTest {
        // The behaviour the `remember(source)` key was there to provide, and the reason
        // the fix compares text rather than dropping the synchronisation entirely:
        // "Reset to starter" hands down a different source and must be adopted.
        var observed = ""
        var reset: () -> Unit = {}
        setContent {
            var source by remember { mutableStateOf("typed by the learner") }
            observed = source
            reset = { source = "def starter():" }
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        waitForIdle()

        reset()
        waitForIdle()
        assertEquals("def starter():", observed)

        // And the adopted buffer is live: typing continues from its end, which also
        // proves the caret was placed inside the new text rather than left stale.
        field.performTextInput("x")
        waitForIdle()
        assertEquals("def starter():x", observed)
    }
}
