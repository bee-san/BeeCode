package dev.bee.beecode.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Redo
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.design.EditorEdits
import dev.bee.beecode.design.EditorHistory
import dev.bee.beecode.design.EditorKeymap
import dev.bee.beecode.design.EditorPreferences
import dev.bee.beecode.design.PythonSyntax
import dev.bee.beecode.design.VimEdits
import dev.bee.beecode.design.sourceOffset
import dev.bee.beecode.python.RunDiagnostic

private enum class VimMode {
    NORMAL,
    INSERT,
}

/**
 * A keyboard-first Python editor built on Compose's native text field.
 *
 * Staying on [BasicTextField] preserves platform IME, selection, clipboard, and
 * accessibility behavior while the surrounding controls add editor commands.
 */
@Composable
fun CodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    focusManager: FocusManager = LocalFocusManager.current,
    preferences: EditorPreferences = EditorPreferences.DesktopDefault,
    diagnostic: RunDiagnostic? = null,
    onRun: () -> Unit = {},
    sessionKey: Any? = null,
) {
    var value by remember(sessionKey) {
        mutableStateOf(TextFieldValue(source, TextRange(source.length)))
    }
    val history = remember(sessionKey) {
        EditorHistory(EditorHistory.Snapshot(source, source.length, source.length))
    }
    var vimMode by remember(sessionKey, preferences.keymap) {
        mutableStateOf(VimMode.NORMAL)
    }
    var pendingDeleteLine by remember(sessionKey, preferences.keymap) {
        mutableStateOf(false)
    }
    var isFocused by remember { mutableStateOf(false) }
    var textLayout by remember { mutableStateOf<TextLayoutResult?>(null) }
    var showSearch by remember { mutableStateOf(false) }
    var showReplace by remember { mutableStateOf(false) }
    var showGoToLine by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var replacement by remember { mutableStateOf("") }
    var matchCase by remember { mutableStateOf(false) }
    var wholeWord by remember { mutableStateOf(false) }
    var currentMatch by remember { mutableIntStateOf(0) }
    var lineInput by remember { mutableStateOf("") }

    if (value.text != source) {
        value = TextFieldValue(source, TextRange(source.length))
        history.adoptExternal(value.toSnapshot())
    }

    fun emit(next: TextFieldValue, record: Boolean = true) {
        val previousText = value.text
        val bounded = next.copy(
            selection = TextRange(
                next.selection.start.coerceIn(0, next.text.length),
                next.selection.end.coerceIn(0, next.text.length),
            ),
        )
        value = bounded
        if (record) history.record(bounded.toSnapshot())
        if (bounded.text != previousText) {
            currentMatch = 0
            onSourceChange(bounded.text)
        }
    }

    fun applySnapshot(snapshot: EditorHistory.Snapshot?) {
        if (snapshot == null) return
        emit(snapshot.toTextFieldValue(), record = false)
    }

    fun applySelection(edit: EditorEdits.SelectionEdit) {
        emit(
            TextFieldValue(
                edit.text,
                TextRange(edit.selectionStart, edit.selectionEnd),
            ),
        )
    }

    fun applyBlock(edit: EditorEdits.BlockEdit) {
        emit(
            TextFieldValue(
                edit.text,
                TextRange(edit.selectionStart, edit.selectionEnd),
            ),
        )
    }

    fun insert(insertion: String) {
        val edit = EditorEdits.insert(
            value.text,
            value.selection.min,
            value.selection.max,
            insertion,
        )
        emit(TextFieldValue(edit.text, TextRange(edit.caret)))
    }

    fun selectMatch(delta: Int) {
        val matches = EditorEdits.findAll(value.text, query, matchCase, wholeWord)
        currentMatch = EditorEdits.searchNavigationIndex(
            matches = matches,
            selectionStart = value.selection.min,
            selectionEnd = value.selection.max,
            currentIndex = currentMatch,
            delta = delta,
        ) ?: return
        val match = matches[currentMatch]
        value = value.copy(selection = TextRange(match.start, match.end))
    }

    fun toggleComment() {
        applySelection(
            EditorEdits.toggleComment(
                value.text,
                value.selection.min,
                value.selection.max,
            ),
        )
    }

    fun goToLine() {
        val line = lineInput.toIntOrNull() ?: return
        val offset = EditorEdits.goToLine(value.text, line)
        value = value.copy(selection = TextRange(offset))
        showGoToLine = false
    }

    fun replaceCurrent() {
        val matches = EditorEdits.findAll(value.text, query, matchCase, wholeWord)
        val selected = matches.firstOrNull {
            it.start == value.selection.min && it.end == value.selection.max
        } ?: matches.getOrNull(currentMatch.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))
        if (selected == null) return
        val edit = EditorEdits.insert(value.text, selected.start, selected.end, replacement)
        emit(TextFieldValue(edit.text, TextRange(edit.caret)))
    }

    fun leaveEditor() {
        focusManager.moveFocus(FocusDirection.Next)
        if (isFocused) focusManager.clearFocus()
    }

    fun applyVimEdit(edit: EditorEdits.SelectionEdit?) {
        if (edit != null) applySelection(edit)
    }

    fun enterInsertMode(caret: Int = value.selection.end) {
        val bounded = caret.coerceIn(0, value.text.length)
        value = value.copy(selection = TextRange(bounded))
        history.record(value.toSnapshot())
        pendingDeleteLine = false
        vimMode = VimMode.INSERT
    }

    fun handleShortcut(event: KeyEvent): Boolean {
        val command = event.isCtrlPressed || event.isMetaPressed
        if (!command) return false
        return when (event.key) {
            Key.Z -> {
                applySnapshot(if (event.isShiftPressed) history.redo() else history.undo())
                true
            }
            Key.Y -> {
                applySnapshot(history.redo())
                true
            }
            Key.R -> {
                if (preferences.keymap == EditorKeymap.VIM) {
                    applySnapshot(history.redo())
                    true
                } else {
                    false
                }
            }
            Key.F -> {
                showSearch = true
                showReplace = false
                true
            }
            Key.H -> {
                showSearch = true
                showReplace = true
                true
            }
            Key.G -> {
                showGoToLine = true
                true
            }
            Key.Slash -> {
                toggleComment()
                true
            }
            Key.Enter, Key.NumPadEnter -> {
                onRun()
                true
            }
            else -> false
        }
    }

    fun handleVimNormal(event: KeyEvent): Boolean {
        if (event.key != Key.D || event.isShiftPressed) pendingDeleteLine = false
        when (event.key) {
            Key.I -> {
                enterInsertMode(
                    if (event.isShiftPressed) {
                        VimEdits.firstNonWhitespace(value.text, value.selection.end)
                    } else {
                        value.selection.end
                    },
                )
            }
            Key.A -> {
                val caret = if (event.isShiftPressed) {
                    VimEdits.lineEnd(value.text, value.selection.end)
                } else {
                    VimEdits.moveHorizontal(value.text, value.selection.end, 1).selectionEnd
                }
                enterInsertMode(caret)
            }
            Key.H, Key.DirectionLeft ->
                applyVimEdit(VimEdits.moveHorizontal(value.text, value.selection.end, -1))
            Key.L, Key.DirectionRight ->
                applyVimEdit(VimEdits.moveHorizontal(value.text, value.selection.end, 1))
            Key.J, Key.DirectionDown ->
                applyVimEdit(VimEdits.moveVertical(value.text, value.selection.end, 1))
            Key.K, Key.DirectionUp ->
                applyVimEdit(VimEdits.moveVertical(value.text, value.selection.end, -1))
            Key.W -> applyVimEdit(VimEdits.nextWord(value.text, value.selection.end))
            Key.B -> applyVimEdit(VimEdits.previousWord(value.text, value.selection.end))
            Key.X, Key.Delete ->
                applyVimEdit(VimEdits.deleteCharacter(value.text, value.selection.end))
            Key.U -> applySnapshot(history.undo())
            Key.D -> {
                if (!event.isShiftPressed && pendingDeleteLine) {
                    pendingDeleteLine = false
                    applyVimEdit(VimEdits.deleteLine(value.text, value.selection.end))
                } else if (!event.isShiftPressed) {
                    pendingDeleteLine = true
                }
            }
            Key.O -> {
                applyVimEdit(
                    if (event.isShiftPressed) {
                        VimEdits.openLineAbove(value.text, value.selection.end)
                    } else {
                        VimEdits.openLineBelow(value.text, value.selection.end)
                    },
                )
                enterInsertMode(value.selection.end)
            }
            Key.MoveHome -> applyVimEdit(
                EditorEdits.moveCaret(
                    value.text,
                    value.selection.end,
                    VimEdits.lineStart(value.text, value.selection.end) - value.selection.end,
                ),
            )
            Key.MoveEnd -> applyVimEdit(
                EditorEdits.moveCaret(
                    value.text,
                    value.selection.end,
                    VimEdits.lineEnd(value.text, value.selection.end) - value.selection.end,
                ),
            )
            Key.Tab -> {
                focusManager.moveFocus(
                    if (event.isShiftPressed) FocusDirection.Previous else FocusDirection.Next,
                )
                if (isFocused) focusManager.clearFocus()
            }
            Key.Escape -> leaveEditor()
            else -> Unit
        }
        // Normal mode owns printable keys. Unknown commands are harmless no-ops.
        return true
    }

    val matches = remember(value.text, query, matchCase, wholeWord) {
        EditorEdits.findAll(value.text, query, matchCase, wholeWord)
    }
    val bracketMatch = remember(value.text, value.selection.end) {
        PythonSyntax.matchingBracket(value.text, value.selection.end)
    }
    val diagnosticOffsets = remember(value.text, diagnostic) {
        diagnostic?.sourceRange?.let { range ->
            val start = sourceOffset(value.text, range.start.line, range.start.column)
            val explicitEnd = range.end?.let {
                sourceOffset(value.text, it.line, it.column)
            }
            val end = when {
                explicitEnd != null && explicitEnd > start -> explicitEnd
                range.start.column != null -> (start + 1).coerceAtMost(value.text.length)
                else -> value.text.indexOf('\n', start).let {
                    if (it < 0) value.text.length else it
                }
            }
            start to end.coerceAtLeast(start)
        }
    }
    val syntaxColors = EditorSyntaxColors(
        keyword = MaterialTheme.colorScheme.primary,
        builtin = MaterialTheme.colorScheme.tertiary,
        definition = MaterialTheme.colorScheme.secondary,
        string = Color(LocalBeeCodePalette.current.accentSuccess),
        number = Color(LocalBeeCodePalette.current.accentCaution),
        comment = MaterialTheme.colorScheme.onSurfaceVariant,
        search = MaterialTheme.colorScheme.secondaryContainer,
        bracket = MaterialTheme.colorScheme.primaryContainer,
        diagnostic = MaterialTheme.colorScheme.error,
    )
    val visualTransformation = remember(
        value.text,
        matches,
        bracketMatch,
        diagnosticOffsets,
        syntaxColors,
    ) {
        editorVisualTransformation(
            source = value.text,
            matches = matches,
            bracketMatch = bracketMatch,
            diagnosticOffsets = diagnosticOffsets,
            colors = syntaxColors,
        )
    }
    val lineCount = value.text.count { it == '\n' } + 1
    val lineNumberWidth = (lineCount.toString().length * 9 + 18).dp
    val activeLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
    val horizontalScroll = rememberScrollState()
    val verticalScroll = rememberScrollState()
    val visualLineStarts = if (preferences.wrapLines) {
        textLayout?.let { layout -> List(layout.lineCount) { layout.getLineStart(it) } }
    } else {
        null
    }
    val lineNumbers = remember(value.text, visualLineStarts) {
        EditorEdits.lineNumberGutter(value.text, visualLineStarts)
    }
    val displayedLineCount = lineNumbers.count { it == '\n' } + 1

    Column(
        modifier
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
            .padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            IconButton(
                onClick = { applySnapshot(history.undo()) },
                enabled = history.canUndo,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = "Undo",
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(
                onClick = { applySnapshot(history.redo()) },
                enabled = history.canRedo,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Redo,
                    contentDescription = "Redo",
                    modifier = Modifier.size(18.dp),
                )
            }
            TextButton(
                onClick = {
                    showSearch = !showSearch
                    showReplace = false
                },
            ) { Text("Find") }
            TextButton(
                onClick = {
                    showSearch = true
                    showReplace = true
                },
            ) { Text("Replace") }
            TextButton(onClick = { showGoToLine = !showGoToLine }) { Text("Go to line") }
            TextButton(onClick = ::toggleComment) { Text("Comment") }
            Spacer(Modifier.weight(1f))
            if (preferences.keymap == EditorKeymap.VIM) {
                Text(
                    vimMode.name,
                    modifier = Modifier.padding(top = 8.dp, end = 10.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Text(
                "Ln ${value.text.take(value.selection.end).count { it == '\n' } + 1}",
                modifier = Modifier.padding(top = 8.dp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (showSearch) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = {
                        query = it
                        currentMatch = 0
                    },
                    label = { Text("Find") },
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .semantics { contentDescription = "Find query" },
                )
                if (showReplace) {
                    OutlinedTextField(
                        value = replacement,
                        onValueChange = { replacement = it },
                        label = { Text("Replace with") },
                        singleLine = true,
                        modifier = Modifier
                            .weight(1f)
                            .semantics { contentDescription = "Replacement" },
                    )
                }
                OutlinedButton(onClick = { selectMatch(-1) }, enabled = matches.isNotEmpty()) {
                    Text("Previous")
                }
                OutlinedButton(onClick = { selectMatch(1) }, enabled = matches.isNotEmpty()) {
                    Text("Next")
                }
            }
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                Checkbox(checked = matchCase, onCheckedChange = { matchCase = it })
                Text("Match case", style = MaterialTheme.typography.labelSmall)
                Checkbox(checked = wholeWord, onCheckedChange = { wholeWord = it })
                Text("Whole word", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    if (matches.isEmpty()) {
                        "No matches"
                    } else {
                        "${currentMatch.coerceIn(0, matches.lastIndex) + 1} of ${matches.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
                if (showReplace) {
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = ::replaceCurrent, enabled = matches.isNotEmpty()) {
                        Text("Replace")
                    }
                    TextButton(
                        onClick = {
                            applySelection(
                                EditorEdits.replaceAll(
                                    value.text,
                                    query,
                                    replacement,
                                    matchCase,
                                    wholeWord,
                                ),
                            )
                        },
                        enabled = matches.isNotEmpty(),
                    ) { Text("Replace all") }
                }
            }
        }

        if (showGoToLine) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = lineInput,
                    onValueChange = { lineInput = it.filter(Char::isDigit) },
                    label = { Text("Line") },
                    singleLine = true,
                    modifier = Modifier
                        .width(120.dp)
                        .semantics { contentDescription = "Line number" },
                )
                Button(onClick = ::goToLine, enabled = lineInput.isNotBlank()) { Text("Go") }
            }
        }

        BasicTextField(
            value = value,
            onValueChange = { next ->
                if (preferences.keymap == EditorKeymap.VIM && vimMode == VimMode.NORMAL) {
                    if (next.text == value.text) emit(next)
                } else {
                    emit(smartPair(value, next))
                }
            },
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(verticalScroll)
                .semantics { contentDescription = "Python solution editor" }
                .onFocusChanged {
                    isFocused = it.isFocused
                    if (!it.isFocused && preferences.keymap == EditorKeymap.VIM) {
                        pendingDeleteLine = false
                        vimMode = VimMode.NORMAL
                    }
                }
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    if (handleShortcut(event)) return@onPreviewKeyEvent true
                    if (preferences.keymap == EditorKeymap.VIM) {
                        if (vimMode == VimMode.NORMAL) {
                            return@onPreviewKeyEvent handleVimNormal(event)
                        }
                        if (event.key == Key.Escape) {
                            pendingDeleteLine = false
                            vimMode = VimMode.NORMAL
                            return@onPreviewKeyEvent true
                        }
                    }
                    when (event.key) {
                        Key.Tab -> {
                            val start = value.selection.min
                            val end = value.selection.max
                            val spansLines = !value.selection.collapsed &&
                                value.text.substring(start, end).contains('\n')
                            when {
                                event.isShiftPressed ->
                                    applyBlock(EditorEdits.dedentBlock(value.text, start, end))
                                spansLines ->
                                    applyBlock(EditorEdits.indentBlock(value.text, start, end))
                                else -> insert(EditorEdits.INDENT)
                            }
                            true
                        }
                        Key.Escape -> {
                            when {
                                showGoToLine -> showGoToLine = false
                                showSearch -> showSearch = false
                                else -> leaveEditor()
                            }
                            true
                        }
                        Key.Enter, Key.NumPadEnter -> {
                            val edit = EditorEdits.newlineWithIndent(
                                value.text,
                                value.selection.min,
                            )
                            emit(TextFieldValue(edit.text, TextRange(edit.caret)))
                            true
                        }
                        Key.Backspace -> {
                            val edit = if (value.selection.collapsed) {
                                EditorEdits.dedent(value.text, value.selection.min)
                            } else {
                                null
                            }
                            if (edit == null) {
                                false
                            } else {
                                emit(TextFieldValue(edit.text, TextRange(edit.caret)))
                                true
                            }
                        }
                        else -> false
                    }
                },
            textStyle = TextStyle(
                fontFamily = FontFamily.Monospace,
                fontSize = preferences.fontSizeSp.sp,
                lineHeight = (preferences.fontSizeSp * 1.5f).sp,
                color = MaterialTheme.colorScheme.onSurface,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            visualTransformation = visualTransformation,
            onTextLayout = { textLayout = it },
            decorationBox = { innerTextField ->
                Row(Modifier.fillMaxSize()) {
                    Text(
                        text = lineNumbers,
                        modifier = Modifier
                            .widthIn(min = lineNumberWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 6.dp, vertical = 4.dp)
                            .clearAndSetSemantics {},
                        style = TextStyle(
                            fontFamily = FontFamily.Monospace,
                            fontSize = preferences.fontSizeSp.sp,
                            lineHeight = (preferences.fontSizeSp * 1.5f).sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        maxLines = displayedLineCount,
                        overflow = TextOverflow.Clip,
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surface)
                            .then(
                                if (preferences.wrapLines) {
                                    Modifier
                                } else {
                                    Modifier.horizontalScroll(horizontalScroll)
                                },
                            )
                            .drawBehind {
                                val layout = textLayout ?: return@drawBehind
                                val offset = value.selection.end.coerceIn(0, value.text.length)
                                val line = layout.getLineForOffset(offset)
                                val top = layout.getLineTop(line)
                                val bottom = layout.getLineBottom(line)
                                drawRect(
                                    color = activeLineColor,
                                    topLeft = Offset(0f, top),
                                    size = Size(size.width, bottom - top),
                                )
                            }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        innerTextField()
                    }
                }
            },
        )
    }
}

private data class EditorSyntaxColors(
    val keyword: Color,
    val builtin: Color,
    val definition: Color,
    val string: Color,
    val number: Color,
    val comment: Color,
    val search: Color,
    val bracket: Color,
    val diagnostic: Color,
)

private fun editorVisualTransformation(
    source: String,
    matches: List<EditorEdits.SearchMatch>,
    bracketMatch: PythonSyntax.BracketMatch?,
    diagnosticOffsets: Pair<Int, Int>?,
    colors: EditorSyntaxColors,
): VisualTransformation = VisualTransformation { original ->
    val annotated = buildAnnotatedString {
        append(original)
        PythonSyntax.tokens(source).forEach { token ->
            addStyle(
                SpanStyle(
                    color = when (token.kind) {
                        PythonSyntax.Kind.KEYWORD -> colors.keyword
                        PythonSyntax.Kind.BUILTIN -> colors.builtin
                        PythonSyntax.Kind.DEFINITION -> colors.definition
                        PythonSyntax.Kind.STRING -> colors.string
                        PythonSyntax.Kind.NUMBER -> colors.number
                        PythonSyntax.Kind.COMMENT -> colors.comment
                    },
                ),
                token.start,
                token.end,
            )
        }
        matches.forEach {
            addStyle(SpanStyle(background = colors.search), it.start, it.end)
        }
        bracketMatch?.let {
            addStyle(SpanStyle(background = colors.bracket), it.opening, it.opening + 1)
            addStyle(SpanStyle(background = colors.bracket), it.closing, it.closing + 1)
        }
        diagnosticOffsets?.let { (start, end) ->
            if (start < end && start in source.indices) {
                addStyle(
                    SpanStyle(
                        color = colors.diagnostic,
                        textDecoration = TextDecoration.Underline,
                    ),
                    start,
                    end.coerceAtMost(source.length),
                )
            }
        }
    }
    TransformedText(annotated, OffsetMapping.Identity)
}

/**
 * Recognize one-character typing edits and add or skip a matching delimiter.
 *
 * Paste and IME composition fall through untouched.
 */
private fun smartPair(previous: TextFieldValue, next: TextFieldValue): TextFieldValue {
    if (previous.composition != null || next.composition != null) return next
    val start = previous.selection.min
    val end = previous.selection.max
    val expectedLength = previous.text.length - (end - start) + 1
    if (next.text.length != expectedLength || next.selection.min != start + 1) return next
    if (!next.text.startsWith(previous.text.substring(0, start))) return next
    if (!next.text.endsWith(previous.text.substring(end))) return next

    val typed = next.text[start]
    if (previous.selection.collapsed &&
        typed in ")]}'\"" &&
        previous.text.getOrNull(start) == typed
    ) {
        return previous.copy(selection = TextRange(start + 1))
    }

    val closing = when (typed) {
        '(' -> ')'
        '[' -> ']'
        '{' -> '}'
        '\'' -> '\''
        '"' -> '"'
        else -> return next
    }
    val edit = EditorEdits.surround(
        previous.text,
        previous.selection.min,
        previous.selection.max,
        typed.toString(),
        closing.toString(),
    )
    return TextFieldValue(
        edit.text,
        TextRange(edit.selectionStart, edit.selectionEnd),
    )
}

private fun TextFieldValue.toSnapshot(): EditorHistory.Snapshot =
    EditorHistory.Snapshot(text, selection.start, selection.end)

private fun EditorHistory.Snapshot.toTextFieldValue(): TextFieldValue =
    TextFieldValue(text, TextRange(selectionStart, selectionEnd))
