package dev.bee.beecode.domain

import kotlinx.datetime.Instant

/**
 * The result of one bounded Python execution.
 *
 * The outcome kind is a **closed** set. Every way an attempt can end maps to
 * exactly one [ExecutionOutcome], because the UI must always be able to tell the
 * learner what happened, and the review policy must always be able to decide
 * what rating is permissible. "Something went wrong" is not an available state.
 *
 * An `ExecutionRun` is an immutable record of an attempt. Running code never, by
 * itself, changes FSRS state, achievements, or any social projection — that only
 * happens when a review is explicitly finalized. This is what makes it safe to
 * experiment.
 */
data class ExecutionRun(
    val id: ExecutionRunId,
    val problemId: ProblemId,
    /** The exact content revision this attempt was judged against. */
    val problemRevisionId: ProblemRevisionId,
    /**
     * The immutable source snapshot that was executed.
     *
     * Held by value rather than by reference to the live draft, because the
     * learner keeps typing while the run is in flight. Finalization binds to the
     * source that actually produced the result, so a review can never claim
     * credit for code that was never run.
     */
    val source: String,
    val outcome: ExecutionOutcome,
    val testResults: List<TestCaseResult>,
    /** Combined bounded stdout/stderr. May be truncated; see [outputTruncated]. */
    val output: String,
    val outputTruncated: Boolean,
    val durationMillis: Long,
    val startedAt: Instant,
    /** Which runner produced this, for cross-platform conformance debugging. */
    val runnerId: String,
    val pythonVersion: String,
) {
    init {
        require(durationMillis >= 0) { "durationMillis must not be negative" }
        require(source.isNotEmpty()) { "An execution run must record its source" }
        if (outcome == ExecutionOutcome.PASSED) {
            require(testResults.isNotEmpty()) { "A passing run must report test results" }
            require(testResults.all { it.passed }) {
                "A run cannot be PASSED while a test result failed"
            }
        }
        if (outcome == ExecutionOutcome.FAILED) {
            require(testResults.any { !it.passed }) {
                "A FAILED run must report at least one failing test"
            }
        }
    }

    val passedTestCount: Int get() = testResults.count { it.passed }

    val totalTestCount: Int get() = testResults.size

    /** True when every declared test ran and passed. */
    val isPass: Boolean get() = outcome == ExecutionOutcome.PASSED
}

/**
 * Every distinct way an execution can end.
 *
 * These are kept separate rather than collapsed into pass/fail because the
 * learner needs different next actions for each: a syntax error is a typo, a
 * timeout is an algorithmic problem, and a worker failure is BeeCode's fault,
 * not theirs.
 */
enum class ExecutionOutcome {
    /** Every declared test passed. The only outcome eligible for an unaided solve. */
    PASSED,

    /** The code ran to completion but at least one test disagreed. */
    FAILED,

    /** The source did not compile. No test ran. */
    SYNTAX_ERROR,

    /** The code raised an uncaught exception. Diagnostics carry the traceback. */
    RUNTIME_ERROR,

    /** The wall-clock deadline elapsed and the process was terminated. */
    TIMEOUT,

    /** The learner cancelled. Not a failure, and never rated. */
    CANCELLED,

    /**
     * The runner itself failed: the worker died, the control channel broke, or
     * the runtime was missing. This is an infrastructure fault, so it must never
     * be presented to the learner as a wrong answer.
     */
    WORKER_FAILURE,
}

/**
 * One test case's result.
 *
 * `expected` and `actual` are nullable because a hidden test withholds them, and
 * because a test that never ran has neither.
 */
data class TestCaseResult(
    val name: String,
    val passed: Boolean,
    val hidden: Boolean,
    /** JSON-rendered expected value. Null when hidden or unavailable. */
    val expectedJson: String?,
    /** JSON-rendered actual value. Null when hidden or the test raised. */
    val actualJson: String?,
    /** Human-readable failure detail, already bounded by the runner. */
    val message: String?,
    val durationMillis: Long,
) {
    init {
        require(name.isNotBlank()) { "A test result must name its test" }
        require(durationMillis >= 0) { "durationMillis must not be negative" }
    }
}
