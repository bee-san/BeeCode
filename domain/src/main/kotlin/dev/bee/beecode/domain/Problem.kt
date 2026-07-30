package dev.bee.beecode.domain

/**
 * A compiled, immutable Problem: everything the learner needs to attempt it and
 * everything the runner needs to judge it.
 *
 * This is the *runtime* representation produced by the content pipeline, not the
 * authoring format. Authoring uses one folder of human-editable files; the
 * pipeline validates it and emits this. Two consequences follow:
 *
 * - A client pack contains **data only**. Comparators and codecs are selected by
 *   versioned ID from a trusted built-in registry, so content can never
 *   introduce arbitrary executable judge logic.
 * - `reference.py` is **not** present here. The reference solution is used at
 *   build time to prove the declared tests actually pass, then excluded from the
 *   pack. There is no field it could occupy.
 */
data class ProblemDefinition(
    val id: ProblemId,
    val revisionId: ProblemRevisionId,
    val title: String,
    val difficulty: ProblemDifficulty,
    /**
     * The union of [dataStructures] and [algorithms], for filtering and statistics
     * that do not care which axis a tag came from.
     *
     * Derived by the content pipeline rather than authored, so it cannot disagree
     * with the two lists it summarises.
     */
    val topics: List<String>,
    /**
     * What the Problem is made of: the structures the input arrives in, or that a
     * solution has to build. Drawn from the pack's closed taxonomy.
     */
    val dataStructures: List<String> = emptyList(),
    /**
     * What the Problem trains you to do: the algorithms and techniques a good
     * solution uses. Drawn from the pack's closed taxonomy.
     *
     * Separate from [dataStructures] because "practise trees" and "practise binary
     * search" are different requests, and one flat list cannot tell them apart.
     */
    val algorithms: List<String> = emptyList(),
    /** Markdown statement shown to the learner. Never executed. */
    val statementMarkdown: String,
    /** The source the editor is pre-filled with on a first attempt. */
    val starterSource: String,
    /** The function the harness calls. Trusted: comes from validated content. */
    val entryPoint: String,
    /** Worked examples shown alongside the statement. Not used for judging. */
    val examples: List<ProblemExample>,
    /** The official suite. Passing all of these is what "passed" means. */
    val tests: List<ProblemTest>,
    val limits: ExecutionLimits,
    /**
     * Revealable explanation. Held as inert text and rendered as Markdown; the
     * runner never receives it, so it cannot be executed even accidentally.
     * Revealing it makes the session aided, which caps the rating.
     */
    val explanationMarkdown: String?,
) {
    init {
        require(title.isNotBlank()) { "Problem $id must have a title" }
        require(statementMarkdown.isNotBlank()) { "Problem $id must have a statement" }
        require(entryPoint.isNotBlank()) { "Problem $id must declare an entry point" }
        require(entryPoint.isValidPythonIdentifier()) {
            "Problem $id entry point must be a Python identifier: '$entryPoint'"
        }
        require(tests.isNotEmpty()) { "Problem $id must declare at least one test" }
        require(tests.map { it.name }.toSet().size == tests.size) {
            "Problem $id has duplicate test names"
        }
        require(topics.all { it.isNotBlank() }) { "Problem $id has a blank topic" }
        require(dataStructures.all { it.isNotBlank() }) { "Problem $id has a blank data structure" }
        require(algorithms.all { it.isNotBlank() }) { "Problem $id has a blank algorithm" }
        // The invariant that makes `topics` safe to read on its own: anything a
        // caller filters by on the union must be findable on one of the two axes.
        require(topics.containsAll(dataStructures) && topics.containsAll(algorithms)) {
            "Problem $id has topics that do not cover its dataStructures and algorithms"
        }
    }

    /** True when the learner can reveal a packaged explanation for this Problem. */
    val hasExplanation: Boolean get() = explanationMarkdown != null
}

enum class ProblemDifficulty {
    EASY,
    MEDIUM,
    HARD,
}

/** An illustrative input/output pair shown in the statement. */
data class ProblemExample(
    val input: String,
    val output: String,
    val explanation: String?,
) {
    init {
        require(input.isNotBlank()) { "An example must have an input" }
        require(output.isNotBlank()) { "An example must have an output" }
    }
}

/**
 * One official test case.
 *
 * Arguments and the expected value are held as JSON text rather than Kotlin
 * values. The runner decodes them with a trusted codec inside the Python
 * process, which keeps the domain free of Python type semantics and keeps the
 * wire format inspectable.
 */
data class ProblemTest(
    val name: String,
    /** JSON array of positional arguments for the entry point. */
    val argumentsJson: String,
    /** JSON-encoded expected return value. */
    val expectedJson: String,
    /**
     * ID of a trusted built-in comparator. Content selects, never supplies,
     * judge logic.
     */
    val comparatorId: ComparatorId,
    /**
     * Hidden tests still run and still gate passing, but their arguments and
     * expected values are not shown on failure. This keeps a Problem from being
     * solved by reading the assertions.
     */
    val hidden: Boolean = false,
) {
    init {
        require(name.isNotBlank()) { "A test must have a name" }
        require(argumentsJson.isNotBlank()) { "Test '$name' must have arguments" }
        require(expectedJson.isNotBlank()) { "Test '$name' must have an expected value" }
    }
}

/**
 * Identifies a comparator in the trusted built-in registry.
 *
 * Comparators are versioned because changing how equality is decided changes
 * whether a stored review passed. A pack pins the comparator it was authored
 * against.
 */
enum class ComparatorId {
    /** Structural equality after JSON decoding. The default. */
    EXACT,

    /** List equality ignoring order, for problems with unordered answers. */
    UNORDERED_LIST,

    /** Floating-point equality within a fixed relative tolerance. */
    APPROXIMATE_NUMERIC,

    /** Any one of several accepted answers, expected value being a JSON array. */
    ANY_OF,
}

/**
 * Bounds on one execution attempt.
 *
 * These are a capability contract, not a security sandbox. They stop honest
 * mistakes — an infinite loop, a runaway print — from taking down the UI or
 * filling the disk. v1 does not claim to contain hostile code.
 */
data class ExecutionLimits(
    val wallClockMillis: Long,
    val maxOutputBytes: Int,
    val maxMemoryBytes: Long?,
) {
    init {
        require(wallClockMillis in MIN_WALL_CLOCK_MILLIS..MAX_WALL_CLOCK_MILLIS) {
            "wallClockMillis must be in $MIN_WALL_CLOCK_MILLIS..$MAX_WALL_CLOCK_MILLIS"
        }
        require(maxOutputBytes in MIN_OUTPUT_BYTES..MAX_OUTPUT_BYTES) {
            "maxOutputBytes must be in $MIN_OUTPUT_BYTES..$MAX_OUTPUT_BYTES"
        }
        require(maxMemoryBytes == null || maxMemoryBytes > 0) {
            "maxMemoryBytes must be positive when set"
        }
    }

    companion object {
        const val MIN_WALL_CLOCK_MILLIS: Long = 100
        const val MAX_WALL_CLOCK_MILLIS: Long = 60_000
        const val MIN_OUTPUT_BYTES: Int = 1_024
        const val MAX_OUTPUT_BYTES: Int = 1_048_576

        /**
         * Defaults sized for interactive study rather than competitive judging:
         * long enough that a correct-but-slow first solution is not punished,
         * short enough that a hung run is noticed immediately.
         */
        val DEFAULT: ExecutionLimits = ExecutionLimits(
            wallClockMillis = 5_000,
            maxOutputBytes = 65_536,
            maxMemoryBytes = 256L * 1024 * 1024,
        )
    }
}

/**
 * A conservative Python identifier check.
 *
 * Deliberately ASCII-only even though Python 3 permits Unicode identifiers: the
 * entry point is interpolated into a generated harness, and restricting it
 * removes a class of encoding surprises. Validated content will not hit this.
 */
private fun String.isValidPythonIdentifier(): Boolean {
    if (isEmpty()) return false
    if (this in PYTHON_KEYWORDS) return false
    val first = this[0]
    if (!(first.isAsciiLetter() || first == '_')) return false
    return all { it.isAsciiLetter() || it in '0'..'9' || it == '_' }
}

private fun Char.isAsciiLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'

private val PYTHON_KEYWORDS = setOf(
    "False", "None", "True", "and", "as", "assert", "async", "await", "break",
    "class", "continue", "def", "del", "elif", "else", "except", "finally",
    "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
    "not", "or", "pass", "raise", "return", "try", "while", "with", "yield",
)
