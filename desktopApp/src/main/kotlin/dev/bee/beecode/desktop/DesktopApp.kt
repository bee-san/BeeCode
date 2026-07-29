package dev.bee.beecode.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.app.AchievementState
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.DueProblem
import dev.bee.beecode.app.FinalizeResult
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.app.RunnerStatus
import dev.bee.beecode.app.StudyQueue
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewRatingPolicy
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.python.RunnerCapability
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The desktop UI.
 *
 * The same shared study service Android drives, presented for a large screen: a
 * navigation rail instead of a bottom bar, and the Problem view as two panes so the
 * statement stays visible while writing code. On a phone that had to be a scroll;
 * here it does not, and reading the Problem while typing is most of the work.
 */
@Composable
fun DesktopApp(profile: BeeCodeProfile) {
    var screen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Queue) }
    var openProblem by remember { mutableStateOf<ProblemId?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    var runnerStatus by remember { mutableStateOf<RunnerStatus?>(null) }

    LaunchedEffect(Unit) { runnerStatus = profile.study.runnerStatus() }

    val active = openProblem
    if (active != null) {
        ProblemPane(
            profile = profile,
            problemId = active,
            onClose = {
                openProblem = null
                // Bump so the queue and statistics re-read after a review.
                refreshToken++
            },
        )
        return
    }

    Row(Modifier.fillMaxSize()) {
        NavigationRail {
            NavigationRailItem(
                selected = screen is DesktopScreen.Queue,
                onClick = { screen = DesktopScreen.Queue; refreshToken++ },
                icon = { Text("🐝", fontSize = 20.sp) },
                label = { Text("Study") },
            )
            NavigationRailItem(
                selected = screen is DesktopScreen.Progress,
                onClick = { screen = DesktopScreen.Progress; refreshToken++ },
                icon = { Text("📊", fontSize = 20.sp) },
                label = { Text("Progress") },
            )
            NavigationRailItem(
                selected = screen is DesktopScreen.Settings,
                onClick = { screen = DesktopScreen.Settings },
                icon = { Text("⚙", fontSize = 20.sp) },
                label = { Text("Settings") },
            )
        }
        VerticalDivider()
        Box(Modifier.fillMaxSize()) {
            when (screen) {
                is DesktopScreen.Queue -> QueuePane(
                    profile = profile,
                    refreshToken = refreshToken,
                    runnerStatus = runnerStatus,
                    onOpen = { openProblem = it },
                )
                is DesktopScreen.Progress -> ProgressPane(profile, refreshToken)
                is DesktopScreen.Settings -> SettingsPane(profile, runnerStatus)
            }
        }
    }
}

private sealed interface DesktopScreen {
    data object Queue : DesktopScreen

    data object Progress : DesktopScreen

    data object Settings : DesktopScreen
}

@Composable
private fun QueuePane(
    profile: BeeCodeProfile,
    refreshToken: Int,
    runnerStatus: RunnerStatus?,
    onOpen: (ProblemId) -> Unit,
) {
    // Re-read whenever the token changes, which happens after a review is
    // finalized. Deriving from the token rather than caching means the queue can
    // never show a Problem that is no longer due.
    val queue: StudyQueue = remember(refreshToken) { profile.study.queue() }
    val statistics: StudyStatistics = remember(refreshToken) { profile.statistics() }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    "BeeCode",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    buildString {
                        append("${statistics.distinctProblemsSolved} of ")
                        append("${statistics.totalProblems} solved")
                        if (statistics.currentStreakDays > 0) {
                            append(" · ${statistics.currentStreakDays} day streak")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // Surface a missing interpreter here rather than letting the learner find
        // out when their first review mysteriously fails.
        runnerStatus?.takeIf { !it.available }?.let { status ->
            item {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Python is unavailable", fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            status.unavailableReason ?: "BeeCode could not start Python.",
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }
        }

        if (queue.due.isNotEmpty()) {
            item { SectionHeader("Due now", queue.due.size) }
            items(queue.due, key = { it.problem.id.value }) { due ->
                DueProblemRow(due) { onOpen(due.problem.id) }
            }
        }
        if (queue.new.isNotEmpty()) {
            item { SectionHeader("New Problems", queue.new.size) }
            items(queue.new, key = { it.id.value }) { problem ->
                ProblemRow(problem, problem.topics.joinToString(" · ")) { onOpen(problem.id) }
            }
        }
        if (queue.isEmpty) {
            item {
                Column(
                    Modifier.fillMaxWidth().padding(vertical = 64.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("🍯", fontSize = 44.sp)
                    Spacer(Modifier.height(12.dp))
                    Text("Nothing due", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Come back when something is scheduled.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(8.dp))
        Text(
            "$count",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DueProblemRow(due: DueProblem, onClick: () -> Unit) {
    ProblemRow(
        problem = due.problem,
        subtitle = buildString {
            append("Reviewed ${due.schedule.reviewCount}×")
            if (due.schedule.lapseCount > 0) append(" · ${due.schedule.lapseCount} lapses")
        },
        onClick = onClick,
    )
}

@Composable
private fun ProblemRow(problem: ProblemDefinition, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(problem.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DifficultyBadge(problem.difficulty)
        }
    }
}

@Composable
private fun DifficultyBadge(difficulty: ProblemDifficulty) {
    val (label, color) = when (difficulty) {
        ProblemDifficulty.EASY -> "Easy" to Color(0xFF6BBF59)
        ProblemDifficulty.MEDIUM -> "Medium" to Color(0xFFE0A030)
        ProblemDifficulty.HARD -> "Hard" to Color(0xFFE05A4F)
    }
    Surface(color = color.copy(alpha = 0.18f), shape = RoundedCornerShape(6.dp)) {
        Text(
            label,
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/**
 * The Problem view: statement on the left, editor and results on the right.
 *
 * State is held here rather than in a view model because the desktop window owns a
 * single Problem at a time and Compose's own state is sufficient. Every decision
 * that matters — permitted ratings, whether an attempt counts as solved — still
 * comes from the shared service.
 */
@Composable
private fun ProblemPane(
    profile: BeeCodeProfile,
    problemId: ProblemId,
    onClose: () -> Unit,
) {
    val opened = remember(problemId) { profile.study.open(problemId) }
    if (opened == null) {
        LaunchedEffect(problemId) { onClose() }
        return
    }

    val scope = rememberCoroutineScope()
    var source by remember(problemId) { mutableStateOf(opened.draft.source) }
    var latestRun by remember(problemId) { mutableStateOf<ExecutionRun?>(null) }
    var isRunning by remember(problemId) { mutableStateOf(false) }
    var aided by remember(problemId) { mutableStateOf(opened.session.aided) }
    var explanation by remember(problemId) { mutableStateOf<String?>(null) }
    var message by remember(problemId) { mutableStateOf<String?>(null) }
    var finalized by remember(problemId) { mutableStateOf<FinalizeResult.Finalized?>(null) }
    var runJob by remember(problemId) { mutableStateOf<Job?>(null) }

    val permitted = latestRun?.let { ReviewRatingPolicy.permittedRatings(it, aided) } ?: emptySet()
    val suggested = latestRun?.let { ReviewRatingPolicy.defaultRating(it, aided) }

    fun persist() {
        profile.drafts.draft(problemId)?.let { profile.study.saveDraft(it.copy(source = source)) }
    }

    Column(Modifier.fillMaxSize()) {
        // Header
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { persist(); profile.study.abandon(problemId); onClose() }) {
                Text("← Back")
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    opened.problem.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    opened.problem.topics.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (aided) {
                // Say plainly that the ceiling dropped, rather than silently
                // disabling Good and Easy.
                Surface(
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(6.dp),
                ) {
                    Text(
                        "Answer revealed",
                        Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
            }
            DifficultyBadge(opened.problem.difficulty)
        }
        HorizontalDivider()

        Row(Modifier.fillMaxSize()) {
            // Left: the statement, and the explanation once revealed.
            Column(
                Modifier
                    .weight(0.42f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                MarkdownBlock(opened.problem.statementMarkdown)

                if (opened.problem.examples.isNotEmpty()) {
                    Text("Examples", style = MaterialTheme.typography.titleSmall)
                    opened.problem.examples.forEach { example ->
                        Column(
                            Modifier
                                .fillMaxWidth()
                                .background(
                                    MaterialTheme.colorScheme.surfaceVariant,
                                    RoundedCornerShape(6.dp),
                                )
                                .padding(10.dp),
                        ) {
                            Mono("Input:  ${example.input}")
                            Mono("Output: ${example.output}")
                            example.explanation?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }

                if (opened.problem.hasExplanation && explanation == null && finalized == null) {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text("Stuck?", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(4.dp))
                            Text(
                                "Reading the explanation means this review will not count as " +
                                    "solved, and the best available rating becomes Hard — a pass " +
                                    "after reading the answer is recognition, not recall.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(10.dp))
                            OutlinedButton(onClick = {
                                profile.study.reveal(problemId)?.let {
                                    explanation = it.explanationMarkdown
                                    aided = true
                                }
                            }) { Text("Show the explanation") }
                        }
                    }
                }

                explanation?.let {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text("Explanation", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            MarkdownBlock(it)
                        }
                    }
                }

                if (opened.history.isNotEmpty()) {
                    Card {
                        Column(Modifier.padding(14.dp)) {
                            Text("Your history", style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            opened.history.takeLast(10).reversed().forEach { review ->
                                Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                                    Text(
                                        review.localDate().toString(),
                                        style = MaterialTheme.typography.bodySmall,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier.width(100.dp),
                                    )
                                    Text(
                                        review.rating.name.lowercase()
                                            .replaceFirstChar { it.uppercase() },
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
            }

            VerticalDivider()

            // Right: the editor, results, and the finalize controls.
            Column(Modifier.weight(0.58f).fillMaxHeight().padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Your solution",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        profile.study.resetToStarter(problemId)?.let { source = it.source }
                    }) { Text("Reset") }
                }
                Spacer(Modifier.height(6.dp))

                CodeEditor(
                    source = source,
                    onSourceChange = { source = it; message = null },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                Spacer(Modifier.height(10.dp))

                message?.let {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                        ),
                    ) {
                        Text(
                            it,
                            Modifier.padding(10.dp),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isRunning) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(10.dp))
                        Text("Running…", style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            // Cancelling kills the child process tree on desktop,
                            // unlike Android where the interpreter is in-process.
                            runJob?.cancel()
                            isRunning = false
                        }) { Text("Stop") }
                    } else {
                        Button(
                            onClick = {
                                message = null
                                isRunning = true
                                runJob = scope.launch {
                                    try {
                                        when (val outcome = profile.study.run(problemId, source)) {
                                            is RunOutcome.Completed -> latestRun = outcome.run
                                            is RunOutcome.AlreadyFinalized -> message =
                                                "This review is already finished."
                                            else -> message =
                                                "BeeCode lost track of this attempt. Go back and " +
                                                    "open the Problem again."
                                        }
                                    } finally {
                                        isRunning = false
                                    }
                                }
                            },
                            enabled = finalized == null,
                        ) { Text(if (latestRun == null) "Run tests" else "Run again") }
                        Spacer(Modifier.weight(1f))
                    }
                }

                latestRun?.let { run ->
                    Spacer(Modifier.height(12.dp))
                    Box(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        ResultBlock(run)
                    }
                }

                finalized?.let { result ->
                    Spacer(Modifier.height(10.dp))
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Text(
                                if (result.review.countsAsSolved) "Solved" else "Review recorded",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                            )
                            result.schedule?.let { schedule ->
                                Text(
                                    "Next review in ${schedule.intervalDays} " +
                                        if (schedule.intervalDays == 1) "day" else "days",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Button(onClick = { persist(); onClose() }) { Text("Back to queue") }
                        }
                    }
                }

                // Only permitted ratings are offered. A disabled "Easy" after a
                // failure would invite an argument with the rule; omitting it
                // states the rule.
                if (latestRun != null && permitted.isNotEmpty() && finalized == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "How did that go?",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ReviewRating.entries.filter { it in permitted }.forEach { rating ->
                            val label = rating.name.lowercase().replaceFirstChar { it.uppercase() }
                            val onFinalize = {
                                persist()
                                when (
                                    val result =
                                        profile.study.finalize(problemId, latestRun!!.id, rating)
                                ) {
                                    is FinalizeResult.Finalized -> finalized = result
                                    is FinalizeResult.Rejected -> message = result.reason
                                    is FinalizeResult.NoSession -> message =
                                        "BeeCode lost track of this attempt."
                                }
                            }
                            if (rating == suggested) {
                                Button(onClick = onFinalize, modifier = Modifier.weight(1f)) {
                                    Text(label)
                                }
                            } else {
                                OutlinedButton(onClick = onFinalize, modifier = Modifier.weight(1f)) {
                                    Text(label)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultBlock(run: ExecutionRun) {
    val (headline, tint) = when (run.outcome) {
        ExecutionOutcome.PASSED -> "All tests passed" to Color(0xFF6BBF59)
        ExecutionOutcome.FAILED ->
            "${run.passedTestCount} of ${run.totalTestCount} tests passed" to Color(0xFFE0A030)
        ExecutionOutcome.SYNTAX_ERROR -> "Your code has a syntax error" to Color(0xFFE05A4F)
        ExecutionOutcome.RUNTIME_ERROR -> "Your code raised an error" to Color(0xFFE05A4F)
        ExecutionOutcome.TIMEOUT -> "Your code ran out of time" to Color(0xFFE0A030)
        ExecutionOutcome.CANCELLED -> "Run stopped" to Color(0xFF98917F)
        ExecutionOutcome.WORKER_FAILURE -> "BeeCode could not run your code" to Color(0xFFE05A4F)
    }

    Card {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(tint, RoundedCornerShape(4.dp)))
                Spacer(Modifier.width(8.dp))
                Text(
                    headline,
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

            if (run.testResults.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                run.testResults.forEach { TestRow(it) }
            }

            if (run.output.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(6.dp))
                Text(
                    if (run.outputTruncated) "Your output (truncated)" else "Your output",
                    style = MaterialTheme.typography.labelMedium,
                )
                Spacer(Modifier.height(4.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(8.dp),
                ) { Mono(run.output.trim()) }
            }
        }
    }
}

@Composable
private fun TestRow(result: TestCaseResult) {
    Column(Modifier.padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (result.passed) "✓" else "✗",
                color = if (result.passed) Color(0xFF6BBF59) else Color(0xFFE05A4F),
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                result.name + if (result.hidden) "  (hidden)" else "",
                style = MaterialTheme.typography.bodySmall,
            )
        }
        // A hidden test reports pass or fail but withholds its values, so the
        // Problem cannot be solved by reading the assertions.
        if (!result.passed && !result.hidden) {
            result.message?.let { message ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 2.dp, start = 20.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(6.dp),
                ) { Mono(message) }
            }
        }
    }
}

@Composable
private fun ProgressPane(profile: BeeCodeProfile, refreshToken: Int) {
    val stats = remember(refreshToken) { profile.statistics() }
    val achievements = remember(refreshToken) { profile.achievements() }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        if (!stats.hasActivity) {
            Text(
                "Solve a Problem and your progress will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatTile("Solved", "${stats.distinctProblemsSolved}", Modifier.weight(1f))
                StatTile("Reviews", "${stats.totalReviews}", Modifier.weight(1f))
                StatTile("Streak", "${stats.currentStreakDays}d", Modifier.weight(1f))
                StatTile("Due now", "${stats.dueNow}", Modifier.weight(1f))
                StatTile(
                    "Accuracy",
                    stats.accuracy?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                    Modifier.weight(1f),
                )
            }

            Card {
                Column(Modifier.padding(16.dp)) {
                    Text("Last 30 days", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(12.dp))
                    val peak = stats.reviewsPerDay.maxOfOrNull { it.reviews }?.coerceAtLeast(1) ?: 1
                    Row(
                        Modifier.fillMaxWidth().height(90.dp),
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        // Empty days are drawn, not skipped: a streak app that hides
                        // gaps is not telling the learner the truth.
                        stats.reviewsPerDay.forEach { day ->
                            val fraction = day.reviews.toFloat() / peak
                            Box(
                                Modifier
                                    .weight(1f)
                                    .height((4 + fraction * 72).dp)
                                    .background(
                                        if (day.reviews == 0) {
                                            MaterialTheme.colorScheme.surfaceVariant
                                        } else {
                                            MaterialTheme.colorScheme.primary
                                        },
                                        RoundedCornerShape(2.dp),
                                    ),
                            )
                        }
                    }
                }
            }

            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("By difficulty", style = MaterialTheme.typography.titleSmall)
                    ProblemDifficulty.entries.forEach { difficulty ->
                        val progress = stats.byDifficulty[difficulty] ?: return@forEach
                        if (progress.total == 0) return@forEach
                        Column {
                            Row {
                                Text(
                                    difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    "${progress.solved}/${progress.total}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.height(4.dp))
                            LinearProgressIndicator(
                                progress = { progress.fraction },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }

        Text(
            "Achievements",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        achievements.states.forEach { AchievementRow(it) }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.padding(14.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AchievementRow(state: AchievementState) {
    Card {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (state.earned) "🏆" else "○", fontSize = 22.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    state.definition.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (state.earned) FontWeight.Bold else FontWeight.Normal,
                )
                Text(
                    state.definition.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (!state.earned) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth(0.5f),
                    )
                }
            }
            Text(
                state.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsPane(profile: BeeCodeProfile, runnerStatus: RunnerStatus?) {
    var limit by remember { mutableStateOf(profile.settings.dailyReviewLimit()) }
    var transferMessage by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Daily review limit", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Caps how many due Problems the queue offers, so a long backlog does not " +
                        "become a reason to skip a day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 5, 10, 20, 50).forEach { candidate ->
                        val select = {
                            profile.settings.setDailyReviewLimit(
                                candidate,
                                kotlinx.datetime.Clock.System.now(),
                            )
                            limit = candidate
                        }
                        if (limit == candidate) {
                            Button(onClick = select) { Text(candidate?.toString() ?: "None") }
                        } else {
                            OutlinedButton(onClick = select) {
                                Text(candidate?.toString() ?: "None")
                            }
                        }
                    }
                }
            }
        }

        // The honest capability statement belongs in the UI, not only in a comment.
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Python execution", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                runnerStatus?.let { status ->
                    Labelled(
                        "Runtime",
                        status.pythonVersion?.let { "Python $it" } ?: "unavailable",
                    )
                    Labelled("Runner", status.runnerId)
                    Labelled(
                        "Isolation",
                        when (status.capability) {
                            RunnerCapability.IN_PROCESS -> "In this app's process"
                            RunnerCapability.SEPARATE_PROCESS -> "Separate process"
                            RunnerCapability.ISOLATED_PROCESS -> "Isolated process"
                        },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "Your code runs in a separate process that BeeCode can stop, in a fresh " +
                        "temporary directory with a cleaned environment. That reliably stops " +
                        "runaway loops and runaway output, but it is not a security sandbox: " +
                        "the process runs with your own user account's privileges. BeeCode " +
                        "does not claim otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Backup", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Export everything to one file you keep: your solutions, review " +
                        "history, schedule, and settings. Restoring merges into this " +
                        "profile rather than replacing it, so importing twice is safe.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = {
                        transferMessage = ProfileFiles.exportTo(profile)
                    }) { Text("Export…") }
                    OutlinedButton(onClick = {
                        transferMessage = ProfileFiles.restoreFrom(profile)
                    }) { Text("Restore…") }
                }
                transferMessage?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Profile", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Labelled("Location", profileDirectory().absolutePath)
                Labelled("Problems", "${profile.catalogue.size}")
                Spacer(Modifier.height(8.dp))
                Text(
                    "Everything is stored on this machine. There is no account, no server, " +
                        "and no network access. Your solutions are in this database file — " +
                        "copy it to back up, and treat it as sensitive because it contains " +
                        "your code.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Labelled(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(88.dp),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}

@Composable
internal fun Mono(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodySmall,
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        lineHeight = 17.sp,
    )
}

/**
 * Renders the small Markdown subset the Problem statements use.
 *
 * Headings, fenced code, inline code, and bullets. A full Markdown dependency would
 * be poor value for content BeeCode itself authors and validates.
 */
@Composable
internal fun MarkdownBlock(markdown: String) {
    Column {
        var inCode = false
        val codeLines = mutableListOf<String>()

        @Composable
        fun flush() {
            if (codeLines.isNotEmpty()) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(10.dp),
                ) { Mono(codeLines.joinToString("\n")) }
                codeLines.clear()
            }
        }

        markdown.lines().forEach { raw ->
            if (raw.trimStart().startsWith("```")) {
                if (inCode) flush()
                inCode = !inCode
                return@forEach
            }
            if (inCode) {
                codeLines += raw
                return@forEach
            }
            val line = raw.trim()
            when {
                line.isEmpty() -> Spacer(Modifier.height(6.dp))
                line.startsWith("## ") -> Text(
                    line.removePrefix("## "),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                line.startsWith("# ") -> Text(
                    line.removePrefix("# "),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                line.startsWith("- ") -> Row {
                    Text("•  ", style = MaterialTheme.typography.bodyMedium)
                    Text(strip(line.removePrefix("- ")), style = MaterialTheme.typography.bodyMedium)
                }
                else -> Text(strip(line), style = MaterialTheme.typography.bodyMedium)
            }
        }
        flush()
    }
}

/**
 * Remove inline emphasis and code markers.
 *
 * They would otherwise render literally, which reads worse than plain text. Styling
 * each span is not worth the complexity for this content.
 */
private fun strip(text: String): String =
    text.replace("`", "").replace("**", "").replace("_", "")
