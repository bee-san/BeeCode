package dev.bee.beecode.fsrs

import dev.bee.fsrs.FsrsAlgorithmInfo
import dev.bee.fsrs.FsrsParameters
import kotlin.test.Test
import kotlin.test.assertEquals
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
 * 38-vector fixture does that. They are testing that BeeCode is scheduling with the
 * mathematics it says it is, because every stored schedule transition records these
 * values and a learner's history is only interpretable if they are true.
 */
class FsrsProvenanceTest {

    @Test
    fun theVendoredEngineIsTheVersionBeeCodeClaims() {
        // Recorded in every transition, so it must match what fsrs-adapter writes.
        assertEquals("bee-fsrs-0.1.0", BeeCodeScheduler.ENGINE_VERSION)
    }

    @Test
    fun theAlgorithmIsTheFsrs6TwentyOneParameterSnapshot() {
        // Deliberately FSRS-6.x, not "FSRS 7". Upstream py-fsrs has published no v7,
        // and kanji_anki's own planning notes say the label should be the FSRS-6
        // family unless upstream says otherwise. If upstream ever does publish a v7,
        // this test is what forces the change to be a decision rather than a drift.
        assertEquals("FSRS-6.x 21-parameter snapshot", FsrsAlgorithmInfo.ALGORITHM_LABEL)
        assertEquals(21, FsrsAlgorithmInfo.PARAMETER_COUNT)
        assertEquals(21, FsrsParameters.PARAMETER_COUNT)
    }

    @Test
    fun theUpstreamSourceIsPinnedExactly() {
        // Commit and blob, not just a tag: a tag can be moved, a commit cannot.
        assertEquals("open-spaced-repetition/py-fsrs", FsrsAlgorithmInfo.UPSTREAM_REPOSITORY)
        assertEquals("v6.3.1", FsrsAlgorithmInfo.UPSTREAM_RELEASE)
        assertEquals(
            "3abe686e9c058d3f3c00bbeb92e68b71211b2b31",
            FsrsAlgorithmInfo.UPSTREAM_COMMIT,
        )
        assertEquals(
            "6d42ecb259bbaaa02101f13c5e1b2ec7cdc77eae",
            FsrsAlgorithmInfo.UPSTREAM_SCHEDULER_BLOB,
        )
    }

    @Test
    fun theDefaultParametersAreExactlyTheExpectedTwentyOneValues() {
        // The most consequential possible regression: changing a parameter silently
        // reschedules every existing learner's entire queue. Pinned by value.
        val expected = doubleArrayOf(
            0.212, 1.2931, 2.3065, 8.2956, 6.4133,
            0.8334, 3.0194, 0.001, 1.8722, 0.1666,
            0.796, 1.4835, 0.0614, 0.2629, 1.6483,
            0.6014, 1.8729, 0.5425, 0.0912, 0.0658,
            0.1542,
        )
        val actual = FsrsDefaults.parameters()
        assertEquals(expected.size, actual.size)
        expected.forEachIndexed { index, value ->
            assertEquals(value, actual[index], 0.0, "FSRS parameter $index")
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
        assertEquals(FsrsAlgorithmInfo.ALGORITHM_LABEL, transition.record.algorithmId)
        assertEquals(BeeCodeScheduler.ENGINE_VERSION, transition.record.engineVersion)
        assertTrue(transition.record.parametersHash.isNotEmpty())
    }

    @Test
    fun theUpstreamReferenceReadsAsAUsableCitation() {
        // Surfaced in the UI and in exports, so it has to be legible to a human
        // trying to reproduce a schedule years later.
        val citation = FsrsAlgorithmInfo.upstreamReference()
        assertTrue(citation.contains("py-fsrs"), citation)
        assertTrue(citation.contains("v6.3.1"), citation)
        assertEquals(FsrsDefaults.upstreamReference, citation)
    }
}
