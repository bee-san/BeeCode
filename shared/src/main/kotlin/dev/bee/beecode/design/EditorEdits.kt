package dev.bee.beecode.design

/**
 * The pure text transformations behind both clients' code editors.
 *
 * Separated from the composables so they can be tested without a UI toolkit. These are
 * the operations that make writing Python in a plain text box bearable, and indentation
 * is syntactically significant in Python, so getting them wrong produces code that does
 * not run.
 *
 * ## Why in `:shared` rather than in each client
 *
 * These lived in `:desktopApp` and Android had none of them: no auto-indent after a
 * colon, no dedent on backspace. The phone editor was the harder one to type Python in
 * and had the *less* help, and the difference was invisible because there was nothing to
 * compare against. Copying them across would have meant two implementations of Python's
 * indentation rules drifting apart — the same mistake the colour palette had already
 * made. `:shared` holds no UI toolkit types, and none of this needs one.
 */
object EditorEdits {

    /** Python's indentation unit. Spaces, not a tab: PEP 8, and they are not equivalent. */
    const val INDENT: String = "    "

    /** A text buffer and caret position. */
    data class Edit(val text: String, val caret: Int)

    /** Replace `[start, end)` with [insertion], leaving the caret after it. */
    fun insert(text: String, start: Int, end: Int, insertion: String): Edit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        return Edit(text.replaceRange(from, to, insertion), from + insertion.length)
    }

    /**
     * Insert a newline that carries the current line's indentation.
     *
     * A line ending in `:` opens a block, so the new line gets one extra level. This
     * is what every code editor does, and without it the learner re-indents by hand
     * after every `if`, `for`, and `def`.
     */
    fun newlineWithIndent(text: String, caret: Int): Edit {
        val position = caret.coerceIn(0, text.length)
        val lineStart = lineStartAt(text, position)
        val line = text.substring(lineStart, position)
        val indent = line.takeWhile { it == ' ' }
        // trimEnd so a trailing comment-free `:` still counts even with stray spaces.
        val extra = if (line.trimEnd().endsWith(":")) INDENT else ""
        return insert(text, position, position, "\n$indent$extra")
    }

    /**
     * Remove one indent level when the caret sits inside a line's leading whitespace.
     *
     * Returns null when ordinary backspace should apply instead, so the caller can
     * let the key event through rather than reimplementing character deletion.
     */
    fun dedent(text: String, caret: Int): Edit? {
        val position = caret.coerceIn(0, text.length)
        if (position == 0) return null
        val lineStart = lineStartAt(text, position)
        val before = text.substring(lineStart, position)
        // Only when everything before the caret on this line is spaces, and there is
        // a whole indent level to remove. A partial indent falls through to normal
        // backspace, which is less surprising than silently swallowing 1–3 spaces.
        if (before.isEmpty() || before.any { it != ' ' } || before.length < INDENT.length) return null
        val removeFrom = position - INDENT.length
        return Edit(text.removeRange(removeFrom, position), removeFrom)
    }

    /**
     * A selection spanning lines, with the caret kept over the same lines afterwards.
     *
     * [Edit] carries a single caret, which cannot describe "these three lines are still
     * selected" — and losing the selection after one Tab means indenting a block by two
     * levels is impossible without reselecting.
     */
    data class BlockEdit(val text: String, val selectionStart: Int, val selectionEnd: Int)

    /**
     * Indent every line the selection touches by one level.
     *
     * Without this, Tab with a selection *replaced* it with four spaces: selecting a
     * loop body and pressing Tab — the way every editor indents a block — deleted the
     * body. Ctrl+Z recovered it, so it was destructive rather than unrecoverable, but
     * "select and Tab" is such standard muscle memory that guessing wrong costs the
     * learner their place in the Problem.
     *
     * Blank lines are skipped: indenting whitespace-only lines leaves trailing spaces
     * that no one asked for and some linters object to.
     */
    fun indentBlock(text: String, start: Int, end: Int): BlockEdit =
        reindentBlock(text, start, end) { INDENT + it }

    /**
     * Remove one indent level from every line the selection touches.
     *
     * Also what Shift+Tab does with no selection. Shift+Tab used to fall through to the
     * plain Tab branch — `event.key` is `Key.Tab` either way — so the shortcut for
     * "dedent" indented, which is the opposite of what it says.
     *
     * A line with less than a full level of leading whitespace loses what it has rather
     * than being left alone, so a block that has drifted out of alignment converges to
     * the left margin instead of staying ragged.
     */
    fun dedentBlock(text: String, start: Int, end: Int): BlockEdit =
        reindentBlock(text, start, end) { line ->
            val removable = line.takeWhile { it == ' ' }.length.coerceAtMost(INDENT.length)
            line.substring(removable)
        }

    /**
     * Apply [transform] to each line the range `[start, end]` touches.
     *
     * The returned selection covers exactly the affected lines, from the first line's
     * start to the last line's end. That is slightly wider than what the learner had
     * selected if they had selected part of a line — and deliberately so, because after
     * the indentation of whole lines changed, a selection that still clipped mid-line
     * would no longer describe anything they could see.
     */
    private fun reindentBlock(
        text: String,
        start: Int,
        end: Int,
        transform: (String) -> String,
    ): BlockEdit {
        val from = start.coerceIn(0, text.length)
        val to = end.coerceIn(from, text.length)
        val blockStart = lineStartAt(text, from)
        val blockEnd = lineEndAt(text, to)

        val rewritten = text.substring(blockStart, blockEnd)
            // split, not lines(): lines() on a trailing-newline string yields a phantom
            // final element, and the block never contains its terminating newline.
            .split("\n")
            .joinToString("\n") { line -> if (line.isBlank()) line else transform(line) }

        return BlockEdit(
            text = text.replaceRange(blockStart, blockEnd, rewritten),
            selectionStart = blockStart,
            selectionEnd = blockStart + rewritten.length,
        )
    }

    /** Index just after the newline preceding [position], or 0. */
    private fun lineStartAt(text: String, position: Int): Int {
        if (position == 0) return 0
        val newline = text.lastIndexOf('\n', position - 1)
        return if (newline < 0) 0 else newline + 1
    }

    /** Index of the newline at or after [position], or the end of the text. */
    private fun lineEndAt(text: String, position: Int): Int {
        val newline = text.indexOf('\n', position)
        return if (newline < 0) text.length else newline
    }
}
