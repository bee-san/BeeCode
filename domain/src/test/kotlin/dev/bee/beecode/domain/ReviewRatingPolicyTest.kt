package dev.bee.beecode.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The full evidence-to-rating matrix.
 *
 * This is the product's honesty rule, so it is covered exhaustively rather than
 * by example: every outcome is asserted in both the aided and unaided case, and
 * the test fails if a new [ExecutionOutcome] is added without a decision.
 */
class ReviewRatingPolicyTest {
    @Test
    fun unaidedPassPermitsEveryRating() {
        val permitted = ReviewRatingPolicy.permittedRatings(run(outcome = ExecutionOutcome.PASSED), aided = false)
        assertEquals(ReviewRating.entries.toSet(), permitted)
    }

    @Test
    fun aidedPassIsCappedAtHard() {
        // A pass after reading the answer is recognition, not recall. GOOD and
        // EASY would let a revealed answer extend the interval as if it had been
        // remembered.
        val permitted = ReviewRatingPolicy.permittedRatings(run(outcome = ExecutionOutcome.PASSED), aided = true)
        assertEquals(setOf(ReviewRating.AGAIN, ReviewRating.HARD), permitted)
    }

    @Test
    fun anyNonPassPermitsOnlyAgain() {
        // Offering HARD for a failure would let a learner soften a real lapse and
        // stop the schedule reacting to it.
        for (outcome in listOf(
            ExecutionOutcome.FAILED,
            ExecutionOutcome.SYNTAX_ERROR,
            ExecutionOutcome.RUNTIME_ERROR,
            ExecutionOutcome.TIMEOUT,
        )) {
            for (aided in listOf(false, true)) {
                assertEquals(
                    setOf(ReviewRating.AGAIN),
                    ReviewRatingPolicy.permittedRatings(run(outcome = outcome), aided),
                    "outcome=$outcome aided=$aided",
                )
            }
        }
    }

    @Test
    fun nonEvidenceOutcomesPermitNoRatingAtAll() {
        // Cancellation is the learner's choice and a worker failure is our bug.
        // Neither may become a review, in either aided state.
        for (outcome in listOf(ExecutionOutcome.CANCELLED, ExecutionOutcome.WORKER_FAILURE)) {
            for (aided in listOf(false, true)) {
                assertTrue(
                    ReviewRatingPolicy.permittedRatings(run(outcome = outcome), aided).isEmpty(),
                    "outcome=$outcome aided=$aided must permit no rating",
                )
            }
        }
    }

    @Test
    fun everyOutcomeIsClassifiedAsEvidenceOrNot() {
        // Guards against a new outcome kind being added and silently defaulting.
        val evidence = ExecutionOutcome.entries.filter { it.isEvidence() }
        val notEvidence = ExecutionOutcome.entries.filterNot { it.isEvidence() }
        assertEquals(5, evidence.size, "expected exactly 5 evidence outcomes, got $evidence")
        assertEquals(2, notEvidence.size, "expected exactly 2 non-evidence outcomes, got $notEvidence")
    }

    @Test
    fun defaultRatingIsTheBestHonestOption() {
        assertEquals(
            ReviewRating.GOOD,
            ReviewRatingPolicy.defaultRating(run(outcome = ExecutionOutcome.PASSED), aided = false),
        )
        assertEquals(
            ReviewRating.HARD,
            ReviewRatingPolicy.defaultRating(run(outcome = ExecutionOutcome.PASSED), aided = true),
        )
        assertEquals(
            ReviewRating.AGAIN,
            ReviewRatingPolicy.defaultRating(run(outcome = ExecutionOutcome.FAILED), aided = false),
        )
        assertEquals(
            null,
            ReviewRatingPolicy.defaultRating(run(outcome = ExecutionOutcome.CANCELLED), aided = false),
        )
    }

    @Test
    fun defaultRatingIsAlwaysItselfPermitted() {
        for (outcome in ExecutionOutcome.entries) {
            for (aided in listOf(false, true)) {
                val r = run(outcome = outcome)
                val default = ReviewRatingPolicy.defaultRating(r, aided)
                val permitted = ReviewRatingPolicy.permittedRatings(r, aided)
                if (default == null) {
                    assertTrue(permitted.isEmpty(), "outcome=$outcome aided=$aided")
                } else {
                    assertTrue(default in permitted, "outcome=$outcome aided=$aided default=$default")
                }
            }
        }
    }
}
