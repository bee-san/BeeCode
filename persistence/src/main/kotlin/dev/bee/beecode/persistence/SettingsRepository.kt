package dev.bee.beecode.persistence

import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.fsrs.SchedulerPolicy
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import java.sql.Connection

/**
 * Profile settings, stored as a small key/value table.
 *
 * Key/value rather than a typed row because settings accrete: a new preference
 * should not need a schema migration. The typed accessors below are the only
 * intended entry points, so the loose storage does not leak into callers.
 */
class SettingsRepository(private val database: BeeCodeDatabase) {

    /**
     * This installation's identity, created on first read.
     *
     * Reserved by ADR 0002 property 4 and unused until sync ships. It exists now
     * because rows written offline before a device had an identity could never be
     * attributed retroactively — chimahon's `SyncData.deviceId` is what lets a
     * device recognize its own writes.
     */
    fun deviceId(generate: () -> DeviceId, now: Instant): DeviceId {
        get(KEY_DEVICE_ID)?.let { return DeviceId(it) }
        return database.transaction { connection ->
            // Re-read inside the transaction: two callers racing on first launch
            // must not mint two identities for one installation.
            readValue(connection, KEY_DEVICE_ID)?.let { return@transaction DeviceId(it) }
            val created = generate()
            writeValue(connection, KEY_DEVICE_ID, created.value, now)
            created
        }
    }

    /**
     * The timezone used to derive local dates for streaks and the 5am Club.
     *
     * Stored rather than read from the system every time, because a learner who
     * travels must not have their streak history silently recomputed in a new
     * zone. Defaults to the system zone on first use.
     */
    fun streakZone(): TimeZone = get(KEY_STREAK_ZONE)
        ?.let { id -> runCatching { TimeZone.of(id) }.getOrNull() }
        ?: TimeZone.currentSystemDefault()

    fun setStreakZone(zone: TimeZone, now: Instant) = put(KEY_STREAK_ZONE, zone.id, now)

    /** The learner's scheduling preferences. */
    fun schedulerPolicy(): SchedulerPolicy {
        val retention = get(KEY_DESIRED_RETENTION)?.toDoubleOrNull()
        val maxInterval = get(KEY_MAX_INTERVAL_DAYS)?.toIntOrNull()
        val parameters = get(KEY_FSRS_PARAMETERS)?.let { encoded ->
            val values = encoded.split(',').mapNotNull { it.trim().toDoubleOrNull() }
            values.takeIf { it.size == SchedulerPolicy.PARAMETER_COUNT }?.toDoubleArray()
        }
        // Each field falls back independently, and an unparseable value falls back
        // rather than throwing: a corrupted setting must not make the app
        // unopenable when a sane default exists.
        return runCatching {
            SchedulerPolicy(
                desiredRetention = retention ?: SchedulerPolicy.DEFAULT_DESIRED_RETENTION,
                maximumIntervalDays = maxInterval ?: SchedulerPolicy.DEFAULT_MAXIMUM_INTERVAL_DAYS,
                parameters = parameters,
            )
        }.getOrDefault(SchedulerPolicy.DEFAULT)
    }

    fun setSchedulerPolicy(policy: SchedulerPolicy, now: Instant) {
        put(KEY_DESIRED_RETENTION, policy.desiredRetention.toString(), now)
        put(KEY_MAX_INTERVAL_DAYS, policy.maximumIntervalDays.toString(), now)
        policy.parameters?.let { put(KEY_FSRS_PARAMETERS, it.joinToString(","), now) }
    }

    /** Daily limit on how many due Problems the queue offers, or null for no limit. */
    fun dailyReviewLimit(): Int? = get(KEY_DAILY_REVIEW_LIMIT)?.toIntOrNull()?.takeIf { it > 0 }

    fun setDailyReviewLimit(limit: Int?, now: Instant) {
        if (limit == null) remove(KEY_DAILY_REVIEW_LIMIT) else put(KEY_DAILY_REVIEW_LIMIT, limit.toString(), now)
    }

    /**
     * Path to the shared file this device syncs through, if the learner set one.
     *
     * Storage the learner already owns — a folder Dropbox, Syncthing, or a network share
     * replicates. Absent means sync is off, which is the default: it is opt-in because it
     * writes source code somewhere BeeCode does not control (ADR 0002).
     */
    fun syncFilePath(): String? = get(KEY_SYNC_FILE)?.takeIf { it.isNotBlank() }

    fun setSyncFilePath(path: String?, now: Instant) {
        if (path.isNullOrBlank()) remove(KEY_SYNC_FILE) else put(KEY_SYNC_FILE, path, now)
    }

    /** Path to a Python interpreter chosen by the learner, if any. */
    fun pythonExecutable(): String? = get(KEY_PYTHON_EXECUTABLE)?.takeIf { it.isNotBlank() }

    fun setPythonExecutable(path: String?, now: Instant) {
        if (path.isNullOrBlank()) remove(KEY_PYTHON_EXECUTABLE) else put(KEY_PYTHON_EXECUTABLE, path, now)
    }

    // ---- Raw access -----------------------------------------------------

    fun get(key: String): String? = database.read { readValue(it, key) }

    fun put(key: String, value: String, now: Instant) {
        database.transaction { writeValue(it, key, value, now) }
    }

    fun remove(key: String) {
        database.transaction { connection ->
            connection.prepareStatement("DELETE FROM settings WHERE key = ?").use { statement ->
                statement.setString(1, key)
                statement.executeUpdate()
            }
        }
    }

    /** Every setting, for export. */
    fun all(): Map<String, String> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT key, value FROM settings").use { rows ->
                buildMap { while (rows.next()) put(rows.getString("key"), rows.getString("value")) }
            }
        }
    }

    /**
     * Every setting with the instant it was last written.
     *
     * Needed because a snapshot merge (ADR 0002) resolves settings by last-write-wins,
     * and a value without its timestamp cannot be merged — only clobbered. The column
     * has always been stored; this exposes it.
     */
    fun allStamped(): Map<String, StampedSetting> = database.read { connection ->
        connection.createStatement().use { statement ->
            statement.executeQuery("SELECT key, value, updated_at FROM settings").use { rows ->
                buildMap {
                    while (rows.next()) {
                        put(
                            rows.getString("key"),
                            StampedSetting(
                                value = rows.getString("value"),
                                updatedAt = Instant.fromEpochMilliseconds(rows.getLong("updated_at")),
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun readValue(connection: Connection, key: String): String? =
        connection.prepareStatement("SELECT value FROM settings WHERE key = ?").use { statement ->
            statement.setString(1, key)
            statement.executeQuery().use { rows -> if (rows.next()) rows.getString("value") else null }
        }

    private fun writeValue(connection: Connection, key: String, value: String, now: Instant) {
        connection.prepareStatement(
            """
            INSERT INTO settings (key, value, updated_at) VALUES (?, ?, ?)
            ON CONFLICT(key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at
            """.trimIndent(),
        ).use { statement ->
            statement.setString(1, key)
            statement.setString(2, value)
            statement.setLong(3, now.toEpochMilliseconds())
            statement.executeUpdate()
        }
    }

    companion object {
        const val KEY_DEVICE_ID = "device.id"
        const val KEY_STREAK_ZONE = "streak.zone"
        const val KEY_DESIRED_RETENTION = "fsrs.desiredRetention"
        const val KEY_MAX_INTERVAL_DAYS = "fsrs.maximumIntervalDays"
        const val KEY_FSRS_PARAMETERS = "fsrs.parameters"
        const val KEY_DAILY_REVIEW_LIMIT = "review.dailyLimit"
        const val KEY_PYTHON_EXECUTABLE = "python.executable"
        const val KEY_SYNC_FILE = "sync.file"
    }
}

/**
 * One setting with the instant it was last written.
 *
 * The timestamp exists for snapshot merge: two devices that both changed a preference
 * are resolved by whichever wrote last (ADR 0002 property 2).
 */
data class StampedSetting(
    val value: String,
    val updatedAt: Instant,
)
