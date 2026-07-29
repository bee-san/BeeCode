package dev.bee.beecode.desktop

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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
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
 * A `BasicTextField` with three keyboard behaviours that Python specifically needs.
 * Without them the editor is technically usable and practically miserable, because
 * indentation is syntactically significant:
 *
 * - **Tab inserts four spaces** rather than moving focus out of the field.
 * - **Enter preserves the current line's indentation**, and adds one level after a
 *   line ending in `:`. Re-indenting by hand after every `if` is the single most
 *   tedious thing about writing Python in a plain text box.
 * - **Backspace at the start of a line's text deletes a whole indent level**, so
 *   dedenting is one keystroke rather than four.
 */
@Composable
fun CodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Keyed on the incoming source so an external reset (e.g. "Reset to starter")
    // replaces the buffer, while ordinary typing does not reset the caret.
    var value by remember(source) {
        mutableStateOf(TextFieldValue(source, TextRange(source.length)))
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
                            insert(EditorEdits.INDENT)
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
