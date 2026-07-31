package dev.bee.beecode.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Hive
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.DailyActivity
import dev.bee.beecode.app.DueTopic
import dev.bee.beecode.app.ProblemRecommendation
import dev.bee.beecode.app.RecommendationReason
import dev.bee.beecode.app.RunnerStatus
import dev.bee.beecode.app.StudyQueue
import dev.bee.beecode.app.StudyRecommendations
import dev.bee.beecode.app.StudyStatistics
import dev.bee.beecode.domain.DueUrgency
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.describeDue
import dev.bee.beecode.domain.formatIntervalDays
import kotlinx.datetime.Clock
import kotlin.math.ceil

private val DashboardShape = RoundedCornerShape(8.dp)
private const val RECOMMENDATION_COUNT = 3
internal const val BROWSE_ALL_NEW_TAG = "browse-all-new"

@Composable
internal fun DesktopSidebar(
    studySelected: Boolean,
    progressSelected: Boolean,
    settingsSelected: Boolean,
    showProgress: Boolean,
    onStudy: () -> Unit,
    onProgress: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .width(196.dp)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .padding(14.dp),
    ) {
        Row(
            Modifier.padding(horizontal = 10.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.Hive,
                contentDescription = null,
                tint = amber(),
                modifier = Modifier.size(30.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "BeeCode",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(18.dp))
        SidebarItem(
            label = "Study",
            icon = Icons.AutoMirrored.Outlined.List,
            selected = studySelected,
            onClick = onStudy,
        )
        if (showProgress) {
            Spacer(Modifier.height(6.dp))
            SidebarItem(
                label = "Progress",
                icon = Icons.Outlined.CheckCircle,
                selected = progressSelected,
                onClick = onProgress,
            )
        }
        Spacer(Modifier.weight(1f))
        HorizontalDivider()
        Spacer(Modifier.height(10.dp))
        SidebarItem(
            label = "Settings",
            icon = Icons.Outlined.Settings,
            selected = settingsSelected,
            onClick = onSettings,
        )
    }
}

@Composable
private fun SidebarItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(7.dp)
    val selectedColor = Color(LocalBeeCodePalette.current.primaryFixed).copy(alpha = 0.45f)
    Row(
        Modifier
            .fillMaxWidth()
            .background(
                if (selected) selectedColor else Color.Transparent,
                shape,
            )
            .then(
                if (selected) {
                    Modifier.border(1.dp, amber().copy(alpha = 0.65f), shape)
                } else {
                    Modifier
                },
            )
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
internal fun DesktopStudyDashboard(
    profile: BeeCodeProfile,
    refreshToken: Int,
    runnerStatus: RunnerStatus?,
    showMotivation: Boolean,
    onOpen: (ProblemId) -> Unit,
    onStartStudy: (ProblemId, Boolean) -> Unit,
) {
    val queue = remember(refreshToken) { profile.study.queue() }
    val statistics = remember(refreshToken) { profile.statistics() }
    val recommendations = remember(refreshToken) {
        StudyRecommendations.rank(queue.new, profile.topicMastery())
    }
    val now = remember(refreshToken) { Clock.System.now() }
    var showAllNew by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().testTag(QUEUE_LIST_TAG),
        contentPadding = PaddingValues(horizontal = 32.dp, vertical = 26.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 760.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        DashboardTitle()
                        MetricStrip(statistics, showMotivation)
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DashboardTitle(Modifier.weight(1f).padding(end = 24.dp))
                        MetricStrip(
                            statistics,
                            showMotivation,
                            Modifier.widthIn(min = 390.dp, max = 520.dp),
                        )
                    }
                }
            }
        }

        runnerStatus?.takeIf { !it.available }?.let { status ->
            item { RunnerUnavailableCard(status) }
        }

        item {
            StudyHero(queue, recommendations) { id, continueReviews ->
                onStartStudy(id, continueReviews)
            }
        }

        item {
            BoxWithConstraints(Modifier.fillMaxWidth()) {
                if (maxWidth < 760.dp) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ActivityCard(statistics, showMotivation)
                        TodayCard(
                            queue = queue,
                            recommendations = recommendations,
                            onStartStudy = onStartStudy,
                        )
                    }
                } else {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        ActivityCard(
                            statistics = statistics,
                            showMotivation = showMotivation,
                            modifier = Modifier.weight(1.65f),
                        )
                        TodayCard(
                            queue = queue,
                            recommendations = recommendations,
                            onStartStudy = onStartStudy,
                            modifier = Modifier.weight(0.85f),
                        )
                    }
                }
            }
        }

        if (queue.dueTopics.isNotEmpty()) {
            item { DashboardSectionHeader("Techniques to review", queue.dueTopics.size) }
            items(queue.dueTopics, key = { "review-${it.topic}" }) { due ->
                ReviewCard(due, now) { onOpen(due.problem.id) }
            }
        }

        if (recommendations.isNotEmpty()) {
            item { DashboardSectionHeader("Recommended for you", null) }
            item {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth < 860.dp) {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            recommendations.take(RECOMMENDATION_COUNT).forEach { recommendation ->
                                RecommendationCard(
                                    recommendation = recommendation,
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    onOpen(recommendation.problem.id)
                                }
                            }
                        }
                    } else {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(14.dp),
                        ) {
                            recommendations.take(RECOMMENDATION_COUNT).forEach { recommendation ->
                                RecommendationCard(
                                    recommendation = recommendation,
                                    modifier = Modifier.weight(1f).heightIn(min = 200.dp),
                                ) {
                                    onOpen(recommendation.problem.id)
                                }
                            }
                        }
                    }
                }
            }
        }

        if (queue.new.size > RECOMMENDATION_COUNT) {
            item {
                OutlinedButton(
                    onClick = { showAllNew = !showAllNew },
                    modifier = Modifier.testTag(BROWSE_ALL_NEW_TAG),
                    shape = DashboardShape,
                ) {
                    Text(
                        if (showAllNew) {
                            "Hide all new Problems"
                        } else {
                            "Browse all ${queue.new.size} new Problems"
                        },
                    )
                }
            }
        }

        if (showAllNew) {
            item { DashboardSectionHeader("All new Problems", queue.new.size) }
            items(queue.new, key = { "all-${it.id.value}" }) { problem ->
                NewProblemRow(problem) { onOpen(problem.id) }
            }
        }

        if (queue.isEmpty) {
            item { CompleteCatalogueCard() }
        }
    }
}

@Composable
private fun DashboardTitle(modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(
            "Study",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            "Focused practice, one review at a time",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun MetricStrip(
    stats: StudyStatistics,
    showMotivation: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedCard(
        modifier = modifier.semantics {
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
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
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
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            value,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(7.dp))
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
            .height(32.dp)
            .background(MaterialTheme.colorScheme.outlineVariant),
    )
}

@Composable
private fun StudyHero(
    queue: StudyQueue,
    recommendations: List<ProblemRecommendation>,
    onStart: (ProblemId, Boolean) -> Unit,
) {
    val due = queue.dueTopics.firstOrNull()
    val next = due?.problem?.id ?: recommendations.firstOrNull()?.problem?.id
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        BoxWithConstraints(Modifier.fillMaxWidth().padding(26.dp)) {
            val compact = maxWidth < 800.dp
            if (compact) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    HeroMessage(queue)
                    HeroAction(queue, next) {
                        next?.let { onStart(it, due != null) }
                    }
                }
            } else {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(28.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    HeroMessage(queue, Modifier.weight(1f))
                    HeroAction(queue, next, Modifier.widthIn(min = 240.dp, max = 340.dp)) {
                        next?.let { onStart(it, due != null) }
                    }
                }
            }
        }
    }
}

@Composable
private fun HeroMessage(queue: StudyQueue, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(50),
            color = amber().copy(alpha = 0.1f),
            border = BorderStroke(1.dp, amber().copy(alpha = 0.5f)),
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.List,
                contentDescription = null,
                tint = amber(),
                modifier = Modifier.padding(15.dp).size(30.dp),
            )
        }
        Spacer(Modifier.width(18.dp))
        Column {
            Text(
                if (queue.totalAvailable > 0) "Ready to study?" else "All caught up",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
            Text(
                when {
                    queue.dueTopics.isNotEmpty() ->
                        "${queue.dueTopics.size} ${reviewWord(queue.dueTopics.size)} due"
                    queue.new.isNotEmpty() -> "Start with a recommended new Problem"
                    else -> "Every Problem in this pack has been practised"
                },
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun HeroAction(
    queue: StudyQueue,
    next: ProblemId?,
    modifier: Modifier = Modifier.fillMaxWidth(),
    onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Button(
            onClick = onClick,
            enabled = next != null,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(6.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = amber(),
                contentColor = Color(LocalBeeCodePalette.current.onPrimaryFixed),
            ),
        ) {
            Text(
                if (queue.dueTopics.isNotEmpty()) "Start Study" else "Start a new Problem",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun ActivityCard(
    statistics: StudyStatistics,
    showMotivation: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    OutlinedCard(
        modifier = modifier,
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(
                "Study activity",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            ActivityHeatmap(statistics.activityCalendar)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(28.dp),
            ) {
                if (showMotivation) {
                    ActivityFact(
                        "${statistics.currentStreakDays} day streak",
                        accentCaution(),
                    )
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
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(
            modifier = Modifier.width(12.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                Text(
                    day,
                    modifier = Modifier.height(12.dp),
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontSize = 10.sp,
                        lineHeight = 12.sp,
                    ),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        BoxWithConstraints(Modifier.weight(1f)) {
            val visibleWeeks = ((maxWidth.value + 3f) / 13f)
                .toInt()
                .coerceIn(1, minOf(52, weeks.size))
            Row(
                Modifier.fillMaxWidth(),
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
                                    .height(12.dp)
                                    .background(heatColor(count), RoundedCornerShape(2.dp))
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
}

@Composable
private fun ActivityFact(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(7.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TodayCard(
    queue: StudyQueue,
    recommendations: List<ProblemRecommendation>,
    onStartStudy: (ProblemId, Boolean) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth(),
) {
    val due = queue.dueTopics.firstOrNull()
    val nextNew = recommendations.firstOrNull()?.problem?.id
    val recommendedNewCount = recommendations.size.coerceAtMost(RECOMMENDATION_COUNT)
    OutlinedCard(
        modifier = modifier,
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column {
            Text(
                "Today",
                modifier = Modifier.padding(start = 18.dp, top = 18.dp, bottom = 8.dp),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            TodayRow(
                label = "Reviews",
                count = queue.dueTopics.size,
                enabled = due != null,
            ) {
                due?.let { onStartStudy(it.problem.id, true) }
            }
            HorizontalDivider(Modifier.padding(horizontal = 18.dp))
            TodayRow(
                label = "New Problems",
                count = recommendedNewCount,
                enabled = nextNew != null,
            ) {
                nextNew?.let { onStartStudy(it, false) }
            }
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
            .padding(horizontal = 18.dp, vertical = 15.dp),
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
        Spacer(Modifier.width(9.dp))
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
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    due.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
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
            Text(
                dueDescription.label,
                style = MaterialTheme.typography.labelSmall,
                color = dueColor,
            )
        }
    }
}

@Composable
private fun RecommendationCard(
    recommendation: ProblemRecommendation,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    OutlinedCard(
        onClick = onClick,
        modifier = modifier,
        shape = DashboardShape,
        colors = dashboardCardColors(),
        border = dashboardBorder(),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(11.dp)) {
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
            Spacer(Modifier.weight(1f))
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
                        if (index < filled) amber()
                        else MaterialTheme.colorScheme.surfaceContainerHighest,
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
            overflow = TextOverflow.Ellipsis,
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
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(problem.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    problem.topics.take(3).joinToString(" / ") { it.replace('-', ' ') },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
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
            Text(
                status.unavailableReason ?: "BeeCode could not start Python.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
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
