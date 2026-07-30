package dev.bee.beecode.app

import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemReviewFinalized
import dev.bee.beecode.domain.ReviewRating
import dev.bee.beecode.domain.TopicSchedule
import kotlinx.datetime.Instant

/**
 * How well the learner remembers each technique.
 *
 * A sibling of [Statistics] and [Achievements], and like them a pure fold with no
 * clock and no storage, so the numbers cannot disagree with the history they
 * summarise.
 *
 * Two figures are reported, and deliberately not blended into one:
 *
 * - **Durability** is [TopicAbility.intervalDays] — the topic's own FSRS interval.
 *   It is the algorithm's own output rather than a number invented here, and it
 *   answers "how long does my memory of DP last" directly.
 * - **Recall rate** is the fraction of reviews that were not lapses, shrunk toward
 *   the learner's own average (see [SHRINKAGE_STRENGTH]).
 *
 * Coverage — how many of a topic's Problems have been practised — is reported
 * *beside* those and never multiplied into them. No single number can separate "weak
 * at DP" from "has barely done any DP", and collapsing them would turn a true figure
 * into a misleading one.
 *
 * **Retrievability is deliberately absent.** FSRS's retrievability is 1.0 at the
 * moment of review and decays with time, so it measures recency of practice rather
 * than ability: a topic just failed and re-solved would score maximal for hours. It
 * is used for no ordering and no score here.
 */
object TopicMastery {

    /**
     * Compute the mastery view.
     *
     * @param reviews every finalized review, in any order. Folded per topic through
     *   the Problem's *current* tags, which is the same rule
     *   `ReviewRepository.rebuildTopicSchedulesFromHistory` uses — so retagging
     *   rewrites a topic's history here too, consistently rather than in one place.
     * @param topicSchedules the stored FSRS state per topic, supplying the durability
     *   figures. A topic with reviews but no schedule row (possible after a restore
     *   that has not yet rebuilt) reports its counts with null durability rather than
     *   vanishing.
     * @param now used only to decide whether a topic is currently due.
     * @param desiredRetention the shrinkage prior when the learner has no history at
     *   all — the scheduler's own target, so the fallback is a stated number rather
     *   than an invented one.
     */
    fun compute(
        reviews: List<ProblemReviewFinalized>,
        topicSchedules: Map<String, TopicSchedule>,
        problems: List<ProblemDefinition>,
        now: Instant,
        desiredRetention: Double,
    ): TopicMasteryProjection {
        val topicsByProblem: Map<ProblemId, List<String>> =
            problems.associate { it.id to it.topics.distinct() }

        val solvedProblemIds = reviews.filter { it.countsAsSolved }.map { it.problemId }.toSet()
        val attemptedProblemIds = reviews.map { it.problemId }.toSet()

        // The learner's own average, and the prior every topic is shrunk toward. Their
        // global rate is a better guess at an unpractised topic than any constant: a
        // learner who lapses often should not have a thin topic flattered by 0.9.
        val globalLapses = reviews.count { it.rating == ReviewRating.AGAIN }
        val globalRecall = if (reviews.isEmpty()) {
            desiredRetention
        } else {
            (reviews.size - globalLapses).toDouble() / reviews.size
        }

        val counts = mutableMapOf<String, MutableTopicCounts>()
        for (review in reviews) {
            for (topic in topicsByProblem[review.problemId].orEmpty()) {
                val entry = counts.getOrPut(topic) { MutableTopicCounts() }
                entry.reviews++
                if (review.rating == ReviewRating.AGAIN) entry.lapses++
                if (entry.lastPractisedAt == null || review.finalizedAt > entry.lastPractisedAt!!) {
                    entry.lastPractisedAt = review.finalizedAt
                }
            }
        }

        val members = mutableMapOf<String, MutableList<ProblemDefinition>>()
        for (problem in problems) {
            for (topic in problem.topics.distinct()) {
                members.getOrPut(topic) { mutableListOf() } += problem
            }
        }

        // Every topic the pack knows about, plus any the log knows about that the pack
        // no longer does. The second half matters: a topic retagged out of existence
        // still holds the learner's practice, and dropping it silently would make
        // their history appear to shrink.
        val allTopics = (members.keys + counts.keys + topicSchedules.keys).sorted()

        val abilities = allTopics.map { topic ->
            val count = counts[topic] ?: MutableTopicCounts()
            val topicMembers = members[topic].orEmpty()
            val schedule = topicSchedules[topic]
            TopicAbility(
                topic = topic,
                displayName = displayName(topic),
                recallRate = shrunkRecallRate(
                    reviews = count.reviews,
                    lapses = count.lapses,
                    prior = globalRecall,
                ),
                intervalDays = schedule?.intervalDays,
                stability = schedule?.stability,
                reviews = count.reviews,
                lapses = count.lapses,
                memberProblems = topicMembers.size,
                attemptedMemberProblems = topicMembers.count { it.id in attemptedProblemIds },
                solvedMemberProblems = topicMembers.count { it.id in solvedProblemIds },
                dueAt = schedule?.dueAt,
                isDue = schedule?.isDueAt(now) ?: false,
                lastPractisedAt = count.lastPractisedAt,
            )
        }

        return TopicMasteryProjection(topics = abilities, globalRecallRate = globalRecall)
    }

    /**
     * The recall rate, shrunk toward [prior], or null on too little evidence.
     *
     * `(successes + k·prior) / (n + k)` — Bayesian shrinkage with a Beta prior, which
     * is the standard way to report a rate over few trials. It is worth one sentence
     * of justification because it is the only judgement call in this file: it behaves
     * as **four notional reviews at the learner's own average**, so one lapse in a
     * barely-practised topic reads as a setback rather than as total failure.
     *
     * Null below [MIN_TOPIC_REVIEWS] rather than a plausible-looking number, matching
     * `Statistics.accuracy`'s convention. A UI must render that as "not enough
     * practice yet" and never as 0%; the difference between "weak at this" and "has
     * not done this" is the one the learner most needs.
     *
     * Because [prior] is the learner's *own* average, 0.0 and 1.0 remain reachable —
     * when a topic is their entire history the prior equals the topic's rate and pulls
     * it nowhere. That is deliberate: 100% for someone who has never lapsed at
     * anything is a true statement about the evidence, and a second invented prior to
     * hide it would make the number less honest rather than more.
     */
    fun shrunkRecallRate(reviews: Int, lapses: Int, prior: Double): Double? {
        if (reviews < MIN_TOPIC_REVIEWS) return null
        val successes = reviews - lapses
        return (successes + SHRINKAGE_STRENGTH * prior) / (reviews + SHRINKAGE_STRENGTH)
    }

    /**
     * A learner-facing name for a topic slug.
     *
     * Derived rather than looked up, because topic slugs are deliberately free-form:
     * there is no canonical vocabulary to validate against, so there is no table of
     * display names either (see ADR 0005). The accepted consequence is that a typo
     * mints a phantom topic, which will read as a phantom topic rather than crash.
     *
     * Sentence case, not title case: "Dynamic programming" is how a person writes it,
     * whereas "Dynamic Programming" is how a style guide does.
     */
    fun displayName(slug: String): String {
        val words = slug.split('-', '_').filter { it.isNotBlank() }
        if (words.isEmpty()) return slug
        return words.joinToString(" ").replaceFirstChar { it.uppercaseChar() }
    }

    /**
     * Reviews of a topic below which no recall rate is reported.
     *
     * Five, because shrinkage toward a prior cannot rescue a sample of one: with a
     * single review the reported number is almost entirely the prior, so it would say
     * more about the learner's average than about the topic and read as if it said
     * the opposite.
     */
    const val MIN_TOPIC_REVIEWS: Int = 5

    /** Strength of the prior, in notional reviews. See [shrunkRecallRate]. */
    const val SHRINKAGE_STRENGTH: Double = 4.0

    private class MutableTopicCounts {
        var reviews: Int = 0
        var lapses: Int = 0
        var lastPractisedAt: Instant? = null
    }
}

/**
 * The learner's ability across every topic, ordered by slug.
 *
 * Ordered deterministically for the same reason `Statistics.solvedByTopic` is sorted:
 * a list that reshuffles between refreshes looks like changing data.
 */
data class TopicMasteryProjection(
    val topics: List<TopicAbility>,
    /**
     * The learner's recall rate across all topics, and the shrinkage prior.
     *
     * Exposed so a client can show what a topic's figure is being pulled toward,
     * rather than leaving the shrinkage as an unexplained adjustment.
     */
    val globalRecallRate: Double,
) {
    val practised: List<TopicAbility> get() = topics.filter { it.reviews > 0 }

    val due: List<TopicAbility> get() = topics.filter { it.isDue }

    /**
     * Topics with enough evidence, weakest recall first.
     *
     * A view for "what should I work on", and it excludes unevidenced topics rather
     * than sorting them to one end — a topic with no recall rate has no place in an
     * ordering by recall rate.
     */
    val weakestFirst: List<TopicAbility>
        get() = topics
            .filter { it.recallRate != null }
            .sortedWith(compareBy({ it.recallRate }, { it.topic }))
}

/**
 * What is known about the learner's memory of one technique.
 *
 * The copy that renders this must be careful: the evidence base is *recall of
 * Problems the learner has already solved*, not fresh problem-solving ability. "Your
 * recall of Problems you have solved in this topic" is true; "your DP ability" is
 * not, and the label is the one place a true number can be turned into a lie.
 */
data class TopicAbility(
    val topic: String,
    val displayName: String,
    /**
     * Shrunk fraction of reviews recalled, or null below
     * [TopicMastery.MIN_TOPIC_REVIEWS]. Render null as "not enough practice yet",
     * never as 0%.
     */
    val recallRate: Double?,
    /**
     * The topic's own FSRS interval — how long this memory currently lasts. Render
     * with `formatIntervalDays`. Null before the topic has ever been scheduled.
     */
    val intervalDays: Double?,
    val stability: Double?,
    val reviews: Int,
    val lapses: Int,
    val memberProblems: Int,
    val attemptedMemberProblems: Int,
    val solvedMemberProblems: Int,
    val dueAt: Instant?,
    val isDue: Boolean,
    val lastPractisedAt: Instant?,
) {
    /**
     * Practised members over total members.
     *
     * Reported as a fraction with a visible numerator rather than folded into
     * [recallRate], for the reason [TopicMastery]'s header gives.
     */
    val coverageFraction: Float
        get() = if (memberProblems == 0) 0f else attemptedMemberProblems.toFloat() / memberProblems

    /** True when a recall rate is being withheld for want of evidence. */
    val hasEnoughEvidence: Boolean get() = recallRate != null
}
