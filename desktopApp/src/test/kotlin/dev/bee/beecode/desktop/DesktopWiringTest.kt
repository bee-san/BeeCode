package dev.bee.beecode.desktop

import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.FinalizeResult
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.app.RunOutcome
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.jvm.ProcessPythonRunner
import kotlinx.coroutines.runBlocking
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
 * The desktop client's composition root, tested headlessly.
 *
 * The UI itself is Compose and needs a display, but everything that can actually be
 * wired up wrongly is testable without one: the pack must be readable from the
 * packaged resource, the profile must open in a real directory, and the study loop
 * must work end to end through the desktop's own runner.
 *
 * This is the desktop half of the Test 1 gate. The Android half is
 * `AndroidStudyJourneyTest`, and the two assert the same outcomes deliberately —
 * that is how cross-platform conformance is checked rather than assumed.
 */
class DesktopWiringTest {

    private lateinit var databaseFile: File
    private val runner = ProcessPythonRunner()

    @BeforeTest
    fun setUp() {
        assumeTrue("Python 3 is unavailable", runBlocking { runner.probe().available })
        databaseFile = kotlin.io.path.createTempFile("beecode-desktop-", ".db").toFile()
        databaseFile.delete()
    }

    @AfterTest
    fun tearDown() {
        databaseFile.delete()
        File(databaseFile.absolutePath + "-wal").delete()
        File(databaseFile.absolutePath + "-shm").delete()
    }

    @Test
    fun theProblemPackLoadsFromThePackagedResource() {
        // The production path. If the build task did not put the pack on the runtime
        // classpath, the app launches with nothing to study — so this failing is far
        // better than shipping it.
        val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)
        assertEquals(12, catalogue.size)
        assertNotNull(catalogue.problem(ProblemId("two-sum")))
        assertTrue(catalogue.topics().isNotEmpty())
    }

    @Test
    fun theShippedPackContainsNoReferenceSolutions() {
        val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)
        for (problem in catalogue.allProblems()) {
            // The starter must leave the work to the learner.
            assertTrue(
                problem.starterSource.lines().any { it.trim() == "pass" },
                "${problem.id}: the shipped starter should not be a solution",
            )
        }
    }

    @Test
    fun theProfileDirectoryFollowsPlatformConvention() {
        // A learner should be able to find and back up their own profile, so this is
        // a real location rather than a dotfile dropped in $HOME.
        val directory = profileDirectory()
        assertTrue(directory.isAbsolute, "profile path must be absolute: $directory")
        val path = directory.path
        val os = System.getProperty("os.name").orEmpty().lowercase()
        when {
            os.contains("win") -> assertTrue(path.contains("BeeCode"))
            os.contains("mac") -> assertTrue(path.contains("Application Support"))
            else -> assertTrue(
                path.contains("beecode"),
                "expected an XDG-style path, got $path",
            )
        }
    }

    @Test
    fun theDesktopRunnerReportsItsCapabilityHonestly() = runBlocking {
        val probe = runner.probe()
        assertTrue(probe.available, probe.unavailableReason)
        // A separate killable process, but with the user's own privileges. Stronger
        // than Android's in-process runner, and still not a sandbox.
        assertEquals(RunnerCapability.SEPARATE_PROCESS, probe.capability)
    }

    @Test
    fun theFullJourneyWorksThroughTheDesktopComposition() = runBlocking {
        val problemId = ProblemId("two-sum")
        val correct = """
            def two_sum(nums, target):
                seen = {}
                for index, value in enumerate(nums):
                    if target - value in seen:
                        return [seen[target - value], index]
                    seen[value] = index
                return []
        """.trimIndent()

        val dueAt: kotlinx.datetime.Instant
        openProfile().use { profile ->
            val opened = assertNotNull(profile.study.open(problemId))
            assertTrue(opened.draft.isPristine)

            // Linear but wrong, so it fails rather than exceeding the deadline on
            // two-sum's 20,000-element case.
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
            assertEquals(
                setOf(ReviewRating.AGAIN),
                profile.study.permittedRatings(problemId, failed.run.id),
            )

            val passed = assertIs<RunOutcome.Completed>(profile.study.run(problemId, correct))
            assertEquals(ExecutionOutcome.PASSED, passed.run.outcome, passed.run.output)

            val result = assertIs<FinalizeResult.Finalized>(
                profile.study.finalize(problemId, passed.run.id, ReviewRating.GOOD),
            )
            assertTrue(result.review.countsAsSolved)
            dueAt = assertNotNull(result.schedule).dueAt
        }

        // Relaunch over the same profile file.
        openProfile().use { profile ->
            val reopened = assertNotNull(profile.study.open(problemId))
            assertEquals(correct, reopened.draft.source, "source must survive a restart")
            assertEquals(1, reopened.history.size)
            assertEquals(dueAt, assertNotNull(reopened.schedule).dueAt)
            assertEquals(1, profile.statistics().totalSolved)
            assertTrue(
                assertNotNull(
                    profile.achievement(dev.bee.beecode.app.Achievements.FIRST_SOLVE),
                ).earned,
            )
            assertTrue(profile.verifyScheduleIntegrity().isEmpty())
        }
    }

    @Test
    fun aChosenPythonExecutableIsHonoured() {
        // The Settings screen lets a learner point BeeCode at a specific interpreter.
        // Storing it is what makes the desktop usable where python3 is not on PATH.
        openProfile().use { profile ->
            val now = kotlinx.datetime.Clock.System.now()
            profile.settings.setPythonExecutable("/usr/bin/python3", now)
            assertEquals("/usr/bin/python3", profile.settings.pythonExecutable())
            profile.settings.setPythonExecutable(null, now)
            assertEquals(null, profile.settings.pythonExecutable())
        }
    }

    private fun openProfile(): BeeCodeProfile = BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE),
        runner = runner,
    )
}
