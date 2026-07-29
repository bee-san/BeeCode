package dev.bee.beecode.android.ui

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
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bee.beecode.app.AchievementState
import dev.bee.beecode.app.RestoreResult
import dev.bee.beecode.app.DueProblem
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.python.RunnerCapability
import kotlin.math.roundToInt

/**
 * The root of the Android UI.
 *
 * Deliberately flat: three top-level destinations and one full-screen Problem view.
 * A study app earns its keep by getting the learner into a Problem quickly, so
 * there is no nested navigation to get lost in.
 */
@Composable
fun BeeCodeApp(viewModel: StudyViewModel) {
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val problem by viewModel.problem.collectAsStateWithLifecycle()

    // The Problem view takes the whole screen, navigation bar included. While
    // solving, the learner needs every pixel for code, and an accidental tab tap
    // would abandon their attempt.
    val active = problem
    if (screen is Screen.Problem && active != null) {
        ProblemScreen(
            state = active,
            onSourceChange = viewModel::editSource,
            onRun = viewModel::run,
            onCancelRun = viewModel::cancelRun,
            onReveal = viewModel::reveal,
            onFinalize = viewModel::finalize,
            onResetToStarter = viewModel::resetToStarter,
            onClose = viewModel::closeProblem,
        )
        return
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = screen is Screen.Queue,
                    onClick = viewModel::showQueue,
                    icon = { Text("🐝", fontSize = 20.sp) },
                    label = { Text("Study") },
                )
                NavigationBarItem(
                    selected = screen is Screen.Statistics,
                    onClick = viewModel::showStatistics,
                    icon = { Text("📊", fontSize = 20.sp) },
                    label = { Text("Progress") },
                )
                NavigationBarItem(
                    selected = screen is Screen.Settings,
                    onClick = viewModel::showSettings,
                    icon = { Text("⚙", fontSize = 20.sp) },
                    label = { Text("Settings") },
                )
            }
        },
    ) { padding ->
        Box(Modifier.padding(padding)) {
            when (screen) {
                is Screen.Statistics -> StatisticsScreen(viewModel)
                is Screen.Settings -> SettingsScreen(viewModel)
                else -> QueueScreen(viewModel)
            }
        }
    }
}

@Composable
private fun QueueScreen(viewModel: StudyViewModel) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val runnerStatus by viewModel.runnerStatus.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column {
                Text(
                    "BeeCode",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                statistics?.let { stats ->
                    Text(
                        buildString {
                            append("${stats.distinctProblemsSolved} of ${stats.totalProblems} solved")
                            if (stats.currentStreakDays > 0) {
                                append(" · ${stats.currentStreakDays} day streak")
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // Surface a broken runtime here rather than letting the learner discover it
        // when their first review mysteriously fails.
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

        val current = queue
        if (current == null) {
            item { LoadingRow() }
        } else {
            if (current.due.isNotEmpty()) {
                item { SectionHeader("Due now", current.due.size) }
                items(current.due, key = { it.problem.id.value }) { due ->
                    DueProblemCard(due) { viewModel.openProblem(due.problem.id) }
                }
            }
            if (current.new.isNotEmpty()) {
                item { SectionHeader("New Problems", current.new.size) }
                items(current.new, key = { it.id.value }) { problem ->
                    NewProblemCard(problem) { viewModel.openProblem(problem.id) }
                }
            }
            if (current.isEmpty) {
                item { AllCaughtUp() }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, count: Int) {
    Row(
        Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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
private fun DueProblemCard(due: DueProblem, onClick: () -> Unit) {
    ProblemCard(
        problem = due.problem,
        // "Reviewed 5 times" means something to a learner; a stability number is
        // FSRS's business rather than theirs.
        subtitle = buildString {
            append("Reviewed ${due.schedule.reviewCount}×")
            if (due.schedule.lapseCount > 0) append(" · ${due.schedule.lapseCount} lapses")
        },
        onClick = onClick,
    )
}

@Composable
private fun NewProblemCard(problem: ProblemDefinition, onClick: () -> Unit) {
    ProblemCard(problem = problem, subtitle = problem.topics.joinToString(" · "), onClick = onClick)
}

@Composable
private fun ProblemCard(problem: ProblemDefinition, subtitle: String, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    problem.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
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
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun AllCaughtUp() {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("🍯", fontSize = 48.sp)
        Spacer(Modifier.height(12.dp))
        Text("Nothing due", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "You have reviewed everything that was scheduled. Come back when something is due.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LoadingRow() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 32.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp))
    }
}

@Composable
private fun StatisticsScreen(viewModel: StudyViewModel) {
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val achievements by viewModel.achievements.collectAsStateWithLifecycle()

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        val stats = statistics
        if (stats == null) {
            LoadingRow()
        } else if (!stats.hasActivity) {
            Text(
                "Solve a Problem and your progress will appear here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            StatGrid(stats)
            ActivityChart(stats)
            DifficultyBreakdown(stats)
        }

        achievements?.let { projection ->
            Text(
                "Achievements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            projection.states.forEach { AchievementRow(it) }
        }
    }
}

@Composable
private fun StatGrid(stats: StudyStatistics) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Solved", "${stats.distinctProblemsSolved}", Modifier.weight(1f))
            StatTile("Reviews", "${stats.totalReviews}", Modifier.weight(1f))
            StatTile("Streak", "${stats.currentStreakDays}d", Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            StatTile("Due now", "${stats.dueNow}", Modifier.weight(1f))
            StatTile(
                "Accuracy",
                stats.accuracy?.let { "${(it * 100).roundToInt()}%" } ?: "—",
                Modifier.weight(1f),
            )
            StatTile("Today", "${stats.reviewsToday}", Modifier.weight(1f))
        }
    }
}

@Composable
private fun StatTile(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
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

/**
 * A fourteen-day activity chart.
 *
 * Empty days are drawn, not skipped. Compressing them would hide a gap, and a
 * streak app that hides gaps is not telling the learner the truth.
 */
@Composable
private fun ActivityChart(stats: StudyStatistics) {
    val recent = stats.reviewsPerDay.takeLast(14)
    val peak = recent.maxOfOrNull { it.reviews }?.coerceAtLeast(1) ?: 1

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Last 14 days", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                recent.forEach { day ->
                    val fraction = day.reviews.toFloat() / peak
                    Box(
                        Modifier
                            .weight(1f)
                            // A floor of 4dp so a zero day reads as a zero day
                            // rather than as missing data.
                            .height((4 + fraction * 56).dp)
                            .background(
                                if (day.reviews == 0) {
                                    MaterialTheme.colorScheme.surfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                RoundedCornerShape(3.dp),
                            )
                            .semantics {
                                contentDescription = "${day.date}: ${day.reviews} reviews"
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun DifficultyBreakdown(stats: StudyStatistics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("By difficulty", style = MaterialTheme.typography.titleSmall)
            ProblemDifficulty.entries.forEach { difficulty ->
                val progress = stats.byDifficulty[difficulty] ?: return@forEach
                if (progress.total == 0) return@forEach
                Column {
                    Row(Modifier.fillMaxWidth()) {
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

@Composable
private fun AchievementRow(state: AchievementState) {
    Card {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
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
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                state.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: StudyViewModel) {
    val runnerStatus by viewModel.runnerStatus.collectAsStateWithLifecycle()
    var transferMessage by remember { mutableStateOf<String?>(null) }
    // Captured once here: LocalContext is a composable read and cannot be called
    // from inside the picker callbacks, which run outside composition.
    val contentResolver = LocalContext.current.contentResolver

    // Document pickers rather than direct file paths: Android's storage model means
    // the learner chooses the location, and BeeCode never needs a storage permission
    // as a result. That is why the manifest declares none.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        transferMessage = if (uri == null) {
            null
        } else {
            runCatching {
                contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(viewModel.exportProfile().encodeToByteArray())
                } ?: error("could not open the chosen file for writing")
                "Exported. This file contains your solutions, so keep it somewhere private."
            }.getOrElse { "Export failed: ${it.message}" }
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        transferMessage = if (uri == null) {
            null
        } else {
            runCatching {
                val payload = contentResolver.openInputStream(uri)
                    ?.use { it.readBytes().decodeToString() }
                    ?: error("could not open the chosen file")
                when (val result = viewModel.restoreProfile(payload)) {
                    is RestoreResult.Restored -> result.describe()
                    is RestoreResult.Failed -> result.reason
                }
            }.getOrElse { "Restore failed: ${it.message}" }
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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
                    "Caps how many due Problems the queue offers, so a long backlog does " +
                        "not become a reason to skip a day.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf<Int?>(null, 5, 10, 20).forEach { limit ->
                        if (viewModel.dailyLimit() == limit) {
                            Button(onClick = { viewModel.setDailyLimit(limit) }) {
                                Text(limit?.toString() ?: "None")
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.setDailyLimit(limit) }) {
                                Text(limit?.toString() ?: "None")
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
                    LabelledValue(
                        "Runtime",
                        status.pythonVersion?.let { "Python $it" } ?: "unavailable",
                    )
                    LabelledValue("Runner", status.runnerId)
                    LabelledValue(
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
                    "On Android your code runs inside BeeCode's own process. BeeCode stops " +
                        "waiting for code that never finishes, but it cannot force Python to " +
                        "stop — so restart the app if running code stops working. This is not " +
                        "a security sandbox, and BeeCode does not claim it is. The app requests " +
                        "no permissions at all, so your code has no network access and no file " +
                        "access beyond BeeCode's own storage.",
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
                    Button(onClick = { exportLauncher.launch(viewModel.suggestedExportName()) }) {
                        Text("Export")
                    }
                    OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }) {
                        Text("Restore")
                    }
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
                Text("About", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "BeeCode schedules Problems with FSRS. Everything is stored on this " +
                        "device: there is no account, no server, and no network access.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
internal fun LabelledValue(label: String, value: String) {
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
