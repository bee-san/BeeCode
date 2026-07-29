package dev.bee.beecode.domain

import kotlinx.datetime.Instant

/**
 * Deterministic fixtures. Nothing here reads a clock or generates randomness, so
 * every domain test is reproducible.
 */

val T0: Instant = Instant.parse("2026-07-29T12:00:00Z")

val TEST_REVISION: ProblemRevisionId = ProblemRevisionId("a".repeat(64))

fun problem(
    id: String = "two-sum",
    revision: ProblemRevisionId = TEST_REVISION,
    explanation: String? = "Use a hash map.",
    tests: List<ProblemTest> = listOf(test("example"), test("hidden-case", hidden = true)),
): ProblemDefinition = ProblemDefinition(
    id = ProblemId(id),
    revisionId = revision,
    title = "Two Sum",
    difficulty = ProblemDifficulty.EASY,
    topics = listOf("arrays", "hash-map"),
    statementMarkdown = "Return indices of the two numbers adding to the target.",
    starterSource = "def two_sum(nums, target):\n    pass\n",
    entryPoint = "two_sum",
    examples = listOf(ProblemExample("[2,7,11,15], 9", "[0,1]", null)),
    tests = tests,
    limits = ExecutionLimits.DEFAULT,
    explanationMarkdown = explanation,
)

fun test(name: String, hidden: Boolean = false): ProblemTest = ProblemTest(
    name = name,
    argumentsJson = "[[2,7,11,15],9]",
    expectedJson = "[0,1]",
    comparatorId = ComparatorId.EXACT,
    hidden = hidden,
)

fun run(
    id: String = "run-1",
    outcome: ExecutionOutcome = ExecutionOutcome.PASSED,
    problemId: String = "two-sum",
    revision: ProblemRevisionId = TEST_REVISION,
    source: String = "def two_sum(nums, target):\n    return [0, 1]\n",
    startedAt: Instant = T0,
): ExecutionRun {
    // Test results must stay consistent with the outcome, because ExecutionRun
    // rejects a PASSED run with a failing test and vice versa.
    val results = when (outcome) {
        ExecutionOutcome.PASSED -> listOf(caseResult("example", true), caseResult("hidden-case", true))
        ExecutionOutcome.FAILED -> listOf(caseResult("example", true), caseResult("hidden-case", false))
        else -> emptyList()
    }
    return ExecutionRun(
        id = ExecutionRunId(id),
        problemId = ProblemId(problemId),
        problemRevisionId = revision,
        source = source,
        outcome = outcome,
        testResults = results,
        output = "",
        outputTruncated = false,
        durationMillis = 12,
        startedAt = startedAt,
        runnerId = "test-runner",
        pythonVersion = "3.12.0",
    )
}

fun caseResult(name: String, passed: Boolean): TestCaseResult = TestCaseResult(
    name = name,
    passed = passed,
    hidden = false,
    expectedJson = "[0,1]",
    actualJson = if (passed) "[0,1]" else "[1,0]",
    message = if (passed) null else "expected [0,1] but got [1,0]",
    durationMillis = 1,
)

fun session(problem: ProblemDefinition = problem()): ReviewSession =
    ReviewSession.start(ReviewSessionId("session-1"), problem, T0)
