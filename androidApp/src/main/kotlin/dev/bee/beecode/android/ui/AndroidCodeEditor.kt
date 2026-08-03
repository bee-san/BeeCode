package dev.bee.beecode.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.android.LocalBeeCodePalette
import dev.bee.beecode.design.EditorEdits
import dev.bee.beecode.design.EditorHistory
import dev.bee.beecode.design.EditorPreferences
import dev.bee.beecode.design.MobileEditorAction
import dev.bee.beecode.design.PythonSyntax
import dev.bee.beecode.design.sourceOffset
import dev.bee.beecode.python.RunDiagnostic

internal class AndroidEditorSession(initialSource: String) {
    val valueState = mutableStateOf(
        TextFieldValue(initialSource, TextRange(initialSource.length)),
    )
    val history = EditorHistory(
        EditorHistory.Snapshot(initialSource, initialSource.length, initialSource.length),
    )
}

@Composable
internal fun AndroidCodeEditor(
    session: AndroidEditorSession,
    source: String,
    onSourceChange: (String) -> Unit,
    onResetToStarter: () -> Unit,
    onRun: () -> Unit,
    onCancelRun: () -> Unit,
    isRunning: Boolean,
    preferences: EditorPreferences,
    diagnostic: RunDiagnostic?,
    fullScreen: Boolean,
    onFullScreenChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    var value by session.valueState
    val history = session.history
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
        emit(TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd)))
    }

    fun applyBlock(edit: EditorEdits.BlockEdit) {
        emit(TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd)))
    }

    fun insert(text: String) {
        val edit = EditorEdits.insert(value.text, value.selection.min, value.selection.max, text)
        emit(TextFieldValue(edit.text, TextRange(edit.caret)))
    }

    fun surround(opening: String, closing: String) {
        applySelection(
            EditorEdits.surround(
                value.text,
                value.selection.min,
                value.selection.max,
                opening,
                closing,
            ),
        )
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

    fun runAction(action: MobileEditorAction) {
        val start = value.selection.min
        val end = value.selection.max
        when (action) {
            MobileEditorAction.INDENT -> {
                val spansLines = !value.selection.collapsed &&
                    value.text.substring(start, end).contains('\n')
                if (spansLines) applyBlock(EditorEdits.indentBlock(value.text, start, end))
                else insert(EditorEdits.INDENT)
            }
            MobileEditorAction.OUTDENT ->
                applyBlock(EditorEdits.dedentBlock(value.text, start, end))
            MobileEditorAction.CURSOR_LEFT ->
                applySelection(EditorEdits.moveCaret(value.text, value.selection.min, -1))
            MobileEditorAction.CURSOR_RIGHT ->
                applySelection(EditorEdits.moveCaret(value.text, value.selection.max, 1))
            MobileEditorAction.UNDO -> applySnapshot(history.undo())
            MobileEditorAction.REDO -> applySnapshot(history.redo())
            MobileEditorAction.COLON -> insert(":")
            MobileEditorAction.PARENTHESES -> surround("(", ")")
            MobileEditorAction.BRACKETS -> surround("[", "]")
            MobileEditorAction.BRACES -> surround("{", "}")
            MobileEditorAction.DOUBLE_QUOTE -> surround("\"", "\"")
            MobileEditorAction.UNDERSCORE -> insert("_")
            MobileEditorAction.EQUALS -> insert("=")
            MobileEditorAction.LESS_THAN -> insert("<")
            MobileEditorAction.GREATER_THAN -> insert(">")
            MobileEditorAction.PLUS -> insert("+")
            MobileEditorAction.MINUS -> insert("-")
            MobileEditorAction.ASTERISK -> insert("*")
            MobileEditorAction.SLASH -> insert("/")
            MobileEditorAction.PERCENT -> insert("%")
            MobileEditorAction.DOT -> insert(".")
            MobileEditorAction.COMMA -> insert(",")
            MobileEditorAction.HASH -> insert("#")
        }
    }

    fun indentedNewlineOrNull(next: TextFieldValue): TextFieldValue? {
        val before = value.text
        val caretBefore = value.selection.min
        if (!value.selection.collapsed) return null
        if (next.text.length != before.length + 1) return null
        if (next.selection.min != caretBefore + 1) return null
        if (next.text.getOrNull(caretBefore) != '\n') return null
        if (next.text.removeRange(caretBefore, caretBefore + 1) != before) return null
        val edit = EditorEdits.newlineWithIndent(before, caretBefore)
        return TextFieldValue(edit.text, TextRange(edit.caret))
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

    fun replaceCurrent() {
        val matches = EditorEdits.findAll(value.text, query, matchCase, wholeWord)
        val selected = matches.firstOrNull {
            it.start == value.selection.min && it.end == value.selection.max
        } ?: matches.getOrNull(currentMatch.coerceIn(0, (matches.size - 1).coerceAtLeast(0)))
        if (selected == null) return
        val edit = EditorEdits.insert(value.text, selected.start, selected.end, replacement)
        emit(TextFieldValue(edit.text, TextRange(edit.caret)))
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
    val colors = AndroidEditorColors(
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
        colors,
    ) {
        androidEditorVisualTransformation(
            value.text,
            matches,
            bracketMatch,
            diagnosticOffsets,
            colors,
        )
    }
    val lineCount = value.text.count { it == '\n' } + 1
    val lineNumberWidth = (lineCount.toString().length * 8 + 16).dp
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
    val activeLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)

    val content: @Composable ColumnScope.() -> Unit = {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Your solution",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = if (isRunning) onCancelRun else onRun,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) { Text(if (isRunning) "Stop" else "Run") }
            TextButton(onClick = { onFullScreenChange(!fullScreen) }) {
                Text(if (fullScreen) "Exit full screen" else "Full screen")
            }
            TextButton(onClick = onResetToStarter) { Text("Reset") }
        }

        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(
                onClick = {
                    showSearch = !showSearch
                    showReplace = false
                },
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Outlined.Search, contentDescription = "Find")
            }
            TextButton(
                onClick = {
                    showSearch = true
                    showReplace = true
                },
            ) { Text("Replace") }
            TextButton(onClick = { showGoToLine = !showGoToLine }) { Text("Go to line") }
            TextButton(onClick = ::toggleComment) { Text("Comment") }
        }

        if (showSearch) {
            OutlinedTextField(
                value = query,
                onValueChange = {
                    query = it
                    currentMatch = 0
                },
                label = { Text("Find") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Find query" },
            )
            if (showReplace) {
                OutlinedTextField(
                    value = replacement,
                    onValueChange = { replacement = it },
                    label = { Text("Replace with") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Replacement" },
                )
            }
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = matchCase, onCheckedChange = { matchCase = it })
                Text("Case", style = MaterialTheme.typography.labelSmall)
                Checkbox(checked = wholeWord, onCheckedChange = { wholeWord = it })
                Text("Word", style = MaterialTheme.typography.labelSmall)
                Spacer(Modifier.weight(1f))
                Text(
                    if (matches.isEmpty()) {
                        "No matches"
                    } else {
                        "${currentMatch.coerceIn(0, matches.lastIndex) + 1}/${matches.size}"
                    },
                    style = MaterialTheme.typography.labelSmall,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { selectMatch(-1) }, enabled = matches.isNotEmpty()) {
                    Text("Previous")
                }
                OutlinedButton(onClick = { selectMatch(1) }, enabled = matches.isNotEmpty()) {
                    Text("Next")
                }
                if (showReplace) {
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
                    ) { Text("All") }
                }
            }
        }

        if (showGoToLine) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
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
                Button(
                    onClick = {
                        lineInput.toIntOrNull()?.let { line ->
                            val offset = EditorEdits.goToLine(value.text, line)
                            value = value.copy(selection = TextRange(offset))
                            showGoToLine = false
                        }
                    },
                    enabled = lineInput.isNotBlank(),
                ) { Text("Go") }
            }
        }

        Box(
            Modifier
                .fillMaxWidth()
                .then(
                    if (fullScreen) {
                        Modifier.weight(1f).fillMaxHeight()
                    } else {
                        Modifier.heightIn(min = 200.dp)
                    },
                )
                .testTag(CODE_EDITOR_TAG)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
        ) {
            BasicTextField(
                value = value,
                onValueChange = { next ->
                    emit(indentedNewlineOrNull(next) ?: smartPair(value, next))
                },
                modifier = Modifier
                    .fillMaxSize()
                    .then(
                        if (fullScreen) {
                            Modifier.verticalScroll(verticalScroll)
                        } else {
                            Modifier
                        },
                    )
                    .semantics { contentDescription = "Python solution editor" }
                    .onPreviewKeyEvent { event ->
                        if (event.type != KeyEventType.KeyDown) {
                            return@onPreviewKeyEvent false
                        }
                        val command = event.isCtrlPressed || event.isMetaPressed
                        if (command) {
                            when (event.key) {
                                Key.Z -> {
                                    applySnapshot(
                                        if (event.isShiftPressed) history.redo() else history.undo(),
                                    )
                                    return@onPreviewKeyEvent true
                                }
                                Key.Y -> {
                                    applySnapshot(history.redo())
                                    return@onPreviewKeyEvent true
                                }
                                Key.F -> {
                                    showSearch = true
                                    showReplace = false
                                    return@onPreviewKeyEvent true
                                }
                                Key.H -> {
                                    showSearch = true
                                    showReplace = true
                                    return@onPreviewKeyEvent true
                                }
                                Key.G -> {
                                    showGoToLine = true
                                    return@onPreviewKeyEvent true
                                }
                                Key.Slash -> {
                                    toggleComment()
                                    return@onPreviewKeyEvent true
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    onRun()
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                        when (event.key) {
                            Key.Tab -> {
                                runAction(
                                    if (event.isShiftPressed) {
                                        MobileEditorAction.OUTDENT
                                    } else {
                                        MobileEditorAction.INDENT
                                    },
                                )
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
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                    imeAction = ImeAction.Default,
                ),
                decorationBox = { innerTextField ->
                    Row(Modifier.fillMaxSize()) {
                        Text(
                            text = lineNumbers,
                            modifier = Modifier
                                .widthIn(min = lineNumberWidth)
                                .fillMaxHeight()
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                                .padding(horizontal = 5.dp, vertical = 8.dp)
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
                                        activeLineColor,
                                        Offset(0f, top),
                                        Size(size.width, bottom - top),
                                    )
                                }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                        ) {
                            innerTextField()
                        }
                    }
                },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .testTag(SYMBOL_ROW_TAG),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            preferences.mobileActions.forEach { action ->
                EditorActionKey(
                    action = action,
                    enabled = when (action) {
                        MobileEditorAction.UNDO -> history.canUndo
                        MobileEditorAction.REDO -> history.canRedo
                        else -> true
                    },
                    onClick = { runAction(action) },
                )
            }
        }
    }

    if (fullScreen) {
        Column(
            modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            content = content,
        )
    } else {
        Card(modifier) {
            Column(
                Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

@Composable
private fun EditorActionKey(
    action: MobileEditorAction,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    when (action) {
        MobileEditorAction.CURSOR_LEFT,
        MobileEditorAction.CURSOR_RIGHT,
        -> IconButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(40.dp)
                .semantics { contentDescription = action.label },
        ) {
            Icon(
                if (action == MobileEditorAction.CURSOR_LEFT) {
                    Icons.AutoMirrored.Outlined.KeyboardArrowLeft
                } else {
                    Icons.AutoMirrored.Outlined.KeyboardArrowRight
                },
                contentDescription = null,
            )
        }
        else -> OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier.heightIn(min = 40.dp),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                when (action) {
                    MobileEditorAction.INDENT -> "Tab"
                    MobileEditorAction.OUTDENT -> "Shift Tab"
                    MobileEditorAction.UNDO -> "Undo"
                    MobileEditorAction.REDO -> "Redo"
                    MobileEditorAction.COLON -> ":"
                    MobileEditorAction.PARENTHESES -> "()"
                    MobileEditorAction.BRACKETS -> "[]"
                    MobileEditorAction.BRACES -> "{}"
                    MobileEditorAction.DOUBLE_QUOTE -> "\"\""
                    MobileEditorAction.UNDERSCORE -> "_"
                    MobileEditorAction.EQUALS -> "="
                    MobileEditorAction.LESS_THAN -> "<"
                    MobileEditorAction.GREATER_THAN -> ">"
                    MobileEditorAction.PLUS -> "+"
                    MobileEditorAction.MINUS -> "-"
                    MobileEditorAction.ASTERISK -> "*"
                    MobileEditorAction.SLASH -> "/"
                    MobileEditorAction.PERCENT -> "%"
                    MobileEditorAction.DOT -> "."
                    MobileEditorAction.COMMA -> ","
                    MobileEditorAction.HASH -> "#"
                    MobileEditorAction.CURSOR_LEFT,
                    MobileEditorAction.CURSOR_RIGHT,
                    -> error("cursor actions use icon buttons")
                },
                modifier = Modifier.semantics { contentDescription = action.label },
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

private data class AndroidEditorColors(
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

private fun androidEditorVisualTransformation(
    source: String,
    matches: List<EditorEdits.SearchMatch>,
    bracketMatch: PythonSyntax.BracketMatch?,
    diagnosticOffsets: Pair<Int, Int>?,
    colors: AndroidEditorColors,
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
    return TextFieldValue(edit.text, TextRange(edit.selectionStart, edit.selectionEnd))
}

private fun TextFieldValue.toSnapshot(): EditorHistory.Snapshot =
    EditorHistory.Snapshot(text, selection.start, selection.end)

private fun EditorHistory.Snapshot.toTextFieldValue(): TextFieldValue =
    TextFieldValue(text, TextRange(selectionStart, selectionEnd))
