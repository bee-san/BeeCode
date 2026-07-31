package dev.bee.beecode.design

/**
 * What a screen reader says where a sighted learner reads a shape or a colour.
 *
 * ## Which icons get a label, and which must not
 *
 * The rule is not "describe every icon" — it is the opposite. An icon that sits beside its
 * own text label carries nothing, and describing it makes a screen reader announce every
 * destination twice: "Study, Study. Progress, Progress." Auditing both clients, that covers
 * almost everything they draw: the nav rail and nav bar items, the theme chips' leading
 * icons, the schedule card's clock, the leech flame, and the brand mark in the rail header
 * are each adjacent to text that already says it, so they stay `contentDescription = null`
 * with the reason stated at the call site.
 *
 * Two places were left where the announcement was wrong rather than merely redundant, and
 * those are the ones this object serves:
 *
 * - **A per-test row's ✓ or ✗.** The glyph is the *only* thing distinguishing a passing
 *   test from a failing one — the rest of the row is the test's name, which is identical
 *   either way. Left bare, a reader announces the character: TalkBack says "check mark",
 *   VoiceOver says "check mark" or nothing at all depending on verbosity, and "✗" is
 *   variously "multiplication x", "ballot x", or silence. A learner would hear a list of
 *   test names with no verdict attached to any of them.
 * - **An achievement's star or lock.** [dev.bee.beecode.app.AchievementState.detail] gives
 *   a count — "3 of 7 days" — which is progress, not status, and at "7 of 7 days" it does
 *   not distinguish earned from about to be. Earned state is carried by a filled star in
 *   the app's amber against an outlined lock in a muted grey: shape and colour, no words.
 *
 * Both are read out per row, so both are deliberately terse. "Passed" rather than "This
 * test passed": in a list of fifteen, the preamble is fifteen wasted announcements.
 *
 * ## Why these live in `:shared`
 *
 * Same reason as [BeeCodeAccentGlyphs] and [RunOutcomePresentation]: two clients cannot be
 * checked against each other while both spell the string inline, and this is text a learner
 * hears on every attempt. `ScreenReaderLabelsTest` pins the properties that matter.
 */
object ScreenReaderLabels {

    /**
     * The verdict on one test case, for the row's ✓/✗ glyph.
     *
     * Replaces the glyph in the accessibility tree rather than adding to it — otherwise the
     * reader announces both the description and the character it describes.
     */
    fun testCase(passed: Boolean): String = if (passed) "Passed" else "Failed"

    /**
     * Whether an achievement is earned, for its star/lock marker.
     *
     * "Not yet earned" rather than "Locked": nothing in BeeCode is locked — every Problem
     * is available from the first launch, and the padlock is a visual convention for
     * incomplete, not a statement about access.
     */
    fun achievement(earned: Boolean): String = if (earned) "Earned" else "Not yet earned"
}
