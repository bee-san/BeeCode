package dev.bee.beecode.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.List
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bee.beecode.android.LocalBeeCodePalette
import dev.bee.beecode.android.accentCaution
import dev.bee.beecode.android.accentDanger
import dev.bee.beecode.android.accentSuccess
import dev.bee.beecode.app.AchievementState
import dev.bee.beecode.app.ActivityBucket
import dev.bee.beecode.android.DocumentSyncStore
import dev.bee.beecode.design.ScreenReaderLabels
import dev.bee.beecode.design.ThemeFamily
import kotlinx.coroutines.launch
import dev.bee.beecode.app.RestoreResult
import dev.bee.beecode.app.SyncReport
import dev.bee.beecode.app.WebDavSyncStore
import dev.bee.beecode.app.DueTopic
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.app.TopicAbility
import dev.bee.beecode.app.TopicMasteryProjection
import dev.bee.beecode.app.StatisticsPeriod
import dev.bee.beecode.app.TopicProgress
import dev.bee.beecode.app.IntervalRange
import dev.bee.beecode.design.ThemeChoice
import dev.bee.beecode.domain.DueDescription
import dev.bee.beecode.domain.DueUrgency
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.describeDue
import dev.bee.beecode.domain.formatIntervalDays
import dev.bee.beecode.python.RunnerCapability
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlin.math.roundToInt

/**
 * Identifies the scrolling study queue so a test can scroll it to a given Problem.
 *
 * The catalogue is expected to keep growing, so any Problem may be below the fold. A
 * test that reaches one by name needs the scrollable container, and finding it by tag
 * is stabler than matching on a layout property. Deliberately the same tag the desktop
 * client uses, so the two suites can drive the queue identically.
 */
internal const val QUEUE_LIST_TAG = "queue-list"

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
    val showProgress by viewModel.showProgress.collectAsStateWithLifecycle()

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
                // Vector icons, not emoji. These three were `Text("🐝")`, `Text("📊")`
                // and `Text("⚙")`, and on the device that came out as two full-colour
                // glyphs sitting off the text baseline beside one flat monochrome one —
                // because the OS picks the font for an emoji and BeeCode does not get a
                // say. Worse, none of them could take the selected tint, so the active
                // tab was signalled only by the pill behind it.
                //
                // The same three icons desktop's rail uses, so both clients name the same
                // destinations with the same symbols. All from `material-icons-core`,
                // which is already a dependency: see the build file for the +3.9 MB the
                // extended library measured, and why a phone does not pay it.
                // All three descriptions are null on purpose: each item's own `label`
                // names the destination, and describing the icon too makes TalkBack
                // announce "Study, Study" on every tab.
                NavigationBarItem(
                    selected = screen is Screen.Queue,
                    onClick = viewModel::showQueue,
                    icon = { Icon(Icons.Outlined.List, contentDescription = null) },
                    label = { Text("Study") },
                )
                if (showProgress) {
                    NavigationBarItem(
                        selected = screen is Screen.Statistics,
                        onClick = viewModel::showStatistics,
                        icon = { Icon(Icons.Outlined.CheckCircle, contentDescription = null) },
                        label = { Text("Progress") },
                    )
                }
                NavigationBarItem(
                    selected = screen is Screen.Settings,
                    onClick = viewModel::showSettings,
                    icon = { Icon(Icons.Outlined.Settings, contentDescription = null) },
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
    val showMotivation by viewModel.showStreaksAndAchievements.collectAsStateWithLifecycle()
    // One instant per queue, not one per row: every row must describe its due time against
    // the same moment, or two Problems due at the same instant can render with different
    // labels. Keyed on the queue itself so a finalized review — which emits a new queue —
    // re-reads the clock, and an ordinary recomposition does not.
    val now = remember(queue) { Clock.System.now() }

    LazyColumn(
        // Tagged so a test can scroll the queue to a specific Problem. Without it a
        // test must assert against whatever happens to be above the fold, which turns
        // adding a Problem into a UI failure — the same reason the solved count is
        // derived from the catalogue rather than written as a literal.
        modifier = Modifier.fillMaxSize().testTag(QUEUE_LIST_TAG),
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
                            if (showMotivation && stats.currentStreakDays > 0) {
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
                    DueTopicCard(due, now) { viewModel.openProblem(due.problem.id) }
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
 *
 * The due badge, the review count, and the interval are all here for the reason they
 * were put on the per-Problem row this card replaced: every one of those numbers was
 * already computed and stored on every review and rendered nowhere, so FSRS looked
 * absent from outside. Moving the card to the topic must not lose that — it is now the
 * *technique's* schedule the learner can check against their own memory.
 */
@Composable
private fun DueTopicCard(due: DueTopic, now: Instant, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp).fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    due.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                DueBadge(describeDue(due.schedule.dueAt, now))
            }
            Spacer(Modifier.height(6.dp))
            Text(
                // The interval as a durability claim rather than a bare number: a
                // technique's interval *is* FSRS's estimate of how long the learner will
                // hold it, and saying so is what makes it checkable against their own
                // sense of whether they still remember it. The review count comes along
                // because it is the evidence behind the estimate.
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
private fun ProblemCard(
    problem: ProblemDefinition,
    subtitle: String,
    onClick: () -> Unit,
    due: DueDescription? = null,
) {
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
            due?.let {
                DueBadge(it)
                Spacer(Modifier.width(8.dp))
            }
            DifficultyBadge(problem.difficulty)
        }
    }
}

/**
 * The scheduler's verdict on one Problem, coloured by how far past due it is.
 *
 * Deliberately the same three states, wording, and colours as desktop's badge. A learner
 * with both clients is looking at one schedule, and two vocabularies for it would read as
 * two different answers.
 */
@Composable
private fun DueBadge(due: DueDescription) {
    val color = when (due.urgency) {
        DueUrgency.OVERDUE -> accentDanger()
        DueUrgency.DUE -> MaterialTheme.colorScheme.primary
        DueUrgency.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(due.label, style = MaterialTheme.typography.labelSmall, color = color)
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
    val showMotivation by viewModel.showStreaksAndAchievements.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(ProgressTab.OVERVIEW) }
    var selectedPeriod by remember { mutableStateOf(StatisticsPeriod.THIRTY_DAYS) }
    val tabs = if (showMotivation) ProgressTab.entries else ProgressTab.entries.dropLast(1)

    LaunchedEffect(showMotivation) {
        if (!showMotivation && selectedTab == ProgressTab.ACHIEVEMENTS) {
            selectedTab = ProgressTab.OVERVIEW
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
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

        val stats = statistics
        if (stats == null) {
            LoadingRow()
        } else {
            when (selectedTab) {
                ProgressTab.OVERVIEW -> OverviewTab(
                    stats = stats,
                    selectedPeriod = selectedPeriod,
                    onPeriodSelected = { selectedPeriod = it },
                    showMotivation = showMotivation,
                    titleOf = viewModel::problemTitle,
                )
                ProgressTab.COVERAGE -> CoverageTab(stats, topicMastery)
                ProgressTab.ACHIEVEMENTS -> achievements?.states?.forEach { AchievementRow(it) }
            }
        }
    }
}

@Composable
private fun OverviewTab(
    stats: StudyStatistics,
    selectedPeriod: StatisticsPeriod,
    onPeriodSelected: (StatisticsPeriod) -> Unit,
    showMotivation: Boolean,
    titleOf: (ProblemId) -> String?,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        PeriodSelector(selectedPeriod, onPeriodSelected)
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
        PeriodSummary(stats, selectedPeriod)
        ActivityChart(stats, selectedPeriod)
        CatalogueProgress(stats, showMotivation)
        ScheduleCard(stats, titleOf)
        IntervalDistribution(stats)
    }
}

@Composable
private fun PeriodSelector(
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
private fun PeriodSummary(stats: StudyStatistics, period: StatisticsPeriod) {
    val comparison = stats.comparison(period)
    val metrics = listOf(
        MetricPresentation(
            label = "Reviews",
            value = comparison.current.reviews.toString(),
            comparison = comparison.countChange(comparison.reviewChange),
        ),
        MetricPresentation(
            label = "Successful reviews",
            value = comparison.current.successfulReviews.toString(),
            comparison = comparison.countChange(comparison.successfulReviewChange),
        ),
        MetricPresentation(
            label = "Success rate",
            value = comparison.current.successRate
                ?.let { "${(it * 100).roundToInt()}%" }
                ?: "-",
            comparison = comparison.rateChange(),
        ),
        MetricPresentation(
            label = "Active days",
            value = comparison.current.activeDays.toString(),
            comparison = comparison.dayChange(),
        ),
    )

    BoxWithConstraints {
        if (maxWidth < 520.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                metrics.chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { metric ->
                            PeriodMetricTile(metric, Modifier.weight(1f))
                        }
                    }
                }
            }
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            Text(
                metric.value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
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

/**
 * The scheduler's own view of the collection.
 *
 * Every number here was already computed by `Statistics` and rendered nowhere on Android,
 * which is why the app gave no evidence it was scheduling anything at all — the complaint
 * this answers was "I'm not sure fsrs / studying is hooked up?", asked about a scheduler
 * that was working correctly and saying nothing.
 *
 * @param titleOf resolves a leech's id to its title. Named rather than counted: a leech is
 *   a Problem the learner keeps failing, and the useful response is to go learn it properly
 *   rather than to keep drilling it.
 */
@Composable
private fun ScheduleCard(stats: StudyStatistics, titleOf: (ProblemId) -> String?) {
    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Your schedule", style = MaterialTheme.typography.titleSmall)
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
                stats.averageIntervalDays?.let { formatIntervalDays(it) } ?: "—",
            )
            ScheduleFact("Not yet attempted", "${stats.notYetAttempted}")

            if (stats.leeches.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text(
                    "${stats.leeches.size} ${if (stats.leeches.size == 1) "leech" else "leeches"}",
                    style = MaterialTheme.typography.labelMedium,
                    color = accentDanger(),
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stats.leeches.mapNotNull(titleOf).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

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

@Composable
private fun ActivityChart(stats: StudyStatistics, period: StatisticsPeriod) {
    val buckets = stats.activity(period)
    val peak = buckets.maxOfOrNull { it.reviews }?.coerceAtLeast(1) ?: 1

    Card {
        Column(Modifier.padding(16.dp)) {
            Text("Activity - ${period.days} days", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth().height(72.dp),
                horizontalArrangement = Arrangement.spacedBy(if (buckets.size > 20) 2.dp else 4.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                buckets.forEach { bucket ->
                    val fraction = bucket.reviews.toFloat() / peak
                    Box(
                        Modifier
                            .weight(1f)
                            .height((4 + fraction * 56).dp)
                            .background(
                                if (bucket.reviews == 0) {
                                    MaterialTheme.colorScheme.surface
                                } else {
                                    MaterialTheme.colorScheme.primary
                                },
                                RoundedCornerShape(3.dp),
                            )
                            .semantics {
                                contentDescription = bucket.activityDescription()
                            },
                    )
                }
            }
        }
    }
}

@Composable
private fun CatalogueProgress(stats: StudyStatistics, showMotivation: Boolean) {
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
private fun IntervalDistribution(stats: StudyStatistics) {
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

/**
 * What has been covered, and how well it is held.
 *
 * The mastery list lives here rather than in Overview because Overview answers "what did
 * I do lately" — period comparisons and activity — while a technique's recall rate and
 * interval are a standing fact about the learner, on the same footing as the coverage
 * fractions directly below. Coverage and recall also have to be read together: no single
 * number can separate "weak at DP" from "hasn't done DP", so the fraction is shown beside
 * the rate and never multiplied into it.
 */
@Composable
private fun CoverageTab(stats: StudyStatistics, topicMastery: TopicMasteryProjection?) {
    var axis by remember { mutableStateOf(TopicAxis.DATA_STRUCTURES) }
    val topics = when (axis) {
        TopicAxis.DATA_STRUCTURES -> stats.dataStructureProgress
        TopicAxis.TECHNIQUES -> stats.techniqueProgress
    }

    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DifficultyBreakdown(stats)
        topicMastery?.practised?.takeIf { it.isNotEmpty() }?.let { practised ->
            Text(
                "Techniques you have practised",
                style = MaterialTheme.typography.titleMedium,
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
    ProgressRow(
        label = progress.topic,
        solved = progress.solved,
        total = progress.total,
        fraction = progress.fraction,
    )
}

@Composable
private fun ProgressRow(label: String, solved: Int, total: Int, fraction: Float) {
    Column {
        Row(Modifier.fillMaxWidth()) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.weight(1f),
            )
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
 * Three things were wrong here on the device, all of them about alignment and contrast
 * rather than about what it says:
 *
 * - The marker was `Text("🏆")` or `Text("○")`, so an earned row rendered a full-colour
 *   emoji and an unearned one a thin monochrome ring — two different visual weights for
 *   two states of the same list, and neither able to take a theme colour.
 * - `Alignment.CenterVertically` centred that marker against the *whole* row, which is
 *   two or three lines tall once the description wraps and a progress bar appears. The
 *   marker floated beside the middle of the text instead of beside its title.
 * - The bar's default track is `secondaryContainer`, 1.021:1 against the card in light —
 *   so an unearned achievement showed a bar with no visible extent, and at 0% Material
 *   still draws its stop indicator, which read as a stray dot with no bar attached.
 */
@Composable
private fun AchievementRow(state: AchievementState) {
    Card {
        Row(
            Modifier.padding(14.dp).fillMaxWidth(),
            // Top, not centre: the marker belongs beside the title, which is the first
            // line, whatever the rest of the row grows to. `Top` alone would hang it a
            // couple of dp high against the title's own line height, so the icon box
            // gets that line's height and centres within it.
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                Modifier.height(with(LocalDensity.current) {
                    MaterialTheme.typography.titleSmall.lineHeight.toDp()
                }),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (state.earned) Icons.Filled.Star else Icons.Outlined.Lock,
                    // Labelled, unlike the nav-bar icons that sit beside their own text.
                    // This one used to be null on the grounds that `state.detail` on the
                    // right already gave the state, and it does not: the detail is a count
                    // — "3 of 7 days" — which is progress, and at "7 of 7 days" it does
                    // not separate earned from about to be. Earned was carried by a filled
                    // amber star against a muted outlined lock, and by nothing else.
                    contentDescription = ScreenReaderLabels.achievement(state.earned),
                    modifier = Modifier.size(20.dp),
                    // Earned is the app's own amber and unearned is deliberately muted:
                    // the state should be legible from colour at a glance, without
                    // reading either the icon shape or the detail text.
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
                // Only once there is progress to show. A 0% bar is not information — it
                // is the same as no bar, plus Material's stop indicator floating alone at
                // the far end where it reads as a rendering fault.
                if (!state.earned && state.fraction > 0f) {
                    Spacer(Modifier.height(8.dp))
                    LinearProgressIndicator(
                        progress = { state.fraction },
                        modifier = Modifier.fillMaxWidth().height(6.dp),
                        // An explicit track: the default `secondaryContainer` is 1.021:1
                        // against this card, so the bar's full extent — the thing that
                        // makes a fraction legible — was invisible.
                        trackColor = MaterialTheme.colorScheme.surface,
                        // The gap Material draws between bar and stop indicator assumes a
                        // track you can see. With one, it just breaks the bar up.
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
                // Aligned to the title for the same reason as the marker, and given the
                // title's line height so a wrapped detail still starts on that line.
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun SettingsScreen(viewModel: StudyViewModel) {
    val runnerStatus by viewModel.runnerStatus.collectAsStateWithLifecycle()
    val theme by viewModel.themeChoice.collectAsStateWithLifecycle()
    val family by viewModel.themeFamily.collectAsStateWithLifecycle()
    val showProgress by viewModel.showProgress.collectAsStateWithLifecycle()
    val showMotivation by viewModel.showStreaksAndAchievements.collectAsStateWithLifecycle()
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

        // Mirrors desktop's Appearance card, with the honest difference that Android always
        // knows what the system theme is — so "System" here is a real answer rather than
        // desktop's best guess on a Linux desktop that will not say.
        Card {
            Column(Modifier.padding(16.dp)) {
                Text("Appearance", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    "BeeCode follows your system setting unless you say otherwise.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeChoice.entries.forEach { candidate ->
                        val label = when (candidate) {
                            ThemeChoice.SYSTEM -> "System"
                            ThemeChoice.DARK -> "Dark"
                            ThemeChoice.LIGHT -> "Light"
                        }
                        if (theme == candidate) {
                            Button(onClick = { viewModel.setThemeChoice(candidate) }) {
                                Text(label)
                            }
                        } else {
                            OutlinedButton(onClick = { viewModel.setThemeChoice(candidate) }) {
                                Text(label)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))
                Text("Theme", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    // Word for word desktop's copy. A learner with both clients is
                    // configuring one preference, and two explanations of it read as two
                    // settings that might not agree.
                    "Which colours to use. Independent of the setting above — every theme " +
                        "has a dark and a light scheme.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Column(
                    // One traversal stop for the group rather than three, which is what
                    // TalkBack's radio-group navigation expects.
                    Modifier.selectableGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    ThemeFamily.entries.forEach { candidate ->
                        ThemeFamilyRow(
                            candidate = candidate,
                            selected = family == candidate,
                            onSelect = { viewModel.setThemeFamily(candidate) },
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
                    onCheckedChange = viewModel::setShowProgress,
                )
                VisibilitySettingRow(
                    label = "Show streaks and achievements",
                    checked = showMotivation,
                    onCheckedChange = viewModel::setShowStreaksAndAchievements,
                )
            }
        }

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

/**
 * One theme family, as a radio row. Deliberately the same shape as desktop's row.
 *
 * ## Why a radio row rather than another button strip
 *
 * The mode above it is three one-word options that fit on a line. A family needs its
 * description shown — "Maximum legibility. Text meets WCAG AAA." is the whole reason a
 * learner would pick it, and a button has room for a word. The two controls look
 * different because they answer differently-sized questions.
 *
 * ## Accessibility
 *
 * `selectable` with [Role.RadioButton] is what makes TalkBack announce "selected" rather
 * than leaving the state to whichever circle is filled. `onClick = null` on the button
 * itself is deliberate: the row already handles the click, and a handler on the button
 * would make the label a dead zone beside a live 20dp circle — the whole row is the
 * target, which is also what keeps it above the 48dp touch minimum.
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
            .padding(vertical = 8.dp),
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
