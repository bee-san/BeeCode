package dev.bee.beecode.app

import kotlinx.datetime.Instant

/**
 * One sync round: pull, merge, apply locally, push, retry on conflict.
 *
 * This is the loop ADR 0002 describes, and it is deliberately thin — all the judgement
 * lives in [SnapshotMerge], all the I/O in a [SyncStore]. What remains here is the
 * *ordering*, and the ordering is the part that can lose data if written casually.
 *
 * ### Why this order
 *
 * ```
 * export local  ->  pull remote  ->  merge  ->  restore merged locally  ->  push merged
 * ```
 *
 * **Restore before push, not after.** If the push fails, the local profile has already
 * absorbed the remote's work, so nothing is lost and the next sync pushes it. If the
 * order were reversed and the process died between push and restore, the remote would
 * hold reviews this device had never applied — recoverable, but only by a sync that
 * happened to run again.
 *
 * **Merge before either.** The pushed snapshot is the *merged* one, never the local one.
 * Pushing local-only would discard the remote's reviews on every sync from a second
 * device, which is the classic way a "sync" feature silently eats data.
 *
 * ### Conflict handling
 *
 * A [SyncOutcome.Conflict] means another device pushed between this device's pull and
 * push. That is expected, not exceptional. The response is to start over — re-pull,
 * re-merge, re-push — because the merge is the only thing that knows how to combine the
 * two, and it is cheap and commutative. [MAX_ATTEMPTS] bounds the retries so a remote
 * being written continuously cannot spin forever.
 *
 * A push is **never** forced. Losing the compare-and-swap repeatedly reports failure and
 * leaves the remote alone; the local profile still has everything it pulled.
 */
class SyncService(
    private val store: SyncStore,
    private val profile: BeeCodeProfile,
) {

    /**
     * Run one sync.
     *
     * @param now stamped into restored rows; supplied rather than read from a clock so a
     *   sync is reproducible under test.
     */
    suspend fun sync(now: Instant): SyncReport {
        var lastConflict: String? = null

        repeat(MAX_ATTEMPTS) { attempt ->
            // Exported fresh each attempt: a retry after a conflict must include
            // anything the previous attempt's restore just applied locally.
            val localText = ProfileTransfer.export(profile, now)

            val remote = when (val pulled = store.pull()) {
                is SyncOutcome.Success -> pulled.value
                is SyncOutcome.Unavailable -> return SyncReport.Failed(pulled.reason)
                // A pull cannot conflict; a backend reporting one is misbehaving, and
                // saying so beats treating it as success.
                is SyncOutcome.Conflict ->
                    return SyncReport.Failed("The store reported a conflict on read: ${pulled.reason}")
            }

            // Nothing there yet: this device seeds the remote. Still a real push, so it
            // goes through the same compare-and-swap with a null expected token — which
            // a correct backend refuses if something appeared in the meantime.
            if (remote == null) {
                return when (val pushed = store.push(localText, expectedToken = null)) {
                    is SyncOutcome.Success -> SyncReport.Completed(
                        merge = null,
                        pushed = true,
                        token = pushed.value,
                    )
                    is SyncOutcome.Conflict -> {
                        lastConflict = pushed.reason
                        return@repeat // Something appeared; retry with it.
                    }
                    is SyncOutcome.Unavailable -> SyncReport.Failed(pushed.reason)
                }
            }

            val merged = when (val result = SnapshotMerge.merge(localText, remote.payloadText)) {
                is MergeResult.Merged -> result
                is MergeResult.Failed -> return SyncReport.Failed(result.reason)
            }

            // Apply locally first. Restore is additive and idempotent, so doing this on
            // a retry costs nothing and repeats no effect.
            val restored = ProfileTransfer.restore(profile, merged.payloadText, now)
            if (restored is RestoreResult.Failed) {
                return SyncReport.Failed("The merged snapshot would not restore: ${restored.reason}")
            }

            // Nothing to send back: the remote already contained everything local had.
            // Skipping the push avoids a write that would only bump the token and make
            // every *other* device see a spurious conflict.
            if (!localAddsAnythingTo(merged)) {
                return SyncReport.Completed(merge = merged, pushed = false, token = remote.token)
            }

            when (val pushed = store.push(merged.payloadText, expectedToken = remote.token)) {
                is SyncOutcome.Success ->
                    return SyncReport.Completed(merge = merged, pushed = true, token = pushed.value)
                is SyncOutcome.Unavailable -> return SyncReport.Failed(pushed.reason)
                is SyncOutcome.Conflict -> {
                    // Another device won the race. Its work is now in the remote, and
                    // this device's work is still local, so looping is safe.
                    lastConflict = pushed.reason
                    if (attempt == MAX_ATTEMPTS - 1) {
                        return SyncReport.Conflicted(
                            attempts = MAX_ATTEMPTS,
                            reason = pushed.reason,
                        )
                    }
                }
            }
        }

        return SyncReport.Conflicted(
            attempts = MAX_ATTEMPTS,
            reason = lastConflict ?: "The remote kept changing during sync.",
        )
    }

    /**
     * Whether the merged snapshot differs from what the remote already holds.
     *
     * Derived from the merge counts rather than by comparing strings: if nothing came
     * *from* the remote then local was a superset and there is something to send, and if
     * everything local was already present there is not. `reviewsAlreadyPresent` is not
     * enough on its own, because drafts and settings can differ with no review changes.
     */
    private fun localAddsAnythingTo(merged: MergeResult.Merged): Boolean =
        merged.localOnlyReviews > 0 || merged.localOnlyDrafts > 0 || merged.localOnlySettings > 0

    companion object {
        /**
         * How many times to re-pull and retry after losing the compare-and-swap.
         *
         * Three, because each attempt is a full round trip and a learner with two
         * devices will not realistically lose three races in a row. A remote under
         * continuous write from elsewhere reports [SyncReport.Conflicted] rather than
         * spinning, which is the honest outcome.
         */
        const val MAX_ATTEMPTS: Int = 3
    }
}

/** What one sync round did. */
sealed interface SyncReport {

    /**
     * Sync ran. [pushed] distinguishes "sent our changes" from "we were already
     * up to date", which is worth telling the learner apart.
     *
     * [merge] is null only when this device seeded an empty remote, where there was
     * nothing to merge against.
     */
    data class Completed(
        val merge: MergeResult.Merged?,
        val pushed: Boolean,
        val token: String,
    ) : SyncReport {
        /** Whether anything arrived from the other device. */
        val receivedChanges: Boolean get() = merge?.changedAnything == true
    }

    /**
     * Another device kept winning the push race.
     *
     * Nothing is lost: whatever was pulled has been applied locally, and the next sync
     * will try again. The remote is untouched.
     */
    data class Conflicted(val attempts: Int, val reason: String) : SyncReport

    data class Failed(val reason: String) : SyncReport
}
