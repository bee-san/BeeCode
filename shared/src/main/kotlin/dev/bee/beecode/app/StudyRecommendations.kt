package dev.bee.beecode.app

import dev.bee.beecode.domain.ProblemDefinition

/**
 * Ranks new Problems by what the learner would benefit from practising next.
 *
 * Recall and coverage stay separate, just as they do in [TopicMastery]. A Problem can
 * be recommended because recall is weak, because its topic is completely new, or
 * because the learner has only sampled a small part of that topic. Calling all three
 * "mastery" would turn unlike evidence into one plausible-looking score.
 */
object StudyRecommendations {

    fun rank(
        newProblems: List<ProblemDefinition>,
        mastery: TopicMasteryProjection,
    ): List<ProblemRecommendation> {
        val abilities = mastery.topics.associateBy { it.topic }
        val ranked = newProblems.map { problem ->
            val focus = problem.topics
                .distinct()
                .mapNotNull(abilities::get)
                .minWithOrNull(topicComparator)

            ProblemRecommendation(
                problem = problem,
                focus = focus,
                reason = focus.reason(),
            )
        }.sortedWith(recommendationComparator)

        // The first cards should not all teach the same thing. Keep the pedagogical
        // order, but defer repeated focus topics until every available focus has had
        // one representative.
        val firstByTopic = mutableListOf<ProblemRecommendation>()
        val repeatedTopics = mutableListOf<ProblemRecommendation>()
        val seenTopics = mutableSetOf<String>()
        for (recommendation in ranked) {
            val topic = recommendation.focus?.topic
            if (topic == null || seenTopics.add(topic)) {
                firstByTopic += recommendation
            } else {
                repeatedTopics += recommendation
            }
        }
        return firstByTopic + repeatedTopics
    }

    private fun TopicAbility?.reason(): RecommendationReason = when {
        this == null || reviews == 0 -> RecommendationReason.NEW_TOPIC
        recallRate != null && recallRate < WEAK_RECALL_THRESHOLD ->
            RecommendationReason.NEEDS_RECALL_PRACTICE
        else -> RecommendationReason.BUILD_COVERAGE
    }

    private val topicComparator = compareBy<TopicAbility>(
        { it.reason().priority },
        {
            when (it.reason()) {
                RecommendationReason.NEEDS_RECALL_PRACTICE -> it.recallRate ?: 1.0
                RecommendationReason.NEW_TOPIC -> -it.memberProblems.toDouble()
                RecommendationReason.BUILD_COVERAGE -> it.coverageFraction.toDouble()
            }
        },
        { it.topic },
    )

    private val recommendationComparator = compareBy<ProblemRecommendation>(
        { it.reason.priority },
        {
            when (it.reason) {
                RecommendationReason.NEEDS_RECALL_PRACTICE ->
                    it.focus?.recallRate ?: 1.0
                RecommendationReason.NEW_TOPIC ->
                    -(it.focus?.memberProblems ?: 0).toDouble()
                RecommendationReason.BUILD_COVERAGE ->
                    it.focus?.coverageFraction?.toDouble() ?: 1.0
            }
        },
        { it.problem.difficulty.ordinal },
        { it.problem.id.value },
    )

    /**
     * Below 80% successful recall, a practised topic is useful to revisit.
     *
     * This is recommendation policy, not an FSRS input and not a mastery boundary.
     * The exact recall rate remains visible so the learner can judge the evidence.
     */
    const val WEAK_RECALL_THRESHOLD: Double = 0.8
}

data class ProblemRecommendation(
    val problem: ProblemDefinition,
    val focus: TopicAbility?,
    val reason: RecommendationReason,
) {
    val focusName: String
        get() = focus?.displayName
            ?: problem.topics.firstOrNull()?.let(TopicMastery::displayName)
            ?: "Foundations"

    val progressFraction: Float
        get() = when (reason) {
            RecommendationReason.NEEDS_RECALL_PRACTICE ->
                focus?.recallRate?.toFloat() ?: 0f
            RecommendationReason.NEW_TOPIC,
            RecommendationReason.BUILD_COVERAGE,
            -> focus?.coverageFraction ?: 0f
        }
}

enum class RecommendationReason(internal val priority: Int) {
    NEEDS_RECALL_PRACTICE(0),
    NEW_TOPIC(1),
    BUILD_COVERAGE(2),
}
