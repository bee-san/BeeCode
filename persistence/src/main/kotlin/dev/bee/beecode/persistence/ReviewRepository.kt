package dev.bee.beecode.persistence

import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.DomainEventId
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.FinalizationPlan
import dev.bee.beecode.domain.FsrsTransitionRecord
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ProblemSchedule
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSessionId
import dev.bee.beecode.domain.TopicSchedule
import dev.bee.beecode.domain.localDateIn
import dev.bee.beecode.domain.localHourIn
import dev.bee.beecode.fsrs.BeeCodeScheduler
import dev.bee.beecode.fsrs.ReplayEntry
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.sql.Connection
import java.sql.ResultSet

/**
 * Reads and writes reviews and schedules.
 *
 * The important method is [finalizeReview]. Everything else supports it or reads
 * what it wrote.
 */
class ReviewRepository(
    private val database: BeeCodeDatabase,
    private val scheduler: BeeCodeScheduler,
) {

    /**
     * Record a finalized review and advance the schedule, exactly once.
     *
     * This is the transaction the whole product's integrity rests on. It commits
     * all of its effects or none:
     *
     * 1. Check whether this session already finalized. If so, return that outcome
     *    unchanged — a retry after a crash, a double tap, or a resumed process
     *    must not review the Problem twice.
     * 2. Read the authoritative schedule *and its version* inside the write
     *    transaction.
     * 3. Compute the FSRS transition. Pure and fast, so doing it inside the
     *    transaction costs nothing and removes any window in which the state it
     *    was based on could change.
     * 4. Append the review row and update the schedule, conditional on the version
     *    still matching.
     * 5. Advance every topic the Problem is tagged with, each on its own
     *    compare-and-swap. One review of `median-two-sorted` rehearses `arrays`,
     *    `binary-search`, and `two-pointers`, so all three move together or none
     *    does — a review that advanced the Problem but not its topics would leave
     *    the topic projection permanently short by one.
     *
     * `BEGIN IMMEDIATE` takes the write lock before step 2, so two concurrent
     * finalizations cannot both read version N and both write version N+1.
     *
     * Achievement projection deliberately does **not** run here. It runs after the
     * commit, in its own idempotent transaction, so a broken reducer cannot roll
     * back a review or block study.
     *
     * @param topics the Problem's current topic tags, supplied by the caller. Passed
     *   in rather than looked up because the catalogue lives in `:shared`, and this
     *   module must not depend on it. Deliberately has no default: an empty list is
     *   a legitimate answer for an untagged Problem, so a default would make
     *   "untagged" and "the caller forgot" the same call, and the second would show
     *   up only as topics that never come due.
     * @param streakZone the profile's timezone, used to derive the local date and
     *   hour once, at write time. Stored rather than recomputed so a later
     *   timezone change cannot silently rewrite history.
     * @return the recorded outcome, and whether this call was the one that
     *   created it.
     */
    fun finalizeReview(
        plan: FinalizationPlan,
        eventId: DomainEventId,
        deviceId: DeviceId,
        finalizedAtInstant: Instant,
        streakZone: TimeZone,
        topics: List<String>,
    ): FinalizeOutcome = database.transaction { connection ->
        // Truncated once, up front, so the review, the schedule, and the FSRS audit
        // all agree with what the database will hold. Storing millisecond precision
        // while returning nanoseconds would make a reloaded review compare unequal
        // to the one just written.
        val finalizedAt = finalizedAtInstant.truncatedToMillis()
        // Step 1: idempotency. Keyed on the session, which is the identity of
        // "one scheduled attempt".
        readReview(connection, plan.sessionId)?.let { existing ->
            return@transaction FinalizeOutcome.AlreadyFinalized(existing)
        }

        // Step 2: the authoritative previous state, read under the write lock.
        val previous = readSchedule(connection, plan.problemId)

        // Step 3: the pure transition.
        val transition = scheduler.schedule(
            problemId = plan.problemId,
            previous = previous,
            rating = plan.rating,
            reviewedAt = finalizedAt,
        )

        // Step 4: the conditional write.
        val updated = writeSchedule(connection, transition.schedule, expectedVersion = previous?.version)
        if (!updated) {
            // Another review advanced this Problem between our read and our write.
            // With BEGIN IMMEDIATE this should be unreachable in-process; it stays
            // because the guarantee must not depend on that assumption holding for
            // every future caller, including a sync merge.
            throw ScheduleConflictException(
                problemId = plan.problemId,
                expectedVersion = previous?.version,
            )
        }

        val review = ProblemReviewFinalized(
            eventId = eventId,
            sessionId = plan.sessionId,
            problemId = plan.problemId,
            problemRevisionId = plan.problemRevisionId,
            executionRunId = plan.selectedRun.id,
            outcome = plan.selectedRun.outcome,
            rating = plan.rating,
            aided = plan.aided,
            countsAsSolved = plan.countsAsSolved,
            finalizedAt = finalizedAt,
            streakZoneId = streakZone.id,
            transition = transition.record,
            deviceId = deviceId,
        )
        writeReview(
            connection = connection,
            review = review,
            selectedSource = plan.selectedRun.source,
        )

        // Step 5: the topic cards. Deduplicated because a Problem tagged with the
        // same topic twice must not advance it twice — the second advance would see
        // its own write as the previous state and compound off it.
        val topicSchedules = topics.distinct().map { topic ->
            val previousTopic = readTopicSchedule(connection, topic)
            val topicTransition = scheduler.scheduleTopic(
                topic = topic,
                previous = previousTopic,
                rating = plan.rating,
                reviewedAt = finalizedAt,
            )
            val topicUpdated = writeTopicSchedule(
                connection,
                topicTransition.schedule,
                expectedVersion = previousTopic?.version,
            )
            if (!topicUpdated) {
                // Throwing rolls the whole transaction back, review row included.
                // Deliberate: the alternative is a review that counted for the
                // Problem but not for the technique, and nothing downstream could
                // detect the difference afterwards.
                throw TopicScheduleConflictException(
                    topic = topic,
                    expectedVersion = previousTopic?.version,
                )
            }
            topicTransition.schedule
        }

        FinalizeOutcome.Finalized(review, transition.schedule, topicSchedules)
    }

    fun schedule(problemId: ProblemId): ProblemSchedule? =
        database.read { readSchedule(it, problemId) }

    fun review(sessionId: ReviewSessionId): ProblemReviewFinalized? =
        database.read { readReview(it, sessionId) }

    /** Every review for one Problem, oldest first. */
    fun reviewHistory(problemId: ProblemId): List<ProblemReviewFinalized> = database.read { connection ->
        connection.prepareStatement(
            "SELECT * FROM problem_review WHERE problem_id = ? ORDER BY finalized_at ASC",
        ).use { statement ->
            statement.setString(1, problemId.value)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toReview()) }
            }
        }
    }

    /** Every review, newest first, for the history screen. */
    fun recentReviews(limit: Int): List<ProblemReviewFinalized> = database.read { connection ->
        connection.prepareStatement(
            "SELECT * FROM problem_review ORDER BY finalized_at DESC LIMIT ?",
        ).use { statement ->
            statement.setInt(1, limit)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toReview()) }
            }
        }
    }

    fun reviewCount(): Long = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT COUNT(*) FROM problem_review").use { rows ->
                if (rows.next()) rows.getLong(1) else 0L
            }
        }
    }

    /**
     * Problems due at or before [now], soonest first.
     *
     * Only returns Problems that have a schedule row; a Problem never reviewed has
     * no row and is offered by the *new* queue instead. Keeping those separate
     * means a learner with a large backlog is not blocked from starting something
     * new, and vice versa.
     */
    fun dueSchedules(now: Instant, limit: Int): List<ProblemSchedule> = database.read { connection ->
        connection.prepareStatement(
            "SELECT * FROM problem_schedule WHERE due_at <= ? ORDER BY due_at ASC LIMIT ?",
        ).use { statement ->
            statement.setLong(1, now.toEpochMilliseconds())
            statement.setInt(2, limit)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toSchedule()) }
            }
        }
    }

    fun topicSchedule(topic: String): TopicSchedule? =
        database.read { readTopicSchedule(it, topic) }

    /**
     * Topics due at or before [now], soonest first.
     *
     * The topic queue, and the whole reason the card sits on the technique: due order
     * *is* the answer to "what should I revise", with no weakness score in front of
     * it. A topic the learner keeps forgetting has low stability, so FSRS gives it a
     * short interval and it arrives here more often on its own.
     */
    fun dueTopicSchedules(now: Instant, limit: Int): List<TopicSchedule> = database.read { connection ->
        connection.prepareStatement(
            "SELECT * FROM topic_schedule WHERE due_at <= ? ORDER BY due_at ASC, topic ASC LIMIT ?",
        ).use { statement ->
            statement.setLong(1, now.toEpochMilliseconds())
            statement.setInt(2, limit)
            statement.executeQuery().use { rows ->
                buildList { while (rows.next()) add(rows.toTopicSchedule()) }
            }
        }
    }

    /** Every topic schedule, keyed by slug. */
    fun topicSchedules(): Map<String, TopicSchedule> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM topic_schedule").use { rows ->
                buildMap {
                    while (rows.next()) {
                        val schedule = rows.toTopicSchedule()
                        put(schedule.topic, schedule)
                    }
                }
            }
        }
    }

    /** IDs of every Problem that has ever been reviewed. */
    fun scheduledProblemIds(): Set<ProblemId> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT problem_id FROM problem_schedule").use { rows ->
                buildSet { while (rows.next()) add(ProblemId(rows.getString("problem_id"))) }
            }
        }
    }

    /**
     * Every per-Problem schedule, keyed by Problem.
     *
     * One read for the whole table, because the topic queue needs each candidate
     * member's `lastReviewedAt` to rotate between them and asking per member would
     * make the study path an N+1 over the topic's size. The table has one row per
     * Problem the learner has ever reviewed, so this is small by construction.
     */
    fun schedules(): Map<ProblemId, ProblemSchedule> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT * FROM problem_schedule").use { rows ->
                buildMap {
                    while (rows.next()) {
                        val schedule = rows.toSchedule()
                        put(schedule.problemId, schedule)
                    }
                }
            }
        }
    }

    /**
     * Rebuild every schedule by replaying the append-only review log.
     *
     * Two uses. As an integrity check, it must reproduce the stored schedules
     * exactly. After a sync merge (ADR 0002), it is the *correct* way to resolve
     * FSRS state: merging two review logs is a set union, and replaying the union
     * is more obviously right than picking a winning schedule row by timestamp.
     *
     * Reads the whole log, so it is a maintenance operation rather than something
     * on the study path.
     */
    fun rebuildSchedulesFromHistory(): Map<ProblemId, ProblemSchedule> = database.read { connection ->
        val byProblem = mutableMapOf<ProblemId, MutableList<ReplayEntry>>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                "SELECT problem_id, rating, finalized_at FROM problem_review ORDER BY finalized_at ASC",
            ).use { rows ->
                while (rows.next()) {
                    val id = ProblemId(rows.getString("problem_id"))
                    byProblem.getOrPut(id) { mutableListOf() } += ReplayEntry(
                        rating = ReviewRating.valueOf(rows.getString("rating")),
                        reviewedAt = Instant.fromEpochMilliseconds(rows.getLong("finalized_at")),
                    )
                }
            }
        }
        byProblem.mapNotNull { (id, history) ->
            scheduler.replay(id, history)?.let { id to it }
        }.toMap()
    }

    /**
     * Rebuild every topic schedule by replaying the review log through current tags.
     *
     * The reason topic state needs no sync payload of its own: it is a fold over the
     * append-only log crossed with the pack's tags, so it can always be recomputed
     * rather than merged (ADR 0002 treats per-Problem schedules the same way).
     *
     * Two consequences worth naming. A review whose Problem has left the pack
     * contributes to nothing, because [topicsFor] returns no tags for it — the review
     * itself survives, as an append-only log requires, but it can no longer rehearse
     * a technique nobody can practise. And *retagging rewrites history*: moving
     * `max-subarray` out of `dynamic-programming` retroactively removes its reviews
     * from DP's fold, because tags are deliberately excluded from a Problem's revision
     * hash. That is the honest behaviour of a projection over mutable metadata.
     *
     * @param topicsFor the current tags for a Problem. A function rather than a
     *   catalogue, so this module stays free of `:shared`.
     */
    fun rebuildTopicSchedulesFromHistory(
        topicsFor: (ProblemId) -> List<String>,
    ): Map<String, TopicSchedule> = database.read { connection ->
        // Interleaved across Problems on purpose, and therefore ordered by time
        // rather than grouped by Problem: a topic's memory is of the technique, so
        // elapsed time between *its* rehearsals is what FSRS must see.
        val byTopic = mutableMapOf<String, MutableList<ReplayEntry>>()
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT problem_id, rating, finalized_at FROM problem_review
                ORDER BY finalized_at ASC, review_session_id ASC
                """.trimIndent(),
            ).use { rows ->
                while (rows.next()) {
                    val entry = ReplayEntry(
                        rating = ReviewRating.valueOf(rows.getString("rating")),
                        reviewedAt = Instant.fromEpochMilliseconds(rows.getLong("finalized_at")),
                    )
                    for (topic in topicsFor(ProblemId(rows.getString("problem_id"))).distinct()) {
                        byTopic.getOrPut(topic) { mutableListOf() } += entry
                    }
                }
            }
        }
        byTopic.mapNotNull { (topic, history) ->
            scheduler.replayTopic(topic, history)?.let { topic to it }
        }.toMap()
    }

    /**
     * The source that produced each review's selected result, keyed by session id.
     *
     * Read separately from the reviews because the source lives alongside the review
     * row rather than on the domain event — it is evidence, not part of what
     * happened. Used by export, so a restored profile shows what the learner wrote
     * and not merely when they wrote it.
     */
    fun selectedSources(): Map<String, String> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT review_session_id, selected_source FROM problem_review")
                .use { rows ->
                    buildMap {
                        while (rows.next()) {
                            put(rows.getString("review_session_id"), rows.getString("selected_source"))
                        }
                    }
                }
        }
    }

    /**
     * Insert a review that happened elsewhere, without scheduling anything.
     *
     * Used only by restore, and deliberately separate from [finalizeReview]: an
     * imported review must not advance a schedule, because the caller replays the
     * whole merged log afterwards. Scheduling here would double-count.
     *
     * Idempotent by primary key, so importing the same backup twice has one effect.
     */
    fun importReview(review: ProblemReviewFinalized, selectedSource: String) {
        database.transaction { connection ->
            if (readReview(connection, review.sessionId) != null) return@transaction
            writeReview(connection, review, selectedSource)
        }
    }

    /**
     * Overwrite every schedule with a rebuilt set.
     *
     * The second half of restore. Runs in one transaction so a profile is never left
     * with some schedules rebuilt and others stale — a half-rebuilt set would give
     * wrong due dates with no way to tell which.
     *
     * Versions restart at 1 because the rebuilt schedules are a new projection; the
     * counter guards concurrent live finalization, not historical continuity.
     */
    fun replaceSchedules(schedules: Map<ProblemId, ProblemSchedule>) {
        database.transaction { connection ->
            connection.createStatement().use { it.execute("DELETE FROM problem_schedule") }
            for (schedule in schedules.values) {
                writeSchedule(connection, schedule, expectedVersion = null)
            }
        }
    }

    /**
     * Overwrite every topic schedule with a rebuilt set.
     *
     * The topic half of restore, in one transaction for the reason
     * [replaceSchedules] gives. `DELETE` first rather than upsert, so a topic that no
     * longer appears in any Problem's tags stops being due — an upsert would leave a
     * retagged-away topic in the queue forever with no Problem to practise it.
     */
    fun replaceTopicSchedules(schedules: Map<String, TopicSchedule>) {
        database.transaction { connection ->
            connection.createStatement().use { it.execute("DELETE FROM topic_schedule") }
            for (schedule in schedules.values) {
                writeTopicSchedule(connection, schedule, expectedVersion = null)
            }
        }
    }

    // ---- SQL ------------------------------------------------------------

    private fun readSchedule(connection: Connection, problemId: ProblemId): ProblemSchedule? =
        connection.prepareStatement("SELECT * FROM problem_schedule WHERE problem_id = ?").use { statement ->
            statement.setString(1, problemId.value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toSchedule() else null }
        }

    /**
     * Insert or conditionally update a schedule.
     *
     * The UPDATE carries `WHERE version = ?`, which is the compare-and-swap. The
     * INSERT is guarded by the primary key. Either way, exactly one row changes or
     * none does, and the caller can tell which.
     *
     * @return true if the row was written.
     */
    private fun writeSchedule(
        connection: Connection,
        schedule: ProblemSchedule,
        expectedVersion: Long?,
    ): Boolean {
        if (expectedVersion == null) {
            // First review. A concurrent inserter loses on the primary key rather
            // than silently overwriting, which is why this is not INSERT OR REPLACE.
            return connection.prepareStatement(
                """
                INSERT OR IGNORE INTO problem_schedule (
                    problem_id, stability, difficulty, due_at, last_reviewed_at,
                    interval_days, review_count, lapse_count, version, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schedule.problemId.value)
                statement.setDouble(2, schedule.stability)
                statement.setDouble(3, schedule.difficulty)
                statement.setLong(4, schedule.dueAt.toEpochMilliseconds())
                statement.setLong(5, schedule.lastReviewedAt.toEpochMilliseconds())
                statement.setDouble(6, schedule.intervalDays)
                statement.setInt(7, schedule.reviewCount)
                statement.setInt(8, schedule.lapseCount)
                statement.setLong(9, schedule.version)
                statement.setLong(10, schedule.updatedAt.toEpochMilliseconds())
                statement.executeUpdate() == 1
            }
        }

        return connection.prepareStatement(
            """
            UPDATE problem_schedule SET
                stability = ?, difficulty = ?, due_at = ?, last_reviewed_at = ?,
                interval_days = ?, review_count = ?, lapse_count = ?,
                version = ?, updated_at = ?
            WHERE problem_id = ? AND version = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setDouble(1, schedule.stability)
            statement.setDouble(2, schedule.difficulty)
            statement.setLong(3, schedule.dueAt.toEpochMilliseconds())
            statement.setLong(4, schedule.lastReviewedAt.toEpochMilliseconds())
            statement.setDouble(5, schedule.intervalDays)
            statement.setInt(6, schedule.reviewCount)
            statement.setInt(7, schedule.lapseCount)
            statement.setLong(8, schedule.version)
            statement.setLong(9, schedule.updatedAt.toEpochMilliseconds())
            statement.setString(10, schedule.problemId.value)
            statement.setLong(11, expectedVersion)
            statement.executeUpdate() == 1
        }
    }

    private fun readTopicSchedule(connection: Connection, topic: String): TopicSchedule? =
        connection.prepareStatement("SELECT * FROM topic_schedule WHERE topic = ?").use { statement ->
            statement.setString(1, topic)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toTopicSchedule() else null }
        }

    /**
     * Insert or conditionally update a topic schedule.
     *
     * The same compare-and-swap [writeSchedule] performs, and for a sharper reason:
     * one review advances several topics, so a lost update here would desynchronise a
     * single topic from the log while its siblings moved on.
     *
     * @return true if the row was written.
     */
    private fun writeTopicSchedule(
        connection: Connection,
        schedule: TopicSchedule,
        expectedVersion: Long?,
    ): Boolean {
        if (expectedVersion == null) {
            return connection.prepareStatement(
                """
                INSERT OR IGNORE INTO topic_schedule (
                    topic, stability, difficulty, due_at, last_reviewed_at,
                    interval_days, review_count, lapse_count, version, updated_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                statement.setString(1, schedule.topic)
                statement.setDouble(2, schedule.stability)
                statement.setDouble(3, schedule.difficulty)
                statement.setLong(4, schedule.dueAt.toEpochMilliseconds())
                statement.setLong(5, schedule.lastReviewedAt.toEpochMilliseconds())
                statement.setDouble(6, schedule.intervalDays)
                statement.setInt(7, schedule.reviewCount)
                statement.setInt(8, schedule.lapseCount)
                statement.setLong(9, schedule.version)
                statement.setLong(10, schedule.updatedAt.toEpochMilliseconds())
                statement.executeUpdate() == 1
            }
        }

        return connection.prepareStatement(
            """
            UPDATE topic_schedule SET
                stability = ?, difficulty = ?, due_at = ?, last_reviewed_at = ?,
                interval_days = ?, review_count = ?, lapse_count = ?,
                version = ?, updated_at = ?
            WHERE topic = ? AND version = ?
            """.trimIndent(),
        ).use { statement ->
            statement.setDouble(1, schedule.stability)
            statement.setDouble(2, schedule.difficulty)
            statement.setLong(3, schedule.dueAt.toEpochMilliseconds())
            statement.setLong(4, schedule.lastReviewedAt.toEpochMilliseconds())
            statement.setDouble(5, schedule.intervalDays)
            statement.setInt(6, schedule.reviewCount)
            statement.setInt(7, schedule.lapseCount)
            statement.setLong(8, schedule.version)
            statement.setLong(9, schedule.updatedAt.toEpochMilliseconds())
            statement.setString(10, schedule.topic)
            statement.setLong(11, expectedVersion)
            statement.executeUpdate() == 1
        }
    }

    private fun readReview(connection: Connection, sessionId: ReviewSessionId): ProblemReviewFinalized? =
        connection.prepareStatement("SELECT * FROM problem_review WHERE review_session_id = ?").use { statement ->
            statement.setString(1, sessionId.value)
            statement.executeQuery().use { rows -> if (rows.next()) rows.toReview() else null }
        }

    private fun writeReview(
        connection: Connection,
        review: ProblemReviewFinalized,
        selectedSource: String,
    ) {
        val record = review.transition
        connection.prepareStatement(
            """
            INSERT INTO problem_review (
                review_session_id, event_id, problem_id, problem_revision_id,
                execution_run_id, outcome, rating, aided, counts_as_solved,
                finalized_at, device_id, selected_source,
                local_date, local_hour, streak_zone_id,
                fsrs_algorithm_id, fsrs_engine_version, fsrs_parameters_hash,
                fsrs_prev_state_hash, fsrs_prev_stability, fsrs_prev_difficulty,
                fsrs_elapsed_days, fsrs_rating_value, fsrs_desired_retention,
                fsrs_max_interval_days, fsrs_next_stability, fsrs_next_difficulty,
                fsrs_next_interval, fsrs_retrievability, fsrs_due_at
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, review.sessionId.value)
            statement.setString(2, review.eventId.value)
            statement.setString(3, review.problemId.value)
            statement.setString(4, review.problemRevisionId.value)
            statement.setString(5, review.executionRunId.value)
            statement.setString(6, review.outcome.name)
            statement.setString(7, review.rating.name)
            statement.setInt(8, if (review.aided) 1 else 0)
            statement.setInt(9, if (review.countsAsSolved) 1 else 0)
            statement.setLong(10, review.finalizedAt.toEpochMilliseconds())
            statement.setString(11, review.deviceId.value)
            statement.setString(12, selectedSource)
            // Denormalised from the review's own recorded zone so the streak and
            // 5am Club queries can filter and group in SQL without recomputing a
            // timezone conversion per row.
            statement.setString(13, review.localDate().toString())
            statement.setInt(14, review.localHour())
            statement.setString(15, review.streakZoneId)
            statement.setString(16, record.algorithmId)
            statement.setString(17, record.engineVersion)
            statement.setString(18, record.parametersHash)
            statement.setString(19, record.previousStateHash)
            record.previousStability?.let { statement.setDouble(20, it) } ?: statement.setNull(20, java.sql.Types.REAL)
            record.previousDifficulty?.let { statement.setDouble(21, it) } ?: statement.setNull(21, java.sql.Types.REAL)
            statement.setDouble(22, record.elapsedDays)
            statement.setInt(23, record.ratingValue)
            statement.setDouble(24, record.desiredRetention)
            statement.setDouble(25, record.maximumIntervalDays)
            statement.setDouble(26, record.nextStability)
            statement.setDouble(27, record.nextDifficulty)
            statement.setDouble(28, record.nextIntervalDays)
            statement.setDouble(29, record.retrievability)
            statement.setLong(30, record.dueAt.toEpochMilliseconds())
            statement.executeUpdate()
        }
    }
}

/** The result of attempting to finalize a review. */
sealed interface FinalizeOutcome {
    val review: ProblemReviewFinalized

    /** This call recorded the review. */
    data class Finalized(
        override val review: ProblemReviewFinalized,
        val schedule: ProblemSchedule,
        /**
         * The topic cards this review advanced, in the order they were given.
         *
         * Empty for an untagged Problem. Returned rather than left to a follow-up read
         * so a caller can show "dynamic programming: next in 9 days" from the same
         * transaction that produced it, instead of re-reading state a concurrent
         * review could already have moved.
         */
        val topicSchedules: List<TopicSchedule> = emptyList(),
    ) : FinalizeOutcome

    /**
     * This session had already been finalized, and the existing outcome is
     * returned unchanged.
     *
     * Not an error: it is the correct answer to a retry after a crash, a double
     * tap, or a resumed process.
     */
    data class AlreadyFinalized(
        override val review: ProblemReviewFinalized,
    ) : FinalizeOutcome
}

/**
 * Another review advanced this Problem's schedule concurrently.
 *
 * Signals a stale caller, not corruption: the correct response is to re-read and
 * retry rather than to force the write.
 */
class ScheduleConflictException(
    val problemId: ProblemId,
    val expectedVersion: Long?,
) : RuntimeException(
    "The schedule for $problemId changed concurrently " +
        "(expected version $expectedVersion). Re-read and retry.",
)

/**
 * Another review advanced this topic's schedule concurrently.
 *
 * A separate type from [ScheduleConflictException] because the remedy differs in
 * scope: a per-Problem conflict means re-read that Problem, while a topic conflict
 * means the whole review rolled back, including its review row, and the finalization
 * must be retried from the start.
 */
class TopicScheduleConflictException(
    val topic: String,
    val expectedVersion: Long?,
) : RuntimeException(
    "The schedule for topic '$topic' changed concurrently " +
        "(expected version $expectedVersion). The review was rolled back; re-read and retry.",
)

// ---- Row mapping ------------------------------------------------------

internal fun ResultSet.toSchedule(): ProblemSchedule = ProblemSchedule(
    problemId = ProblemId(getString("problem_id")),
    stability = getDouble("stability"),
    difficulty = getDouble("difficulty"),
    dueAt = Instant.fromEpochMilliseconds(getLong("due_at")),
    lastReviewedAt = Instant.fromEpochMilliseconds(getLong("last_reviewed_at")),
    intervalDays = getDouble("interval_days"),
    reviewCount = getInt("review_count"),
    lapseCount = getInt("lapse_count"),
    version = getLong("version"),
    updatedAt = Instant.fromEpochMilliseconds(getLong("updated_at")),
)

internal fun ResultSet.toTopicSchedule(): TopicSchedule = TopicSchedule(
    topic = getString("topic"),
    stability = getDouble("stability"),
    difficulty = getDouble("difficulty"),
    dueAt = Instant.fromEpochMilliseconds(getLong("due_at")),
    lastReviewedAt = Instant.fromEpochMilliseconds(getLong("last_reviewed_at")),
    intervalDays = getDouble("interval_days"),
    reviewCount = getInt("review_count"),
    lapseCount = getInt("lapse_count"),
    version = getLong("version"),
    updatedAt = Instant.fromEpochMilliseconds(getLong("updated_at")),
)

internal fun ResultSet.toReview(): ProblemReviewFinalized = ProblemReviewFinalized(
    eventId = DomainEventId(getString("event_id")),
    sessionId = ReviewSessionId(getString("review_session_id")),
    problemId = ProblemId(getString("problem_id")),
    problemRevisionId = ProblemRevisionId(getString("problem_revision_id")),
    executionRunId = ExecutionRunId(getString("execution_run_id")),
    outcome = ExecutionOutcome.valueOf(getString("outcome")),
    rating = ReviewRating.valueOf(getString("rating")),
    aided = getInt("aided") == 1,
    countsAsSolved = getInt("counts_as_solved") == 1,
    finalizedAt = Instant.fromEpochMilliseconds(getLong("finalized_at")),
    streakZoneId = getString("streak_zone_id"),
    deviceId = DeviceId(getString("device_id")),
    transition = FsrsTransitionRecord(
        algorithmId = getString("fsrs_algorithm_id"),
        engineVersion = getString("fsrs_engine_version"),
        parametersHash = getString("fsrs_parameters_hash"),
        previousStateHash = getString("fsrs_prev_state_hash"),
        previousStability = getNullableDouble("fsrs_prev_stability"),
        previousDifficulty = getNullableDouble("fsrs_prev_difficulty"),
        elapsedDays = getDouble("fsrs_elapsed_days"),
        ratingValue = getInt("fsrs_rating_value"),
        desiredRetention = getDouble("fsrs_desired_retention"),
        maximumIntervalDays = getDouble("fsrs_max_interval_days"),
        nextStability = getDouble("fsrs_next_stability"),
        nextDifficulty = getDouble("fsrs_next_difficulty"),
        nextIntervalDays = getDouble("fsrs_next_interval"),
        retrievability = getDouble("fsrs_retrievability"),
        dueAt = Instant.fromEpochMilliseconds(getLong("fsrs_due_at")),
    ),
)

/**
 * Read a nullable REAL.
 *
 * JDBC returns 0.0 for a SQL NULL, so [ResultSet.wasNull] must be consulted. A
 * first review has no previous state, and reading 0.0 as a real stability would
 * silently corrupt the audit — and FsrsTransitionRecord would reject it for
 * having one previous value present and the other absent.
 */
private fun ResultSet.getNullableDouble(column: String): Double? {
    val value = getDouble(column)
    return if (wasNull()) null else value
}
