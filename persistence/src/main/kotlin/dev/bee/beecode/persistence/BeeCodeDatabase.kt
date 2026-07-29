package dev.bee.beecode.persistence

import kotlinx.datetime.Instant
import java.io.Closeable
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException

/**
 * Drop precision finer than a millisecond.
 *
 * Every instant column in the schema is epoch milliseconds, so a value written
 * with nanoseconds comes back different from what was stored. Truncating before a
 * write, and returning the truncated value to the caller, keeps in-memory state
 * and stored state identical by construction rather than nearly equal.
 */
internal fun Instant.truncatedToMillis(): Instant =
    Instant.fromEpochMilliseconds(toEpochMilliseconds())

/**
 * The local SQLite database: BeeCode's authority for all study state.
 *
 * Opening this runs migrations to [Schema.VERSION]. Nothing else in the codebase
 * opens a connection, so the pragmas and the migration path have exactly one
 * home.
 *
 * ### Concurrency model
 *
 * One connection, serialized by a lock. SQLite permits more, but a single writer
 * is the honest model for a single-user study app and removes an entire class of
 * `SQLITE_BUSY` bugs that would otherwise appear only under the timing a real
 * learner produces. WAL mode is still enabled because it makes reads cheap and,
 * more importantly, makes crash recovery a documented rollback rather than a
 * corrupted file.
 */
class BeeCodeDatabase private constructor(
    private val connection: Connection,
    /** Null for an in-memory database. */
    val databasePath: String?,
) : Closeable {

    private val lock = Any()

    /**
     * Run [block] inside a single write transaction.
     *
     * `BEGIN IMMEDIATE` rather than the default deferred begin: it takes the
     * write lock up front, so a transaction that intends to write cannot discover
     * halfway through that another writer got there first. That is what makes the
     * compare-and-swap in review finalization meaningful.
     *
     * The block either commits entirely or rolls back entirely. A partially
     * finalized review — schedule advanced but no review row, or vice versa — is
     * the specific corruption this exists to make impossible.
     */
    fun <T> transaction(block: (Connection) -> T): T = synchronized(lock) {
        connection.createStatement().use { it.execute("BEGIN IMMEDIATE") }
        try {
            val result = block(connection)
            connection.createStatement().use { it.execute("COMMIT") }
            result
        } catch (e: Throwable) {
            // Rollback failure must not mask the original cause: the caller needs
            // to know what actually went wrong, and a failed rollback is
            // additional information rather than a replacement for it.
            try {
                connection.createStatement().use { it.execute("ROLLBACK") }
            } catch (rollbackFailure: SQLException) {
                e.addSuppressed(rollbackFailure)
            }
            throw e
        }
    }

    /** Run [block] for reading. Held under the same lock as writes. */
    fun <T> read(block: (Connection) -> T): T = synchronized(lock) { block(connection) }

    override fun close() = synchronized(lock) {
        // Checkpoint so the WAL is folded into the main database file. Without
        // this, a backup taken by copying the file could miss recent commits.
        runCatching { connection.createStatement().use { it.execute("PRAGMA wal_checkpoint(TRUNCATE)") } }
        connection.close()
    }

    companion object {
        /** Open or create the database at [path], migrating it to the current version. */
        fun open(path: String): BeeCodeDatabase {
            val connection = DriverManager.getConnection("jdbc:sqlite:$path")
            return configure(connection, path)
        }

        /**
         * Open a private in-memory database.
         *
         * Used by tests, and by nothing else: an in-memory profile would lose
         * every review on exit.
         */
        fun inMemory(): BeeCodeDatabase {
            val connection = DriverManager.getConnection("jdbc:sqlite::memory:")
            return configure(connection, null)
        }

        private fun configure(connection: Connection, path: String?): BeeCodeDatabase {
            // Explicit transaction control. JDBC autocommit would defeat
            // BEGIN IMMEDIATE and silently commit each statement separately.
            connection.autoCommit = true

            connection.createStatement().use { statement ->
                // Enforce declared foreign keys. Off by default in SQLite, which
                // means a schema can look relational and behave otherwise.
                statement.execute("PRAGMA foreign_keys = ON")

                // WAL: readers do not block the writer, and an interrupted write
                // is recovered by replaying the log rather than leaving a torn
                // page. In-memory databases do not support it.
                if (path != null) {
                    statement.execute("PRAGMA journal_mode = WAL")
                    // FULL, not NORMAL. NORMAL can lose the most recent commits on
                    // an OS crash or power loss, and BeeCode's plan treats losing
                    // a finalized review as a nonwaivable defect. The cost is one
                    // fsync per review, which is imperceptible at study pace.
                    statement.execute("PRAGMA synchronous = FULL")
                }

                // Wait rather than failing immediately if another connection holds
                // the lock. Belt-and-braces: the single-connection model should
                // mean this never triggers.
                statement.execute("PRAGMA busy_timeout = 5000")
            }

            val database = BeeCodeDatabase(connection, path)
            database.migrate()
            return database
        }
    }

    /**
     * Bring the database up to [Schema.VERSION].
     *
     * Uses SQLite's own `user_version`, so the version travels with the file and a
     * restored backup is recognized correctly.
     *
     * Each migration runs in its own transaction, so an interrupted upgrade leaves
     * the database at a known version rather than half-migrated.
     */
    private fun migrate() {
        val current = read { connection ->
            connection.createStatement().use { statement ->
                statement.executeQuery("PRAGMA user_version").use { rows ->
                    if (rows.next()) rows.getInt(1) else 0
                }
            }
        }

        if (current > Schema.VERSION) {
            // A newer BeeCode wrote this file. Guessing would risk destroying
            // reviews, so refuse and let the UI tell the learner to upgrade.
            throw IllegalStateException(
                "This profile was created by a newer version of BeeCode " +
                    "(database version $current, this build supports ${Schema.VERSION}). " +
                    "Upgrade BeeCode to open it.",
            )
        }

        for (version in (current + 1)..Schema.VERSION) {
            transaction { connection ->
                for (sql in Schema.MIGRATIONS[version - 1]) {
                    connection.createStatement().use { it.execute(sql.trimIndent()) }
                }
                // Set inside the same transaction as the statements it describes,
                // so the recorded version cannot disagree with the actual shape.
                connection.createStatement().use { it.execute("PRAGMA user_version = $version") }
            }
        }
    }

    /** The schema version actually recorded in the file. */
    fun schemaVersion(): Int = read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("PRAGMA user_version").use { rows ->
                if (rows.next()) rows.getInt(1) else 0
            }
        }
    }
}
