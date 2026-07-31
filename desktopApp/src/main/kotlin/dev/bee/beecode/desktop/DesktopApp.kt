package dev.bee.beecode.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Hive
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
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
import java.io.File
import kotlinx.datetime.Clock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.app.AchievementState
import dev.bee.beecode.app.ActivityBucket
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.IntervalRange
import dev.bee.beecode.app.LeaderboardService
import dev.bee.beecode.app.DueTopic
import dev.bee.beecode.app.TopicAbility
import dev.bee.beecode.app.TopicMasteryProjection
import dev.bee.beecode.app.FinalizeResult
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.app.RunnerStatus
import dev.bee.beecode.app.StudyQueue
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.app.StatisticsPeriod
import dev.bee.beecode.app.TopicProgress
import dev.bee.beecode.design.RunOutcomePresentation
import dev.bee.beecode.design.ScreenReaderLabels
import dev.bee.beecode.design.tint
import dev.bee.beecode.design.Markdown
import dev.bee.beecode.design.ThemeChoice
import dev.bee.beecode.design.ThemeFamily
import dev.bee.beecode.design.setThemeChoice
import dev.bee.beecode.design.setThemeFamily
import dev.bee.beecode.domain.DueDescription
import dev.bee.beecode.domain.DueUrgency
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewRatingPolicy
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.domain.describeDue
import dev.bee.beecode.domain.formatIntervalDays
import dev.bee.beecode.python.RunnerCapability
import kotlinx.datetime.Instant
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Identifies the scrolling study queue so a test can scroll it to a given Problem.
 *
 * The catalogue is expected to keep growing, so any Problem may be below the fold. A
 * test that reaches one by name needs the scrollable container, and finding it by tag
 * is stabler than matching on a layout property.
 */
internal const val QUEUE_LIST_TAG = "queue-list"

/**
 * The desktop UI.
 *
 * The same shared study service Android drives, presented for a large screen: a
 * navigation rail instead of a bottom bar, and the Problem view as two panes so the
 * statement stays visible while writing code. On a phone that had to be a scroll;
 * here it does not, and reading the Problem while typing is most of the work.
 */
@Composable
fun DesktopApp(
    profile: BeeCodeProfile,
    theme: ThemeChoice = ThemeChoice.SYSTEM,
    onThemeChange: (ThemeChoice) -> Unit = {},
    family: ThemeFamily = ThemeFamily.Default,
    onFamilyChange: (ThemeFamily) -> Unit = {},
) {
    var screen by remember { mutableStateOf<DesktopScreen>(DesktopScreen.Queue) }
    var openProblem by remember { mutableStateOf<ProblemId?>(null) }
    var refreshToken by remember { mutableStateOf(0) }
    var runnerStatus by remember { mutableStateOf<RunnerStatus?>(null) }
    var showProgress by remember { mutableStateOf(profile.settings.showProgress()) }
    var showMotivation by remember {
        mutableStateOf(profile.settings.showStreaksAndAchievements())
    }

    LaunchedEffect(Unit) { runnerStatus = profile.study.runnerStatus() }

    val refreshVisibility = {
        showProgress = profile.settings.showProgress()
        showMotivation = profile.settings.showStreaksAndAchievements()
        if (!showProgress && screen is DesktopScreen.Progress) {
            screen = DesktopScreen.Queue
        }
        refreshToken++
        Unit
    }

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
        // Vector icons rather than emoji. An emoji renders in whatever font the OS
        // happens to substitute — full-colour and off-baseline on one machine, a
        // monochrome box on another — so it cannot be aligned or tinted with the
        // selected state.
        //
        // The three destination icons come from `material-icons-core` so they are the
        // same ones Android's bottom bar uses; a learner with both clients should not
        // have to learn two symbols for "Study". The rail's Hive mark and the due badges
        // below stay on `-extended`, which desktop can afford: it ships as a bundle with
        // its own JVM, where those megabytes are noise, and on a phone they measured
        // +3.9 MB of APK for four icons. See androidApp/build.gradle.kts.
        NavigationRail(
            header = {
                // The one place the brand mark belongs: at the top of the rail, in the
                // primary colour, rather than as a nav item competing with the pages.
                Icon(
                    Icons.Outlined.Hive,
                    // Decorative by construction: a brand mark states nothing a learner
                    // needs, and announcing "BeeCode" at the top of the rail would be the
                    // first thing read on every visit to the window.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 12.dp, bottom = 4.dp).size(28.dp),
                )
            },
        ) {
            // All three descriptions are null on purpose: each item's own `label` names
            // the destination, and describing the icon too makes a reader announce
            // "Study, Study" on every item.
            NavigationRailItem(
                selected = screen is DesktopScreen.Queue,
                onClick = { screen = DesktopScreen.Queue; refreshToken++ },
                icon = { Icon(Icons.Outlined.List, contentDescription = null) },
                label = { Text("Study") },
            )
            if (showProgress) {
                NavigationRailItem(
                    selected = screen is DesktopScreen.Progress,
                    onClick = { screen = DesktopScreen.Progress; refreshToken++ },
                    icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                    label = { Text("Progress") },
                )
            }
            NavigationRailItem(
                selected = screen is DesktopScreen.Settings,
                onClick = { screen = DesktopScreen.Settings },
                icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
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
                    showMotivation = showMotivation,
                    onOpen = { openProblem = it },
                )
                is DesktopScreen.Progress -> ProgressPane(
                    profile = profile,
                    refreshToken = refreshToken,
                    showMotivation = showMotivation,
                )
                is DesktopScreen.Settings -> SettingsPane(
                    profile = profile,
                    runnerStatus = runnerStatus,
                    theme = theme,
                    onThemeChange = onThemeChange,
                    family = family,
                    onFamilyChange = onFamilyChange,
                    onVisibilityChanged = refreshVisibility,
                )
            }
        }
    }
}

/**
 * The ceiling on the run-results block, past which it scrolls.
 *
 * Chosen by measuring, not by taste. `EditorHeightTest` reports the editor keeping
 * 331px of 519px on a pass and 331px on a fail at this value; at 190dp the failing
 * case dropped to 237px, and at 220dp to 207px, which is barely better than the
 * `weight(1f)` collapse this replaced. Above roughly 150dp the block simply takes
 * whatever it is given, so a larger ceiling buys nothing but a smaller editor.
 */
private val RESULT_BLOCK_MAX_HEIGHT = 150.dp

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
    showMotivation: Boolean,
    onOpen: (ProblemId) -> Unit,
) {
    // Re-read whenever the token changes, which happens after a review is
    // finalized. Deriving from the token rather than caching means the queue can
    // never show a Problem that is no longer due.
    val queue: StudyQueue = remember(refreshToken) { profile.study.queue() }
    val statistics: StudyStatistics = remember(refreshToken) { profile.statistics() }
    // Read once per queue read, not per row: every row must describe its due time
    // against the same instant, or two Problems due at the same moment could render
    // with different labels.
    val now = remember(refreshToken) { Clock.System.now() }

    LazyColumn(
        // Tagged so a test can scroll the queue to a specific Problem. Without it a
        // test must assert against whatever happens to be above the fold, which turns
        // adding a Problem into a UI failure — the same reason the solved count is
        // derived from the catalogue rather than written as a literal.
        Modifier.fillMaxSize().testTag(QUEUE_LIST_TAG),
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
                        if (showMotivation && statistics.currentStreakDays > 0) {
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

        if (queue.dueTopics.isNotEmpty()) {
            // "Techniques", not "Problems": what fell due is dynamic programming, and
            // the Problem underneath is the exercise that rehearses it.
            item { SectionHeader("Techniques to review", queue.dueTopics.size) }
            items(queue.dueTopics, key = { it.topic }) { due ->
                DueTopicRow(due, now) { onOpen(due.problem.id) }
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
                        // Name the next thing the scheduler will do, rather than
                        // "come back when something is scheduled" — which reads as
                        // though nothing is, when in fact everything is.
                        if (statistics.dueTomorrow > 0) {
                            "${statistics.dueTomorrow} " +
                                "${if (statistics.dueTomorrow == 1) "Problem" else "Problems"} " +
                                "come back tomorrow."
                        } else {
                            "Everything you have learned is scheduled further out. " +
                                "That is the algorithm working."
                        },
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

/**
 * A technique that has come round, and the Problem chosen to rehearse it.
 *
 * The technique is the headline and the Problem the subtitle, which is the point of the
 * change: the learner is told "practise dynamic programming" and then given something
 * to practise it with, rather than handed a Problem and left to infer why. Mirrors
 * Android's `DueTopicCard` — the same words in both clients, because a learner using
 * both should not have to learn two vocabularies.
 *
 * The difficulty badge sits on the *Problem's* line rather than beside the technique
 * name, where it first went. A technique has no difficulty, and a badge next to
 * "Arrays" reads as though it did — a screenshot caught that, not a review.
 *
 * The due badge, interval, and review count are carried over from the per-Problem row
 * this replaced. Every one of those numbers was computed on every review and rendered
 * nowhere, which made FSRS look absent from outside; putting the card on the technique
 * must not undo that. They now describe the *technique's* schedule.
 */
@Composable
private fun DueTopicRow(due: DueTopic, now: Instant, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    due.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                DueBadge(describeDue(due.schedule.dueAt, now))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                buildString {
                    append("Memory lasts about ${formatIntervalDays(due.schedule.intervalDays)}")
                    append(" · reviewed ${due.schedule.reviewCount}×")
                    if (due.schedule.lapseCount > 0) {
                        append(" · ${due.schedule.lapseCount} forgotten")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
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
private fun ProblemRow(
    problem: ProblemDefinition,
    subtitle: String,
    due: DueDescription? = null,
    onClick: () -> Unit,
) {
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
            due?.let {
                DueBadge(it)
                Spacer(Modifier.width(8.dp))
            }
            DifficultyBadge(problem.difficulty)
        }
    }
}

/** The scheduler's verdict on one Problem, coloured by how far past due it is. */
@Composable
private fun DueBadge(due: DueDescription) {
    val (color, icon) = when (due.urgency) {
        DueUrgency.OVERDUE -> accentDanger() to Icons.Outlined.LocalFireDepartment
        DueUrgency.DUE -> MaterialTheme.colorScheme.primary to Icons.Outlined.Bolt
        DueUrgency.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant to Icons.Outlined.Schedule
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        // Null on purpose: `due.label` beside it already reads "Overdue by 3 days", so
        // the icon restates it and a description would have it read twice.
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(due.label, style = MaterialTheme.typography.labelSmall, color = color)
    }
}

@Composable
private fun DifficultyBadge(difficulty: ProblemDifficulty) {
    val (label, color) = when (difficulty) {
        ProblemDifficulty.EASY -> "Easy" to accentSuccess()
        ProblemDifficulty.MEDIUM -> "Medium" to accentCaution()
        ProblemDifficulty.HARD -> "Hard" to accentDanger()
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
        // Via the service, which creates the draft row on demand. Reading
        // `profile.drafts.draft(problemId)` first and saving only when it was non-null
        // meant a Problem opened and typed into but never run had no row yet, so this
        // silently did nothing and the source was lost on Back.
        profile.study.saveSource(problemId, source)
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
                    // The memory state FSRS is holding for this Problem, alongside the
                    // topics. It was stored on every review and shown nowhere, so the
                    // learner had no way to see that repeated success was lengthening
                    // the interval — which is the entire premise they are trusting.
                    buildString {
                        append(opened.problem.topics.joinToString(" · "))
                        opened.schedule?.let { schedule ->
                            append("  ·  reviewed ${schedule.reviewCount}×")
                            append(", interval ${formatIntervalDays(schedule.intervalDays)}")
                        } ?: append("  ·  first attempt")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            opened.schedule?.let {
                DueBadge(describeDue(it.dueAt, Clock.System.now()))
                Spacer(Modifier.width(10.dp))
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
                // In a Card, like every sibling in this column — and like the Android
                // client, which has always wrapped the statement in one.
                //
                // Not only for consistency. An inset surface is painted `surface`, the
                // page's own colour, so it reads as recessed *against a card*. This
                // column had no fill of its own, and `background` and `surface` are the
                // same value in both palettes, so the statement's code blocks and the
                // examples below were painted page-colour-on-page-colour: 1.000:1,
                // invisible. Android was unaffected because its statement sits in a
                // Card, which is exactly why reviewing one client is not reviewing both.
                // Caught by probing `captureToImage` pixels — see
                // `ProblemPaneContrastTest`.
                Card {
                    Column(Modifier.padding(14.dp)) {
                        MarkdownBlock(opened.problem.statementMarkdown)

                        if (opened.problem.examples.isNotEmpty()) {
                            Spacer(Modifier.height(10.dp))
                            Text("Examples", style = MaterialTheme.typography.titleSmall)
                            opened.problem.examples.forEach { example ->
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
                                    Mono("Input:  ${example.input}")
                                    Mono("Output: ${example.output}")
                                    example.explanation?.let {
                                        Spacer(Modifier.height(4.dp))
                                        Text(it, style = MaterialTheme.typography.bodySmall)
                                    }
                                }
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

                // The only weighted child of this Column, which is what keeps it the
                // largest thing in the pane.
                //
                // The result block below used to claim `weight(1f)` as well. Two
                // siblings of one Column with equal weight split the space equally, so
                // pressing Run halved the editor — measured 519px to 201px, a 61% loss
                // — and pushed the code the learner was about to rate off the top of
                // the screen. Rating a solution you can no longer read is the worst
                // possible moment to lose sight of it.
                //
                // Making the results content-sized and capped instead means the editor
                // gives up only what the results genuinely need, rather than a fixed
                // proportion whether they need it or not: a run with two test rows now
                // costs the editor two test rows.
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
                    // Capped, then scrolls. A long stack of failing tests with
                    // tracebacks would otherwise be unbounded and squeeze the editor to
                    // nothing, which is the same defect in the other direction.
                    Box(
                        Modifier
                            .heightIn(max = RESULT_BLOCK_MAX_HEIGHT)
                            .verticalScroll(rememberScrollState()),
                    ) {
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
                                    "Next review in ${formatIntervalDays(schedule.intervalDays)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                                // The memory-strength change behind that interval.
                                // "Next review in 6 days" alone does not say whether
                                // the review helped; stability moving from 4 days to 9
                                // does, and it is the number FSRS actually optimises.
                                // Taken from the recorded transition rather than
                                // recomputed, so what is displayed is what was stored.
                                val transition = result.review.transition
                                Spacer(Modifier.height(2.dp))
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
    // The mapping is in :shared so Android cannot word the same outcome differently.
    val outcome = RunOutcomePresentation.of(run.outcome, run.passedTestCount, run.totalTestCount)
    val tint = Color(outcome.tint(LocalBeeCodePalette.current))

    // A passing run starts collapsed. Its per-test rows are a column of identical
    // ticks that say nothing the headline has not already said, and they cost the
    // editor the height they occupy at exactly the moment the learner is re-reading
    // their solution to rate it. A failure is the opposite: the rows are the whole
    // point, so it starts open. Keyed on the run so a new attempt re-applies the rule
    // rather than inheriting the last one's state.
    var showTests by remember(run.id) { mutableStateOf(run.outcome != ExecutionOutcome.PASSED) }

    Card {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // A glyph rather than the coloured dot this replaced. The dot's only
                // content was its tint, so a learner who cannot separate the green from
                // the amber — or who reads the screen in greyscale — got nothing from it
                // that the headline did not already say, and it failed WCAG 1.4.1 for
                // exactly that reason. The glyph says pass, warn, or fail in its shape.
                Text(
                    outcome.glyph,
                    color = tint,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium,
                    // Cleared rather than described: `outcome.headline` is the very next
                    // node and already reads "All tests passed". Unlike the per-test rows,
                    // where the glyph is the only verdict, here it is a second copy of one
                    // — so a reader should announce the sentence, not the character before
                    // it. The glyph earns its place visually (WCAG 1.4.1) and nowhere else.
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
                // In the headline row, not under the rows it controls. Below them it sat
                // inside the block's scrolling area, so with enough failing tests the
                // only way to collapse them was to scroll past them first — and the
                // reason to collapse them is that there are too many to scroll through.
                if (run.testResults.isNotEmpty()) {
                    Spacer(Modifier.width(4.dp))
                    TextButton(
                        onClick = { showTests = !showTests },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text(
                            // Both directions are always offered: a learner who passed
                            // may still want to see which tests ran, and one debugging a
                            // failure may want the rows out of the way to see more code.
                            if (showTests) {
                                "Hide the ${run.testResults.size} tests"
                            } else {
                                "Show the ${run.testResults.size} tests"
                            },
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (run.testResults.isNotEmpty() && showTests) {
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
                            MaterialTheme.colorScheme.surface,
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
                color = if (result.passed) accentSuccess() else accentDanger(),
                fontWeight = FontWeight.Bold,
                // The glyph is the only thing separating a pass from a failure here — the
                // rest of the row is the test's name, identical either way. Left bare, a
                // reader announces the character ("check mark", "multiplication x") or
                // skips it, so a learner heard a list of test names with no verdicts.
                // `clearAndSetSemantics` replaces the glyph rather than adding to it.
                modifier = Modifier.clearAndSetSemantics {
                    contentDescription = ScreenReaderLabels.testCase(result.passed)
                },
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
                            MaterialTheme.colorScheme.surface,
                            RoundedCornerShape(4.dp),
                        )
                        .padding(6.dp),
                ) { Mono(message) }
            }
        }
    }
}

@Composable
private fun ProgressPane(
    profile: BeeCodeProfile,
    refreshToken: Int,
    showMotivation: Boolean,
) {
    val stats = remember(refreshToken) { profile.statistics() }
    val achievements = remember(refreshToken) { profile.achievements() }
    val topicMastery = remember(refreshToken) { profile.topicMastery() }
    var selectedTab by remember { mutableStateOf(ProgressTab.OVERVIEW) }
    var selectedPeriod by remember { mutableStateOf(StatisticsPeriod.THIRTY_DAYS) }
    val tabs = if (showMotivation) ProgressTab.entries else ProgressTab.entries.dropLast(1)

    LaunchedEffect(showMotivation) {
        if (!showMotivation && selectedTab == ProgressTab.ACHIEVEMENTS) {
            selectedTab = ProgressTab.OVERVIEW
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            "Progress",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )

        PrimaryTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0)) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { selectedTab = tab },
                    text = { Text(tab.label, maxLines = 1) },
                )
            }
        }

        when (selectedTab) {
            ProgressTab.OVERVIEW -> DesktopOverview(
                profile = profile,
                stats = stats,
                selectedPeriod = selectedPeriod,
                onPeriodSelected = { selectedPeriod = it },
                showMotivation = showMotivation,
            )
            ProgressTab.COVERAGE -> DesktopCoverage(stats, topicMastery)
            ProgressTab.ACHIEVEMENTS -> achievements.states.forEach { AchievementRow(it) }
        }
    }
}

@Composable
private fun DesktopOverview(
    profile: BeeCodeProfile,
    stats: StudyStatistics,
    selectedPeriod: StatisticsPeriod,
    onPeriodSelected: (StatisticsPeriod) -> Unit,
    showMotivation: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DesktopPeriodSelector(selectedPeriod, onPeriodSelected)
        if (!stats.hasActivity) {
            Text(
                "No review activity yet. Catalogue and schedule totals are still available.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (stats.comparison(selectedPeriod).current.reviews == 0) {
            Text(
                "No reviews in this period.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DesktopPeriodSummary(stats, selectedPeriod)
        DesktopActivityChart(stats, selectedPeriod)
        DesktopCatalogueProgress(stats, showMotivation)
        DesktopScheduleCard(profile, stats)
        DesktopIntervalDistribution(stats)
    }
}

/**
 * One technique's figures. Mirrors Android's `TopicAbilityRow`.
 *
 * Two numbers side by side and never blended: how long the memory lasts, and how much
 * of the technique has been practised. A null recall rate reads "not enough practice
 * yet" rather than 0% — the difference between "weak at this" and "has barely done
 * this" is the one the learner most needs, and a fake zero destroys it.
 */
@Composable
private fun TopicAbilityRow(ability: TopicAbility) {
    Card {
        Column(Modifier.padding(14.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    ability.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
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
private fun DesktopPeriodSelector(
    selected: StatisticsPeriod,
    onSelected: (StatisticsPeriod) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        StatisticsPeriod.entries.forEachIndexed { index, period ->
            SegmentedButton(
                selected = selected == period,
                onClick = { onSelected(period) },
                shape = SegmentedButtonDefaults.itemShape(index, StatisticsPeriod.entries.size),
                modifier = Modifier.weight(1f),
            ) {
                Text("${period.days} days", maxLines = 1)
            }
        }
    }
}

@Composable
private fun DesktopPeriodSummary(stats: StudyStatistics, period: StatisticsPeriod) {
    val comparison = stats.comparison(period)
    val metrics = listOf(
        MetricPresentation(
            "Reviews",
            comparison.current.reviews.toString(),
            comparison.countChange(comparison.reviewChange),
        ),
        MetricPresentation(
            "Successful reviews",
            comparison.current.successfulReviews.toString(),
            comparison.countChange(comparison.successfulReviewChange),
        ),
        MetricPresentation(
            "Success rate",
            comparison.current.successRate?.let { "${(it * 100).roundToInt()}%" } ?: "-",
            comparison.rateChange(),
        ),
        MetricPresentation(
            "Active days",
            comparison.current.activeDays.toString(),
            comparison.dayChange(),
        ),
    )

    BoxWithConstraints {
        if (maxWidth < 700.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        row.forEach { metric ->
                            PeriodMetricTile(metric, Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                metrics.forEach { metric ->
                    PeriodMetricTile(metric, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun PeriodMetricTile(metric: MetricPresentation, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(
            Modifier.fillMaxWidth().height(104.dp).padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(metric.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                metric.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                metric.comparison,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun DesktopActivityChart(stats: StudyStatistics, period: StatisticsPeriod) {
    val buckets = stats.activity(period)
    val peak = buckets.maxOfOrNull { it.reviews }?.coerceAtLeast(1) ?: 1

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Activity - ${period.days} days", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(90.dp),
                horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 20) 3.dp else 5.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                buckets.forEach { bucket ->
                    val fraction = bucket.reviews.toFloat() / peak
                    Box(
                        Modifier
                            .weight(1f)
                            .height((4 + fraction * 72).dp)
                            .background(
                                if (bucket.reviews == 0) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                RoundedCornerShape(2.dp),
                            )
                            .semantics { contentDescription = bucket.activityDescription() },
                    )
                }
            }
        }
    }
}

@Composable
private fun DesktopCatalogueProgress(stats: StudyStatistics, showMotivation: Boolean) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("All-time catalogue progress", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                "${stats.catalogueProblemsSolved} of ${stats.totalProblems} Problems solved",
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { stats.catalogueCompletionFraction },
                modifier = Modifier.fillMaxWidth().height(6.dp),
                trackColor = MaterialTheme.colorScheme.surface,
                gapSize = 0.dp,
                drawStopIndicator = {},
            )
            if (showMotivation && stats.currentStreakDays > 0) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "${stats.currentStreakDays} day streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DesktopScheduleCard(profile: BeeCodeProfile, stats: StudyStatistics) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Outlined.Schedule,
                    // Null on purpose: "Your schedule" is the next node in the row.
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text("Your schedule", style = MaterialTheme.typography.titleSmall)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                "FSRS-7 picks each interval from how well you recalled the Problem, not " +
                    "from a fixed ladder.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            ScheduleFact("Due now", "${stats.dueNow}")
            ScheduleFact("Due tomorrow", "${stats.dueTomorrow}")
            ScheduleFact(
                "Average interval",
                stats.averageIntervalDays?.let { formatIntervalDays(it) } ?: "-",
            )
            ScheduleFact("Not yet attempted", "${stats.notYetAttempted}")
            if (stats.leeches.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.LocalFireDepartment,
                        // Null on purpose: "3 leeches" follows it, and the flame's only
                        // job is to make the count findable while scanning.
                        contentDescription = null,
                        tint = accentDanger(),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "${stats.leeches.size} " +
                            if (stats.leeches.size == 1) "leech" else "leeches",
                        style = MaterialTheme.typography.labelMedium,
                        color = accentDanger(),
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    stats.leeches.mapNotNull { profile.catalogue.problem(it)?.title }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun DesktopIntervalDistribution(stats: StudyStatistics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Current interval distribution", style = MaterialTheme.typography.titleSmall)
            stats.intervalDistribution.forEach { bucket ->
                ProgressRow(
                    label = bucket.range.displayLabel(),
                    solved = bucket.count,
                    total = bucket.total,
                    fraction = bucket.fraction,
                )
            }
        }
    }
}

@Composable
private fun DesktopCoverage(stats: StudyStatistics, topicMastery: TopicMasteryProjection) {
    var axis by remember { mutableStateOf(TopicAxis.DATA_STRUCTURES) }
    val topics = when (axis) {
        TopicAxis.DATA_STRUCTURES -> stats.dataStructureProgress
        TopicAxis.TECHNIQUES -> stats.techniqueProgress
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DifficultyBreakdown(stats)
        // Beside coverage rather than in Overview, and for the same reason as on Android:
        // Overview answers "what did I do lately", while recall and interval are standing
        // facts, and coverage has to be read next to recall — no single number separates
        // "weak at DP" from "hasn't done DP".
        topicMastery.practised.takeIf { it.isNotEmpty() }?.let { practised ->
            Text(
                "Techniques you have practised",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            // The evidence base, stated above the numbers rather than in a footnote.
            // What is measured is recall of Problems already solved; a learner who
            // reads these as raw problem-solving ability will trust them for a
            // decision they cannot support.
            Text(
                "How well you recall Problems you have already solved in each technique.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            practised.forEach { TopicAbilityRow(it) }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            TopicAxis.entries.forEachIndexed { index, candidate ->
                SegmentedButton(
                    selected = axis == candidate,
                    onClick = { axis = candidate },
                    shape = SegmentedButtonDefaults.itemShape(index, TopicAxis.entries.size),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(candidate.label, maxLines = 1)
                }
            }
        }
        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(axis.label, style = MaterialTheme.typography.titleSmall)
                if (topics.isEmpty()) {
                    Text(
                        "No topics in this catalogue.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    topics.forEach { TopicProgressRow(it) }
                }
            }
        }
    }
}

@Composable
private fun DifficultyBreakdown(stats: StudyStatistics) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Difficulty progress", style = MaterialTheme.typography.titleSmall)
            ProblemDifficulty.entries.forEach { difficulty ->
                val progress = stats.byDifficulty[difficulty] ?: return@forEach
                if (progress.total == 0) return@forEach
                ProgressRow(
                    label = difficulty.name.lowercase().replaceFirstChar { it.uppercase() },
                    solved = progress.solved,
                    total = progress.total,
                    fraction = progress.fraction,
                )
            }
        }
    }
}

@Composable
private fun TopicProgressRow(progress: TopicProgress) {
    ProgressRow(progress.topic, progress.solved, progress.total, progress.fraction)
}

@Composable
private fun ProgressRow(label: String, solved: Int, total: Int, fraction: Float) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
            Text(
                "$solved/$total",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            trackColor = MaterialTheme.colorScheme.surface,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

/** One label-and-value line in the schedule card. */
@Composable
private fun ScheduleFact(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
    }
}

private data class MetricPresentation(
    val label: String,
    val value: String,
    val comparison: String,
)

private enum class ProgressTab(val label: String) {
    OVERVIEW("Overview"),
    COVERAGE("Coverage"),
    ACHIEVEMENTS("Achievements"),
}

private enum class TopicAxis(val label: String) {
    DATA_STRUCTURES("Data structures"),
    TECHNIQUES("Techniques"),
}

private fun dev.bee.beecode.app.PeriodComparison.countChange(change: Int): String =
    if (!hasEarlierActivity) "No earlier activity" else changeDescription(change)

private fun dev.bee.beecode.app.PeriodComparison.dayChange(): String =
    if (!hasEarlierActivity) {
        "No earlier activity"
    } else {
        val amount = kotlin.math.abs(activeDayChange)
        when {
            activeDayChange > 0 -> "$amount more active ${plural(amount, "day")}"
            activeDayChange < 0 -> "$amount fewer active ${plural(amount, "day")}"
            else -> "No change"
        }
    }

private fun dev.bee.beecode.app.PeriodComparison.rateChange(): String {
    if (!hasEarlierActivity) return "No earlier activity"
    val change = successRatePercentagePointChange ?: return "No comparable rate"
    return when {
        change > 0.0 -> "+${formatPercentagePoints(change)} pp"
        change < 0.0 -> "${formatPercentagePoints(change)} pp"
        else -> "No change"
    }
}

private fun changeDescription(change: Int): String {
    val amount = kotlin.math.abs(change)
    return when {
        change > 0 -> "$amount more"
        change < 0 -> "$amount fewer"
        else -> "No change"
    }
}

private fun formatPercentagePoints(value: Double): String =
    if (value == value.roundToInt().toDouble()) value.roundToInt().toString() else "%.1f".format(value)

private fun plural(count: Int, singular: String): String =
    if (count == 1) singular else "${singular}s"

private fun ActivityBucket.activityDescription(): String {
    val dates = if (isSingleDay) "$startDate" else "$startDate to $endDate"
    return "$dates: $reviews ${plural(reviews, "review")}, " +
        "$successfulReviews successful ${plural(successfulReviews, "review")}"
}

private fun IntervalRange.displayLabel(): String = when (this) {
    IntervalRange.LESS_THAN_ONE_DAY -> "<1 day"
    IntervalRange.ONE_TO_SIX_DAYS -> "1-6 days"
    IntervalRange.ONE_TO_FOUR_WEEKS -> "1-4 weeks"
    IntervalRange.ONE_TO_SIX_MONTHS -> "1-6 months"
    IntervalRange.SIX_MONTHS_OR_MORE -> "6+ months"
}

/**
 * One achievement: earned, or how far along.
 *
 * Kept in step with Android's row of the same name, which is where the three defects
 * this fixes are described: an emoji marker that could not take a theme colour, centring
 * that floated it beside the middle of a multi-line row rather than beside its title, and
 * a progress bar whose track was 1.021:1 against the card — so an unearned achievement
 * showed a bar with no visible extent, plus a stop indicator at 0% that read as a stray
 * dot.
 */
@Composable
private fun AchievementRow(state: AchievementState) {
    Card {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.height(with(LocalDensity.current) {
                    MaterialTheme.typography.titleSmall.lineHeight.toDp()
                }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.earned) Icons.Filled.Star else Icons.Outlined.Lock,
                    // Labelled, unlike the icons that sit beside their own text: earned
                    // state is carried by shape and tint alone here. `state.detail` gives
                    // a count — "3 of 7 days" — which is progress, not status, and at
                    // "7 of 7 days" does not separate earned from about to be.
                    contentDescription = ScreenReaderLabels.achievement(state.earned),
                    modifier = Modifier.size(20.dp),
                    tint = if (state.earned) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
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
                if (!state.earned && state.fraction > 0f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth(0.5f).height(6.dp),
                        trackColor = MaterialTheme.colorScheme.surface,
                        gapSize = 0.dp,
                        drawStopIndicator = {},
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Text(
                state.detail,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingsPane(
    profile: BeeCodeProfile,
    runnerStatus: RunnerStatus?,
    theme: ThemeChoice,
    onThemeChange: (ThemeChoice) -> Unit,
    family: ThemeFamily,
    onFamilyChange: (ThemeFamily) -> Unit,
    onVisibilityChanged: () -> Unit,
) {
    var limit by remember { mutableStateOf(profile.settings.dailyReviewLimit()) }
    var showProgress by remember { mutableStateOf(profile.settings.showProgress()) }
    var showMotivation by remember {
        mutableStateOf(profile.settings.showStreaksAndAchievements())
    }
    var transferMessage by remember { mutableStateOf<String?>(null) }
    var syncPath by remember { mutableStateOf(profile.settings.syncFilePath()) }
    var webDavUrl by remember { mutableStateOf(profile.settings.syncWebDavUrl() ?: "") }
    var webDavUser by remember { mutableStateOf(profile.settings.syncWebDavUsername() ?: "") }
    // Resolved through SyncCredential, so a password delegated to the OS keyring comes back
    // as the password rather than as the marker that stands in for it in the database.
    var webDavPassword by remember { mutableStateOf(SyncCredential.resolve(profile.settings) ?: "") }
    // Probed once per Settings composition rather than per recomposition: it spawns no
    // process, but it does touch PATH, and the answer cannot change while the pane is open.
    val credentialBackend = remember { SyncCredential.backendName() }
    var linkedAt by remember { mutableStateOf(profile.settings.leaderboardLinkedAt()) }
    var boardStatus by remember { mutableStateOf(LeaderboardService(profile).status()) }
    var boardMessage by remember { mutableStateOf<String?>(null) }
    var syncMessage by remember { mutableStateOf<String?>(null) }
    var syncing by remember { mutableStateOf(false) }
    val settingsScope = rememberCoroutineScope()

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
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "BeeCode follows the desktop's own setting when it can report one. Not " +
                        "every Linux desktop does, and there the app cannot tell — so it " +
                        "assumes dark, and this is how you say otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.entries.forEach { candidate ->
                        FilterChip(
                            selected = theme == candidate,
                            onClick = {
                                profile.settings.setThemeChoice(candidate, Clock.System.now())
                                onThemeChange(candidate)
                            },
                            label = {
                                Text(
                                    when (candidate) {
                                        ThemeChoice.SYSTEM -> "System"
                                        ThemeChoice.DARK -> "Dark"
                                        ThemeChoice.LIGHT -> "Light"
                                    },
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    when (candidate) {
                                        ThemeChoice.SYSTEM -> Icons.Outlined.Bolt
                                        ThemeChoice.DARK -> Icons.Outlined.Bedtime
                                        ThemeChoice.LIGHT -> Icons.Outlined.LightMode
                                    },
                                    // Null on purpose: the chip's own label says System,
                                    // Dark, or Light, and the reader announces that.
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    // Two settings rather than six entries in one list: dark and light are
                    // a *mode*, and folding them in would mean picking a theme gave up
                    // following the desktop's own setting.
                    "Which colours to use. Independent of the setting above — every theme " +
                        "has a dark and a light scheme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    // One tab stop for the group, arrow keys within it.
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeFamily.entries.forEach { candidate ->
                        ThemeFamilyRow(
                            candidate = candidate,
                            selected = family == candidate,
                            onSelect = {
                                profile.settings.setThemeFamily(candidate, Clock.System.now())
                                onFamilyChange(candidate)
                            },
                        )
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Progress visibility", style = MaterialTheme.typography.titleSmall)
                VisibilitySettingRow(
                    label = "Show Progress",
                    checked = showProgress,
                    onCheckedChange = { show ->
                        profile.settings.setShowProgress(show, Clock.System.now())
                        showProgress = show
                        onVisibilityChanged()
                    },
                )
                VisibilitySettingRow(
                    label = "Show streaks and achievements",
                    checked = showMotivation,
                    onCheckedChange = { show ->
                        profile.settings.setShowStreaksAndAchievements(show, Clock.System.now())
                        showMotivation = show
                        onVisibilityChanged()
                    },
                )
            }
        }

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
                        showProgress = profile.settings.showProgress()
                        showMotivation = profile.settings.showStreaksAndAchievements()
                        onVisibilityChanged()
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
                Text("Sync between devices", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Optional. Point this and your other devices at the same file in a " +
                        "folder something already syncs — Dropbox, Syncthing, iCloud Drive, " +
                        "a network share. There is no account and no BeeCode server; the " +
                        "file is yours.",
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
                Spacer(Modifier.height(10.dp))
                Text(
                    "A WebDAV server — Nextcloud, ownCloud, Synology — is the stronger " +
                        "option if you have one: it checks for a conflicting write itself, " +
                        "so two devices syncing at the same moment cannot overwrite each " +
                        "other. A shared file relies on the two syncs not overlapping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Labelled("File", syncPath ?: "Not set — sync is off")
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        ProfileFiles.chooseSyncFile()?.let { chosen ->
                            profile.settings.setSyncFilePath(chosen.absolutePath, Clock.System.now())
                            syncPath = chosen.absolutePath
                            syncMessage = null
                        }
                    }) { Text(if (syncPath == null) "Choose a file…" else "Change…") }

                    Button(
                        // Disabled rather than hidden while running, so a second click
                        // cannot start an overlapping sync against the same file.
                        enabled = syncPath != null && !syncing,
                        onClick = {
                            val target = syncPath ?: return@Button
                            syncing = true
                            syncMessage = null
                            settingsScope.launch {
                                syncMessage = withContext(Dispatchers.IO) {
                                    ProfileFiles.sync(profile, File(target))
                                }
                                showProgress = profile.settings.showProgress()
                                showMotivation = profile.settings.showStreaksAndAchievements()
                                onVisibilityChanged()
                                syncing = false
                            }
                        },
                    ) { Text(if (syncing) "Syncing…" else "Sync now") }

                    if (syncPath != null) {
                        TextButton(onClick = {
                            profile.settings.setSyncFilePath(null, Clock.System.now())
                            syncPath = null
                            syncMessage = "Sync turned off. The file was left where it is."
                        }) { Text("Turn off") }
                    }
                }
                HorizontalDivider(Modifier.padding(vertical = 14.dp))

                Text("Or a WebDAV server", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(6.dp))
                Text(
                    "The address must point at a file, not a folder — for example " +
                        "https://cloud.example.com/remote.php/dav/files/you/beecode-sync.json. " +
                        "https is required: the password is sent with every request, and so " +
                        "are your solutions.",
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
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = webDavUser,
                        onValueChange = { webDavUser = it },
                        label = { Text("Username") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = webDavPassword,
                        onValueChange = { webDavPassword = it },
                        label = { Text("Password") },
                        singleLine = true,
                        // Masked so a password is not readable over a shoulder or in a
                        // screen share. It is still stored in plaintext locally, which the
                        // Settings text below says rather than implying otherwise.
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    // Two different truths, and saying the pessimistic one where it no longer
                    // applies would be as dishonest as the reverse. Which branch a learner sees
                    // is decided by whether a secret service was actually found.
                    if (credentialBackend != null) {
                        "The password is stored in $credentialBackend, not in this profile — so " +
                            "a copy or backup of the profile does not contain it. It is never " +
                            "included in an export or uploaded with your study data."
                    } else {
                        "This machine has no keyring BeeCode can use, so the password is stored " +
                            "in this profile's database unencrypted — the profile folder is " +
                            "readable only by your user account, but a backup of it would " +
                            "expose the password. It is never included in an export or " +
                            "uploaded with your study data."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    enabled = webDavUrl.isNotBlank() && !syncing,
                    onClick = {
                        syncing = true
                        syncMessage = null
                        val now = Clock.System.now()
                        // Saved before syncing, so a learner who closes the window does not
                        // have to retype it — and cleared to null when blank rather than
                        // stored as an empty string that would read as "configured".
                        profile.settings.setSyncWebDavUrl(webDavUrl, now)
                        profile.settings.setSyncWebDavUsername(webDavUser.ifBlank { null }, now)
                        // Into the OS keyring where there is one, leaving only a marker in the
                        // database; plaintext as before where there is not.
                        SyncCredential.store(profile.settings, webDavPassword, now = now)
                        settingsScope.launch {
                            syncMessage = withContext(Dispatchers.IO) {
                                ProfileFiles.syncWebDav(
                                    profile = profile,
                                    url = webDavUrl,
                                    username = webDavUser.ifBlank { null },
                                    password = webDavPassword.ifBlank { null },
                                )
                            }
                            showProgress = profile.settings.showProgress()
                            showMotivation = profile.settings.showStreaksAndAchievements()
                            onVisibilityChanged()
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
                        "yet — so joining now only records what your device would share once " +
                        "one does. Nothing leaves this machine.",
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

                if (linkedAt == null) {
                    Labelled("Status", "Not joined")
                    Spacer(Modifier.height(10.dp))
                    OutlinedButton(onClick = {
                        val now = Clock.System.now()
                        profile.settings.setLeaderboardLinkedAt(now, now)
                        linkedAt = now
                        // Refresh immediately so the count is honest rather than showing zero
                        // until something happens to trigger it.
                        profile.refreshLeaderboardActivity(now)
                        boardStatus = LeaderboardService(profile).status()
                        boardMessage = "Joined. Solves from now on will be shared once a " +
                            "server exists; everything before now stays private."
                    }) { Text("Join a Leaderboard") }
                } else {
                    Labelled("Waiting to send", "${boardStatus.pending}")
                    Labelled("Sent", "${boardStatus.acknowledged}")
                    if (boardStatus.parked > 0) {
                        Labelled("Stuck", "${boardStatus.parked}")
                    }
                    if (boardStatus.rejected > 0) {
                        // Named separately from "stuck": a refusal is final and a learner
                        // should not be invited to retry it forever.
                        Labelled("Refused by the server", "${boardStatus.rejected}")
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {
                            val added = profile.refreshLeaderboardActivity(Clock.System.now())
                            boardStatus = LeaderboardService(profile).status()
                            boardMessage = if (added > 0) {
                                "Queued $added new ${if (added == 1) "solve" else "solves"}."
                            } else {
                                "Nothing new to queue."
                            }
                        }) { Text("Check for new activity") }

                        if (boardStatus.parked > 0) {
                            OutlinedButton(onClick = {
                                val revived = LeaderboardService(profile).retryParked(Clock.System.now())
                                boardStatus = LeaderboardService(profile).status()
                                boardMessage = "Will try $revived again."
                            }) { Text("Try stuck items again") }
                        }

                        TextButton(onClick = {
                            val now = Clock.System.now()
                            profile.settings.setLeaderboardLinkedAt(null, now)
                            LeaderboardService(profile).forget()
                            linkedAt = null
                            boardStatus = LeaderboardService(profile).status()
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

/**
 * One theme family, as a radio row.
 *
 * ## Why a radio row rather than another chip strip
 *
 * The mode above it is three one-word options and fits on a line. A family needs its
 * description shown — "Maximum legibility. Text meets WCAG AAA." is the whole reason a
 * learner would choose it, and a chip has room for a word. So the two controls look
 * different because they are answering differently-sized questions.
 *
 * ## Accessibility
 *
 * `selectable` on the row, and `Modifier.selectableGroup()` on the column that holds
 * them, is what makes this one tab stop with arrow keys inside rather than three
 * unrelated stops — the desktop keyboard behaviour a radio group is expected to have.
 * `onClick = null` on the button itself is deliberate: the row already handles the
 * click, and giving the button its own handler makes the label a dead zone next to a
 * live 20dp circle. The role tells a screen reader to announce "radio button, selected",
 * so the state does not depend on seeing which circle is filled.
 */
@Composable
private fun ThemeFamilyRow(
    candidate: ThemeFamily,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Column(Modifier.weight(1f)) {
            Text(
                candidate.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                candidate.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun VisibilitySettingRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = label },
        )
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
 * Renders the Markdown subset the Problem statements use.
 *
 * The parsing lives in [Markdown] so this and Android's renderer cannot disagree about
 * what a statement means — they had each grown their own copy, with the same two bugs
 * (see that file). What remains here is only the styling, which is the part that should
 * differ per client: a desktop window gets `bodyMedium` where a phone gets `bodySmall`.
 */
@Composable
internal fun MarkdownBlock(markdown: String) {
    Column {
        Markdown.blocks(markdown).forEachIndexed { index, block ->
            // Between blocks, not after each: a trailing gap inside a card reads as a
            // layout mistake, and the card already has its own padding.
            if (index > 0) Spacer(Modifier.height(if (block is Markdown.Block.Heading) 12.dp else 6.dp))
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
                    style = MaterialTheme.typography.bodyMedium,
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
                ) { Mono(block.lines.joinToString("\n")) }
            }
        }
    }
}

/**
 * A list item whose wrapped lines line up under its text rather than under its marker.
 *
 * The marker sits in its own column, so a bullet long enough to wrap keeps its second
 * line inside the list rather than back at the margin.
 */
@Composable
private fun MarkdownListItem(marker: String, text: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(
            marker,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(24.dp),
        )
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
