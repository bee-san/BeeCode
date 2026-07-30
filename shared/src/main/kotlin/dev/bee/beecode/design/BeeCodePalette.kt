package dev.bee.beecode.design

/**
 * BeeCode's colours, as one source both clients read.
 *
 * ## Why this exists
 *
 * Each client used to declare its own palette inline, and they had already drifted:
 * desktop set `errorContainer` and Android did not, and light `surface` was
 * `#FFFCF7` on one and `#FFF8F0` on the other. Nothing could catch that, because a
 * colour scheme built in a `@Composable` is not comparable to one built in another
 * module.
 *
 * ## Why plain Longs rather than a Material ColorScheme
 *
 * `:shared` is deliberately UI-free — the whole study loop is testable without a UI
 * toolkit, which is how the fail-then-fix journey has an automated test. It also
 * cannot hold a Compose type even if that were wanted: Compose Multiplatform's
 * `material3` resolves to `material3-desktop` on a plain JVM module, and an Android
 * client cannot consume that artifact. Sharing a `ColorScheme` would need a full
 * Kotlin Multiplatform module with `android` and `jvm` targets.
 *
 * So the *values* live here as ARGB, and each client maps them onto its own
 * `ColorScheme`. The mapping is mechanical, and each client has a test that walks
 * every role reflectively and asserts it equals the palette — so a role left unmapped
 * or wired to the wrong field fails the build rather than shipping.
 *
 * ## Why every role is set
 *
 * Material 3 has 48 colour roles and both clients used to set 16. The other 32 fell
 * back to the M3 *baseline*, which is purple: `surfaceContainerHighest` resolved to
 * `#E6E0E9`, which is what `Card` fills with, `outlineVariant` to `#CAC4D0`, which is
 * what every divider draws, and `secondaryContainer` to `#E8DEF8`, which is the
 * navigation rail's active pill. The result was lavender cards and purple-grey rules
 * on a warm cream background — an amber app whose actual surfaces were all purple.
 * Leaving a role unset is not neutral; it is a vote for someone else's brand.
 *
 * The neutrals here are warm on purpose: BeeCode is for practising at 5am, and the
 * dark scheme is the one that hour deserves.
 */
data class BeeCodePalette(
    val primary: Long,
    val onPrimary: Long,
    val primaryContainer: Long,
    val onPrimaryContainer: Long,
    val inversePrimary: Long,
    val secondary: Long,
    val onSecondary: Long,
    val secondaryContainer: Long,
    val onSecondaryContainer: Long,
    val tertiary: Long,
    val onTertiary: Long,
    val tertiaryContainer: Long,
    val onTertiaryContainer: Long,
    val background: Long,
    val onBackground: Long,
    val surface: Long,
    val onSurface: Long,
    val surfaceVariant: Long,
    val onSurfaceVariant: Long,
    val surfaceTint: Long,
    val inverseSurface: Long,
    val inverseOnSurface: Long,
    val error: Long,
    val onError: Long,
    val errorContainer: Long,
    val onErrorContainer: Long,
    val outline: Long,
    val outlineVariant: Long,
    val scrim: Long,
    val surfaceBright: Long,
    val surfaceDim: Long,
    val surfaceContainer: Long,
    val surfaceContainerHigh: Long,
    /**
     * What a `Card` fills with — and so the background every inset surface sits on.
     *
     * ## Why this role is called out
     *
     * Because getting it wrong is what let an invisible boundary ship *twice*.
     *
     * Both clients drew code blocks, examples, test output, and the activity chart's
     * empty bars on `surfaceVariant`, and every one of those sits inside a `Card`. On a
     * device that came back with the pixel `(237,225,207)` on both sides of the boundary
     * — light `surfaceVariant` `#EDE1CF` on this role's `#ECE3D1` is **1.012:1**. A code
     * block was not a block at all, just monospace text lying on the card.
     *
     * That defect was light-only: dark `surfaceVariant` is 1.135:1 against a card, which is
     * visible. It was recorded as "1.012:1 in dark too", and that number came from
     * measuring against `surfaceContainerHigh` — the same wrong background as the first
     * fix below.
     *
     * The first fix reached for [surfaceDim] in light and [surfaceBright] in dark, on the
     * reasoning that M3 defines those as surfaces *off* the elevation ramp and that they
     * move in opposite directions. Sound reasoning, wrong background: it was measured
     * against `surfaceContainerHigh`, giving 1.157:1 and 1.199:1, and the new test
     * asserted the same wrong pairing — so it passed while the device rendered dark
     * `surfaceBright` `#383327` on `#353024`, **1.045:1**. Which is what a screenshot
     * showed, and what closed the question: `FilledCardTokens.ContainerColor` is
     * `SurfaceContainerHighest`, verified by `javap` on the shipped `material3-android`
     * bytecode, and the probed card pixel `#353024` is this field exactly.
     *
     * ## What an inset surface uses instead
     *
     * `surface` — the page's own colour. 1.207:1 in light, 1.447:1 in dark, both clear of
     * the floor, with body text at 8.87:1 or better. No other role manages both schemes:
     * light `surfaceDim` is 1.087:1 here and dark `surfaceBright` 1.045:1, and the
     * container ramp is no help above a card because every step in it is a *lift* —
     * `surfaceContainerHigh` under `Highest` is 1.065:1 in light.
     *
     * It also needs no branch on the active scheme, which is the part that matters
     * structurally. The scheme-dependent version needed a composition local in each
     * client to carry the choice down; "the same colour as the page behind the card"
     * needs nothing, and there is no second place to keep in step. Reading as *recessed*
     * rather than raised is the honest description of a code block anyway.
     *
     * `PaletteContrastTest` asserts against this role, by name, for both schemes.
     */
    val surfaceContainerHighest: Long,
    val surfaceContainerLow: Long,
    val surfaceContainerLowest: Long,
    val primaryFixed: Long,
    val primaryFixedDim: Long,
    val onPrimaryFixed: Long,
    val onPrimaryFixedVariant: Long,
    val secondaryFixed: Long,
    val secondaryFixedDim: Long,
    val onSecondaryFixed: Long,
    val onSecondaryFixedVariant: Long,
    val tertiaryFixed: Long,
    val tertiaryFixedDim: Long,
    val onTertiaryFixed: Long,
    val onTertiaryFixedVariant: Long,
) {
    companion object {
        /**
         * Honey amber on warm near-black.
         *
         * The default, and the one the app was designed around. Neutrals carry a small
         * amount of yellow chroma rather than being true greys, so a card reads as
         * being lit by the same light as the amber rather than sitting beside it.
         */
        val Dark: BeeCodePalette = BeeCodePalette(
            primary = 0xFFF2B32C,
            onPrimary = 0xFF241A00,
            primaryContainer = 0xFF3A2E0A,
            onPrimaryContainer = 0xFFFFDF9E,
            inversePrimary = 0xFF6E5100,
            secondary = 0xFFD9C9A3,
            onSecondary = 0xFF2B2413,
            secondaryContainer = 0xFF3E3726,
            onSecondaryContainer = 0xFFF1E3C2,
            tertiary = 0xFFE0A886,
            onTertiary = 0xFF44240F,
            tertiaryContainer = 0xFF5D3A22,
            onTertiaryContainer = 0xFFFFD9C2,
            background = 0xFF12100C,
            onBackground = 0xFFE9E2D4,
            surface = 0xFF12100C,
            onSurface = 0xFFE9E2D4,
            surfaceVariant = 0xFF2A2720,
            onSurfaceVariant = 0xFFCEC6B4,
            surfaceTint = 0xFFF2B32C,
            inverseSurface = 0xFFE9E2D4,
            inverseOnSurface = 0xFF302C24,
            error = 0xFFFFB4A4,
            onError = 0xFF5F1600,
            errorContainer = 0xFF5A2318,
            onErrorContainer = 0xFFFFDAD4,
            outline = 0xFF9A9280,
            // The divider colour. Visibly a rule, not a seam that reads as a rendering
            // artifact, and warm rather than the baseline's #CAC4D0.
            outlineVariant = 0xFF4A4638,
            scrim = 0xFF000000,
            surfaceBright = 0xFF383327,
            surfaceDim = 0xFF12100C,
            // The surfaceContainer ramp is what Card, Sheet, and Menu actually fill
            // with. Each step is a real, visible lift off the background: a card that
            // cannot be told apart from what it sits on is not a card.
            surfaceContainerLowest = 0xFF0C0A07,
            surfaceContainerLow = 0xFF1A1712,
            surfaceContainer = 0xFF201D16,
            surfaceContainerHigh = 0xFF2A261D,
            surfaceContainerHighest = 0xFF353024,
            // The "fixed" roles keep one value across both schemes, for surfaces that
            // must not flip with the theme. Unused today, set so they cannot fall back.
            primaryFixed = 0xFFFFDF9E,
            primaryFixedDim = 0xFFF2B32C,
            onPrimaryFixed = 0xFF241A00,
            onPrimaryFixedVariant = 0xFF553F00,
            secondaryFixed = 0xFFF1E3C2,
            secondaryFixedDim = 0xFFD9C9A3,
            onSecondaryFixed = 0xFF211B0B,
            onSecondaryFixedVariant = 0xFF524629,
            tertiaryFixed = 0xFFFFD9C2,
            tertiaryFixedDim = 0xFFE0A886,
            onTertiaryFixed = 0xFF2E1607,
            onTertiaryFixedVariant = 0xFF5D3A22,
        )

        /**
         * The same palette for daylight: warm cream rather than the M3 baseline's cool
         * off-white, so the amber does not look like a stain on grey paper.
         */
        val Light: BeeCodePalette = BeeCodePalette(
            primary = 0xFF7A5900,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFFDF9E,
            onPrimaryContainer = 0xFF261A00,
            inversePrimary = 0xFFF2B32C,
            secondary = 0xFF6A5D3F,
            onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFF3E5C5,
            onSecondaryContainer = 0xFF241A05,
            tertiary = 0xFF7C4E31,
            onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFFDBC7,
            onTertiaryContainer = 0xFF2E1607,
            background = 0xFFFFF8EC,
            onBackground = 0xFF1E1B16,
            surface = 0xFFFFF8EC,
            onSurface = 0xFF1E1B16,
            surfaceVariant = 0xFFEDE1CF,
            onSurfaceVariant = 0xFF4C4639,
            surfaceTint = 0xFF7A5900,
            inverseSurface = 0xFF34302A,
            inverseOnSurface = 0xFFF8F0E2,
            error = 0xFFBA1A1A,
            onError = 0xFFFFFFFF,
            errorContainer = 0xFFFFDAD6,
            onErrorContainer = 0xFF410002,
            outline = 0xFF7F7767,
            outlineVariant = 0xFFD3C8B3,
            scrim = 0xFF000000,
            surfaceBright = 0xFFFFFBF2,
            surfaceDim = 0xFFE4DAC8,
            surfaceContainerLowest = 0xFFFFFFFF,
            surfaceContainerLow = 0xFFFDF5E7,
            surfaceContainer = 0xFFF8F0E1,
            surfaceContainerHigh = 0xFFF2EAD9,
            surfaceContainerHighest = 0xFFECE3D1,
            primaryFixed = 0xFFFFDF9E,
            primaryFixedDim = 0xFFF2B32C,
            onPrimaryFixed = 0xFF241A00,
            onPrimaryFixedVariant = 0xFF553F00,
            secondaryFixed = 0xFFF1E3C2,
            secondaryFixedDim = 0xFFD9C9A3,
            onSecondaryFixed = 0xFF211B0B,
            onSecondaryFixedVariant = 0xFF524629,
            tertiaryFixed = 0xFFFFD9C2,
            tertiaryFixedDim = 0xFFE0A886,
            onTertiaryFixed = 0xFF2E1607,
            onTertiaryFixedVariant = 0xFF5D3A22,
        )
    }
}

/**
 * Semantic accents that are not Material roles.
 *
 * Difficulty badges and test-result ticks were hard-coded in both clients, with the
 * same six literals copied into each. They are not theme roles — a passing test is
 * green whichever scheme is active — but they are still shared vocabulary, and having
 * them in one place is the difference between "green" meaning one thing and two.
 */
object BeeCodeAccents {
    /** A pass, and the Easy difficulty. */
    const val Success: Long = 0xFF6BBF59

    /** A partial failure or a timeout: something to look at, not something broken. */
    const val Caution: Long = 0xFFE0A030

    /** A failure, an error, and the Hard difficulty. */
    const val Danger: Long = 0xFFE05A4F

    /** Cancelled or absent: present but not asserting anything. */
    const val Muted: Long = 0xFF98917F
}

/**
 * Which colour scheme to use.
 *
 * A stored preference rather than only the OS signal, because the OS signal is not
 * always available: on Linux desktop skiko's `currentSystemTheme` returns `UNKNOWN`
 * (verified on this host), so `isSystemInDarkTheme()` is false and BeeCode booted
 * into its light scheme — the one an app built for 5am practice least wants to show.
 * [SYSTEM] resolves that ambiguity toward dark rather than leaving it to a default
 * that happens to mean "light".
 */
enum class ThemeChoice {
    /** Follow the OS, resolving "unknown" to dark. */
    SYSTEM,
    DARK,
    LIGHT,
    ;

    companion object {
        /** Parse a stored value, falling back to [SYSTEM] for anything unrecognised. */
        fun parse(stored: String?): ThemeChoice =
            entries.firstOrNull { it.name.equals(stored?.trim(), ignoreCase = true) } ?: SYSTEM
    }
}

/**
 * Resolve a choice against the OS signal.
 *
 * @param systemIsDark what the platform reports, or null when it does not know.
 *   Null resolves to dark under [ThemeChoice.SYSTEM] — see [ThemeChoice].
 */
fun ThemeChoice.resolvesToDark(systemIsDark: Boolean?): Boolean = when (this) {
    ThemeChoice.DARK -> true
    ThemeChoice.LIGHT -> false
    ThemeChoice.SYSTEM -> systemIsDark ?: true
}

/**
 * The learner's stored theme choice.
 *
 * An extension rather than a method on [dev.bee.beecode.persistence.SettingsRepository]
 * because [ThemeChoice] lives here and `:persistence` does not depend on `:shared`.
 * The repository stores the string; this owns what the string means.
 */
fun dev.bee.beecode.persistence.SettingsRepository.themeChoice(): ThemeChoice =
    ThemeChoice.parse(appTheme())

fun dev.bee.beecode.persistence.SettingsRepository.setThemeChoice(
    choice: ThemeChoice,
    now: kotlinx.datetime.Instant,
) {
    // SYSTEM clears the key rather than storing "SYSTEM", so the absence of a
    // preference and an explicit "follow the system" are the same state. One fewer
    // way for storage to disagree with itself.
    setAppTheme(if (choice == ThemeChoice.SYSTEM) null else choice.name, now)
}

/**
 * BeeCode's type scale, as one source both clients read.
 *
 * ## Why the default scale was not enough
 *
 * Material's baseline sizes are tuned for content-first apps. BeeCode is a dense tool
 * screen — a queue of rows, a statement pane, an editor, a results list — and 47 of the
 * desktop client's 72 text calls landed on `bodySmall` or `labelSmall`, both 11–12sp.
 * That is not a scale, it is one size with two names: card headings sat 2sp from their
 * own body text, so nothing established what to read first.
 *
 * These sizes widen the gaps that carry hierarchy — a title is clearly a title — while
 * lifting the smallest body text off 11sp, which is below comfortable reading on a
 * desktop monitor at arm's length.
 *
 * Sizes are `Float` sp and weights are the numeric `FontWeight` values, because
 * `:shared` holds no Compose types (see [BeeCodePalette]).
 */
data class BeeCodeTypeScale(
    val sizeSp: Float,
    val lineHeightSp: Float,
    val weight: Int,
    /** Hundredths of an sp, as Material expresses letter spacing. */
    val letterSpacingSp: Float = 0f,
) {
    companion object {
        /** Screen titles: "BeeCode", "Progress". One per screen. */
        val Headline: BeeCodeTypeScale = BeeCodeTypeScale(28f, 34f, 700, -0.25f)

        /** A Problem's title on its own screen, and the big number on a stat tile. */
        val Title: BeeCodeTypeScale = BeeCodeTypeScale(21f, 27f, 600)

        /** A queue row's Problem title, and a card's own heading. */
        val Subtitle: BeeCodeTypeScale = BeeCodeTypeScale(16f, 22f, 600)

        /** A section heading inside a card. Distinct from its body, which it was not. */
        val SectionLabel: BeeCodeTypeScale = BeeCodeTypeScale(13f, 18f, 700, 0.6f)

        /** The default reading size: statements, explanations, prose. */
        val Body: BeeCodeTypeScale = BeeCodeTypeScale(15f, 22f, 400)

        /** Secondary prose: a row's subtitle, a card's supporting sentence. */
        val BodySmall: BeeCodeTypeScale = BeeCodeTypeScale(13f, 19f, 400)

        /** Buttons and tabs. */
        val Action: BeeCodeTypeScale = BeeCodeTypeScale(14f, 20f, 600, 0.1f)

        /** Badges, counts, timings. The smallest text BeeCode uses. */
        val Caption: BeeCodeTypeScale = BeeCodeTypeScale(12f, 16f, 600, 0.4f)
    }
}
