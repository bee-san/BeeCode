package dev.bee.beecode.desktop

/**
 * The pure text transformations behind the code editor's keyboard behaviour.
 *
 * Separated from the composable so they can be tested without a UI toolkit. These
 * are the operations that make writing Python in a plain text box bearable, and
 * indentation is syntactically significant in Python, so getting them wrong
 * produces code that does not run.
 */
internal object EditorEdits {

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

    /** Index just after the newline preceding [position], or 0. */
    private fun lineStartAt(text: String, position: Int): Int {
        if (position == 0) return 0
        val newline = text.lastIndexOf('\n', position - 1)
        return if (newline < 0) 0 else newline + 1
    }
}
