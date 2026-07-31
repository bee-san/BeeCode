package dev.bee.beecode.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.android.LocalBeeCodePalette
import dev.bee.beecode.android.accentCaution
import dev.bee.beecode.android.accentDanger
import dev.bee.beecode.android.accentSuccess
import dev.bee.beecode.design.EditorEdits
import dev.bee.beecode.design.Markdown
import dev.bee.beecode.design.RunOutcomePresentation
import dev.bee.beecode.design.ScreenReaderLabels
import dev.bee.beecode.design.tint
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.domain.formatIntervalDays

internal const val CODE_EDITOR_TAG = "code-editor"
internal const val SYMBOL_ROW_TAG = "symbol-row"

/**
 * The Problem view: statement, editor, results, and finalize.
 *
 * One vertically scrolling column rather than tabs. On a phone the learner moves
 * between reading the Problem, writing code, and reading a failure constantly, and
 * tabs would make that a navigation task instead of a scroll.
 */
@Composable
fun ProblemScreen(
    state: ProblemUiState,
    onSourceChange: (String) -> Unit,
    onRun: () -> Unit,
    onCancelRun: () -> Unit,
    onReveal: () -> Unit,
    onFinalize: (ReviewRating) -> Unit,
    onResetToStarter: () -> Unit,
    onClose: () -> Unit,
) {
    Scaffold(
        bottomBar = {
            ActionBar(
                state = state,
                onRun = onRun,
                onCancelRun = onCancelRun,
                onFinalize = onFinalize,
                onClose = onClose,
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                // So the editor is not hidden behind the soft keyboard.
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ProblemHeader(state, onClose)

            state.message?.let { message ->
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Text(
                        message,
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            StatementCard(state)
            CodeEditor(state.source, onSourceChange, onResetToStarter)

            if (state.isRunning) {
                RunningIndicator(onCancelRun)
            }

            state.latestRun?.let { run -> ResultCard(run) }

            state.finalized?.let { finalized -> FinalizedCard(finalized, onClose) }

            if (state.problem.hasExplanation && state.revealedExplanation == null &&
                state.finalized == null
            ) {
                RevealPrompt(onReveal)
            }

            state.revealedExplanation?.let { explanation ->
                ExplanationCard(explanation)
            }

            if (state.history.isNotEmpty()) {
                HistoryCard(state)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ProblemHeader(state: ProblemUiState, onClose: () -> Unit) {
    Column(Modifier.padding(top = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onClose, contentPadding = PaddingValues(4.dp)) { Text("← Back") }
            Spacer(Modifier.weight(1f))
            if (state.aided) {
                // Say plainly that the ceiling has dropped, rather than silently
                // disabling the Good and Easy buttons.
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "Answer revealed",
                        Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
        }
        Text(
            state.problem.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )
        Text(
            // The memory state FSRS is holding for *this* Problem, alongside the topics. It
            // was stored on every review and shown nowhere, so the learner had no way to see
            // that repeated success was lengthening the interval — which is the entire
            // premise they are being asked to trust.
            buildString {
                append(state.problem.topics.joinToString(" · "))
                state.schedule?.let { schedule ->
                    append("  ·  reviewed ${schedule.reviewCount}×")
                    append(", interval ${formatIntervalDays(schedule.intervalDays)}")
                } ?: append("  ·  first attempt")
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatementCard(state: ProblemUiState) {
    var expanded by remember { mutableStateOf(true) }
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Problem",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { expanded = !expanded }) {
                    Text(if (expanded) "Hide" else "Show")
                }
            }
            if (expanded) {
                Spacer(Modifier.height(4.dp))
                // Rendered as lightly formatted plain text rather than with a
                // Markdown library: the statements use a small, known subset, and a
                // dependency for that would be poor value.
                MarkdownText(state.problem.statementMarkdown)

                if (state.problem.examples.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Examples", style = MaterialTheme.typography.titleSmall)
                    state.problem.examples.forEach { example ->
                        Spacer(Modifier.height(6.dp))
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surface,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(10.dp),
                        ) {
                            MonoText("Input:  ${example.input}")
                            MonoText("Output: ${example.output}")
                            example.explanation?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * The code editor.
 *
 * A `BasicTextField` with monospace text, autocorrect and capitalisation disabled, and a
 * symbol row. Those three settings matter more than they look: an IME that capitalises
 * `Def` or autocorrects `nums` produces syntax errors the learner did not write, and a
 * phone keyboard with no colon or bracket makes Python unwritable.
 *
 * ## Indentation
 *
 * Enter carries the current line's indentation and adds a level after a `:`, and
 * Backspace inside leading whitespace removes a whole level. Both come from
 * [EditorEdits], which the desktop editor also uses — this client had neither, so the
 * harder of the two keyboards to type Python on had the less help. Re-indenting by hand
 * after every `if` is tedious on a desktop and genuinely discouraging on a phone.
 *
 * Enter is detected in `onValueChange` rather than as a key event, because a soft
 * keyboard's Enter arrives as an already-committed text change and never reaches
 * `onPreviewKeyEvent`. Tab and Shift+Tab are the reverse — no soft keyboard sends them,
 * so they are handled only on the key path, for a hardware keyboard.
 */
@Composable
private fun CodeEditor(
    source: String,
    onSourceChange: (String) -> Unit,
    onResetToStarter: () -> Unit,
) {
    // Own the selection locally so inserting a symbol can place the caret after it.
    var value by remember { mutableStateOf(TextFieldValue(source, TextRange(source.length))) }

    // Adopt [source] only when it genuinely disagrees with the buffer — a reset to
    // starter, or a different Problem. Keying `remember` on `source` re-ran the
    // initialiser after every keystroke, because the caller echoes `source` back from
    // this editor's own `onSourceChange`, and rebuilding the selection as
    // `TextRange(source.length)` sent the caret to the end of the buffer after each
    // edit. Every symbol-row insertion after the first therefore appended instead of
    // landing at the caret. The desktop editor had the identical defect.
    if (value.text != source) {
        value = TextFieldValue(source, TextRange(source.length))
    }

    fun update(next: TextFieldValue) {
        value = next
        onSourceChange(next.text)
    }

    /**
     * Insert text at the caret, replacing any selection.
     *
     * The caret then sits after the inserted text, which is what makes the symbol
     * row usable for typing rather than a novelty.
     */
    fun insert(text: String) {
        val start = value.selection.min
        val end = value.selection.max
        val updated = value.text.replaceRange(start, end, text)
        update(TextFieldValue(updated, TextRange(start + text.length)))
    }

    /**
     * Auto-indent a newline the soft keyboard just inserted.
     *
     * Detected from the resulting text rather than from a key event, and that is not a
     * shortcut: a soft keyboard's Enter arrives through `onValueChange` as an already-
     * committed edit, and `onPreviewKeyEvent` — which is how the desktop editor does
     * this — never fires for it. Writing this the desktop way would have produced code
     * that looked right, compiled, and did nothing on a phone.
     *
     * Recognised as *exactly one* "\n" inserted at the caret. A paste containing
     * newlines already carries its own indentation and must not be re-indented, and a
     * newline typed into the middle of a line is still an Enter, so the test is on the
     * shape of the edit and not on the caret's position in the line.
     */
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

    Card {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Your solution",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onResetToStarter) { Text("Reset") }
            }

            Spacer(Modifier.height(6.dp))

            Box(
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp)
                    .testTag(CODE_EDITOR_TAG)
                    .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(6.dp)),
            ) {
                BasicTextField(
                    value = value,
                    onValueChange = { next -> update(indentedNewlineOrNull(next) ?: next) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(10.dp)
                        // Horizontal scroll so a long line is reachable rather than
                        // wrapped into misleading indentation.
                        .horizontalScroll(rememberScrollState())
                        // Tab and Backspace from a *hardware* keyboard, which a phone in a
                        // dock or a tablet with a case has. The soft keyboard has no Tab at
                        // all — the symbol row's first key is what serves that purpose there
                        // — so this path is additive rather than the main one, and Enter is
                        // deliberately absent from it: a soft Enter never reaches here, so
                        // handling it in both places would double-indent on hardware.
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                            when (event.key) {
                                Key.Tab -> {
                                    val start = value.selection.min
                                    val end = value.selection.max
                                    val spansLines = !value.selection.collapsed &&
                                        value.text.substring(start, end).contains('\n')
                                    when {
                                        event.isShiftPressed -> EditorEdits
                                            .dedentBlock(value.text, start, end)
                                            .let {
                                                update(
                                                    TextFieldValue(
                                                        it.text,
                                                        TextRange(it.selectionStart, it.selectionEnd),
                                                    ),
                                                )
                                            }
                                        spansLines -> EditorEdits
                                            .indentBlock(value.text, start, end)
                                            .let {
                                                update(
                                                    TextFieldValue(
                                                        it.text,
                                                        TextRange(it.selectionStart, it.selectionEnd),
                                                    ),
                                                )
                                            }
                                        else -> insert(EditorEdits.INDENT)
                                    }
                                    true
                                }
                                // Only when it would remove a whole indent level; otherwise
                                // fall through so ordinary character deletion still works.
                                Key.Backspace -> {
                                    val edit = if (value.selection.collapsed) {
                                        EditorEdits.dedent(value.text, value.selection.min)
                                    } else {
                                        null
                                    }
                                    if (edit == null) {
                                        false
                                    } else {
                                        update(TextFieldValue(edit.text, TextRange(edit.caret)))
                                        true
                                    }
                                }
                                else -> false
                            }
                        }
                        .semantics { contentDescription = "Python solution editor" },
                    textStyle = TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                    ),
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(
                        // All three matter: autocorrect and capitalisation turn
                        // valid Python into syntax errors the learner never typed.
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Ascii,
                        imeAction = ImeAction.Default,
                    ),
                )
            }

            Spacer(Modifier.height(8.dp))
            SymbolRow(onInsert = ::insert)
        }
    }
}

/**
 * The symbol row.
 *
 * Python needs `:`, `_`, brackets, and indentation, and a phone keyboard buries or
 * omits them. The first entry inserts four spaces, because indentation is
 * syntactically significant in Python and a tab character is not equivalent.
 */
@Composable
private fun SymbolRow(onInsert: (String) -> Unit) {
    val symbols = listOf(
        "    " to "⇥",
        ":" to ":",
        "_" to "_",
        "(" to "(",
        ")" to ")",
        "[" to "[",
        "]" to "]",
        "{" to "{",
        "}" to "}",
        "\"" to "\"",
        "=" to "=",
        "<" to "<",
        ">" to ">",
        "+" to "+",
        "-" to "-",
        "*" to "*",
        "/" to "/",
        "%" to "%",
        "." to ".",
        "," to ",",
        "#" to "#",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .testTag(SYMBOL_ROW_TAG),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        symbols.forEach { (text, label) ->
            Surface(
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)),
                shape = RoundedCornerShape(6.dp),
                onClick = { onInsert(text) },
            ) {
                Text(
                    label,
                    Modifier
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .semantics {
                            contentDescription = if (text == "    ") "Insert indent" else "Insert $text"
                        },
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                )
            }
        }
    }
}

@Composable
private fun RunningIndicator(onCancel: () -> Unit) {
    Card {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(12.dp))
            Text("Running your code…", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = onCancel) { Text("Stop") }
        }
    }
}

/**
 * The result of a run.
 *
 * Every outcome gets its own headline, because the learner's next action differs:
 * a syntax error is a typo, a timeout is an algorithmic problem, and a worker
 * failure is BeeCode's fault rather than theirs.
 */
@Composable
private fun ResultCard(run: ExecutionRun) {
    // The mapping is in :shared so desktop cannot word the same outcome differently.
    val outcome = RunOutcomePresentation.of(run.outcome, run.passedTestCount, run.totalTestCount)
    val tint = Color(outcome.tint(LocalBeeCodePalette.current))

    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A glyph rather than the coloured dot this replaced. The dot's only
                // content was its tint, so a learner who cannot separate the green from
                // the amber — or who reads the screen in greyscale — got nothing from it
                // that the headline did not already say, which is what WCAG 1.4.1
                // forbids. The glyph says pass, warn, or fail in its shape.
                Text(
                    outcome.glyph,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    // Cleared rather than described: `outcome.headline` is the very next
                    // node and already reads "All tests passed". Unlike the per-test rows,
                    // where the glyph is the only verdict, here it is a second copy of one.
                    modifier = Modifier.clearAndSetSemantics {},
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    outcome.headline,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    "${run.durationMillis} ms",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            run.let { it.testResults }.takeIf { it.isNotEmpty() }?.let { results ->
                Spacer(Modifier.height(10.dp))
                results.forEach { TestResultRow(it) }
            }

            // Diagnostics carry the traceback or the timeout explanation.
            (run.let { r -> r.testResults.firstOrNull { !it.passed }?.message }
                ?.takeIf { run.testResults.isNotEmpty() })
                ?.let { }

            if (run.output.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    if (run.outputTruncated) "Your output (truncated)" else "Your output",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(8.dp),
                ) {
                    MonoText(run.output.trim())
                }
            }
        }
    }
}

@Composable
private fun TestResultRow(result: TestCaseResult) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (result.passed) "✓" else "✗",
                color = if (result.passed) accentSuccess() else accentDanger(),
                fontWeight = FontWeight.Bold,
                // The glyph is the only thing separating a pass from a failure here — the
                // rest of the row is the test's name, identical either way. Left bare,
                // TalkBack announces the character or skips it, so a learner heard a list
                // of test names with no verdicts. `clearAndSetSemantics` replaces the
                // glyph rather than adding to it.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = ScreenReaderLabels.testCase(result.passed)
                },
            )
            Spacer(Modifier.width(8.dp))
            Text(
                result.name + if (result.hidden) " (hidden)" else "",
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
        }
        // A hidden test reports pass or fail but withholds its values, so the
        // Problem cannot be solved by reading the assertions.
        if (!result.passed && !result.hidden) {
            result.message?.let { message ->
                Spacer(Modifier.height(2.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(6.dp),
                ) {
                    MonoText(message)
                }
            }
        }
    }
}

/**
 * The rating buttons.
 *
 * Only the ratings the evidence permits are shown at all. Showing a disabled
 * "Easy" after a failure would invite the learner to argue with the rule; omitting
 * it states the rule instead.
 */
@Composable
private fun ActionBar(
    state: ProblemUiState,
    onRun: () -> Unit,
    onCancelRun: () -> Unit,
    onFinalize: (ReviewRating) -> Unit,
    onClose: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.padding(12.dp).imePadding()) {
            if (state.finalized != null) {
                Button(onClick = onClose, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
                return@Column
            }

            if (state.canFinalize) {
                Text(
                    "How did that go?",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReviewRating.entries
                        .filter { it in state.permittedRatings }
                        .forEach { rating ->
                            val suggested = rating == state.suggestedRating
                            if (suggested) {
                                Button(
                                    onClick = { onFinalize(rating) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 10.dp),
                                ) { Text(rating.label(), fontSize = 13.sp) }
                            } else {
                                OutlinedButton(
                                    onClick = { onFinalize(rating) },
                                    modifier = Modifier.weight(1f),
                                    contentPadding = PaddingValues(vertical = 10.dp),
                                ) { Text(rating.label(), fontSize = 13.sp) }
                            }
                        }
                }
                Spacer(Modifier.height(8.dp))
            }

            if (state.isRunning) {
                OutlinedButton(onClick = onCancelRun, modifier = Modifier.fillMaxWidth()) {
                    Text("Stop")
                }
            } else {
                Button(onClick = onRun, modifier = Modifier.fillMaxWidth()) {
                    Text(if (state.latestRun == null) "Run tests" else "Run again")
                }
            }
        }
    }
}

private fun ReviewRating.label(): String = when (this) {
    ReviewRating.AGAIN -> "Again"
    ReviewRating.HARD -> "Hard"
    ReviewRating.GOOD -> "Good"
    ReviewRating.EASY -> "Easy"
}

@Composable
private fun FinalizedCard(finalized: FinalizedUiState, onClose: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                if (finalized.review.countsAsSolved) "Solved" else "Review recorded",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(6.dp))
            finalized.schedule?.let { schedule ->
                Text(
                    "Next review in ${formatIntervalDays(schedule.intervalDays)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                // The memory-strength change behind that interval. "Next review in 6 days"
                // alone does not say whether the review *helped*; stability moving from 4
                // days to 9 does, and it is the number FSRS actually optimises. Read from
                // the recorded transition rather than recomputed, so what is shown is what
                // was stored.
                val transition = finalized.review.transition
                Text(
                    buildString {
                        append("Memory strength ")
                        transition.previousStability?.let {
                            append("${formatIntervalDays(it)} → ")
                        }
                        append(formatIntervalDays(transition.nextStability))
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(onClick = onClose) { Text("Continue studying") }
        }
    }
}

/**
 * The reveal prompt.
 *
 * States the cost before the learner commits, rather than after. Revealing is a
 * legitimate choice when genuinely stuck, but it must be an informed one.
 */
@Composable
private fun RevealPrompt(onReveal: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Stuck?", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "You can read the explanation. Doing so means this review will not count " +
                    "as solved, and the best rating available becomes Hard — because a pass " +
                    "after reading the answer is recognition rather than recall.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onReveal) { Text("Show the explanation") }
        }
    }
}

@Composable
private fun ExplanationCard(explanation: String) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Explanation", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            MarkdownText(explanation)
        }
    }
}

@Composable
private fun HistoryCard(state: ProblemUiState) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Your history", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            state.history.takeLast(8).reversed().forEach { review ->
                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                    Text(
                        review.localDate().toString(),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.width(96.dp),
                    )
                    Text(
                        review.rating.label(),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                    if (review.aided) {
                        Text(
                            "revealed",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the Markdown subset the Problem statements use.
 *
 * The parsing lives in [Markdown] so this and desktop's renderer cannot disagree about
 * what a statement means — they had each grown their own copy, with the same two bugs.
 * What remains here is only the styling, which is the part that should differ per client:
 * this uses `bodySmall` for a phone where desktop uses `bodyMedium`.
 *
 * Paragraphs are separated by spacing rather than by one `Text` per source line. That is
 * the fix for the wrapping: the content is hard-wrapped in its source file, and honouring
 * those newlines made every wrapped sentence break mid-clause on a narrow screen.
 */
@Composable
private fun MarkdownText(markdown: String) {
    Column {
        Markdown.blocks(markdown).forEachIndexed { index, block ->
            // Between blocks, not after each: a trailing gap inside a card reads as a
            // layout mistake, and the card already has its own padding.
            if (index > 0) Spacer(Modifier.height(if (block is Markdown.Block.Heading) 10.dp else 6.dp))
            when (block) {
                is Markdown.Block.Heading -> Text(
                    block.text,
                    style = if (block.level == 1) {
                        MaterialTheme.typography.titleMedium
                    } else {
                        MaterialTheme.typography.titleSmall
                    },
                    fontWeight = if (block.level == 1) FontWeight.Bold else FontWeight.SemiBold,
                )
                is Markdown.Block.Paragraph -> Text(
                    block.text,
                    style = MaterialTheme.typography.bodySmall,
                )
                is Markdown.Block.Bullet -> MarkdownListItem("•", block.text)
                is Markdown.Block.Numbered -> MarkdownListItem(block.marker, block.text)
                is Markdown.Block.Code -> Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(10.dp),
                ) {
                    MonoText(block.lines.joinToString("\n"))
                }
            }
        }
    }
}

/**
 * A list item whose wrapped lines line up under its text rather than under its marker.
 *
 * The marker sits in its own column: with the marker inline, a bullet long enough to wrap
 * — and on a phone most of them are — put its second line hard against the margin, which
 * is what stops a list looking like a list.
 */
@Composable
private fun MarkdownListItem(marker: String, text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            marker,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.width(22.dp),
        )
        Text(text, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun MonoText(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}
