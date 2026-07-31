package dev.bee.beecode.app

import dev.bee.beecode.domain.ComparatorId
import dev.bee.beecode.domain.ExecutionLimits
import dev.bee.beecode.domain.ProblemDefinition
import dev.bee.beecode.domain.ProblemDifficulty
import dev.bee.beecode.domain.ProblemId
import dev.bee.beecode.domain.ProblemRevisionId
import dev.bee.beecode.domain.ProblemTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class StudyRecommendationsTest {

    @Test
    fun weakRecallComesBeforeUnseenAndPartlyCoveredTopics() {
        val weak = problem("weak-problem", "dynamic-programming")
        val unseen = problem("unseen-problem", "graphs")
        val building = problem("building-problem", "arrays")
        val mastery = projection(
            ability("dynamic-programming", reviews = 10, recallRate = 0.6, attempted = 4),
            ability("graphs", reviews = 0, recallRate = null, attempted = 0),
            ability("arrays", reviews = 8, recallRate = 0.9, attempted = 2),
        )

        val ranked = StudyRecommendations.rank(listOf(building, unseen, weak), mastery)

        assertEquals(
            listOf(
                RecommendationReason.NEEDS_RECALL_PRACTICE,
                RecommendationReason.NEW_TOPIC,
                RecommendationReason.BUILD_COVERAGE,
            ),
            ranked.map { it.reason },
        )
    }

    @Test
    fun theFirstRecommendationsCoverDifferentFocusTopics() {
        val recommendations = StudyRecommendations.rank(
            newProblems = listOf(
                problem("arrays-a", "arrays"),
                problem("arrays-b", "arrays"),
                problem("graphs-a", "graphs"),
            ),
            mastery = projection(
                ability("arrays", reviews = 10, recallRate = 0.5, attempted = 2),
                ability("graphs", reviews = 10, recallRate = 0.6, attempted = 2),
            ),
        )

        assertEquals(listOf("arrays", "graphs", "arrays"), recommendations.map { it.focus?.topic })
    }

    @Test
    fun aMultiTopicProblemUsesItsWeakestSupportedTopicAsTheReason() {
        val recommendation = StudyRecommendations.rank(
            newProblems = listOf(problem("mixed", "arrays", "dynamic-programming")),
            mastery = projection(
                ability("arrays", reviews = 10, recallRate = 0.95, attempted = 8),
                ability("dynamic-programming", reviews = 10, recallRate = 0.55, attempted = 2),
            ),
        ).single()

        assertEquals("dynamic-programming", recommendation.focus?.topic)
        assertEquals(RecommendationReason.NEEDS_RECALL_PRACTICE, recommendation.reason)
        assertEquals(0.55f, recommendation.progressFraction)
    }

    @Test
    fun rankingIsDeterministicWhenThereIsNoHistory() {
        val problems = listOf(
            problem("z-problem", "arrays"),
            problem("a-problem", "graphs"),
        )
        val mastery = projection(
            ability("arrays", reviews = 0, recallRate = null, attempted = 0),
            ability("graphs", reviews = 0, recallRate = null, attempted = 0),
        )

        val first = StudyRecommendations.rank(problems, mastery)
        val second = StudyRecommendations.rank(problems.reversed(), mastery)

        assertEquals(first.map { it.problem.id }, second.map { it.problem.id })
        assertTrue(first.all { it.reason == RecommendationReason.NEW_TOPIC })
    }

    private fun projection(vararg abilities: TopicAbility) = TopicMasteryProjection(
        topics = abilities.toList(),
        globalRecallRate = 0.85,
    )

    private fun ability(
        topic: String,
        reviews: Int,
        recallRate: Double?,
        attempted: Int,
    ) = TopicAbility(
        topic = topic,
        displayName = TopicMastery.displayName(topic),
        recallRate = recallRate,
        intervalDays = if (reviews == 0) null else 3.0,
        stability = if (reviews == 0) null else 3.0,
        reviews = reviews,
        lapses = 0,
        memberProblems = 10,
        attemptedMemberProblems = attempted,
        solvedMemberProblems = attempted,
        dueAt = null,
        isDue = false,
        lastPractisedAt = null,
    )

    private fun problem(id: String, vararg topics: String) = ProblemDefinition(
        id = ProblemId(id),
        revisionId = ProblemRevisionId("0".repeat(64)),
        title = id,
        difficulty = ProblemDifficulty.MEDIUM,
        topics = topics.toList(),
        dataStructures = topics.take(1),
        algorithms = topics.drop(1),
        statementMarkdown = "Solve it.",
        starterSource = "def solve():\n    pass",
        entryPoint = "solve",
        examples = emptyList(),
        tests = listOf(
            ProblemTest(
                name = "example",
                argumentsJson = "[]",
                expectedJson = "null",
                comparatorId = ComparatorId.EXACT,
            ),
        ),
        limits = ExecutionLimits(
            wallClockMillis = 1_000,
            maxOutputBytes = 1_024,
            maxMemoryBytes = null,
        ),
        explanationMarkdown = null,
    )
}
