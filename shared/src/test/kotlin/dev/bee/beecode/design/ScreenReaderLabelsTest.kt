package dev.bee.beecode.design

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The spoken labels say something, say different things, and are short enough to hear.
 *
 * These assertions look almost too small to write, and the one that earns the file is
 * [aLabelNeverRepeatsTheGlyphItReplaces]: the whole point of a description on the ✓/✗ is
 * that a reader announces words instead of a character it may name badly or skip. A label
 * that contained the glyph would defeat that while looking correct in a diff.
 */
class ScreenReaderLabelsTest {

    @Test
    fun aTestCaseVerdictDistinguishesPassFromFail() {
        // The row's name is identical either way, so these two strings are the only thing
        // a screen-reader user has to go on.
        assertEquals("Passed", ScreenReaderLabels.testCase(passed = true))
        assertEquals("Failed", ScreenReaderLabels.testCase(passed = false))
        assertNotEquals(
            ScreenReaderLabels.testCase(passed = true),
            ScreenReaderLabels.testCase(passed = false),
        )
    }

    @Test
    fun anAchievementSaysWhetherItIsEarnedRatherThanWhetherItIsLocked() {
        assertEquals("Earned", ScreenReaderLabels.achievement(earned = true))
        // Not "Locked": nothing in BeeCode is locked. Every Problem is available from the
        // first launch, and the padlock is a visual convention for incomplete rather than
        // a statement about access — saying otherwise tells a learner something false.
        assertEquals("Not yet earned", ScreenReaderLabels.achievement(earned = false))
        assertFalse(
            ScreenReaderLabels.achievement(earned = false).contains("lock", ignoreCase = true),
            "the unearned label must not claim anything is locked",
        )
    }

    @Test
    fun aLabelNeverRepeatsTheGlyphItReplaces() {
        // A description containing "✓" would put the reader back where it started: some
        // readers name the character, some skip it, and none of them say "passed".
        val glyphs = listOf(
            BeeCodeAccentGlyphs.Success,
            BeeCodeAccentGlyphs.Caution,
            BeeCodeAccentGlyphs.Danger,
            BeeCodeAccentGlyphs.Muted,
        )
        for (label in everyLabel()) {
            for (glyph in glyphs) {
                assertFalse(
                    label.contains(glyph),
                    "\"$label\" contains the glyph \"$glyph\" it is meant to replace",
                )
            }
        }
    }

    @Test
    fun everyLabelIsSpeakableAndShort() {
        for (label in everyLabel()) {
            assertNotEquals("", label.trim(), "an empty description is worse than none: " +
                "the node stays in the tree and announces nothing")
            // Read out once per row, in lists that run to fifteen tests. "This test
            // passed" is three words of preamble times fifteen.
            assertTrue(
                label.split(" ").size <= 3,
                "\"$label\" is too long to hear repeated down a list",
            )
            assertEquals(label.trim(), label, "\"$label\" has surrounding whitespace")
        }
    }

    private fun everyLabel(): List<String> = listOf(
        ScreenReaderLabels.testCase(passed = true),
        ScreenReaderLabels.testCase(passed = false),
        ScreenReaderLabels.achievement(earned = true),
        ScreenReaderLabels.achievement(earned = false),
    )
}
