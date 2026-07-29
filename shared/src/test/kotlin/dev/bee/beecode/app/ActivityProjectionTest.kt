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
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/**
 * Which reviews the Leaderboard is entitled to know about.
 *
 * Two rules from `goals/08-leaderboards.md`, and both are the kind that fails quietly:
 *
 * - **No pre-link backfill.** A learner who studied for months then joins a board must not
 *   arrive with months of activity. That would look like cheating to everyone else on it,
 *   and nobody consented to sharing a history predating the decision to share.
 * - **Only unaided passes count.** The same rule that stops the 5am Club being farmed by
 *   reading an explanation.
 *
 * Driven through a real profile and real SQLite rather than hand-built review objects, so
 * `countsAsSolved` is whatever the domain actually decided rather than whatever this test
 * assumes it decides.
 */
class ActivityProjectionTest {

    private val open = mutableListOf<BeeCodeProfile>()

    @AfterTest
    fun tearDown() = open.forEach { it.close() }

    @Test
    fun reviewsFinalizedBeforeLinkingAreNeverUploaded() {
        // The headline rule. Three solves before linking, one after: only the last is the
        // board's business.
        val profile = profile()
        solve(profile, "two-sum")
        solve(profile, "binary-search")
        solve(profile, "valid-anagram")
        val linkedAt = LATER
        solve(profile, "contains-duplicate", at = LATER)

        // The first three were finalized at NOW, the last at LATER — see solve().
        val events = ActivityProjection.eventsFor(profile.allReviews(), linkedAt, TimeZone.UTC)
        assertEquals(1, events.size, "only post-link activity may be uploaded")
        assertEquals("contains-duplicate", events.single().problemId)
    }

    @Test
    fun aReviewInTheSameInstantAsLinkingIsIncluded() {
        // Deliberately >= rather than >: a review finalized in the linking instant happened
        // after the learner chose to share, so excluding it would lose real activity.
        val profile = profile()
        solve(profile, "two-sum", at = LATER)
        val events = ActivityProjection.eventsFor(profile.allReviews(), LATER, TimeZone.UTC)
        assertEquals(1, events.size)
    }

    @Test
    fun onlyUnaidedPassesBecomeActivity() {
        // A failure and a revealed pass are real study but not solves, and the board counts
        // solves. Asserted through the domain's own countsAsSolved rather than re-derived.
        val profile = profile()
        solve(profile, "two-sum")
        fail(profile, "binary-search")
        revealThenPass(profile, "valid-anagram")

        val reviews = profile.allReviews()
        assertEquals(3, reviews.size, "all three are real reviews locally")

        val events = ActivityProjection.eventsFor(reviews, NOW, TimeZone.UTC)
        assertEquals(listOf("two-sum"), events.map { it.problemId })
    }

    @Test
    fun theEventIdIsTheReviewSessionSoReplayIsIdempotent() {
        // The projection is derived, not emitted, which is what makes an outbox lost to a
        // reinstall rebuildable. That only works if the key is stable: minting a fresh id
        // would make every rebuild look like new activity and double every count.
        val profile = profile()
        solve(profile, "two-sum")
        val first = ActivityProjection.eventsFor(profile.allReviews(), NOW, TimeZone.UTC)
        val second = ActivityProjection.eventsFor(profile.allReviews(), NOW, TimeZone.UTC)

        assertEquals(first.map { it.eventId }, second.map { it.eventId })
        assertEquals(
            profile.allReviews().single().sessionId.value,
            first.single().eventId,
        )
    }

    @Test
    fun enqueueingTwiceAddsNothingTheSecondTime() {
        // Safe to run on launch, after a sync, and after a restore.
        val profile = profile()
        solve(profile, "two-sum")
        solve(profile, "binary-search")

        val first = ActivityProjection.enqueueNew(emptyList(), profile.allReviews(), NOW, TimeZone.UTC, NOW)
        assertEquals(2, first.eventsAdded)

        val second = ActivityProjection.enqueueNew(first.rows, profile.allReviews(), NOW, TimeZone.UTC, NOW)
        assertEquals(0, second.eventsAdded)
        assertEquals(2, second.eventsConsidered, "still considered, just not re-added")
        assertEquals(2, second.rows.size)
    }

    @Test
    fun anAcknowledgedEventIsNotResurrectedByAReplay() {
        // The case that would double-count after a restore: the row is already settled, and
        // re-projecting must not push it back to pending.
        val profile = profile()
        solve(profile, "two-sum")
        val queued = ActivityProjection.enqueueNew(emptyList(), profile.allReviews(), NOW, TimeZone.UTC, NOW)
        val settled = ActivityOutbox.applyVerdict(
            queued.rows,
            queued.rows.single().event.eventId,
            UploadVerdict.Accepted,
            NOW,
        )

        val replayed = ActivityProjection.enqueueNew(settled, profile.allReviews(), NOW, TimeZone.UTC, NOW + 1.days)
        assertEquals(0, replayed.eventsAdded)
        assertEquals(OutboxState.ACKNOWLEDGED, replayed.rows.single().state)
        assertEquals(emptyList(), ActivityOutbox.nextBatch(replayed.rows, NOW + 30.days))
    }

    @Test
    fun theLocalDateComesFromTheProfileZoneNotUtc() {
        // A streak is a local-calendar question. Deriving it server-side from UTC would get
        // travel and DST wrong, so the date travels with the event.
        val profile = profile()
        // 23:30 UTC on the 29th is already the 30th in Tokyo.
        solve(profile, "two-sum", at = Instant.parse("2026-07-29T23:30:00Z"))

        val utc = ActivityProjection.eventsFor(profile.allReviews(), NOW, TimeZone.UTC).single()
        val tokyo = ActivityProjection.eventsFor(
            profile.allReviews(),
            NOW,
            TimeZone.of("Asia/Tokyo"),
        ).single()

        assertEquals("2026-07-29", utc.localDate)
        assertEquals("2026-07-30", tokyo.localDate)
    }

    @Test
    fun eventsAreOrderedByWhenTheyHappened() {
        val profile = profile()
        solve(profile, "binary-search", at = NOW + 2.hours)
        solve(profile, "two-sum", at = NOW)
        val events = ActivityProjection.eventsFor(profile.allReviews(), NOW, TimeZone.UTC)
        assertEquals(listOf("two-sum", "binary-search"), events.map { it.problemId })
    }

    @Test
    fun anEmptyHistoryProducesNothing() {
        val profile = profile()
        assertEquals(emptyList(), ActivityProjection.eventsFor(profile.allReviews(), NOW, TimeZone.UTC))
    }

    // ---- Fixtures ------------------------------------------------------------

    private fun profile(): BeeCodeProfile {
        val profile = BeeCodeProfile.inMemory(
            catalogue = ProblemCatalogue.fromSourceDirectory(File(repoRoot(), "content/packs/core")),
            runner = ScriptedRunner(),
            clock = MutableClock,
        )
        open += profile
        return profile
    }

    private fun solve(profile: BeeCodeProfile, problem: String, at: Instant = NOW) = runBlocking {
        MutableClock.now = at
        val problemId = ProblemId(problem)
        profile.study.open(problemId)
        val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, "# pass\n"))
        profile.study.finalize(problemId, run.run.id, ReviewRating.GOOD)
    }

    private fun fail(profile: BeeCodeProfile, problem: String) = runBlocking {
        MutableClock.now = NOW
        val problemId = ProblemId(problem)
        profile.study.open(problemId)
        val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, "# fail\n"))
        // A non-pass permits only Again, which the domain enforces.
        profile.study.finalize(problemId, run.run.id, ReviewRating.AGAIN)
    }

    private fun revealThenPass(profile: BeeCodeProfile, problem: String) = runBlocking {
        MutableClock.now = NOW
        val problemId = ProblemId(problem)
        profile.study.open(problemId)
        profile.study.reveal(problemId)
        val run = assertIs<RunOutcome.Completed>(profile.study.run(problemId, "# pass\n"))
        // Capped at Hard after a reveal, and not a solve.
        profile.study.finalize(problemId, run.run.id, ReviewRating.HARD)
    }

    /** A clock the fixtures move, so finalization instants are chosen rather than observed. */
    private object MutableClock : kotlinx.datetime.Clock {
        var now: Instant = Instant.parse("2026-07-29T12:00:00Z")
        override fun now(): Instant = now
    }

    /** Passes anything not containing "fail", so no interpreter is needed. */
    private class ScriptedRunner : PythonRunner {
        override val runnerId = "scripted"
        override val capability = RunnerCapability.SEPARATE_PROCESS

        override suspend fun probe() = RunnerProbe(
            available = true,
            pythonVersion = "3.12.0 (scripted)",
            capability = capability,
            unavailableReason = null,
        )

        override suspend fun execute(request: RunRequest): RunResult {
            val passes = !request.source.contains("fail")
            return RunResult(
                runId = request.runId,
                outcome = if (passes) ExecutionOutcome.PASSED else ExecutionOutcome.FAILED,
                testResults = request.tests.map {
                    TestCaseResult(
                        name = it.name,
                        passed = passes,
                        hidden = it.hidden,
                        expectedJson = if (it.hidden) null else it.expectedJson,
                        actualJson = if (it.hidden) null else it.expectedJson,
                        message = if (passes) null else "wrong",
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
    }

    private companion object {
        val NOW: Instant = Instant.parse("2026-07-29T12:00:00Z")
        val LATER: Instant = Instant.parse("2026-08-15T09:00:00Z")

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
