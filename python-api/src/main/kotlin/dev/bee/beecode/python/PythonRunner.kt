package dev.bee.beecode.python

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.TestCaseResult

/**
 * Runs learner Python and returns a typed result.
 *
 * Both platforms implement this, but by very different means: desktop spawns a
 * disposable CPython child under a supervisor, Android drives an embedded
 * interpreter in a separate process. The contract exists so the review flow above
 * cannot tell them apart, and so cross-platform conformance is testable.
 *
 * Implementations must honour three rules:
 *
 * 1. **Always return, never throw.** Every failure mode maps to an
 *    [ExecutionOutcome]; a runner that throws would leave the review flow with
 *    no state to show the learner. Infrastructure faults use
 *    [ExecutionOutcome.WORKER_FAILURE], which the domain refuses to treat as a
 *    wrong answer.
 * 2. **Respect cancellation.** Cancelling the calling coroutine must terminate
 *    the process tree, not merely stop waiting for it. A Python `while True`
 *    holds the GIL and will not notice a polite request.
 * 3. **Bound the output.** Truncate at [ExecutionLimits.maxOutputBytes] and set
 *    the truncation flag rather than streaming a runaway `print` into memory.
 */
interface PythonRunner {
    /** Identifies which runner produced a result, for conformance debugging. */
    val runnerId: String

    /**
     * The honest containment level this runner actually achieves.
     *
     * Surfaced in the UI rather than assumed, because v1 does not claim to be a
     * hostile-code sandbox and pretending otherwise would be the more dangerous
     * error.
     */
    val capability: RunnerCapability

    /**
     * Prepare the runtime, returning whether Python is actually usable.
     *
     * Separate from [execute] so the UI can report a missing interpreter at
     * startup rather than as a mysterious failed review.
     */
    suspend fun probe(): RunnerProbe

    suspend fun execute(request: RunRequest): RunResult
}

/**
 * Everything needed to judge one attempt.
 *
 * Deliberately self-contained: a request carries its own tests and limits rather
 * than a Problem reference, so the runner needs no access to the content pack,
 * the database, or the learner's history. That is a privacy boundary as much as a
 * design one — see [RunRequest.Companion.from].
 */
data class RunRequest(
    val runId: ExecutionRunId,
    val problemId: ProblemId,
    val problemRevisionId: ProblemRevisionId,
    /** The immutable source snapshot to execute. */
    val source: String,
    val entryPoint: String,
    val tests: List<RunTest>,
    val limits: ExecutionLimits,
    /**
     * Version of the harness protocol. Bumped when the JSON contract between
     * Kotlin and the Python harness changes, so a stale worker is detected
     * rather than silently misparsed.
     */
    val harnessVersion: Int = HARNESS_VERSION,
) {
    init {
        require(source.isNotEmpty()) { "A run request must carry source to execute" }
        require(entryPoint.isNotBlank()) { "A run request must name an entry point" }
        require(tests.isNotEmpty()) { "A run request must carry at least one test" }
    }

    companion object {
        const val HARNESS_VERSION: Int = 2

        /**
         * Build a request from a Problem and a source snapshot.
         *
         * Note what is *not* copied: the statement, the explanation, the
         * learner's history, and any credential. The runner receives the minimum
         * needed to judge the code. The explanation in particular is inert text
         * the runner never sees, so it cannot be executed even by accident.
         */
        fun from(
            runId: ExecutionRunId,
            problem: ProblemDefinition,
            source: String,
        ): RunRequest = RunRequest(
            runId = runId,
            problemId = problem.id,
            problemRevisionId = problem.revisionId,
            source = source,
            entryPoint = problem.entryPoint,
            tests = problem.tests.map {
                RunTest(
                    name = it.name,
                    argumentsJson = it.argumentsJson,
                    expectedJson = it.expectedJson,
                    comparatorId = it.comparatorId,
                    hidden = it.hidden,
                )
            },
            limits = problem.limits,
        )
    }
}

/** One test case as the runner sees it. */
data class RunTest(
    val name: String,
    val argumentsJson: String,
    val expectedJson: String,
    val comparatorId: ComparatorId,
    val hidden: Boolean,
)

/**
 * The typed result of one attempt.
 *
 * This is a *value*, not a report: it carries no exception and no partially
 * populated error state. The runner has already classified what happened.
 */
data class RunResult(
    val runId: ExecutionRunId,
    val outcome: ExecutionOutcome,
    val testResults: List<TestCaseResult>,
    val output: String,
    val outputTruncated: Boolean,
    val durationMillis: Long,
    val runnerId: String,
    val pythonVersion: String,
    /** Human-readable detail and, when known, the learner-source location. */
    val diagnostic: RunDiagnostic?,
) {
    init {
        require(durationMillis >= 0) { "durationMillis must not be negative" }
    }

    val isPass: Boolean get() = outcome == ExecutionOutcome.PASSED
}

/**
 * A one-based position in learner source.
 *
 * [column] is counted in Unicode code points, matching Python's diagnostics. It
 * is null when Python can identify a line but not a precise character.
 */
data class SourcePosition(
    val line: Int,
    val column: Int? = null,
) {
    init {
        require(line >= 1) { "line must be one-based" }
        require(column == null || column >= 1) { "column must be one-based" }
    }
}

/**
 * A source range whose end is exclusive when a column is available.
 *
 * Runtime tracebacks usually provide only [start]; syntax errors can provide
 * both ends.
 */
data class SourceRange(
    val start: SourcePosition,
    val end: SourcePosition? = null,
)

/** Learner-facing run detail with an optional location in the submitted source. */
data class RunDiagnostic(
    val message: String,
    val sourceRange: SourceRange? = null,
) {
    init {
        require(message.isNotBlank()) { "A diagnostic must explain the problem" }
    }
}

/**
 * How much containment a runner honestly provides.
 *
 * Ordered weakest to strongest. The UI shows this verbatim; the plan is explicit
 * that same-process execution must not be called a sandbox.
 */
enum class RunnerCapability {
    /**
     * Learner code shares the UI process. A hang or crash can take the app with
     * it. Labelled "trusted code only" and used only as a last-resort fallback.
     */
    IN_PROCESS,

    /**
     * A separate OS process that can be killed without harming the UI, but with
     * ordinary user privileges: it can read the user's files and open sockets.
     * This is the honest baseline.
     */
    SEPARATE_PROCESS,

    /**
     * A separate process with meaningful additional restrictions — no network, no
     * access to app-private storage or credentials, and a distinct UID.
     */
    ISOLATED_PROCESS,
}

/**
 * Whether a runner can actually run.
 *
 * A missing interpreter is a normal condition on desktop, not an exceptional one,
 * so it is a return value with a message the UI can show and act on.
 */
data class RunnerProbe(
    val available: Boolean,
    val pythonVersion: String?,
    val capability: RunnerCapability,
    /** Why it is unavailable, and ideally what to do about it. */
    val unavailableReason: String?,
) {
    init {
        require(available == (pythonVersion != null)) {
            "An available runner must report its Python version, and an unavailable one must not"
        }
        require(available == (unavailableReason == null)) {
            "An unavailable runner must give a reason, and an available one must not"
        }
    }
}
