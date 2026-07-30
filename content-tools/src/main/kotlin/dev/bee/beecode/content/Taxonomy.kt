package dev.bee.beecode.content

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File

/**
 * The closed classification vocabulary a pack's Problems must draw from.
 *
 * Every Problem says what data structures it is really about and what algorithms or
 * techniques it trains. Both are validated against this, and an unrecognised slug
 * fails the build.
 *
 * The point is not bureaucracy, it is that classification is only useful if it is
 * consistent. Free-form tags let `dfs` and `depth-first-search` coexist, which
 * silently splits one topic into two: a learner filtering for one sees half the
 * Problems and a coverage report understates the gap. A typo should break the build
 * instead of quietly creating a topic with one Problem in it.
 *
 * It lives in the pack as `taxonomy.yaml` rather than in Kotlin because goal
 * PROB-004 requires that adding content never means editing a Kotlin registry. The
 * same reasoning applies to the words that content needs. Extending the vocabulary
 * is a reviewed content change and cannot introduce executable logic — the file is
 * slugs mapped to prose descriptions, and the descriptions are never interpreted.
 */
class Taxonomy(
    val dataStructures: Vocabulary,
    val algorithms: Vocabulary,
) {
    /**
     * One closed set of slugs, plus the descriptions that document them.
     *
     * [Permissive] exists so loading a single Problem directory in a test does not
     * need a whole pack around it. It is a distinct subclass rather than a flag or a
     * pretend set so that "accepts anything" can never be reached by a pack load
     * that merely failed to find its taxonomy file.
     */
    sealed class Vocabulary {
        abstract operator fun contains(slug: String): Boolean

        /** The accepted slugs, for an error message. Empty when anything is accepted. */
        abstract val known: Set<String>

        class Closed(private val descriptions: Map<String, String>) : Vocabulary() {
            override fun contains(slug: String): Boolean = descriptions.containsKey(slug)

            override val known: Set<String> get() = descriptions.keys

            /** The prose description of a slug, for generated documentation. */
            fun describe(slug: String): String? = descriptions[slug]
        }

        object Permissive : Vocabulary() {
            override fun contains(slug: String): Boolean = true

            override val known: Set<String> get() = emptySet()
        }
    }

    sealed interface LoadOutcome {
        data class Loaded(val taxonomy: Taxonomy) : LoadOutcome

        data class Failed(val messages: List<String>) : LoadOutcome
    }

    companion object {
        /** Version of the taxonomy format this build understands. */
        const val SCHEMA_VERSION: Int = 1

        /**
         * Accepts any slug. For loading one Problem outside a pack, in tests and
         * authoring tools. Never used by [ProblemLoader.loadPack].
         */
        val PERMISSIVE: Taxonomy = Taxonomy(Vocabulary.Permissive, Vocabulary.Permissive)

        /**
         * Read a pack's taxonomy.
         *
         * Collects every problem rather than throwing on the first, matching how the
         * rest of content loading reports failures: an author fixing the file wants
         * the whole list.
         */
        fun load(file: File): LoadOutcome {
            if (!file.isFile) {
                return LoadOutcome.Failed(
                    listOf(
                        "Missing ${file.name}. A pack must declare the classification " +
                            "vocabulary its Problems draw from.",
                    ),
                )
            }

            val errors = mutableListOf<String>()
            val document = runCatching { parse(file) }.getOrElse {
                return LoadOutcome.Failed(listOf("${file.name} is not valid YAML: ${it.message}"))
            } ?: return LoadOutcome.Failed(listOf("${file.name} must contain a top-level mapping"))

            val schemaVersion = (document["schemaVersion"] as? Number)?.toInt()
            if (schemaVersion == null) {
                errors += "${file.name} must declare schemaVersion"
            } else if (schemaVersion != SCHEMA_VERSION) {
                errors += "${file.name} declares schemaVersion $schemaVersion " +
                    "but this build of BeeCode reads version $SCHEMA_VERSION"
            }

            val dataStructures = readSection(file.name, document, "dataStructures", errors)
            val algorithms = readSection(file.name, document, "algorithms", errors)

            // A slug meaning one thing under dataStructures and another under
            // algorithms would make a tag ambiguous about which axis it classifies.
            val overlap = dataStructures.keys.intersect(algorithms.keys)
            if (overlap.isNotEmpty()) {
                errors += "${overlap.sorted().joinToString()} appear under both " +
                    "dataStructures and algorithms; each slug must classify one axis"
            }

            return if (errors.isEmpty()) {
                LoadOutcome.Loaded(
                    Taxonomy(Vocabulary.Closed(dataStructures), Vocabulary.Closed(algorithms)),
                )
            } else {
                LoadOutcome.Failed(errors)
            }
        }

        private fun readSection(
            fileName: String,
            document: Map<*, *>,
            key: String,
            errors: MutableList<String>,
        ): Map<String, String> {
            val node = document[key]
            if (node == null) {
                errors += "$fileName must declare a '$key' section"
                return emptyMap()
            }
            val map = node as? Map<*, *> ?: run {
                errors += "$fileName '$key' must be a mapping of slug to description"
                return emptyMap()
            }
            val result = linkedMapOf<String, String>()
            for ((rawSlug, rawDescription) in map) {
                val slug = rawSlug?.toString().orEmpty()
                if (!isValidSlug(slug)) {
                    // Matching ProblemId's shape keeps slugs usable in a filename, a
                    // URL, or a query string without escaping.
                    errors += "$fileName '$key' has an invalid slug '$slug'; " +
                        "use lowercase ASCII letters, digits, and single hyphens"
                    continue
                }
                val description = rawDescription?.toString()?.takeIf { it.isNotBlank() }
                if (description == null) {
                    // A slug with no description is how a vocabulary rots: nobody can
                    // tell later whether a new Problem belongs under it.
                    errors += "$fileName '$key' slug '$slug' must have a non-empty description"
                    continue
                }
                result[slug] = description
            }
            if (result.isEmpty() && errors.isEmpty()) {
                errors += "$fileName '$key' must define at least one slug"
            }
            return result
        }

        private fun isValidSlug(value: String): Boolean =
            value.isNotEmpty() &&
                value.all { it in 'a'..'z' || it in '0'..'9' || it == '-' } &&
                !value.startsWith('-') &&
                !value.endsWith('-') &&
                !value.contains("--")

        private fun parse(file: File): Map<*, *>? {
            // Bounded and alias-free for the same reason ProblemLoader is: content is
            // contributor-authored, and a YAML expansion attack must not be able to
            // exhaust memory at build time.
            val options = LoaderOptions().apply {
                setAllowDuplicateKeys(false)
                setAllowRecursiveKeys(false)
                setCodePointLimit(MAX_YAML_CODE_POINTS)
            }
            return Yaml(options).load<Any?>(file.readText()) as? Map<*, *>
        }

        private const val MAX_YAML_CODE_POINTS = 1024 * 1024
    }
}
