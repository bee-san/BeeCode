package dev.bee.beecode.app

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.python.jvm.ProcessPythonRunner
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Export and restore, the plan's Test 2 recovery gate.
 *
 * The journey asserted here is the one the owner test describes: solve Problems,
 * export the profile, restore it into a *clean* install, and retain the expected
 * source, reviews, schedule, statistics, and achievement state.
 *
 * Real Python and real SQLite throughout, because a recovery path that only works
 * against fakes is not a recovery path.
 */
class ProfileTransferTest {

    private lateinit var originalFile: File
    private lateinit var restoredFile: File
    private lateinit var catalogue: ProblemCatalogue
    private val runner = ProcessPythonRunner()

    private val exportedAt: Instant = Instant.parse("2026-07-29T12:00:00Z")

    @BeforeTest
    fun setUp() {
        assumeTrue("Python 3 is unavailable", runBlocking { runner.probe().available })
        originalFile = tempDatabase("original")
        restoredFile = tempDatabase("restored")
        catalogue = ProblemCatalogue.fromSourceDirectory(File(repoRoot(), "content/packs/core"))
    }

    @AfterTest
    fun tearDown() {
        listOf(originalFile, restoredFile).forEach { file ->
            file.delete()
            File(file.absolutePath + "-wal").delete()
            File(file.absolutePath + "-shm").delete()
        }
    }

    @Test
    fun aSolvedProfileRestoresIntoACleanInstall() = runBlocking {
        val problemId = ProblemId("two-sum")
        val source = workingTwoSum()

        // ---- Build a profile worth recovering -----------------------------
        val expectedDueAt: Instant
        openOriginal().use { profile ->
            profile.study.open(problemId)
            val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, source))
            assertEquals(ExecutionOutcome.PASSED, run.run.outcome, run.run.output)
            val finalized = assertIs<FinalizeResult.Finalized>(
                profile.study.finalize(problemId, run.run.id, ReviewRating.GOOD),
            )
            expectedDueAt = assertNotNull(finalized.schedule).dueAt

            profile.settings.setDailyReviewLimit(15, exportedAt)
        }

        // ---- Export -------------------------------------------------------
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }
        assertTrue(payload.contains("two-sum"))
        // An export carries the learner's source. That is the point, and it is why
        // the file is sensitive.
        assertTrue(payload.contains("seen[value] = index"), "the export must carry the source")

        // ---- Restore into a clean install --------------------------------
        openRestored().use { profile ->
            assertEquals(0, profile.reviews.reviewCount(), "the target must start empty")

            val result = assertIs<RestoreResult.Restored>(
                ProfileTransfer.restore(profile, payload, Clock.System.now()),
            )
            assertEquals(1, result.reviewsApplied)
            assertEquals(0, result.reviewsSkipped)
            assertEquals(1, result.schedulesRebuilt)

            // The review came back.
            val history = profile.reviews.reviewHistory(problemId)
            assertEquals(1, history.size)
            assertTrue(history.first().countsAsSolved)

            // The schedule was rebuilt by replaying the log, and lands on the same
            // due date the original computed. This is the assertion that makes
            // restore trustworthy: the date is derived, not copied.
            val schedule = assertNotNull(profile.reviews.schedule(problemId))
            assertEquals(expectedDueAt, schedule.dueAt)
            assertEquals(1, schedule.reviewCount)

            // Statistics and achievements follow from the log.
            assertEquals(1, profile.statistics().totalSolved)
            assertTrue(assertNotNull(profile.achievement(Achievements.FIRST_SOLVE)).earned)

            // Settings came back.
            assertEquals(15, profile.settings.dailyReviewLimit())

            // And the source the learner wrote is recoverable.
            assertEquals(source, assertNotNull(profile.drafts.draft(problemId)).source)
        }
    }

    @Test
    fun restoringTwiceHasOneEffect() = runBlocking {
        val problemId = ProblemId("contains-duplicate")
        openOriginal().use { profile ->
            profile.study.open(problemId)
            val run = assertIs<RunOutcome.Completed>(
                profile.study.run(
                    problemId,
                    "def contains_duplicate(nums):\n    return len(set(nums)) != len(nums)\n",
                ),
            )
            profile.study.finalize(problemId, run.run.id, ReviewRating.GOOD)
        }
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }

        openRestored().use { profile ->
            val first = assertIs<RestoreResult.Restored>(
                ProfileTransfer.restore(profile, payload, Clock.System.now()),
            )
            assertEquals(1, first.reviewsApplied)

            // The common real mistake is importing the same backup twice. Reviews are
            // keyed by session, so this must be a no-op rather than a duplicate.
            val second = assertIs<RestoreResult.Restored>(
                ProfileTransfer.restore(profile, payload, Clock.System.now()),
            )
            assertEquals(0, second.reviewsApplied)
            assertEquals(1, second.reviewsSkipped)

            assertEquals(1, profile.reviews.reviewCount())
            assertEquals(1, assertNotNull(profile.reviews.schedule(problemId)).reviewCount)
        }
    }

    @Test
    fun restoringMergesRatherThanClobberingExistingHistory() = runBlocking {
        // Importing a backup into the wrong profile is a mistake a learner will make,
        // and losing their existing reviews to it would be unrecoverable. Restore is
        // additive.
        val exported = ProblemId("two-sum")
        openOriginal().use { profile ->
            profile.study.open(exported)
            val run = assertIs<RunOutcome.Completed>(profile.study.run(exported, workingTwoSum()))
            profile.study.finalize(exported, run.run.id, ReviewRating.GOOD)
        }
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }

        val local = ProblemId("valid-anagram")
        openRestored().use { profile ->
            profile.study.open(local)
            val run = assertIs<RunOutcome.Completed>(
                profile.study.run(local, "def is_anagram(s, t):\n    return sorted(s) == sorted(t)\n"),
            )
            profile.study.finalize(local, run.run.id, ReviewRating.GOOD)
            assertEquals(1, profile.reviews.reviewCount())

            ProfileTransfer.restore(profile, payload, Clock.System.now())

            // Both survive.
            assertEquals(2, profile.reviews.reviewCount())
            assertEquals(1, profile.reviews.reviewHistory(exported).size)
            assertEquals(1, profile.reviews.reviewHistory(local).size)
            assertNotNull(profile.reviews.schedule(exported))
            assertNotNull(profile.reviews.schedule(local))
            assertEquals(2, profile.statistics().distinctProblemsSolved)
        }
    }

    @Test
    fun restoringDoesNotOverwriteAnEditedLocalDraft() = runBlocking {
        // Unsaved work in progress may exist nowhere else, so an import must not
        // silently replace it.
        val problemId = ProblemId("two-sum")
        openOriginal().use { profile ->
            val opened = assertNotNull(profile.study.open(problemId))
            profile.study.saveDraft(opened.draft.copy(source = "# from the backup\n"))
        }
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }

        openRestored().use { profile ->
            val opened = assertNotNull(profile.study.open(problemId))
            profile.study.saveDraft(opened.draft.copy(source = "# work in progress\n"))

            ProfileTransfer.restore(profile, payload, Clock.System.now())

            assertEquals(
                "# work in progress\n",
                assertNotNull(profile.drafts.draft(problemId)).source,
                "an edited local draft must survive a restore",
            )
        }
    }

    @Test
    fun theDeviceIdentityIsNotImported() = runBlocking {
        // Two installations must never claim the same identity, or a future sync
        // could not tell their writes apart (ADR 0002 property 4).
        val originalDeviceId = openOriginal().use { profile ->
            profile.study.queue()
            profile.settings.get(dev.bee.beecode.persistence.SettingsRepository.KEY_DEVICE_ID)
        }
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }

        openRestored().use { profile ->
            // Force the target to mint its own identity first.
            profile.study.queue()
            val before = profile.settings.get(
                dev.bee.beecode.persistence.SettingsRepository.KEY_DEVICE_ID,
            )
            ProfileTransfer.restore(profile, payload, Clock.System.now())
            val after = profile.settings.get(
                dev.bee.beecode.persistence.SettingsRepository.KEY_DEVICE_ID,
            )
            assertEquals(before, after, "the device identity must not be overwritten by an import")
            if (originalDeviceId != null && before != null) {
                assertTrue(originalDeviceId != after, "identities must remain distinct")
            }
        }
    }

    @Test
    fun anEmptyProfileExportsAndRestoresCleanly() {
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }
        openRestored().use { profile ->
            val result = assertIs<RestoreResult.Restored>(
                ProfileTransfer.restore(profile, payload, Clock.System.now()),
            )
            assertEquals(0, result.reviewsApplied)
            assertEquals(0, profile.reviews.reviewCount())
        }
    }

    @Test
    fun garbageIsRejectedWithAReadableReason() {
        openRestored().use { profile ->
            val result = assertIs<RestoreResult.Failed>(
                ProfileTransfer.restore(profile, "this is not json", Clock.System.now()),
            )
            assertTrue(
                result.reason.contains("BeeCode export"),
                "the message must tell the learner what went wrong: ${result.reason}",
            )
            assertEquals(0, profile.reviews.reviewCount(), "a failed restore must change nothing")
        }
    }

    @Test
    fun aNewerFormatIsRefusedRatherThanPartiallyRead() {
        // Guessing at an unknown format risks silently dropping data the learner
        // believes they restored.
        openRestored().use { profile ->
            val future = """{"formatVersion":99,"exportedAtEpochMillis":0,"settings":{},""" +
                """"drafts":[],"reviews":[]}"""
            val result = assertIs<RestoreResult.Failed>(
                ProfileTransfer.restore(profile, future, Clock.System.now()),
            )
            assertTrue(result.reason.contains("newer version"), result.reason)
        }
    }

    @Test
    fun reviewsForUnknownProblemsAreReportedRatherThanSilentlyDropped() = runBlocking {
        // A backup from a build with more Problems than this one. The learner should
        // be told, not left wondering why their count is lower.
        val problemId = ProblemId("two-sum")
        openOriginal().use { profile ->
            profile.study.open(problemId)
            val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, workingTwoSum()))
            profile.study.finalize(problemId, run.run.id, ReviewRating.GOOD)
        }
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }
            .replace("\"two-sum\"", "\"a-problem-from-the-future\"")

        openRestored().use { profile ->
            val result = assertIs<RestoreResult.Restored>(
                ProfileTransfer.restore(profile, payload, Clock.System.now()),
            )
            assertEquals(0, result.reviewsApplied)
            assertEquals(1, result.reviewsForUnknownProblems)
            assertTrue(result.describe().contains("does not have"), result.describe())
        }
    }

    @Test
    fun aMultiReviewHistoryRestoresToTheSameSchedule() = runBlocking {
        // Several reviews across several Problems, so the replay does real work
        // rather than folding a single entry.
        val expected = mutableMapOf<ProblemId, Instant>()
        openOriginal().use { profile ->
            listOf(
                ProblemId("two-sum") to workingTwoSum(),
                ProblemId("contains-duplicate") to
                    "def contains_duplicate(nums):\n    return len(set(nums)) != len(nums)\n",
                ProblemId("valid-anagram") to
                    "def is_anagram(s, t):\n    return sorted(s) == sorted(t)\n",
            ).forEach { (problemId, source) ->
                profile.study.open(problemId)
                val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, source))
                assertEquals(ExecutionOutcome.PASSED, run.run.outcome, run.run.output)
                val finalized = assertIs<FinalizeResult.Finalized>(
                    profile.study.finalize(problemId, run.run.id, ReviewRating.GOOD),
                )
                expected[problemId] = assertNotNull(finalized.schedule).dueAt
            }
        }
        val payload = openOriginal().use { ProfileTransfer.export(it, exportedAt) }

        openRestored().use { profile ->
            val result = assertIs<RestoreResult.Restored>(
                ProfileTransfer.restore(profile, payload, Clock.System.now()),
            )
            assertEquals(3, result.reviewsApplied)
            assertEquals(3, result.schedulesRebuilt)

            expected.forEach { (problemId, dueAt) ->
                assertEquals(
                    dueAt,
                    assertNotNull(profile.reviews.schedule(problemId)).dueAt,
                    "restored due date for $problemId",
                )
            }
            // And the rebuilt schedules agree with a fresh replay.
            assertTrue(profile.verifyScheduleIntegrity().isEmpty())
        }
    }

    // ---- Helpers --------------------------------------------------------

    private fun openOriginal(): BeeCodeProfile = open(originalFile)

    private fun openRestored(): BeeCodeProfile = open(restoredFile)

    private fun open(file: File): BeeCodeProfile = BeeCodeProfile.open(
        databasePath = file.absolutePath,
        catalogue = catalogue,
        runner = runner,
    )

    private fun workingTwoSum(): String = """
        def two_sum(nums, target):
            seen = {}
            for index, value in enumerate(nums):
                if target - value in seen:
                    return [seen[target - value], index]
                seen[value] = index
            return []
    """.trimIndent()

    private companion object {
        fun tempDatabase(label: String): File =
            kotlin.io.path.createTempFile("beecode-$label-", ".db").toFile().also { it.delete() }

        fun repoRoot(): File {
            System.getProperty("beecode.repoRoot")?.let { return File(it) }
            var candidate = File(".").absoluteFile
            repeat(6) {
                if (File(candidate, "content/packs/core").isDirectory) return candidate
                candidate = candidate.parentFile ?: return candidate
            }
            return File(".").absoluteFile
        }
    }
}
