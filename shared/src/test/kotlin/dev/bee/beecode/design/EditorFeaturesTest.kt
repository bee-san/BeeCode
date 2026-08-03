package dev.bee.beecode.design

import dev.bee.beecode.persistence.BeeCodeDatabase
import dev.bee.beecode.persistence.SettingsRepository
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.datetime.Instant

class EditorFeaturesTest {
    private lateinit var database: BeeCodeDatabase
    private lateinit var settings: SettingsRepository

    @BeforeTest
    fun setUp() {
        database = BeeCodeDatabase.inMemory()
        settings = SettingsRepository(database)
    }

    @AfterTest
    fun tearDown() = database.close()

    @Test
    fun aPairWrapsASelectionAndKeepsItSelected() {
        val edit = EditorEdits.surround("value", 0, 5, "(", ")")
        assertEquals("(value)", edit.text)
        assertEquals(1, edit.selectionStart)
        assertEquals(6, edit.selectionEnd)
    }

    @Test
    fun anEmptyPairLeavesTheCaretInside() {
        val edit = EditorEdits.surround("x = ", 4, 4, "[", "]")
        assertEquals("x = []", edit.text)
        assertEquals(5, edit.selectionStart)
        assertEquals(5, edit.selectionEnd)
    }

    @Test
    fun commentTogglePreservesIndentationAndRoundTrips() {
        val source = "def f():\n    value = 1\n    return value"
        val commented = EditorEdits.toggleComment(source, 9, source.length)
        assertEquals("def f():\n    # value = 1\n    # return value", commented.text)
        val restored = EditorEdits.toggleComment(
            commented.text,
            commented.selectionStart,
            commented.selectionEnd,
        )
        assertEquals(source, restored.text)
    }

    @Test
    fun literalSearchSupportsCaseAndWholeWords() {
        val source = "value VALUE values value"
        assertEquals(4, EditorEdits.findAll(source, "value").size)
        assertEquals(3, EditorEdits.findAll(source, "value", wholeWord = true).size)
        assertEquals(2, EditorEdits.findAll(source, "value", matchCase = true, wholeWord = true).size)
    }

    @Test
    fun searchNavigationStartsAtTheFirstMatchAndThenAdvances() {
        val matches = EditorEdits.findAll("value + value", "value")
        assertEquals(
            0,
            EditorEdits.searchNavigationIndex(matches, 0, 0, currentIndex = 0, delta = 1),
        )
        assertEquals(
            1,
            EditorEdits.searchNavigationIndex(matches, 0, 5, currentIndex = 0, delta = 1),
        )
        assertEquals(
            1,
            EditorEdits.searchNavigationIndex(matches, 0, 0, currentIndex = 0, delta = -1),
        )
    }

    @Test
    fun replaceAllUsesOriginalMatchOffsets() {
        assertEquals(
            "xx xx",
            EditorEdits.replaceAll("a a", "a", "xx").text,
        )
    }

    @Test
    fun goToLineClampsPastTheDocument() {
        assertEquals(4, EditorEdits.goToLine("a\nb\nc", 3))
        assertEquals(5, EditorEdits.goToLine("a\nb\nc", 99))
    }

    @Test
    fun lineNumbersLeaveWrappedContinuationLinesBlank() {
        val source = "alpha\nbeta"
        assertEquals("1\n2", EditorEdits.lineNumberGutter(source))
        assertEquals(
            "1\n\n2",
            EditorEdits.lineNumberGutter(source, visualLineStarts = listOf(0, 3, 6)),
        )
    }

    @Test
    fun highlighterRecognizesPythonWithoutTreatingStringsAsComments() {
        val source = "def solve(value=10):\n    text = \"# not comment\"\n    return len(text) # note"
        val tokens = PythonSyntax.tokens(source)
        assertTrue(tokens.any { it.kind == PythonSyntax.Kind.KEYWORD && source.substring(it.start, it.end) == "def" })
        assertTrue(tokens.any { it.kind == PythonSyntax.Kind.DEFINITION && source.substring(it.start, it.end) == "solve" })
        assertTrue(tokens.any { it.kind == PythonSyntax.Kind.NUMBER && source.substring(it.start, it.end) == "10" })
        assertEquals(1, tokens.count { it.kind == PythonSyntax.Kind.COMMENT })
    }

    @Test
    fun bracketMatchingIgnoresStringsAndComments() {
        val source = "items = [\")\", value]  # ]"
        val match = PythonSyntax.matchingBracket(source, source.indexOf('[') + 1)
        assertEquals(source.indexOf('['), match?.opening)
        assertEquals(source.indexOf(']'), match?.closing)
        assertNull(PythonSyntax.matchingBracket(source, source.indexOf("\")") + 1))
    }

    @Test
    fun historyIsBoundedAndRedoIsClearedByANewEdit() {
        val history = EditorHistory(EditorHistory.Snapshot("", 0, 0), limit = 2)
        history.record(EditorHistory.Snapshot("a", 1, 1))
        history.record(EditorHistory.Snapshot("ab", 2, 2))
        history.record(EditorHistory.Snapshot("abc", 3, 3))
        assertEquals("ab", history.undo()?.text)
        assertEquals("a", history.undo()?.text)
        assertNull(history.undo())
        assertEquals("ab", history.redo()?.text)
        history.record(EditorHistory.Snapshot("abx", 3, 3))
        assertFalse(history.canRedo)
    }

    @Test
    fun movingTheCaretDoesNotCreateAnUndoStep() {
        val history = EditorHistory(EditorHistory.Snapshot("abc", 3, 3))
        history.record(EditorHistory.Snapshot("abc", 1, 1))
        assertFalse(history.canUndo)
        history.record(EditorHistory.Snapshot("aXbc", 2, 2))
        assertEquals(EditorHistory.Snapshot("abc", 1, 1), history.undo())
    }

    @Test
    fun unicodeSourcePositionsMapCodePointsToUtf16Offsets() {
        val source = "first\n😀x\nlast"
        assertEquals(6, sourceOffset(source, line = 2, column = 1))
        assertEquals(8, sourceOffset(source, line = 2, column = 2))
        assertEquals(9, sourceOffset(source, line = 2, column = 99))
        assertEquals(source.length, sourceOffset(source, line = 99, column = 1))
    }

    @Test
    fun vimVerticalMovementPreservesTheColumnAndClampsShortLines() {
        val source = "alpha\nx\nomega"
        assertEquals(7, VimEdits.moveVertical(source, 4, 1).selectionEnd)
        assertEquals(1, VimEdits.moveVertical(source, 7, -1).selectionEnd)
        assertEquals(9, VimEdits.moveVertical(source, 7, 1).selectionEnd)
    }

    @Test
    fun vimWordMovementTreatsPunctuationAsItsOwnWord() {
        val source = "alpha + beta"
        assertEquals(6, VimEdits.nextWord(source, 0).selectionEnd)
        assertEquals(8, VimEdits.nextWord(source, 6).selectionEnd)
        assertEquals(6, VimEdits.previousWord(source, 8).selectionEnd)
        assertEquals(0, VimEdits.previousWord(source, 6).selectionEnd)
    }

    @Test
    fun vimLineCommandsPreserveIndentationAndDocumentBoundaries() {
        val source = "if ready:\n    run()\n    stop()"
        val below = VimEdits.openLineBelow(source, source.indexOf("run"))
        assertEquals("if ready:\n    run()\n    \n    stop()", below.text)
        assertEquals(below.text.indexOf("\n    \n") + 5, below.selectionEnd)

        val withoutLast = VimEdits.deleteLine(source, source.indexOf("stop"))
        assertEquals("if ready:\n    run()", withoutLast.text)
        assertEquals(withoutLast.text.length, withoutLast.selectionEnd)
    }

    @Test
    fun editorPreferencesRoundTripAndMalformedValuesFallBack() {
        val now = Instant.fromEpochMilliseconds(1)
        assertEquals(EditorPreferences.DesktopDefault, settings.editorPreferences(EditorPlatform.DESKTOP))
        settings.setEditorWrap(EditorPlatform.DESKTOP, true, now)
        settings.setEditorFontSize(EditorPlatform.DESKTOP, 18, now)
        settings.setEditorKeymap(EditorPlatform.DESKTOP, EditorKeymap.VIM, now)
        assertEquals(
            EditorPreferences.DesktopDefault.copy(
                wrapLines = true,
                fontSizeSp = 18,
                keymap = EditorKeymap.VIM,
            ),
            settings.editorPreferences(EditorPlatform.DESKTOP),
        )

        settings.put("editor.desktop.fontSizeSp", "huge", now)
        settings.put("editor.desktop.wrap", "sometimes", now)
        settings.put("editor.desktop.keymap", "ed", now)
        assertEquals(EditorPreferences.DesktopDefault, settings.editorPreferences(EditorPlatform.DESKTOP))
    }

    @Test
    fun mobileToolbarCanBeReorderedAndReset() {
        val now = Instant.fromEpochMilliseconds(1)
        val custom = listOf(MobileEditorAction.COLON, MobileEditorAction.INDENT)
        settings.setMobileEditorActions(custom, now)
        assertEquals(custom, settings.editorPreferences(EditorPlatform.ANDROID).mobileActions)
        settings.setMobileEditorActions(MobileEditorAction.DEFAULTS, now)
        assertEquals(
            MobileEditorAction.DEFAULTS,
            settings.editorPreferences(EditorPlatform.ANDROID).mobileActions,
        )
    }

    @Test
    fun mobileToolbarCanBeEmpty() {
        settings.setMobileEditorActions(emptyList(), Instant.fromEpochMilliseconds(1))
        assertTrue(settings.editorPreferences(EditorPlatform.ANDROID).mobileActions.isEmpty())
    }
}
