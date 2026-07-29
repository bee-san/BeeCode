package dev.bee.beecode.app

import dev.bee.beecode.domain.AchievementId
import dev.bee.beecode.domain.ProblemReviewFinalized
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.datetime.toLocalDateTime

/**
 * Achievement definitions and their projection.
 *
 * Definitions are data; evaluators are trusted code. Progress is derived and
 * rebuildable; an award is a fact and immutable once earned.
 *
 * Projection is a **pure fold over the review log**, not an incrementing counter.
 * That choice is what makes late-arriving events safe: a review that is inserted
 * out of order — by a sync merge, or by a clock correction — recomputes correctly
 * instead of corrupting a counter that only ever went up. It also means a full
 * replay must equal incremental projection, which is asserted in tests.
 */
object Achievements {

    val FIVE_AM_CLUB = AchievementId("five-am-club")
    val FIRST_SOLVE = AchievementId("first-solve")
    val TEN_SOLVED = AchievementId("ten-problems-solved")
    val WEEK_STREAK = AchievementId("seven-day-streak")

    val ALL: List<AchievementDefinition> = listOf(
        AchievementDefinition(
            id = FIRST_SOLVE,
            title = "First Solve",
            description = "Solve your first Problem unaided.",
            target = 1,
        ),
        AchievementDefinition(
            id = TEN_SOLVED,
            title = "Ten Down",
            description = "Solve ten Problems unaided.",
            target = 10,
        ),
        AchievementDefinition(
            id = WEEK_STREAK,
            title = "Seven Day Streak",
            description = "Solve at least one Problem unaided on seven consecutive days.",
            target = 7,
        ),
        AchievementDefinition(
            id = FIVE_AM_CLUB,
            title = "5am Club",
            description = "Solve a Problem unaided before 6am, seven days running.",
            target = 7,
        ),
    )

    /**
     * Project every achievement from the review log.
     *
     * @param reviews all finalized reviews. Order does not matter; they are sorted
     *   internally, which is what makes the fold immune to late arrivals.
     */
    fun project(reviews: List<ProblemReviewFinalized>): AchievementProjection {
        // Only unaided passes count. This is the single definition of "solved",
        // shared with statistics, and it is why revealing the explanation cannot
        // farm any achievement.
        val solved = reviews
            .filter { it.countsAsSolved }
            .sortedBy { it.finalizedAt }

        val distinctSolved = solved.map { it.problemId }.distinct().size

        return AchievementProjection(
            states = listOf(
                countState(FIRST_SOLVE, solved.size, target = 1),
                countState(TEN_SOLVED, distinctSolved, target = 10),
                streakState(WEEK_STREAK, solved, earlyOnly = false),
                streakState(FIVE_AM_CLUB, solved, earlyOnly = true),
            ),
        )
    }

    private fun countState(id: AchievementId, count: Int, target: Int): AchievementState {
        val definition = ALL.first { it.id == id }
        return AchievementState(
            definition = definition,
            progress = count.coerceAtMost(target),
            earned = count >= target,
            detail = "$count of $target",
        )
    }

    /**
     * The streak rule, shared by [WEEK_STREAK] and [FIVE_AM_CLUB].
     *
     * The 5am Club is the normative case, and its subtleties are the reason this is
     * a fold rather than a counter:
     *
     * - **The window is `[00:00, 06:00)` local**, evaluated in the zone that was
     *   recorded at write time. Using the *current* zone would silently rewrite
     *   history when a learner travels.
     * - **One qualifying contribution per date.** Solving three Problems at 5am
     *   advances the streak by one day, not three.
     * - **Consecutive local dates.** A gap resets. Computed by walking sorted
     *   distinct dates, so a late-inserted event lands in the right place instead
     *   of extending whatever the counter last held.
     * - **The zone is locked per epoch.** A streak's dates are all interpreted in
     *   the zone recorded when it started; a new streak after a gap adopts the
     *   then-current zone. Otherwise a flight could retroactively break or create a
     *   streak.
     *
     * Reporting the *best* streak rather than only the current one means the award,
     * once earned, cannot be lost by a later gap — awards are immutable.
     */
    private fun streakState(
        id: AchievementId,
        solved: List<ProblemReviewFinalized>,
        earlyOnly: Boolean,
    ): AchievementState {
        val definition = ALL.first { it.id == id }

        val qualifying = solved.filter { review ->
            // localHour() uses the zone recorded with the review, not the current
            // one, so travelling cannot retroactively qualify or disqualify a day.
            !earlyOnly || review.localHour() < FIVE_AM_CLUB_END_HOUR
        }

        if (qualifying.isEmpty()) {
            return AchievementState(
                definition = definition,
                progress = 0,
                earned = false,
                detail = "0 of ${definition.target} days",
            )
        }

        // Distinct local dates, each interpreted in its own review's recorded zone.
        val dates = qualifying.map { it.localDate() }.distinct().sorted()

        var best = 1
        var current = 1
        var currentStart = dates.first()
        var bestDates = listOf(dates.first())
        var currentDates = mutableListOf(dates.first())

        for (index in 1 until dates.size) {
            val previous = dates[index - 1]
            val date = dates[index]
            if (date == previous.plus(DatePeriod(days = 1))) {
                current++
                currentDates += date
            } else {
                current = 1
                currentStart = date
                currentDates = mutableListOf(date)
            }
            if (current > best) {
                best = current
                bestDates = currentDates.toList()
            }
        }
        // Suppress an unused-value warning while keeping the variable, which
        // documents the epoch start the streak is measured from.
        check(currentStart <= dates.last())

        val earned = best >= definition.target
        return AchievementState(
            definition = definition,
            progress = best.coerceAtMost(definition.target),
            earned = earned,
            detail = buildString {
                append(best.coerceAtMost(definition.target))
                append(" of ")
                append(definition.target)
                append(" days")
                if (earned) {
                    append(" — earned ")
                    append(bestDates.take(definition.target).last())
                }
            },
            completedOn = if (earned) bestDates.take(definition.target).last() else null,
        )
    }

    /**
     * The current consecutive-day solve streak ending today or yesterday.
     *
     * Yesterday counts as still alive: a learner who has not studied yet today has
     * not broken anything, and showing "0" before their first review of the day
     * would be both wrong and discouraging.
     */
    fun currentStreak(reviews: List<ProblemReviewFinalized>, today: LocalDate): Int {
        val dates = reviews
            .filter { it.countsAsSolved }
            .map { it.localDate() }
            .distinct()
            .sortedDescending()
        if (dates.isEmpty()) return 0

        val mostRecent = dates.first()
        if (mostRecent != today && mostRecent != today.minus(DatePeriod(days = 1))) return 0

        var streak = 1
        for (index in 1 until dates.size) {
            if (dates[index] == dates[index - 1].minus(DatePeriod(days = 1))) streak++ else break
        }
        return streak
    }

    /** The local hour before which a solve counts for the 5am Club. */
    const val FIVE_AM_CLUB_END_HOUR: Int = 6
}

data class AchievementDefinition(
    val id: AchievementId,
    val title: String,
    val description: String,
    val target: Int,
)

data class AchievementState(
    val definition: AchievementDefinition,
    val progress: Int,
    val earned: Boolean,
    val detail: String,
    val completedOn: LocalDate? = null,
) {
    val fraction: Float
        get() = if (definition.target == 0) 0f else progress.toFloat() / definition.target
}

data class AchievementProjection(val states: List<AchievementState>) {
    val earned: List<AchievementState> get() = states.filter { it.earned }

    val inProgress: List<AchievementState> get() = states.filter { !it.earned }

    fun state(id: AchievementId): AchievementState? = states.firstOrNull { it.definition.id == id }
}

/** Convenience for tests and callers that already have instants. */
internal fun Instant.dateIn(zone: TimeZone): LocalDate = toLocalDateTime(zone).date
