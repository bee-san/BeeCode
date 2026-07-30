package dev.bee.beecode.content

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemExample
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ProblemTest
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * The compiled, client-facing form of a Problem pack.
 *
 * A pack is **data only**. It carries no executable judge logic: comparators are
 * named by ID and resolved against a trusted built-in registry, so content can
 * choose how equality is decided but never supply the code that decides it.
 *
 * `reference.py` is absent by construction — there is no field it could occupy.
 * That is checked rather than assumed, in [PackValidator] and in this module's
 * tests, because shipping the answer would defeat the product.
 *
 * Serialization is deterministic: the same source produces byte-identical output.
 * Without that the pack hash would not be usable as a version.
 */
object ProblemPack {
    private val json = Json {
        prettyPrint = false
        encodeDefaults = true
    }

    // prettyPrintIndent is still experimental in kotlinx-serialization. It only
    // affects the human-readable debug form, never the shipped pack, so opting in
    // here cannot change what a client reads.
    @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
    private val readableJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
    }

    /** Serialize a whole pack. */
    fun encode(
        packId: String,
        problems: List<ProblemDefinition>,
        readable: Boolean = false,
    ): String {
        val payload = PackPayload(
            formatVersion = FORMAT_VERSION,
            packId = packId,
            // Sorted so pack contents cannot depend on filesystem listing order.
            problems = problems.sortedBy { it.id.value }.map { it.toWire() },
        )
        return if (readable) readableJson.encodeToString(payload) else json.encodeToString(payload)
    }

    fun decode(text: String): List<ProblemDefinition> {
        val payload = json.decodeFromString<PackPayload>(text)
        require(payload.formatVersion == FORMAT_VERSION) {
            "This pack uses format version ${payload.formatVersion}, " +
                "but this build of BeeCode reads version $FORMAT_VERSION"
        }
        return payload.problems.map { it.toDomain() }
    }

    /** Serialize one Problem. Used by the validator's leakage check. */
    fun compileProblem(problem: ProblemDefinition): String = json.encodeToString(problem.toWire())

    const val FORMAT_VERSION: Int = 1

    private fun ProblemDefinition.toWire() = WireProblem(
        id = id.value,
        revisionId = revisionId.value,
        title = title,
        difficulty = difficulty.name,
        topics = topics,
        dataStructures = dataStructures,
        algorithms = algorithms,
        statementMarkdown = statementMarkdown,
        starterSource = starterSource,
        entryPoint = entryPoint,
        examples = examples.map { WireExample(it.input, it.output, it.explanation) },
        tests = tests.map {
            WireTest(it.name, it.argumentsJson, it.expectedJson, it.comparatorId.name, it.hidden)
        },
        wallClockMillis = limits.wallClockMillis,
        maxOutputBytes = limits.maxOutputBytes,
        maxMemoryBytes = limits.maxMemoryBytes,
        explanationMarkdown = explanationMarkdown,
    )

    private fun WireProblem.toDomain() = ProblemDefinition(
        id = ProblemId(id),
        revisionId = ProblemRevisionId(revisionId),
        title = title,
        difficulty = ProblemDifficulty.valueOf(difficulty),
        topics = topics,
        dataStructures = dataStructures,
        algorithms = algorithms,
        statementMarkdown = statementMarkdown,
        starterSource = starterSource,
        entryPoint = entryPoint,
        examples = examples.map { ProblemExample(it.input, it.output, it.explanation) },
        tests = tests.map {
            ProblemTest(
                name = it.name,
                argumentsJson = it.argumentsJson,
                expectedJson = it.expectedJson,
                comparatorId = ComparatorId.valueOf(it.comparatorId),
                hidden = it.hidden,
            )
        },
        limits = ExecutionLimits(wallClockMillis, maxOutputBytes, maxMemoryBytes),
        explanationMarkdown = explanationMarkdown,
    )
}

@Serializable
private data class PackPayload(
    val formatVersion: Int,
    val packId: String,
    val problems: List<WireProblem>,
)

@Serializable
private data class WireProblem(
    val id: String,
    val revisionId: String,
    val title: String,
    val difficulty: String,
    val topics: List<String>,
    val dataStructures: List<String>,
    val algorithms: List<String>,
    val statementMarkdown: String,
    val starterSource: String,
    val entryPoint: String,
    val examples: List<WireExample>,
    val tests: List<WireTest>,
    val wallClockMillis: Long,
    val maxOutputBytes: Int,
    val maxMemoryBytes: Long?,
    val explanationMarkdown: String?,
)

@Serializable
private data class WireExample(
    val input: String,
    val output: String,
    val explanation: String?,
)

@Serializable
private data class WireTest(
    val name: String,
    val argumentsJson: String,
    val expectedJson: String,
    val comparatorId: String,
    val hidden: Boolean,
)
