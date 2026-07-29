package dev.bee.beecode.fsrs

/**
 * BeeCode's scheduling preferences.
 *
 * These are BeeCode's to choose, not FSRS's. The engine computes memory state;
 * this decides what BeeCode does with it.
 */
data class SchedulerPolicy(
    /**
     * Target probability of recall at the moment a Problem comes due.
     *
     * 0.9 is the FSRS default. Lower means longer intervals and more
     * forgetting; higher means shorter intervals and more work per day.
     */
    val desiredRetention: Double = DEFAULT_DESIRED_RETENTION,
    /**
     * Cap on any single interval, in fractional days.
     *
     * Ten years, not infinity: an interval beyond a decade is indistinguishable
     * from "never" and makes the schedule impossible to reason about.
     */
    val maximumIntervalDays: Double = DEFAULT_MAXIMUM_INTERVAL_DAYS,
    /**
     * The 35 FSRS-7 parameters, or null for the engine's pinned defaults.
     *
     * Overridable because a learner's own optimized parameters are the main
     * reason to keep FSRS rather than a fixed ladder.
     */
    val parameters: DoubleArray? = null,
) {
    init {
        require(desiredRetention.isFinite() && desiredRetention > 0.0 && desiredRetention < 1.0) {
            "desiredRetention must be in (0, 1), was $desiredRetention"
        }
        require(maximumIntervalDays.isFinite() && maximumIntervalDays > 0.0) {
            "maximumIntervalDays must be finite and positive, was $maximumIntervalDays"
        }
        require(parameters == null || parameters.size == PARAMETER_COUNT) {
            "parameters must contain exactly $PARAMETER_COUNT values"
        }
    }

    // DoubleArray is a reference type, so the generated data-class equals would
    // compare identity and silently treat two identical parameter sets as
    // different. That would make the policy unusable as a cache key.
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SchedulerPolicy) return false
        return desiredRetention == other.desiredRetention &&
            maximumIntervalDays == other.maximumIntervalDays &&
            (parameters?.contentEquals(other.parameters) ?: (other.parameters == null))
    }

    override fun hashCode(): Int {
        var result = desiredRetention.hashCode()
        result = 31 * result + maximumIntervalDays.hashCode()
        result = 31 * result + (parameters?.contentHashCode() ?: 0)
        return result
    }

    companion object {
        const val DEFAULT_DESIRED_RETENTION: Double = 0.9

        /** Ten years. */
        const val DEFAULT_MAXIMUM_INTERVAL_DAYS: Double = 36_500.0

        /** FSRS-7's parameter count, not FSRS-6's 21. */
        const val PARAMETER_COUNT: Int = 35

        val DEFAULT: SchedulerPolicy = SchedulerPolicy()
    }
}
