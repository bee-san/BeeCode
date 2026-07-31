package dev.bee.beecode.design

import dev.bee.beecode.domain.ExecutionOutcome

/**
 * How one run outcome is presented: a headline, a glyph, and which accent to tint them.
 *
 * ## Why this is shared rather than written twice
 *
 * Both clients had their own `when (run.outcome)` mapping all seven outcomes onto a
 * headline and a colour, and they had already drifted — Android printed
 * `"${'$'}passed of ${'$'}total tests passed"` where desktop had the same string with a
 * different tint chain, and each had its own copy of the wording a learner reads after
 * every single attempt. Two clients cannot be checked against each other while both
 * declare the mapping inline, so this holds it once and
 * `RunOutcomePresentationTest` asserts the properties that matter.
 *
 * The tint is an [Accent], not a colour: `:shared` has no UI types and, more usefully,
 * the *right* colour depends on the active [ThemeFamily] and scheme. The client resolves
 * the accent against its own palette, so this mapping stays true in all six schemes.
 *
 * @property headline what the learner reads. Every outcome gets its own, because the next
 *   action differs: a syntax error is a typo, a timeout is an algorithmic problem, and a
 *   worker failure is BeeCode's fault rather than theirs.
 * @property glyph the non-colour cue — see [BeeCodeAccentGlyphs].
 * @property accent which semantic accent tints both.
 */
data class RunOutcomePresentation(
    val headline: String,
    val glyph: String,
    val accent: Accent,
) {
    /** Which of the palette's four semantic accents an outcome uses. */
    enum class Accent { SUCCESS, CAUTION, DANGER, MUTED }

    companion object {
        /**
         * Describe [outcome] for a learner.
         *
         * @param passedTestCount how many tests passed. Only read for
         *   [ExecutionOutcome.FAILED], where the count *is* the headline.
         * @param totalTestCount how many tests ran.
         */
        fun of(
            outcome: ExecutionOutcome,
            passedTestCount: Int = 0,
            totalTestCount: Int = 0,
        ): RunOutcomePresentation = when (outcome) {
            ExecutionOutcome.PASSED ->
                RunOutcomePresentation("All tests passed", BeeCodeAccentGlyphs.Success, Accent.SUCCESS)

            // A partial pass is caution rather than danger: the learner has working code
            // and a specific gap, which is a different situation from code that will not
            // run at all, and colouring both red says otherwise.
            ExecutionOutcome.FAILED -> RunOutcomePresentation(
                "$passedTestCount of $totalTestCount tests passed",
                BeeCodeAccentGlyphs.Caution,
                Accent.CAUTION,
            )

            ExecutionOutcome.SYNTAX_ERROR -> RunOutcomePresentation(
                "Your code has a syntax error",
                BeeCodeAccentGlyphs.Danger,
                Accent.DANGER,
            )

            ExecutionOutcome.RUNTIME_ERROR -> RunOutcomePresentation(
                "Your code raised an error",
                BeeCodeAccentGlyphs.Danger,
                Accent.DANGER,
            )

            // Caution, not danger. A timeout means the algorithm is too slow, which is a
            // solvable problem with correct-looking code — not a broken program.
            ExecutionOutcome.TIMEOUT -> RunOutcomePresentation(
                "Your code ran out of time",
                BeeCodeAccentGlyphs.Caution,
                Accent.CAUTION,
            )

            // Muted, and never danger: the learner chose to stop. Marking their own
            // decision as an error is the app disagreeing with them about nothing.
            ExecutionOutcome.CANCELLED ->
                RunOutcomePresentation("Run stopped", BeeCodeAccentGlyphs.Muted, Accent.MUTED)

            // BeeCode's fault, not the learner's — see ExecutionOutcome.WORKER_FAILURE.
            // The wording says so; the accent is danger because something is genuinely
            // broken and the learner should not read it as a wrong answer.
            ExecutionOutcome.WORKER_FAILURE -> RunOutcomePresentation(
                "BeeCode could not run your code",
                BeeCodeAccentGlyphs.Danger,
                Accent.DANGER,
            )
        }
    }
}

/** The ARGB value this presentation's accent takes in [palette]. */
fun RunOutcomePresentation.tint(palette: BeeCodePalette): Long = when (accent) {
    RunOutcomePresentation.Accent.SUCCESS -> palette.accentSuccess
    RunOutcomePresentation.Accent.CAUTION -> palette.accentCaution
    RunOutcomePresentation.Accent.DANGER -> palette.accentDanger
    RunOutcomePresentation.Accent.MUTED -> palette.accentMuted
}
