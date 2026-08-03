package dev.bee.beecode.android.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.android.LocalBeeCodePalette
import dev.bee.beecode.android.accentCaution
import dev.bee.beecode.android.accentDanger
import dev.bee.beecode.android.accentSuccess
import dev.bee.beecode.design.EditorPreferences
import dev.bee.beecode.design.Markdown
import dev.bee.beecode.design.RunOutcomePresentation
import dev.bee.beecode.design.ScreenReaderLabels
import dev.bee.beecode.design.tint
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.domain.formatIntervalDays
import dev.bee.beecode.python.RunDiagnostic

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
    editorPreferences: EditorPreferences,
    onSourceChange: (String) -> Unit,
    onRun: () -> Unit,
    onCancelRun: () -> Unit,
    onReveal: () -> Unit,
    onFinalize: (ReviewRating) -> Unit,
    onResetToStarter: () -> Unit,
    onClose: () -> Unit,
) {
    var editorFullScreen by remember(state.problem.id) { mutableStateOf(false) }
    val editorSession = remember(state.problem.id) { AndroidEditorSession(state.source) }
    BackHandler(enabled = editorFullScreen) { editorFullScreen = false }
    if (editorFullScreen) {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            AndroidCodeEditor(
                session = editorSession,
                source = state.source,
                onSourceChange = onSourceChange,
                onResetToStarter = onResetToStarter,
                onRun = onRun,
                onCancelRun = onCancelRun,
                isRunning = state.isRunning,
                preferences = editorPreferences,
                diagnostic = state.latestDiagnostic,
                fullScreen = true,
                onFullScreenChange = { editorFullScreen = it },
                modifier = Modifier.fillMaxSize(),
            )
        }
        return
    }

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
            AndroidCodeEditor(
                session = editorSession,
                source = state.source,
                onSourceChange = onSourceChange,
                onResetToStarter = onResetToStarter,
                onRun = onRun,
                onCancelRun = onCancelRun,
                isRunning = state.isRunning,
                preferences = editorPreferences,
                diagnostic = state.latestDiagnostic,
                fullScreen = false,
                onFullScreenChange = { editorFullScreen = it },
            )

            if (state.isRunning) {
                RunningIndicator(onCancelRun)
            }

            state.latestRun?.let { run -> ResultCard(run, state.latestDiagnostic) }

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
private fun ResultCard(run: ExecutionRun, diagnostic: RunDiagnostic?) {
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

            diagnostic?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    it.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (
                        run.outcome == ExecutionOutcome.SYNTAX_ERROR ||
                        run.outcome == ExecutionOutcome.RUNTIME_ERROR
                    ) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontFamily = FontFamily.Monospace,
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
