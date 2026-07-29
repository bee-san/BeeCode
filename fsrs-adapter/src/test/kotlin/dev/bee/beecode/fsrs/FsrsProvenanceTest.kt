package dev.bee.beecode.fsrs

import dev.bee.fsrs.Fsrs7AlgorithmInfo
import dev.bee.fsrs.Fsrs7Parameters
import dev.bee.fsrs.FsrsParameters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * Pins the identity of the vendored FSRS engine.
 *
 * `bee-fsrs/` is a vendored checkout of a release from
 * [`bee-san/bee-fsrs`](https://github.com/bee-san/bee-fsrs), and a vendored copy can
 * drift from the version it claims to be — someone edits it locally instead of
 * upstreaming, and nothing notices.
 *
 * These assertions are what notices. They are not testing FSRS; the engine's own
 * fixtures do that, with 38 FSRS-6 vectors and 384 FSRS-7 ones. They are testing that
 * BeeCode is scheduling with the mathematics it says it is, because every stored
 * schedule transition records these values and a learner's history is only
 * interpretable if they are true.
 *
 * BeeCode schedules with **FSRS-7**. An earlier version of this file asserted the
 * opposite — that the engine was FSRS-6 and specifically *not* the 35-parameter
 * FSRS-7 — which was true when written and is precisely the gate the adoption had to
 * come through. The checks are inverted here rather than deleted: that the two
 * algorithms are distinguishable by number is still the useful property, and it now
 * runs in the other direction.
 */
class FsrsProvenanceTest {

    @Test
    fun theVendoredEngineIsTheVersionBeeCodeClaims() {
        // Recorded in every transition, so it must match what fsrs-adapter writes.
        assertEquals("bee-fsrs-0.2.0", BeeCodeScheduler.ENGINE_VERSION)
    }

    @Test
    fun theAlgorithmIsTheFsrs7ThirtyFiveParameterSnapshot() {
        // FSRS-7, not FSRS-6. The parameter count is what distinguishes them, so it is
        // checked rather than asserted in prose: a swap that changed the label but not
        // the mathematics, or the reverse, fails here.
        assertEquals("FSRS-7 35-parameter snapshot", Fsrs7AlgorithmInfo.ALGORITHM_LABEL)
        assertEquals(35, Fsrs7AlgorithmInfo.PARAMETER_COUNT)
        assertEquals(35, Fsrs7Parameters.PARAMETER_COUNT)
        assertEquals(35, FsrsDefaults.PARAMETER_COUNT)
        assertEquals(35, SchedulerPolicy.PARAMETER_COUNT)
    }

    @Test
    fun theEngineIsNotTheTwentyOneParameterFsrs6() {
        // Stated as its own assertion because the difference is a number, not a label.
        // The FSRS-6 engine is still vendored — old rows must stay replayable — so
        // "BeeCode uses FSRS-7" is a claim about which engine the adapter reaches for,
        // and that is what this checks.
        assertEquals(21, FsrsParameters.PARAMETER_COUNT)
        assertNotEquals(FsrsParameters.PARAMETER_COUNT, Fsrs7Parameters.PARAMETER_COUNT)

        val fsrs6FirstFour = doubleArrayOf(0.212, 1.2931, 2.3065, 8.2956)
        val actual = FsrsDefaults.parameters()
        fsrs6FirstFour.forEachIndexed { index, fsrs6Value ->
            assertNotEquals(
                fsrs6Value,
                actual[index],
                "parameter $index matches FSRS-6's default; the adapter may have been " +
                    "pointed back at the older engine without updating ALGORITHM_LABEL",
            )
        }
    }

    @Test
    fun theUpstreamSourceIsPinnedExactly() {
        // Commit and blob, not a tag or a branch. FSRS-7 has no release to pin: it
        // lives in a research repository whose main moves, so "the FSRS-7 in
        // srs-benchmark" is not a reproducible statement without a hash.
        assertEquals("open-spaced-repetition/srs-benchmark", Fsrs7AlgorithmInfo.UPSTREAM_REPOSITORY)
        assertEquals(
            "70cc4387f573ff20b13ac9c106333a335c8a4cb8",
            Fsrs7AlgorithmInfo.UPSTREAM_COMMIT,
        )
        assertEquals(
            "33893c3fed0f7dbe28c2b55874a50d9b3fa77df5",
            Fsrs7AlgorithmInfo.UPSTREAM_MODEL_BLOB,
        )
        assertEquals("models/fsrs_v7.py", Fsrs7AlgorithmInfo.UPSTREAM_MODEL_PATH)
    }

    @Test
    fun theDefaultParametersAreExactlyTheExpectedThirtyFiveValues() {
        // The most consequential possible regression: changing a parameter silently
        // reschedules every existing learner's entire queue. Pinned by value, and
        // byte-exact to upstream's FSRS7.init_w.
        val expected = doubleArrayOf(
            // Initial stability
            0.041, 2.4175, 4.1283, 11.9709,
            // Difficulty
            5.6385, 0.4468, 3.262,
            // Stability, long-term
            2.3054, 0.1688, 1.3325, 0.3524, 0.0049, 0.7503, 0.0896, 0.6625, 1.3,
            // Stability, short-term
            0.882, 0.3072, 3.5875, 0.303, 0.0107, 0.2279, 2.6413, 0.5594, 1.3,
            // Long-short term transition
            2.5, 1.0,
            // Forgetting curve
            0.0723, 0.1634, 0.5, 0.9555, 0.2245, 0.6232, 0.1362, 0.3862,
        )
        val actual = FsrsDefaults.parameters()
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, value ->
            assertEquals(value, actual[index], 0.0, "FSRS-7 parameter $index")
        }
    }

    @Test
    fun theRecordedTransitionCarriesTheEngineIdentity() {
        // The audit is only useful if the identity actually reaches the stored row.
        val transition = BeeCodeScheduler().schedule(
            problemId = dev.bee.beecode.domain.ProblemId("two-sum"),
            previous = null,
            rating = dev.bee.beecode.domain.ReviewRating.GOOD,
            reviewedAt = kotlinx.datetime.Instant.parse("2026-07-29T12:00:00Z"),
        )
        assertEquals(Fsrs7AlgorithmInfo.ALGORITHM_LABEL, transition.record.algorithmId)
        assertEquals(BeeCodeScheduler.ENGINE_VERSION, transition.record.engineVersion)
        assertTrue(transition.record.parametersHash.isNotEmpty())
    }

    @Test
    fun theUpstreamReferenceReadsAsAUsableCitation() {
        // Surfaced in the UI and in exports, so it has to be legible to a human trying
        // to reproduce a schedule years later — which for FSRS-7 means carrying the
        // commit, since there is no version number to quote.
        val citation = Fsrs7AlgorithmInfo.upstreamReference()
        assertTrue(citation.contains("srs-benchmark"), citation)
        assertTrue(citation.contains("fsrs_v7.py"), citation)
        assertTrue(citation.contains(Fsrs7AlgorithmInfo.UPSTREAM_COMMIT), citation)
        assertEquals(FsrsDefaults.upstreamReference, citation)
    }
}
