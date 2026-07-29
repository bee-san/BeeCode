package dev.bee.beecode.app

import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.DomainEventId
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.FsrsTransitionRecord
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSessionId
import dev.bee.beecode.domain.SolutionDraft
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Whole-profile export and restore.
 *
 * The learner's escape hatch: everything they have done, in one file they own, that
 * restores into a clean install. This is the only recovery path in v1 and it works
 * with no account and no network.
 *
 * ### Why this shape
 *
 * The payload is a **whole-profile snapshot**, not a diff, and reviews are carried
 * as the append-only log rather than as final schedule state. That is deliberate
 * preparation for ADR 0002: the chimahon sync model merges two snapshots per
 * entity, and merging append-only logs keyed by session is a set union, which is
 * always correct. When sync arrives this format is what it serializes, so the
 * work is a feature addition rather than a migration.
 *
 * ### Privacy
 *
 * An export **contains the learner's source code**. That is the point — a backup
 * without your solutions is not a backup — but it makes the file sensitive, and the
 * UI that produces one must say so. This is exactly the distinction ADR 0002 draws:
 * the prohibition on uploading source applies to the *Leaderboard*, not to a file
 * the learner keeps.
 */
object ProfileTransfer {

    /**
     * Bumped when the payload shape changes incompatibly.
     *
     * 2: FSRS-7's fractional intervals. `elapsedDays`, `maximumIntervalDays`, and
     * `nextIntervalDays` widened from integer to fractional days.
     *
     * Reading *older* exports still works, because JSON does not distinguish the
     * two and `3` decodes into a fractional field as `3.0`. The bump is for the
     * other direction: a version-1 build reading this payload would fail to parse
     * `0.00694` as an integer, and the check below turns that into an explicit
     * "upgrade BeeCode" rather than a corrupt restore.
     */
    const val FORMAT_VERSION: Int = 2

    internal val json = Json {
        prettyPrint = true
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    /**
     * Serialize a whole profile.
     *
     * @param exportedAt stamped into the payload for the learner's benefit. Supplied
     *   rather than read from a clock so an export is reproducible under test.
     */
    fun export(profile: BeeCodeProfile, exportedAt: Instant): String {
        // The source that produced each finalized result is stored alongside the
        // review rather than on the domain event, so it is read separately here.
        // Without it a restored profile would have correct due dates and no record
        // of what the learner actually wrote — which is most of what they want back.
        val sources = profile.reviews.selectedSources()
        val payload = ProfilePayload(
            formatVersion = FORMAT_VERSION,
            exportedAtEpochMillis = exportedAt.toEpochMilliseconds(),
            settings = profile.settings.all(),
            settingsUpdatedAtEpochMillis = profile.settings.allStamped()
                .mapValues { (_, stamped) -> stamped.updatedAt.toEpochMilliseconds() },
            drafts = profile.drafts.allDrafts().map { it.toWire() },
            reviews = profile.allReviews().map { review ->
                review.toWire(sources[review.sessionId.value].orEmpty())
            },
        )
        return json.encodeToString(payload)
    }

    /**
     * Restore a payload into a profile.
     *
     * Restoring is **additive and idempotent**, not destructive. Reviews are keyed by
     * session, so importing the same file twice has one effect, and importing into a
     * profile that already has history merges rather than clobbers. That matters
     * because the common real mistake is importing a backup into the wrong profile,
     * and losing existing reviews to that would be unrecoverable.
     *
     * Schedules are deliberately **not** carried in the payload. They are recomputed
     * by replaying the restored review log, which is more obviously correct than
     * trusting a stored projection and is the same fold a sync merge will use.
     *
     * @return a summary of what was applied and what was skipped.
     */
    fun restore(profile: BeeCodeProfile, payloadText: String, now: Instant): RestoreResult {
        val payload = try {
            json.decodeFromString<ProfilePayload>(payloadText)
        } catch (e: Exception) {
            return RestoreResult.Failed("This does not look like a BeeCode export: ${e.message}")
        }

        if (payload.formatVersion > FORMAT_VERSION) {
            // A newer BeeCode wrote this. Guessing risks silently dropping data the
            // learner believes they restored.
            return RestoreResult.Failed(
                "This export was created by a newer version of BeeCode " +
                    "(format ${payload.formatVersion}, this build reads $FORMAT_VERSION). " +
                    "Upgrade BeeCode to restore it.",
            )
        }

        var reviewsApplied = 0
        var reviewsSkipped = 0
        var unknownProblems = 0
        val restoredProblems = mutableSetOf<ProblemId>()

        // Oldest first, so replaying the log rebuilds schedules in the right order.
        for (wire in payload.reviews.sortedBy { it.finalizedAtEpochMillis }) {
            val review = try {
                wire.toDomain()
            } catch (e: IllegalArgumentException) {
                // A malformed row is skipped rather than aborting the whole restore:
                // recovering most of a damaged backup beats recovering none of it.
                reviewsSkipped++
                continue
            }

            // A review for a Problem this build does not ship cannot be replayed,
            // because there is no content to schedule. Counted and reported rather
            // than silently dropped.
            if (profile.catalogue.problem(review.problemId) == null) {
                unknownProblems++
                continue
            }

            if (profile.reviews.review(review.sessionId) != null) {
                reviewsSkipped++
                continue
            }

            profile.reviews.importReview(review, wire.selectedSource)
            restoredProblems += review.problemId
            reviewsApplied++
        }

        // Rebuild schedules from the merged log. This is the step that makes restore
        // trustworthy: the due dates are derived, not imported.
        val rebuilt = profile.reviews.rebuildSchedulesFromHistory()
        profile.reviews.replaceSchedules(rebuilt)

        var draftsApplied = 0
        for (wire in payload.drafts) {
            val draft = try {
                wire.toDomain()
            } catch (e: IllegalArgumentException) {
                continue
            }
            if (profile.catalogue.problem(draft.problemId) == null) continue
            // Only restore a draft when the local one is untouched, so importing a
            // backup cannot overwrite work in progress the learner has not saved
            // anywhere else.
            val existing = profile.drafts.draft(draft.problemId)
            if (existing == null || existing.isPristine) {
                profile.drafts.save(draft.copy(version = existing?.version ?: 0), now)
                draftsApplied++
            }
        }

        var settingsApplied = 0
        for ((key, value) in payload.settings) {
            // The device identity is per-installation and must not be imported, or
            // two devices would claim the same identity and a future sync could not
            // tell their writes apart.
            if (key == dev.bee.beecode.persistence.SettingsRepository.KEY_DEVICE_ID) continue
            profile.settings.put(key, value, now)
            settingsApplied++
        }

        return RestoreResult.Restored(
            reviewsApplied = reviewsApplied,
            reviewsSkipped = reviewsSkipped,
            reviewsForUnknownProblems = unknownProblems,
            draftsApplied = draftsApplied,
            settingsApplied = settingsApplied,
            schedulesRebuilt = rebuilt.size,
        )
    }

    // ---- Wire mapping ---------------------------------------------------

    private fun SolutionDraft.toWire() = WireDraft(
        problemId = problemId.value,
        problemRevisionId = problemRevisionId.value,
        source = source,
        starterBaseline = starterBaseline,
        updatedAtEpochMillis = updatedAt.toEpochMilliseconds(),
    )

    private fun WireDraft.toDomain() = SolutionDraft(
        problemId = ProblemId(problemId),
        problemRevisionId = ProblemRevisionId(problemRevisionId),
        source = source,
        starterBaseline = starterBaseline,
        version = 0,
        updatedAt = Instant.fromEpochMilliseconds(updatedAtEpochMillis),
    )

    private fun ProblemReviewFinalized.toWire(selectedSource: String) = WireReview(
        eventId = eventId.value,
        sessionId = sessionId.value,
        problemId = problemId.value,
        problemRevisionId = problemRevisionId.value,
        executionRunId = executionRunId.value,
        outcome = outcome.name,
        rating = rating.name,
        aided = aided,
        countsAsSolved = countsAsSolved,
        finalizedAtEpochMillis = finalizedAt.toEpochMilliseconds(),
        streakZoneId = streakZoneId,
        deviceId = deviceId.value,
        // Carried so history can show what the learner actually wrote. This is the
        // sensitive part of an export, and the reason the export UI must warn.
        selectedSource = selectedSource,
        algorithmId = transition.algorithmId,
        engineVersion = transition.engineVersion,
        parametersHash = transition.parametersHash,
        previousStateHash = transition.previousStateHash,
        previousStability = transition.previousStability,
        previousDifficulty = transition.previousDifficulty,
        elapsedDays = transition.elapsedDays,
        ratingValue = transition.ratingValue,
        desiredRetention = transition.desiredRetention,
        maximumIntervalDays = transition.maximumIntervalDays,
        nextStability = transition.nextStability,
        nextDifficulty = transition.nextDifficulty,
        nextIntervalDays = transition.nextIntervalDays,
        retrievability = transition.retrievability,
        dueAtEpochMillis = transition.dueAt.toEpochMilliseconds(),
    )

    private fun WireReview.toDomain() = ProblemReviewFinalized(
        eventId = DomainEventId(eventId),
        sessionId = ReviewSessionId(sessionId),
        problemId = ProblemId(problemId),
        problemRevisionId = ProblemRevisionId(problemRevisionId),
        executionRunId = ExecutionRunId(executionRunId),
        outcome = ExecutionOutcome.valueOf(outcome),
        rating = ReviewRating.valueOf(rating),
        aided = aided,
        countsAsSolved = countsAsSolved,
        finalizedAt = Instant.fromEpochMilliseconds(finalizedAtEpochMillis),
        streakZoneId = streakZoneId,
        deviceId = DeviceId(deviceId),
        transition = FsrsTransitionRecord(
            algorithmId = algorithmId,
            engineVersion = engineVersion,
            parametersHash = parametersHash,
            previousStateHash = previousStateHash,
            previousStability = previousStability,
            previousDifficulty = previousDifficulty,
            elapsedDays = elapsedDays,
            ratingValue = ratingValue,
            desiredRetention = desiredRetention,
            maximumIntervalDays = maximumIntervalDays,
            nextStability = nextStability,
            nextDifficulty = nextDifficulty,
            nextIntervalDays = nextIntervalDays,
            retrievability = retrievability,
            dueAt = Instant.fromEpochMilliseconds(dueAtEpochMillis),
        ),
    )
}

sealed interface RestoreResult {
    data class Restored(
        val reviewsApplied: Int,
        val reviewsSkipped: Int,
        /** Reviews for Problems this build does not ship, so they cannot be replayed. */
        val reviewsForUnknownProblems: Int,
        val draftsApplied: Int,
        val settingsApplied: Int,
        val schedulesRebuilt: Int,
    ) : RestoreResult {
        fun describe(): String = buildString {
            append("Restored $reviewsApplied reviews")
            if (reviewsSkipped > 0) append(", skipped $reviewsSkipped already present")
            if (reviewsForUnknownProblems > 0) {
                append(", $reviewsForUnknownProblems for Problems this version does not have")
            }
            append(". $draftsApplied drafts and $settingsApplied settings applied; ")
            append("$schedulesRebuilt schedules rebuilt.")
        }
    }

    data class Failed(val reason: String) : RestoreResult
}

@Serializable
internal data class ProfilePayload(
    val formatVersion: Int,
    val exportedAtEpochMillis: Long,
    val settings: Map<String, String>,
    val drafts: List<WireDraft>,
    val reviews: List<WireReview>,
    /**
     * When each setting was last written, for [SnapshotMerge].
     *
     * Additive and defaulted, so a payload written before this field existed still
     * decodes; a setting with no stamp is treated as older than any stamped one.
     * `settings` is kept as-is rather than replaced, so an older BeeCode can still
     * restore a newer export.
     */
    val settingsUpdatedAtEpochMillis: Map<String, Long> = emptyMap(),
)

@Serializable
internal data class WireDraft(
    val problemId: String,
    val problemRevisionId: String,
    val source: String,
    val starterBaseline: String,
    val updatedAtEpochMillis: Long,
)

@Serializable
internal data class WireReview(
    val eventId: String,
    val sessionId: String,
    val problemId: String,
    val problemRevisionId: String,
    val executionRunId: String,
    val outcome: String,
    val rating: String,
    val aided: Boolean,
    val countsAsSolved: Boolean,
    val finalizedAtEpochMillis: Long,
    val streakZoneId: String,
    val deviceId: String,
    val selectedSource: String = "",
    val algorithmId: String,
    val engineVersion: String,
    val parametersHash: String,
    val previousStateHash: String,
    val previousStability: Double? = null,
    val previousDifficulty: Double? = null,
    val elapsedDays: Double,
    val ratingValue: Int,
    val desiredRetention: Double,
    val maximumIntervalDays: Double,
    val nextStability: Double,
    val nextDifficulty: Double,
    val nextIntervalDays: Double,
    val retrievability: Double,
    val dueAtEpochMillis: Long,
)
