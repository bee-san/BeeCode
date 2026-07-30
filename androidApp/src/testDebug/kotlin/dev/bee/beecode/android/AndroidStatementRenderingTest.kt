package dev.bee.beecode.android

import android.app.Application
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.QUEUE_LIST_TAG
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.design.Markdown
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A Problem statement reaches the phone screen as prose, not as its source file.
 *
 * [dev.bee.beecode.design.MarkdownTest] covers the parsing, and covers it far more
 * thoroughly than this can. What it cannot cover is the wiring: the old renderer was
 * inline in a private composable, and swapping it for the shared parser is exactly the
 * kind of change that compiles while rendering nothing.
 *
 * So this asserts the two things a unit test on the parser cannot see — that the composable
 * is fed the real statement and that the fixed text is what appears — and nothing more.
 *
 * Found by running the app rather than by reading it: on the emulator "…is the price of a
 * stock" / "on day i." broke mid-sentence, because the content is hard-wrapped in its
 * source file and every source line became its own paragraph.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, qualifiers = "w411dp-h891dp-xhdpi")
class AndroidStatementRenderingTest {

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
    fun aSoftWrappedSentenceReachesTheScreenAsOneParagraph() {
        // The assertion has to *span* the source's line break, and that is the whole subtlety
        // here: a substring lying entirely within the first source line matches even when
        // every line is its own paragraph, so it proves nothing. This one crosses the break
        // between "…the indices of" and "the two numbers…", which no per-line rendering can
        // put in a single node.
        //
        // Taken from the pack rather than written out, so it stays true if the content is
        // rewrapped — the point is that a break was crossed, not which words surround it.
        openTwoSum()
        val problem = profile.catalogue.allProblems().first { it.title == TWO_SUM_TITLE }
        val lines = problem.statementMarkdown.lines()
        // Each line is stripped of markup *before* being sliced: slicing first could cut a
        // `` ` `` or a `**` in half and leave the expectation asking for a stray marker.
        val spanning = Markdown.inline(lines[0]).takeLast(20) +
            " " + Markdown.inline(lines[1]).take(20)
        compose.onNode(hasText(spanning, substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun aConstraintKeepsTheUnderscoreInItsNumericLiteral() {
        // `10_000` had been rendering as `10000`: an underscore stripped as emphasis from
        // content that has none, turning a bound into one ten times larger. Taken from the
        // pack's own text rather than written out here, so the assertion cannot drift from
        // the content — and found by line content rather than by the number, because which
        // bound this Problem states is not what is being tested.
        openTwoSum()
        val problem = profile.catalogue.allProblems().first { it.title == TWO_SUM_TITLE }
        val constraint = problem.statementMarkdown
            .lines()
            .first { it.startsWith("- ") && it.contains('_') }
            .removePrefix("- ")
            .replace("`", "")
        compose.onNode(hasText(constraint, substring = true))
            .performScrollTo()
            .assertIsDisplayed()
    }

    private fun openTwoSum() {
        compose.setContent { BeeCodeTheme { BeeCodeApp(StudyViewModel(profile)) } }
        // Scrolled to rather than assumed on screen: the queue is a lazy list and Two Sum
        // sits below the fold in a catalogue this size, so the row has no semantics to
        // click until it composes.
        compose.onNodeWithTag(QUEUE_LIST_TAG).performScrollToNode(hasText(TWO_SUM_TITLE))
        compose.onAllNodesWithText(TWO_SUM_TITLE).onFirst().performClick()
        compose.waitForIdle()
    }

    private companion object {
        /** The Problem these tests drive. Solvable in a few lines and stable content. */
        const val TWO_SUM_TITLE = "Two Sum"
    }
}
