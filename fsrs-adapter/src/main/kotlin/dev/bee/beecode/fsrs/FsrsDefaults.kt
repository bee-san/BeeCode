package dev.bee.beecode.fsrs

import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsParameters

/**
 * The engine's defaults and identity, re-exposed as plain values.
 *
 * This exists so the settings UI and export format can show which FSRS
 * parameters are in use without importing `dev.bee.fsrs` types. The adapter is
 * the only module that depends on the engine, and keeping that true is what
 * makes the engine replaceable.
 */
object FsrsDefaults {
    const val PARAMETER_COUNT: Int = FsrsParameters.PARAMETER_COUNT

    /** Human-readable algorithm label, recorded in every transition. */
    const val ALGORITHM_LABEL: String = FsrsAlgorithmInfo.ALGORITHM_LABEL

    /** The upstream reference this engine is a snapshot of. */
    val upstreamReference: String = FsrsAlgorithmInfo.upstreamReference()

    /** A fresh copy of the 21 default parameters. Never the engine's own array. */
    fun parameters(): DoubleArray = FsrsParameters.latestDefaultValues()

    /**
     * Validate a candidate parameter set, returning null when it is usable and a
     * human-readable reason when it is not.
     *
     * Returns a message rather than throwing because this backs a settings field
     * a learner types into, where an exception is the wrong shape.
     */
    fun validateParameters(values: DoubleArray): String? = try {
        FsrsParameters.of(values)
        null
    } catch (e: IllegalArgumentException) {
        e.message ?: "The parameter set is not valid"
    }
}
