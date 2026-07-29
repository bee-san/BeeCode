package dev.bee.beecode.python

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.TestCaseResult
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The wire format between BeeCode and the Python harness.
 *
 * Shared by both platforms so desktop and Android cannot drift apart in how they
 * interpret a result. The harness script itself is also shared verbatim; only the
 * transport differs.
 *
 * The response is located by a sentinel line rather than by parsing all of stdout,
 * because learner code writes to the same stream. A learner who prints something
 * shaped like our JSON must not be able to forge a pass.
 *
 * The payload is base64-encoded by the harness. That is not obfuscation: it
 * guarantees the payload region cannot itself contain the sentinel. Without it,
 * a learner who prints the sentinel has it echoed back inside the captured-output
 * field of the real response, which sits *after* the real sentinel and makes a
 * last-occurrence scan read garbage. Base64's alphabet excludes the sentinel, so
 * the last occurrence is always the true frame.
 */
object HarnessProtocol {
    /** Marks the start of the harness response within captured stdout. */
    const val RESULT_SENTINEL: String = "__BEECODE_RESULT__"

    /** Classpath location of the harness script, shipped with both clients. */
    const val HARNESS_RESOURCE: String = "/dev/bee/beecode/python/beecode_harness.py"

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    /** Load the harness source. Fails loudly: a missing harness is a build error. */
    fun harnessSource(): String =
        HarnessProtocol::class.java.getResourceAsStream(HARNESS_RESOURCE)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?: error("The BeeCode Python harness is missing from the classpath at $HARNESS_RESOURCE")

    fun encodeRequest(request: RunRequest): String = json.encodeToString(
        HarnessRequest(
            harnessVersion = request.harnessVersion,
            source = request.source,
            entryPoint = request.entryPoint,
            tests = request.tests.map {
                HarnessTest(
                    name = it.name,
                    argumentsJson = it.argumentsJson,
                    expectedJson = it.expectedJson,
                    comparatorId = it.comparatorId.name,
                    hidden = it.hidden,
                )
            },
        ),
    )

    /**
     * Extract and decode the response payload from raw stdout.
     *
     * Takes the text after the **last** sentinel and base64-decodes it. Because
     * the harness encodes the payload, the region after the final sentinel is
     * always the true frame: a learner's echoed sentinel can only appear earlier,
     * or quoted inside the encoded blob where it is inert.
     *
     * Returns null when no frame is present or it does not decode, which the
     * caller maps to a worker failure rather than a wrong answer.
     */
    fun extractPayload(stdout: String): String? {
        val index = stdout.lastIndexOf(RESULT_SENTINEL)
        if (index < 0) return null
        val encoded = stdout.substring(index + RESULT_SENTINEL.length)
            .trim()
            .ifEmpty { return null }
        return try {
            java.util.Base64.getDecoder().decode(encoded).decodeToString()
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    /**
     * Decode a harness payload into a typed result.
     *
     * Any parse failure becomes [ExecutionOutcome.WORKER_FAILURE]: if BeeCode
     * cannot understand its own harness, that is BeeCode's fault and must not be
     * charged to the learner as a wrong answer.
     */
    fun decodeResult(
        payload: String,
        request: RunRequest,
        durationMillis: Long,
        runnerId: String,
        limits: Int,
    ): RunResult {
        val decoded = try {
            json.decodeFromString<HarnessResponse>(payload)
        } catch (e: Exception) {
            return workerFailure(
                request = request,
                durationMillis = durationMillis,
                runnerId = runnerId,
                diagnostic = "Could not parse the harness response: ${e.message}",
            )
        }

        val outcome = when (decoded.outcome) {
            "PASSED" -> ExecutionOutcome.PASSED
            "FAILED" -> ExecutionOutcome.FAILED
            "SYNTAX_ERROR" -> ExecutionOutcome.SYNTAX_ERROR
            "RUNTIME_ERROR" -> ExecutionOutcome.RUNTIME_ERROR
            // The harness caught an error in itself. Not the learner's fault.
            "HARNESS_ERROR" -> ExecutionOutcome.WORKER_FAILURE
            else -> return workerFailure(
                request = request,
                durationMillis = durationMillis,
                runnerId = runnerId,
                diagnostic = "The harness reported an unknown outcome '${decoded.outcome}'",
            )
        }

        val (output, truncated) = truncateOutput(decoded.output, limits)

        return RunResult(
            runId = request.runId,
            outcome = outcome,
            testResults = decoded.testResults.map {
                TestCaseResult(
                    name = it.name,
                    passed = it.passed,
                    hidden = it.hidden,
                    expectedJson = it.expectedJson,
                    actualJson = it.actualJson,
                    message = it.message,
                    durationMillis = it.durationMillis.coerceAtLeast(0),
                )
            },
            output = output,
            outputTruncated = truncated,
            durationMillis = durationMillis,
            runnerId = runnerId,
            pythonVersion = decoded.pythonVersion,
            diagnostic = decoded.diagnostic,
        )
    }

    /**
     * Bound captured output.
     *
     * Keeps the **tail**, not the head: when a program prints in a loop and then
     * crashes, the informative part is what it said last.
     */
    fun truncateOutput(output: String, maxBytes: Int): Pair<String, Boolean> {
        val bytes = output.encodeToByteArray()
        if (bytes.size <= maxBytes) return output to false
        val kept = bytes.copyOfRange(bytes.size - maxBytes, bytes.size)
        // Decoding a byte-sliced UTF-8 sequence can leave a broken leading
        // character; decodeToString replaces it rather than throwing.
        return kept.decodeToString() to true
    }

    /** Build a [RunResult] for an infrastructure fault. */
    fun workerFailure(
        request: RunRequest,
        durationMillis: Long,
        runnerId: String,
        diagnostic: String,
        pythonVersion: String = "unknown",
        output: String = "",
    ): RunResult = RunResult(
        runId = request.runId,
        outcome = ExecutionOutcome.WORKER_FAILURE,
        testResults = emptyList(),
        output = output,
        outputTruncated = false,
        durationMillis = durationMillis,
        runnerId = runnerId,
        pythonVersion = pythonVersion,
        diagnostic = diagnostic,
    )

    /** Build a [RunResult] for a run that exceeded its deadline. */
    fun timeout(
        request: RunRequest,
        durationMillis: Long,
        runnerId: String,
        output: String = "",
        pythonVersion: String = "unknown",
    ): RunResult = RunResult(
        runId = request.runId,
        outcome = ExecutionOutcome.TIMEOUT,
        testResults = emptyList(),
        output = output,
        outputTruncated = false,
        durationMillis = durationMillis,
        runnerId = runnerId,
        pythonVersion = pythonVersion,
        diagnostic = "Your code did not finish within ${request.limits.wallClockMillis} ms " +
            "and was stopped. This usually means a loop never ends.",
    )

    /** Build a [RunResult] for a learner-cancelled run. */
    fun cancelled(
        request: RunRequest,
        durationMillis: Long,
        runnerId: String,
        pythonVersion: String = "unknown",
    ): RunResult = RunResult(
        runId = request.runId,
        outcome = ExecutionOutcome.CANCELLED,
        testResults = emptyList(),
        output = "",
        outputTruncated = false,
        durationMillis = durationMillis,
        runnerId = runnerId,
        pythonVersion = pythonVersion,
        diagnostic = null,
    )
}

@Serializable
private data class HarnessRequest(
    val harnessVersion: Int,
    val source: String,
    val entryPoint: String,
    val tests: List<HarnessTest>,
)

@Serializable
private data class HarnessTest(
    val name: String,
    val argumentsJson: String,
    val expectedJson: String,
    val comparatorId: String,
    val hidden: Boolean,
)

@Serializable
private data class HarnessResponse(
    val outcome: String,
    @SerialName("testResults")
    val testResults: List<HarnessTestResult> = emptyList(),
    val output: String = "",
    val pythonVersion: String = "unknown",
    val diagnostic: String? = null,
)

@Serializable
private data class HarnessTestResult(
    val name: String,
    val passed: Boolean,
    val hidden: Boolean = false,
    val expectedJson: String? = null,
    val actualJson: String? = null,
    val message: String? = null,
    val durationMillis: Long = 0,
)
