package dev.bee.beecode.content

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemExample
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ProblemTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray

import org.yaml.snakeyaml.LoaderOptions
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.security.MessageDigest

/**
 * Loads and compiles one-folder Problem definitions.
 *
 * Authoring is a directory of human-editable files; the runtime representation is
 * [ProblemDefinition]. Discovery is by directory listing, so adding a Problem
 * never requires editing a Kotlin registry — which is the property that makes
 * content contribution cheap.
 *
 * Loading is strict. Every failure is collected and reported with the file it came
 * from rather than throwing on the first one, because an author fixing a new
 * Problem wants the whole list, not one error at a time.
 */
class ProblemLoader(private val json: Json = Json) {

    /**
     * Load every Problem in a pack directory.
     *
     * Sorted by ID so a pack build is deterministic: the same source must produce
     * byte-identical output, or the pack hash is meaningless as a version.
     */
    fun loadPack(packDirectory: File): PackLoadResult {
        val problemsDirectory = File(packDirectory, "problems")
        if (!problemsDirectory.isDirectory) {
            return PackLoadResult(
                problems = emptyList(),
                failures = listOf(
                    ProblemLoadFailure(
                        problemId = null,
                        path = problemsDirectory.path,
                        messages = listOf("No 'problems' directory exists in ${packDirectory.path}"),
                    ),
                ),
            )
        }

        val directories = problemsDirectory.listFiles()
            ?.filter { it.isDirectory && !it.name.startsWith(".") }
            ?.sortedBy { it.name }
            ?: emptyList()

        val problems = mutableListOf<ProblemDefinition>()
        val failures = mutableListOf<ProblemLoadFailure>()
        for (directory in directories) {
            when (val result = load(directory)) {
                is ProblemLoadOutcome.Loaded -> problems += result.problem
                is ProblemLoadOutcome.Failed -> failures += result.failure
            }
        }
        return PackLoadResult(problems, failures)
    }

    /** Load and compile a single Problem directory. */
    fun load(directory: File): ProblemLoadOutcome {
        val errors = mutableListOf<String>()

        val id = runCatching { ProblemId(directory.name) }.getOrElse {
            // Without a valid ID nothing else can be attributed, so stop here.
            return ProblemLoadOutcome.Failed(
                ProblemLoadFailure(null, directory.path, listOf("Invalid Problem directory name: ${it.message}")),
            )
        }

        val metadataFile = File(directory, FILE_METADATA)
        val statementFile = File(directory, FILE_STATEMENT)
        val starterFile = File(directory, FILE_STARTER)
        val testsFile = File(directory, FILE_TESTS)
        val referenceFile = File(directory, FILE_REFERENCE)
        val explanationFile = File(directory, FILE_EXPLANATION)

        for (required in listOf(metadataFile, statementFile, starterFile, testsFile, referenceFile)) {
            if (!required.isFile) errors += "Missing required file ${required.name}"
        }
        if (errors.isNotEmpty()) {
            return ProblemLoadOutcome.Failed(ProblemLoadFailure(id, directory.path, errors))
        }

        val metadata = runCatching { parseYaml(metadataFile) }.getOrElse {
            errors += "$FILE_METADATA is not valid YAML: ${it.message}"
            null
        }
        val testsDocument = runCatching { parseYaml(testsFile) }.getOrElse {
            errors += "$FILE_TESTS is not valid YAML: ${it.message}"
            null
        }
        if (metadata == null || testsDocument == null) {
            return ProblemLoadOutcome.Failed(ProblemLoadFailure(id, directory.path, errors))
        }

        val schemaVersion = metadata.int("schemaVersion")
        if (schemaVersion == null) {
            errors += "$FILE_METADATA must declare schemaVersion"
        } else if (schemaVersion != SCHEMA_VERSION) {
            // Refuse rather than guess: a format change could silently alter how
            // tests are judged.
            errors += "$FILE_METADATA declares schemaVersion $schemaVersion " +
                "but this build of BeeCode reads version $SCHEMA_VERSION"
        }

        val title = metadata.string("title")?.takeIf { it.isNotBlank() }
        if (title == null) errors += "$FILE_METADATA must declare a non-empty title"

        val difficulty = metadata.string("difficulty")?.let { raw ->
            when (raw.lowercase()) {
                "easy" -> ProblemDifficulty.EASY
                "medium" -> ProblemDifficulty.MEDIUM
                "hard" -> ProblemDifficulty.HARD
                else -> {
                    errors += "Unknown difficulty '$raw'; expected easy, medium, or hard"
                    null
                }
            }
        } ?: run {
            if (metadata.string("difficulty") == null) errors += "$FILE_METADATA must declare a difficulty"
            null
        }

        val entryPoint = metadata.string("entryPoint")?.takeIf { it.isNotBlank() }
        if (entryPoint == null) errors += "$FILE_METADATA must declare an entryPoint"

        val topics = metadata.stringList("topics")
        if (topics.isEmpty()) errors += "$FILE_METADATA must declare at least one topic"

        // Provenance is mandatory: the plan commits to original or licensed
        // content only, so a Problem that cannot say where it came from is not
        // publishable.
        val provenance = metadata.map("provenance")
        if (provenance == null) {
            errors += "$FILE_METADATA must declare a provenance block"
        } else {
            for (field in listOf("origin", "author", "license")) {
                if (provenance.string(field).isNullOrBlank()) {
                    errors += "provenance must declare a non-empty '$field'"
                }
            }
        }

        val limits = parseLimits(metadata.map("limits"), errors)
        val examples = parseExamples(metadata.list("examples"), errors)
        val tests = parseTests(directory, testsDocument, errors)

        val statement = statementFile.readText()
        if (statement.isBlank()) errors += "$FILE_STATEMENT must not be empty"
        val starter = starterFile.readText()
        if (starter.isBlank()) errors += "$FILE_STARTER must not be empty"

        // Explanation is optional, but if present it must not be empty: an empty
        // reveal is worse than no reveal button at all.
        val explanation = if (explanationFile.isFile) {
            explanationFile.readText().also {
                if (it.isBlank()) errors += "$FILE_EXPLANATION exists but is empty"
            }
        } else {
            null
        }

        if (errors.isNotEmpty()) {
            return ProblemLoadOutcome.Failed(ProblemLoadFailure(id, directory.path, errors))
        }

        val problem = try {
            ProblemDefinition(
                id = id,
                // Computed from the content that determines behaviour, so a
                // statement or test change produces a new revision and a learner's
                // history stays attributed to what they actually saw.
                revisionId = computeRevision(
                    id = id,
                    title = title!!,
                    statement = statement,
                    starter = starter,
                    entryPoint = entryPoint!!,
                    tests = tests,
                    limits = limits,
                ),
                title = title,
                difficulty = difficulty!!,
                topics = topics,
                statementMarkdown = statement,
                starterSource = starter,
                entryPoint = entryPoint,
                examples = examples,
                tests = tests,
                limits = limits,
                explanationMarkdown = explanation,
            )
        } catch (e: IllegalArgumentException) {
            // The domain's own invariants are the last line of defence, and they
            // catch what field-by-field validation above cannot express.
            return ProblemLoadOutcome.Failed(
                ProblemLoadFailure(id, directory.path, listOf("Invalid Problem: ${e.message}")),
            )
        }

        return ProblemLoadOutcome.Loaded(problem)
    }

    private fun parseLimits(node: YamlMap?, errors: MutableList<String>): ExecutionLimits {
        if (node == null) return ExecutionLimits.DEFAULT
        val wallClock = node.long("wallClockMillis") ?: ExecutionLimits.DEFAULT.wallClockMillis
        val maxOutput = node.int("maxOutputBytes") ?: ExecutionLimits.DEFAULT.maxOutputBytes
        val maxMemory = node.long("maxMemoryBytes") ?: ExecutionLimits.DEFAULT.maxMemoryBytes
        return runCatching { ExecutionLimits(wallClock, maxOutput, maxMemory) }.getOrElse {
            errors += "Invalid limits: ${it.message}"
            ExecutionLimits.DEFAULT
        }
    }

    private fun parseExamples(nodes: List<Any?>?, errors: MutableList<String>): List<ProblemExample> {
        if (nodes == null) return emptyList()
        return nodes.mapIndexedNotNull { index, raw ->
            val node = raw.asYamlMap() ?: run {
                errors += "examples[$index] is not a mapping"
                return@mapIndexedNotNull null
            }
            val input = node.string("input")
            val output = node.string("output")
            if (input.isNullOrBlank() || output.isNullOrBlank()) {
                errors += "examples[$index] must declare a non-empty input and output"
                return@mapIndexedNotNull null
            }
            ProblemExample(input, output, node.string("explanation"))
        }
    }

    private fun parseTests(
        directory: File,
        document: YamlMap,
        errors: MutableList<String>,
    ): List<ProblemTest> {
        val nodes = document.list("tests")
        if (nodes.isNullOrEmpty()) {
            errors += "$FILE_TESTS must declare at least one test"
            return emptyList()
        }

        val seen = mutableSetOf<String>()
        val tests = nodes.mapIndexedNotNull { index, raw ->
            val node = raw.asYamlMap() ?: run {
                errors += "tests[$index] is not a mapping"
                return@mapIndexedNotNull null
            }
            val name = node.string("name")?.takeIf { it.isNotBlank() } ?: run {
                errors += "tests[$index] must declare a name"
                return@mapIndexedNotNull null
            }
            if (!seen.add(name)) {
                // Duplicate names would make a failure report ambiguous about which
                // case actually failed.
                errors += "Duplicate test name '$name'"
                return@mapIndexedNotNull null
            }

            // Arguments come either inline or from a sidecar file, which keeps a
            // 20,000-element performance case out of the readable YAML.
            val argumentsJson = when {
                node.containsKey("argumentsFile") -> {
                    val fileName = node.string("argumentsFile")
                    val file = fileName?.let { File(directory, it) }
                    when {
                        fileName == null -> {
                            errors += "Test '$name' declares an empty argumentsFile"
                            null
                        }
                        // Reject traversal explicitly: content is authored by
                        // contributors, and a path escaping the Problem directory
                        // must never be read.
                        fileName.contains("..") || fileName.contains('/') || fileName.contains('\\') -> {
                            errors += "Test '$name' argumentsFile must be a plain file name in the Problem directory"
                            null
                        }
                        file == null || !file.isFile -> {
                            errors += "Test '$name' argumentsFile '$fileName' does not exist"
                            null
                        }
                        else -> file.readText().trim().also {
                            if (parseJsonArray(it) == null) {
                                errors += "Test '$name' argumentsFile must contain a JSON array"
                            }
                        }
                    }
                }
                node.containsKey("arguments") -> encodeYamlAsJson(node["arguments"]).also {
                    if (parseJsonArray(it) == null) {
                        errors += "Test '$name' arguments must be a list of positional arguments"
                    }
                }
                else -> {
                    errors += "Test '$name' must declare arguments or argumentsFile"
                    null
                }
            } ?: return@mapIndexedNotNull null

            if (!node.containsKey("expected")) {
                errors += "Test '$name' must declare an expected value"
                return@mapIndexedNotNull null
            }
            val expectedJson = encodeYamlAsJson(node["expected"])

            val comparatorRaw = node.string("comparator") ?: DEFAULT_COMPARATOR
            val comparator = COMPARATORS[comparatorRaw.lowercase()] ?: run {
                // Fail closed. A pack built against a newer BeeCode must not be
                // loaded with a comparator that silently judges differently.
                errors += "Test '$name' uses unknown comparator '$comparatorRaw'; " +
                    "known comparators: ${COMPARATORS.keys.sorted().joinToString()}"
                return@mapIndexedNotNull null
            }

            runCatching {
                ProblemTest(
                    name = name,
                    argumentsJson = argumentsJson,
                    expectedJson = expectedJson,
                    comparatorId = comparator,
                    hidden = node.boolean("hidden") ?: false,
                )
            }.getOrElse {
                errors += "Test '$name' is invalid: ${it.message}"
                null
            }
        }

        if (tests.none { !it.hidden }) {
            // A Problem with only hidden tests gives a learner nothing to debug
            // against, which makes failure unactionable.
            errors += "$FILE_TESTS must declare at least one visible (non-hidden) test"
        }
        return tests
    }

    private fun parseJsonArray(text: String): JsonArray? =
        runCatching { json.parseToJsonElement(text) as? JsonArray }.getOrNull()

    /**
     * Convert a YAML value into the canonical JSON the runner sees.
     *
     * Canonical matters: the revision hash is computed over this text, so two
     * authors writing the same test differently in YAML must produce the same
     * bytes here, or the same content would appear to be different revisions.
     */
    private fun encodeYamlAsJson(value: Any?): String = buildString { appendYamlAsJson(value, this) }

    private fun appendYamlAsJson(value: Any?, out: StringBuilder) {
        when (value) {
            null -> out.append("null")
            is Boolean -> out.append(if (value) "true" else "false")
            is Int, is Long, is Short, is Byte -> out.append(value.toString())
            is Double, is Float -> {
                val d = (value as Number).toDouble()
                // JSON has no infinity or NaN; a test expecting one is a content
                // bug that must surface rather than serialize to something invalid.
                require(d.isFinite()) { "Numeric values must be finite, got $d" }
                // Render integral doubles without a trailing .0 so YAML's 1 and 1.0
                // hash identically.
                if (d == Math.floor(d) && !d.isInfinite() && Math.abs(d) < 1e15) {
                    out.append(d.toLong().toString())
                } else {
                    out.append(d.toString())
                }
            }
            is String -> appendJsonString(value, out)
            is List<*> -> {
                out.append('[')
                value.forEachIndexed { index, element ->
                    if (index > 0) out.append(',')
                    appendYamlAsJson(element, out)
                }
                out.append(']')
            }
            is Map<*, *> -> {
                out.append('{')
                // Sorted so map ordering in YAML cannot change the hash.
                value.entries
                    .sortedBy { it.key?.toString().orEmpty() }
                    .forEachIndexed { index, entry ->
                        if (index > 0) out.append(',')
                        appendJsonString(entry.key?.toString().orEmpty(), out)
                        out.append(':')
                        appendYamlAsJson(entry.value, out)
                    }
                out.append('}')
            }
            else -> appendJsonString(value.toString(), out)
        }
    }

    private fun appendJsonString(value: String, out: StringBuilder) {
        out.append('"')
        for (c in value) {
            when (c) {
                '"' -> out.append("\\\"")
                '\\' -> out.append("\\\\")
                '\n' -> out.append("\\n")
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                else -> if (c < ' ') {
                    out.append("\\u").append(c.code.toString(16).padStart(4, '0'))
                } else {
                    out.append(c)
                }
            }
        }
        out.append('"')
    }

    private fun parseYaml(file: File): YamlMap {
        // Bounded and alias-free: content is contributor-authored, and a YAML
        // billion-laughs expansion must not be able to exhaust memory at build
        // time.
        val options = LoaderOptions().apply {
            // A duplicated key in a Problem file is silently the last-one-wins
            // otherwise, which could hide a test the author believes is running.
            setAllowDuplicateKeys(false)
            setAllowRecursiveKeys(false)
            setCodePointLimit(MAX_YAML_CODE_POINTS)
        }
        val loaded = Yaml(options).load<Any?>(file.readText())
        return loaded.asYamlMap() ?: throw IllegalArgumentException("expected a top-level mapping")
    }

    /**
     * A content hash covering everything that determines behaviour.
     *
     * Deliberately excludes the explanation and the examples: revealing better
     * prose should not invalidate a learner's review history, because the Problem
     * they solved is unchanged. Includes the tests and limits, because those decide
     * whether a given solution passes.
     */
    private fun computeRevision(
        id: ProblemId,
        title: String,
        statement: String,
        starter: String,
        entryPoint: String,
        tests: List<ProblemTest>,
        limits: ExecutionLimits,
    ): ProblemRevisionId {
        val digest = MessageDigest.getInstance("SHA-256")
        fun feed(vararg parts: String) {
            for (part in parts) {
                digest.update(part.encodeToByteArray())
                // A separator prevents "ab" + "c" from hashing like "a" + "bc".
                digest.update(0)
            }
        }
        feed("beecode-problem-v$SCHEMA_VERSION", id.value, title, statement, starter, entryPoint)
        feed(limits.wallClockMillis.toString(), limits.maxOutputBytes.toString())
        for (test in tests) {
            feed(test.name, test.argumentsJson, test.expectedJson, test.comparatorId.name, test.hidden.toString())
        }
        return ProblemRevisionId(digest.digest().joinToString("") { "%02x".format(it) })
    }

    companion object {
        /** Version of the authoring format this loader understands. */
        const val SCHEMA_VERSION: Int = 1

        const val FILE_METADATA = "problem.yaml"
        const val FILE_STATEMENT = "statement.md"
        const val FILE_STARTER = "starter.py"
        const val FILE_TESTS = "tests.yaml"
        const val FILE_REFERENCE = "reference.py"
        const val FILE_EXPLANATION = "explanation.md"

        /**
         * Files that exist for tooling and must never reach a client pack.
         *
         * `reference.py` is the answer; shipping it would defeat the product.
         */
        val TOOLING_ONLY_FILES = setOf(FILE_REFERENCE)

        private const val DEFAULT_COMPARATOR = "exact"

        private const val MAX_YAML_CODE_POINTS = 8 * 1024 * 1024

        private val COMPARATORS: Map<String, ComparatorId> = mapOf(
            "exact" to ComparatorId.EXACT,
            "unordered_list" to ComparatorId.UNORDERED_LIST,
            "approximate_numeric" to ComparatorId.APPROXIMATE_NUMERIC,
            "any_of" to ComparatorId.ANY_OF,
        )
    }
}

/** The outcome of loading one Problem. */
sealed interface ProblemLoadOutcome {
    data class Loaded(val problem: ProblemDefinition) : ProblemLoadOutcome

    data class Failed(val failure: ProblemLoadFailure) : ProblemLoadOutcome
}

/**
 * Every problem found with one Problem folder.
 *
 * All messages, not just the first: an author fixing a new Problem wants the whole
 * list in one pass.
 */
data class ProblemLoadFailure(
    val problemId: ProblemId?,
    val path: String,
    val messages: List<String>,
) {
    fun describe(): String = buildString {
        append(problemId?.value ?: path)
        append(':')
        for (message in messages) {
            append("\n  - ")
            append(message)
        }
    }
}

data class PackLoadResult(
    val problems: List<ProblemDefinition>,
    val failures: List<ProblemLoadFailure>,
) {
    val isValid: Boolean get() = failures.isEmpty()

    fun describeFailures(): String = failures.joinToString("\n") { it.describe() }
}

// ---- Minimal typed YAML access ---------------------------------------

/**
 * A thin typed view over SnakeYAML's untyped maps.
 *
 * Exists so the loader reads like validation rather than a sequence of casts, and
 * so a wrong type produces a message naming the field instead of a
 * ClassCastException.
 */
internal class YamlMap(private val backing: Map<*, *>) {
    operator fun get(key: String): Any? = backing[key]

    fun containsKey(key: String): Boolean = backing.containsKey(key)

    fun string(key: String): String? = when (val value = backing[key]) {
        null -> null
        is String -> value
        else -> value.toString()
    }

    fun int(key: String): Int? = (backing[key] as? Number)?.toInt()

    fun long(key: String): Long? = (backing[key] as? Number)?.toLong()

    fun boolean(key: String): Boolean? = backing[key] as? Boolean

    fun list(key: String): List<Any?>? = backing[key] as? List<Any?>

    fun map(key: String): YamlMap? = backing[key].asYamlMap()

    fun stringList(key: String): List<String> =
        (backing[key] as? List<*>)?.mapNotNull { it?.toString()?.takeIf(String::isNotBlank) } ?: emptyList()
}

internal fun Any?.asYamlMap(): YamlMap? = (this as? Map<*, *>)?.let { YamlMap(it) }
