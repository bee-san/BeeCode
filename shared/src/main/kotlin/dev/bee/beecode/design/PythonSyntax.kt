package dev.bee.beecode.design

/**
 * A deliberately lexical Python highlighter.
 *
 * It never claims that code is valid and never feeds execution. Malformed or incomplete
 * input simply produces fewer tokens, which is the safe failure mode while a learner is
 * halfway through typing.
 */
object PythonSyntax {
    enum class Kind {
        KEYWORD,
        BUILTIN,
        DEFINITION,
        STRING,
        NUMBER,
        COMMENT,
    }

    data class Token(val start: Int, val end: Int, val kind: Kind) {
        init {
            require(start >= 0)
            require(end > start)
        }
    }

    data class BracketMatch(val opening: Int, val closing: Int)

    private val keywords = setOf(
        "False", "None", "True", "and", "as", "assert", "async", "await", "break",
        "case", "class", "continue", "def", "del", "elif", "else", "except", "finally",
        "for", "from", "global", "if", "import", "in", "is", "lambda", "match",
        "nonlocal", "not", "or", "pass", "raise", "return", "try", "while", "with",
        "yield",
    )

    private val builtins = setOf(
        "abs", "all", "any", "bool", "dict", "enumerate", "filter", "float", "int",
        "len", "list", "map", "max", "min", "print", "range", "reversed", "set",
        "sorted", "str", "sum", "tuple", "zip",
    )

    fun tokens(source: String): List<Token> {
        val result = mutableListOf<Token>()
        var index = 0
        var definitionExpected = false
        while (index < source.length) {
            val char = source[index]
            when {
                char == '#' -> {
                    val end = source.indexOf('\n', index).let { if (it < 0) source.length else it }
                    result += Token(index, end, Kind.COMMENT)
                    index = end
                }
                char == '\'' || char == '"' -> {
                    val end = stringEnd(source, index, char)
                    result += Token(index, end, Kind.STRING)
                    index = end
                }
                char.isDigit() -> {
                    val end = numberEnd(source, index)
                    result += Token(index, end, Kind.NUMBER)
                    index = end
                }
                char == '_' || char.isLetter() -> {
                    val end = identifierEnd(source, index)
                    val identifier = source.substring(index, end)
                    val kind = when {
                        definitionExpected -> Kind.DEFINITION
                        identifier in keywords -> Kind.KEYWORD
                        identifier in builtins -> Kind.BUILTIN
                        else -> null
                    }
                    if (kind != null) result += Token(index, end, kind)
                    definitionExpected = identifier == "def" || identifier == "class"
                    index = end
                }
                !char.isWhitespace() -> {
                    definitionExpected = false
                    index++
                }
                else -> index++
            }
        }
        return result
    }

    fun matchingBracket(source: String, caret: Int): BracketMatch? {
        if (source.isEmpty()) return null
        val position = listOf(caret - 1, caret)
            .firstOrNull { it in source.indices && source[it] in "()[]{}" }
            ?: return null
        val ignored = tokens(source)
            .filter { it.kind == Kind.STRING || it.kind == Kind.COMMENT }
        if (ignored.any { position in it.start until it.end }) return null

        val char = source[position]
        val opening = "([{"
        val closing = ")]}"
        val openingIndex = opening.indexOf(char)
        val direction = if (openingIndex >= 0) 1 else -1
        val pairIndex = if (openingIndex >= 0) openingIndex else closing.indexOf(char)
        val open = opening[pairIndex]
        val close = closing[pairIndex]
        var depth = 0
        var index = position
        while (index in source.indices) {
            if (ignored.none { index in it.start until it.end }) {
                when (source[index]) {
                    open -> depth += direction
                    close -> depth -= direction
                }
                if (depth == 0 && index != position) {
                    return if (direction > 0) {
                        BracketMatch(position, index)
                    } else {
                        BracketMatch(index, position)
                    }
                }
            }
            index += direction
        }
        return null
    }

    private fun identifierEnd(source: String, start: Int): Int {
        var end = start + 1
        while (end < source.length && (source[end] == '_' || source[end].isLetterOrDigit())) end++
        return end
    }

    private fun numberEnd(source: String, start: Int): Int {
        var end = start + 1
        while (end < source.length && (source[end].isLetterOrDigit() || source[end] in "._")) end++
        return end
    }

    private fun stringEnd(source: String, start: Int, quote: Char): Int {
        val triple = source.regionMatches(start, "$quote$quote$quote", 0, 3)
        val delimiterLength = if (triple) 3 else 1
        var index = start + delimiterLength
        while (index < source.length) {
            if (source[index] == '\\') {
                index = (index + 2).coerceAtMost(source.length)
                continue
            }
            if (source.regionMatches(index, quote.toString().repeat(delimiterLength), 0, delimiterLength)) {
                return index + delimiterLength
            }
            if (!triple && source[index] == '\n') return index
            index++
        }
        return source.length
    }
}

/** A bounded undo/redo stack of complete editor values. */
class EditorHistory(initial: Snapshot, private val limit: Int = 200) {
    data class Snapshot(val text: String, val selectionStart: Int, val selectionEnd: Int)

    private val undo = ArrayDeque<Snapshot>()
    private val redo = ArrayDeque<Snapshot>()
    private var latest = initial

    val canUndo: Boolean get() = undo.isNotEmpty()
    val canRedo: Boolean get() = redo.isNotEmpty()

    fun record(next: Snapshot) {
        if (next == latest) return
        if (next.text == latest.text) {
            latest = next
            return
        }
        undo.addLast(latest)
        while (undo.size > limit) undo.removeFirst()
        redo.clear()
        latest = next
    }

    fun adoptExternal(next: Snapshot) {
        record(next)
    }

    fun undo(): Snapshot? {
        val previous = undo.removeLastOrNull() ?: return null
        redo.addLast(latest)
        latest = previous
        return previous
    }

    fun redo(): Snapshot? {
        val next = redo.removeLastOrNull() ?: return null
        undo.addLast(latest)
        latest = next
        return next
    }
}

/**
 * Convert a one-based Unicode code-point line/column into Kotlin's UTF-16 offset.
 *
 * Invalid positions are clamped to the nearest source boundary so malformed runner
 * diagnostics can never crash the editor.
 */
fun sourceOffset(source: String, line: Int, column: Int?): Int {
    val targetLine = line.coerceAtLeast(1)
    var lineStart = 0
    repeat(targetLine - 1) {
        val newline = source.indexOf('\n', lineStart)
        if (newline < 0) return source.length
        lineStart = newline + 1
    }
    if (column == null) return lineStart
    val lineEnd = source.indexOf('\n', lineStart).let { if (it < 0) source.length else it }
    val codePoints = (column - 1).coerceAtLeast(0)
    return runCatching {
        Character.offsetByCodePoints(source, lineStart, codePoints).coerceAtMost(lineEnd)
    }.getOrDefault(lineEnd)
}
