package dev.bee.beecode.persistence

import java.sql.Connection

/**
 * Durable storage for the Leaderboard activity outbox.
 *
 * `ActivityOutbox` in `:shared` owns every state transition as a pure function over a
 * list; this owns getting that list on and off disk. The split is deliberate — the
 * transitions are where double-counting would happen and they are exhaustively tested
 * without a database, while this layer has one job and no policy.
 *
 * The rows are stored as flat columns rather than serialized JSON so a stuck queue can be
 * inspected with `sqlite3` when a learner reports one, and so the batch query can be an
 * index scan rather than a full deserialize-and-filter.
 *
 * ### Why replaceAll rather than per-row updates
 *
 * The pure state machine returns a whole new list, so the natural persistence operation is
 * "make the table equal this list". Doing it in one transaction means a process death
 * cannot leave the queue half-advanced — say, a row marked in-flight while its sibling
 * still reads pending, from one upload batch. Per-row updates would be less code and more
 * states.
 *
 * The cost is rewriting rows that did not change. That is acceptable because the queue is
 * bounded in practice: a learner solves a few Problems a day, and acknowledged rows prune
 * after a week.
 *
 * This layer holds **no domain types**. It speaks in primitives, and `:shared` maps them,
 * because `:persistence` must not depend on `:shared` — the dependency runs the other way.
 */
class ActivityOutboxRepository(private val database: BeeCodeDatabase) {

    /** Every queued row, oldest event first. */
    fun all(): List<StoredOutboxRow> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery(
                """
                SELECT event_id, problem_id, occurred_at, local_date, counts_as_solved,
                       state, attempts, next_attempt_at, last_reason
                FROM activity_outbox
                ORDER BY occurred_at, event_id
                """.trimIndent(),
            ).use { rows ->
                buildList {
                    while (rows.next()) {
                        add(
                            StoredOutboxRow(
                                eventId = rows.getString("event_id"),
                                problemId = rows.getString("problem_id"),
                                occurredAtEpochMillis = rows.getLong("occurred_at"),
                                localDate = rows.getString("local_date"),
                                countsAsSolved = rows.getInt("counts_as_solved") != 0,
                                state = rows.getString("state"),
                                attempts = rows.getInt("attempts"),
                                nextAttemptAtEpochMillis = rows.getLong("next_attempt_at"),
                                lastReason = rows.getString("last_reason"),
                            ),
                        )
                    }
                }
            }
        }
    }

    /**
     * Make the stored queue equal [rows], atomically.
     *
     * Delete-then-insert inside one transaction. A row absent from [rows] has been pruned,
     * and a row present has whatever state the machine just computed; there is no third
     * case, so reconciling row by row would only add ways to be half-applied.
     */
    fun replaceAll(rows: List<StoredOutboxRow>) {
        database.transaction { connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM activity_outbox") }
            if (rows.isEmpty()) return@transaction
            connection.prepareStatement(
                """
                INSERT INTO activity_outbox (
                    event_id, problem_id, occurred_at, local_date, counts_as_solved,
                    state, attempts, next_attempt_at, last_reason
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """.trimIndent(),
            ).use { statement ->
                for (row in rows) {
                    statement.setString(1, row.eventId)
                    statement.setString(2, row.problemId)
                    statement.setLong(3, row.occurredAtEpochMillis)
                    statement.setString(4, row.localDate)
                    statement.setInt(5, if (row.countsAsSolved) 1 else 0)
                    statement.setString(6, row.state)
                    statement.setInt(7, row.attempts)
                    statement.setLong(8, row.nextAttemptAtEpochMillis)
                    statement.setString(9, row.lastReason)
                    statement.addBatch()
                }
                statement.executeBatch()
            }
        }
    }

    /**
     * How many rows are in each state, without loading them.
     *
     * For a status line, which is read far more often than the queue is uploaded. Counting
     * in SQL keeps a settings screen from deserializing a week of acknowledged rows to
     * display a number.
     */
    fun countsByState(): Map<String, Int> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT state, COUNT(*) AS total FROM activity_outbox GROUP BY state")
                .use { rows ->
                    buildMap { while (rows.next()) put(rows.getString("state"), rows.getInt("total")) }
                }
        }
    }

    /** Discard the whole queue, for the learner who unlinks their account. */
    fun clear() {
        database.transaction { connection: Connection ->
            connection.createStatement().use { it.executeUpdate("DELETE FROM activity_outbox") }
        }
    }
}

/**
 * One outbox row as stored, in primitives.
 *
 * Not `OutboxRow` from `:shared`: this module cannot see that type, and should not — the
 * dependency runs `:shared` → `:persistence`. Mapping between the two lives in `:shared`,
 * which is the layer that already knows both the wire shape and the domain.
 */
data class StoredOutboxRow(
    val eventId: String,
    val problemId: String,
    val occurredAtEpochMillis: Long,
    val localDate: String,
    val countsAsSolved: Boolean,
    val state: String,
    val attempts: Int,
    val nextAttemptAtEpochMillis: Long,
    val lastReason: String?,
)
