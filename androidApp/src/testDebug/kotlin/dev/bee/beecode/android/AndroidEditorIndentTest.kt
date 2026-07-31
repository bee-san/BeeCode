package dev.bee.beecode.android

import android.app.Application
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextInputSelection
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.pressKey
import androidx.compose.ui.test.requestFocus
import androidx.compose.ui.test.withKeyDown
import androidx.compose.ui.text.TextRange
import androidx.test.core.app.ApplicationProvider
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.BROWSE_ALL_NEW_TAG
import dev.bee.beecode.android.ui.QUEUE_LIST_TAG
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The phone editor helps with Python's indentation.
 *
 * ### Why this is not just desktop's test again
 *
 * The two clients reach the same [dev.bee.beecode.design.EditorEdits] functions by
 * genuinely different routes, and the difference is the whole reason this file exists.
 *
 * A soft keyboard's Enter arrives through `onValueChange` as an edit that has *already
 * happened* — `onPreviewKeyEvent`, which is how desktop does auto-indent, never fires for
 * it. So Android detects Enter by inspecting the shape of the incoming value against the
 * outgoing one, and that detection is the code most likely to be subtly wrong: it has to
 * recognise a typed newline while ignoring a pasted one. Conversely no soft keyboard emits
 * Tab at all, so Tab and Shift+Tab are hardware-only and take the key path.
 *
 * Written the desktop way, the Android auto-indent would have compiled, read correctly,
 * and done nothing on a phone. Only a test that goes through the text-input path rather
 * than the key path can tell those two apart.
 *
 * ### Why through the whole app rather than the composable
 *
 * `CodeEditor` is private to `ProblemScreen.kt`, and opening it up so a test could
 * instantiate it directly would widen the API to suit the test. Driving the real screen
 * costs a Problem-open per test and proves slightly more: that the editor is reachable and
 * wired to the Problem it claims to be editing.
 *
 * The buffer is read back through the field's own `editableText` semantics rather than
 * through the profile's draft, so an assertion here cannot pass on a stale save.
 */
// `performKeyInput` is the experimental part, and it is not optional here: hardware Tab is
// half of what this file tests, and there is no stable API for injecting a key event.
@OptIn(ExperimentalTestApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class AndroidEditorIndentTest {

    @get:Rule
    val compose = createAndroidComposeRule<ComposeTestHostActivity>()

    private lateinit var profile: BeeCodeProfile

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Application>()
        System.setProperty("org.sqlite.tmpdir", context.cacheDir.absolutePath)
        val catalogue = context.assets.open(BeeCodeApplication.PACK_ASSET)
            .bufferedReader()
            .use { ProblemCatalogue.fromPackJson(it.readText()) }
        profile = BeeCodeProfile.inMemory(catalogue = catalogue, runner = ScriptedPythonRunner())
    }

    @After
    fun tearDown() {
        profile.close()
    }

    @Test
    fun pressingEnterAfterAColonIndentsTheNextLine() {
        // The soft-keyboard path, and the one that decides whether any of this works on a
        // phone. Python's indentation is syntactically significant, so an editor that makes
        // the learner tap four spaces after every colon is an editor they will get wrong.
        val editor = openEditorWith("def f():")
        editor.setCaretToEnd()

        editor.performTextInput("\n")

        // Not "def f():\n" — the body is already indented, ready to type into.
        assertEquals("def f():\n    ", editorText())
    }

    @Test
    fun pressingEnterInsideABlockKeepsTheCurrentIndentation() {
        // The common case, and distinct from the one above: no colon here, so nothing new is
        // opening. Continuing at the same depth is what makes a multi-line body typable;
        // returning to the left margin would mean re-indenting every single line.
        val editor = openEditorWith("def f():\n    x = 1")
        editor.setCaretToEnd()

        editor.performTextInput("\n")

        assertEquals("def f():\n    x = 1\n    ", editorText())
    }

    @Test
    fun pastingMultipleLinesDoesNotGetReIndented() {
        // The boundary on the Enter detection. Pasted text carries its own indentation, and
        // re-indenting it would mangle exactly the case where a learner brings in a
        // half-finished solution. The detection accepts *one* newline inserted at the caret
        // and nothing else, which is what distinguishes a keystroke from a paste.
        val editor = openEditorWith("def f():")
        editor.setCaretToEnd()

        editor.performTextInput("\n        deep = 1")

        // Untouched: neither an added indent on the new line nor a stripped one.
        assertEquals("def f():\n        deep = 1", editorText())
    }

    @Test
    fun aHardwareTabIndentsRatherThanMovingFocusAway() {
        // Tab in a code editor must type, not navigate. Compose's default is to move focus,
        // which on a docked phone or tablet means Tab silently leaves the editor — the
        // learner's next keystrokes go somewhere else entirely.
        val editor = openEditorWith("x = 1")
        editor.setCaretToEnd()

        editor.performKeyInput { pressKey(Key.Tab) }

        assertEquals("x = 1    ", editorText())
    }

    @Test
    fun aHardwareShiftTabDedentsRatherThanIndenting() {
        // The reported bug's Android counterpart. `event.key` is `Key.Tab` whether or not
        // Shift is held, so without an explicit Shift branch the dedent shortcut *adds*
        // indentation — the exact opposite of what it is for. Unreachable from the
        // EditorEdits tests, which passed throughout because `dedentBlock` was correct and
        // simply never called.
        val editor = openEditorWith("def f():\n        pass")
        // Caret inside "pass", within the second line's eight spaces.
        editor.performTextInputSelection(TextRange(20))
        compose.waitForIdle()

        editor.performKeyInput { withKeyDown(Key.ShiftLeft) { pressKey(Key.Tab) } }

        assertEquals("def f():\n    pass", editorText())
    }

    @Test
    fun tabWithLinesSelectedIndentsThemInsteadOfDeletingThem() {
        // The destructive case. A plain insert-at-selection replaces the selected lines with
        // four spaces: the learner selects a loop body, presses Tab the way every editor
        // they have ever used expects, and their code is gone.
        val editor = openEditorWith("def f():\nx = 1\ny = 2")
        editor.performTextInputSelection(TextRange(9, 20))
        compose.waitForIdle()

        editor.performKeyInput { pressKey(Key.Tab) }

        assertEquals("def f():\n    x = 1\n    y = 2", editorText())
    }

    @Test
    fun backspaceInIndentationRemovesAWholeLevel() {
        // Four taps to undo one Tab is the kind of asymmetry that makes an editor feel
        // broken. Only inside leading whitespace, though — see the next test.
        val editor = openEditorWith("def f():\n        pass")
        editor.performTextInputSelection(TextRange(17))
        compose.waitForIdle()

        editor.performKeyInput { pressKey(Key.Backspace) }

        assertEquals("def f():\n    pass", editorText())
    }

    @Test
    fun backspaceInOrdinaryTextStillDeletesOneCharacter() {
        // The boundary that keeps the level-dedent from being a bug of its own: outside
        // leading whitespace, Backspace must fall through to Compose's own handling. A
        // Backspace that ate four characters of an identifier would be far worse than no
        // dedent at all.
        val editor = openEditorWith("value = 12")
        editor.setCaretToEnd()

        editor.performKeyInput { pressKey(Key.Backspace) }

        assertEquals("value = 1", editorText())
    }

    /** Open Two Sum and put [source] in its editor. */
    private fun openEditorWith(source: String): SemanticsNodeInteraction {
        compose.setContent {
            BeeCodeTheme {
                BeeCodeApp(StudyViewModel(profile))
            }
        }
        // Scrolled to rather than assumed on screen: the queue is a lazy list and Two Sum
        // sits below the fold in a catalogue this size, so the row has no semantics to
        // click until it composes.
        compose.onNodeWithTag(QUEUE_LIST_TAG)
            .performScrollToNode(hasTestTag(BROWSE_ALL_NEW_TAG))
        compose.onNodeWithTag(BROWSE_ALL_NEW_TAG).performClick()
        compose.onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(TWO_SUM_TITLE))
        compose.onAllNodesWithText(TWO_SUM_TITLE).onFirst().performClick()
        val editor = compose.onNodeWithContentDescription("Python solution editor")
        editor.requestFocus()
        editor.performTextReplacement(source)
        compose.waitForIdle()
        return editor
    }

    /**
     * Put the caret at the end of the buffer.
     *
     * `performTextReplacement` leaves the selection where the harness chooses, and every
     * assertion here depends on where the caret is — so it is always stated rather than
     * assumed.
     */
    private fun SemanticsNodeInteraction.setCaretToEnd() {
        performTextInputSelection(TextRange(editorText().length))
        compose.waitForIdle()
    }

    /** The editor's buffer, read from the field itself rather than from the saved draft. */
    private fun editorText(): String = compose
        .onNodeWithContentDescription("Python solution editor")
        .fetchSemanticsNode()
        .config[SemanticsProperties.EditableText]
        .text

    private companion object {
        /** The Problem these tests drive. Solvable in a few lines and stable content. */
        const val TWO_SUM_TITLE = "Two Sum"
    }
}
