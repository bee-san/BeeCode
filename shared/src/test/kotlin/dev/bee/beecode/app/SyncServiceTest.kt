package dev.bee.beecode.app

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Two devices, one shared file, real SQLite: cross-device sync end to end.
 *
 * `SnapshotMergeTest` proves the merge algebra. This proves the *loop* — pull, merge,
 * apply, push, retry — which is where ordering mistakes lose data even when the merge is
 * correct. Both "devices" are real profiles over real databases, and the remote is a real
 * file, so the only thing simulated here is Python.
 *
 * The scenarios are the ones a person with a phone and a laptop actually hits.
 */
class SyncServiceTest {

    private val directory: File = createTempDirectory("beecode-sync-").toFile()
    private val remote: File = File(directory, "beecode-sync.json")
    private val open = mutableListOf<BeeCodeProfile>()

    @AfterTest
    fun tearDown() {
        open.forEach { it.close() }
        directory.deleteRecursively()
    }

    @Test
    fun aSecondDeviceReceivesTheFirstDevicesWork() {
        // The headline case. Solve on the phone, sync; sync the laptop; the laptop has
        // the review, the schedule, and the source.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)

        val first = assertIs<SyncReport.Completed>(sync(phone))
        assertTrue(first.pushed)
        // Nothing to merge against on a first sync — this device seeded the remote.
        assertEquals(null, first.merge)
        assertTrue(remote.isFile)

        val laptop = device()
        assertEquals(0, laptop.allReviews().size)
        val second = assertIs<SyncReport.Completed>(sync(laptop))

        assertTrue(second.receivedChanges)
        assertEquals(1, laptop.allReviews().size)
        assertNotNull(laptop.reviews.schedule(ProblemId("two-sum")))
        // The source came across, not just the fact that something was solved.
        assertEquals(
            phone.reviews.selectedSources().values.single(),
            laptop.reviews.selectedSources().values.single(),
        )
        // And the schedule was rebuilt by replaying the log, not copied.
        assertEquals(emptyList(), laptop.verifyScheduleIntegrity())
    }

    @Test
    fun offlineWorkOnBothDevicesConvergesAfterTwoRounds() {
        // Both devices study while apart, both sync. Neither loses anything, and after
        // the second device syncs twice the two agree. This is the scenario a naive
        // "upload my snapshot" implementation silently breaks.
        val phone = device()
        val laptop = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        solve(laptop, "binary-search", ReviewRating.EASY)

        assertIs<SyncReport.Completed>(sync(phone))

        // The laptop pulls the phone's work, merges, and pushes the union.
        val laptopSync = assertIs<SyncReport.Completed>(sync(laptop))
        assertTrue(laptopSync.pushed, "the laptop had local work to contribute")
        assertEquals(2, laptop.allReviews().size)

        // The phone syncs again and now has both.
        val phoneAgain = assertIs<SyncReport.Completed>(sync(phone))
        assertTrue(phoneAgain.receivedChanges)
        assertEquals(2, phone.allReviews().size)
        assertEquals(
            phone.allReviews().map { it.sessionId.value }.sorted(),
            laptop.allReviews().map { it.sessionId.value }.sorted(),
        )
    }

    @Test
    fun syncingTwiceWithNoChangesPushesNothing() {
        // An idle sync must not write. Bumping the remote's token for no reason makes
        // every *other* device see a spurious conflict on its next sync.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        val first = assertIs<SyncReport.Completed>(sync(phone))

        val second = assertIs<SyncReport.Completed>(sync(phone))
        assertTrue(!second.pushed, "an unchanged profile must not push")
        assertTrue(!second.receivedChanges)
        // Same token, so the file was genuinely untouched.
        assertEquals(first.token, second.token)
    }

    @Test
    fun syncingIsIdempotentAndNeverDuplicatesAReview() {
        // Reviews are keyed by session, so repeated syncs are a set union with itself.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        repeat(4) { sync(phone) }

        val laptop = device()
        repeat(4) { sync(laptop) }

        assertEquals(1, phone.allReviews().size)
        assertEquals(1, laptop.allReviews().size)
    }

    @Test
    fun aLostRaceIsRetriedRatherThanForced() {
        // Another device pushes between this device's pull and push. The correct
        // response is re-pull, re-merge, re-push — never overwrite, which would discard
        // the other device's work.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)

        val interloper = device()
        solve(interloper, "binary-search", ReviewRating.GOOD)

        // Seed the remote so both have something to conflict over.
        val seeder = device()
        solve(seeder, "valid-anagram", ReviewRating.GOOD)
        sync(seeder)

        // A store that lets exactly one push slip in behind us before the first attempt.
        val racing = RacingStore(FileSyncStore(remote)) {
            runBlocking { SyncService(FileSyncStore(remote), interloper).sync(NOW) }
        }

        val report = assertIs<SyncReport.Completed>(
            runBlocking { SyncService(racing, phone).sync(NOW) },
        )
        assertTrue(report.pushed)
        assertEquals(1, racing.conflictsObserved, "the compare-and-swap must have fired once")

        // Everyone's work survived: the seeder's, the interloper's, and this device's.
        val laptop = device()
        sync(laptop)
        assertEquals(3, laptop.allReviews().size)
    }

    @Test
    fun repeatedConflictsReportRatherThanSpin() {
        // A remote under continuous write from elsewhere must not loop forever.
        val alwaysConflicts = object : SyncStore {
            override val storeId = "always-conflicts"
            override suspend fun pull() = SyncOutcome.Success(
                SyncSnapshot(payloadText = ProfileTransfer.export(device(), NOW), token = "t"),
            )
            override suspend fun push(payloadText: String, expectedToken: String?) =
                SyncOutcome.Conflict("someone else got there first")
        }

        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        val report = assertIs<SyncReport.Conflicted>(
            runBlocking { SyncService(alwaysConflicts, phone).sync(NOW) },
        )
        assertEquals(SyncService.MAX_ATTEMPTS, report.attempts)
        // Local work is untouched: nothing was lost by failing to push.
        assertEquals(1, phone.allReviews().size)
    }

    @Test
    fun anUnreachableStoreFailsWithoutTouchingTheProfile() {
        val unreachable = object : SyncStore {
            override val storeId = "unreachable"
            override suspend fun pull() = SyncOutcome.Unavailable("no network")
            override suspend fun push(payloadText: String, expectedToken: String?) =
                SyncOutcome.Unavailable("no network")
        }

        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        val report = assertIs<SyncReport.Failed>(
            runBlocking { SyncService(unreachable, phone).sync(NOW) },
        )
        assertTrue(report.reason.contains("no network"), report.reason)
        assertEquals(1, phone.allReviews().size)
    }

    @Test
    fun aCorruptRemoteSnapshotIsRefusedRatherThanApplied() {
        // Half a file, or someone else's data. Refusing beats importing nonsense into a
        // profile that currently works.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        remote.writeText("{ not a snapshot")

        val report = assertIs<SyncReport.Failed>(sync(phone))
        assertTrue(report.reason.contains("not readable"), report.reason)
        assertEquals(1, phone.allReviews().size)
    }

    @Test
    fun aPushDoesNotLeaveATemporaryFileBehind() {
        // The atomic write uses a sibling temp file; leaving one would make the sync
        // directory accumulate litter in whatever folder the learner chose.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        sync(phone)
        sync(phone)
        assertEquals(
            listOf(remote.name),
            directory.listFiles()!!.map { it.name }.filter { it.startsWith(remote.name) }.sorted(),
        )
    }

    @Test
    fun theFileStoreRefusesToOverwriteAnUnexpectedRemote() {
        // The compare-and-swap itself, at the store level: a stale token must not win.
        val store = FileSyncStore(remote)
        runBlocking {
            val seeded = assertIs<SyncOutcome.Success<String>>(store.push("{}", expectedToken = null))
            assertIs<SyncOutcome.Success<String>>(store.push("{\"a\":1}", expectedToken = seeded.value))
            // Replaying the first token must now fail.
            val stale = assertIs<SyncOutcome.Conflict>(store.push("{\"b\":2}", expectedToken = seeded.value))
            assertTrue(stale.reason.contains("changed"), stale.reason)
            // And claiming nothing exists when something does must also fail.
            assertIs<SyncOutcome.Conflict>(store.push("{\"c\":3}", expectedToken = null))
        }
    }

    @Test
    fun anEmptySyncFileIsSeededRatherThanTreatedAsCorrupt() {
        // The state Android's document picker leaves behind. `CreateDocument` makes a
        // zero-byte file the moment the learner names it, and that file replicates to the
        // desktop through the very folder sync BeeCode tells them to use — so the desktop's
        // *first* sync sees an empty remote, not an absent one.
        //
        // Treating it as a real snapshot wedged sync permanently: the merge cannot parse "",
        // so every sync reported "the remote snapshot is not readable", and no sync ever
        // pushed, so nothing healed it. Turning sync off and on again did not help either,
        // because the empty file stayed.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        remote.writeText("")

        val report = assertIs<SyncReport.Completed>(sync(phone))
        assertTrue(report.pushed, "an empty remote must be seeded, not refused")
        assertTrue(remote.readText().contains("formatVersion"))

        // And the seeded file is a real remote for the other device, which is the point.
        val laptop = device()
        assertTrue(assertIs<SyncReport.Completed>(sync(laptop)).receivedChanges)
        assertEquals(1, laptop.allReviews().size)
    }

    @Test
    fun aWhitespaceOnlySyncFileIsAlsoSeeded() {
        // A newline is what several replicating clients leave when they touch a file, and it
        // is just as unparseable as zero bytes. Asserted separately from the empty case
        // because a fix that only checked `isEmpty()` would pass that test and fail here.
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        remote.writeText("\n  \n")

        assertTrue(assertIs<SyncReport.Completed>(sync(phone)).pushed)
        assertTrue(remote.readText().contains("formatVersion"))
    }

    @Test
    fun theFileStorePushAgreesWithPullAboutAnEmptyRemote() {
        // pull() and push() must classify a blank file the same way. If pull says "nothing
        // there" (token null) but push still hashes the zero bytes, the seeding push
        // mismatches its expected token and reports a conflict on every attempt — sync
        // wedged again, just with a different message.
        remote.writeText("")
        runBlocking {
            val pulled = assertIs<SyncOutcome.Success<SyncSnapshot?>>(FileSyncStore(remote).pull())
            assertEquals(null, pulled.value, "a blank file holds no snapshot")
            assertIs<SyncOutcome.Success<String>>(
                FileSyncStore(remote).push("{\"a\":1}", expectedToken = null),
            )
        }
    }

    @Test
    fun theFileStoreTokenIsAContentHashSoIdenticalContentGivesTheSameToken() {
        // Determinism the merge relies on: two devices that computed the same merged
        // snapshot must agree on its token, or each would see the other's push as a
        // change. A modification time would not have this property.
        val other = File(directory, "other.json")
        runBlocking {
            FileSyncStore(remote).push("{\"same\":true}", expectedToken = null)
            FileSyncStore(other).push("{\"same\":true}", expectedToken = null)
            val a = assertIs<SyncOutcome.Success<SyncSnapshot?>>(FileSyncStore(remote).pull())
            val b = assertIs<SyncOutcome.Success<SyncSnapshot?>>(FileSyncStore(other).pull())
            assertEquals(a.value!!.token, b.value!!.token)
        }
    }

    @Test
    fun theSyncFileIsNotReadableByOtherUsers() {
        // The snapshot contains the learner's source code, and a default umask of 022 would
        // make it 0644 — readable by every other user on the machine. The learner chose a
        // folder that something else replicates, which does not mean they chose to share it
        // with everyone logged into the same box.
        //
        // Skipped where POSIX permissions do not exist, because the production code is
        // best-effort there and asserting an absent capability would be a false failure.
        org.junit.Assume.assumeTrue(
            "this filesystem has no POSIX permissions",
            java.nio.file.Files.getFileStore(directory.toPath()).supportsFileAttributeView("posix"),
        )
        val phone = device()
        solve(phone, "two-sum", ReviewRating.GOOD)
        assertIs<SyncReport.Completed>(sync(phone))

        val permissions = java.nio.file.Files.getPosixFilePermissions(remote.toPath())
        assertTrue(
            permissions.none { it.name.startsWith("GROUP_") || it.name.startsWith("OTHERS_") },
            "the sync file must not be readable by others, found: $permissions",
        )
        // And the owner can still read it back, which is the point.
        assertIs<SyncOutcome.Success<SyncSnapshot?>>(runBlocking { FileSyncStore(remote).pull() })
    }

    // ---- Fixtures ------------------------------------------------------------

    /** A fresh device: its own in-memory database over the real Problem pack. */
    private fun device(): BeeCodeProfile {
        val profile = BeeCodeProfile.inMemory(
            catalogue = ProblemCatalogue.fromSourceDirectory(File(repoRoot(), "content/packs/core")),
            runner = ScriptedRunner(),
        )
        open += profile
        return profile
    }

    private fun sync(profile: BeeCodeProfile): SyncReport =
        runBlocking { SyncService(FileSyncStore(remote), profile).sync(NOW) }

    private fun solve(profile: BeeCodeProfile, problem: String, rating: ReviewRating) = runBlocking {
        val problemId = ProblemId(problem)
        profile.study.open(problemId)
        val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, "# solved on ${profile.hashCode()}\n"))
        profile.study.finalize(problemId, run.run.id, rating)
    }

    /**
     * Wraps a store and runs [interfere] once, immediately before the first push.
     *
     * A deterministic stand-in for another device winning the race. Doing it with a real
     * second sync, rather than by faking a Conflict, means the retry path is exercised
     * against a genuinely advanced remote.
     */
    private class RacingStore(
        private val delegate: SyncStore,
        private val interfere: () -> Unit,
    ) : SyncStore {
        var conflictsObserved: Int = 0
            private set
        private var interfered = false

        override val storeId: String get() = delegate.storeId

        override suspend fun pull(): SyncOutcome<SyncSnapshot?> = delegate.pull()

        override suspend fun push(payloadText: String, expectedToken: String?): SyncOutcome<String> {
            if (!interfered) {
                interfered = true
                interfere()
            }
            val result = delegate.push(payloadText, expectedToken)
            if (result is SyncOutcome.Conflict) conflictsObserved++
            return result
        }
    }

    /** Passes anything, so no interpreter is needed for a test about sync. */
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
