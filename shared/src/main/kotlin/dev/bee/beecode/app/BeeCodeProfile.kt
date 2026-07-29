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
    private val clock: Clock,
) : Closeable {

    /** Statistics over the learner's whole history. */
    fun statistics(): StudyStatistics {
        val now = clock.now()
        val zone = settings.streakZone()
        return Statistics.compute(
            reviews = allReviews(),
            schedules = reviews.scheduledProblemIds().mapNotNull { id ->
                reviews.schedule(id)?.let { id to it }
            }.toMap(),
            problems = catalogue.allProblems(),
            today = now.dateIn(zone),
            now = now,
        )
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
                clock = clock,
            )
        }
    }
}
