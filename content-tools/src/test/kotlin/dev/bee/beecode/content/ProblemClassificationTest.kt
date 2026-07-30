package dev.bee.beecode.content

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests that a Problem's declared classification is actually enforced.
 *
 * [TaxonomyTest] covers the vocabulary file. This covers the other half: that a
 * Problem drawing a slug from outside the vocabulary, or declaring none at all, fails
 * to load. Both halves are needed for the promise in `taxonomy.yaml` to hold, since a
 * perfectly valid vocabulary that nothing is checked against enforces nothing.
 */
class ProblemClassificationTest {
    private val scratches = mutableListOf<File>()
    private val scratch: File = newScratch()

    private fun newScratch(): File =
        createTempDirectory("beecode-classification").toFile().also { scratches += it }

    @AfterTest
    fun cleanUp() {
        scratches.forEach { it.deleteRecursively() }
    }

    @Test
    fun aProblemDrawingFromTheVocabularyLoads() {
        val pack = pack(dataStructures = listOf("array"), algorithms = listOf("two-pointers"))
        val result = ProblemLoader().loadPack(pack)
        assertTrue(result.isValid, result.describeFailures())
        val problem = result.problems.single()
        assertEquals(listOf("array"), problem.dataStructures)
        assertEquals(listOf("two-pointers"), problem.algorithms)
    }

    @Test
    fun topicsIsTheUnionOfTheTwoAxes() {
        val pack = pack(
            dataStructures = listOf("array", "hash-map"),
            algorithms = listOf("two-pointers", "sorting"),
        )
        val problem = ProblemLoader().loadPack(pack).problems.single()
        assertEquals(
            listOf("array", "hash-map", "two-pointers", "sorting"),
            problem.topics,
            "topics must be derived from the two axes, in order",
        )
    }

    @Test
    fun aTypoInADataStructureFailsTheBuild() {
        // The claim written into taxonomy.yaml and every problem.yaml comment: a
        // near-miss spelling must fail rather than quietly become its own topic.
        val pack = pack(dataStructures = listOf("arrays"), algorithms = listOf("sorting"))
        val messages = failureMessages(pack)
        assertTrue(
            messages.any { it.contains("'arrays'") && it.contains("taxonomy.yaml") },
            "the message must name the bad slug and the file that defines the vocabulary: $messages",
        )
        // The usual cause is a near-miss, so the accepted forms must be listed.
        assertTrue(messages.any { it.contains("array") }, "the message must list the alternatives")
    }

    @Test
    fun aTypoInAnAlgorithmFailsTheBuild() {
        val pack = pack(dataStructures = listOf("array"), algorithms = listOf("dfs"))
        assertTrue(failureMessages(pack).any { it.contains("'dfs'") })
    }

    @Test
    fun aSlugFromTheWrongAxisFailsTheBuild() {
        // The axes are separate vocabularies. Tagging `sorting` as a data structure is
        // exactly the confusion the split exists to prevent.
        val pack = pack(dataStructures = listOf("sorting"), algorithms = listOf("sorting"))
        assertTrue(failureMessages(pack).any { it.contains("'sorting'") })
    }

    @Test
    fun aProblemMustDeclareBothAxes() {
        assertTrue(
            failureMessages(pack(dataStructures = emptyList(), algorithms = listOf("sorting")))
                .any { it.contains("at least one 'dataStructures'") },
        )
        assertTrue(
            failureMessages(pack(dataStructures = listOf("array"), algorithms = emptyList()))
                .any { it.contains("at least one 'algorithms'") },
        )
    }

    @Test
    fun aRepeatedSlugFailsTheBuild() {
        val pack = pack(dataStructures = listOf("array", "array"), algorithms = listOf("sorting"))
        assertTrue(failureMessages(pack).any { it.contains("more than once") })
    }

    @Test
    fun aPackWithoutATaxonomyFailsAsAWhole() {
        // One failure against the pack, not the same failure repeated per Problem.
        val pack = pack(dataStructures = listOf("array"), algorithms = listOf("sorting"))
        File(pack, ProblemLoader.FILE_TAXONOMY).delete()
        val result = ProblemLoader().loadPack(pack)
        assertTrue(result.problems.isEmpty(), "no Problem may load without a vocabulary")
        val failure = result.failures.single()
        assertEquals(null, failure.problemId, "the failure belongs to the pack, not a Problem")
        assertTrue(failure.messages.single().contains("Missing taxonomy.yaml"))
    }

    @Test
    fun retaggingDoesNotChangeTheRevision() {
        // The revision is stored with every review, and it deliberately excludes
        // classification. Retagging the whole catalogue must not detach a learner's
        // history from the content they solved.
        val original = pack(dataStructures = listOf("array"), algorithms = listOf("sorting"))
        val before = ProblemLoader().loadPack(original).problems.single().revisionId

        val retagged = pack(
            dataStructures = listOf("array", "hash-map"),
            algorithms = listOf("two-pointers"),
            directory = newScratch(),
        )
        val after = ProblemLoader().loadPack(retagged).problems.single().revisionId
        assertEquals(before, after, "classification must not feed the revision hash")
    }

    // ---- fixture ------------------------------------------------------

    private fun failureMessages(pack: File): List<String> {
        val result = ProblemLoader().loadPack(pack)
        assertTrue(result.failures.isNotEmpty(), "expected the pack to fail to load")
        return result.failures.flatMap { it.messages }
    }

    /**
     * Write a minimal one-Problem pack with a small taxonomy.
     *
     * Deliberately not a copy of the shipped pack: these tests are about the loader's
     * rules, and a fixture that grows whenever content is added would obscure them.
     */
    private fun pack(
        dataStructures: List<String>,
        algorithms: List<String>,
        directory: File = scratch,
    ): File {
        File(directory, ProblemLoader.FILE_TAXONOMY).writeText(
            """
            schemaVersion: ${Taxonomy.SCHEMA_VERSION}
            dataStructures:
              array: A contiguous, index-addressable sequence.
              hash-map: A key-to-value dictionary.
            algorithms:
              two-pointers: Two indices moving under an invariant.
              sorting: Ordering the input.
            """.trimIndent() + "\n",
        )

        val problem = File(directory, "problems/sum-two-values").apply { mkdirs() }
        // Built line by line rather than with an interpolated raw string: a multi-line
        // value spliced into `trimIndent()` leaves the surrounding lines indented,
        // which produces invalid YAML instead of the case under test.
        val metadata = buildString {
            appendLine("schemaVersion: ${ProblemLoader.SCHEMA_VERSION}")
            appendLine("title: Sum Two Values")
            appendLine("difficulty: easy")
            appendAxis("dataStructures", dataStructures)
            appendAxis("algorithms", algorithms)
            appendLine("entryPoint: sum_two")
            appendLine("examples:")
            appendLine("  - input: \"a = 1, b = 2\"")
            appendLine("    output: \"3\"")
            appendLine("provenance:")
            appendLine("  origin: original")
            appendLine("  author: test")
            appendLine("  license: CC-BY-4.0")
            appendLine("limits:")
            appendLine("  wallClockMillis: 5000")
            appendLine("  maxOutputBytes: 65536")
        }
        File(problem, ProblemLoader.FILE_METADATA).writeText(metadata)
        File(problem, "statement.md").writeText("Return the sum of `a` and `b`.\n")
        File(problem, "starter.py").writeText("def sum_two(a, b):\n    pass\n")
        File(problem, "reference.py").writeText("def sum_two(a, b):\n    return a + b\n")
        File(problem, "tests.yaml").writeText(
            """
            tests:
              - name: adds-two-positives
                arguments: [1, 2]
                expected: 3
                comparator: exact
              - name: adds-negatives
                arguments: [-1, -2]
                expected: -3
                comparator: exact
                hidden: true
            """.trimIndent() + "\n",
        )
        return directory
    }

    /** An empty axis is written as `[]` so the key is present but declares nothing. */
    private fun StringBuilder.appendAxis(name: String, slugs: List<String>) {
        if (slugs.isEmpty()) {
            appendLine("$name: []")
        } else {
            appendLine("$name:")
            slugs.forEach { appendLine("  - $it") }
        }
    }
}
