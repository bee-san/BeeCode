package dev.bee.beecode.app

import dev.bee.beecode.domain.AchievementId
import dev.bee.beecode.domain.IdGenerator
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.persistence.ActivityOutboxRepository
import dev.bee.beecode.persistence.BeeCodeDatabase
import dev.bee.beecode.persistence.DraftRepository
import dev.bee.beecode.persistence.ReviewRepository
import dev.bee.beecode.persistence.SettingsRepository
import dev.bee.beecode.fsrs.BeeCodeScheduler
import dev.bee.beecode.python.PythonRunner
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import java.io.Closeable

/**
 * One learner's complete local study environment.
 *
 * The single object a client constructs. It owns the database and closes it, so a
 * client's lifecycle code is "open a profile, use it, close it" rather than wiring
 * six collaborators in the right order.
 *
 * Everything here works offline with no account. That is not a feature flag; there
 * is no code path that contacts a network.
 */
class BeeCodeProfile private constructor(
    private val database: BeeCodeDatabase,
    val catalogue: ProblemCatalogue,
    val study: StudyService,
    val reviews: ReviewRepository,
    val drafts: DraftRepository,
    val settings: SettingsRepository,
    val activityOutbox: ActivityOutboxRepository,
    /**
     * The mathematics this profile schedules with.
     *
     * Kept private on purpose. A public accessor would let a client build a second
     * scheduler from a different policy and run two mathematics in one session, which
     * is exactly what reading the policy once at open (see [Companion.build]) exists
     * to prevent.
     */
    private val scheduler: BeeCodeScheduler,
    private val clock: Clock,
) : Closeable {

    /** Statistics over the learner's whole history. */
    fun statistics(): StudyStatistics {
        val now = clock.now()
        val zone = settings.streakZone()
        return Statistics.compute(
            reviews = allReviews(),
            schedules = reviews.schedules(),
            problems = catalogue.allProblems(),
            today = now.dateIn(zone),
            now = now,
        )
    }

    /**
     * The learner's remembering of each technique.
     *
     * The topic-level answer to "how am I doing", and the counterpart to the
     * topic-level queue: what falls due is a technique, so what is reported is a
     * technique.
     */
    fun topicMastery(): TopicMasteryProjection = TopicMastery.compute(
        reviews = allReviews(),
        topicSchedules = reviews.topicSchedules(),
        problems = catalogue.allProblems(),
        now = clock.now(),
        // The scheduler's own retention target, used as the shrinkage prior when the
        // learner has no history at all. A stated number rather than an invented one.
        desiredRetention = scheduler.desiredRetention,
    )

    /**
     * Recompute every topic schedule from the review log and the pack's current tags.
     *
     * Needed after a restore or a sync merge, and after a pack update that retags
     * Problems. Cheap to run and safe to repeat: it is a fold over the log, so it has
     * no state of its own to get out of step.
     *
     * @return how many topics have a schedule afterwards.
     */
    fun rebuildTopicSchedules(): Int {
        val rebuilt = reviews.rebuildTopicSchedulesFromHistory { id ->
            catalogue.problem(id)?.topics ?: emptyList()
        }
        reviews.replaceTopicSchedules(rebuilt)
        return rebuilt.size
    }

    /**
     * Queue any Leaderboard activity the review log has produced but not yet shared.
     *
     * Called by a client after finalizing, and again on launch. Deliberately *not* wired
     * into `StudyService.finalize`: the review path must not acquire a Leaderboard
     * dependency, because "review finalization never waits for network" is only credible if
     * finalization cannot fail for a Leaderboard reason. This runs after the commit, reads
     * the durable log, and its worst failure is a delayed count.
     *
     * A no-op when the Leaderboard is off, which is the default — so a client can call it
     * unconditionally rather than checking first.
     *
     * @return how many events were newly queued, or 0 when there is nothing to share.
     */
    fun refreshLeaderboardActivity(now: Instant = clock.now()): Int {
        val linkedAt = settings.leaderboardLinkedAt() ?: return 0
        return LeaderboardService(this).refresh(linkedAt = linkedAt, now = now).eventsAdded
    }

    /** Achievement state, projected fresh from the review log. */
    fun achievements(): AchievementProjection = Achievements.project(allReviews())

    fun achievement(id: AchievementId): AchievementState? = achievements().state(id)

    /**
     * Every review, for projection and statistics.
     *
     * Reads the whole log. That is affordable because a review is a few hundred
     * bytes and a dedicated learner produces a few thousand a year; recomputing
     * from the source of truth is worth more than the milliseconds a cache saves.
     */
    fun allReviews(): List<dev.bee.beecode.domain.ProblemReviewFinalized> =
        reviews.recentReviews(limit = ALL_REVIEWS_LIMIT)

    /** The learner's history for one Problem, oldest first. */
    fun history(problemId: ProblemId) = reviews.reviewHistory(problemId)

    /**
     * Verify that replaying the review log reproduces the stored schedules.
     *
     * An integrity check, not part of the study path. It is the same fold a sync
     * merge would use (ADR 0002), so running it locally is how that path stays
     * trustworthy before sync exists.
     */
    fun verifyScheduleIntegrity(): List<ProblemId> {
        val rebuilt = reviews.rebuildSchedulesFromHistory()
        return rebuilt.filter { (id, expected) ->
            val stored = reviews.schedule(id)
            stored == null ||
                stored.stability != expected.stability ||
                stored.difficulty != expected.difficulty ||
                stored.dueAt != expected.dueAt
        }.keys.toList()
    }

    /**
     * Verify that replaying the log through current tags reproduces the stored topic
     * schedules.
     *
     * The topic counterpart of [verifyScheduleIntegrity], and the check that keeps the
     * topic projection trustworthy: if incremental fan-out and a full replay ever
     * disagree, a learner's due dates are being computed by two different rules.
     *
     * A topic whose Problems have been retagged since will legitimately differ — a
     * projection over mutable metadata cannot claim otherwise — so this is a
     * diagnostic to read alongside [rebuildTopicSchedules] rather than an alarm.
     *
     * @return the topics whose stored state does not match a fresh replay.
     */
    fun verifyTopicScheduleIntegrity(): List<String> {
        val rebuilt = reviews.rebuildTopicSchedulesFromHistory { id ->
            catalogue.problem(id)?.topics ?: emptyList()
        }
        val stored = reviews.topicSchedules()
        return rebuilt.filter { (topic, expected) ->
            val actual = stored[topic]
            actual == null ||
                actual.stability != expected.stability ||
                actual.difficulty != expected.difficulty ||
                actual.dueAt != expected.dueAt
        }.keys.toList().sorted()
    }

    override fun close() = database.close()

    companion object {
        /**
         * A cap high enough to be irrelevant in practice while still bounding a
         * pathological read.
         */
        const val ALL_REVIEWS_LIMIT: Int = 1_000_000

        /**
         * Open a profile backed by a database file.
         *
         * @param databasePath where study state lives. The caller supplies it
         *   because the right location is platform-specific: app-private storage on
         *   Android, a user data directory on desktop.
         */
        fun open(
            databasePath: String,
            catalogue: ProblemCatalogue,
            runner: PythonRunner,
            ids: IdGenerator = UuidIdGenerator(),
            clock: Clock = Clock.System,
        ): BeeCodeProfile = build(BeeCodeDatabase.open(databasePath), catalogue, runner, ids, clock)

        /** An in-memory profile, for tests. */
        fun inMemory(
            catalogue: ProblemCatalogue,
            runner: PythonRunner,
            ids: IdGenerator = UuidIdGenerator(),
            clock: Clock = Clock.System,
        ): BeeCodeProfile = build(BeeCodeDatabase.inMemory(), catalogue, runner, ids, clock)

        private fun build(
            database: BeeCodeDatabase,
            catalogue: ProblemCatalogue,
            runner: PythonRunner,
            ids: IdGenerator,
            clock: Clock,
        ): BeeCodeProfile {
            val settings = SettingsRepository(database)
            // The learner's own FSRS parameters, if they have set any. Read once at
            // open: changing them mid-session would make two reviews in the same
            // sitting use different mathematics.
            val scheduler = BeeCodeScheduler(settings.schedulerPolicy())
            val reviews = ReviewRepository(database, scheduler)
            val drafts = DraftRepository(database)
            val activityOutbox = ActivityOutboxRepository(database)
            return BeeCodeProfile(
                database = database,
                catalogue = catalogue,
                study = StudyService(
                    catalogue = catalogue,
                    drafts = drafts,
                    reviews = reviews,
                    settings = settings,
                    runner = runner,
                    ids = ids,
                    clock = clock,
                ),
                reviews = reviews,
                drafts = drafts,
                settings = settings,
                activityOutbox = activityOutbox,
                scheduler = scheduler,
                clock = clock,
            )
        }
    }
}
