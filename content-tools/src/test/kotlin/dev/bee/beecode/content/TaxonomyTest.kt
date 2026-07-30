package dev.bee.beecode.content

import java.io.File
import kotlin.io.path.createTempDirectory
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Tests the classification vocabulary itself.
 *
 * `taxonomy.yaml` and every generated `problem.yaml` tell an author that a typo in a
 * tag fails the build rather than silently creating a topic with one Problem in it.
 * These tests are what make that claim true rather than aspirational — without them
 * the promise is a comment.
 */
class TaxonomyTest {
    private val scratch: File = createTempDirectory("beecode-taxonomy").toFile()

    @AfterTest
    fun cleanUp() {
        scratch.deleteRecursively()
    }

    private fun taxonomy(text: String): Taxonomy.LoadOutcome {
        val file = File(scratch, "taxonomy.yaml")
        file.writeText(text.trimIndent())
        return Taxonomy.load(file)
    }

    private fun failureMessages(outcome: Taxonomy.LoadOutcome): List<String> {
        assertIs<Taxonomy.LoadOutcome.Failed>(outcome, "expected the load to fail")
        return outcome.messages
    }

    @Test
    fun aWellFormedTaxonomyLoads() {
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              array: A contiguous, index-addressable sequence.
            algorithms:
              two-pointers: Two indices moving under an invariant.
            """,
        )
        val loaded = assertIs<Taxonomy.LoadOutcome.Loaded>(outcome, "unexpected failure")
        assertTrue(loaded.taxonomy.dataStructures.contains("array"))
        assertTrue(loaded.taxonomy.algorithms.contains("two-pointers"))
        // The axes are separate vocabularies, not one pooled set: an algorithm slug
        // must not satisfy a dataStructures declaration.
        assertFalse(loaded.taxonomy.dataStructures.contains("two-pointers"))
        assertFalse(loaded.taxonomy.algorithms.contains("array"))
    }

    @Test
    fun aMissingFileFailsRatherThanAcceptingAnything() {
        // The failure mode worth guarding: a pack with no taxonomy must not fall back
        // to accepting every slug, which would make the whole check decorative.
        val outcome = Taxonomy.load(File(scratch, "absent.yaml"))
        assertTrue(
            failureMessages(outcome).single().contains("Missing absent.yaml"),
            "the message must name the missing file",
        )
    }

    @Test
    fun aWrongSchemaVersionIsRefused() {
        val outcome = taxonomy(
            """
            schemaVersion: 99
            dataStructures:
              array: A sequence.
            algorithms:
              sorting: Ordering the input.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("schemaVersion 99") })
    }

    @Test
    fun aMissingSchemaVersionIsRefused() {
        val outcome = taxonomy(
            """
            dataStructures:
              array: A sequence.
            algorithms:
              sorting: Ordering the input.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("must declare schemaVersion") })
    }

    @Test
    fun bothAxesAreRequired() {
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              array: A sequence.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("'algorithms' section") })
    }

    @Test
    fun anEmptyAxisIsRefused() {
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              array: A sequence.
            algorithms: {}
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("at least one slug") })
    }

    @Test
    fun anUndescribedSlugIsRefused() {
        // A slug with no description is how a vocabulary rots: later nobody can tell
        // whether a new Problem belongs under it.
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              array:
            algorithms:
              sorting: Ordering the input.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("non-empty description") })
    }

    @Test
    fun aMalformedSlugIsRefused() {
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              Hash_Map: Shouting, with an underscore.
            algorithms:
              sorting: Ordering the input.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("invalid slug 'Hash_Map'") })
    }

    @Test
    fun aSlugOnBothAxesIsRefused() {
        // An ambiguous slug would make a tag unable to say which axis it classifies.
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              graph: Vertices and edges.
              sorting: Not a data structure.
            algorithms:
              sorting: Ordering the input.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("both") && it.contains("sorting") })
    }

    @Test
    fun aDuplicateSlugWithinOneAxisIsRefused() {
        // SnakeYAML is configured to reject duplicate keys, so this surfaces as a
        // parse failure rather than a silent last-one-wins.
        val outcome = taxonomy(
            """
            schemaVersion: 1
            dataStructures:
              array: A sequence.
              array: The same sequence again.
            algorithms:
              sorting: Ordering the input.
            """,
        )
        assertTrue(failureMessages(outcome).any { it.contains("not valid YAML") })
    }

    @Test
    fun everyProblemInAFaultyFileIsReportedAtOnce() {
        // An author fixing the vocabulary wants the whole list in one pass, matching
        // how the rest of content loading reports failures.
        val outcome = taxonomy(
            """
            schemaVersion: 7
            dataStructures:
              NOT-A-SLUG: Invalid shape.
              array: A sequence.
            algorithms:
              sorting:
            """,
        )
        val messages = failureMessages(outcome)
        assertTrue(messages.size >= 3, "expected several messages, got: $messages")
        assertTrue(messages.any { it.contains("schemaVersion 7") })
        assertTrue(messages.any { it.contains("invalid slug") })
        assertTrue(messages.any { it.contains("non-empty description") })
    }

    @Test
    fun invalidYamlIsReportedAsSuch() {
        val outcome = taxonomy("this: is: not: valid: yaml:")
        assertTrue(failureMessages(outcome).any { it.contains("not valid YAML") })
    }

    @Test
    fun theShippedTaxonomyLoadsAndDescribesEverySlug() {
        val outcome = Taxonomy.load(File(corePackDirectory(), ProblemLoader.FILE_TAXONOMY))
        val loaded = assertIs<Taxonomy.LoadOutcome.Loaded>(outcome, "the shipped taxonomy must load")
        val dataStructures = assertIs<Taxonomy.Vocabulary.Closed>(loaded.taxonomy.dataStructures)
        val algorithms = assertIs<Taxonomy.Vocabulary.Closed>(loaded.taxonomy.algorithms)
        assertTrue(dataStructures.known.isNotEmpty())
        assertTrue(algorithms.known.isNotEmpty())
        for (slug in dataStructures.known) {
            assertFalse(dataStructures.describe(slug).isNullOrBlank(), "$slug has no description")
        }
        for (slug in algorithms.known) {
            assertFalse(algorithms.describe(slug).isNullOrBlank(), "$slug has no description")
        }
    }

    @Test
    fun theShippedVocabularyHasNoUnusedSlugs() {
        // A slug no Problem uses is either a gap somebody meant to fill or a word
        // left behind by a retag. Either way it inflates the apparent breadth of the
        // catalogue, so it should be noticed here rather than in a coverage report.
        val pack = corePackDirectory()
        val outcome = Taxonomy.load(File(pack, ProblemLoader.FILE_TAXONOMY))
        val taxonomy = assertIs<Taxonomy.LoadOutcome.Loaded>(outcome).taxonomy
        val problems = ProblemLoader().loadPack(pack).problems

        val usedStructures = problems.flatMap { it.dataStructures }.toSet()
        val usedAlgorithms = problems.flatMap { it.algorithms }.toSet()
        assertEquals(
            emptySet(),
            taxonomy.dataStructures.known - usedStructures,
            "these dataStructures are defined but no Problem uses them",
        )
        assertEquals(
            emptySet(),
            taxonomy.algorithms.known - usedAlgorithms,
            "these algorithms are defined but no Problem uses them",
        )
    }

    private companion object {
        fun corePackDirectory(): File = File(repoRoot(), "content/packs/core")

        /** Matches [CorePackTest]: the build passes the root so the test is cwd-independent. */
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
