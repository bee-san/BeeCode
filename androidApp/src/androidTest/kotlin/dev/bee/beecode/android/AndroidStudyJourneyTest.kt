package dev.bee.beecode.android

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.FinalizeResult
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.python.RunnerCapability
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The Test 1 journey, on a real Android device or emulator.
 *
 * This is the evidence the plan's highest-risk gate asks for: that embedded Python
 * actually starts on Android, that the shared harness produces the same typed
 * outcomes it does on desktop, and that the study loop survives a restart. It runs
 * against the real APK, the real bundled pack, real Chaquopy, and real SQLite.
 *
 * Each test uses its own database file so a failure cannot cascade.
 */
@RunWith(AndroidJUnit4::class)
class AndroidStudyJourneyTest {

    private lateinit var databaseFile: File
    private lateinit var catalogue: ProblemCatalogue
    private lateinit var runner: ChaquopyPythonRunner

    private val application: BeeCodeApplication
        get() = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as BeeCodeApplication

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The driver needs a writable temp directory to extract its native library.
        System.setProperty("java.io.tmpdir", context.cacheDir.absolutePath)
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)

        databaseFile = File(context.cacheDir, "beecode-test-${System.nanoTime()}.db")
        catalogue = application.catalogue
        runner = application.runner
    }

    @After
    fun tearDown() {
        databaseFile.delete()
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    @Test
    fun theBundledPackLoadsFromAssets() {
        // A client that cannot read its own pack has nothing to study. A lower bound
        // rather than an equality: the claim is that the pack reached the APK's assets,
        // and adding a Problem should not fail a packaging test.
        assertTrue("expected the full pack, got ${catalogue.size}", catalogue.size >= 12)
        assertNotNull(catalogue.problem(ProblemId("two-sum")))
        // And it must not carry the answers.
        val problem = catalogue.problem(ProblemId("two-sum"))!!
        assertFalse(problem.starterSource.contains("seen[value] = index"))
    }

    @Test
    fun embeddedPythonStartsAndReportsItsCapabilityHonestly() = runBlocking {
        val probe = runner.probe()
        assertTrue("Python did not start: ${probe.unavailableReason}", probe.available)
        assertNotNull(probe.pythonVersion)
        assertTrue(
            "expected Python 3.12, got ${probe.pythonVersion}",
            probe.pythonVersion!!.startsWith("3.12"),
        )
        // In-process, not a sandbox. The plan requires this be stated plainly
        // rather than overclaimed.
        assertEquals(RunnerCapability.IN_PROCESS, probe.capability)
    }

    @Test
    fun sqliteOpensAndMigratesOnAndroid() {
        // ADR 0003: the same JDBC driver as desktop, so there is no second
        // implementation that could disagree about review semantics.
        openProfile().use { profile ->
            assertEquals(0, profile.reviews.reviewCount())
            assertTrue(profile.study.queue().new.isNotEmpty())
        }
    }

    @Test
    fun theFailThenFixThenFinalizeThenRestartJourneyWorksOnAndroid() = runBlocking {
        val problemId = ProblemId("two-sum")
        // Named locally because this test's subject is that *this exact source* comes
        // back after a restart, which reads better than a constant at the assertion.
        val correct = TWO_SUM_SOLUTION

        val dueAt: kotlinx.datetime.Instant
        openProfile().use { profile ->
            val opened = requireNotNull(profile.study.open(problemId))
            assertTrue(opened.draft.isPristine)

            // One intentional failure: linear but returns the wrong pair.
            val wrong = """
                def two_sum(nums, target):
                    seen = {}
                    for index, value in enumerate(nums):
                        if target - value in seen:
                            return [index, index]
                        seen[value] = index
                    return []
            """.trimIndent()
            val failed = profile.study.run(problemId, wrong) as RunOutcome.Completed
            assertEquals(
                "expected a wrong answer, output was: ${failed.run.output}",
                ExecutionOutcome.FAILED,
                failed.run.outcome,
            )
            // Only Again is permitted for a failure.
            assertEquals(
                setOf(ReviewRating.AGAIN),
                profile.study.permittedRatings(problemId, failed.run.id),
            )

            // The correction.
            val passed = profile.study.run(problemId, correct) as RunOutcome.Completed
            assertEquals(
                "expected a pass, output was: ${passed.run.output}",
                ExecutionOutcome.PASSED,
                passed.run.outcome,
            )
            assertTrue(passed.run.testResults.all { it.passed })

            val result = profile.study.finalize(
                problemId, passed.run.id, ReviewRating.GOOD,
            ) as FinalizeResult.Finalized
            assertTrue(result.review.countsAsSolved)
            dueAt = requireNotNull(result.schedule).dueAt
        }

        // Restart: a new profile over the same file, as a relaunch would do.
        openProfile().use { profile ->
            val reopened = requireNotNull(profile.study.open(problemId))
            assertEquals("source must survive a restart", correct, reopened.draft.source)
            assertEquals(1, reopened.history.size)
            assertEquals(
                "the due date must survive a restart",
                dueAt,
                requireNotNull(reopened.schedule).dueAt,
            )
            assertEquals(1, profile.statistics().totalSolved)
            assertTrue(
                profile.achievement(dev.bee.beecode.app.Achievements.FIRST_SOLVE)!!.earned,
            )
        }
    }

    @Test
    fun aSyntaxErrorIsDistinctFromAWrongAnswerOnAndroid() = runBlocking {
        // Cross-platform conformance: the shared harness must classify identically
        // on both platforms, because the review policy depends on the distinction.
        openProfile().use { profile ->
            val problemId = ProblemId("two-sum")
            profile.study.open(problemId)
            val broken = "def two_sum(nums, target)\n    return []\n"
            val result = profile.study.run(problemId, broken) as RunOutcome.Completed
            assertEquals(ExecutionOutcome.SYNTAX_ERROR, result.run.outcome)
            assertTrue(result.run.testResults.isEmpty())
            assertNotNull(result.run.output)
        }
    }

    @Test
    fun aRuntimeErrorShowsOnlyLearnerFramesOnAndroid() = runBlocking {
        openProfile().use { profile ->
            val problemId = ProblemId("two-sum")
            profile.study.open(problemId)
            val result = profile.study.run(
                problemId,
                "def two_sum(nums, target):\n    return nums[999999]\n",
            ) as RunOutcome.Completed
            assertEquals(ExecutionOutcome.RUNTIME_ERROR, result.run.outcome)
            val failing = result.run.testResults.firstOrNull { !it.passed }
            val detail = failing?.message ?: ""
            assertTrue("expected an IndexError, got: $detail", detail.contains("IndexError"))
            assertFalse(
                "harness frames must stay hidden: $detail",
                detail.contains("beecode_harness"),
            )
        }
    }

    @Test
    fun aTimeoutReturnsPromptlyAndKeepsTheAppResponsive() = runBlocking {
        // The honest limitation, tested rather than assumed: Chaquopy cannot kill a
        // GIL-bound loop, so the deadline is enforced at the UI boundary. What must
        // hold is that the learner gets a typed TIMEOUT quickly and keeps their
        // source; the abandoned thread is the acknowledged cost.
        openProfile().use { profile ->
            val problemId = ProblemId("two-sum")
            profile.study.open(problemId)
            val infinite = "def two_sum(nums, target):\n    while True:\n        pass\n"

            // Probe first, explicitly. The runner pays CPython's cold start here, and
            // this test previously passed only because another test in the class
            // happened to run before it and warm the interpreter up. Run alone it
            // failed, reporting a timeout after 72s against a 5s limit — a real defect
            // for any learner whose first submission of a session loops forever, and
            // one this test was structurally unable to catch.
            profile.study.runnerStatus()

            val startedAt = System.nanoTime()
            val result = profile.study.run(problemId, infinite) as RunOutcome.Completed
            val elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000

            assertEquals(ExecutionOutcome.TIMEOUT, result.run.outcome)
            // The Problem's limit is 5s, plus the runner's small residual warm-up
            // allowance. 15s is chosen to be comfortably above what a loaded,
            // unaccelerated emulator needs while still failing the bug this caught:
            // a 60s allowance reported this timeout after 72s. Keep this bound tight
            // — its whole value is that a regression in the deadline path trips it.
            assertTrue("returned after ${elapsedMillis}ms", elapsedMillis < 15_000)
            // The learner's code is still there to fix.
            assertEquals(infinite, requireNotNull(profile.drafts.draft(problemId)).source)
        }
    }

    @Test
    fun hiddenTestsWithholdValuesOnAndroid() = runBlocking {
        openProfile().use { profile ->
            val problemId = ProblemId("contains-duplicate")
            profile.study.open(problemId)
            val source = """
                def contains_duplicate(nums):
                    return len(set(nums)) != len(nums)
            """.trimIndent()
            val result = profile.study.run(problemId, source) as RunOutcome.Completed
            assertEquals(
                "output was: ${result.run.output}",
                ExecutionOutcome.PASSED,
                result.run.outcome,
            )
            val hidden = result.run.testResults.filter { it.hidden }
            assertTrue("the pack should declare hidden tests", hidden.isNotEmpty())
            hidden.forEach {
                assertEquals(null, it.expectedJson)
                assertEquals(null, it.actualJson)
            }
        }
    }

    @Test
    fun learnerPythonHasNoNetworkPermission() = runBlocking {
        // The manifest declares no permissions, and learner code runs in this
        // process, so it inherits none. Proven by having Python try.
        openProfile().use { profile ->
            val problemId = ProblemId("two-sum")
            profile.study.open(problemId)
            val probe = """
                import socket
                def two_sum(nums, target):
                    s = socket.socket()
                    s.settimeout(2)
                    s.connect(("93.184.216.34", 80))
                    return [0, 1]
            """.trimIndent()
            val result = profile.study.run(problemId, probe) as RunOutcome.Completed
            // Without INTERNET the connect fails; the point is that it cannot
            // succeed, not which error it produces.
            assertFalse(
                "learner code reached the network: ${result.run.output}",
                result.run.outcome == ExecutionOutcome.PASSED,
            )
        }
    }

    @Test
    fun oneReviewSchedulesEveryTechniqueItRehearsesOnAndroid() = runBlocking {
        // The topic card is the SRS unit (ADR 0005), and it lives in a table added at
        // schema v4 and written inside the review's own transaction. Both of those are
        // claims about SQLite, so they belong here rather than only in a JVM test: this
        // is the one place the migration runs on real Android storage.
        //
        // Instrumented UI tests are skipped on emulators that refuse injected touch
        // input, so nothing that drives the screen can be relied on in CI. This test
        // takes the headless path deliberately, which is why it runs at all.
        val problemId = ProblemId("two-sum")
        val topics = requireNotNull(catalogue.problem(problemId)).topics
        assertTrue("two-sum must be tagged to rehearse anything", topics.isNotEmpty())

        openProfile().use { profile ->
            // `run` needs an open session — without it the outcome is `NoSession` and no
            // review is ever recorded, so the fan-out assertions below would be testing
            // an empty log.
            requireNotNull(profile.study.open(problemId))
            val passed = profile.study.run(problemId, TWO_SUM_SOLUTION) as RunOutcome.Completed
            assertEquals(
                "expected a pass, output was: ${passed.run.output}",
                ExecutionOutcome.PASSED,
                passed.run.outcome,
            )
            // Asserted rather than discarded: a rejected finalize records no review, and
            // "no topic schedules" would then be correct behaviour for the wrong reason.
            assertTrue(
                "the review was not finalized",
                profile.study.finalize(
                    problemId, passed.run.id, ReviewRating.GOOD,
                ) is FinalizeResult.Finalized,
            )

            // Every tagged technique, not just the first: fanning out to one of them
            // would be the silent half-failure this design is most exposed to.
            for (topic in topics) {
                val schedule = profile.reviews.topicSchedule(topic)
                assertNotNull("no schedule for '$topic' after reviewing $problemId", schedule)
                assertEquals("$topic must have one review", 1, schedule!!.reviewCount)
                assertTrue("$topic must have a real interval", schedule.intervalDays > 0.0)
            }
        }

        // And it survives a relaunch, which is the part a projection held only in memory
        // would pass everything else and still fail.
        openProfile().use { profile ->
            for (topic in topics) {
                assertNotNull(
                    "the schedule for '$topic' did not survive a restart",
                    profile.reviews.topicSchedule(topic),
                )
            }
            // Rebuilding from the log must agree with what the review wrote incrementally.
            // If it does not, the topic card is not the projection ADR 0005 says it is,
            // and a synced device would restore a profile that studies differently.
            // Compared through the profile's own check rather than by map equality,
            // because `version` and `updatedAt` are storage bookkeeping and a rebuild is
            // entitled to differ on them — the memory state is what must match.
            assertEquals(
                "rebuilding from history must reproduce the incremental state",
                emptyList<String>(),
                profile.verifyTopicScheduleIntegrity(),
            )
        }
    }

    private fun openProfile(): BeeCodeProfile = BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = catalogue,
        runner = runner,
    )

    private companion object {
        /** A passing Two Sum, for tests whose subject is what happens after the pass. */
        val TWO_SUM_SOLUTION = """
            def two_sum(nums, target):
                seen = {}
                for index, value in enumerate(nums):
                    if target - value in seen:
                        return [seen[target - value], index]
                    seen[value] = index
                return []
        """.trimIndent()
    }
}
