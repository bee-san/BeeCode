package dev.bee.beecode.domain

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ReviewSessionTest {
    @Test
    fun aNewSessionIsStartedAndUnaided() {
        val s = session()
        assertEquals(ReviewSessionState.STARTED, s.state)
        assertFalse(s.aided)
        assertNull(s.latestRun)
        assertNull(s.finalizedAt)
        assertFalse(s.hasPassingRun)
    }

    @Test
    fun aFailingRunMovesTheSessionToWorking() {
        val s = session().recordRun(run(outcome = ExecutionOutcome.FAILED))
        assertEquals(ReviewSessionState.WORKING, s.state)
        assertFalse(s.hasPassingRun)
    }

    @Test
    fun aPassingRunMovesTheSessionToPassed() {
        val s = session().recordRun(run(outcome = ExecutionOutcome.PASSED))
        assertEquals(ReviewSessionState.PASSED, s.state)
        assertTrue(s.hasPassingRun)
    }

    @Test
    fun theFailThenFixJourneyEndsPassed() {
        // The core Test 1 journey: one intentional failure, then a correction.
        val s = session()
            .recordRun(run(id = "run-1", outcome = ExecutionOutcome.FAILED))
            .recordRun(run(id = "run-2", outcome = ExecutionOutcome.PASSED))
        assertEquals(ReviewSessionState.PASSED, s.state)
        assertEquals(2, s.runs.size)
        assertEquals(ExecutionRunId("run-2"), s.latestRun?.id)
    }

    @Test
    fun aCancelledRunAfterAPassDoesNotUndoThePass() {
        // Cancelling an exploratory run must not cost the learner a pass they
        // already legitimately earned.
        val s = session()
            .recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED))
            .recordRun(run(id = "run-2", outcome = ExecutionOutcome.CANCELLED))
        assertEquals(ReviewSessionState.PASSED, s.state)
    }

    @Test
    fun aWorkerFailureAfterAPassDoesNotUndoThePass() {
        val s = session()
            .recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED))
            .recordRun(run(id = "run-2", outcome = ExecutionOutcome.WORKER_FAILURE))
        assertEquals(ReviewSessionState.PASSED, s.state)
    }

    @Test
    fun aRealFailureAfterAPassReturnsTheSessionToWorking() {
        // The learner broke working code. The session should reflect the current
        // truth, though the earlier passing run remains selectable.
        val s = session()
            .recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED))
            .recordRun(run(id = "run-2", outcome = ExecutionOutcome.FAILED))
        assertEquals(ReviewSessionState.WORKING, s.state)
        assertTrue(s.hasPassingRun)
    }

    @Test
    fun cancelledAndWorkerFailureRunsAreNotSelectable() {
        val s = session()
            .recordRun(run(id = "run-1", outcome = ExecutionOutcome.CANCELLED))
            .recordRun(run(id = "run-2", outcome = ExecutionOutcome.WORKER_FAILURE))
            .recordRun(run(id = "run-3", outcome = ExecutionOutcome.TIMEOUT))
        assertEquals(listOf(ExecutionRunId("run-3")), s.selectableRuns.map { it.id })
    }

    @Test
    fun revealLatchesAndIsIdempotent() {
        val s = session().reveal()
        assertTrue(s.aided)
        assertTrue(s.reveal().aided, "reveal must stay latched")
    }

    @Test
    fun recordingTheSameRunTwiceIsRejected() {
        val s = session().recordRun(run(id = "run-1"))
        assertFailsWith<IllegalArgumentException> { s.recordRun(run(id = "run-1")) }
    }

    @Test
    fun aRunForAnotherProblemIsRejected() {
        assertFailsWith<IllegalArgumentException> {
            session().recordRun(run(problemId = "valid-parentheses"))
        }
    }

    @Test
    fun aRunAgainstAnotherRevisionIsRejected() {
        // Content changed underneath the learner; the run judged different tests.
        assertFailsWith<IllegalArgumentException> {
            session().recordRun(run(revision = ProblemRevisionId("b".repeat(64))))
        }
    }

    @Test
    fun anUnaidedPassPlansAsSolved() {
        val s = session().recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED))
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        assertTrue(plan.countsAsSolved)
        assertFalse(plan.aided)
        assertEquals(ReviewRating.GOOD, plan.rating)
    }

    @Test
    fun anAidedPassDoesNotCountAsSolved() {
        // This is what stops the 5am Club being farmed by revealing the answer.
        val s = session().reveal().recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED))
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.HARD)
        assertFalse(plan.countsAsSolved)
        assertTrue(plan.aided)
    }

    @Test
    fun revealingAfterPassingStillDowngradesTheSession() {
        // Passing first and reading the explanation afterwards is legitimate, but
        // it cannot retain an unaided grade.
        val s = session().recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED)).reveal()
        assertFailsWith<IllegalArgumentException> {
            s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        }
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.HARD)
        assertFalse(plan.countsAsSolved)
    }

    @Test
    fun aFailedRunCannotBeRatedBetterThanAgain() {
        val s = session().recordRun(run(id = "run-1", outcome = ExecutionOutcome.FAILED))
        assertFailsWith<IllegalArgumentException> {
            s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        }
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.AGAIN)
        assertFalse(plan.countsAsSolved)
    }

    @Test
    fun anEarlierPassingRunMayBeDeliberatelyFinalized() {
        // The learner passed, kept experimenting, broke it, and finalizes the
        // attempt that actually worked.
        val s = session()
            .recordRun(run(id = "run-1", outcome = ExecutionOutcome.PASSED))
            .recordRun(run(id = "run-2", outcome = ExecutionOutcome.FAILED))
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        assertEquals(ExecutionRunId("run-1"), plan.selectedRun.id)
        assertTrue(plan.countsAsSolved)
    }

    @Test
    fun theFinalizedPlanBindsTheExactSourceThatRan() {
        // Finalization must credit the source that produced the result, not
        // whatever the editor happens to contain now.
        val ran = "def two_sum(nums, target):\n    return [0, 1]\n"
        val s = session().recordRun(run(id = "run-1", source = ran))
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        assertEquals(ran, plan.selectedRun.source)
    }

    @Test
    fun aCancelledRunCannotBeFinalized() {
        val s = session().recordRun(run(id = "run-1", outcome = ExecutionOutcome.CANCELLED))
        assertFailsWith<IllegalArgumentException> {
            s.planFinalization(ExecutionRunId("run-1"), ReviewRating.AGAIN)
        }
    }

    @Test
    fun aWorkerFailureCannotBecomeALapse() {
        // BeeCode's own bug must never damage the learner's schedule.
        val s = session().recordRun(run(id = "run-1", outcome = ExecutionOutcome.WORKER_FAILURE))
        assertFailsWith<IllegalArgumentException> {
            s.planFinalization(ExecutionRunId("run-1"), ReviewRating.AGAIN)
        }
    }

    @Test
    fun anUnknownRunCannotBeFinalized() {
        val s = session().recordRun(run(id = "run-1"))
        assertFailsWith<IllegalArgumentException> {
            s.planFinalization(ExecutionRunId("run-9"), ReviewRating.GOOD)
        }
    }

    @Test
    fun finalizingTwiceIsRejected() {
        val s = session().recordRun(run(id = "run-1"))
        val plan = s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        val finalized = s.finalize(plan, T0)
        assertEquals(ReviewSessionState.FINALIZED, finalized.state)
        assertEquals(T0, finalized.finalizedAt)
        assertFailsWith<IllegalStateException> { finalized.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD) }
        assertFailsWith<IllegalStateException> { finalized.finalize(plan, T0) }
    }

    @Test
    fun aFinalizedSessionAcceptsNoFurtherActivity() {
        val s = session().recordRun(run(id = "run-1"))
        val finalized = s.finalize(s.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD), T0)
        assertFailsWith<IllegalStateException> { finalized.recordRun(run(id = "run-2")) }
        assertFailsWith<IllegalStateException> { finalized.reveal() }
    }

    @Test
    fun finalizingWithAnotherSessionsPlanIsRejected() {
        val a = session().recordRun(run(id = "run-1"))
        val plan = a.planFinalization(ExecutionRunId("run-1"), ReviewRating.GOOD)
        val b = ReviewSession.start(ReviewSessionId("session-2"), problem(), T0)
            .recordRun(run(id = "run-1"))
        assertFailsWith<IllegalArgumentException> { b.finalize(plan, T0) }
    }
}
