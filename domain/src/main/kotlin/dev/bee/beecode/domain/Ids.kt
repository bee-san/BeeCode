package dev.bee.beecode.domain

/**
 * Stable identities for BeeCode.
 *
 * Every identity here is device-independent, because ADR 0002 commits to a
 * snapshot-merge sync model in which two devices may create rows offline and
 * later merge them. An autoincrement integer would collide across devices, so
 * no syncable row uses one for its identity.
 *
 * Identities come from exactly two places:
 *
 * - **Content**, for things the repository defines. A [ProblemId] is the folder
 *   name, so the same Problem has the same ID on every device forever.
 * - **A generated opaque string**, for things a device observes. These are
 *   supplied by the caller rather than generated here, because the domain has no
 *   randomness source. See [IdGenerator].
 */

/** A slug identifying one Problem, stable across devices and releases. */
@JvmInline
value class ProblemId(val value: String) {
    init {
        require(value.isNotEmpty()) { "ProblemId must not be empty" }
        require(value.length <= MAX_LENGTH) { "ProblemId must be at most $MAX_LENGTH characters" }
        require(value.all { it.isLowercaseAsciiLetter() || it.isAsciiDigit() || it == '-' }) {
            "ProblemId must be lowercase ASCII letters, digits, or hyphens: '$value'"
        }
        require(!value.startsWith('-') && !value.endsWith('-')) {
            "ProblemId must not start or end with a hyphen: '$value'"
        }
        require(!value.contains("--")) { "ProblemId must not contain consecutive hyphens: '$value'" }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 64
    }
}

/**
 * Identifies one compiled revision of a Problem's content.
 *
 * A revision is a content hash, not a counter. Two devices that compile the same
 * Problem source independently must agree on the revision, and a Problem whose
 * statement or tests changed must not be mistaken for the one a learner already
 * reviewed. Recorded review history therefore always names the exact content the
 * learner actually saw.
 */
@JvmInline
value class ProblemRevisionId(val value: String) {
    init {
        require(value.length == LENGTH) { "ProblemRevisionId must be $LENGTH hex characters" }
        require(value.all { it.isLowercaseHexDigit() }) {
            "ProblemRevisionId must be lowercase hexadecimal"
        }
    }

    override fun toString(): String = value

    companion object {
        /** A 256-bit content hash rendered as lowercase hex. */
        const val LENGTH: Int = 64
    }
}

/** Identifies one bounded local Python execution attempt. */
@JvmInline
value class ExecutionRunId(val value: String) {
    init {
        requireOpaqueId(value, "ExecutionRunId")
    }

    override fun toString(): String = value
}

/**
 * Identifies one scheduled attempt at a Problem.
 *
 * This is the idempotency key for finalization: the review transaction refuses
 * to finalize the same session twice, and a retried finalize returns the
 * existing outcome rather than scheduling the Problem again.
 */
@JvmInline
value class ReviewSessionId(val value: String) {
    init {
        requireOpaqueId(value, "ReviewSessionId")
    }

    override fun toString(): String = value
}

/** Identifies one appended domain event. */
@JvmInline
value class DomainEventId(val value: String) {
    init {
        requireOpaqueId(value, "DomainEventId")
    }

    override fun toString(): String = value
}

/**
 * Identifies an achievement *definition*, not an award.
 *
 * Definition IDs are content, like [ProblemId], because an award earned on one
 * device must be recognizable as the same achievement on another.
 */
@JvmInline
value class AchievementId(val value: String) {
    init {
        require(value.isNotEmpty()) { "AchievementId must not be empty" }
        require(value.length <= MAX_LENGTH) { "AchievementId must be at most $MAX_LENGTH characters" }
        require(value.all { it.isLowercaseAsciiLetter() || it.isAsciiDigit() || it == '-' }) {
            "AchievementId must be lowercase ASCII letters, digits, or hyphens: '$value'"
        }
    }

    override fun toString(): String = value

    companion object {
        const val MAX_LENGTH: Int = 64
    }
}

/**
 * Identifies the installation that produced a row.
 *
 * Unused until sync ships, and deliberately reserved now: ADR 0002 property 4.
 * Chimahon's `SyncData.deviceId` exists so a device can recognize its own
 * writes, and retrofitting that onto rows already written offline is not
 * possible. Generated once on first launch and never changed.
 */
@JvmInline
value class DeviceId(val value: String) {
    init {
        requireOpaqueId(value, "DeviceId")
    }

    override fun toString(): String = value
}

/**
 * Supplies opaque identities.
 *
 * The domain does not generate IDs itself because it has no randomness source
 * and must stay deterministic under test. Production supplies a UUIDv4-backed
 * implementation; tests supply a counter, which is what makes review and
 * achievement behaviour reproducible.
 */
interface IdGenerator {
    fun newExecutionRunId(): ExecutionRunId

    fun newReviewSessionId(): ReviewSessionId

    fun newDomainEventId(): DomainEventId

    fun newDeviceId(): DeviceId
}

/**
 * Opaque IDs are bounded and restricted to URL-safe characters so they can be
 * embedded in file names, JSON keys, and sync payloads without escaping.
 */
private fun requireOpaqueId(value: String, typeName: String) {
    require(value.isNotEmpty()) { "$typeName must not be empty" }
    require(value.length <= MAX_OPAQUE_ID_LENGTH) {
        "$typeName must be at most $MAX_OPAQUE_ID_LENGTH characters"
    }
    require(value.all { it.isOpaqueIdChar() }) {
        "$typeName must be ASCII alphanumeric, hyphen, or underscore: '$value'"
    }
}

private const val MAX_OPAQUE_ID_LENGTH = 64

private fun Char.isOpaqueIdChar(): Boolean =
    isAsciiDigit() || this in 'a'..'z' || this in 'A'..'Z' || this == '-' || this == '_'

private fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private fun Char.isLowercaseAsciiLetter(): Boolean = this in 'a'..'z'

private fun Char.isLowercaseHexDigit(): Boolean = isAsciiDigit() || this in 'a'..'f'
