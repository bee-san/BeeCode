package dev.bee.beecode.design

/**
 * Pure cursor and buffer operations used by BeeCode's bounded Vim keymap.
 *
 * This is intentionally a useful editing mode, not a claim of full Vim emulation.
 * Keeping the transformations independent of Compose makes their edge cases testable.
 */
object VimEdits {
    fun moveHorizontal(text: String, caret: Int, delta: Int): EditorEdits.SelectionEdit {
        val position = caret.coerceIn(0, text.length)
        val start = lineStart(text, position)
        val end = lineEnd(text, position)
        return selection(text, (position + delta).coerceIn(start, end))
    }

    fun moveVertical(text: String, caret: Int, delta: Int): EditorEdits.SelectionEdit {
        val position = caret.coerceIn(0, text.length)
        val currentStart = lineStart(text, position)
        val column = position - currentStart
        val targetStart = when {
            delta < 0 && currentStart > 0 -> lineStart(text, currentStart - 1)
            delta > 0 -> {
                val currentEnd = lineEnd(text, position)
                if (currentEnd < text.length) currentEnd + 1 else currentStart
            }
            else -> currentStart
        }
        val target = (targetStart + column).coerceAtMost(lineEnd(text, targetStart))
        return selection(text, target)
    }

    fun lineStart(text: String, caret: Int): Int {
        val position = caret.coerceIn(0, text.length)
        if (position == 0) return 0
        return text.lastIndexOf('\n', position - 1).let { if (it < 0) 0 else it + 1 }
    }

    fun firstNonWhitespace(text: String, caret: Int): Int {
        val start = lineStart(text, caret)
        val end = lineEnd(text, caret)
        var position = start
        while (position < end && text[position].isWhitespace()) position++
        return position
    }

    fun lineEnd(text: String, caret: Int): Int {
        val position = caret.coerceIn(0, text.length)
        return text.indexOf('\n', position).let { if (it < 0) text.length else it }
    }

    fun nextWord(text: String, caret: Int): EditorEdits.SelectionEdit {
        var position = caret.coerceIn(0, text.length)
        if (position < text.length && !text[position].isWhitespace()) {
            val word = text[position].isWordCharacter()
            while (
                position < text.length &&
                !text[position].isWhitespace() &&
                text[position].isWordCharacter() == word
            ) {
                position++
            }
        }
        while (position < text.length && text[position].isWhitespace()) position++
        return selection(text, position)
    }

    fun previousWord(text: String, caret: Int): EditorEdits.SelectionEdit {
        var position = caret.coerceIn(0, text.length)
        if (position == 0) return selection(text, 0)
        position--
        while (position > 0 && text[position].isWhitespace()) position--
        val word = text[position].isWordCharacter()
        while (
            position > 0 &&
            !text[position - 1].isWhitespace() &&
            text[position - 1].isWordCharacter() == word
        ) {
            position--
        }
        return selection(text, position)
    }

    fun deleteCharacter(text: String, caret: Int): EditorEdits.SelectionEdit? {
        val position = caret.coerceIn(0, text.length)
        if (position >= text.length || text[position] == '\n') return null
        return selection(text.removeRange(position, position + 1), position)
    }

    fun deleteLine(text: String, caret: Int): EditorEdits.SelectionEdit {
        val start = lineStart(text, caret)
        val end = lineEnd(text, caret)
        return when {
            end < text.length -> selection(text.removeRange(start, end + 1), start)
            start > 0 -> selection(text.removeRange(start - 1, text.length), start - 1)
            else -> selection("", 0)
        }
    }

    fun openLineBelow(text: String, caret: Int): EditorEdits.SelectionEdit {
        val start = lineStart(text, caret)
        val end = lineEnd(text, caret)
        val indent = text.substring(start, end).takeWhile { it == ' ' }
        val insertion = "\n$indent"
        return selection(text.replaceRange(end, end, insertion), end + insertion.length)
    }

    fun openLineAbove(text: String, caret: Int): EditorEdits.SelectionEdit {
        val start = lineStart(text, caret)
        val end = lineEnd(text, caret)
        val indent = text.substring(start, end).takeWhile { it == ' ' }
        return selection(text.replaceRange(start, start, "$indent\n"), start + indent.length)
    }

    private fun selection(text: String, caret: Int): EditorEdits.SelectionEdit {
        val bounded = caret.coerceIn(0, text.length)
        return EditorEdits.SelectionEdit(text, bounded, bounded)
    }

    private fun Char.isWordCharacter(): Boolean = this == '_' || isLetterOrDigit()
}
