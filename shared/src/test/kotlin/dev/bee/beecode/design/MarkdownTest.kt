package dev.bee.beecode.design

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The Markdown the Problem statements are written in, as the learner reads it.
 *
 * ### What was wrong, and why a test is the right response
 *
 * Both clients rendered one `Text` per *source* line. The content is hard-wrapped at about
 * 85 columns, so on a phone every wrapped sentence broke mid-clause with a paragraph gap
 * — "the price of a stock" / "on day i." — and the statements were laid out for the file
 * they were typed in rather than the screen they were read on. And `_` was stripped as an
 * emphasis marker although the content never uses `_emphasis_`, so `100_000` rendered as
 * `100000` and `climb_stairs` as `climbstairs`: one constraint mis-read, one function name
 * mis-typed.
 *
 * Neither bug could fail a test, because rendering was inline in a private composable in
 * each client. Both clients had it, and both had it identically — a copied bug. So the
 * assertions here are mostly against [Markdown] directly, and the last few are against
 * *the real content pack*, since a rule about markup is only worth what the actual
 * statements do with it.
 */
class MarkdownTest {

    @Test
    fun aSoftWrappedParagraphBecomesOneParagraph() {
        // The bug, at its smallest. Two source lines, one sentence — and joined with a
        // space, not concatenated, or the words either side of the break would collide.
        val blocks = Markdown.blocks(
            """
            You are given a list of integers prices where prices[i] is the price of a stock
            on day i.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Markdown.Block.Paragraph(
                    "You are given a list of integers prices where prices[i] is the price of " +
                        "a stock on day i.",
                ),
            ),
            blocks,
        )
    }

    @Test
    fun aBlankLineStillSeparatesParagraphs() {
        // The other half of the soft-break rule, and the reason this is not simply
        // "join everything": a *blank* line is a real paragraph break and must survive.
        val blocks = Markdown.blocks("First one\nwrapped.\n\nSecond one.")
        assertEquals(
            listOf(
                Markdown.Block.Paragraph("First one wrapped."),
                Markdown.Block.Paragraph("Second one."),
            ),
            blocks,
        )
    }

    @Test
    fun underscoresInsideCodeSpansSurvive() {
        // `100_000` was rendered as `100000` — a constraint off by a factor of ten to any
        // learner reading it — and `climb_stairs` as `climbstairs`, a name that does not
        // exist. Both from stripping `_` as emphasis in content that never uses it.
        assertEquals("0 <= len(prices) <= 100_000", Markdown.inline("`0 <= len(prices) <= 100_000`"))
        assertEquals("climb_stairs(0)", Markdown.inline("`climb_stairs(0)`"))
        assertEquals(
            "Call climb_stairs(40) and wait.",
            Markdown.inline("Call `climb_stairs(40)` and wait."),
        )
    }

    @Test
    fun underscoresOutsideCodeSpansSurviveToo() {
        // `_` is not markup in BeeCode's content *anywhere*, not merely inside spans, and
        // this is the assertion that says so. Without it, re-adding `.replace("_", "")` to
        // the emphasis stripper passes the whole suite: every underscore in the pack today
        // happens to sit inside a code span, so the real content cannot catch it. A
        // statement written tomorrow with a bare `two_sum` in prose would silently lose it.
        assertEquals("Call two_sum with the list.", Markdown.inline("Call two_sum with the list."))
        assertEquals("snake_case", Markdown.inline("snake_case"))
    }

    @Test
    fun asterisksInsideCodeSpansSurvive() {
        // Multiplication, not emphasis. Every asterisk in the content that is *not* a
        // marker is inside a code span, which is what makes the code-span-first ordering
        // sufficient rather than merely better.
        assertEquals("prefix[i] * nums[i]", Markdown.inline("`prefix[i] * nums[i]`"))
    }

    @Test
    fun emphasisMarkersOutsideCodeSpansAreRemoved() {
        assertEquals(
            "You may buy on one day and sell on one later day.",
            Markdown.inline("You may buy on one day and sell on one **later** day."),
        )
        assertEquals(
            "Note the difference between a substring and a subsequence.",
            Markdown.inline("Note the difference between a *substring* and a *subsequence*."),
        )
    }

    @Test
    fun bothKindsOfMarkupInOneLineAreHandledIndependently() {
        // The case that distinguishes "strip code spans first" from "strip everything":
        // one line where the same character is markup on one side and content on the other.
        assertEquals(
            "Bold, and len(nums) <= 100_000 with a * in it.",
            Markdown.inline("**Bold**, and `len(nums) <= 100_000` with a `*` in it."),
        )
    }

    @Test
    fun aBulletsIndentedContinuationStaysPartOfTheBullet() {
        // "…earns / nothing." is one bullet in the source. Rendered as two blocks, the
        // word "nothing." became its own paragraph sitting outside the list.
        val blocks = Markdown.blocks(
            """
            - 0 <= prices[i] <= 10^9
            - You must sell strictly after you buy; buying and selling on the same day earns
              nothing.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Markdown.Block.Bullet("0 <= prices[i] <= 10^9"),
                Markdown.Block.Bullet(
                    "You must sell strictly after you buy; buying and selling on the same " +
                        "day earns nothing.",
                ),
            ),
            blocks,
        )
    }

    @Test
    fun numberedItemsKeepTheAuthorsNumbering() {
        // Rendered as prose before this, so a numbered list read as a run-on paragraph.
        // The marker is the author's rather than a recomputed index: statements refer to
        // their own steps, and renumbering would make the prose disagree with the list.
        val blocks = Markdown.blocks(
            """
            1. Each group is sorted alphabetically (ascending).
            2. The list of groups is sorted alphabetically by group, so effectively by each
               group's first word.
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Markdown.Block.Numbered("1.", "Each group is sorted alphabetically (ascending)."),
                Markdown.Block.Numbered(
                    "2.",
                    "The list of groups is sorted alphabetically by group, so effectively " +
                        "by each group's first word.",
                ),
            ),
            blocks,
        )
    }

    @Test
    fun fencedCodeKeepsItsLinesAndItsIndentation() {
        // Verbatim, and the joining that fixes prose must not touch this: indentation is
        // syntax in Python, and a sample folded into one line is not a sample.
        val blocks = Markdown.blocks(
            """
            Try this:

            ```python
            def f(n):
                if n < 2:
                    return n
            ```
            """.trimIndent(),
        )
        assertEquals(
            listOf(
                Markdown.Block.Paragraph("Try this:"),
                Markdown.Block.Code(listOf("def f(n):", "    if n < 2:", "        return n")),
            ),
            blocks,
        )
    }

    @Test
    fun anUnterminatedFenceStillYieldsItsCode() {
        // A content bug should cost the fence, not the sample. Dropping the block outright
        // would hide the sample *and* the mistake that caused it.
        val blocks = Markdown.blocks("```\nx = 1")
        assertEquals(listOf(Markdown.Block.Code(listOf("x = 1"))), blocks)
    }

    @Test
    fun headingsInterruptWhateverWasOpen() {
        val blocks = Markdown.blocks("Some prose\nwrapped.\n## Constraints\n- one")
        assertEquals(
            listOf(
                Markdown.Block.Paragraph("Some prose wrapped."),
                Markdown.Block.Heading(2, "Constraints"),
                Markdown.Block.Bullet("one"),
            ),
            blocks,
        )
    }

    @Test
    fun anUnindentedLineAfterAListEndsTheList() {
        // Continuations are indented and paragraphs are not, which is the only signal
        // available — so the two must not be confused, or the paragraph following a list
        // would be swallowed into its last item.
        val blocks = Markdown.blocks("- one\n- two\nThis is prose again.")
        assertEquals(
            listOf(
                Markdown.Block.Bullet("one"),
                Markdown.Block.Bullet("two"),
                Markdown.Block.Paragraph("This is prose again."),
            ),
            blocks,
        )
    }

    // ---- Against the real content ---------------------------------------

    @Test
    fun noRenderedContentKeepsABacktick() {
        // The whole pack, because a markup rule is worth what the actual statements do
        // with it. A backtick is always a delimiter and never content, so none may reach
        // the screen — unlike `*` and `_`, which are content inside a span.
        val markdowns = contentMarkdown()
        assertTrue(markdowns.size >= 30, "expected the pack's statements, found ${markdowns.size}")

        markdowns.forEach { (path, markdown) ->
            proseOf(Markdown.blocks(markdown)).forEach { rendered ->
                assertFalse(rendered.contains('`'), "backtick left in $path: $rendered")
            }
        }
    }

    @Test
    fun theOnlyAsterisksLeftInTheRealContentAreTheOnesInsideCodeSpans() {
        // An asterisk is a marker outside a span and multiplication inside one, so
        // "none left" is the wrong assertion — `prefix[i] * nums[i]` must keep its
        // operator while `**later**` must lose its markers. Counted per file, so both
        // directions fail: a surviving marker and a swallowed operator.
        contentMarkdown().forEach { (path, markdown) ->
            val expected = codeSpanContents(markdown).sumOf { span -> span.count { it == '*' } }
            val actual = proseOf(Markdown.blocks(markdown)).sumOf { rendered ->
                rendered.count { it == '*' }
            }
            assertEquals(expected, actual, "wrong asterisks survived in $path")
        }
    }

    @Test
    fun everyUnderscoreInTheRealContentSurvivesRendering() {
        // 131 of them, all inside code spans — Python identifiers and numeric literals.
        // Counted rather than spot-checked so a statement added later with an underscore
        // in a new position is covered too.
        contentMarkdown().forEach { (path, markdown) ->
            val blocks = Markdown.blocks(markdown)
            val expected = markdown.count { it == '_' }
            val actual = blocks.sumOf { block ->
                when (block) {
                    is Markdown.Block.Heading -> block.text.count { it == '_' }
                    is Markdown.Block.Paragraph -> block.text.count { it == '_' }
                    is Markdown.Block.Bullet -> block.text.count { it == '_' }
                    is Markdown.Block.Numbered -> block.text.count { it == '_' }
                    is Markdown.Block.Code -> block.lines.sumOf { line ->
                        line.count { it == '_' }
                    }
                }
            }
            assertEquals(expected, actual, "underscores lost or gained in $path")
        }
    }

    @Test
    fun everyRealStatementHasFewerBlocksThanSourceLines() {
        // The bug's signature, at pack scale: one block per source line *was* the bug, so
        // if any statement still produces as many blocks as it has non-blank lines, its
        // paragraphs are not being joined.
        contentMarkdown().forEach { (path, markdown) ->
            val nonBlankLines = markdown.lines().count { it.isNotBlank() }
            val blocks = Markdown.blocks(markdown).size
            assertTrue(
                blocks < nonBlankLines,
                "$path renders $blocks blocks from $nonBlankLines lines — nothing was joined",
            )
        }
    }

    private companion object {

        /**
         * The rendered text of every block except code.
         *
         * Fenced code is excluded deliberately: it is verbatim by design, so a Python
         * sample may legitimately contain any character these assertions object to.
         */
        fun proseOf(blocks: List<Markdown.Block>): List<String> = blocks.mapNotNull { block ->
            when (block) {
                is Markdown.Block.Heading -> block.text
                is Markdown.Block.Paragraph -> block.text
                is Markdown.Block.Bullet -> block.text
                is Markdown.Block.Numbered -> block.text
                is Markdown.Block.Code -> null
            }
        }

        /**
         * The contents of each `` `code span` ``, outside fenced blocks.
         *
         * The fence check matters: a backtick inside a fenced sample is not a span
         * delimiter, and pairing it with the next one would make the expected counts
         * arbitrary.
         */
        fun codeSpanContents(markdown: String): List<String> {
            val spans = mutableListOf<String>()
            var inFence = false
            markdown.lines().forEach { raw ->
                if (raw.trimStart().startsWith("```")) {
                    inFence = !inFence
                    return@forEach
                }
                if (inFence) return@forEach
                // Odd indices are span contents, matching how `Markdown.inline` splits.
                raw.split("`").forEachIndexed { index, part ->
                    if (index % 2 == 1) spans += part
                }
            }
            return spans
        }

        /** Every statement and explanation in the core pack, by path. */
        fun contentMarkdown(): List<Pair<String, String>> {
            val problems = File(repoRoot(), "content/packs/core/problems")
            check(problems.isDirectory) { "content pack not found at $problems" }
            return problems.listFiles().orEmpty().sortedBy { it.name }.flatMap { directory ->
                listOf("statement.md", "explanation.md").mapNotNull { name ->
                    File(directory, name).takeIf { it.isFile }
                        ?.let { "${directory.name}/$name" to it.readText() }
                }
            }
        }

        fun repoRoot(): File {
            System.getProperty("beecode.repoRoot")?.let { return File(it) }
            var candidate = File(".").absoluteFile
            repeat(6) {
                if (File(candidate, "content/packs/core").isDirectory) return candidate
                candidate = candidate.parentFile ?: return candidate
            }
            return File(".").absoluteFile
        }
    }
}
