package dev.bee.beecode.android

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.TestCaseResult
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunResult
import dev.bee.beecode.python.RunnerCapability
import dev.bee.beecode.python.RunnerProbe

/**
 * A [PythonRunner] that decides the outcome from the submitted source, without CPython.
 *
 * The local Compose tests are about the *UI's* reaction to a result — which ratings
 * appear, what the outcome banner says, whether the queue updates. Driving real Python
 * for that would mean either Chaquopy (unavailable off-device) or a `python3` child
 * process (a dependency of the host, and tens of seconds of interpreter warm-up per
 * test). Neither adds evidence about the UI.
 *
 * That real Python actually judges these Problems correctly is covered where it belongs:
 * `:content-tools` runs every reference solution and starter through a real interpreter,
 * and [AndroidStudyJourneyTest] runs the full journey against Chaquopy on a device.
 *
 * The rule is deliberately crude — a source containing [PASS_MARKER] passes — because a
 * fake that tried to *evaluate* Python would be a second implementation with its own
 * bugs, and a test whose fake is wrong is worse than no test.
 */
class ScriptedPythonRunner(
    private val pythonVersion: String = "3.12.0 (scripted)",
) : PythonRunner {

    override val runnerId: String = "scripted"

    /**
     * Reported as [RunnerCapability.IN_PROCESS] to match the real Android runner, so
     * any UI that renders the capability label is exercised against the same value a
     * learner sees.
     */
    override val capability: RunnerCapability = RunnerCapability.IN_PROCESS

    /** Every request this runner was asked to execute, for assertions about wiring. */
    val requests: MutableList<RunRequest> = mutableListOf()

    override suspend fun probe(): RunnerProbe = RunnerProbe(
        available = true,
        pythonVersion = pythonVersion,
        capability = capability,
        unavailableReason = null,
    )

    override suspend fun execute(request: RunRequest): RunResult {
        requests += request
        val passes = request.source.contains(PASS_MARKER)
        return RunResult(
            runId = request.runId,
            outcome = if (passes) ExecutionOutcome.PASSED else ExecutionOutcome.FAILED,
            testResults = request.tests.map { test ->
                TestCaseResult(
                    name = test.name,
                    passed = passes,
                    hidden = test.hidden,
                    expectedJson = if (test.hidden) null else test.expectedJson,
                    actualJson = when {
                        test.hidden -> null
                        passes -> test.expectedJson
                        else -> WRONG_ANSWER_JSON
                    },
                    message = if (passes) null else "expected ${test.expectedJson}, got $WRONG_ANSWER_JSON",
                    durationMillis = 1,
                )
            },
            output = "",
            outputTruncated = false,
            durationMillis = 1,
            runnerId = runnerId,
            pythonVersion = pythonVersion,
            diagnostic = null,
        )
    }

    companion object {
        /** A source containing this passes; anything else fails. */
        const val PASS_MARKER: String = "# scripted: pass"

        private const val WRONG_ANSWER_JSON = "[9, 9]"
    }
}
