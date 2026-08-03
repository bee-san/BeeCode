package dev.bee.beecode.app

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe
import java.io.File
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull

class StudyServiceConcurrencyTest {
    @Test
    fun aLateRunCannotOverwriteAReplacementSession() = runBlocking {
        val problemId = ProblemId("two-sum")
        val runner = DelayedPassingRunner()
        val catalogue = ProblemCatalogue.fromSourceDirectory(File(repoRoot(), "content/packs/core"))

        BeeCodeProfile.inMemory(catalogue = catalogue, runner = runner).use { profile ->
            val first = assertNotNull(profile.study.open(problemId))
            val staleRun = async {
                profile.study.run(problemId, first.draft.source)
            }
            runner.awaitStarted()

            profile.study.abandon(problemId)
            val replacement = assertNotNull(profile.study.open(problemId))
            assertNotEquals(first.session.id, replacement.session.id)

            runner.release()
            assertIs<RunOutcome.NoSession>(staleRun.await())

            val currentRun = assertIs<RunOutcome.Completed>(
                profile.study.run(problemId, replacement.draft.source),
            )
            assertEquals(replacement.session.id, currentRun.session.id)
            assertEquals(1, currentRun.session.runs.size)
        }
    }

    private class DelayedPassingRunner : PythonRunner {
        private val started = CompletableDeferred<Unit>()
        private val released = CompletableDeferred<Unit>()

        override val runnerId = "delayed-passing"
        override val capability = RunnerCapability.SEPARATE_PROCESS

        suspend fun awaitStarted() = started.await()

        fun release() {
            released.complete(Unit)
        }

        override suspend fun probe() = RunnerProbe(
            available = true,
            pythonVersion = "3.12.0 (test)",
            capability = capability,
            unavailableReason = null,
        )

        override suspend fun execute(request: RunRequest): RunResult {
            started.complete(Unit)
            released.await()
            return RunResult(
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
                pythonVersion = "3.12.0 (test)",
                diagnostic = null,
            )
        }
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
