package dev.bee.beecode.design

import dev.bee.beecode.persistence.SettingsRepository
import kotlinx.datetime.Instant

/**
 * The settings that change how one platform's editor is presented.
 *
 * Keys are platform-scoped because a comfortable phone font and wrapping choice need
 * not be the right desktop choice. They still sync and export through the ordinary
 * settings table, so another device of the same kind picks them up.
 */
data class EditorPreferences(
    val wrapLines: Boolean,
    val fontSizeSp: Int,
    val keymap: EditorKeymap = EditorKeymap.STANDARD,
    val mobileActions: List<MobileEditorAction> = MobileEditorAction.DEFAULTS,
) {
    init {
        require(fontSizeSp in MIN_FONT_SIZE_SP..MAX_FONT_SIZE_SP)
        require(mobileActions.distinct().size == mobileActions.size)
    }

    companion object {
        const val MIN_FONT_SIZE_SP: Int = 12
        const val MAX_FONT_SIZE_SP: Int = 20

        val DesktopDefault: EditorPreferences = EditorPreferences(
            wrapLines = false,
            fontSizeSp = 14,
        )
        val AndroidDefault: EditorPreferences = EditorPreferences(
            wrapLines = false,
            fontSizeSp = 13,
        )
    }
}

/** Keyboard behavior for the desktop editor. */
enum class EditorKeymap {
    STANDARD,
    VIM,
}

/** One command available in Android's editing bar. */
enum class MobileEditorAction(val label: String) {
    INDENT("Insert indent"),
    OUTDENT("Outdent"),
    CURSOR_LEFT("Move cursor left"),
    CURSOR_RIGHT("Move cursor right"),
    UNDO("Undo"),
    REDO("Redo"),
    COLON("Insert :"),
    PARENTHESES("Insert ("),
    BRACKETS("Insert ["),
    BRACES("Insert {"),
    DOUBLE_QUOTE("Insert \""),
    UNDERSCORE("Insert _"),
    EQUALS("Insert ="),
    LESS_THAN("Insert <"),
    GREATER_THAN("Insert >"),
    PLUS("Insert +"),
    MINUS("Insert -"),
    ASTERISK("Insert *"),
    SLASH("Insert /"),
    PERCENT("Insert %"),
    DOT("Insert ."),
    COMMA("Insert ,"),
    HASH("Insert #"),
    ;

    companion object {
        private const val EMPTY_ENCODING = "NONE"

        val DEFAULTS: List<MobileEditorAction> = listOf(
            INDENT,
            OUTDENT,
            CURSOR_LEFT,
            CURSOR_RIGHT,
            UNDO,
            REDO,
            COLON,
            PARENTHESES,
            BRACKETS,
            BRACES,
            DOUBLE_QUOTE,
            UNDERSCORE,
            EQUALS,
            LESS_THAN,
            GREATER_THAN,
            PLUS,
            MINUS,
            ASTERISK,
            SLASH,
            PERCENT,
            DOT,
            COMMA,
            HASH,
        )

        fun parse(encoded: String?): List<MobileEditorAction> {
            if (encoded.isNullOrBlank()) return DEFAULTS
            if (encoded == EMPTY_ENCODING) return emptyList()
            val parsed = encoded.split(',')
                .mapNotNull { raw -> entries.firstOrNull { it.name == raw.trim() } }
                .distinct()
            return parsed.ifEmpty { DEFAULTS }
        }

        fun encode(actions: List<MobileEditorAction>): String =
            if (actions.isEmpty()) EMPTY_ENCODING else actions.joinToString(",") { it.name }
    }
}

enum class EditorPlatform(val storageName: String, val defaults: EditorPreferences) {
    DESKTOP("desktop", EditorPreferences.DesktopDefault),
    ANDROID("android", EditorPreferences.AndroidDefault),
}

fun SettingsRepository.editorPreferences(platform: EditorPlatform): EditorPreferences {
    val prefix = "editor.${platform.storageName}"
    val defaults = platform.defaults
    val fontSize = get("$prefix.fontSizeSp")
        ?.toIntOrNull()
        ?.takeIf { it in EditorPreferences.MIN_FONT_SIZE_SP..EditorPreferences.MAX_FONT_SIZE_SP }
        ?: defaults.fontSizeSp
    val actions = if (platform == EditorPlatform.ANDROID) {
        MobileEditorAction.parse(get("$prefix.toolbar"))
    } else {
        defaults.mobileActions
    }
    return EditorPreferences(
        wrapLines = get("$prefix.wrap")?.toBooleanStrictOrNull() ?: defaults.wrapLines,
        fontSizeSp = fontSize,
        keymap = get("$prefix.keymap")
            ?.let { stored -> EditorKeymap.entries.firstOrNull { it.name == stored } }
            ?: defaults.keymap,
        mobileActions = actions,
    )
}

fun SettingsRepository.setEditorWrap(platform: EditorPlatform, wrap: Boolean, now: Instant) {
    put("editor.${platform.storageName}.wrap", wrap.toString(), now)
}

fun SettingsRepository.setEditorFontSize(
    platform: EditorPlatform,
    fontSizeSp: Int,
    now: Instant,
) {
    val bounded = fontSizeSp.coerceIn(
        EditorPreferences.MIN_FONT_SIZE_SP,
        EditorPreferences.MAX_FONT_SIZE_SP,
    )
    put("editor.${platform.storageName}.fontSizeSp", bounded.toString(), now)
}

fun SettingsRepository.setEditorKeymap(
    platform: EditorPlatform,
    keymap: EditorKeymap,
    now: Instant,
) {
    val storageKey = "editor.${platform.storageName}.keymap"
    if (keymap == platform.defaults.keymap) {
        remove(storageKey)
    } else {
        put(storageKey, keymap.name, now)
    }
}

fun SettingsRepository.setMobileEditorActions(actions: List<MobileEditorAction>, now: Instant) {
    val normalized = actions.distinct()
    if (normalized == MobileEditorAction.DEFAULTS) {
        remove("editor.android.toolbar")
    } else {
        put("editor.android.toolbar", MobileEditorAction.encode(normalized), now)
    }
}
