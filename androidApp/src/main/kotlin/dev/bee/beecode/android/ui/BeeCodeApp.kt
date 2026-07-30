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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import dev.bee.beecode.android.DocumentSyncStore
import kotlinx.coroutines.launch
import dev.bee.beecode.app.RestoreResult
import dev.bee.beecode.app.SyncReport
import dev.bee.beecode.app.WebDavSyncStore
import dev.bee.beecode.app.DueTopic
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.app.TopicAbility
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.formatIntervalDays
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
            if (current.dueTopics.isNotEmpty()) {
                // "Techniques", not "Problems": what fell due is dynamic programming,
                // and the Problem underneath is the exercise that rehearses it.
                item { SectionHeader("Techniques to review", current.dueTopics.size) }
                items(current.dueTopics, key = { it.topic }) { due ->
                    DueTopicCard(due) { viewModel.openProblem(due.problem.id) }
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

/**
 * A technique that has come round, and the Problem chosen to rehearse it.
 *
 * The technique is the headline and the Problem is the subtitle, which is the whole
 * point of the change: the learner is told "practise dynamic programming" and then
 * given something to practise it with, rather than being handed a Problem and left to
 * infer why.
 *
 * "Memory lasts about N days" is [formatIntervalDays] over the topic's own FSRS
 * interval — the algorithm's own output rather than a figure invented for the UI.
 *
 * The difficulty badge sits on the *Problem's* line rather than beside the technique
 * name, where it first went. A technique has no difficulty, and a badge next to
 * "Arrays" reads as though it did.
 */
@Composable
private fun DueTopicCard(due: DueTopic, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Text(
                due.displayName,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Memory lasts about ${formatIntervalDays(due.schedule.intervalDays)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    // Naming the Problem matters here: the learner needs to know they are
                    // revisiting something, and which something.
                    "Practise with ${due.problem.title} · " +
                        "${due.attemptedMemberProblems} of ${due.memberProblems} practised",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                DifficultyBadge(due.problem.difficulty)
            }
        }
    }
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
    val topicMastery by viewModel.topicMastery.collectAsStateWithLifecycle()

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

        topicMastery?.practised?.takeIf { it.isNotEmpty() }?.let { practised ->
            Text(
                "Techniques",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
            )
            // The evidence base, stated once, above the numbers rather than in a
            // footnote. What is measured is recall of Problems already solved, and a
            // learner who reads these as raw problem-solving ability will trust them
            // for a decision they cannot support.
            Text(
                "How well you recall Problems you have already solved in each technique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            practised.forEach { TopicAbilityRow(it) }
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

/**
 * One technique's figures.
 *
 * Two numbers side by side and never blended: how long the memory lasts, and how much
 * of the technique has been practised. A null recall rate says "not enough practice
 * yet" rather than 0%, because the difference between "weak at this" and "has barely
 * done this" is the one the learner most needs and the one a fake zero destroys.
 */
@Composable
private fun TopicAbilityRow(ability: TopicAbility) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ability.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    ability.recallRate?.let { "${(it * 100).roundToInt()}% recall" }
                        ?: "Not enough practice yet",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                buildString {
                    ability.intervalDays?.let {
                        append("Memory lasts about ${formatIntervalDays(it)} · ")
                    }
                    append("${ability.solvedMemberProblems} of ${ability.memberProblems} solved")
                    append(" · ${ability.reviews} ${if (ability.reviews == 1) "review" else "reviews"}")
                    if (ability.lapses > 0) append(", ${ability.lapses} forgotten")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
    var syncTarget by remember { mutableStateOf(viewModel.syncTarget()) }
    var webDavUrl by remember { mutableStateOf(viewModel.webDavUrl() ?: "") }
    var webDavUser by remember { mutableStateOf(viewModel.webDavUsername() ?: "") }
    var webDavPassword by remember { mutableStateOf(viewModel.webDavPassword() ?: "") }
    var boardJoined by remember { mutableStateOf(viewModel.leaderboardJoined()) }
    var boardStatus by remember { mutableStateOf(viewModel.leaderboardStatus()) }
    var boardMessage by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()
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

    // CreateDocument, so the learner names the shared file and chooses its folder — and
    // BeeCode still needs no storage permission. takePersistableUriPermission is what
    // makes the grant survive a restart; without it sync would work once and then fail.
    val syncPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            runCatching {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            viewModel.setSyncTarget(uri.toString())
            syncTarget = uri.toString()
            syncMessage = null
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
                Text("Sync between devices", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Optional. Pick one file in a folder something already syncs — Drive, " +
                        "Dropbox, Syncthing — and choose the same file on your other " +
                        "devices. There is no account and no BeeCode server; the file is " +
                        "yours, and BeeCode still asks for no storage permission.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "Reviews from every device are combined, and the most recent edit of a " +
                        "draft wins. Like an export, the file contains your solutions, so " +
                        "choose somewhere private.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    if (syncTarget == null) "Not set — sync is off" else "Sync file chosen",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { syncPicker.launch(SYNC_FILE_NAME) }) {
                        Text(if (syncTarget == null) "Choose a file" else "Change")
                    }
                    Button(
                        // Disabled while a sync is in flight, so a second tap cannot start
                        // an overlapping sync against the same file.
                        enabled = syncTarget != null && !syncing,
                        onClick = {
                            val target = syncTarget ?: return@Button
                            syncing = true
                            syncMessage = null
                            settingsScope.launch {
                                val store = DocumentSyncStore(
                                    contentResolver = contentResolver,
                                    uri = android.net.Uri.parse(target),
                                )
                                syncMessage = viewModel.sync(store).describe()
                                syncing = false
                            }
                        },
                    ) { Text(if (syncing) "Syncing…" else "Sync now") }
                    if (syncTarget != null) {
                        TextButton(onClick = {
                            viewModel.setSyncTarget(null)
                            syncTarget = null
                            syncMessage = "Sync turned off. The file was left where it is."
                        }) { Text("Turn off") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp))

                Text("Or a WebDAV server", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Stronger if you have one — Nextcloud, ownCloud, Synology. The server " +
                        "checks for a conflicting write itself, so two devices syncing at " +
                        "the same moment cannot overwrite each other; a shared file relies " +
                        "on the two syncs not overlapping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The address must point at a file, not a folder. https is required: the " +
                        "password is sent with every request, and so are your solutions.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = webDavUrl,
                    onValueChange = { webDavUrl = it },
                    label = { Text("WebDAV file URL") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = webDavUser,
                    onValueChange = { webDavUser = it },
                    label = { Text("Username") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = webDavPassword,
                    onValueChange = { webDavPassword = it },
                    label = { Text("Password") },
                    singleLine = true,
                    // Masked so it is not readable over a shoulder. That does not change
                    // where it is stored, which is why the note below says so plainly.
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "The password is encrypted with a key held in this device's keystore, so " +
                        "a copy of the app's data taken elsewhere cannot read it, and it is " +
                        "never included in an export or uploaded with your study data.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = webDavUrl.isNotBlank() && !syncing,
                    onClick = {
                        syncing = true
                        syncMessage = null
                        viewModel.setWebDav(webDavUrl, webDavUser, webDavPassword)
                        settingsScope.launch {
                            val built = WebDavSyncStore.create(
                                url = webDavUrl,
                                username = webDavUser.ifBlank { null },
                                password = webDavPassword.ifBlank { null },
                            )
                            syncMessage = built.fold(
                                onSuccess = { viewModel.sync(it).describe() },
                                // A rejected configuration is a message, not a crash: the
                                // learner needs to know it was the https rule or the folder
                                // address rather than watching a sync fail obscurely.
                                onFailure = { it.message ?: "That WebDAV address cannot be used." },
                            )
                            syncing = false
                        }
                    },
                ) { Text(if (syncing) "Syncing…" else "Sync with WebDAV") }

                syncMessage?.let { message ->
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
                Text("Leaderboard", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Optional, off by default, and it needs a server that does not exist " +
                        "yet — so joining now only records what this device would share " +
                        "once one does. Nothing leaves your phone.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "A board sees counts and streaks: which day you solved something, and " +
                        "whether it was an unaided pass. Never your code, your test output, " +
                        "or your schedule. Activity from before you join is never shared.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))

                if (!boardJoined) {
                    Text("Not joined", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        viewModel.joinLeaderboard()
                        boardJoined = true
                        boardStatus = viewModel.leaderboardStatus()
                        boardMessage = "Joined. Solves from now on will be shared once a " +
                            "server exists; everything before now stays private."
                    }) { Text("Join a Leaderboard") }
                } else {
                    Text(
                        "Waiting to send: ${boardStatus.pending}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text("Sent: ${boardStatus.acknowledged}", style = MaterialTheme.typography.bodyMedium)
                    if (boardStatus.parked > 0) {
                        Text("Stuck: ${boardStatus.parked}", style = MaterialTheme.typography.bodyMedium)
                    }
                    if (boardStatus.rejected > 0) {
                        // Separate from "stuck" because a refusal is final: a learner should
                        // not be invited to retry a decision the server already made.
                        Text(
                            "Refused by the server: ${boardStatus.rejected}",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val added = viewModel.refreshLeaderboard()
                            boardStatus = viewModel.leaderboardStatus()
                            boardMessage = if (added > 0) {
                                "Queued $added new ${if (added == 1) "solve" else "solves"}."
                            } else {
                                "Nothing new to queue."
                            }
                        }) { Text("Check activity") }

                        if (boardStatus.parked > 0) {
                            OutlinedButton(onClick = {
                                val revived = viewModel.retryStuckLeaderboardItems()
                                boardStatus = viewModel.leaderboardStatus()
                                boardMessage = "Will try $revived again."
                            }) { Text("Retry stuck") }
                        }

                        TextButton(onClick = {
                            viewModel.leaveLeaderboard()
                            boardJoined = false
                            boardStatus = viewModel.leaderboardStatus()
                            boardMessage = "Left. Your reviews, schedule, and achievements " +
                                "are untouched — a board is a view of your study, never " +
                                "where it lives."
                        }) { Text("Leave") }
                    }
                }

                boardMessage?.let { message ->
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
                        "device: there is no account and no server, and BeeCode holds no " +
                        "network permission — sync writes to a file you choose, through " +
                        "whatever already syncs it.",
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

/**
 * Turn a sync report into something worth reading.
 *
 * Specific about *what moved*, on purpose: "Synced" tells a learner nothing, and the
 * difference between "already up to date" and "received 12 reviews" is the difference
 * between trusting sync and wondering whether it ran. Kept in step with the desktop
 * client's wording so the two clients describe the same event the same way.
 */
private fun SyncReport.describe(): String = when (this) {
    is SyncReport.Completed -> {
        val received = merge?.let { merge ->
            buildList {
                if (merge.reviewsFromRemote > 0) add("${merge.reviewsFromRemote} reviews")
                if (merge.draftsFromRemote > 0) add("${merge.draftsFromRemote} drafts")
                if (merge.settingsFromRemote > 0) add("${merge.settingsFromRemote} settings")
            }
        }.orEmpty()
        when {
            received.isNotEmpty() && pushed ->
                "Received ${received.joinToString(", ")}, and sent this device's changes."
            received.isNotEmpty() -> "Received ${received.joinToString(", ")}."
            pushed -> "Sent this device's changes."
            else -> "Already up to date."
        }
    }
    // Nothing was lost: whatever was pulled has already been applied on this device.
    is SyncReport.Conflicted ->
        "Another device kept updating the file, so this device's changes were not sent. " +
            "Everything it had was received, and the next sync will try again."
    is SyncReport.Failed -> "Could not sync: $reason"
}

/**
 * The suggested name for the shared file.
 *
 * Not dated, unlike an export's name: this is one file every device keeps writing to, so
 * a name that changed daily would silently start a new sync history.
 */
private const val SYNC_FILE_NAME = "beecode-sync.json"
