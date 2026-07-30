package dev.bee.beecode.app

import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/**
 * Leaving a Problem keeps what the learner typed.
 *
 * This exists because it did not. Both clients persisted with
 * `drafts.draft(id)?.let { save(it.copy(source)) }`, and [dev.bee.beecode.persistence.DraftRepository.draft]
 * returns null until a draft has actually been written. [StudyService.open] only ever
 * *constructs* one, so on a first visit that `?.let` short-circuited and everything the
 * learner had typed was discarded on Back — silently, with no error, for anyone who had
 * not pressed Run first. Running happened to save as a side effect, which is why the
 * journey tests never saw it: every one of them runs code before checking the draft.
 *
 * So these deliberately never run anything. That absence is the test.
 *
 * No Python here: the point is the persistence boundary, and requiring an interpreter
 * would let the regression back in on any machine where the journey suite skips.
 */
class DraftPersistenceTest {

    private lateinit var databaseFile: File
    private lateinit var catalogue: ProblemCatalogue

    @BeforeTest
    fun setUp() {
        databaseFile = kotlin.io.path.createTempFile("beecode-drafts-", ".db").toFile()
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
    fun typingOnAFirstVisitAndLeavingWithoutRunningKeepsTheSource() {
        val problemId = ProblemId("two-sum")
        val typed = "def two_sum(nums, target):\n    # I had an idea and then my bus arrived\n    pass\n"

        openProfile().use { profile ->
            profile.study.open(problemId)
            // Nothing has been run, so nothing has written a draft row yet.
            assertNull(
                profile.drafts.draft(problemId),
                "open() must not write, or this test would not be testing anything",
            )

            profile.study.saveSource(problemId, typed)
        }

        // A brand-new profile over the same file: what a relaunch does.
        openProfile().use { profile ->
            assertEquals(
                typed,
                assertNotNull(profile.study.open(problemId)).draft.source,
                "source typed on a first visit must survive leaving and relaunching",
            )
        }
    }

    @Test
    fun savingSourceKeepsTheStarterBaselineSoResetStillWorks() {
        val problemId = ProblemId("two-sum")
        val starter = assertNotNull(catalogue.problem(problemId)).starterSource

        openProfile().use { profile ->
            profile.study.open(problemId)
            profile.study.saveSource(problemId, "def two_sum(nums, target):\n    return []\n")

            // saveSource builds on loadOrStart, so the baseline the reset button needs
            // is established rather than lost to a draft row invented from nothing.
            val reset = assertNotNull(profile.study.resetToStarter(problemId))
            assertEquals(starter, reset.source)
        }
    }

    @Test
    fun savingSourceForAnUnknownProblemDoesNothing() {
        openProfile().use { profile ->
            // No catalogue entry means no starter to build a draft around. Returning
            // null beats inventing a row keyed to a Problem that does not exist.
            assertNull(profile.study.saveSource(ProblemId("no-such-problem"), "whatever"))
        }
    }

    @Test
    fun aSecondVisitOverwritesRatherThanAppendsOrDrops() {
        val problemId = ProblemId("contains-duplicate")

        openProfile().use { profile ->
            profile.study.open(problemId)
            profile.study.saveSource(problemId, "first")
        }
        openProfile().use { profile ->
            profile.study.open(problemId)
            profile.study.saveSource(problemId, "second")
        }
        openProfile().use { profile ->
            assertEquals("second", assertNotNull(profile.drafts.draft(problemId)).source)
        }
    }

    private fun openProfile(): BeeCodeProfile = BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = catalogue,
        runner = InertRunner(),
    )

    /**
     * A runner that is never invoked.
     *
     * Present only because [BeeCodeProfile.open] requires one. If a test in this class
     * ever starts depending on it, that test has stopped being about persistence.
     */
    private class InertRunner : PythonRunner {
        override val runnerId = "inert"
        override val capability = RunnerCapability.SEPARATE_PROCESS

        override suspend fun probe() = RunnerProbe(
            available = false,
            pythonVersion = null,
            capability = capability,
            unavailableReason = "this test never runs code",
        )

        override suspend fun execute(request: RunRequest): RunResult = error(
            "DraftPersistenceTest must not run code: not running is the condition under test",
        )
    }

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
