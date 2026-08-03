package dev.bee.beecode.content

import dev.bee.beecode.domain.ExecutionOutcome
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.python.PythonRunner
import dev.bee.beecode.python.RunRequest
import java.io.File

/**
 * Proves a Problem pack is actually correct before it ships.
 *
 * Loading checks that the files parse. Validation checks something stronger and
 * much more useful: that the declared tests **agree with a working solution**.
 *
 * Three failures this catches that no amount of schema validation could:
 *
 * - A wrong `expected` value. The author fat-fingered an index and every learner
 *   would fail a Problem that is stated correctly.
 * - A starter that already solves the Problem, so there is nothing to learn.
 * - A reference that does not actually work, meaning the Problem has never been
 *   solved by anyone and may not be solvable as stated.
 *
 * It also proves `reference.py` is excluded from the compiled output, because
 * shipping the answer would defeat the product.
 */
class PackValidator(
    private val runner: PythonRunner,
    private val loader: ProblemLoader = ProblemLoader(),
) {

    /**
     * Validate every Problem in a pack.
     *
     * Runs real Python, so it is slow by design — this belongs in CI and in the
     * authoring loop, not on a client's startup path.
     */
    suspend fun validate(packDirectory: File): ValidationReport {
        val loadResult = loader.loadPack(packDirectory)
        val problemFindings = mutableListOf<ProblemValidation>()

        val probe = runner.probe()
        if (!probe.available) {
            // Report rather than throw: a machine without Python can still check
            // the schema, and saying so plainly beats a confusing crash.
            return ValidationReport(
                loadFailures = loadResult.failures,
                problems = emptyList(),
                skippedReason = "Python is unavailable, so reference solutions were not run: " +
                    probe.unavailableReason,
            )
        }

        for (problem in loadResult.problems) {
            problemFindings += validateProblem(packDirectory, problem)
        }

        return ValidationReport(
            loadFailures = loadResult.failures,
            problems = problemFindings,
            skippedReason = null,
        )
    }

    private suspend fun validateProblem(
        packDirectory: File,
        problem: ProblemDefinition,
    ): ProblemValidation {
        val errors = mutableListOf<String>()
        val warnings = mutableListOf<String>()
        val directory = File(File(packDirectory, "problems"), problem.id.value)

        // 1. The reference must pass every declared test. This is the assertion
        //    that makes the test data trustworthy.
        val referenceSource = File(directory, ProblemLoader.FILE_REFERENCE).readText()
        val referenceResult = runner.execute(
            RunRequest.from(
                runId = ExecutionRunId("validate-reference-${problem.id.value}".take(64)),
                problem = problem,
                source = referenceSource,
            ),
        )
        if (referenceResult.outcome != ExecutionOutcome.PASSED) {
            errors += buildString {
                append("reference.py does not pass the declared tests (")
                append(referenceResult.outcome)
                append(')')
                referenceResult.diagnostic?.let {
                    append(": ").append(it.message.lineSequence().first())
                }
                val failing = referenceResult.testResults.filter { !it.passed }
                if (failing.isNotEmpty()) {
                    append("; failing: ")
                    append(failing.joinToString { test -> "${test.name}${test.message?.let { " ($it)" } ?: ""}" })
                }
            }
        }

        // 2. The starter must NOT pass. If it does, the Problem teaches nothing.
        val starterResult = runner.execute(
            RunRequest.from(
                runId = ExecutionRunId("validate-starter-${problem.id.value}".take(64)),
                problem = problem,
                source = problem.starterSource,
            ),
        )
        if (starterResult.outcome == ExecutionOutcome.PASSED) {
            errors += "starter.py already passes every test, so the Problem has nothing to solve"
        }

        // 3. The reference solution must not be readable before the learner
        //    chooses to reveal.
        //
        //    Scoped deliberately. `explanation.md` legitimately contains a worked
        //    solution — that is what a revealable explanation is for, and revealing
        //    it marks the session aided and caps the rating. The leak that matters
        //    is solution logic reaching the statement, the starter, or the tests,
        //    where a learner sees it without opting in.
        val compiled = ProblemPack.compileProblem(problem)
        if (compiled.contains(ProblemLoader.FILE_REFERENCE)) {
            errors += "the compiled Problem still references ${ProblemLoader.FILE_REFERENCE}"
        }

        val visibleBeforeReveal = buildString {
            append(problem.statementMarkdown)
            append('\n')
            append(problem.starterSource)
            append('\n')
            problem.examples.forEach { append(it.input).append(it.output).append(it.explanation.orEmpty()) }
            problem.tests.forEach { append(it.name).append(it.argumentsJson).append(it.expectedJson) }
        }
        val leakedLines = referenceSource.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("def ") }
            .filter { it.length >= MIN_LEAK_LINE_LENGTH }
            .filter { visibleBeforeReveal.contains(it) }
        if (leakedLines.isNotEmpty()) {
            errors += "reference solution logic is visible before revealing: ${leakedLines.joinToString("; ")}"
        }

        // 4. Quality warnings. Not build failures, because content can legitimately
        //    be terse, but worth surfacing while authoring.
        if (problem.tests.size < MIN_RECOMMENDED_TESTS) {
            warnings += "only ${problem.tests.size} tests; ${MIN_RECOMMENDED_TESTS}+ gives better coverage"
        }
        if (problem.tests.none { it.hidden }) {
            warnings += "no hidden tests, so the Problem can be solved by reading the assertions"
        }
        if (problem.explanationMarkdown == null) {
            warnings += "no explanation.md, so the learner has nothing to reveal when stuck"
        }
        if (problem.examples.isEmpty()) {
            warnings += "no worked examples in problem.yaml"
        }

        return ProblemValidation(
            problemId = problem.id.value,
            revisionId = problem.revisionId.value,
            testCount = problem.tests.size,
            hiddenTestCount = problem.tests.count { it.hidden },
            errors = errors,
            warnings = warnings,
        )
    }

    private companion object {
        const val MIN_RECOMMENDED_TESTS = 4

        /** Below this length a matching line is coincidence, not a leak. */
        const val MIN_LEAK_LINE_LENGTH = 12
    }
}

data class ProblemValidation(
    val problemId: String,
    val revisionId: String,
    val testCount: Int,
    val hiddenTestCount: Int,
    val errors: List<String>,
    val warnings: List<String>,
) {
    val isValid: Boolean get() = errors.isEmpty()
}

data class ValidationReport(
    val loadFailures: List<ProblemLoadFailure>,
    val problems: List<ProblemValidation>,
    /** Set when reference solutions could not be run at all. */
    val skippedReason: String?,
) {
    val isValid: Boolean get() = loadFailures.isEmpty() && problems.all { it.isValid }

    val errorCount: Int get() = loadFailures.sumOf { it.messages.size } + problems.sumOf { it.errors.size }

    val warningCount: Int get() = problems.sumOf { it.warnings.size }

    /** A human-readable report, for CI logs and the authoring loop. */
    fun describe(): String = buildString {
        appendLine("BeeCode pack validation")
        appendLine("  Problems loaded: ${problems.size}")
        appendLine("  Tests: ${problems.sumOf { it.testCount }} (${problems.sumOf { it.hiddenTestCount }} hidden)")
        appendLine("  Errors: $errorCount")
        appendLine("  Warnings: $warningCount")
        skippedReason?.let { appendLine("  SKIPPED: $it") }

        if (loadFailures.isNotEmpty()) {
            appendLine()
            appendLine("Load failures:")
            for (failure in loadFailures) appendLine(failure.describe())
        }

        val broken = problems.filter { !it.isValid }
        if (broken.isNotEmpty()) {
            appendLine()
            appendLine("Validation errors:")
            for (problem in broken) {
                appendLine("${problem.problemId}:")
                for (error in problem.errors) appendLine("  - $error")
            }
        }

        val warned = problems.filter { it.warnings.isNotEmpty() }
        if (warned.isNotEmpty()) {
            appendLine()
            appendLine("Warnings:")
            for (problem in warned) {
                appendLine("${problem.problemId}:")
                for (warning in problem.warnings) appendLine("  - $warning")
            }
        }
    }
}
