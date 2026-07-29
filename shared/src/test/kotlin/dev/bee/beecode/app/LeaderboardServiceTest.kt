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
import kotlinx.datetime.TimeZone
import java.io.File
import kotlin.io.path.createTempFile
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Leaderboard activity end to end: solve, project, queue, upload, survive a restart.
 *
 * The pieces are each tested in isolation — the projection's privacy rules, the outbox's
 * state transitions, the repository's durability. This tests that they compose, which is
 * where an ordering mistake would live: forgetting to save, projecting before recovering
 * stranded rows, or double-counting after a reinstall.
 *
 * Real profiles over a real database file, because the whole point of the previous commit
 * was that the queue survives a process death, and an in-memory profile cannot show that.
 * The server is a lambda: there isn't one yet, which is exactly why the transport is a
 * parameter.
 */
class LeaderboardServiceTest {

    private val files = mutableListOf<File>()

    @AfterTest
    fun tearDown() {
        files.forEach { file ->
            listOf("", "-wal", "-shm").forEach { File(file.absolutePath + it).delete() }
        }
    }

    @Test
    fun solvingThenRefreshingQueuesTheActivity() {
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)

            val result = service.refresh(linkedAt = NOW, now = NOW, zone = TimeZone.UTC)
            assertEquals(1, result.eventsAdded)
            assertEquals(1, service.status().pending)
        }
    }

    @Test
    fun theQueueSurvivesReopeningTheProfile() {
        // The durability claim at the level a learner experiences it: study, close the app,
        // reopen it, and the unsent activity is still waiting.
        val file = tempFile()
        withProfile(file) { profile ->
            solve(profile, "two-sum")
            solve(profile, "binary-search")
            LeaderboardService(profile).refresh(NOW, NOW, TimeZone.UTC)
        }
        withProfile(file) { profile ->
            assertEquals(2, LeaderboardService(profile).status().pending)
        }
    }

    @Test
    fun refreshingIsSafeToRunOnEveryLaunch() {
        // It is called on launch, after a sync, and after a restore. If it were not
        // idempotent, every launch would add a duplicate count.
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)

            assertEquals(1, service.refresh(NOW, NOW, TimeZone.UTC).eventsAdded)
            assertEquals(0, service.refresh(NOW, NOW + 1.hours, TimeZone.UTC).eventsAdded)
            assertEquals(0, service.refresh(NOW, NOW + 2.hours, TimeZone.UTC).eventsAdded)
            assertEquals(1, service.status().pending)
        }
    }

    @Test
    fun preLinkActivityIsNeverQueuedEvenAfterManyRefreshes() {
        // The privacy rule holds through composition, not just in the projection: a learner
        // who studied before joining a board does not leak that history on the tenth launch.
        withProfile { profile ->
            solve(profile, "two-sum", at = NOW)
            val linkedAt = NOW + 30.days
            val service = LeaderboardService(profile)

            repeat(10) { service.refresh(linkedAt, linkedAt + it.hours, TimeZone.UTC) }
            assertEquals(0, service.status().pending)

            // And activity after linking does queue, so this is a cutoff rather than the
            // queue being broken.
            solve(profile, "binary-search", at = linkedAt + 1.hours)
            service.refresh(linkedAt, linkedAt + 2.hours, TimeZone.UTC)
            assertEquals(1, service.status().pending)
        }
    }

    @Test
    fun aSuccessfulUploadAcknowledgesAndStopsRetrying() {
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)

            val report = runBlocking {
                service.uploadWith(NOW) { events ->
                    events.associate { it.eventId to UploadVerdict.Accepted }
                }
            }
            assertEquals(1, report.attempted)
            assertEquals(1, report.accepted)
            assertEquals(0, service.status().pending)
            assertEquals(1, service.status().acknowledged)

            // Nothing left to send, so a second attempt does nothing rather than re-uploading.
            val second = runBlocking { service.uploadWith(NOW) { error("must not be called") } }
            assertTrue(!second.didAnything)
        }
    }

    @Test
    fun aServerThatGoesQuietAboutSomeEventsDefersThemRatherThanAssumingSuccess() {
        // A partial answer is not an answer. Assuming success from silence is exactly how a
        // Leaderboard count goes missing with nobody noticing.
        withProfile { profile ->
            solve(profile, "two-sum")
            solve(profile, "binary-search")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)

            val report = runBlocking {
                service.uploadWith(NOW) { events ->
                    // Answers for the first only.
                    mapOf(events.first().eventId to UploadVerdict.Accepted)
                }
            }
            assertEquals(2, report.attempted)
            assertEquals(1, report.accepted)
            assertEquals(1, report.deferred)
            // The unanswered one is pending again, with its backoff applied.
            assertEquals(1, service.status().pending)
            assertEquals(1, service.status().acknowledged)
        }
    }

    @Test
    fun aTransportThatThrowsDoesNotLoseTheQueue() {
        // A throwing transport is a bug in the transport, and must not cost the learner
        // their unsent activity.
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)

            val report = runBlocking {
                service.uploadWith(NOW) { error("socket exploded") }
            }
            assertEquals(1, report.deferred)
            assertEquals(1, service.status().pending)
            assertEquals(0, service.status().rejected)
        }
    }

    @Test
    fun aProcessDeathMidUploadRecoversOnTheNextRefresh() {
        // The interesting durability case. Mark in flight, then die — the rows are persisted
        // as IN_FLIGHT with no verdict coming. refresh() must recover them, because dropping
        // loses a count and leaving them strands the queue forever.
        val file = tempFile()
        withProfile(file) { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)
            // A genuine process death: the rows must be left IN_FLIGHT on disk with no
            // verdict ever applied.
            //
            // An earlier version of this test returned emptyMap() from the upload lambda,
            // which does NOT do that — uploadWith returns normally, applies its
            // "server did not answer" fallback, and saves the rows as pending. So the test
            // passed while never exercising recovery at all, and a mutation removing
            // recoverAfterRestart survived it. Throwing from inside the lambda after the
            // in-flight save is what actually strands them.
            runBlocking {
                runCatching {
                    service.uploadWith(NOW) { throw KillProcess() }
                }
            }
            // Prove the precondition rather than assuming it.
            assertEquals(1, service.status().inFlight, "the rows must be stranded in flight")
        }
        withProfile(file) { profile ->
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW + 2.hours, TimeZone.UTC)
            assertEquals(1, service.status().pending, "a stranded row must come back as pending")

            // And it uploads cleanly, with the server reporting the duplicate it already has.
            val report = runBlocking {
                service.uploadWith(NOW + 2.hours) { events ->
                    events.associate { it.eventId to UploadVerdict.Duplicate }
                }
            }
            assertEquals(1, report.accepted, "a duplicate counts as success")
            assertEquals(1, service.status().acknowledged)
        }
    }

    @Test
    fun aRejectedEventDoesNotTouchTheLocalReview() {
        // "Rejection cannot undo local review, FSRS, or local achievement."
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)

            runBlocking {
                service.uploadWith(NOW) { events ->
                    events.associate { it.eventId to UploadVerdict.Rejected("unknown revision") }
                }
            }
            assertEquals(1, service.status().rejected)
            // The learner's work is entirely unaffected.
            assertEquals(1, profile.allReviews().size)
            assertTrue(profile.allReviews().single().countsAsSolved)
            assertEquals(1, profile.statistics().distinctProblemsSolved)
            assertTrue(profile.reviews.schedule(ProblemId("two-sum")) != null)
        }
    }

    @Test
    fun repeatedOutagesParkAndAManualRetryRevivesThem() {
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)

            runBlocking {
                repeat(ActivityOutbox.MAX_ATTEMPTS) { attempt ->
                    service.uploadWith(NOW + (attempt * 24).hours) { events ->
                        events.associate { it.eventId to UploadVerdict.Retryable("502") }
                    }
                }
            }
            assertEquals(1, service.status().parked)
            assertTrue(service.status().needsAttention)

            assertEquals(1, service.retryParked(NOW + 30.days))
            assertEquals(1, service.status().pending)
        }
    }

    @Test
    fun forgettingClearsTheQueueAndKeepsEveryLocalReview() {
        // Unlinking an account must not cost a learner their study history: the Leaderboard
        // is a view of study, never its source.
        withProfile { profile ->
            solve(profile, "two-sum")
            solve(profile, "binary-search")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)
            assertEquals(2, service.status().pending)

            service.forget()
            assertEquals(0, service.status().pending)
            assertEquals(2, profile.allReviews().size)
            assertEquals(2, profile.statistics().distinctProblemsSolved)
        }
    }

    @Test
    fun acknowledgedRowsPruneOnceTheirRetentionElapses() {
        withProfile { profile ->
            solve(profile, "two-sum")
            val service = LeaderboardService(profile)
            service.refresh(NOW, NOW, TimeZone.UTC)
            runBlocking {
                service.uploadWith(NOW) { events -> events.associate { it.eventId to UploadVerdict.Accepted } }
            }
            assertEquals(1, service.status().acknowledged)

            // A later refresh prunes it, and does not re-queue the same review.
            val later = NOW + ActivityOutbox.ACKNOWLEDGED_RETENTION + 1.days
            val result = service.refresh(NOW, later, TimeZone.UTC)
            assertEquals(0, service.status().acknowledged)
            assertEquals(
                0,
                service.status().pending,
                "a pruned-and-acknowledged review must not be queued again",
            )
            assertEquals(1, result.eventsConsidered, "the review is still considered, just not re-added")
        }
    }

    // ---- Fixtures ------------------------------------------------------------

    private fun tempFile(): File {
        val file = createTempFile("beecode-leaderboard-", ".db").toFile()
        file.delete()
        files += file
        return file
    }

    private fun withProfile(file: File = tempFile(), body: (BeeCodeProfile) -> Unit) {
        BeeCodeProfile.open(
            databasePath = file.absolutePath,
            catalogue = ProblemCatalogue.fromSourceDirectory(File(repoRoot(), "content/packs/core")),
            runner = PassingRunner(),
            clock = MutableClock,
        ).use(body)
    }

    private fun solve(profile: BeeCodeProfile, problem: String, at: Instant = NOW) = runBlocking {
        MutableClock.now = at
        val problemId = ProblemId(problem)
        profile.study.open(problemId)
        val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, "# pass\n"))
        profile.study.finalize(problemId, run.run.id, ReviewRating.GOOD)
    }

    /** A clock the fixtures move, so finalization instants are chosen rather than observed. */
    private object MutableClock : kotlinx.datetime.Clock {
        var now: Instant = Instant.parse("2026-07-29T12:00:00Z")
        override fun now(): Instant = now
    }

    private class PassingRunner : PythonRunner {
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

    /**
     * Stands in for the process dying mid-upload.
     *
     * An Error rather than an Exception on purpose: `uploadWith` catches Exception to turn a
     * throwing transport into a retryable batch, which is correct behaviour and exactly what
     * must NOT happen here. An Error escapes that catch, so the rows stay in flight on disk
     * the way a real kill -9 would leave them.
     */
    private class KillProcess : Error("simulated process death")

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
