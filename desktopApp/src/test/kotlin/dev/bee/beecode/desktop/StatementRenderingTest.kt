package dev.bee.beecode.desktop

import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.design.Markdown
import kotlin.test.Test

/**
 * A Problem statement reaches the desktop window as prose, not as its source file.
 *
 * The counterpart to Android's `AndroidStatementRenderingTest`, and deliberately the same
 * two assertions. [dev.bee.beecode.design.MarkdownTest] covers the parsing itself; what
 * needs covering *twice* is the wiring, because there are two renderers and either can be
 * broken alone. Before the shared parser existed both clients had their own copy with the
 * same two bugs — hard-wrapped source lines rendered as separate paragraphs, and `_`
 * stripped out of Python identifiers and numeric literals — and neither client's suite
 * could see either one.
 */
@OptIn(ExperimentalTestApi::class)
class StatementRenderingTest {

    @Test
    fun aSoftWrappedSentenceReachesTheWindowAsOneParagraph() {
        // The expectation must *span* the source's line break: a substring taken from
        // within one source line matches even when every line is its own paragraph, so it
        // would pass against the very bug this covers.
        withTwoSumOpen { statement ->
            val lines = statement.lines()
            // Markup stripped before slicing, so a slice cannot cut a `` ` `` in half and
            // leave the expectation asking for a stray marker.
            val spanning = Markdown.inline(lines[0]).takeLast(20) +
                " " + Markdown.inline(lines[1]).take(20)
            onNode(hasText(spanning, substring = true)).assertIsDisplayed()
        }
    }

    @Test
    fun aConstraintKeepsTheUnderscoreInItsNumericLiteral() {
        // `10_000` had rendered as `10000` — a bound ten times too large. Read from the
        // pack rather than written out, so the assertion cannot drift from the content.
        withTwoSumOpen { statement ->
            val constraint = statement.lines()
                .first { it.startsWith("- ") && it.contains('_') }
                .removePrefix("- ")
                .replace("`", "")
            onNode(hasText(constraint, substring = true)).assertIsDisplayed()
        }
    }

    /** Open Two Sum and hand the callback its raw statement Markdown. */
    private fun withTwoSumOpen(body: ComposeUiTest.(statement: String) -> Unit) {
        val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)
        val statement = catalogue.allProblems().first { it.title == "Two Sum" }.statementMarkdown
        val profile = BeeCodeProfile.inMemory(
            catalogue = catalogue,
            runner = ScriptedPythonRunner(),
        )
        try {
            runComposeUiTest {
                setContent { DesktopApp(profile) }
                onAllNodesWithText("Two Sum").onFirst().performClick()
                waitForIdle()
                body(statement)
            }
        } finally {
            profile.close()
        }
    }
}
