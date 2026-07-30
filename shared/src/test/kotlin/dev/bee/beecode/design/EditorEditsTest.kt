package dev.bee.beecode.design

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
    fun tabWithABlockSelectedIndentsEveryLineItTouches() {
        // The destructive case. `insert` — which is what Tab used to call unconditionally
        // — would have returned "def f():\n    " here: the selected body replaced by four
        // spaces. The learner selects a loop body, presses Tab the way every editor
        // expects, and their code is gone.
        val text = "def f():\nx = 1\ny = 2"
        val edit = EditorEdits.indentBlock(text, start = 9, end = text.length)
        assertEquals("def f():\n    x = 1\n    y = 2", edit.text)
    }

    @Test
    fun anIndentedBlockStaysSelectedSoASecondTabAddsASecondLevel() {
        // The reason BlockEdit carries a selection rather than a caret. Losing the
        // selection after one Tab makes indenting by two levels impossible without
        // reselecting, which is the point at which a learner gives up and uses spaces.
        val text = "x = 1\ny = 2"
        val first = EditorEdits.indentBlock(text, 0, text.length)
        val second = EditorEdits.indentBlock(first.text, first.selectionStart, first.selectionEnd)
        assertEquals("        x = 1\n        y = 2", second.text)
    }

    @Test
    fun aPartialSelectionStillIndentsWholeLines() {
        // Selecting from the middle of one line to the middle of the next indents both in
        // full. Indenting from the selection's own edges would insert four spaces into the
        // middle of a statement, which is not what "indent this" means anywhere.
        val text = "alpha = 1\nbeta = 2"
        val edit = EditorEdits.indentBlock(text, start = 3, end = 12)
        assertEquals("    alpha = 1\n    beta = 2", edit.text)
        // And the returned selection covers the whole of both lines, since a selection
        // still clipped mid-line would no longer describe anything the learner can see.
        assertEquals(0, edit.selectionStart)
        assertEquals(edit.text.length, edit.selectionEnd)
    }

    @Test
    fun blankLinesInABlockAreNotIndented() {
        // Indenting whitespace-only lines leaves trailing spaces nobody asked for, and
        // flake8's W291/W293 complain about them.
        val text = "x = 1\n\ny = 2"
        val edit = EditorEdits.indentBlock(text, 0, text.length)
        assertEquals("    x = 1\n\n    y = 2", edit.text)
    }

    @Test
    fun shiftTabRemovesOneLevelFromEveryLine() {
        val text = "        x = 1\n        y = 2"
        val edit = EditorEdits.dedentBlock(text, 0, text.length)
        assertEquals("    x = 1\n    y = 2", edit.text)
    }

    @Test
    fun shiftTabWithNoSelectionDedentsTheLineTheCaretIsOn() {
        // Shift+Tab has no selection most of the time, and this is the case that used to
        // *indent*: `event.key` is `Key.Tab` whether or not Shift is held, so the dedent
        // shortcut fell through to the plain-insert branch and did the opposite of its name.
        val text = "def f():\n        pass"
        // A collapsed range inside "pass" — start == end, as a caret is.
        val edit = EditorEdits.dedentBlock(text, 20, 20)
        assertEquals("def f():\n    pass", edit.text)
    }

    @Test
    fun dedentingAtTheLeftMarginChangesNothing() {
        // No exception, no borrowed characters from the line above. A learner holding
        // Shift+Tab must simply arrive at the margin and stop.
        val text = "x = 1\ny = 2"
        val edit = EditorEdits.dedentBlock(text, 0, text.length)
        assertEquals(text, edit.text)
    }

    @Test
    fun aRaggedBlockConvergesOnTheLeftMarginRatherThanStayingRagged() {
        // Two spaces is less than a level, so that line loses what it has instead of being
        // skipped. Skipping it would preserve the misalignment forever: the aligned lines
        // would keep moving left and the ragged one would not.
        val text = "        x = 1\n  y = 2\nz = 3"
        val edit = EditorEdits.dedentBlock(text, 0, text.length)
        assertEquals("    x = 1\ny = 2\nz = 3", edit.text)
    }

    @Test
    fun aBlockEditIsExactlyReversedByItsOpposite() {
        // Indent then dedent must be the identity, which is the property a learner relies
        // on when they Tab by mistake and immediately Shift+Tab. Asserted as a round trip
        // because it can hold for each direction's own test and still fail together.
        val text = "def f():\n    if x:\n        return 1\n\n    return 0"
        val indented = EditorEdits.indentBlock(text, 0, text.length)
        val restored = EditorEdits.dedentBlock(
            indented.text,
            indented.selectionStart,
            indented.selectionEnd,
        )
        assertEquals(text, restored.text)
    }

    @Test
    fun blockEditsClampOutOfRangePositions() {
        // Same defensive contract as the single-caret edits: the composable's state and
        // the incoming source can briefly disagree during a reset.
        assertEquals("    abc", EditorEdits.indentBlock("abc", -5, 99).text)
        assertEquals("abc", EditorEdits.dedentBlock("abc", 99, -5).text)
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
