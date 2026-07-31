package dev.bee.beecode.persistence

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.DomainEventId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRun
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemExample
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ProblemTest
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSession
import dev.bee.beecode.domain.ReviewSessionId
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.fsrs.BeeCodeScheduler
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private val T0 = Instant.parse("2026-07-29T12:00:00Z")
private val REVISION = ProblemRevisionId("a".repeat(64))
private val DEVICE = DeviceId("device-1")
private val UTC = TimeZone.UTC

class ReviewRepositoryTest {
    private lateinit var database: BeeCodeDatabase
    private lateinit var reviews: ReviewRepository

    @BeforeTest
    fun setUp() {
        database = BeeCodeDatabase.inMemory()
        reviews = ReviewRepository(database, BeeCodeScheduler())
    }

    @AfterTest
    fun tearDown() {
        database.close()
    }

    @Test
    fun aFreshDatabaseIsMigratedToTheCurrentVersion() {
        assertEquals(Schema.VERSION, database.schemaVersion())
    }

    @Test
    fun migrationIsIdempotentAcrossReopen() {
        // Reopening must not re-run migrations or lose data. This is the ordinary
        // case every app launch takes.
        val file = kotlin.io.path.createTempFile("beecode-test-", ".db").toFile()
        try {
            BeeCodeDatabase.open(file.absolutePath).use { first ->
                assertEquals(Schema.VERSION, first.schemaVersion())
                ReviewRepository(first, BeeCodeScheduler()).finalize(
                    plan = passingPlan(),
                    eventId = DomainEventId("evt-1"),
                    deviceId = DEVICE,
                    finalizedAtInstant = T0,
                    streakZone = UTC,
                )
            }
            BeeCodeDatabase.open(file.absolutePath).use { second ->
                assertEquals(Schema.VERSION, second.schemaVersion())
                val repository = ReviewRepository(second, BeeCodeScheduler())
                assertEquals(1, repository.reviewCount())
                assertNotNull(repository.review(ReviewSessionId("session-1")))
            }
        } finally {
            file.delete()
            java.io.File(file.absolutePath + "-wal").delete()
            java.io.File(file.absolutePath + "-shm").delete()
        }
    }

    @Test
    fun finalizingRecordsTheReviewAndTheSchedule() {
        val outcome = reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC)

        val finalized = assertIs<FinalizeOutcome.Finalized>(outcome)
        assertEquals(ReviewRating.GOOD, finalized.review.rating)
        assertTrue(finalized.review.countsAsSolved)
        assertEquals(1, finalized.schedule.reviewCount)
        assertEquals(1L, finalized.schedule.version)

        val stored = assertNotNull(reviews.schedule(ProblemId("two-sum")))
        assertEquals(finalized.schedule, stored)
        assertTrue(stored.dueAt > T0, "the Problem must be scheduled into the future")
    }

    @Test
    fun finalizingTheSameSessionTwiceHasOneEffect() {
        // The idempotency guarantee. A retry after a crash, a double tap, or a
        // resumed process must not review the Problem twice.
        val first = reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC)
        val second = reviews.finalize(passingPlan(), DomainEventId("evt-2"), DEVICE, T0.plusDays(1), UTC)

        assertIs<FinalizeOutcome.Finalized>(first)
        val repeated = assertIs<FinalizeOutcome.AlreadyFinalized>(second)

        assertEquals(1, reviews.reviewCount(), "a retry must not append a second review")
        // The original outcome is returned unchanged, including its event ID: the
        // second call's ID must not overwrite the recorded one.
        assertEquals(DomainEventId("evt-1"), repeated.review.eventId)
        assertEquals(T0, repeated.review.finalizedAt)
        // And crucially, the schedule did not advance a second time.
        assertEquals(1, reviews.schedule(ProblemId("two-sum"))!!.reviewCount)
        assertEquals(1L, reviews.schedule(ProblemId("two-sum"))!!.version)
    }

    @Test
    fun concurrentFinalizationsOfOneSessionProduceExactlyOneReview() {
        // The same session finalized from many threads at once: the shape a
        // double-tap or a resumed background job actually takes.
        val threads = 8
        val ready = CountDownLatch(threads)
        val go = CountDownLatch(1)
        val created = AtomicInteger()
        val alreadyDone = AtomicInteger()
        val executor = Executors.newFixedThreadPool(threads)
        try {
            repeat(threads) { i ->
                executor.submit {
                    ready.countDown()
                    go.await()
                    when (
                        reviews.finalize(
                            passingPlan(),
                            DomainEventId("evt-$i"),
                            DEVICE,
                            T0,
                            UTC,
                        )
                    ) {
                        is FinalizeOutcome.Finalized -> created.incrementAndGet()
                        is FinalizeOutcome.AlreadyFinalized -> alreadyDone.incrementAndGet()
                    }
                }
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS), "threads did not start")
            go.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "finalization deadlocked")
        } finally {
            executor.shutdownNow()
        }

        assertEquals(1, created.get(), "exactly one caller may create the review")
        assertEquals(threads - 1, alreadyDone.get(), "every other caller must see it already finalized")
        assertEquals(1, reviews.reviewCount())
        assertEquals(1, reviews.schedule(ProblemId("two-sum"))!!.reviewCount)
    }

    @Test
    fun concurrentFinalizationsOfDistinctSessionsAllApply() {
        // Different sessions on the same Problem must each advance the schedule
        // exactly once, with no lost update. This is what the schedule version
        // guards.
        val count = 12
        val executor = Executors.newFixedThreadPool(6)
        val go = CountDownLatch(1)
        val succeeded = AtomicInteger()
        try {
            repeat(count) { i ->
                executor.submit {
                    go.await()
                    runCatching {
                        reviews.finalize(
                            passingPlan(sessionId = "session-$i"),
                            DomainEventId("evt-$i"),
                            DEVICE,
                            T0.plusDays(i.toLong()),
                            UTC,
                        )
                    }.onSuccess { succeeded.incrementAndGet() }
                }
            }
            go.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "finalization deadlocked")
        } finally {
            executor.shutdownNow()
        }

        assertEquals(count, succeeded.get(), "no distinct session may be rejected")
        assertEquals(count.toLong(), reviews.reviewCount())
        val schedule = assertNotNull(reviews.schedule(ProblemId("two-sum")))
        // Every review counted: no lost update.
        assertEquals(count, schedule.reviewCount)
        assertEquals(count.toLong(), schedule.version)
    }

    @Test
    fun aFailedReviewIsRecordedAsALapseAndNotAsSolved() {
        val outcome = reviews.finalize(failingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC)
        val finalized = assertIs<FinalizeOutcome.Finalized>(outcome)

        assertEquals(ReviewRating.AGAIN, finalized.review.rating)
        assertFalse(finalized.review.countsAsSolved)
        assertEquals(ExecutionOutcome.FAILED, finalized.review.outcome)
        assertEquals(1, finalized.schedule.lapseCount)
    }

    @Test
    fun anAidedPassIsRecordedAsNotSolved() {
        // The honesty rule survives the round trip to disk: this is what keeps the
        // 5am Club from being farmed by revealing the answer.
        val session = ReviewSession.start(ReviewSessionId("session-aided"), problem(), T0)
            .reveal()
            .recordRun(run(outcome = ExecutionOutcome.PASSED))
        val plan = session.planFinalization(ExecutionRunId("run-1"), ReviewRating.HARD)

        val finalized = assertIs<FinalizeOutcome.Finalized>(
            reviews.finalize(plan, DomainEventId("evt-1"), DEVICE, T0, UTC),
        )
        assertTrue(finalized.review.aided)
        assertFalse(finalized.review.countsAsSolved)
    }

    @Test
    fun theFullFsrsAuditIsPersistedAndReadBack() {
        // The audit is what lets a future BeeCode rebuild a due date it did not
        // compute. If it does not survive the round trip it is worthless.
        reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC)
        val second = assertIs<FinalizeOutcome.Finalized>(
            reviews.finalize(passingPlan("session-2"), DomainEventId("evt-2"), DEVICE, T0.plusDays(5), UTC),
        )

        val stored = assertNotNull(reviews.review(ReviewSessionId("session-2")))
        assertEquals(second.review.transition, stored.transition)
        assertEquals("FSRS-7 35-parameter snapshot", stored.transition.algorithmId)
        assertEquals("bee-fsrs-0.2.0", stored.transition.engineVersion)
        assertEquals(5.0, stored.transition.elapsedDays)
        assertNotNull(stored.transition.previousStability)
        assertNotNull(stored.transition.previousDifficulty)
    }

    @Test
    fun aFirstReviewStoresNullPreviousStateRatherThanZero() {
        // JDBC returns 0.0 for a SQL NULL, so reading this back without checking
        // wasNull would silently record a real stability of zero — and
        // FsrsTransitionRecord would reject one previous value present with the
        // other absent.
        reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC)
        val stored = assertNotNull(reviews.review(ReviewSessionId("session-1")))

        assertTrue(stored.transition.isFirstReview)
        assertNull(stored.transition.previousStability)
        assertNull(stored.transition.previousDifficulty)
    }

    @Test
    fun replayingTheAppendOnlyLogReproducesTheStoredSchedules() {
        // The property that makes sync-merge recomputation trustworthy, asserted
        // through the database rather than only in the scheduler's own tests.
        val ratings = listOf(
            ReviewRating.GOOD, ReviewRating.AGAIN, ReviewRating.HARD,
            ReviewRating.GOOD, ReviewRating.EASY,
        )
        var at = T0
        ratings.forEachIndexed { i, rating ->
            val plan = if (rating == ReviewRating.AGAIN) {
                failingPlan(sessionId = "session-$i")
            } else {
                passingPlan(sessionId = "session-$i", rating = rating)
            }
            reviews.finalize(plan, DomainEventId("evt-$i"), DEVICE, at, UTC)
            at = at.plusDays(3)
        }

        val stored = assertNotNull(reviews.schedule(ProblemId("two-sum")))
        val rebuilt = assertNotNull(reviews.rebuildSchedulesFromHistory()[ProblemId("two-sum")])

        // Version and updatedAt are write-path bookkeeping, so compare the state
        // that actually determines what the learner sees next.
        assertEquals(stored.stability, rebuilt.stability)
        assertEquals(stored.difficulty, rebuilt.difficulty)
        assertEquals(stored.dueAt, rebuilt.dueAt)
        assertEquals(stored.intervalDays, rebuilt.intervalDays)
        assertEquals(stored.reviewCount, rebuilt.reviewCount)
        assertEquals(stored.lapseCount, rebuilt.lapseCount)
    }

    @Test
    fun theDueQueueReturnsOnlyDueProblemsSoonestFirst() {
        reviews.finalize(passingPlan(problemId = "two-sum"), DomainEventId("e1"), DEVICE, T0, UTC)
        reviews.finalize(
            failingPlan(sessionId = "session-b", problemId = "valid-parentheses"),
            DomainEventId("e2"), DEVICE, T0, UTC,
        )

        // Nothing is due immediately after being reviewed.
        assertTrue(reviews.dueSchedules(T0, limit = 10).isEmpty())

        // The lapsed Problem comes back first: Again produces the shortest interval.
        val laterDue = reviews.dueSchedules(T0.plusDays(400), limit = 10)
        assertEquals(2, laterDue.size)
        assertEquals(ProblemId("valid-parentheses"), laterDue.first().problemId)
        assertTrue(laterDue[0].dueAt <= laterDue[1].dueAt, "the queue must be ordered by due date")
    }

    @Test
    fun theDueQueueRespectsItsLimit() {
        repeat(5) { i ->
            reviews.finalize(
                passingPlan(sessionId = "session-$i", problemId = "problem-$i"),
                DomainEventId("evt-$i"), DEVICE, T0, UTC,
            )
        }
        assertEquals(3, reviews.dueSchedules(T0.plusDays(400), limit = 3).size)
    }

    @Test
    fun reviewHistoryIsOrderedOldestFirstAndScopedToOneProblem() {
        reviews.finalize(passingPlan("session-1"), DomainEventId("e1"), DEVICE, T0, UTC)
        reviews.finalize(passingPlan("session-2"), DomainEventId("e2"), DEVICE, T0.plusDays(2), UTC)
        reviews.finalize(
            passingPlan("session-3", problemId = "other-problem"),
            DomainEventId("e3"), DEVICE, T0.plusDays(1), UTC,
        )

        val history = reviews.reviewHistory(ProblemId("two-sum"))
        assertEquals(2, history.size)
        assertEquals(T0, history[0].finalizedAt)
        assertEquals(T0.plusDays(2), history[1].finalizedAt)
    }

    @Test
    fun recentReviewsAreOrderedNewestFirstAcrossProblems() {
        reviews.finalize(passingPlan("session-1"), DomainEventId("e1"), DEVICE, T0, UTC)
        reviews.finalize(
            passingPlan("session-2", problemId = "other-problem"),
            DomainEventId("e2"), DEVICE, T0.plusDays(2), UTC,
        )

        val recent = reviews.recentReviews(limit = 10)
        assertEquals(2, recent.size)
        assertEquals(T0.plusDays(2), recent[0].finalizedAt)
    }

    @Test
    fun theSelectedSourceAndLocalDateSurviveTheRoundTrip() {
        // The stored local date is what streaks and the 5am Club read. Deriving it
        // once at write time means a later timezone change cannot rewrite history.
        val fiveAm = Instant.parse("2026-07-29T05:30:00Z")
        reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, fiveAm, UTC)

        database.read { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery(
                    "SELECT selected_source, local_date, local_hour, streak_zone_id FROM problem_review",
                ).use { rows ->
                    assertTrue(rows.next())
                    assertTrue(
                        rows.getString("selected_source").contains("two_sum"),
                        "the source that produced the result must be retained",
                    )
                    assertEquals("2026-07-29", rows.getString("local_date"))
                    assertEquals(5, rows.getInt("local_hour"))
                    // kotlinx-datetime renders TimeZone.UTC's id as "Z".
                    assertEquals(UTC.id, rows.getString("streak_zone_id"))
                }
            }
        }
    }

    @Test
    fun aRolledBackTransactionLeavesNoPartialState() {
        // The all-or-nothing guarantee: a schedule advanced without its review row
        // is the specific corruption the transaction exists to prevent.
        runCatching {
            database.transaction { connection ->
                connection.createStatement().use {
                    it.execute(
                        """
                        INSERT INTO problem_schedule (
                            problem_id, stability, difficulty, due_at, last_reviewed_at,
                            interval_days, review_count, lapse_count, version, updated_at
                        ) VALUES ('orphan', 1.0, 5.0, 0, 0, 1, 1, 0, 1, 0)
                        """.trimIndent(),
                    )
                }
                throw IllegalStateException("simulated failure after a partial write")
            }
        }
        assertNull(reviews.schedule(ProblemId("orphan")), "the partial write must have rolled back")
        assertEquals(0, reviews.reviewCount())
    }

    @Test
    fun aDatabaseFromANewerBeeCodeIsRefusedRatherThanGuessedAt() {
        // Guessing at an unknown schema risks destroying reviews. Refusing lets the
        // UI tell the learner to upgrade.
        val file = kotlin.io.path.createTempFile("beecode-future-", ".db").toFile()
        try {
            BeeCodeDatabase.open(file.absolutePath).use { database ->
                database.transaction { connection ->
                    connection.createStatement().use { it.execute("PRAGMA user_version = 9999") }
                }
            }
            val failure = runCatching { BeeCodeDatabase.open(file.absolutePath).close() }.exceptionOrNull()
            assertIs<IllegalStateException>(failure)
            assertTrue(failure.message!!.contains("newer version"), failure.message!!)
        } finally {
            file.delete()
            java.io.File(file.absolutePath + "-wal").delete()
            java.io.File(file.absolutePath + "-shm").delete()
        }
    }

    // ---- Topic cards ----------------------------------------------------

    @Test
    fun oneReviewAdvancesEveryTopicTheProblemIsTaggedWith() {
        // The product claim, at the persistence layer: solving median-two-sorted
        // rehearses arrays, binary search, and two pointers, so all three move.
        val topics = listOf("arrays", "binary-search", "two-pointers")
        val finalized = assertIs<FinalizeOutcome.Finalized>(
            reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC, topics),
        )

        assertEquals(topics, finalized.topicSchedules.map { it.topic })
        for (topic in topics) {
            val stored = assertNotNull(reviews.topicSchedule(topic), topic)
            assertEquals(1, stored.reviewCount)
            assertEquals(1L, stored.version)
            assertTrue(stored.dueAt > T0, "$topic must be scheduled into the future")
        }
    }

    @Test
    fun aTopicAccumulatesAcrossDifferentProblems() {
        // What makes the topic the card rather than a label: two different arrays
        // Problems are two rehearsals of one technique, so the topic's review count
        // reaches 2 while neither Problem's does.
        reviews.finalize(
            passingPlan(problemId = "two-sum"), DomainEventId("e1"), DEVICE, T0, UTC, listOf("arrays"),
        )
        reviews.finalize(
            passingPlan(sessionId = "session-2", problemId = "max-subarray"),
            DomainEventId("e2"), DEVICE, T0.plusDays(3), UTC, listOf("arrays"),
        )

        val topic = assertNotNull(reviews.topicSchedule("arrays"))
        assertEquals(2, topic.reviewCount)
        assertEquals(T0.plusDays(3), topic.lastReviewedAt)
        assertEquals(1, reviews.schedule(ProblemId("two-sum"))!!.reviewCount)
        assertEquals(1, reviews.schedule(ProblemId("max-subarray"))!!.reviewCount)
    }

    @Test
    fun aProblemTaggedWithTheSameTopicTwiceAdvancesItOnce() {
        // A duplicate tag is a content typo, not a second rehearsal. Advancing twice
        // would compound the second transition off the first's own write, which is
        // both wrong and invisible afterwards.
        val finalized = assertIs<FinalizeOutcome.Finalized>(
            reviews.finalize(
                passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC, listOf("arrays", "arrays"),
            ),
        )

        assertEquals(listOf("arrays"), finalized.topicSchedules.map { it.topic })
        assertEquals(1, reviews.topicSchedule("arrays")!!.reviewCount)
    }

    @Test
    fun anUntaggedProblemSchedulesNoTopics() {
        val finalized = assertIs<FinalizeOutcome.Finalized>(
            reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC, emptyList()),
        )

        assertTrue(finalized.topicSchedules.isEmpty())
        assertTrue(reviews.topicSchedules().isEmpty())
        // The Problem itself is still scheduled: an untagged Problem is studiable,
        // it just rehearses no named technique.
        assertNotNull(reviews.schedule(ProblemId("two-sum")))
    }

    @Test
    fun aRetriedFinalizationDoesNotAdvanceTheTopicASecondTime() {
        // Idempotency has to cover the topic cards too. It does so structurally —
        // step 1 returns before step 5 runs — and this is the assertion that would
        // catch a future reordering.
        reviews.finalize(passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC, listOf("arrays"))
        val repeated = reviews.finalize(
            passingPlan(), DomainEventId("evt-2"), DEVICE, T0.plusDays(1), UTC, listOf("arrays"),
        )

        assertIs<FinalizeOutcome.AlreadyFinalized>(repeated)
        val topic = assertNotNull(reviews.topicSchedule("arrays"))
        assertEquals(1, topic.reviewCount)
        assertEquals(1L, topic.version)
    }

    @Test
    fun forgettingATopicBringsItBackSoonerThanRememberingIt() {
        // The whole point of putting the card on the technique: "frequently forgets
        // DP" needs no weakness heuristic, because FSRS shortens DP's interval.
        reviews.finalize(
            failingPlan(problemId = "coin-change"),
            DomainEventId("e1"), DEVICE, T0, UTC, listOf("dynamic-programming"),
        )
        reviews.finalize(
            passingPlan(sessionId = "session-2", problemId = "two-sum"),
            DomainEventId("e2"), DEVICE, T0, UTC, listOf("arrays"),
        )

        val lapsed = assertNotNull(reviews.topicSchedule("dynamic-programming"))
        val recalled = assertNotNull(reviews.topicSchedule("arrays"))
        assertEquals(1, lapsed.lapseCount)
        assertEquals(0, recalled.lapseCount)
        assertTrue(
            lapsed.dueAt < recalled.dueAt,
            "a forgotten topic must come back before a remembered one: " +
                "${lapsed.dueAt} vs ${recalled.dueAt}",
        )
    }

    @Test
    fun theTopicQueueReturnsOnlyDueTopicsSoonestFirst() {
        reviews.finalize(
            failingPlan(problemId = "coin-change"),
            DomainEventId("e1"), DEVICE, T0, UTC, listOf("dynamic-programming"),
        )
        reviews.finalize(
            passingPlan(sessionId = "session-2", problemId = "two-sum"),
            DomainEventId("e2"), DEVICE, T0, UTC, listOf("arrays"),
        )

        assertTrue(reviews.dueTopicSchedules(T0, limit = 10).isEmpty(), "nothing is due when just rehearsed")

        val due = reviews.dueTopicSchedules(T0.plusDays(400), limit = 10)
        assertEquals(listOf("dynamic-programming", "arrays"), due.map { it.topic })
        assertEquals(1, reviews.dueTopicSchedules(T0.plusDays(400), limit = 1).size)
    }

    @Test
    fun aFailedTopicWriteRollsBackTheWholeReview() {
        // The all-or-nothing claim, and the reason a topic conflict throws rather than
        // being tolerated: a review that advanced the Problem but not its topics would
        // leave the projection permanently short by one, and nothing downstream could
        // detect the difference afterwards.
        //
        // A trigger rather than a staged version mismatch. `BEGIN IMMEDIATE` plus the
        // read happening inside the transaction makes a genuine compare-and-swap loss
        // unreachable in-process — any version this test wrote beforehand is simply
        // the version the repository then reads. So the failing write is provoked
        // directly, which is what the rollback assertion actually needs.
        database.transaction { connection ->
            connection.createStatement().use {
                it.execute(
                    """
                    CREATE TRIGGER refuse_arrays BEFORE INSERT ON topic_schedule
                    WHEN new.topic = 'arrays'
                    BEGIN SELECT RAISE(ABORT, 'simulated topic write failure'); END
                    """.trimIndent(),
                )
            }
        }

        val failure = runCatching {
            reviews.finalize(
                passingPlan(), DomainEventId("evt-1"), DEVICE, T0, UTC, listOf("binary-search", "arrays"),
            )
        }.exceptionOrNull()

        assertNotNull(failure, "the failing write must surface rather than be swallowed")
        assertEquals(0, reviews.reviewCount(), "the review row must have rolled back")
        assertNull(reviews.schedule(ProblemId("two-sum")), "the Problem schedule must have rolled back too")
        // binary-search was written before arrays failed. It must not survive: a
        // half-advanced topic set is the corruption this transaction exists to prevent.
        assertTrue(reviews.topicSchedules().isEmpty(), reviews.topicSchedules().keys.toString())
    }

    @Test
    fun concurrentReviewsOfOneTopicAllCount() {
        // What the topic compare-and-swap is for. Twelve distinct sessions rehearsing
        // one technique must produce twelve topic advances: a lost update here would
        // silently understate how much the learner has practised, and the number it
        // understated would be the one FSRS schedules from.
        val count = 12
        val executor = Executors.newFixedThreadPool(6)
        val go = CountDownLatch(1)
        val succeeded = AtomicInteger()
        try {
            repeat(count) { i ->
                executor.submit {
                    go.await()
                    runCatching {
                        reviews.finalize(
                            passingPlan(sessionId = "session-$i", problemId = "problem-$i"),
                            DomainEventId("evt-$i"),
                            DEVICE,
                            T0.plusDays(i.toLong()),
                            UTC,
                            listOf("arrays"),
                        )
                    }.onSuccess { succeeded.incrementAndGet() }
                }
            }
            go.countDown()
            executor.shutdown()
            assertTrue(executor.awaitTermination(30, TimeUnit.SECONDS), "finalization deadlocked")
        } finally {
            executor.shutdownNow()
        }

        assertEquals(count, succeeded.get(), "no distinct session may be rejected")
        val topic = assertNotNull(reviews.topicSchedule("arrays"))
        assertEquals(count, topic.reviewCount)
        assertEquals(count.toLong(), topic.version)
    }

    @Test
    fun rebuildingTopicsFromHistoryReproducesTheStoredState() {
        // The property that lets topic state stay out of the sync payload: it can be
        // recomputed from the append-only log rather than merged.
        val ratings = listOf(
            ReviewRating.GOOD, ReviewRating.AGAIN, ReviewRating.HARD,
            ReviewRating.GOOD, ReviewRating.EASY,
        )
        var at = T0
        ratings.forEachIndexed { i, rating ->
            // Alternating Problems, one topic: the interleaving a real learner
            // produces, and the case a per-Problem fold would get wrong.
            val problemId = if (i % 2 == 0) "two-sum" else "max-subarray"
            val plan = if (rating == ReviewRating.AGAIN) {
                failingPlan(sessionId = "session-$i", problemId = problemId)
            } else {
                passingPlan(sessionId = "session-$i", problemId = problemId, rating = rating)
            }
            reviews.finalize(plan, DomainEventId("evt-$i"), DEVICE, at, UTC, listOf("arrays"))
            at = at.plusDays(3)
        }

        val stored = assertNotNull(reviews.topicSchedule("arrays"))
        val rebuilt = assertNotNull(
            reviews.rebuildTopicSchedulesFromHistory { listOf("arrays") }["arrays"],
        )

        // Version and updatedAt are write-path bookkeeping; compare the state that
        // decides what the learner sees next.
        assertEquals(stored.stability, rebuilt.stability)
        assertEquals(stored.difficulty, rebuilt.difficulty)
        assertEquals(stored.dueAt, rebuilt.dueAt)
        assertEquals(stored.intervalDays, rebuilt.intervalDays)
        assertEquals(5, rebuilt.reviewCount)
        assertEquals(1, rebuilt.lapseCount)
    }

    @Test
    fun aReviewWhoseProblemLeftThePackContributesToNoTopic() {
        // The review survives, as an append-only log requires, but it can no longer
        // rehearse a technique nobody can practise.
        reviews.finalize(
            passingPlan(problemId = "two-sum"), DomainEventId("e1"), DEVICE, T0, UTC, listOf("arrays"),
        )
        reviews.finalize(
            passingPlan(sessionId = "session-2", problemId = "departed-problem"),
            DomainEventId("e2"), DEVICE, T0.plusDays(3), UTC, listOf("arrays"),
        )

        val rebuilt = reviews.rebuildTopicSchedulesFromHistory { id ->
            if (id == ProblemId("two-sum")) listOf("arrays") else emptyList()
        }

        assertEquals(setOf("arrays"), rebuilt.keys)
        assertEquals(1, rebuilt.getValue("arrays").reviewCount)
        assertEquals(2, reviews.reviewCount(), "the log itself is untouched")
    }

    @Test
    fun retaggingRewritesATopicsHistory() {
        // Tags are deliberately excluded from a Problem's revision hash, so a replay
        // uses *current* tags. Documented consequence, asserted rather than assumed:
        // moving a Problem out of a topic removes its reviews from that topic's fold.
        reviews.finalize(
            passingPlan(problemId = "max-subarray"),
            DomainEventId("e1"), DEVICE, T0, UTC, listOf("dynamic-programming"),
        )

        val afterRetag = reviews.rebuildTopicSchedulesFromHistory { listOf("arrays") }
        assertEquals(setOf("arrays"), afterRetag.keys)
        assertEquals(1, afterRetag.getValue("arrays").reviewCount)
    }

    @Test
    fun replacingTopicSchedulesDropsTheOnesNoLongerTagged() {
        // Restore's second half. DELETE-then-insert rather than upsert, so a topic
        // that no longer appears in any Problem's tags stops being due — an upsert
        // would leave it in the queue forever with no Problem to practise it.
        reviews.finalize(
            passingPlan(), DomainEventId("e1"), DEVICE, T0, UTC, listOf("arrays", "hash-map"),
        )
        assertEquals(setOf("arrays", "hash-map"), reviews.topicSchedules().keys)

        reviews.replaceTopicSchedules(reviews.rebuildTopicSchedulesFromHistory { listOf("arrays") })

        assertEquals(setOf("arrays"), reviews.topicSchedules().keys)
        // Versions restart at 1: a rebuilt set is a new projection, and the counter
        // guards concurrent live finalization rather than historical continuity.
        assertEquals(1L, reviews.topicSchedules().getValue("arrays").version)
    }

    // ---- Fixtures -------------------------------------------------------

    private fun passingPlan(
        sessionId: String = "session-1",
        problemId: String = "two-sum",
        rating: ReviewRating = ReviewRating.GOOD,
    ) = ReviewSession.start(ReviewSessionId(sessionId), problem(problemId), T0)
        .recordRun(run(outcome = ExecutionOutcome.PASSED, problemId = problemId))
        .planFinalization(ExecutionRunId("run-1"), rating)

    private fun failingPlan(
        sessionId: String = "session-1",
        problemId: String = "two-sum",
    ) = ReviewSession.start(ReviewSessionId(sessionId), problem(problemId), T0)
        .recordRun(run(outcome = ExecutionOutcome.FAILED, problemId = problemId))
        .planFinalization(ExecutionRunId("run-1"), ReviewRating.AGAIN)
}

internal fun problem(id: String = "two-sum"): ProblemDefinition = ProblemDefinition(
    id = ProblemId(id),
    revisionId = REVISION,
    title = "Two Sum",
    difficulty = ProblemDifficulty.EASY,
    topics = listOf("arrays"),
    statementMarkdown = "Return indices of the two numbers adding to the target.",
    starterSource = "def two_sum(nums, target):\n    pass\n",
    entryPoint = "two_sum",
    examples = listOf(ProblemExample("[2,7], 9", "[0,1]", null)),
    tests = listOf(
        ProblemTest("example", "[[2,7],9]", "[0,1]", ComparatorId.EXACT),
    ),
    limits = ExecutionLimits.DEFAULT,
    explanationMarkdown = "Use a hash map.",
)

internal fun run(
    id: String = "run-1",
    outcome: ExecutionOutcome = ExecutionOutcome.PASSED,
    problemId: String = "two-sum",
): ExecutionRun = ExecutionRun(
    id = ExecutionRunId(id),
    problemId = ProblemId(problemId),
    problemRevisionId = REVISION,
    source = "def two_sum(nums, target):\n    return [0, 1]\n",
    outcome = outcome,
    testResults = when (outcome) {
        ExecutionOutcome.PASSED -> listOf(caseResult("example", true))
        ExecutionOutcome.FAILED -> listOf(caseResult("example", false))
        else -> emptyList()
    },
    output = "",
    outputTruncated = false,
    durationMillis = 12,
    startedAt = T0,
    runnerId = "test-runner",
    pythonVersion = "3.12.0",
)

private fun caseResult(name: String, passed: Boolean) = TestCaseResult(
    name = name,
    passed = passed,
    hidden = false,
    expectedJson = "[0,1]",
    actualJson = if (passed) "[0,1]" else "[1,0]",
    message = if (passed) null else "expected [0,1] but got [1,0]",
    durationMillis = 1,
)

internal fun Instant.plusDays(days: Long): Instant =
    Instant.fromEpochSeconds(epochSeconds + days * 86_400L)

/**
 * [ReviewRepository.finalizeReview] with the fixture's topics filled in.
 *
 * `topics` has no default on the real method on purpose — an empty list is a
 * legitimate answer for an untagged Problem, so a default would make "untagged" and
 * "the caller forgot" indistinguishable. Tests that are not about topics still want
 * one, though, so the default lives here where forgetting it costs nothing.
 */
internal fun ReviewRepository.finalize(
    plan: dev.bee.beecode.domain.FinalizationPlan,
    eventId: DomainEventId,
    deviceId: DeviceId,
    finalizedAtInstant: Instant,
    streakZone: TimeZone,
    topics: List<String> = listOf("arrays"),
): FinalizeOutcome = finalizeReview(plan, eventId, deviceId, finalizedAtInstant, streakZone, topics)
