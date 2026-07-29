package dev.bee.beecode.app

import kotlinx.serialization.encodeToString

/**
 * Merge two whole-profile snapshots into one.
 *
 * This is the heart of the sync model ADR 0002 chose: chimahon's `mergeSyncData(local,
 * remote)`, a per-entity merge followed by an ETag-guarded compare-and-swap push. No
 * network, no storage backend, and no clock are involved here — merging is a pure
 * function of two payloads, which is what makes the hard part of sync testable before
 * any backend exists.
 *
 * ### The three merge rules
 *
 * Each entity merges by the rule its shape allows, and the shapes were chosen in the
 * local schema specifically so these rules would be available:
 *
 * | Entity | Rule | Why it is correct |
 * |---|---|---|
 * | Reviews | set union on `sessionId` | Append-only immutable events. Union of two logs is always right and needs no timestamp. |
 * | Drafts | last-write-wins on `updatedAt` | Mutable, and the learner's most recent edit is what they want. |
 * | Settings | last-write-wins on `updatedAt` | Same, per key rather than per map. |
 *
 * ### What is deliberately *not* merged
 *
 * **Schedules.** They are a projection, not truth, and are absent from the payload
 * entirely — the merged review log is replayed to rebuild them. That is more obviously
 * correct than merging two projections by timestamp, and it is only possible because
 * reviews are append-only and every transition records its own inputs. ADR 0002 calls
 * this the leading candidate; it is what [ProfileTransfer.restore] already does, so the
 * merge inherits it for free.
 *
 * **The device identity.** Per-installation, never merged and never imported, or two
 * devices would claim one identity and could not recognize their own writes.
 *
 * ### On last-write-wins
 *
 * LWW loses data by design: two devices editing the same draft offline means one edit
 * is discarded. That is the correct trade for a *single learner's* devices — the
 * alternatives are a merge UI for prose nobody wants, or CRDT machinery whose cost is
 * not justified when conflicts are rare and the loser is one draft revision.
 *
 * It is emphatically *not* the right trade for reviews, which is why they are
 * append-only instead. A lost review would corrupt a schedule and a streak.
 */
object SnapshotMerge {

    /**
     * Merge [remoteText] into [localText], returning the merged payload and a summary.
     *
     * Both arguments are serialized snapshots as [ProfileTransfer.export] produces.
     * Neither is mutated; the result is a third snapshot, so a caller can inspect the
     * outcome before writing anything.
     *
     * The operation is **commutative** on reviews and **deterministic** on ties: when
     * two drafts or settings carry the same `updatedAt`, local wins. A tie means the
     * two devices genuinely disagree with no ordering information available, and
     * preferring the copy already on this device is the choice that never surprises the
     * person sitting in front of it.
     */
    fun merge(localText: String, remoteText: String): MergeResult {
        val local = decode(localText) ?: return MergeResult.Failed("The local snapshot is not readable.")
        val remote = decode(remoteText) ?: return MergeResult.Failed("The remote snapshot is not readable.")

        if (local.formatVersion != ProfileTransfer.FORMAT_VERSION) {
            return MergeResult.Failed(
                "The local snapshot is format ${local.formatVersion}; this build expects " +
                    "${ProfileTransfer.FORMAT_VERSION}.",
            )
        }
        if (remote.formatVersion != ProfileTransfer.FORMAT_VERSION) {
            return MergeResult.Failed(
                "The remote snapshot is format ${remote.formatVersion}; this build expects " +
                    "${ProfileTransfer.FORMAT_VERSION}. Update BeeCode on this device before syncing.",
            )
        }

        val reviews = mergeReviews(local.reviews, remote.reviews)
        val drafts = mergeDrafts(local.drafts, remote.drafts)
        val settings = mergeSettings(local, remote)

        val merged = ProfilePayload(
            formatVersion = ProfileTransfer.FORMAT_VERSION,
            // The later of the two, so the merged snapshot does not claim to be older
            // than a snapshot it contains.
            exportedAtEpochMillis = maxOf(local.exportedAtEpochMillis, remote.exportedAtEpochMillis),
            settings = settings.mapValues { (_, stamped) -> stamped.first },
            drafts = drafts.merged,
            reviews = reviews.merged,
            settingsUpdatedAtEpochMillis = settings.mapValues { (_, stamped) -> stamped.second },
        )

        return MergeResult.Merged(
            payloadText = ProfileTransfer.json.encodeToString(merged),
            reviewsFromRemote = reviews.fromRemote,
            reviewsAlreadyPresent = reviews.alreadyPresent,
            draftsFromRemote = drafts.fromRemote,
            draftsKeptLocal = drafts.keptLocal,
            settingsFromRemote = settings.count { (key, value) ->
                remote.settings[key] == value.first && local.settings[key] != value.first
            },
        )
    }

    private fun decode(text: String): ProfilePayload? =
        runCatching { ProfileTransfer.json.decodeFromString<ProfilePayload>(text) }.getOrNull()

    /**
     * Set union keyed by session.
     *
     * A review is immutable once finalized and `sessionId` is minted on-device as a
     * UUID, so two devices can never produce different content under one key. Union is
     * therefore total: no comparison, no loss, and the same result whichever side is
     * called "local".
     */
    private fun mergeReviews(local: List<WireReview>, remote: List<WireReview>): ReviewMerge {
        val bySession = local.associateByTo(LinkedHashMap()) { it.sessionId }
        var fromRemote = 0
        var alreadyPresent = 0
        for (review in remote) {
            if (bySession.putIfAbsent(review.sessionId, review) == null) fromRemote++ else alreadyPresent++
        }
        return ReviewMerge(
            // Sorted by finalization instant so the merged log reads chronologically and
            // two devices merging the same pair produce byte-identical output — which is
            // what makes an ETag comparison meaningful.
            merged = bySession.values.sortedWith(compareBy({ it.finalizedAtEpochMillis }, { it.sessionId })),
            fromRemote = fromRemote,
            alreadyPresent = alreadyPresent,
        )
    }

    /** Last-write-wins per Problem, ties to local. */
    private fun mergeDrafts(local: List<WireDraft>, remote: List<WireDraft>): DraftMerge {
        val byProblem = local.associateByTo(LinkedHashMap()) { it.problemId }
        var fromRemote = 0
        var keptLocal = 0
        for (draft in remote) {
            val current = byProblem[draft.problemId]
            when {
                current == null -> {
                    byProblem[draft.problemId] = draft
                    fromRemote++
                }
                draft.updatedAtEpochMillis > current.updatedAtEpochMillis -> {
                    byProblem[draft.problemId] = draft
                    fromRemote++
                }
                else -> keptLocal++
            }
        }
        return DraftMerge(
            merged = byProblem.values.sortedBy { it.problemId },
            fromRemote = fromRemote,
            keptLocal = keptLocal,
        )
    }

    /**
     * Last-write-wins per key, ties to local, device identity excluded.
     *
     * A key with no timestamp in `settingsUpdatedAtEpochMillis` is treated as epoch 0 —
     * older than anything stamped. That is how a snapshot exported before the stamps
     * existed merges sensibly instead of being rejected.
     */
    private fun mergeSettings(
        local: ProfilePayload,
        remote: ProfilePayload,
    ): Map<String, Pair<String, Long>> {
        fun stamped(payload: ProfilePayload): Map<String, Pair<String, Long>> =
            payload.settings
                .filterKeys { it != DEVICE_ID_KEY }
                .mapValues { (key, value) ->
                    value to (payload.settingsUpdatedAtEpochMillis[key] ?: 0L)
                }

        val merged = stamped(local).toMutableMap()
        for ((key, remoteValue) in stamped(remote)) {
            val localValue = merged[key]
            if (localValue == null || remoteValue.second > localValue.second) {
                merged[key] = remoteValue
            }
        }
        return merged.toSortedMap()
    }

    private data class ReviewMerge(
        val merged: List<WireReview>,
        val fromRemote: Int,
        val alreadyPresent: Int,
    )

    private data class DraftMerge(
        val merged: List<WireDraft>,
        val fromRemote: Int,
        val keptLocal: Int,
    )

    /**
     * The settings key holding this installation's identity.
     *
     * Duplicated as a literal rather than imported from `SettingsRepository`, because
     * `:shared` merging a snapshot must not depend on the persistence layer's constants
     * — and the key is part of the *wire format*, so it is frozen either way.
     * `SnapshotMergeTest` asserts the two agree.
     */
    internal const val DEVICE_ID_KEY = "device.id"
}

/** The outcome of merging two snapshots. */
sealed interface MergeResult {

    /**
     * A merged snapshot, ready to restore locally and push remotely.
     *
     * The counts exist so the UI can say what happened rather than claiming a silent
     * success — "12 reviews from your phone" is the difference between trusting sync
     * and wondering whether it ran.
     */
    data class Merged(
        val payloadText: String,
        val reviewsFromRemote: Int,
        val reviewsAlreadyPresent: Int,
        val draftsFromRemote: Int,
        val draftsKeptLocal: Int,
        val settingsFromRemote: Int,
    ) : MergeResult {
        /** Whether the merge changed anything relative to the local snapshot. */
        val changedAnything: Boolean
            get() = reviewsFromRemote > 0 || draftsFromRemote > 0 || settingsFromRemote > 0
    }

    data class Failed(val reason: String) : MergeResult
}
