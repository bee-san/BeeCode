package dev.bee.beecode.desktop

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * The editor's text transformations.
 *
 * Worth testing rather than eyeballing: Python's indentation is syntactically
 * significant, so an off-by-one here produces code that does not run and a learner
 * who blames themselves.
 */
class EditorEditsTest {

    @Test
    fun tabInsertsFourSpacesAtTheCaret() {
        val edit = EditorEdits.insert("def f():\n", 9, 9, EditorEdits.INDENT)
        assertEquals("def f():\n    ", edit.text)
        assertEquals(13, edit.caret)
    }

    @Test
    fun insertReplacesASelection() {
        val edit = EditorEdits.insert("abcdef", 1, 4, "X")
        assertEquals("aXef", edit.text)
        assertEquals(2, edit.caret)
    }

    @Test
    fun enterCarriesTheCurrentIndentation() {
        val text = "def f():\n    x = 1"
        val edit = EditorEdits.newlineWithIndent(text, text.length)
        assertEquals("def f():\n    x = 1\n    ", edit.text)
        assertEquals(edit.text.length, edit.caret)
    }

    @Test
    fun enterAddsALevelAfterAColon() {
        // The behaviour that saves the most keystrokes: a block opener indents.
        val text = "def f():"
        val edit = EditorEdits.newlineWithIndent(text, text.length)
        assertEquals("def f():\n    ", edit.text)
    }

    @Test
    fun enterAddsALevelAfterANestedColon() {
        val text = "def f():\n    if x:"
        val edit = EditorEdits.newlineWithIndent(text, text.length)
        assertEquals("def f():\n    if x:\n        ", edit.text)
    }

    @Test
    fun enterAtTheStartOfTheBufferAddsNoIndent() {
        val edit = EditorEdits.newlineWithIndent("", 0)
        assertEquals("\n", edit.text)
        assertEquals(1, edit.caret)
    }

    @Test
    fun enterIgnoresTrailingSpacesWhenDetectingAColon() {
        val text = "for i in range(3):   "
        val edit = EditorEdits.newlineWithIndent(text, text.length)
        assertEquals("for i in range(3):   \n    ", edit.text)
    }

    @Test
    fun enterInTheMiddleOfALineSplitsItWithIndentation() {
        val text = "    return a + b"
        // Caret immediately after the "+", so the split is "    return a +" and " b".
        val edit = EditorEdits.newlineWithIndent(text, 14)
        // The remainder keeps its own leading space and gains the line's indent.
        assertEquals("    return a +\n     b", edit.text)
    }

    @Test
    fun backspaceRemovesAWholeIndentLevel() {
        val text = "def f():\n        pass"
        // Caret at the start of "pass", inside eight spaces of indentation.
        val edit = EditorEdits.dedent(text, 17)
        assertEquals("def f():\n    pass", edit!!.text)
        assertEquals(13, edit.caret)
    }

    @Test
    fun backspaceFallsThroughWhenNotInLeadingWhitespace() {
        // Mid-word: ordinary backspace should apply, so this returns null rather
        // than reimplementing character deletion.
        assertNull(EditorEdits.dedent("def f():\n    pass", 20))
    }

    @Test
    fun backspaceFallsThroughOnAPartialIndent() {
        // Two spaces is not a full level. Silently swallowing 1-3 spaces would be
        // more surprising than deleting one.
        assertNull(EditorEdits.dedent("def f():\n  pass", 11))
    }

    @Test
    fun backspaceFallsThroughAtTheStartOfTheBuffer() {
        assertNull(EditorEdits.dedent("hello", 0))
    }

    @Test
    fun backspaceDedentsOnTheFirstLineToo() {
        // No preceding newline, so the line-start calculation has to handle index 0.
        val edit = EditorEdits.dedent("        x", 8)
        assertEquals("    x", edit!!.text)
        assertEquals(4, edit.caret)
    }

    @Test
    fun editsClampOutOfRangePositions() {
        // Defensive: a caret past the end must not throw, because the composable's
        // state and the incoming source can briefly disagree during a reset.
        val edit = EditorEdits.insert("abc", 99, 99, "X")
        assertEquals("abcX", edit.text)
        assertEquals(EditorEdits.newlineWithIndent("abc", 99).caret, 4)
        assertNull(EditorEdits.dedent("abc", -5))
    }

    @Test
    fun aRealisticSolutionTypesOutCorrectly() {
        // Compose the operations the way a learner actually would: type a def, press
        // Enter, type a body line, press Enter, and dedent. The result must be valid
        // Python.
        var text = "def two_sum(nums, target):"
        var caret = text.length

        EditorEdits.newlineWithIndent(text, caret).let { text = it.text; caret = it.caret }
        assertEquals("def two_sum(nums, target):\n    ", text)

        EditorEdits.insert(text, caret, caret, "seen = {}").let { text = it.text; caret = it.caret }
        EditorEdits.newlineWithIndent(text, caret).let { text = it.text; caret = it.caret }
        assertEquals("def two_sum(nums, target):\n    seen = {}\n    ", text)

        EditorEdits.insert(text, caret, caret, "for i, n in enumerate(nums):")
            .let { text = it.text; caret = it.caret }
        EditorEdits.newlineWithIndent(text, caret).let { text = it.text; caret = it.caret }
        // The nested block indented by itself.
        assertEquals(
            "def two_sum(nums, target):\n    seen = {}\n    for i, n in enumerate(nums):\n        ",
            text,
        )

        // Dedent back out of the loop body.
        EditorEdits.dedent(text, caret)!!.let { text = it.text; caret = it.caret }
        assertEquals(
            "def two_sum(nums, target):\n    seen = {}\n    for i, n in enumerate(nums):\n    ",
            text,
        )
    }
}
