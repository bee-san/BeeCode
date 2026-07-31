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
 * Migrations, exercised against real databases built at the old shape.
 *
 * Version 2 exists because FSRS-7 schedules in fractional days, and `interval_days`
 * was declared INTEGER. The danger is specific and quiet: SQLite would coerce a
 * ten-minute interval toward zero, so the exact case the new algorithm exists to
 * handle would be the case that silently broke. Version 4 adds `topic_schedule`,
 * the card a learner actually studies.
 *
 * These tests build a **real old database** with the original DDL and real rows,
 * then open it through [BeeCodeDatabase] and check the data survived. Asserting
 * against the migration SQL alone would only prove the statements parse; a learner
 * upgrading BeeCode cares whether their review history is still there and still
 * correct afterwards.
 *
 * The old DDL is spelled out in this file rather than generated from [Schema], so a
 * test cannot be made to pass by editing a shipped migration — which is the mistake
 * the schema's "never edit an existing migration" rule exists to prevent.
 */
class SchemaMigrationTest {

    @Test
    fun aVersionOneDatabaseUpgradesAndKeepsItsRows() {
        withTempFile { path ->
            createVersionOneDatabase(path)

            BeeCodeDatabase.open(path).use { database ->
                assertEquals(Schema.VERSION, database.schemaVersion())
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

            BeeCodeDatabase.open(path).use { assertEquals(Schema.VERSION, it.schemaVersion()) }
            // A second open must not re-run the migration: the renamed table it works
            // from no longer exists, so a re-run would fail rather than no-op.
            BeeCodeDatabase.open(path).use { database ->
                assertEquals(Schema.VERSION, database.schemaVersion())
                assertNotNull(ReviewRepository(database, BeeCodeScheduler()).schedule(ProblemId("two-sum")))
            }
        }
    }

    @Test
    fun aVersionThreeDatabaseGainsTheTopicScheduleAndKeepsItsRows() {
        withTempFile { path ->
            createVersionThreeDatabase(path)

            BeeCodeDatabase.open(path).use { database ->
                assertEquals(Schema.VERSION, database.schemaVersion())

                // Version 4 only appends a table. The learner's existing history is
                // the thing a migration is most able to quietly damage, so it is
                // checked even when nothing was supposed to touch it.
                val schedule = assertNotNull(
                    ReviewRepository(database, BeeCodeScheduler()).schedule(ProblemId("two-sum")),
                )
                assertEquals(7.0, schedule.intervalDays)
                assertEquals(9L, schedule.version)
                assertNotNull(
                    ReviewRepository(database, BeeCodeScheduler()).review(ReviewSessionId("session-1")),
                )

                assertTrue("topic_schedule" in tableNames(database), tableNames(database).toString())
                assertTrue("idx_topic_schedule_due" in indexNames(database), indexNames(database).toString())
            }
        }
    }

    @Test
    fun theTopicIntervalIsFractionalFromTheStart() {
        withTempFile { path ->
            createVersionThreeDatabase(path)

            BeeCodeDatabase.open(path).use { database ->
                // `problem_schedule` shipped this column as INTEGER and needed a whole
                // rename-copy-drop migration to widen it, because SQLite cannot alter a
                // declared type. A ten-minute topic interval must survive a round trip
                // on the first day this table exists, not after a version 5.
                database.transaction { connection ->
                    connection.prepareStatement(
                        """
                        INSERT INTO topic_schedule (
                            topic, stability, difficulty, due_at, last_reviewed_at,
                            interval_days, review_count, lapse_count, version, updated_at
                        ) VALUES ('dynamic-programming', 2.5, 6.0, 1000600, 1000000, ?, 1, 0, 1, 1000000)
                        """.trimIndent(),
                    ).use { statement ->
                        statement.setDouble(1, 10.0 / 1_440.0)
                        statement.executeUpdate()
                    }
                }

                val stored = database.read { connection ->
                    connection.createStatement().use { statement ->
                        statement.executeQuery(
                            "SELECT interval_days FROM topic_schedule WHERE topic = 'dynamic-programming'",
                        ).use { rows ->
                            assertTrue(rows.next(), "the row must be there to read")
                            rows.getDouble(1)
                        }
                    }
                }
                assertEquals(10.0 / 1_440.0, stored)
            }
        }
    }

    @Test
    fun aFreshDatabaseAndAMigratedOneEndUpTheSameShape() {
        // The upgrade path and the create-from-empty path both reach version 4, and
        // it would be easy for them not to agree: a table added only to the last
        // migration is invisible to anyone reading version 1's DDL. Comparing the
        // two schemas is what makes "append a migration" actually equivalent to
        // "declare the table".
        withTempFile { migratedPath ->
            createVersionThreeDatabase(migratedPath)
            val migrated = BeeCodeDatabase.open(migratedPath).use { schemaShape(it) }

            withTempFile { freshPath ->
                val fresh = BeeCodeDatabase.open(freshPath).use { schemaShape(it) }
                assertEquals(fresh, migrated)
            }
        }
    }

    @Test
    fun theMigrationListStaysInStepWithTheDeclaredVersion() {
        // Schema's own init already checks these agree. What this adds is that the
        // count is what a reviewer expects to see change: bumping VERSION without
        // appending a migration, or vice versa, fails here with a number to read.
        assertEquals(4, Schema.VERSION, "bump this deliberately when adding a migration")
        assertEquals(Schema.VERSION, Schema.MIGRATIONS.size)
    }

    private fun tableNames(database: BeeCodeDatabase): Set<String> =
        namesFromMaster(database, "type = 'table' AND name NOT LIKE 'sqlite_%'")

    private fun indexNames(database: BeeCodeDatabase): Set<String> =
        namesFromMaster(database, "type = 'index'")

    private fun namesFromMaster(database: BeeCodeDatabase, predicate: String): Set<String> =
        database.read { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("SELECT name FROM sqlite_master WHERE $predicate").use { rows ->
                    buildSet { while (rows.next()) add(rows.getString(1)) }
                }
            }
        }

    /**
     * The database's shape, as a comparable set of facts.
     *
     * Column names with their *declared* types, plus nullability and primary keys,
     * rather than the raw `sqlite_master` SQL — two databases can be identical and
     * still store differently indented DDL text, and this test is about the shape
     * rather than the formatting. Declared types are the interesting part: the whole
     * reason version 2 exists is that a column was declared INTEGER.
     */
    private fun schemaShape(database: BeeCodeDatabase): Set<String> {
        val tables = tableNames(database)
        val columns = database.read { connection ->
            buildSet {
                for (table in tables) {
                    connection.createStatement().use { statement ->
                        statement.executeQuery("PRAGMA table_info($table)").use { rows ->
                            while (rows.next()) {
                                val name = rows.getString("name")
                                val type = rows.getString("type")
                                val notNull = rows.getInt("notnull")
                                val primaryKey = rows.getInt("pk")
                                add("$table.$name $type notnull=$notNull pk=$primaryKey")
                            }
                        }
                    }
                }
            }
        }
        return columns + indexNames(database).map { "index $it" }
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

    /**
     * Build a database with version 3's shape, then insert real rows.
     *
     * Spelled out here for the reason [createVersionOneDatabase] explains, and
     * spelled out *in full* rather than assembled from version 1's DDL plus the
     * later migrations: this is the shape a learner's file is actually in before
     * version 4 runs, and reading it from [Schema] would make the test agree with
     * whatever the code currently says instead of with what shipped.
     */
    private fun createVersionThreeDatabase(path: String) {
        DriverManager.getConnection("jdbc:sqlite:$path").use { connection ->
            connection.autoCommit = false
            connection.createStatement().use { statement ->
                for (sql in VERSION_THREE_DDL) {
                    statement.execute(sql)
                }
            }
            insertVersionOneRows(connection)
            connection.createStatement().use { it.execute("PRAGMA user_version = 3") }
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
        /**
         * Version 3's complete DDL, as an installed client's file actually stands.
         *
         * The tables version 2 widened appear here already widened, because a learner
         * arriving at version 4 has run version 2. What this must *not* contain is
         * `topic_schedule` — that is the thing version 4 adds, and if it crept in here
         * the migration test would pass without the migration running.
         */
        private val VERSION_THREE_DDL: List<String> = listOf(
            """
            CREATE TABLE settings (
                key         TEXT    NOT NULL PRIMARY KEY,
                value       TEXT    NOT NULL,
                updated_at  INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE solution_draft (
                problem_id           TEXT    NOT NULL PRIMARY KEY,
                problem_revision_id  TEXT    NOT NULL,
                source               TEXT    NOT NULL,
                starter_baseline     TEXT    NOT NULL,
                version              INTEGER NOT NULL,
                updated_at           INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE problem_schedule (
                problem_id        TEXT    NOT NULL PRIMARY KEY,
                stability         REAL    NOT NULL,
                difficulty        REAL    NOT NULL,
                due_at            INTEGER NOT NULL,
                last_reviewed_at  INTEGER NOT NULL,
                interval_days     REAL    NOT NULL,
                review_count      INTEGER NOT NULL,
                lapse_count       INTEGER NOT NULL,
                version           INTEGER NOT NULL,
                updated_at        INTEGER NOT NULL
            )
            """,
            "CREATE INDEX idx_problem_schedule_due ON problem_schedule (due_at)",
            """
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
                fsrs_elapsed_days      REAL    NOT NULL,
                fsrs_rating_value      INTEGER NOT NULL,
                fsrs_desired_retention REAL    NOT NULL,
                fsrs_max_interval_days REAL    NOT NULL,
                fsrs_next_stability    REAL    NOT NULL,
                fsrs_next_difficulty   REAL    NOT NULL,
                fsrs_next_interval     REAL    NOT NULL,
                fsrs_retrievability    REAL    NOT NULL,
                fsrs_due_at            INTEGER NOT NULL
            )
            """,
            "CREATE INDEX idx_problem_review_problem ON problem_review (problem_id, finalized_at)",
            "CREATE INDEX idx_problem_review_finalized ON problem_review (finalized_at)",
            "CREATE INDEX idx_problem_review_solved_date ON problem_review (counts_as_solved, local_date)",
            """
            CREATE TABLE achievement_award (
                achievement_id  TEXT    NOT NULL PRIMARY KEY,
                awarded_at      INTEGER NOT NULL,
                local_date      TEXT    NOT NULL,
                detail_json     TEXT,
                updated_at      INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE achievement_progress (
                achievement_id  TEXT    NOT NULL PRIMARY KEY,
                progress_json   TEXT    NOT NULL,
                updated_at      INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE projection_cursor (
                name             TEXT    NOT NULL PRIMARY KEY,
                last_finalized_at INTEGER NOT NULL,
                last_session_id  TEXT    NOT NULL,
                updated_at       INTEGER NOT NULL
            )
            """,
            """
            CREATE TABLE activity_outbox (
                event_id        TEXT    NOT NULL PRIMARY KEY,
                problem_id      TEXT    NOT NULL,
                occurred_at     INTEGER NOT NULL,
                local_date      TEXT    NOT NULL,
                counts_as_solved INTEGER NOT NULL,
                state           TEXT    NOT NULL,
                attempts        INTEGER NOT NULL,
                next_attempt_at INTEGER NOT NULL,
                last_reason     TEXT
            )
            """,
            "CREATE INDEX idx_activity_outbox_ready ON activity_outbox (state, next_attempt_at, occurred_at)",
        ).map { it.trimIndent() }

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
