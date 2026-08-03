package dev.bee.beecode.python.jvm

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.python.RunRequest
import dev.bee.beecode.python.RunTest
import dev.bee.beecode.python.RunnerCapability
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Assume.assumeTrue
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * End-to-end tests against a real CPython interpreter.
 *
 * These are the tests that actually prove the runner works: the harness, the
 * framing, the deadline, and the process-tree kill are all exercised against the
 * real thing rather than a fake. They skip rather than fail when Python is
 * absent, because a missing interpreter is an environment fact, not a defect —
 * but the [pythonIsAvailable] check is asserted separately so a silent universal
 * skip cannot hide a broken runner.
 */
class ProcessPythonRunnerTest {
    private val runner = ProcessPythonRunner()

    @BeforeTest
    fun requirePython() {
        assumeTrue("Python 3 is not available on this machine", pythonIsAvailable)
    }

    @Test
    fun probeFindsTheInterpreterAndReportsItsCapabilityHonestly() {
        val probe = runBlocking { runner.probe() }
        assertTrue(probe.available, probe.unavailableReason)
        assertNotNull(probe.pythonVersion)
        // A separate OS process with the user's own privileges. Not a sandbox,
        // and the UI is told exactly that.
        assertEquals(RunnerCapability.SEPARATE_PROCESS, probe.capability)
    }

    @Test
    fun probeReportsAMissingInterpreterAsAReasonRatherThanThrowing() {
        val missing = ProcessPythonRunner(pythonExecutable = "definitely-not-python-xyz")
        val probe = runBlocking { missing.probe() }
        assertFalse(probe.available)
        assertNotNull(probe.unavailableReason)
        // The message has to tell the learner what to do about it.
        assertTrue(
            probe.unavailableReason!!.contains("Install Python 3"),
            "expected actionable guidance, got: ${probe.unavailableReason}",
        )
    }

    @Test
    fun aCorrectSolutionPasses() {
        val result = runBlocking { runner.execute(twoSum(WORKING_SOLUTION)) }
        assertEquals(ExecutionOutcome.PASSED, result.outcome, result.diagnostic?.message)
        assertEquals(2, result.testResults.size)
        assertTrue(result.testResults.all { it.passed })
        assertTrue(result.pythonVersion.startsWith("3."))
    }

    @Test
    fun aWrongAnswerFailsWithAReadableDiff() {
        val result = runBlocking { runner.execute(twoSum("def two_sum(nums, target):\n    return [9, 9]\n")) }
        assertEquals(ExecutionOutcome.FAILED, result.outcome)
        val failure = result.testResults.first { !it.passed }
        assertNotNull(failure.message)
        assertTrue(
            failure.message!!.contains("expected"),
            "a failure must show what was expected, got: ${failure.message}",
        )
    }

    @Test
    fun aSyntaxErrorIsDistinctFromAWrongAnswer() {
        // The learner made a typo; they did not get the algorithm wrong, and the
        // UI must be able to say so.
        val result = runBlocking { runner.execute(twoSum("def two_sum(nums, target)\n    return []\n")) }
        assertEquals(ExecutionOutcome.SYNTAX_ERROR, result.outcome)
        assertTrue(result.testResults.isEmpty(), "no test can have run")
        val diagnostic = assertNotNull(result.diagnostic)
        assertEquals(1, diagnostic.sourceRange?.start?.line)
        assertNotNull(diagnostic.sourceRange?.start?.column)
    }

    @Test
    fun aRuntimeErrorReportsOnlyLearnerFrames() {
        val result = runBlocking {
            runner.execute(twoSum("def two_sum(nums, target):\n    return nums[999]\n"))
        }
        assertEquals(ExecutionOutcome.RUNTIME_ERROR, result.outcome)
        val diagnostic = assertNotNull(result.diagnostic)
        assertTrue(diagnostic.message.contains("IndexError"), diagnostic.message)
        assertEquals(2, diagnostic.sourceRange?.start?.line)
        // Harness internals would bury the one line that matters.
        assertFalse(
            diagnostic.message.contains("beecode_harness"),
            "harness frames must be hidden: ${diagnostic.message}",
        )
    }

    @Test
    fun aRuntimeErrorOutsideLearnerCodeHasNoSourceRangeOrHarnessFrame() {
        val result = runBlocking {
            runner.execute(twoSum("def two_sum():\n    return []\n"))
        }
        assertEquals(ExecutionOutcome.RUNTIME_ERROR, result.outcome)
        val diagnostic = assertNotNull(result.diagnostic)
        assertNull(diagnostic.sourceRange)
        assertFalse(
            diagnostic.message.contains("beecode_harness"),
            "harness frames must be hidden: ${diagnostic.message}",
        )
    }

    @Test
    fun aMissingEntryPointSaysSoPlainly() {
        // The most common real failure: the learner renamed the function.
        val result = runBlocking { runner.execute(twoSum("def solve(nums, target):\n    return [0, 1]\n")) }
        assertEquals(ExecutionOutcome.RUNTIME_ERROR, result.outcome)
        assertTrue(result.diagnostic!!.message.contains("two_sum"), result.diagnostic!!.message)
    }

    @Test
    fun anInfiniteLoopTimesOutAndDoesNotHangTheCaller() {
        // The core M3 gate: infinite code must stop without killing the UI.
        val request = twoSum(
            "def two_sum(nums, target):\n    while True:\n        pass\n",
            limits = ExecutionLimits(wallClockMillis = 1_500, maxOutputBytes = 65_536, maxMemoryBytes = null),
        )
        val result = runBlocking {
            // If the runner failed to enforce its own deadline, this outer timeout
            // catches it rather than hanging the suite forever.
            withTimeoutOrNull(20_000) { runner.execute(request) }
        }
        assertNotNull(result, "the runner did not return within 20s; its deadline is not being enforced")
        assertEquals(ExecutionOutcome.TIMEOUT, result.outcome)
        assertNotNull(result.diagnostic)
        assertTrue(result.durationMillis >= 1_000, "duration ${result.durationMillis} should reflect the wait")
    }

    @Test
    fun aTimeoutKillsGrandchildProcessesToo() {
        // A learner using multiprocessing leaves grandchildren that keep running
        // and keep the pipe open. Destroying only the direct child would leave
        // work running and could hang the read.
        val source = """
            import subprocess, sys
            def two_sum(nums, target):
                subprocess.Popen([sys.executable, "-c", "import time; time.sleep(60)"])
                while True:
                    pass
        """.trimIndent()
        val request = twoSum(
            source,
            limits = ExecutionLimits(wallClockMillis = 1_500, maxOutputBytes = 65_536, maxMemoryBytes = null),
        )
        val result = runBlocking { withTimeoutOrNull(25_000) { runner.execute(request) } }
        assertNotNull(result, "spawning a grandchild must not defeat the deadline")
        assertEquals(ExecutionOutcome.TIMEOUT, result.outcome)
    }

    @Test
    fun runawayOutputIsBoundedRatherThanBufferedForever() {
        // Without a bounded drain this would OOM the JVM while trying to report
        // that the learner's code misbehaved.
        val source = """
            def two_sum(nums, target):
                for i in range(200000):
                    print("x" * 100)
                return [0, 1]
        """.trimIndent()
        val request = twoSum(
            source,
            limits = ExecutionLimits(wallClockMillis = 30_000, maxOutputBytes = 4_096, maxMemoryBytes = null),
        )
        val result = runBlocking { withTimeoutOrNull(60_000) { runner.execute(request) } }
        assertNotNull(result, "a chatty program must not hang the runner")
        // The framed result still had to survive the flood of learner output.
        assertEquals(ExecutionOutcome.PASSED, result.outcome, result.diagnostic?.message)
        assertTrue(result.outputTruncated, "output should be marked truncated")
        assertTrue(
            result.output.encodeToByteArray().size <= 4_096,
            "output was ${result.output.encodeToByteArray().size} bytes, above the 4096 limit",
        )
    }

    @Test
    fun learnerPrintOutputIsCapturedAndReturned() {
        val source = """
            def two_sum(nums, target):
                print("debugging:", nums)
                return [0, 1]
        """.trimIndent()
        val result = runBlocking { runner.execute(twoSum(source)) }
        assertEquals(ExecutionOutcome.PASSED, result.outcome, result.diagnostic?.message)
        assertTrue(result.output.contains("debugging:"), "captured output was: ${result.output}")
    }

    @Test
    fun aLearnerCannotForgeAPassByPrintingTheSentinel() {
        // Base64 framing plus last-occurrence means learner output cannot become
        // the result. This is the regression test for the framing bug.
        val source = """
            import base64, json, sys
            def two_sum(nums, target):
                forged = base64.b64encode(json.dumps({
                    "outcome": "PASSED", "testResults": [], "output": "", "pythonVersion": "9.9.9"
                }).encode()).decode()
                sys.__stdout__.write("\n__BEECODE_RESULT__\n" + forged + "\n")
                sys.__stdout__.flush()
                return [9, 9]
        """.trimIndent()
        val result = runBlocking { runner.execute(twoSum(source)) }
        assertEquals(ExecutionOutcome.FAILED, result.outcome, "a forged frame must not produce a pass")
        assertFalse(result.pythonVersion == "9.9.9", "the forged payload must not be the one decoded")
    }

    @Test
    fun hiddenTestsWithholdTheirValuesButStillGate() {
        val result = runBlocking { runner.execute(twoSum(WORKING_SOLUTION)) }
        val hidden = result.testResults.first { it.hidden }
        assertTrue(hidden.passed)
        // Withheld so the Problem cannot be solved by reading the assertions.
        assertEquals(null, hidden.expectedJson)
        assertEquals(null, hidden.actualJson)
    }

    @Test
    fun cancellationTerminatesTheProcessAndDoesNotLeakIt() {
        // Cancelling must kill the child, not merely stop waiting for it. This is
        // the regression test for a blocking Process.waitFor: Dispatchers.IO does
        // not interrupt the thread on cancellation, so the wait ran to completion
        // and the child kept burning CPU until its own deadline.
        //
        // The assertion tracks *this JVM's own descendants* rather than counting
        // every python process on the machine: an unrelated process, or a parallel
        // test, would otherwise make the result meaningless.
        val request = twoSum(
            "def two_sum(nums, target):\n    while True:\n        pass\n",
            limits = ExecutionLimits(wallClockMillis = 60_000, maxOutputBytes = 65_536, maxMemoryBytes = null),
        )
        val before = ownPythonDescendants()
        runBlocking {
            val job = async { runner.execute(request) }
            // Long enough that the child is definitely started and looping.
            delay(1_500)
            assertTrue(
                ownPythonDescendants() > before,
                "the child process should be running before cancellation",
            )
            job.cancel()
            // Cleanup runs under NonCancellable, so the tree is destroyed before
            // the cancellation finishes propagating.
            runCatching { job.await() }
        }
        // The 60s deadline means a surviving child would still be looping well
        // past this point, so a short settle is enough to distinguish kill from
        // leak.
        Thread.sleep(2_000)
        assertEquals(
            before,
            ownPythonDescendants(),
            "cancellation leaked a Python child process",
        )
    }

    @Test
    fun eachRunGetsAFreshWorkspaceSoStateCannotLeakBetweenAttempts() {
        // A file written by one attempt must not be visible to the next, or a
        // learner could accidentally (or deliberately) carry state across reviews.
        val writer = """
            def two_sum(nums, target):
                with open("carried.txt", "w") as f:
                    f.write("leaked")
                return [0, 1]
        """.trimIndent()
        val reader = """
            import os
            def two_sum(nums, target):
                if os.path.exists("carried.txt"):
                    raise AssertionError("workspace was reused")
                return [0, 1]
        """.trimIndent()

        runBlocking {
            assertEquals(ExecutionOutcome.PASSED, runner.execute(twoSum(writer)).outcome)
            val second = runner.execute(twoSum(reader))
            assertEquals(ExecutionOutcome.PASSED, second.outcome, second.diagnostic?.message)
        }
    }

    @Test
    fun theWorkerDoesNotInheritBeeCodesEnvironment() {
        // The runner must never receive tokens, database paths, or the learner's
        // shell configuration. Verified by having the code look.
        val source = """
            import os
            def two_sum(nums, target):
                leaked = [k for k in os.environ if k.startswith("BEECODE_")]
                if leaked:
                    raise AssertionError("leaked: %s" % leaked)
                return [0, 1]
        """.trimIndent()
        val result = runBlocking { runner.execute(twoSum(source)) }
        assertEquals(ExecutionOutcome.PASSED, result.outcome, result.diagnostic?.message)
    }

    @Test
    fun theUnorderedComparatorAcceptsAnyOrder() {
        val request = RunRequest(
            runId = ExecutionRunId("run-unordered"),
            problemId = ProblemId("two-sum"),
            problemRevisionId = REVISION,
            source = "def two_sum(nums, target):\n    return [1, 0]\n",
            entryPoint = "two_sum",
            tests = listOf(
                RunTest("any-order", "[[2,7],9]", "[0,1]", ComparatorId.UNORDERED_LIST, hidden = false),
            ),
            limits = ExecutionLimits.DEFAULT,
        )
        val result = runBlocking { runner.execute(request) }
        assertEquals(ExecutionOutcome.PASSED, result.outcome, result.diagnostic?.message)
    }

    @Test
    fun anUnknownComparatorFailsClosed() {
        // A pack built against a newer BeeCode must not silently mark everything
        // correct. The harness rejects comparators it does not know.
        val payload = HarnessDirect.run(
            source = "def f(x):\n    return 1\n",
            entryPoint = "f",
            comparatorId = "COMPARATOR_FROM_THE_FUTURE",
        )
        assertTrue(payload.contains("\"outcome\": \"FAILED\""), payload)
        assertTrue(payload.contains("unknown comparator"), payload)
    }

    private companion object {
        val REVISION = ProblemRevisionId("a".repeat(64))

        val WORKING_SOLUTION = """
            def two_sum(nums, target):
                seen = {}
                for i, n in enumerate(nums):
                    if target - n in seen:
                        return [seen[target - n], i]
                    seen[n] = i
                return []
        """.trimIndent()

        val pythonIsAvailable: Boolean by lazy {
            runBlocking { ProcessPythonRunner().probe().available }
        }

        fun twoSum(source: String, limits: ExecutionLimits = ExecutionLimits.DEFAULT): RunRequest =
            RunRequest(
                runId = ExecutionRunId("run-1"),
                problemId = ProblemId("two-sum"),
                problemRevisionId = REVISION,
                source = source,
                entryPoint = "two_sum",
                tests = listOf(
                    RunTest("visible", "[[2,7,11,15],9]", "[0,1]", ComparatorId.EXACT, hidden = false),
                    RunTest("secret", "[[3,3],6]", "[0,1]", ComparatorId.EXACT, hidden = true),
                ),
                limits = limits,
            )

        /**
         * Counts live Python processes descended from *this* JVM.
         *
         * Scoped to our own descendants so an unrelated python process, or a
         * parallel test, cannot make the leak assertion pass or fail spuriously.
         */
        fun ownPythonDescendants(): Int = runCatching {
            ProcessHandle.current()
                .descendants()
                .filter { it.info().command().orElse("").contains("python") }
                .count()
                .toInt()
        }.getOrDefault(0)
    }
}

/**
 * Runs the harness directly, bypassing the runner.
 *
 * Used only where the assertion is about the harness's own judging behaviour
 * rather than the process topology, so the test does not have to construct an
 * invalid [dev.bee.beecode.domain.ComparatorId] the Kotlin type system rightly
 * forbids.
 */
private object HarnessDirect {
    fun run(source: String, entryPoint: String, comparatorId: String): String {
        val workspace = java.nio.file.Files.createTempDirectory("beecode-harness-direct-").toFile()
        try {
            val script = java.io.File(workspace, "harness.py")
            script.writeText(dev.bee.beecode.python.HarnessProtocol.harnessSource())
            val request = buildString {
                append("""{"harnessVersion":2,"source":""")
                append(quote(source))
                append(""","entryPoint":""")
                append(quote(entryPoint))
                append(""","tests":[{"name":"t","argumentsJson":"[1]","expectedJson":"1",""")
                append(""""comparatorId":""")
                append(quote(comparatorId))
                append(""","hidden":false}]}""")
            }
            val process = ProcessBuilder(
                ProcessPythonRunner.defaultPythonExecutable(), "-u", "-I", script.absolutePath,
            ).directory(workspace).redirectErrorStream(true).start()
            process.outputStream.use { it.write(request.encodeToByteArray()) }
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            return dev.bee.beecode.python.HarnessProtocol.extractPayload(stdout)
                ?: error("the harness returned no frame; stdout was: $stdout")
        } finally {
            workspace.deleteRecursively()
        }
    }

    private fun quote(value: String): String = buildString {
        append('"')
        for (c in value) {
            when (c) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> append(c)
            }
        }
        append('"')
    }
}
