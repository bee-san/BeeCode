package dev.bee.beecode.app

/**
 * Remote storage for one learner's profile snapshot.
 *
 * The abstraction ADR 0002 takes from chimahon's `SyncService`: **dumb storage plus an
 * optimistic-concurrency token**. There is deliberately no server-side domain logic
 * here — no merge, no validation, no notion of a review. A backend that understood
 * BeeCode's data would be a service the learner has to trust; one that stores an opaque
 * blob is storage the learner already owns.
 *
 * That is why this interface is three methods over strings. WebDAV, a file in a synced
 * folder, Google Drive, and a self-hosted endpoint can all satisfy it, and none of them
 * needs to be told what a snapshot means.
 *
 * ### The concurrency token
 *
 * [SyncSnapshot.token] is whatever the backend uses to say "this is the version you
 * read" — an HTTP ETag, a Drive file revision, a modification time, a content hash. It
 * is opaque to BeeCode and only ever compared for equality by the backend itself.
 *
 * [push] takes the token that was read and **must fail rather than overwrite** if the
 * remote has moved since. That is the compare-and-swap: losing it means another device
 * pushed first, and the correct response is to re-pull, merge again, and retry — never
 * to force. Forcing would silently discard whatever the other device wrote, which is the
 * one failure mode a sync system must not have.
 *
 * ### What implementations must guarantee
 *
 * 1. **Never throw.** Every failure is a [SyncOutcome]; a sync that crashes the app is
 *    worse than one that reports it could not run.
 * 2. **Never partially write.** A push either lands whole or not at all. A truncated
 *    snapshot is worse than a stale one, because it looks valid.
 * 3. **Treat the payload as opaque.** Do not parse, reformat, or re-serialize it. The
 *    bytes are compared for equality by the merge's determinism guarantee.
 */
interface SyncStore {

    /** Names this backend in diagnostics and in the UI. */
    val storeId: String

    /** Read the remote snapshot, or report that there is not one yet. */
    suspend fun pull(): SyncOutcome<SyncSnapshot?>

    /**
     * Write [payloadText], but only if the remote is still at [expectedToken].
     *
     * @param expectedToken the token from the [pull] this push is based on, or null to
     *   mean "there was nothing there". Passing null when something *does* exist must
     *   fail with [SyncOutcome.Conflict] rather than overwrite it.
     * @return the new token, so a caller can push again without re-pulling.
     */
    suspend fun push(payloadText: String, expectedToken: String?): SyncOutcome<String>
}

/** A snapshot as stored remotely, with the token identifying this version. */
data class SyncSnapshot(
    val payloadText: String,
    val token: String,
)

/**
 * The result of a sync operation.
 *
 * A sealed result rather than exceptions, because every one of these is an ordinary
 * condition on a network: the remote is unreachable, the credentials expired, another
 * device pushed first. None is a bug, and all of them need to reach the UI as words.
 */
sealed interface SyncOutcome<out T> {

    data class Success<T>(val value: T) : SyncOutcome<T>

    /**
     * The remote moved since it was read, so the push was refused.
     *
     * Not an error — this is the compare-and-swap working. The caller should re-pull,
     * merge, and retry.
     */
    data class Conflict(val reason: String) : SyncOutcome<Nothing>

    /** The backend could not be reached, or refused the request. */
    data class Unavailable(val reason: String) : SyncOutcome<Nothing>
}
