package dev.bee.beecode.desktop

import dev.bee.beecode.design.EditorEdits
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The desktop code editor.
 *
 * A `BasicTextField` with the keyboard behaviours Python specifically needs. Without
 * them the editor is technically usable and practically miserable, because
 * indentation is syntactically significant:
 *
 * - **Tab inserts four spaces** rather than moving focus out of the field.
 * - **Tab with lines selected indents the whole block**, and keeps it selected so a
 *   second Tab adds a second level. It used to *replace* the selection with four
 *   spaces, which deleted the selected code.
 * - **Shift+Tab dedents**, with or without a selection. It used to indent, because
 *   `event.key` is `Key.Tab` whether or not Shift is held.
 * - **Enter preserves the current line's indentation**, and adds one level after a
 *   line ending in `:`. Re-indenting by hand after every `if` is the single most
 *   tedious thing about writing Python in a plain text box.
 * - **Backspace at the start of a line's text deletes a whole indent level**, so
 *   dedenting is one keystroke rather than four.
 * - **Escape releases focus.** Since Tab is claimed by indentation the field would
 *   otherwise have no keyboard exit at all, which makes it a focus trap for anyone
 *   not using a mouse.
 */
@Composable
fun CodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusManager: FocusManager = LocalFocusManager.current,
) {
    var value by remember { mutableStateOf(TextFieldValue(source, TextRange(source.length))) }

    // Adopt [source] only when it genuinely disagrees with the buffer — an external
    // reset such as "Reset to starter", or switching Problem.
    //
    // This must NOT be a `remember(source)` key. The caller re-supplies `source` from
    // this editor's own `onSourceChange`, so keying on it re-ran this initialiser after
    // every keystroke and rebuilt the selection as `TextRange(source.length)`: the caret
    // jumped to the end of the document after each edit. Typing `ab`, moving left, then
    // typing `X`, `Y`, `Z` gave `aXbYZ` — only the first insertion landed where the
    // learner had put the caret. Tab was the most visible casualty, indenting the end of
    // the buffer rather than the line being written.
    //
    // Comparing text converges in one pass: after an ordinary edit the caller echoes
    // back exactly what was just emitted, so the branch does not run.
    if (value.text != source) {
        value = TextFieldValue(source, TextRange(source.length))
    }

    fun apply(next: TextFieldValue) {
        value = next
        onSourceChange(next.text)
    }

    /** Apply one of [EditorEdits]'s pure transformations to the live buffer. */
    fun applyEdit(edit: EditorEdits.Edit) {
        apply(TextFieldValue(edit.text, TextRange(edit.caret)))
    }

    fun insert(insertion: String) {
        applyEdit(
            EditorEdits.insert(value.text, value.selection.min, value.selection.max, insertion),
        )
    }

    /** Apply a block re-indent, keeping the affected lines selected. */
    fun applyBlock(edit: EditorEdits.BlockEdit) {
        apply(
            TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd)),
        )
    }

    /** True when the selection spans more than one line, i.e. Tab means "indent this block". */
    fun selectionSpansLines(): Boolean = !value.selection.collapsed &&
        value.text.substring(value.selection.min, value.selection.max).contains('\n')

    Box(
        modifier.background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp)),
    ) {
        BasicTextField(
            value = value,
            onValueChange = ::apply,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                // A BasicTextField carries no label, so a screen reader would announce
                // this only as an edit field. The Android editor names itself the same
                // way, which also keeps the two clients' UI tests addressing it by the
                // same identifier.
                .semantics { contentDescription = "Python solution editor" }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    when (event.key) {
                        Key.Tab -> {
                            // Four cases, and only the last is a plain insertion. Shift+Tab
                            // used to fall into that last branch — `event.key` is `Key.Tab`
                            // whether or not Shift is held — so the dedent shortcut
                            // indented. And Tab with a block selected replaced it with four
                            // spaces, deleting the selected code.
                            val start = value.selection.min
                            val end = value.selection.max
                            when {
                                event.isShiftPressed ->
                                    applyBlock(EditorEdits.dedentBlock(value.text, start, end))
                                selectionSpansLines() ->
                                    applyBlock(EditorEdits.indentBlock(value.text, start, end))
                                else -> insert(EditorEdits.INDENT)
                            }
                            true
                        }
                        // Tab is claimed by indentation, so the field has no keyboard exit.
                        // Escape gives it one, which matters for anyone driving BeeCode
                        // without a mouse: without it the editor is a focus trap.
                        Key.Escape -> {
                            focusManager.clearFocus()
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            applyEdit(EditorEdits.newlineWithIndent(value.text, value.selection.min))
                            true
                        }
                        Key.Backspace -> {
                            // Only intercept a collapsed caret; a selection deletes
                            // normally. Null means ordinary backspace applies.
                            val edit = if (value.selection.collapsed) {
                                EditorEdits.dedent(value.text, value.selection.min)
                            } else {
                                null
                            }
                            edit?.let { applyEdit(it); true } ?: false
                        }
                        else -> false
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        )
    }
}
