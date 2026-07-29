package dev.bee.beecode.app

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.persistence.SettingsRepository
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe
import dev.bee.beecode.domain.TestCaseResult
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Snapshot merge, the correctness core of the sync model in ADR 0002.
 *
 * Sync has two halves: moving bytes to storage the learner owns, and deciding what the
 * truth is when two devices disagree. The second half is where data gets lost, and it is
 * a pure function — so it is tested here, in full, before any backend exists. When a
 * WebDAV or Drive client arrives it will be plumbing over an already-verified merge.
 *
 * The scenarios are the ones that actually happen to a person with a phone and a laptop:
 * study offline on both, edit the same Problem in two places, change a setting on one
 * device, sync twice by accident, sync in the wrong direction.
 *
 * No Python here. A snapshot is data, and these assertions are about merge algebra;
 * `ProfileTransferTest` covers the round trip through real SQLite and real CPython.
 */
class SnapshotMergeTest {

    // ---- Reviews: set union ---------------------------------------------------

    @Test
    fun disjointReviewsFromTwoDevicesAreBothKept() {
        // The central case sync exists for: study on the phone, study on the laptop,
        // end up with both. Losing either would corrupt a schedule and a streak.
        val local = snapshot(reviews = listOf(review("s1", "two-sum", 1_000), review("s2", "binary-search", 2_000)))
        val remote = snapshot(reviews = listOf(review("s3", "valid-anagram", 3_000)))

        val merged = assertMerged(SnapshotMerge.merge(local, remote))
        assertEquals(3, merged.reviewSessionIds().size)
        assertEquals(listOf("s1", "s2", "s3"), merged.reviewSessionIds())
        assertEquals(1, merged.result.reviewsFromRemote)
        assertEquals(0, merged.result.reviewsAlreadyPresent)
    }

    @Test
    fun aReviewPresentOnBothSidesIsNotDuplicated() {
        // Sync runs twice, or a snapshot is merged after it was already applied. The
        // review log is keyed by session, so this is idempotent by construction.
        val shared = review("s1", "two-sum", 1_000)
        val merged = assertMerged(
            SnapshotMerge.merge(
                snapshot(reviews = listOf(shared)),
                snapshot(reviews = listOf(shared, review("s2", "binary-search", 2_000))),
            ),
        )
        assertEquals(listOf("s1", "s2"), merged.reviewSessionIds())
        assertEquals(1, merged.result.reviewsFromRemote)
        assertEquals(1, merged.result.reviewsAlreadyPresent)
    }

    @Test
    fun aLocalReviewIsNeverOverwrittenByACollidingRemoteOne() {
        // The union must keep the copy it already has, not let the remote one win.
        //
        // This test exists because a mutation exposed its absence: changing
        // `putIfAbsent` to `put` — so remote clobbers local — passed the whole suite.
        // The shared-review test used byte-identical content on both sides, so it could
        // not tell union from overwrite. A real collision cannot normally happen, since
        // sessionIds are per-device UUIDs and a finalized review is immutable; that is
        // exactly why the rule needs asserting rather than assuming.
        val local = snapshot(reviews = listOf(review("s1", "two-sum", 1_000, source = "# local")))
        val remote = snapshot(reviews = listOf(review("s1", "two-sum", 1_000, source = "# remote")))

        val merged = assertMerged(SnapshotMerge.merge(local, remote))
        assertEquals(listOf("s1"), merged.reviewSessionIds())
        assertTrue(
            merged.result.payloadText.contains("# local"),
            "the local review must survive a colliding sessionId",
        )
        assertTrue(!merged.result.payloadText.contains("# remote"))
        assertEquals(0, merged.result.reviewsFromRemote)
        assertEquals(1, merged.result.reviewsAlreadyPresent)
    }

    @Test
    fun mergingIsCommutativeOnReviewsAndDeterministicInOrder() {
        // Both devices must compute the same merged bytes, or the ETag
        // compare-and-swap that guards the push is meaningless: each would think the
        // other's copy was a conflicting write and they would ping-pong forever.
        val a = snapshot(reviews = listOf(review("s2", "b", 2_000), review("s1", "a", 1_000)))
        val b = snapshot(reviews = listOf(review("s3", "c", 3_000)))

        val forwards = assertMerged(SnapshotMerge.merge(a, b))
        val backwards = assertMerged(SnapshotMerge.merge(b, a))
        assertEquals(forwards.reviewSessionIds(), backwards.reviewSessionIds())
        assertEquals(listOf("s1", "s2", "s3"), forwards.reviewSessionIds())
    }

    @Test
    fun reviewsWithIdenticalTimestampsStillOrderDeterministically() {
        // Two devices finalizing in the same millisecond is rare but not impossible,
        // and a tie in the sort key would otherwise leave the order up to map iteration.
        val a = snapshot(reviews = listOf(review("sB", "b", 5_000)))
        val b = snapshot(reviews = listOf(review("sA", "a", 5_000)))
        assertEquals(
            assertMerged(SnapshotMerge.merge(a, b)).reviewSessionIds(),
            assertMerged(SnapshotMerge.merge(b, a)).reviewSessionIds(),
        )
    }

    // ---- Drafts: last-write-wins ---------------------------------------------

    @Test
    fun theNewerDraftWinsRegardlessOfWhichSideItIsOn() {
        val older = draft("two-sum", "# old", 1_000)
        val newer = draft("two-sum", "# new", 9_000)

        val remoteNewer = assertMerged(SnapshotMerge.merge(snapshot(drafts = listOf(older)), snapshot(drafts = listOf(newer))))
        assertEquals("# new", remoteNewer.draftSource("two-sum"))
        assertEquals(1, remoteNewer.result.draftsFromRemote)

        val localNewer = assertMerged(SnapshotMerge.merge(snapshot(drafts = listOf(newer)), snapshot(drafts = listOf(older))))
        assertEquals("# new", localNewer.draftSource("two-sum"))
        assertEquals(1, localNewer.result.draftsKeptLocal)
    }

    @Test
    fun aDraftTieKeepsTheLocalCopy() {
        // Equal timestamps mean there is no ordering information, so the tie-break has
        // to be a stated rule rather than an accident. Keeping what is already on this
        // device is the choice that never surprises the person looking at the screen.
        val merged = assertMerged(
            SnapshotMerge.merge(
                snapshot(drafts = listOf(draft("two-sum", "# local", 5_000))),
                snapshot(drafts = listOf(draft("two-sum", "# remote", 5_000))),
            ),
        )
        assertEquals("# local", merged.draftSource("two-sum"))
        assertEquals(1, merged.result.draftsKeptLocal)
    }

    @Test
    fun draftsForDifferentProblemsAreAllKept() {
        val merged = assertMerged(
            SnapshotMerge.merge(
                snapshot(drafts = listOf(draft("two-sum", "# a", 1_000))),
                snapshot(drafts = listOf(draft("binary-search", "# b", 1_000))),
            ),
        )
        assertEquals("# a", merged.draftSource("two-sum"))
        assertEquals("# b", merged.draftSource("binary-search"))
    }

    // ---- Settings: last-write-wins per key -----------------------------------

    @Test
    fun theNewerSettingValueWinsPerKey() {
        val local = snapshot(
            settings = mapOf("review.dailyLimit" to "10", "streak.zone" to "Europe/London"),
            settingStamps = mapOf("review.dailyLimit" to 1_000L, "streak.zone" to 9_000L),
        )
        val remote = snapshot(
            settings = mapOf("review.dailyLimit" to "25", "streak.zone" to "Asia/Tokyo"),
            settingStamps = mapOf("review.dailyLimit" to 5_000L, "streak.zone" to 2_000L),
        )

        val merged = assertMerged(SnapshotMerge.merge(local, remote))
        // Per key, not per map: each side wins the key it wrote more recently.
        assertEquals("25", merged.setting("review.dailyLimit"))
        assertEquals("Europe/London", merged.setting("streak.zone"))
    }

    @Test
    fun aSettingTieKeepsTheLocalValue() {
        // Same rule as drafts, and asserted for the same reason: a mutation from `>` to
        // `>=` in the settings comparison passed the whole suite, because every other
        // settings test used distinct timestamps. A tie has no ordering information, so
        // which side wins must be a stated choice rather than an implementation detail.
        val merged = assertMerged(
            SnapshotMerge.merge(
                snapshot(
                    settings = mapOf("review.dailyLimit" to "10"),
                    settingStamps = mapOf("review.dailyLimit" to 5_000L),
                ),
                snapshot(
                    settings = mapOf("review.dailyLimit" to "25"),
                    settingStamps = mapOf("review.dailyLimit" to 5_000L),
                ),
            ),
        )
        assertEquals("10", merged.setting("review.dailyLimit"))
        assertTrue(!merged.result.changedAnything)
    }

    @Test
    fun theDeviceIdentityIsNeverMerged() {
        // Two devices claiming one identity could not recognize their own writes, which
        // is the whole reason ADR 0002 reserves a per-installation deviceId.
        val merged = assertMerged(
            SnapshotMerge.merge(
                snapshot(settings = mapOf(SnapshotMerge.DEVICE_ID_KEY to "device-local")),
                snapshot(
                    settings = mapOf(SnapshotMerge.DEVICE_ID_KEY to "device-remote"),
                    settingStamps = mapOf(SnapshotMerge.DEVICE_ID_KEY to 9_999L),
                ),
            ),
        )
        assertEquals(null, merged.setting(SnapshotMerge.DEVICE_ID_KEY))
    }

    @Test
    fun syncCredentialsAreNeverMerged() {
        // Merging a remote password would spread one device's credential to every other,
        // and it would already have been uploaded to the server it authenticates to.
        val merged = assertMerged(
            SnapshotMerge.merge(
                snapshot(settings = mapOf("review.dailyLimit" to "10")),
                snapshot(
                    settings = mapOf(
                        "sync.webdav.url" to "https://cloud.example.com/beecode-sync.json",
                        "sync.webdav.username" to "someone",
                        "sync.webdav.password" to "hunter2",
                        "sync.file" to "/their/laptop/beecode-sync.json",
                        "review.dailyLimit" to "25",
                    ),
                    settingStamps = mapOf(
                        "sync.webdav.password" to 9_999L,
                        "review.dailyLimit" to 9_999L,
                    ),
                ),
            ),
        )
        assertEquals(null, merged.setting("sync.webdav.password"))
        assertEquals(null, merged.setting("sync.webdav.username"))
        assertEquals(null, merged.setting("sync.webdav.url"))
        assertEquals(null, merged.setting("sync.file"))
        assertTrue(
            !merged.result.payloadText.contains("hunter2"),
            "a credential must not appear anywhere in a merged snapshot",
        )
        // An ordinary setting still merges, so this is a targeted exclusion rather than
        // settings merging being broken.
        assertEquals("25", merged.setting("review.dailyLimit"))
    }

    @Test
    fun theDeviceOnlyKeysMatchThePersistenceLayer() {
        // The merge duplicates these as literals to avoid depending on persistence. This
        // is what stops the duplicate drifting into a key that is no longer excluded —
        // which for a password would be a silent credential leak.
        assertEquals(SettingsRepository.DEVICE_ONLY_KEYS, SnapshotMerge.DEVICE_ONLY_KEYS)
    }

    @Test
    fun theDeviceIdKeyMatchesThePersistenceLayer() {
        // The merge duplicates this key as a literal to avoid depending on persistence.
        // This is what stops the duplicate drifting into a silently un-excluded key.
        assertEquals(SettingsRepository.KEY_DEVICE_ID, SnapshotMerge.DEVICE_ID_KEY)
    }

    @Test
    fun anUnstampedSettingLosesToAStampedOne() {
        // A snapshot exported before settingsUpdatedAtEpochMillis existed still merges:
        // its settings are treated as older than anything carrying a real timestamp.
        val legacy = snapshot(settings = mapOf("review.dailyLimit" to "10"), settingStamps = emptyMap())
        val current = snapshot(
            settings = mapOf("review.dailyLimit" to "25"),
            settingStamps = mapOf("review.dailyLimit" to 1L),
        )
        assertEquals("25", assertMerged(SnapshotMerge.merge(legacy, current)).setting("review.dailyLimit"))
    }

    // ---- Failure modes -------------------------------------------------------

    @Test
    fun unreadableSnapshotsFailWithAReasonRatherThanThrowing() {
        assertIs<MergeResult.Failed>(SnapshotMerge.merge("not json", snapshot()))
        assertIs<MergeResult.Failed>(SnapshotMerge.merge(snapshot(), "{\"nonsense\":true}"))
    }

    @Test
    fun aFutureFormatVersionIsRefusedRatherThanPartiallyMerged() {
        // Silently merging a payload from a newer BeeCode would drop fields this build
        // cannot see — and the loss would be invisible until it mattered.
        val future = snapshot().replace(
            "\"formatVersion\": ${ProfileTransfer.FORMAT_VERSION}",
            "\"formatVersion\": ${ProfileTransfer.FORMAT_VERSION + 1}",
        )
        val failed = assertIs<MergeResult.Failed>(SnapshotMerge.merge(snapshot(), future))
        assertTrue(failed.reason.contains("Update BeeCode"), failed.reason)
    }

    @Test
    fun aNoOpMergeReportsThatNothingChanged() {
        // So the UI can stay quiet rather than claiming it synced something.
        val only = snapshot(reviews = listOf(review("s1", "two-sum", 1_000)))
        val merged = assertMerged(SnapshotMerge.merge(only, only))
        assertTrue(!merged.result.changedAnything)
        assertEquals(1, merged.result.reviewsAlreadyPresent)
    }

    // ---- The round trip through a real profile -------------------------------

    @Test
    fun aMergedSnapshotRestoresIntoACleanProfileWithBothDevicesReviews() {
        // The end-to-end claim: merge is not just algebra over JSON, its output is a
        // valid snapshot that restores. This is what a sync client would actually do —
        // merge, then restore locally, then push.
        val phone = profileWith("two-sum", ReviewRating.GOOD)
        val laptop = profileWith("binary-search", ReviewRating.EASY)

        val merged = assertMerged(SnapshotMerge.merge(phone, laptop))
        assertEquals(1, merged.result.reviewsFromRemote)

        val restored = BeeCodeProfile.inMemory(catalogue = catalogue(), runner = ScriptedRunner())
        restored.use { profile ->
            val outcome = ProfileTransfer.restore(profile, merged.result.payloadText, NOW)
            val applied = assertIs<RestoreResult.Restored>(outcome)
            assertEquals(2, applied.reviewsApplied)

            // Both devices' work is present, and schedules were rebuilt by replaying the
            // merged log rather than merged as a projection.
            assertEquals(2, profile.allReviews().size)
            assertNotNull(profile.reviews.schedule(ProblemId("two-sum")))
            assertNotNull(profile.reviews.schedule(ProblemId("binary-search")))
            assertEquals(2, profile.statistics().distinctProblemsSolved)
            // And replaying the log reproduces the stored schedules exactly.
            assertEquals(emptyList(), profile.verifyScheduleIntegrity())
        }
    }

    @Test
    fun mergingTheSameSnapshotPairTwiceIsIdempotentEndToEnd() {
        // A sync that retries after a failed push must not double-count. Merging the
        // merged result against either input again changes nothing.
        val phone = profileWith("two-sum", ReviewRating.GOOD)
        val laptop = profileWith("binary-search", ReviewRating.GOOD)

        val once = assertMerged(SnapshotMerge.merge(phone, laptop))
        val twice = assertMerged(SnapshotMerge.merge(once.result.payloadText, laptop))
        assertTrue(!twice.result.changedAnything)
        assertEquals(once.reviewSessionIds(), twice.reviewSessionIds())
    }

    // ---- Fixtures ------------------------------------------------------------

    // Compiled from the authoring directories, the same way the other shared tests
    // build one. Loading the real pack rather than a fixture means the end-to-end
    // assertions below fail if the pack stops loading.
    private fun catalogue(): ProblemCatalogue =
        ProblemCatalogue.fromSourceDirectory(java.io.File(repoRoot(), "content/packs/core"))

    /** A real profile with one finalized review, exported as a snapshot. */
    private fun profileWith(problem: String, rating: ReviewRating): String {
        val profile = BeeCodeProfile.inMemory(catalogue = catalogue(), runner = ScriptedRunner())
        return profile.use {
            runBlocking {
                val problemId = ProblemId(problem)
                profile.study.open(problemId)
                val run = profile.study.run(problemId, "# scripted: pass\n")
                val completed = assertIs<RunOutcome.Completed>(run)
                profile.study.finalize(problemId, completed.run.id, rating)
            }
            ProfileTransfer.export(profile, NOW)
        }
    }

    private fun snapshot(
        reviews: List<String> = emptyList(),
        drafts: List<String> = emptyList(),
        settings: Map<String, String> = emptyMap(),
        settingStamps: Map<String, Long> = emptyMap(),
    ): String {
        fun obj(entries: Map<String, String>) =
            entries.entries.joinToString(", ") { "\"${it.key}\": \"${it.value}\"" }
        fun stamps(entries: Map<String, Long>) =
            entries.entries.joinToString(", ") { "\"${it.key}\": ${it.value}" }
        return """
            {
              "formatVersion": ${ProfileTransfer.FORMAT_VERSION},
              "exportedAtEpochMillis": 1000,
              "settings": { ${obj(settings)} },
              "drafts": [ ${drafts.joinToString(", ")} ],
              "reviews": [ ${reviews.joinToString(", ")} ],
              "settingsUpdatedAtEpochMillis": { ${stamps(settingStamps)} }
            }
        """.trimIndent()
    }

    private fun draft(problemId: String, source: String, updatedAt: Long) = """
        {
          "problemId": "$problemId",
          "problemRevisionId": "rev-1",
          "source": "$source",
          "starterBaseline": "# starter",
          "updatedAtEpochMillis": $updatedAt
        }
    """.trimIndent()

    /**
     * A review as an **FSRS-6 era export** wrote it.
     *
     * Deliberately left in its original form after the FSRS-7 migration: the
     * algorithm label and engine version are the old ones, and `elapsedDays`,
     * `maximumIntervalDays`, and `nextIntervalDays` are bare JSON integers rather
     * than the fractional values a current export emits.
     *
     * That makes every test using this fixture a check that an older export still
     * restores. Widening those fields to fractional days would have been a silent
     * data-loss bug if JSON distinguished `3` from `3.0`, and the whole point of
     * bumping `ProfileTransfer.FORMAT_VERSION` was to make the *other* direction —
     * an old build reading a new payload — fail loudly instead.
     */
    private fun review(
        sessionId: String,
        problemId: String,
        finalizedAt: Long,
        source: String = "# solved",
    ) = """
        {
          "eventId": "event-$sessionId",
          "sessionId": "$sessionId",
          "problemId": "$problemId",
          "problemRevisionId": "rev-1",
          "executionRunId": "run-$sessionId",
          "outcome": "PASSED",
          "rating": "GOOD",
          "aided": false,
          "countsAsSolved": true,
          "finalizedAtEpochMillis": $finalizedAt,
          "streakZoneId": "UTC",
          "deviceId": "device-x",
          "selectedSource": "$source",
          "algorithmId": "FSRS-6.x 21-parameter snapshot",
          "engineVersion": "bee-fsrs-0.1.0",
          "parametersHash": "hash",
          "previousStateHash": "none",
          "elapsedDays": 0,
          "ratingValue": 3,
          "desiredRetention": 0.9,
          "maximumIntervalDays": 36500,
          "nextStability": 3.0,
          "nextDifficulty": 5.0,
          "nextIntervalDays": 3,
          "retrievability": 0.9,
          "dueAtEpochMillis": ${finalizedAt + 259_200_000}
        }
    """.trimIndent()

    /** Assert a merge succeeded and wrap it for readable assertions. */
    private fun assertMerged(result: MergeResult): MergedView {
        val merged = assertIs<MergeResult.Merged>(result, (result as? MergeResult.Failed)?.reason)
        return MergedView(merged)
    }

    /**
     * Reads fields back out of a merged payload by regex.
     *
     * Deliberately not by decoding into the wire types: those are `internal`, and a test
     * that reparsed them would assert against the same code that produced them. Reading
     * the emitted JSON checks what a *different* BeeCode install would actually receive.
     */
    private class MergedView(val result: MergeResult.Merged) {
        fun reviewSessionIds(): List<String> =
            Regex("\"sessionId\": \"([^\"]+)\"").findAll(result.payloadText).map { it.groupValues[1] }.toList()

        fun draftSource(problemId: String): String? =
            Regex("\"problemId\": \"$problemId\",[\\s\\S]{0,200}?\"source\": \"([^\"]*)\"")
                .find(result.payloadText)?.groupValues?.get(1)

        fun setting(key: String): String? =
            Regex("\"$key\": \"([^\"]*)\"").find(
                result.payloadText.substringAfter("\"settings\":").substringBefore("\"drafts\":"),
            )?.groupValues?.get(1)
    }

    /** Passes any source containing the marker, so no interpreter is needed. */
    private class ScriptedRunner : PythonRunner {
        override val runnerId = "scripted"
        override val capability = RunnerCapability.SEPARATE_PROCESS

        override suspend fun probe() = RunnerProbe(
            available = true,
            pythonVersion = "3.12.0 (scripted)",
            capability = capability,
            unavailableReason = null,
        )

        override suspend fun execute(request: RunRequest) = RunResult(
            runId = request.runId,
            outcome = ExecutionOutcome.PASSED,
            testResults = request.tests.map {
                TestCaseResult(
                    name = it.name,
                    passed = true,
                    hidden = it.hidden,
                    expectedJson = if (it.hidden) null else it.expectedJson,
                    actualJson = if (it.hidden) null else it.expectedJson,
                    message = null,
                    durationMillis = 1,
                )
            },
            output = "",
            outputTruncated = false,
            durationMillis = 1,
            runnerId = runnerId,
            pythonVersion = "3.12.0 (scripted)",
            diagnostic = null,
        )
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T12:00:00Z")

        /** Walks up to the repository root, as the other shared tests do. */
        fun repoRoot(): java.io.File {
            System.getProperty("beecode.repoRoot")?.let { return java.io.File(it) }
            var candidate = java.io.File(".").absoluteFile
            repeat(6) {
                if (java.io.File(candidate, "content/packs/core").isDirectory) return candidate
                candidate = candidate.parentFile ?: return candidate
            }
            return java.io.File(".").absoluteFile
        }
    }
}
