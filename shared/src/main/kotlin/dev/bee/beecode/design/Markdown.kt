package dev.bee.beecode.design

/**
 * Groups a Problem statement's Markdown into blocks, and cleans up inline markup.
 *
 * BeeCode authors and validates its own content, so this handles the subset that content
 * actually uses — headings, paragraphs, bullets, numbered lists, fenced code — rather than
 * pulling in a full CommonMark dependency.
 *
 * ## Why in `:shared` rather than in each client
 *
 * Both clients had a private copy of the same line-at-a-time renderer, and both had the
 * same two bugs, because a bug in duplicated code is duplicated too. Grouping lines into
 * blocks needs no UI toolkit, and putting it here means a fix lands on the phone and the
 * desktop at once, with one test rather than two.
 *
 * ## The two bugs this fixes
 *
 * **Every line was its own paragraph.** The content is hard-wrapped at about 85 columns,
 * as prose in a text file should be. Rendering one `Text` per source line makes the
 * *source's* wrap column the reader's wrap column, so on a phone "…the price of a stock"
 * / "on day i." broke mid-sentence with a paragraph gap, and every wrapped line did the
 * same. In Markdown a single newline is a soft break — it means a space, not a new
 * paragraph. That is what made the statements "kinda ugly" on a narrow screen: the text
 * was laid out for the file it was typed in rather than the screen it was read on.
 *
 * **`_` was stripped everywhere.** It was treated as an emphasis marker, but the content
 * never uses `_emphasis_` — all 131 underscores are inside code spans, in Python
 * identifiers and numeric literals. So `100_000` rendered as `100000` and
 * `climb_stairs` as `climbstairs`: a constraint the learner would mis-read and a function
 * name they would mis-type. Emphasis markers are now removed only *outside* code spans,
 * which also keeps the `*` in `prefix[i] * nums[i]` from vanishing as a stray marker.
 */
object Markdown {

    /** One rendered unit. Clients map these onto their own text styles. */
    sealed interface Block {

        /** `#` or `##`. [level] is 1 or 2; deeper headings are not used by the content. */
        data class Heading(val level: Int, val text: String) : Block

        /** Prose. Soft-wrapped source lines are already joined into one string. */
        data class Paragraph(val text: String) : Block

        /** A `- ` item, with any indented continuation lines folded in. */
        data class Bullet(val text: String) : Block

        /**
         * A `1. ` item.
         *
         * Carries [marker] rather than a plain index so the rendered numbering is the
         * author's. Renumbering would silently disagree with a statement that refers to
         * "step 2", and the content does refer to its own steps.
         */
        data class Numbered(val marker: String, val text: String) : Block

        /** A fenced block, verbatim: indentation is the content in Python. */
        data class Code(val lines: List<String>) : Block
    }

    /**
     * Parse [markdown] into blocks.
     *
     * Blank lines separate blocks; within a block, source lines are joined with a space.
     * An unterminated fence still yields its code rather than being dropped, because
     * losing a code sample outright is a worse failure than rendering a stray fence.
     */
    fun blocks(markdown: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val paragraph = mutableListOf<String>()
        val code = mutableListOf<String>()
        var inCode = false

        // A list item under construction. Its continuation lines are indented, and are
        // part of the item rather than a new block — "- You must sell strictly after you
        // buy; buying and selling on the same day earns / nothing." is one bullet.
        var listItem: Block? = null

        fun flushParagraph() {
            if (paragraph.isNotEmpty()) {
                blocks += Block.Paragraph(inline(paragraph.joinToString(" ")))
                paragraph.clear()
            }
        }

        fun flushListItem() {
            listItem?.let { blocks += it }
            listItem = null
        }

        /** Append a continuation line to whichever block is open, if any. */
        fun continueOpenBlock(line: String): Boolean {
            val open = listItem
            return when {
                open is Block.Bullet -> {
                    listItem = open.copy(text = open.text + " " + inline(line))
                    true
                }
                open is Block.Numbered -> {
                    listItem = open.copy(text = open.text + " " + inline(line))
                    true
                }
                paragraph.isNotEmpty() -> {
                    paragraph += line
                    true
                }
                else -> false
            }
        }

        markdown.lines().forEach { raw ->
            if (raw.trimStart().startsWith("```")) {
                flushParagraph()
                flushListItem()
                if (inCode) {
                    blocks += Block.Code(code.toList())
                    code.clear()
                }
                inCode = !inCode
                return@forEach
            }
            if (inCode) {
                code += raw
                return@forEach
            }

            val line = raw.trim()
            val indented = raw.startsWith("  ") && line.isNotEmpty()
            when {
                line.isEmpty() -> {
                    flushParagraph()
                    flushListItem()
                }
                line.startsWith("## ") -> {
                    flushParagraph()
                    flushListItem()
                    blocks += Block.Heading(2, inline(line.removePrefix("## ")))
                }
                line.startsWith("# ") -> {
                    flushParagraph()
                    flushListItem()
                    blocks += Block.Heading(1, inline(line.removePrefix("# ")))
                }
                line.startsWith("- ") -> {
                    flushParagraph()
                    flushListItem()
                    listItem = Block.Bullet(inline(line.removePrefix("- ")))
                }
                NUMBERED.matches(line) -> {
                    flushParagraph()
                    flushListItem()
                    val marker = NUMBERED.find(line)!!.groupValues[1]
                    listItem = Block.Numbered(marker, inline(line.removePrefix("$marker ").trim()))
                }
                // Indented under an open item: a continuation, not a new block.
                indented && continueOpenBlock(line) -> Unit
                else -> {
                    // An unindented line after a list item ends the list, since the
                    // content wraps continuations with indentation and never without.
                    flushListItem()
                    paragraph += line
                }
            }
        }

        if (inCode && code.isNotEmpty()) blocks += Block.Code(code.toList())
        flushParagraph()
        flushListItem()
        return blocks
    }

    /**
     * Remove inline markup, leaving the text a reader wants.
     *
     * Backticks, `**bold**`, and `*emphasis*` are dropped rather than styled: per-span
     * styling would mean building an annotated string in each client, which is a lot of
     * machinery for content whose emphasis is decorative.
     *
     * Code spans are found first and their contents passed through untouched. That
     * ordering is the whole point — `100_000`, `climb_stairs`, and `prefix[i] * nums[i]`
     * all contain characters that are markup outside a code span and content inside one.
     */
    fun inline(text: String): String = buildString {
        // Odd indices are inside a code span: "a `b` c" splits to [a, b, c].
        text.split("`").forEachIndexed { index, part ->
            if (index % 2 == 1) append(part) else append(stripEmphasis(part))
        }
    }

    /**
     * Drop `**` and `*` markers from text known to be outside a code span.
     *
     * Every asterisk outside a code span in BeeCode's content is a marker — the
     * multiplication in `prefix[i] * nums[i]` is inside one — so this removes them all
     * rather than trying to pair them up. `**` goes first only so a bold run's four
     * characters are removed as two markers and not as four separate ones.
     */
    private fun stripEmphasis(part: String): String = part.replace("**", "").replace("*", "")

    /** `1. `, `2. `, … — the only numbered form the content uses. */
    private val NUMBERED = Regex("""^(\d+\.)\s+\S.*""")
}
