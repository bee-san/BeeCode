package dev.bee.beecode.python

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import java.util.Base64
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun request(): RunRequest = RunRequest(
    runId = ExecutionRunId("run-1"),
    problemId = ProblemId("two-sum"),
    problemRevisionId = ProblemRevisionId("a".repeat(64)),
    source = "def two_sum(nums, target):\n    return [0, 1]\n",
    entryPoint = "two_sum",
    tests = listOf(
        RunTest("example", "[[2,7],9]", "[0,1]", ComparatorId.EXACT, hidden = false),
    ),
    limits = ExecutionLimits.DEFAULT,
)

private fun frame(json: String): String =
    "\n${HarnessProtocol.RESULT_SENTINEL}\n" +
        Base64.getEncoder().encodeToString(json.encodeToByteArray()) + "\n"

class HarnessProtocolTest {
    @Test
    fun theHarnessScriptShipsOnTheClasspath() {
        // A missing harness is a packaging failure that would otherwise surface as
        // every review mysteriously failing.
        val source = HarnessProtocol.harnessSource()
        assertTrue(source.contains("def main()"), "harness must define main()")
        assertTrue(
            source.contains(HarnessProtocol.RESULT_SENTINEL),
            "harness and Kotlin must agree on the sentinel",
        )
    }

    @Test
    fun aRequestCarriesOnlyWhatJudgingNeeds() {
        // Privacy boundary: the runner must not receive the statement, the
        // explanation, the learner's history, or any credential.
        val encoded = HarnessProtocol.encodeRequest(request())
        assertTrue(encoded.contains("two_sum"))
        assertTrue(encoded.contains("harnessVersion"))
        assertFalse(encoded.contains("statement"), "the statement must not reach the runner")
        assertFalse(encoded.contains("explanation"), "the explanation must not reach the runner")
    }

    @Test
    fun aFramedPayloadRoundTrips() {
        val payload = """{"outcome":"PASSED"}"""
        assertEquals(payload, HarnessProtocol.extractPayload(frame(payload)))
    }

    @Test
    fun learnerOutputBeforeTheFrameIsIgnored() {
        val payload = """{"outcome":"FAILED"}"""
        val stdout = "some print output\nmore output\n" + frame(payload)
        assertEquals(payload, HarnessProtocol.extractPayload(stdout))
    }

    @Test
    fun aLearnerEchoingTheSentinelCannotBreakDecoding() {
        // The bug base64 framing exists to prevent: the learner's echoed sentinel
        // is quoted inside the real response's output field, which sits *after*
        // the real sentinel in the raw stream.
        val payload = """{"outcome":"FAILED","output":"${HarnessProtocol.RESULT_SENTINEL}\n"}"""
        val stdout = "${HarnessProtocol.RESULT_SENTINEL}\nfake\n" + frame(payload)
        assertEquals(payload, HarnessProtocol.extractPayload(stdout))
    }

    @Test
    fun aForgedFrameWrittenFirstLosesToTheRealOne() {
        // A learner can reach past the capture to sys.__stdout__ and write a
        // syntactically perfect frame. The harness writes last, so the
        // last-occurrence rule still yields the truth.
        val forged = """{"outcome":"PASSED","pythonVersion":"9.9.9"}"""
        val real = """{"outcome":"FAILED","pythonVersion":"3.12.0"}"""
        val stdout = frame(forged) + frame(real)

        val extracted = assertNotNull(HarnessProtocol.extractPayload(stdout))
        val result = HarnessProtocol.decodeResult(extracted, request(), 10, "test", 65_536)
        assertEquals(ExecutionOutcome.FAILED, result.outcome)
        assertEquals("3.12.0", result.pythonVersion)
    }

    @Test
    fun missingOrUndecodableFramesReturnNull() {
        assertNull(HarnessProtocol.extractPayload("just learner output\n"))
        assertNull(HarnessProtocol.extractPayload(""))
        assertNull(HarnessProtocol.extractPayload("\n${HarnessProtocol.RESULT_SENTINEL}\n"))
        assertNull(
            HarnessProtocol.extractPayload("\n${HarnessProtocol.RESULT_SENTINEL}\nnot base64 !!!\n"),
            "an undecodable frame must be reported as absent, not guessed at",
        )
    }

    @Test
    fun anUnparseablePayloadBecomesAWorkerFailure() {
        // If BeeCode cannot understand its own harness, that is BeeCode's fault
        // and must never be charged to the learner as a wrong answer.
        val result = HarnessProtocol.decodeResult("{not json", request(), 10, "test", 65_536)
        assertEquals(ExecutionOutcome.WORKER_FAILURE, result.outcome)
        assertNotNull(result.diagnostic)
    }

    @Test
    fun anUnknownOutcomeBecomesAWorkerFailure() {
        val result = HarnessProtocol.decodeResult(
            """{"outcome":"SOMETHING_NEW"}""", request(), 10, "test", 65_536,
        )
        assertEquals(ExecutionOutcome.WORKER_FAILURE, result.outcome)
    }

    @Test
    fun aHarnessErrorIsAWorkerFailureNotALearnerMistake() {
        val result = HarnessProtocol.decodeResult(
            """{"outcome":"HARNESS_ERROR","diagnostic":"boom"}""", request(), 10, "test", 65_536,
        )
        assertEquals(ExecutionOutcome.WORKER_FAILURE, result.outcome)
        assertEquals("boom", result.diagnostic)
    }

    @Test
    fun everyHarnessOutcomeMapsToADomainOutcome() {
        val mapping = mapOf(
            "PASSED" to ExecutionOutcome.PASSED,
            "FAILED" to ExecutionOutcome.FAILED,
            "SYNTAX_ERROR" to ExecutionOutcome.SYNTAX_ERROR,
            "RUNTIME_ERROR" to ExecutionOutcome.RUNTIME_ERROR,
            "HARNESS_ERROR" to ExecutionOutcome.WORKER_FAILURE,
        )
        for ((raw, expected) in mapping) {
            val result = HarnessProtocol.decodeResult(
                """{"outcome":"$raw"}""", request(), 5, "test", 65_536,
            )
            assertEquals(expected, result.outcome, "harness outcome $raw")
        }
    }

    @Test
    fun testResultsDecodeIncludingHiddenWithheldValues() {
        val payload = """
            {"outcome":"FAILED","pythonVersion":"3.12.0","testResults":[
              {"name":"visible","passed":true,"hidden":false,"expectedJson":"[0,1]","actualJson":"[0,1]","durationMillis":3},
              {"name":"secret","passed":false,"hidden":true,"expectedJson":null,"actualJson":null,"message":null,"durationMillis":4}
            ]}
        """.trimIndent()
        val result = HarnessProtocol.decodeResult(payload, request(), 20, "test", 65_536)

        assertEquals(2, result.testResults.size)
        val secret = result.testResults[1]
        assertTrue(secret.hidden)
        assertFalse(secret.passed)
        // A hidden test reports pass/fail but withholds values, so a Problem
        // cannot be solved by reading the assertions.
        assertNull(secret.expectedJson)
        assertNull(secret.actualJson)
    }

    @Test
    fun outputIsTruncatedFromTheTail() {
        // When a program prints in a loop and then crashes, the informative part
        // is what it said last.
        val (kept, truncated) = HarnessProtocol.truncateOutput("abcdefghij", maxBytes = 4)
        assertTrue(truncated)
        assertEquals("ghij", kept)
    }

    @Test
    fun outputWithinTheLimitIsNotTruncated() {
        val (kept, truncated) = HarnessProtocol.truncateOutput("hello", maxBytes = 64)
        assertFalse(truncated)
        assertEquals("hello", kept)
    }

    @Test
    fun truncatingMultiByteTextDoesNotThrow() {
        // Slicing UTF-8 mid-character must degrade to a replacement character
        // rather than crashing the whole run.
        val (kept, truncated) = HarnessProtocol.truncateOutput("日本語テキスト", maxBytes = 7)
        assertTrue(truncated)
        assertTrue(kept.isNotEmpty())
    }

    @Test
    fun timeoutAndCancellationCarryNoTestResults() {
        val timedOut = HarnessProtocol.timeout(request(), 5_000, "test")
        assertEquals(ExecutionOutcome.TIMEOUT, timedOut.outcome)
        assertTrue(timedOut.testResults.isEmpty())
        assertNotNull(timedOut.diagnostic, "a timeout must explain itself to the learner")

        val cancelled = HarnessProtocol.cancelled(request(), 120, "test")
        assertEquals(ExecutionOutcome.CANCELLED, cancelled.outcome)
        // Cancellation is the learner's own choice, so it needs no explanation.
        assertNull(cancelled.diagnostic)
    }

    @Test
    fun aProbeMustBeInternallyConsistent() {
        // An "available" runner with no version, or an unavailable one with no
        // reason, would leave the UI unable to say anything useful.
        RunnerProbe(true, "3.12.0", RunnerCapability.SEPARATE_PROCESS, null)
        RunnerProbe(false, null, RunnerCapability.SEPARATE_PROCESS, "python3 was not found")
        runCatching { RunnerProbe(true, null, RunnerCapability.SEPARATE_PROCESS, null) }
            .onSuccess { error("an available probe without a version must be rejected") }
        runCatching { RunnerProbe(false, null, RunnerCapability.SEPARATE_PROCESS, null) }
            .onSuccess { error("an unavailable probe without a reason must be rejected") }
    }
}
