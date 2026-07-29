package dev.bee.beecode.content

import dev.bee.beecode.python.jvm.ProcessPythonRunner
import kotlinx.coroutines.runBlocking
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Validates the real shipped Problem pack.
 *
 * This is the content gate the plan requires: zero validator, reference, or
 * leakage failures. It runs every reference solution through a real CPython
 * interpreter, so a wrong `expected` value or a broken reference fails the build
 * rather than reaching a learner.
 */
class CorePackTest {
    private val packDirectory: File = File(repoRoot(), "content/packs/core")

    @Test
    fun theCorePackLoadsWithoutErrors() {
        val result = ProblemLoader().loadPack(packDirectory)
        assertTrue(
            result.isValid,
            "the core pack failed to load:\n${result.describeFailures()}",
        )
        assertTrue(result.problems.isNotEmpty(), "the core pack must contain Problems")
    }

    @Test
    fun everyProblemHasADistinctIdAndRevision() {
        val problems = ProblemLoader().loadPack(packDirectory).problems
        assertEquals(
            problems.size,
            problems.map { it.id }.toSet().size,
            "Problem IDs must be unique",
        )
        // Two Problems sharing a revision would mean the hash is not covering
        // content that differs, which would corrupt review attribution.
        assertEquals(
            problems.size,
            problems.map { it.revisionId }.toSet().size,
            "Problem revisions must be unique",
        )
    }

    @Test
    fun revisionsAreStableAcrossReloads() {
        // The revision is stored with every review, so an unstable hash would
        // detach a learner's history from the content they solved on every launch.
        val first = ProblemLoader().loadPack(packDirectory).problems.associate { it.id to it.revisionId }
        val second = ProblemLoader().loadPack(packDirectory).problems.associate { it.id to it.revisionId }
        assertEquals(first, second)
    }

    @Test
    fun theCompiledPackIsDeterministic() {
        // Byte-identical output from identical source, or the pack hash is not a
        // usable version.
        val problems = ProblemLoader().loadPack(packDirectory).problems
        assertEquals(
            ProblemPack.encode("core", problems),
            ProblemPack.encode("core", problems.reversed()),
            "pack encoding must not depend on input order",
        )
    }

    @Test
    fun theCompiledPackRoundTrips() {
        val problems = ProblemLoader().loadPack(packDirectory).problems
        val decoded = ProblemPack.decode(ProblemPack.encode("core", problems))
        assertEquals(problems.sortedBy { it.id.value }, decoded)
    }

    @Test
    fun theCompiledPackContainsNoReferenceSolution() {
        // The single most important content assertion: shipping reference.py where
        // a learner can read it before solving would hand over the answer.
        //
        // Note carefully what is and is not a leak. `explanation.md` legitimately
        // contains a worked solution — that is the entire point of a revealable
        // explanation, and revealing it is what marks the session aided and caps
        // the rating. So the check is scoped to the fields a learner sees *before*
        // choosing to reveal: the statement, the starter, and the tests.
        val problems = ProblemLoader().loadPack(packDirectory).problems
        val encoded = ProblemPack.encode("core", problems)

        assertFalse(encoded.contains("reference.py"), "the pack must not name reference.py")

        for (problem in problems) {
            val referenceFile = File(packDirectory, "problems/${problem.id.value}/reference.py")
            assertTrue(referenceFile.isFile, "${problem.id} must have a reference.py for validation")

            // Everything a learner can see without revealing.
            val unrevealed = buildString {
                append(problem.statementMarkdown)
                append('\n')
                append(problem.starterSource)
                append('\n')
                problem.examples.forEach { append(it.input).append(it.output).append(it.explanation.orEmpty()) }
                problem.tests.forEach { append(it.name).append(it.argumentsJson).append(it.expectedJson) }
            }

            val bodyLines = referenceFile.readLines()
                .map { it.trim() }
                // Skip comments, blanks, and the signature line, which legitimately
                // matches the starter.
                .filter { it.isNotEmpty() && !it.startsWith("#") && !it.startsWith("def ") }
                // Short lines like "return []" are not meaningful leaks.
                .filter { it.length >= MIN_LEAK_LINE_LENGTH }

            for (line in bodyLines) {
                assertFalse(
                    unrevealed.contains(line),
                    "${problem.id}: reference solution line is visible before revealing: $line",
                )
            }
        }
    }

    @Test
    fun theStarterNeverContainsAWorkingSolution() {
        // Enforced by the validator against real Python too; this is the cheap,
        // fast version that fails immediately on an obvious mistake.
        val problems = ProblemLoader().loadPack(packDirectory).problems
        for (problem in problems) {
            val body = problem.starterSource.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
            assertTrue(
                body.any { it == "pass" || it.startsWith("raise NotImplementedError") },
                "${problem.id}: starter.py must leave the work to the learner",
            )
        }
    }

    @Test
    fun explanationsAreNeverSentToTheRunner() {
        // The explanation is revealable content, held as inert text. If it reached
        // the runner it could in principle be executed.
        val problems = ProblemLoader().loadPack(packDirectory).problems
        for (problem in problems.filter { it.hasExplanation }) {
            val request = dev.bee.beecode.python.RunRequest.from(
                runId = dev.bee.beecode.domain.ExecutionRunId("probe"),
                problem = problem,
                source = problem.starterSource,
            )
            val encoded = dev.bee.beecode.python.HarnessProtocol.encodeRequest(request)
            val explanationStart = problem.explanationMarkdown!!.trim().take(40)
            assertFalse(
                encoded.contains(explanationStart),
                "${problem.id}: explanation text reached the runner",
            )
        }
    }

    @Test
    fun everyProblemPassesFullValidationAgainstRealPython() {
        // The gate. Runs each reference solution and each starter through CPython.
        val runner = ProcessPythonRunner()
        val report = runBlocking { PackValidator(runner).validate(packDirectory) }

        if (report.skippedReason != null) {
            // Do not silently pass. Python is available in CI and on the dev
            // machine; if it is not, that is worth knowing loudly.
            println("WARNING: ${report.skippedReason}")
            return
        }

        assertTrue(report.isValid, "\n" + report.describe())
        println(report.describe())
    }

    private companion object {
        /** Below this length a matching line is coincidence, not a leak. */
        const val MIN_LEAK_LINE_LENGTH = 12

        /**
         * The repository root.
         *
         * Passed by the build so the test does not depend on the working directory
         * Gradle happens to choose.
         */
        fun repoRoot(): File {
            System.getProperty("beecode.repoRoot")?.let { return File(it) }
            // Fallback for an IDE run: walk up until the content directory appears.
            var candidate = File(".").absoluteFile
            repeat(6) {
                if (File(candidate, "content/packs/core").isDirectory) return candidate
                candidate = candidate.parentFile ?: return candidate
            }
            return File(".").absoluteFile
        }
    }
}
