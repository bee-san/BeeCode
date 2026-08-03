package dev.bee.beecode.desktop

import dev.bee.beecode.design.EditorEdits
import dev.bee.beecode.design.EditorKeymap
import dev.bee.beecode.design.EditorPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertIsNotFocused
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.TextRange
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
    fun vimNormalAndInsertModesShareTheLiveUndoableBuffer() = runComposeUiTest {
        var observed = "abc"
        setContent {
            var source by remember { mutableStateOf(observed) }
            observed = source
            CodeEditor(
                source = source,
                onSourceChange = { source = it },
                preferences = EditorPreferences.DesktopDefault.copy(
                    keymap = EditorKeymap.VIM,
                ),
            )
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()

        // Normal mode starts at the end. h, x removes the final character.
        field.performKeyInput {
            pressKey(Key.H)
            pressKey(Key.X)
        }
        waitForIdle()
        assertEquals("ab", observed)

        field.performKeyInput { pressKey(Key.I) }
        field.performTextInput("z")
        field.performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        assertEquals("abz", observed)
        field.assertIsFocused()

        field.performKeyInput {
            pressKey(Key.D)
            pressKey(Key.D)
        }
        waitForIdle()
        assertEquals("", observed)
    }

    @Test
    fun vimNormalModeRejectsTextInputAndASecondEscapeLeavesTheEditor() = runComposeUiTest {
        var observed = "pass"
        setContent {
            CodeEditor(
                source = observed,
                onSourceChange = { observed = it },
                preferences = EditorPreferences.DesktopDefault.copy(
                    keymap = EditorKeymap.VIM,
                ),
            )
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performTextInput("ignored")
        waitForIdle()
        assertEquals("pass", observed)

        field.performKeyInput {
            pressKey(Key.I)
            pressKey(Key.Escape)
        }
        field.assertIsFocused()
        field.performKeyInput { pressKey(Key.Escape) }
        field.assertIsNotFocused()
    }

    @Test
    fun anOpeningDelimiterAddsItsPairAndKeepsTypingInside() = runComposeUiTest {
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performTextInput("(")
        waitForIdle()
        assertEquals("()", observed)

        field.performTextInput("value")
        waitForIdle()
        assertEquals("(value)", observed)
    }

    @Test
    fun editorToolbarUndoRestoresThePreviousBuffer() = runComposeUiTest {
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performTextInput("value")
        waitForIdle()

        onNodeWithContentDescription("Undo").performClick()
        waitForIdle()
        assertEquals("", observed)
    }

    @Test
    fun replaceAllIsWiredToTheLiveBuffer() = runComposeUiTest {
        var observed = "value + value"
        setContent {
            var source by remember { mutableStateOf(observed) }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }

        onNodeWithText("Replace").performClick()
        onNodeWithContentDescription("Find query").performTextInput("value")
        onNodeWithContentDescription("Replacement").performTextInput("item")
        onNodeWithText("Replace all").performClick()
        waitForIdle()

        assertEquals("item + item", observed)
    }

    @Test
    fun commandEnterRunsWithoutAddingANewline() = runComposeUiTest {
        var source = "pass"
        var runs = 0
        setContent {
            CodeEditor(
                source = source,
                onSourceChange = { source = it },
                onRun = { runs++ },
            )
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.performKeyInput {
            withKeyDown(Key.CtrlLeft) { pressKey(Key.Enter) }
        }
        waitForIdle()

        assertEquals(1, runs)
        assertEquals("pass", source)
    }

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
    fun shiftTabDedentsRatherThanIndenting() = runComposeUiTest {
        // The reported bug, and it is only reachable through the composable: `event.key` is
        // `Key.Tab` whether or not Shift is held, so Shift+Tab fell through to the plain
        // Tab branch and *indented*. Every EditorEditsTest passed throughout, because
        // `dedentBlock` was correct and simply never called.
        var observed = "def f():\n        pass"
        setContent {
            var source by remember { mutableStateOf(observed) }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        // Caret inside "pass", within the second line's eight spaces of indentation.
        field.performTextInputSelection(TextRange(20))
        waitForIdle()

        field.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }
        waitForIdle()

        // One level removed. Under the bug this was "def f():\n            pass" — the
        // shortcut for "dedent" adding indentation.
        assertEquals("def f():\n    pass", observed)
    }

    @Test
    fun tabWithLinesSelectedIndentsThemInsteadOfDeletingThem() = runComposeUiTest {
        // The destructive half of the same defect. Tab called `insert` unconditionally, and
        // `insert` replaces the selection — so selecting a loop body and pressing Tab, which
        // is how every editor indents a block, replaced the body with four spaces.
        var observed = "def f():\nx = 1\ny = 2"
        setContent {
            var source by remember { mutableStateOf(observed) }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        // Select the two body lines, spanning a newline.
        field.performTextInputSelection(TextRange(9, observed.length))
        waitForIdle()

        field.performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals("def f():\n    x = 1\n    y = 2", observed)

        // And the block is still selected, so a second Tab adds a second level rather than
        // replacing the lines it just indented. This is the assertion that fails if the
        // dispatch keeps the selection but `applyBlock` collapses it to a caret.
        field.performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals("def f():\n        x = 1\n        y = 2", observed)
    }

    @Test
    fun tabWithinOneLineStillInsertsSpacesRatherThanIndentingTheLine() = runComposeUiTest {
        // The boundary between the two Tab behaviours. A selection inside a single line is
        // an ordinary replace-what-is-selected, not a block indent: treating every
        // selection as a block would make Tab unable to overwrite a selected word.
        var observed = "alpha beta"
        setContent {
            var source by remember { mutableStateOf(observed) }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        // "beta" selected, no newline inside it.
        field.performTextInputSelection(TextRange(6, 10))
        waitForIdle()

        field.performKeyInput { pressKey(Key.Tab) }
        waitForIdle()
        assertEquals("alpha ${EditorEdits.INDENT}", observed)
    }

    @Test
    fun escapeLeavesTheEditorSoItIsNotAFocusTrap() = runComposeUiTest {
        // Tab is claimed by indentation, which removes the field's only keyboard exit.
        // Without Escape, anyone driving BeeCode from the keyboard reaches the editor and
        // cannot leave it — an accessibility defect introduced by fixing a usability one.
        var observed = ""
        setContent {
            var source by remember { mutableStateOf("") }
            observed = source
            CodeEditor(source = source, onSourceChange = { source = it })
        }
        val field = onNodeWithContentDescription("Python solution editor")
        field.requestFocus()
        field.assertIsFocused()

        field.performKeyInput { pressKey(Key.Escape) }
        waitForIdle()
        field.assertIsNotFocused()

        // And Escape did not also edit the buffer: a key that both exits and types would
        // corrupt the solution on the way out.
        assertEquals("", observed)
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
