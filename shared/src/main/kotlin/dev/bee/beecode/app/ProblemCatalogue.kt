package dev.bee.beecode.app

import dev.bee.beecode.content.ProblemLoader
import dev.bee.beecode.content.ProblemPack
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemId
import java.io.File
import java.util.UUID
import dev.bee.beecode.domain.DeviceId
import dev.bee.beecode.domain.DomainEventId
import dev.bee.beecode.domain.ExecutionRunId
import dev.bee.beecode.domain.IdGenerator
import dev.bee.beecode.domain.ReviewSessionId

/**
 * The Problems available to study.
 *
 * Loaded once at startup and held in memory: the whole launch pack is a handful of
 * Problems and a few hundred kilobytes, so paging it would add failure modes for
 * no benefit. Loading eagerly also means a broken pack fails at launch with a
 * clear message rather than mid-review.
 */
class ProblemCatalogue private constructor(
    private val byId: Map<ProblemId, ProblemDefinition>,
) {
    fun problem(id: ProblemId): ProblemDefinition? = byId[id]

    /** Every Problem, ordered by difficulty then ID for a stable presentation. */
    fun allProblems(): List<ProblemDefinition> =
        byId.values.sortedWith(compareBy({ it.difficulty.ordinal }, { it.id.value }))

    val size: Int get() = byId.size

    fun topics(): List<String> = byId.values.flatMap { it.topics }.distinct().sorted()

    companion object {
        /**
         * Load from a compiled pack, as shipped inside a client.
         *
         * This is the production path: clients read data, never author directories.
         */
        fun fromPackJson(json: String): ProblemCatalogue =
            ProblemCatalogue(ProblemPack.decode(json).associateBy { it.id })

        /**
         * Load from a compiled pack on the classpath.
         *
         * @throws IllegalStateException if the resource is missing, because a
         *   client with no Problems cannot do anything useful and should say so.
         */
        fun fromResource(resourcePath: String): ProblemCatalogue {
            val text = ProblemCatalogue::class.java.getResourceAsStream(resourcePath)
                ?.bufferedReader()
                ?.use { it.readText() }
                ?: error("The BeeCode Problem pack is missing from the classpath at $resourcePath")
            return fromPackJson(text)
        }

        /**
         * Load directly from authoring directories.
         *
         * For development and tests only. Throws on any validation failure so a
         * broken Problem is impossible to miss while authoring.
         */
        fun fromSourceDirectory(packDirectory: File): ProblemCatalogue {
            val result = ProblemLoader().loadPack(packDirectory)
            check(result.isValid) { "The Problem pack failed to load:\n${result.describeFailures()}" }
            return ProblemCatalogue(result.problems.associateBy { it.id })
        }

        fun of(problems: List<ProblemDefinition>): ProblemCatalogue =
            ProblemCatalogue(problems.associateBy { it.id })
    }
}

/**
 * Production identity generator.
 *
 * UUIDv4 because ADR 0002 commits to a sync model in which two devices create rows
 * offline and later merge them: identities must not collide across devices, which
 * rules out any counter.
 */
class UuidIdGenerator : IdGenerator {
    override fun newExecutionRunId(): ExecutionRunId = ExecutionRunId(newId())

    override fun newReviewSessionId(): ReviewSessionId = ReviewSessionId(newId())

    override fun newDomainEventId(): DomainEventId = DomainEventId(newId())

    override fun newDeviceId(): DeviceId = DeviceId(newId())

    private fun newId(): String = UUID.randomUUID().toString()
}
