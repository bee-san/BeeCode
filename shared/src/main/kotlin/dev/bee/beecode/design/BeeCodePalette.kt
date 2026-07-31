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
    /**
     * A pass, and the Easy difficulty.
     *
     * ## Why the semantic accents live in the palette
     *
     * They used to be four constants in `BeeCodeAccents`, one value each, on the stated
     * reasoning that "a passing test is green whichever scheme is active". That reasoning
     * is right about *hue* and wrong about *lightness*, and the difference shipped a real
     * defect: every one of the four was chosen against the dark scheme and then drawn as
     * **text** on cream. `Success` `#6BBF59` on a light `Card` is **1.787:1**, against a
     * 4.5:1 floor — "All tests passed" was very nearly invisible in light mode, and so was
     * every Easy badge. `Caution` was 1.783:1, `Muted` 2.462:1, `Danger` 2.868:1.
     *
     * `PaletteContrastTest` did not catch it because the accents were not in the palette,
     * so nothing walked them. That is the whole argument for moving them here: this type
     * is what the contrast suite enumerates, and a colour outside it is a colour nobody
     * checks. The same class of bug as the invisible code block in
     * [surfaceContainerHighest] — a value that was mapped correctly and could not be seen.
     *
     * Hue is still constant across schemes; only lightness moves, which is what keeps
     * green meaning "pass" in both.
     *
     * ## Why the three signal accents differ in lightness, not only hue
     *
     * The first corrected set cleared every text threshold and still had a defect: dark
     * `#8ED97A` (success) and `#F0C05A` (caution) are **1.003:1** apart. Different hues,
     * the same luminance — identical in greyscale, and near-identical to a learner with
     * deuteranopia, which is around 6% of men. Contrast against the *background* says
     * nothing about whether two foregrounds can be told apart from each other.
     *
     * So the values are spread along lightness as well: within each scheme, every pair of
     * success/caution/danger is at least 1.25:1 apart. Both properties are asserted —
     * `accentsAreLegibleAsTextEverywhereTheyAreDrawn` for the background, and
     * `theAccentsAreDistinguishableFromEachOther` for each other. The luminance spread is
     * the belt; [BeeCodeAccentGlyphs] is the braces, and it is the one that actually
     * carries the meaning.
     */
    val accentSuccess: Long,
    /** A partial failure or a timeout: something to look at, not something broken. */
    val accentCaution: Long,
    /** A failure, an error, and the Hard difficulty. */
    val accentDanger: Long,
    /** Cancelled or absent: present but not asserting anything. */
    val accentMuted: Long,
) {
    companion object {
        /**
         * Honey amber on warm near-black.
         *
         * The default, and the one the app was designed around. Neutrals carry a small
         * amount of yellow chroma rather than being true greys, so a card reads as
         * being lit by the same light as the amber rather than sitting beside it.
         */
        val HoneyDark: BeeCodePalette = BeeCodePalette(
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
            // Lifted off the old shared constants so they clear AA against a *dark* Card,
            // not merely against the page: #6BBF59 was 5.765:1 on surface but the old
            // Danger and Muted were 3.591:1 and 4.183:1 on surfaceContainerHighest.
            // Lightness is also spread deliberately — see accentSuccess's KDoc.
            accentSuccess = 0xFF5EC970,
            accentCaution = 0xFFF1C253,
            accentDanger = 0xFFF26F61,
            accentMuted = 0xFFB8B0A0,
        )

        /**
         * The same palette for daylight: warm cream rather than the M3 baseline's cool
         * off-white, so the amber does not look like a stain on grey paper.
         */
        val HoneyLight: BeeCodePalette = BeeCodePalette(
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
            // Darkened substantially: these are drawn as text on cream, where the old
            // dark-tuned values ran 1.78:1–2.87:1. Same hues, and now legible.
            accentSuccess = 0xFF1C6F2A,
            accentCaution = 0xFF724903,
            accentDanger = 0xFF841A0F,
            accentMuted = 0xFF5C564A,
        )

        /**
         * Maximum legibility: pure black or white pages, and text at AAA.
         *
         * ## Why this is a theme rather than a toggle
         *
         * A "high contrast mode" that post-processes another palette has to guess which
         * roles are text and which are decoration, and gets the accents wrong for the same
         * reason the old shared constants did. Declaring the values instead means every one
         * of them is walked by the same contrast suite as the default family — at a 7:1
         * floor rather than 4.5:1, asserted in `PaletteContrastTest`.
         *
         * The warm chroma of [HoneyDark] is deliberately dropped. Tinted neutrals cost
         * contrast for atmosphere, which is exactly the wrong trade here, so surfaces are
         * near-neutral and the amber survives only in [primary] — where it stays because a
         * high-contrast theme should still be recognisably BeeCode.
         */
        val HighContrastDark: BeeCodePalette = BeeCodePalette(
            primary = 0xFFFFD24A,
            onPrimary = 0xFF000000,
            primaryContainer = 0xFFFFD24A,
            onPrimaryContainer = 0xFF000000,
            inversePrimary = 0xFF4A3600,
            secondary = 0xFFF0E6D2,
            onSecondary = 0xFF000000,
            secondaryContainer = 0xFF2E2A20,
            onSecondaryContainer = 0xFFFFFFFF,
            tertiary = 0xFFFFC49A,
            onTertiary = 0xFF000000,
            tertiaryContainer = 0xFF3A2415,
            onTertiaryContainer = 0xFFFFFFFF,
            background = 0xFF000000,
            onBackground = 0xFFFFFFFF,
            surface = 0xFF000000,
            onSurface = 0xFFFFFFFF,
            surfaceVariant = 0xFF1C1C1C,
            onSurfaceVariant = 0xFFF0EDE6,
            surfaceTint = 0xFFFFD24A,
            inverseSurface = 0xFFFFFFFF,
            inverseOnSurface = 0xFF000000,
            error = 0xFFFF9A8E,
            onError = 0xFF000000,
            errorContainer = 0xFF5C0F06,
            onErrorContainer = 0xFFFFFFFF,
            // Brighter than the default family's outline: at this contrast level a
            // divider that merely "reads as a rule" is not enough, it has to be crisp.
            outline = 0xFFD6D0C4,
            outlineVariant = 0xFF8A8478,
            scrim = 0xFF000000,
            surfaceBright = 0xFF2A2A2A,
            surfaceDim = 0xFF000000,
            surfaceContainerLowest = 0xFF000000,
            surfaceContainerLow = 0xFF0D0D0B,
            surfaceContainer = 0xFF141410,
            surfaceContainerHigh = 0xFF1C1B17,
            surfaceContainerHighest = 0xFF24231D,
            primaryFixed = 0xFFFFE9B8,
            primaryFixedDim = 0xFFFFD24A,
            onPrimaryFixed = 0xFF000000,
            onPrimaryFixedVariant = 0xFF4A3600,
            secondaryFixed = 0xFFF0E6CC,
            secondaryFixedDim = 0xFFD9C9A3,
            onSecondaryFixed = 0xFF000000,
            onSecondaryFixedVariant = 0xFF3A3323,
            tertiaryFixed = 0xFFFFE0CC,
            tertiaryFixedDim = 0xFFFFC49A,
            onTertiaryFixed = 0xFF000000,
            onTertiaryFixedVariant = 0xFF53301A,
            accentSuccess = 0xFF8CDE9A,
            accentCaution = 0xFFFAE198,
            accentDanger = 0xFFF89181,
            accentMuted = 0xFFD6D0C4,
        )

        /** [HighContrastDark]'s daylight counterpart: black text on white, accents at AAA. */
        val HighContrastLight: BeeCodePalette = BeeCodePalette(
            primary = 0xFF4A3600,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFFFE9B8,
            onPrimaryContainer = 0xFF000000,
            inversePrimary = 0xFFFFD24A,
            secondary = 0xFF3A3323,
            onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFF0E6CC,
            onSecondaryContainer = 0xFF000000,
            tertiary = 0xFF53301A,
            onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFFFE0CC,
            onTertiaryContainer = 0xFF000000,
            background = 0xFFFFFFFF,
            onBackground = 0xFF000000,
            surface = 0xFFFFFFFF,
            onSurface = 0xFF000000,
            surfaceVariant = 0xFFF0EDE6,
            onSurfaceVariant = 0xFF24221C,
            surfaceTint = 0xFF4A3600,
            inverseSurface = 0xFF000000,
            inverseOnSurface = 0xFFFFFFFF,
            error = 0xFF8C0009,
            onError = 0xFFFFFFFF,
            errorContainer = 0xFFFFDAD6,
            onErrorContainer = 0xFF000000,
            outline = 0xFF3A362E,
            outlineVariant = 0xFF6E6A60,
            scrim = 0xFF000000,
            surfaceBright = 0xFFFFFFFF,
            surfaceDim = 0xFFE0DDD4,
            surfaceContainerLowest = 0xFFFFFFFF,
            surfaceContainerLow = 0xFFFAF8F2,
            surfaceContainer = 0xFFF5F2EA,
            surfaceContainerHigh = 0xFFEFECE2,
            surfaceContainerHighest = 0xFFE8E4DA,
            primaryFixed = 0xFFFFE9B8,
            primaryFixedDim = 0xFFFFD24A,
            onPrimaryFixed = 0xFF000000,
            onPrimaryFixedVariant = 0xFF4A3600,
            secondaryFixed = 0xFFF0E6CC,
            secondaryFixedDim = 0xFFD9C9A3,
            onSecondaryFixed = 0xFF000000,
            onSecondaryFixedVariant = 0xFF3A3323,
            tertiaryFixed = 0xFFFFE0CC,
            tertiaryFixedDim = 0xFFFFC49A,
            onTertiaryFixed = 0xFF000000,
            onTertiaryFixedVariant = 0xFF53301A,
            accentSuccess = 0xFF0B4014,
            accentCaution = 0xFF5F3F00,
            accentDanger = 0xFF4E0E04,
            accentMuted = 0xFF3A362E,
        )

        /**
         * Cool desaturated blue-grey, with a cyan primary.
         *
         * The counterpoint to [HoneyDark]: where that one is warm and close, this is cool
         * and quiet. Low-chroma neutrals stay restful over a long session, and the cyan
         * gives the queue's "due" markers a colour that is not competing with the
         * red/amber/green the difficulty badges and run results already use — the one
         * complaint the amber family cannot answer, since its primary *is* amber.
         */
        val SlateDark: BeeCodePalette = BeeCodePalette(
            primary = 0xFF7FD4E8,
            onPrimary = 0xFF00363F,
            primaryContainer = 0xFF1B4650,
            onPrimaryContainer = 0xFFB8EAF8,
            inversePrimary = 0xFF00606E,
            secondary = 0xFFB4C4CC,
            onSecondary = 0xFF1E2C31,
            secondaryContainer = 0xFF33424A,
            onSecondaryContainer = 0xFFD4E3EB,
            tertiary = 0xFFC0C2E8,
            onTertiary = 0xFF282A4D,
            tertiaryContainer = 0xFF3E4165,
            onTertiaryContainer = 0xFFE0E0FF,
            background = 0xFF0E1417,
            onBackground = 0xFFDDE3E7,
            surface = 0xFF0E1417,
            onSurface = 0xFFDDE3E7,
            surfaceVariant = 0xFF212A2E,
            onSurfaceVariant = 0xFFBFC8CC,
            surfaceTint = 0xFF7FD4E8,
            inverseSurface = 0xFFDDE3E7,
            inverseOnSurface = 0xFF2A3135,
            error = 0xFFFFB4A4,
            onError = 0xFF5F1600,
            errorContainer = 0xFF54231A,
            onErrorContainer = 0xFFFFDAD4,
            outline = 0xFF8A9499,
            outlineVariant = 0xFF404A4F,
            scrim = 0xFF000000,
            surfaceBright = 0xFF333C41,
            surfaceDim = 0xFF0E1417,
            surfaceContainerLowest = 0xFF080D0F,
            surfaceContainerLow = 0xFF151C20,
            surfaceContainer = 0xFF1B2327,
            surfaceContainerHigh = 0xFF252E33,
            surfaceContainerHighest = 0xFF2F393E,
            primaryFixed = 0xFFB8EAF8,
            primaryFixedDim = 0xFF7FD4E8,
            onPrimaryFixed = 0xFF001F26,
            onPrimaryFixedVariant = 0xFF004E5A,
            secondaryFixed = 0xFFD4E3EB,
            secondaryFixedDim = 0xFFB4C4CC,
            onSecondaryFixed = 0xFF061E25,
            onSecondaryFixedVariant = 0xFF33424A,
            tertiaryFixed = 0xFFE0E0FF,
            tertiaryFixedDim = 0xFFC0C2E8,
            onTertiaryFixed = 0xFF0A0B33,
            onTertiaryFixedVariant = 0xFF3E4165,
            accentSuccess = 0xFF65CC76,
            accentCaution = 0xFFF2C761,
            accentDanger = 0xFFF48377,
            accentMuted = 0xFFAAB4B9,
        )

        /** [SlateDark] for daylight: cool near-white paper rather than cream. */
        val SlateLight: BeeCodePalette = BeeCodePalette(
            primary = 0xFF00606E,
            onPrimary = 0xFFFFFFFF,
            primaryContainer = 0xFFB8EAF8,
            onPrimaryContainer = 0xFF001F26,
            inversePrimary = 0xFF7FD4E8,
            secondary = 0xFF4A5A62,
            onSecondary = 0xFFFFFFFF,
            secondaryContainer = 0xFFD4E3EB,
            onSecondaryContainer = 0xFF061E25,
            tertiary = 0xFF4E5080,
            onTertiary = 0xFFFFFFFF,
            tertiaryContainer = 0xFFE0E0FF,
            onTertiaryContainer = 0xFF0A0B33,
            background = 0xFFF7FAFC,
            onBackground = 0xFF171D20,
            surface = 0xFFF7FAFC,
            onSurface = 0xFF171D20,
            surfaceVariant = 0xFFDCE4E8,
            onSurfaceVariant = 0xFF40484C,
            surfaceTint = 0xFF00606E,
            inverseSurface = 0xFF2B3134,
            inverseOnSurface = 0xFFEDF2F5,
            error = 0xFFBA1A1A,
            onError = 0xFFFFFFFF,
            errorContainer = 0xFFFFDAD6,
            onErrorContainer = 0xFF410002,
            outline = 0xFF70787C,
            outlineVariant = 0xFFC0C8CC,
            scrim = 0xFF000000,
            surfaceBright = 0xFFFFFFFF,
            surfaceDim = 0xFFD8DEE2,
            surfaceContainerLowest = 0xFFFFFFFF,
            surfaceContainerLow = 0xFFF1F5F8,
            surfaceContainer = 0xFFEBF0F3,
            surfaceContainerHigh = 0xFFE5EAEE,
            surfaceContainerHighest = 0xFFDFE5E9,
            primaryFixed = 0xFFB8EAF8,
            primaryFixedDim = 0xFF7FD4E8,
            onPrimaryFixed = 0xFF001F26,
            onPrimaryFixedVariant = 0xFF004E5A,
            secondaryFixed = 0xFFD4E3EB,
            secondaryFixedDim = 0xFFB4C4CC,
            onSecondaryFixed = 0xFF061E25,
            onSecondaryFixedVariant = 0xFF33424A,
            tertiaryFixed = 0xFFE0E0FF,
            tertiaryFixedDim = 0xFFC0C2E8,
            onTertiaryFixed = 0xFF0A0B33,
            onTertiaryFixedVariant = 0xFF3E4165,
            accentSuccess = 0xFF1C6F2A,
            accentCaution = 0xFF724903,
            accentDanger = 0xFF841A0F,
            accentMuted = 0xFF545C60,
        )
    }
}

/**
 * A pair of palettes that belong together, and the unit a learner actually picks.
 *
 * ## Why a family rather than a longer list of themes
 *
 * The alternative was one flat enum — `HONEY_DARK`, `HONEY_LIGHT`, `HIGH_CONTRAST_DARK`,
 * and so on — which reads simpler until "follow the system" has to fit into it. There is
 * no single entry that means "high contrast, tracking the OS", so either that combination
 * is unavailable or [ThemeChoice.SYSTEM] silently means the default family. Both are worse
 * than two settings, and the list doubles in length with every family added.
 *
 * Splitting them keeps the two questions separate: [ThemeChoice] answers *when* to be
 * dark, this answers *which* dark. Every family must supply both schemes — the type makes
 * a family with only one impossible to declare, which is what stops a learner who prefers
 * light mode from being pushed back to the default family to get it.
 */
enum class ThemeFamily(
    /** What Settings shows. Sentence case on both clients, so the wording cannot drift. */
    val label: String,
    /** One line under the label, explaining who each family is for. */
    val description: String,
    val dark: BeeCodePalette,
    val light: BeeCodePalette,
) {
    HONEY(
        label = "Honey",
        description = "Warm amber. BeeCode's original colours.",
        dark = BeeCodePalette.HoneyDark,
        light = BeeCodePalette.HoneyLight,
    ),
    HIGH_CONTRAST(
        label = "High contrast",
        description = "Maximum legibility. Text meets WCAG AAA.",
        dark = BeeCodePalette.HighContrastDark,
        light = BeeCodePalette.HighContrastLight,
    ),
    SLATE(
        label = "Slate",
        description = "Cool blue-grey, easier on the eyes at length.",
        dark = BeeCodePalette.SlateDark,
        light = BeeCodePalette.SlateLight,
    ),
    ;

    /** The palette for a resolved mode. */
    fun palette(dark: Boolean): BeeCodePalette = if (dark) this.dark else this.light

    companion object {
        /** The family BeeCode uses when nothing is stored. */
        val Default: ThemeFamily = HONEY

        /** Parse a stored value, falling back to [Default] for anything unrecognised. */
        fun parse(stored: String?): ThemeFamily =
            entries.firstOrNull { it.name.equals(stored?.trim(), ignoreCase = true) } ?: Default
    }
}

/**
 * A non-colour cue for each semantic accent.
 *
 * ## Why colour is not enough
 *
 * WCAG 1.4.1 (Use of Colour) requires that colour never be the *only* way information is
 * conveyed. Auditing both clients found one place that was: the run outcome's status dot,
 * an 8dp filled square whose entire content was its tint. Green, amber, and grey squares
 * are one square to a learner with deuteranopia, or on a greyscale screen.
 *
 * The rest of the audit came back clean, which is worth recording so it is not re-fixed:
 * the difficulty badges print "Easy"/"Medium"/"Hard", the due badges print "Overdue by
 * 3 days", and the per-test rows already drew ✓ and ✗ — in all three, colour restates a
 * label rather than carrying meaning alone, which 1.4.1 permits.
 *
 * The fix is a glyph that differs in *shape*, not only in colour — so the meaning survives
 * a greyscale screenshot, which is the cheap way to check it.
 *
 * These are plain strings rather than icon references because `:shared` holds no UI types
 * (see [BeeCodePalette]), and because a glyph survives a screen reader reading it aloud
 * where a tinted vector does not.
 */
object BeeCodeAccentGlyphs {
    /** A pass. */
    const val Success: String = "✓"

    /** A partial failure or a timeout. Distinct in shape from both other states. */
    const val Caution: String = "!"

    /** A failure or an error. */
    const val Danger: String = "✗"

    /** Cancelled or absent: present, but not asserting anything. */
    const val Muted: String = "–"
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

/** The learner's stored theme family, defaulting to [ThemeFamily.Default]. */
fun dev.bee.beecode.persistence.SettingsRepository.themeFamily(): ThemeFamily =
    ThemeFamily.parse(appThemeFamily())

fun dev.bee.beecode.persistence.SettingsRepository.setThemeFamily(
    family: ThemeFamily,
    now: kotlinx.datetime.Instant,
) {
    // The default clears the key, for the same reason SYSTEM does above: "never chose"
    // and "chose the default" should not be two distinguishable states in storage.
    setAppThemeFamily(if (family == ThemeFamily.Default) null else family.name, now)
}

/**
 * The palette to render with, from both stored preferences and the OS signal.
 *
 * One function rather than each client combining the three itself — that is how the two
 * clients drifted the last time colour selection lived in a composable.
 */
fun resolvePalette(
    family: ThemeFamily,
    choice: ThemeChoice,
    systemIsDark: Boolean?,
): BeeCodePalette = family.palette(choice.resolvesToDark(systemIsDark))

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
