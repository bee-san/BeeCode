package dev.bee.beecode.persistence

import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.fsrs.BeeCodeScheduler
import dev.bee.beecode.domain.ReviewSessionId
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The version 1 to 2 migration: integer intervals widened to fractional.
 *
 * Version 2 exists because FSRS-7 schedules in fractional days, and `interval_days`
 * was declared INTEGER. The danger is specific and quiet: SQLite would coerce a
 * ten-minute interval toward zero, so the exact case the new algorithm exists to
 * handle would be the case that silently broke.
 *
 * These tests build a **real version 1 database** with the original DDL and real rows,
 * then open it through [BeeCodeDatabase] and check the data survived. Asserting
 * against the migration SQL alone would only prove the statements parse; a learner
 * upgrading BeeCode cares whether their review history is still there and still
 * correct afterwards.
 */
class SchemaMigrationTest {

    @Test
    fun aVersionOneDatabaseUpgradesAndKeepsItsRows() {
        withTempFile { path ->
            createVersionOneDatabase(path)

            BeeCodeDatabase.open(path).use { database ->
                assertEquals(2, database.schemaVersion())
                val repository = ReviewRepository(database, BeeCodeScheduler())

                // The schedule row survived, and its whole-day interval reads back
                // as the same number now that the column is fractional.
                val schedule = assertNotNull(repository.schedule(ProblemId("two-sum")))
                assertEquals(7.0, schedule.intervalDays)
                assertEquals(4.5, schedule.stability)
                assertEquals(6.25, schedule.difficulty)
                assertEquals(3, schedule.reviewCount)
                assertEquals(1, schedule.lapseCount)
                assertEquals(9L, schedule.version)

                // The append-only review log survived with its FSRS audit intact.
                // This is the part that must not be lost: it is what makes an old
                // row explainable after the engine changed underneath it.
                val review = assertNotNull(repository.review(ReviewSessionId("session-1")))
                assertEquals("FSRS-6.x 21-parameter snapshot", review.transition.algorithmId)
                assertEquals("bee-fsrs-0.1.0", review.transition.engineVersion)
                assertEquals(5.0, review.transition.elapsedDays)
                assertEquals(7.0, review.transition.nextIntervalDays)
                assertEquals(36_500.0, review.transition.maximumIntervalDays)
                assertEquals(4.5, review.transition.nextStability)
            }
        }
    }

    @Test
    fun theUpgradedColumnsActuallyStoreFractionalValues() {
        withTempFile { path ->
            createVersionOneDatabase(path)

            BeeCodeDatabase.open(path).use { database ->
                // The point of the migration. Before it, this write would round to
                // zero and the assertion below would read 0.0 back.
                database.transaction { connection ->
                    connection.prepareStatement(
                        "UPDATE problem_schedule SET interval_days = ? WHERE problem_id = ?",
                    ).use { statement ->
                        statement.setDouble(1, 10.0 / 1_440.0)
                        statement.setString(2, "two-sum")
                        statement.executeUpdate()
                    }
                }

                val schedule = assertNotNull(
                    ReviewRepository(database, BeeCodeScheduler()).schedule(ProblemId("two-sum")),
                )
                assertEquals(10.0 / 1_440.0, schedule.intervalDays)
                assertTrue(schedule.intervalDays > 0.0, "a ten-minute interval must not round to zero")
            }
        }
    }

    @Test
    fun theDueIndexSurvivesTheTableRebuild() {
        withTempFile { path ->
            createVersionOneDatabase(path)

            BeeCodeDatabase.open(path).use { database ->
                // The migration drops and recreates both tables, which takes their
                // indexes with them. The due queue is an indexed scan, so a missing
                // index turns into a silent performance cliff rather than an error.
                val indexes = database.read { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery(
                            "SELECT name FROM sqlite_master WHERE type = 'index' AND name LIKE 'idx_%'",
                        ).use { rows ->
                            buildSet { while (rows.next()) add(rows.getString(1)) }
                        }
                    }
                }

                assertTrue("idx_problem_schedule_due" in indexes, indexes.toString())
                assertTrue("idx_problem_review_problem" in indexes, indexes.toString())
                assertTrue("idx_problem_review_finalized" in indexes, indexes.toString())
                assertTrue("idx_problem_review_solved_date" in indexes, indexes.toString())
            }
        }
    }

    @Test
    fun migratingIsIdempotentAcrossReopens() {
        withTempFile { path ->
            createVersionOneDatabase(path)

            BeeCodeDatabase.open(path).use { assertEquals(2, it.schemaVersion()) }
            // A second open must not re-run the migration: the renamed table it works
            // from no longer exists, so a re-run would fail rather than no-op.
            BeeCodeDatabase.open(path).use { database ->
                assertEquals(2, database.schemaVersion())
                assertNotNull(ReviewRepository(database, BeeCodeScheduler()).schedule(ProblemId("two-sum")))
            }
        }
    }

    @Test
    fun theMigrationListStaysInStepWithTheDeclaredVersion() {
        // Schema.VERSION and the migration count are checked in Schema's own init,
        // so this asserts the pair is what this build expects rather than merely
        // self-consistent.
        assertEquals(2, Schema.VERSION)
        assertEquals(Schema.VERSION, Schema.MIGRATIONS.size)
    }

    private fun withTempFile(block: (String) -> Unit) {
        val file = File.createTempFile("beecode-migration", ".db")
        file.delete()
        try {
            block(file.absolutePath)
        } finally {
            file.delete()
        }
    }

    /**
     * Build a database with version 1's original DDL, then insert real rows.
     *
     * The DDL is spelled out here rather than read from [Schema] on purpose. A
     * migration test that generated the old shape from current code would pass even
     * if version 1's definition were edited, which is the mistake the schema's own
     * "never edit an existing migration" rule exists to prevent. These are the
     * INTEGER columns as shipped.
     */
    private fun createVersionOneDatabase(path: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                for (sql in Schema.MIGRATIONS[0]) {
                    statement.execute(sql.trimIndent())
                }
            }
            // Replace the two tables the migration rewrites with their version 1
            // shape, so the widening is genuinely exercised.
            connection.createStatement().use { statement ->
                statement.execute("DROP TABLE problem_schedule")
                statement.execute("DROP TABLE problem_review")
                statement.execute(VERSION_ONE_PROBLEM_SCHEDULE)
                statement.execute("CREATE INDEX idx_problem_schedule_due ON problem_schedule (due_at)")
                statement.execute(VERSION_ONE_PROBLEM_REVIEW)
                statement.execute(
                    "CREATE INDEX idx_problem_review_problem ON problem_review (problem_id, finalized_at)",
                )
                statement.execute("CREATE INDEX idx_problem_review_finalized ON problem_review (finalized_at)")
                statement.execute(
                    "CREATE INDEX idx_problem_review_solved_date ON problem_review (counts_as_solved, local_date)",
                )
            }
            insertVersionOneRows(connection)
            connection.createStatement().use { it.execute("PRAGMA user_version = 1") }
            connection.commit()
        }
    }

    private fun insertVersionOneRows(connection: Connection) {
        connection.prepareStatement(
            """
            INSERT INTO problem_schedule (
                problem_id, stability, difficulty, due_at, last_reviewed_at,
                interval_days, review_count, lapse_count, version, updated_at
            ) VALUES ('two-sum', 4.5, 6.25, 1000000, 500000, 7, 3, 1, 9, 500000)
            """.trimIndent(),
        ).use { it.executeUpdate() }

        connection.prepareStatement(
            """
            INSERT INTO problem_review (
                review_session_id, event_id, problem_id, problem_revision_id,
                execution_run_id, outcome, rating, aided, counts_as_solved,
                finalized_at, device_id, selected_source, local_date, local_hour,
                streak_zone_id, fsrs_algorithm_id, fsrs_engine_version,
                fsrs_parameters_hash, fsrs_prev_state_hash, fsrs_prev_stability,
                fsrs_prev_difficulty, fsrs_elapsed_days, fsrs_rating_value,
                fsrs_desired_retention, fsrs_max_interval_days, fsrs_next_stability,
                fsrs_next_difficulty, fsrs_next_interval, fsrs_retrievability,
                fsrs_due_at
            ) VALUES (
                'session-1', 'event-1', 'two-sum', ?, 'run-1', 'PASSED', 'GOOD', 0, 1,
                500000, 'device-1', 'print(1)', '2026-07-29', 12, 'UTC',
                'FSRS-6.x 21-parameter snapshot', 'bee-fsrs-0.1.0',
                '0123456789abcdef', 'fedcba9876543210', 2.5, 6.5,
                5, 3, 0.9, 36500, 4.5, 6.25, 7, 0.87, 1000000
            )
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, "a".repeat(64))
            statement.executeUpdate()
        }
    }

    private companion object {
        /** Version 1's `problem_schedule`, with `interval_days` as INTEGER. */
        private val VERSION_ONE_PROBLEM_SCHEDULE = """
            CREATE TABLE problem_schedule (
                problem_id        TEXT    NOT NULL PRIMARY KEY,
                stability         REAL    NOT NULL,
                difficulty        REAL    NOT NULL,
                due_at            INTEGER NOT NULL,
                last_reviewed_at  INTEGER NOT NULL,
                interval_days     INTEGER NOT NULL,
                review_count      INTEGER NOT NULL,
                lapse_count       INTEGER NOT NULL,
                version           INTEGER NOT NULL,
                updated_at        INTEGER NOT NULL
            )
        """.trimIndent()

        /** Version 1's `problem_review`, with the three interval columns as INTEGER. */
        private val VERSION_ONE_PROBLEM_REVIEW = """
            CREATE TABLE problem_review (
                review_session_id      TEXT    NOT NULL PRIMARY KEY,
                event_id               TEXT    NOT NULL UNIQUE,
                problem_id             TEXT    NOT NULL,
                problem_revision_id    TEXT    NOT NULL,
                execution_run_id       TEXT    NOT NULL,
                outcome                TEXT    NOT NULL,
                rating                 TEXT    NOT NULL,
                aided                  INTEGER NOT NULL,
                counts_as_solved       INTEGER NOT NULL,
                finalized_at           INTEGER NOT NULL,
                device_id              TEXT    NOT NULL,
                selected_source        TEXT    NOT NULL,
                local_date             TEXT    NOT NULL,
                local_hour             INTEGER NOT NULL,
                streak_zone_id         TEXT    NOT NULL,
                fsrs_algorithm_id      TEXT    NOT NULL,
                fsrs_engine_version    TEXT    NOT NULL,
                fsrs_parameters_hash   TEXT    NOT NULL,
                fsrs_prev_state_hash   TEXT    NOT NULL,
                fsrs_prev_stability    REAL,
                fsrs_prev_difficulty   REAL,
                fsrs_elapsed_days      INTEGER NOT NULL,
                fsrs_rating_value      INTEGER NOT NULL,
                fsrs_desired_retention REAL    NOT NULL,
                fsrs_max_interval_days INTEGER NOT NULL,
                fsrs_next_stability    REAL    NOT NULL,
                fsrs_next_difficulty   REAL    NOT NULL,
                fsrs_next_interval     INTEGER NOT NULL,
                fsrs_retrievability    REAL    NOT NULL,
                fsrs_due_at            INTEGER NOT NULL
            )
        """.trimIndent()
    }
}
