package dev.bee.beecode.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.bee.beecode.android.LocalBeeCodePalette
import dev.bee.beecode.android.accentCaution
import dev.bee.beecode.android.accentDanger
import dev.bee.beecode.android.accentSuccess
import dev.bee.beecode.app.DailyActivity
import dev.bee.beecode.app.DueTopic
import dev.bee.beecode.app.ProblemRecommendation
import dev.bee.beecode.app.RecommendationReason
import dev.bee.beecode.app.RunnerStatus
import dev.bee.beecode.app.StudyQueue
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.domain.DueUrgency
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.describeDue
import dev.bee.beecode.domain.formatIntervalDays
import kotlinx.datetime.Clock
import kotlin.math.ceil

private val DashboardShape = RoundedCornerShape(8.dp)
private const val RECOMMENDATION_COUNT = 3
internal const val BROWSE_ALL_NEW_TAG = "browse-all-new"

@Composable
internal fun StudyDashboardScreen(viewModel: StudyViewModel) {
    val queue by viewModel.queue.collectAsStateWithLifecycle()
    val statistics by viewModel.statistics.collectAsStateWithLifecycle()
    val recommendations by viewModel.recommendations.collectAsStateWithLifecycle()
    val runnerStatus by viewModel.runnerStatus.collectAsStateWithLifecycle()
    val showMotivation by viewModel.showStreaksAndAchievements.collectAsStateWithLifecycle()
    var showAllNew by remember { mutableStateOf(false) }
    val currentQueue = queue
    val stats = statistics
    val now = remember(currentQueue) { Clock.System.now() }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(QUEUE_LIST_TAG),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Column {
                Text(
                    "BeeCode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    "Study",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        if (stats != null) {
            item { MetricStrip(stats, showMotivation) }
        }

        runnerStatus?.takeIf { !it.available }?.let { status ->
            item { RunnerUnavailableCard(status) }
        }

        if (currentQueue == null || stats == null) {
            item { DashboardLoading() }
        } else {
            item {
                StudyHero(
                    queue = currentQueue,
                    onStart = viewModel::startStudy,
                )
            }

            item {
                ActivityCard(
                    activity = stats.activityCalendar,
                    statistics = stats,
                    showMotivation = showMotivation,
                )
            }

            item {
                TodayCard(
                    reviews = currentQueue.dueTopics.size,
                    recommendedProblems = recommendations
                        .size
                        .coerceAtMost(RECOMMENDATION_COUNT),
                    onReviews = viewModel::startStudy,
                    onNew = viewModel::startNewProblem,
                )
            }

            if (currentQueue.dueTopics.isNotEmpty()) {
                item { DashboardSectionHeader("Techniques to review", currentQueue.dueTopics.size) }
                items(currentQueue.dueTopics, key = { "review-${it.topic}" }) { due ->
                    ReviewCard(due, now) { viewModel.openProblem(due.problem.id) }
                }
            }

            if (recommendations.isNotEmpty()) {
                item { DashboardSectionHeader("Recommended for you", null) }
                items(
                    recommendations.take(RECOMMENDATION_COUNT),
                    key = { "recommended-${it.problem.id.value}" },
                ) { recommendation ->
                    RecommendationCard(recommendation) {
                        viewModel.openProblem(recommendation.problem.id)
                    }
                }
            }

            if (currentQueue.new.size > RECOMMENDATION_COUNT) {
                item {
                    OutlinedButton(
                        onClick = { showAllNew = !showAllNew },
                        modifier = Modifier.fillMaxWidth().testTag(BROWSE_ALL_NEW_TAG),
                        shape = DashboardShape,
                    ) {
                        Text(
                            if (showAllNew) {
                                "Hide all new Problems"
                            } else {
                                "Browse all ${currentQueue.new.size} new Problems"
                            },
                        )
                    }
                }
            }

            if (showAllNew) {
                item { DashboardSectionHeader("All new Problems", currentQueue.new.size) }
                items(currentQueue.new, key = { "all-${it.id.value}" }) { problem ->
                    NewProblemRow(problem) { viewModel.openProblem(problem.id) }
                }
            }

            if (currentQueue.isEmpty) {
                item { CompleteCatalogueCard() }
            }
        }
    }
}

@Composable
private fun MetricStrip(stats: StudyStatistics, showMotivation: Boolean) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = buildString {
                    append("Study summary: ")
                    if (showMotivation) append("${stats.currentStreakDays} day streak, ")
                    append("${stats.distinctProblemsSolved} solved, ${stats.totalReviews} reviews")
                }
            },
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (showMotivation) {
                Metric(
                    value = "${stats.currentStreakDays}",
                    label = "day streak",
                    color = accentCaution(),
                )
                MetricDivider()
            }
            Metric(
                value = "${stats.distinctProblemsSolved}",
                label = "solved",
                color = accentSuccess(),
            )
            MetricDivider()
            Metric(
                value = "${stats.totalReviews}",
                label = "reviews",
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun Metric(value: String, label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = color,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricDivider() {
    Box(
        Modifier
            .width(1.dp)
            .height(38.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun StudyHero(queue: StudyQueue, onStart: () -> Unit) {
    val due = queue.dueTopics.size
    val canStart = queue.totalAvailable > 0
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = amber().copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, amber().copy(alpha = 0.45f)),
                ) {
                    Icon(
                        Icons.AutoMirrored.Outlined.List,
                        contentDescription = null,
                        tint = amber(),
                        modifier = Modifier.padding(12.dp).size(26.dp),
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column {
                    Text(
                        if (canStart) "Ready to study?" else "All caught up",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        when {
                            due > 0 -> "$due ${reviewWord(due)} due"
                            queue.new.isNotEmpty() -> "Start with a recommended new Problem"
                            else -> "Every Problem in this pack has been practised"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Button(
                onClick = onStart,
                enabled = canStart,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(6.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = amber(),
                    contentColor = Color(LocalBeeCodePalette.current.onPrimaryFixed),
                ),
            ) {
                Text(
                    if (due > 0) "Start Study" else "Start a new Problem",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ActivityCard(
    activity: List<DailyActivity>,
    statistics: StudyStatistics,
    showMotivation: Boolean,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                "Study activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ActivityHeatmap(activity)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (showMotivation) {
                    ActivityFact("${statistics.currentStreakDays} day streak", accentCaution())
                }
                ActivityFact("${statistics.distinctProblemsSolved} solved", accentSuccess())
                ActivityFact("${statistics.totalReviews} reviews", MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun ActivityHeatmap(activity: List<DailyActivity>) {
    val weeks = remember(activity) { calendarWeeks(activity) }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val visibleWeeks = ((maxWidth.value + 3f) / 13f).toInt().coerceIn(1, weeks.size)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            weeks.takeLast(visibleWeeks).forEach { week ->
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    week.forEach { day ->
                        val count = day?.reviews ?: 0
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(
                                    heatColor(count),
                                    RoundedCornerShape(2.dp),
                                )
                                .semantics {
                                    if (day != null) {
                                        contentDescription =
                                            "${day.date}: $count ${reviewWord(count)}"
                                    }
                                },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ActivityFact(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodayCard(
    reviews: Int,
    recommendedProblems: Int,
    onReviews: () -> Unit,
    onNew: () -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column {
            Text(
                "Today",
                modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TodayRow(
                label = "Reviews",
                count = reviews,
                enabled = reviews > 0,
                onClick = onReviews,
            )
            HorizontalDivider(Modifier.padding(horizontal = 16.dp))
            TodayRow(
                label = "New Problems",
                count = recommendedProblems,
                enabled = recommendedProblems > 0,
                onClick = onNew,
            )
        }
    }
}

@Composable
private fun TodayRow(
    label: String,
    count: Int,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (label == "Reviews") {
                Icons.Outlined.CheckCircle
            } else {
                Icons.AutoMirrored.Outlined.List
            },
            contentDescription = null,
            tint = if (enabled) amber() else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(12.dp))
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text("$count", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(8.dp))
        Icon(
            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ReviewCard(due: DueTopic, now: kotlinx.datetime.Instant, onClick: () -> Unit) {
    val dueDescription = describeDue(due.schedule.dueAt, now)
    val dueColor = when (dueDescription.urgency) {
        DueUrgency.OVERDUE -> accentDanger()
        DueUrgency.DUE -> amber()
        DueUrgency.UPCOMING -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    due.displayName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    dueDescription.label,
                    style = MaterialTheme.typography.labelSmall,
                    color = dueColor,
                )
            }
            Text(
                buildString {
                    append("Memory lasts about ${formatIntervalDays(due.schedule.intervalDays)}")
                    append(" \u00b7 reviewed ${due.schedule.reviewCount}\u00d7")
                    if (due.schedule.lapseCount > 0) {
                        append(" \u00b7 ${due.schedule.lapseCount} forgotten")
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "${due.problem.title} \u00b7 ${due.attemptedMemberProblems} of " +
                    "${due.memberProblems} practised",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: ProblemRecommendation,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(
                    recommendation.problem.title,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                DashboardDifficultyBadge(recommendation.problem.difficulty)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                recommendation.displayTopics.forEach { topic ->
                    TopicChip(topic.replace('-', ' ').replaceFirstChar { it.uppercaseChar() })
                }
            }
            Text(
                "Focus: ${recommendation.focusName}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SegmentedProgress(recommendation.progressFraction)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    recommendation.reason.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    recommendation.detail,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun SegmentedProgress(fraction: Float) {
    val filled = ceil(fraction.coerceIn(0f, 1f) * 5).toInt()
    Row(
        Modifier.fillMaxWidth().semantics {
            contentDescription = "${(fraction.coerceIn(0f, 1f) * 100).toInt()} percent"
        },
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        repeat(5) { index ->
            Box(
                Modifier
                    .weight(1f)
                    .height(7.dp)
                    .background(
                        if (index < filled) amber() else MaterialTheme.colorScheme.surfaceContainerHighest,
                        RoundedCornerShape(3.dp),
                    ),
            )
        }
    }
}

@Composable
private fun TopicChip(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(4.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun NewProblemRow(problem: ProblemDefinition, onClick: () -> Unit) {
    OutlinedCard(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    problem.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    problem.topics.take(3).joinToString(" / ") {
                        it.replace('-', ' ')
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            DashboardDifficultyBadge(problem.difficulty)
        }
    }
}

@Composable
private fun DashboardDifficultyBadge(difficulty: ProblemDifficulty) {
    val (label, color) = when (difficulty) {
        ProblemDifficulty.EASY -> "Easy" to accentSuccess()
        ProblemDifficulty.MEDIUM -> "Medium" to accentCaution()
        ProblemDifficulty.HARD -> "Hard" to accentDanger()
    }
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(5.dp),
        border = BorderStroke(1.dp, color),
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            style = MaterialTheme.typography.labelSmall,
            color = color,
        )
    }
}

@Composable
private fun DashboardSectionHeader(title: String, count: Int?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        count?.let {
            Spacer(Modifier.width(8.dp))
            Text(
                "$it",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun RunnerUnavailableCard(status: RunnerStatus) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
        ),
        shape = DashboardShape,
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

@Composable
private fun DashboardLoading() {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 40.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        CircularProgressIndicator(Modifier.size(28.dp))
    }
}

@Composable
private fun CompleteCatalogueCard() {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Catalogue complete", style = MaterialTheme.typography.titleMedium)
            Text(
                "Every Problem has been practised. Scheduled reviews will appear here when due.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val RecommendationReason.label: String
    get() = when (this) {
        RecommendationReason.NEEDS_RECALL_PRACTICE -> "Needs recall practice"
        RecommendationReason.NEW_TOPIC -> "New topic"
        RecommendationReason.BUILD_COVERAGE -> "Build coverage"
    }

private val ProblemRecommendation.detail: String
    get() = when (reason) {
        RecommendationReason.NEEDS_RECALL_PRACTICE ->
            "${((focus?.recallRate ?: 0.0) * 100).toInt()}% recall"
        RecommendationReason.NEW_TOPIC -> "Not practised yet"
        RecommendationReason.BUILD_COVERAGE ->
            "${focus?.attemptedMemberProblems ?: 0} of ${focus?.memberProblems ?: 0} practised"
    }

private val ProblemRecommendation.displayTopics: List<String>
    get() = buildList {
        focus?.topic?.let(::add)
        addAll(problem.topics)
    }.distinct().take(2)

private fun calendarWeeks(activity: List<DailyActivity>): List<List<DailyActivity?>> {
    if (activity.isEmpty()) return listOf(List(7) { null })
    val padded = buildList<DailyActivity?> {
        repeat(activity.first().date.dayOfWeek.ordinal) { add(null) }
        addAll(activity)
        repeat(6 - activity.last().date.dayOfWeek.ordinal) { add(null) }
    }
    return padded.chunked(7)
}

@Composable
private fun heatColor(reviews: Int): Color {
    if (reviews == 0) return MaterialTheme.colorScheme.surfaceContainerHighest
    val alpha = when {
        reviews == 1 -> 0.35f
        reviews <= 3 -> 0.58f
        reviews <= 6 -> 0.78f
        else -> 1f
    }
    return amber().copy(alpha = alpha)
}

@Composable
private fun amber(): Color = Color(LocalBeeCodePalette.current.primaryFixedDim)

@Composable
private fun dashboardCardColors() = CardDefaults.outlinedCardColors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
)

@Composable
private fun dashboardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

private fun reviewWord(count: Int): String = if (count == 1) "review" else "reviews"
