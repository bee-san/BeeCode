package dev.bee.beecode.app

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.ReviewSessionState
import dev.bee.beecode.python.jvm.ProcessPythonRunner
import kotlinx.coroutines.runBlocking
import org.junit.Assume.assumeTrue
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The plan's Test 1 journey, automated end to end.
 *
 * Real CPython, real SQLite, real content, real FSRS. No fakes anywhere in the
 * path — which is the only way this test can be evidence that the gate actually
 * passes rather than that the mocks agree with each other.
 *
 * The journey: open a bundled Problem, write Python, see one intentional failure,
 * correct it, pass, finalize, and confirm the source, history, and due date survive
 * a restart.
 */
class StudyJourneyTest {
    private lateinit var databaseFile: File
    private lateinit var catalogue: ProblemCatalogue
    private val runner = ProcessPythonRunner()

    @BeforeTest
    fun setUp() {
        assumeTrue("Python 3 is unavailable", runBlocking { runner.probe().available })
        databaseFile = kotlin.io.path.createTempFile("beecode-journey-", ".db").toFile()
        // Delete so the profile creates and migrates it, as a first launch would.
        databaseFile.delete()
        catalogue = ProblemCatalogue.fromSourceDirectory(File(repoRoot(), "content/packs/core"))
    }

    @AfterTest
    fun tearDown() {
        databaseFile.delete()
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    @Test
    fun theFailThenFixThenFinalizeThenRestartJourneyWorks() = runBlocking {
        val problemId = ProblemId("two-sum")

        // ---- First launch -------------------------------------------------
        val dueDate: kotlinx.datetime.Instant
        val finalizedSource: String
        openProfile().use { profile ->
            // The queue offers unattempted Problems.
            val queue = profile.study.queue()
            assertTrue(queue.due.isEmpty(), "nothing can be due before anything is reviewed")
            assertTrue(queue.new.any { it.id == problemId }, "two-sum must be offered as new")

            val opened = assertNotNull(profile.study.open(problemId))
            assertTrue(opened.isFirstAttempt)
            assertTrue(opened.draft.isPristine, "a fresh draft starts at the starter")
            assertEquals(ReviewSessionState.STARTED, opened.session.state)
            assertTrue(opened.history.isEmpty())

            // ---- The intentional failure ----------------------------------
            //
            // Deliberately linear but wrong: it returns the same index twice
            // instead of the pair. A quadratic wrong answer would time out on
            // two-sum's 20,000-element case rather than failing, which is the
            // pack's complexity guard doing its job but the wrong signal here.
            val wrong = """
                def two_sum(nums, target):
                    seen = {}
                    for index, value in enumerate(nums):
                        if target - value in seen:
                            return [index, index]
                        seen[value] = index
                    return []
            """.trimIndent()
            val failed = assertIs<RunOutcome.Completed>(profile.study.run(problemId, wrong))
            assertEquals(ExecutionOutcome.FAILED, failed.run.outcome, failed.run.output)
            assertEquals(ReviewSessionState.WORKING, failed.session.state)
            assertTrue(failed.run.testResults.any { !it.passed })
            // A failure may only be rated Again.
            assertEquals(
                setOf(ReviewRating.AGAIN),
                profile.study.permittedRatings(problemId, failed.run.id),
            )

            // ---- The correction ------------------------------------------
            val correct = """
                def two_sum(nums, target):
                    seen = {}
                    for index, value in enumerate(nums):
                        if target - value in seen:
                            return [seen[target - value], index]
                        seen[value] = index
                    return []
            """.trimIndent()
            val passed = assertIs<RunOutcome.Completed>(profile.study.run(problemId, correct))
            assertEquals(ExecutionOutcome.PASSED, passed.run.outcome, passed.run.output)
            assertEquals(ReviewSessionState.PASSED, passed.session.state)
            assertEquals(2, passed.session.runs.size, "both attempts are retained")
            // An unaided pass permits every rating.
            assertEquals(
                ReviewRating.entries.toSet(),
                profile.study.permittedRatings(problemId, passed.run.id),
            )
            assertEquals(ReviewRating.GOOD, profile.study.defaultRating(problemId, passed.run.id))

            // ---- Finalize -------------------------------------------------
            val result = assertIs<FinalizeResult.Finalized>(
                profile.study.finalize(problemId, passed.run.id, ReviewRating.GOOD),
            )
            assertFalse(result.wasAlreadyFinalized)
            assertTrue(result.review.countsAsSolved, "an unaided pass counts as solved")
            assertFalse(result.review.aided)
            val schedule = assertNotNull(result.schedule)
            assertTrue(schedule.intervalDays >= 1)
            dueDate = schedule.dueAt
            finalizedSource = result.review.let { correct }

            // The source that ran is what got credited.
            assertEquals(correct, passed.run.source)
        }

        // ---- Restart ------------------------------------------------------
        // A brand-new profile over the same file: the same thing a relaunch does.
        openProfile().use { profile ->
            val opened = assertNotNull(profile.study.open(problemId))

            // The draft survived.
            assertEquals(finalizedSource, opened.draft.source, "typed source must survive a restart")

            // The finalized history survived.
            assertEquals(1, opened.history.size)
            assertTrue(opened.history.first().countsAsSolved)

            // The due date survived, exactly.
            val schedule = assertNotNull(opened.schedule)
            assertEquals(dueDate, schedule.dueAt, "the due date must survive a restart")
            assertEquals(1, schedule.reviewCount)

            // And it is a fresh session, not the finalized one.
            assertEquals(ReviewSessionState.STARTED, opened.session.state)

            // Statistics reflect the solve.
            val statistics = profile.statistics()
            assertEquals(1, statistics.totalReviews)
            assertEquals(1, statistics.totalSolved)
            assertEquals(1, statistics.distinctProblemsSolved)
            assertEquals(0, statistics.lapses)
            assertEquals(1.0, statistics.accuracy)

            // The first-solve achievement was earned.
            val firstSolve = assertNotNull(profile.achievement(Achievements.FIRST_SOLVE))
            assertTrue(firstSolve.earned, "solving one Problem must earn First Solve")

            // Replaying the log reproduces the stored schedule.
            assertTrue(
                profile.verifyScheduleIntegrity().isEmpty(),
                "replaying the review log must reproduce the stored schedule",
            )
        }
    }

    @Test
    fun revealingTheExplanationCapsTheRatingAndForfeitsTheSolve() = runBlocking {
        val problemId = ProblemId("two-sum")
        openProfile().use { profile ->
            profile.study.open(problemId)

            val reveal = assertNotNull(profile.study.reveal(problemId))
            assertTrue(reveal.explanationMarkdown.isNotBlank())
            assertTrue(reveal.session.aided)

            val correct = """
                def two_sum(nums, target):
                    seen = {}
                    for index, value in enumerate(nums):
                        if target - value in seen:
                            return [seen[target - value], index]
                        seen[value] = index
                    return []
            """.trimIndent()
            val passed = assertIs<RunOutcome.Completed>(profile.study.run(problemId, correct))
            assertEquals(ExecutionOutcome.PASSED, passed.run.outcome)

            // Capped at Hard: a pass after reading the answer is recognition, not
            // recall.
            assertEquals(
                setOf(ReviewRating.AGAIN, ReviewRating.HARD),
                profile.study.permittedRatings(problemId, passed.run.id),
            )

            // Asking for Good is refused with a reason rather than downgraded.
            val rejected = assertIs<FinalizeResult.Rejected>(
                profile.study.finalize(problemId, passed.run.id, ReviewRating.GOOD),
            )
            assertTrue(rejected.reason.contains("not permitted"), rejected.reason)

            val finalized = assertIs<FinalizeResult.Finalized>(
                profile.study.finalize(problemId, passed.run.id, ReviewRating.HARD),
            )
            assertTrue(finalized.review.aided)
            // This is what stops the 5am Club being farmed by revealing.
            assertFalse(finalized.review.countsAsSolved)

            assertEquals(0, profile.statistics().totalSolved)
            assertFalse(assertNotNull(profile.achievement(Achievements.FIRST_SOLVE)).earned)
        }
    }

    @Test
    fun aTimeoutIsRatedAsALapseAndDoesNotLoseTheSource() = runBlocking {
        val problemId = ProblemId("two-sum")
        openProfile().use { profile ->
            profile.study.open(problemId)

            val infinite = "def two_sum(nums, target):\n    while True:\n        pass\n"
            val timedOut = assertIs<RunOutcome.Completed>(profile.study.run(problemId, infinite))
            assertEquals(ExecutionOutcome.TIMEOUT, timedOut.run.outcome)

            // A timeout is evidence about recall, so it can be finalized — as Again.
            assertEquals(
                setOf(ReviewRating.AGAIN),
                profile.study.permittedRatings(problemId, timedOut.run.id),
            )
            val finalized = assertIs<FinalizeResult.Finalized>(
                profile.study.finalize(problemId, timedOut.run.id, ReviewRating.AGAIN),
            )
            assertFalse(finalized.review.countsAsSolved)
            assertEquals(1, assertNotNull(finalized.schedule).lapseCount)

            // The infinite loop the learner wrote is still there to fix.
            assertEquals(infinite, assertNotNull(profile.drafts.draft(problemId)).source)
        }
    }

    @Test
    fun finalizingTwiceFromTheSameSessionHasOneEffect() = runBlocking {
        val problemId = ProblemId("contains-duplicate")
        openProfile().use { profile ->
            val opened = assertNotNull(profile.study.open(problemId))
            val source = """
                def contains_duplicate(nums):
                    return len(set(nums)) != len(nums)
            """.trimIndent()
            val passed = assertIs<RunOutcome.Completed>(profile.study.run(problemId, source))
            assertEquals(ExecutionOutcome.PASSED, passed.run.outcome, passed.run.output)

            val first = assertIs<FinalizeResult.Finalized>(
                profile.study.finalize(problemId, passed.run.id, ReviewRating.GOOD),
            )
            assertFalse(first.wasAlreadyFinalized)

            // The session is finalized, so a second attempt is refused outright.
            val second = profile.study.finalize(problemId, passed.run.id, ReviewRating.GOOD)
            assertIs<FinalizeResult.Rejected>(second)

            assertEquals(1, profile.reviews.reviewCount())
            assertEquals(1, assertNotNull(profile.reviews.schedule(problemId)).reviewCount)
            assertEquals(opened.session.id, first.review.sessionId)
        }
    }

    @Test
    fun aReviewedProblemBecomesDueAndLeavesTheNewQueue() = runBlocking {
        val problemId = ProblemId("valid-anagram")
        openProfile().use { profile ->
            profile.study.open(problemId)
            val source = """
                def is_anagram(s, t):
                    return sorted(s) == sorted(t)
            """.trimIndent()
            val passed = assertIs<RunOutcome.Completed>(profile.study.run(problemId, source))
            assertEquals(ExecutionOutcome.PASSED, passed.run.outcome, passed.run.output)
            profile.study.finalize(problemId, passed.run.id, ReviewRating.GOOD)

            val queue = profile.study.queue()
            assertFalse(
                queue.new.any { it.id == problemId },
                "a reviewed Problem must leave the new queue",
            )
            // Not due yet either: it was just reviewed.
            assertFalse(queue.due.any { it.problem.id == problemId })
        }
    }

    @Test
    fun abandoningASessionKeepsTheDraftButRecordsNothing() = runBlocking {
        val problemId = ProblemId("binary-search")
        openProfile().use { profile ->
            profile.study.open(problemId)
            val halfWritten = "def search(nums, target):\n    # thinking about it\n    pass\n"
            profile.study.run(problemId, halfWritten)

            profile.study.abandon(problemId)

            assertEquals(0, profile.reviews.reviewCount(), "abandoning must record no review")
            assertNull(profile.reviews.schedule(problemId))
            // But the code is still there.
            assertEquals(halfWritten, assertNotNull(profile.drafts.draft(problemId)).source)
        }
    }

    @Test
    fun resetToStarterRestoresTheStarterSource() = runBlocking {
        val problemId = ProblemId("two-sum")
        openProfile().use { profile ->
            val opened = assertNotNull(profile.study.open(problemId))
            profile.study.saveDraft(opened.draft.copy(source = "def two_sum(a, b):\n    return 'mess'\n"))

            val reset = assertNotNull(profile.study.resetToStarter(problemId))
            assertEquals(assertNotNull(profile.catalogue.problem(problemId)).starterSource, reset.source)
            assertTrue(reset.isPristine)
        }
    }

    @Test
    fun theRunnerReportsItsCapabilityHonestly() = runBlocking {
        openProfile().use { profile ->
            val status = profile.study.runnerStatus()
            assertTrue(status.available, status.unavailableReason)
            assertNotNull(status.pythonVersion)
            // A separate OS process with the user's own privileges — not a sandbox.
            assertEquals(
                dev.bee.beecode.python.RunnerCapability.SEPARATE_PROCESS,
                status.capability,
            )
        }
    }

    private fun openProfile(): BeeCodeProfile = BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = catalogue,
        runner = runner,
    )

    private companion object {
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
