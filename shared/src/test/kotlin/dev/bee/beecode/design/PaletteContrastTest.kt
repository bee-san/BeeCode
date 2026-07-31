package dev.bee.beecode.design

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contrast assertions on every [BeeCodePalette] in every [ThemeFamily].
 *
 * ## Why this exists
 *
 * `BeeCodePaletteTest` and `AndroidThemeTest` assert that every Material role is
 * *mapped*, which is a real guarantee and was worth having — but both clients passed
 * them while shipping a code block that was invisible. The fill behind code was
 * `surfaceVariant` at `#EDE1CF`, the `Card` under it `#ECE3D1`, and a device screenshot
 * came back with the pixel `(237,225,207)` on both sides of the boundary. Every role was
 * mapped, correctly, to a colour that could not be seen.
 *
 * So "is it wired up" and "can you see it" are different questions, and only the first
 * had a test. These assert the second: that a surface which must read as distinct from
 * what it sits on actually does, and that text on each surface stays legible.
 *
 * ## Why it walks families rather than two palettes
 *
 * Because a hand-written palette is exactly the kind of artifact that passes review and
 * fails a measurement. Adding a family means writing 52 colours by hand, and the failure
 * mode is not a typo — it is a value that looks right in a swatch and is 2:1 against the
 * text drawn on it. Enumerating [ThemeFamily.entries] means a new family cannot be added
 * without being measured; there is no "add the theme now, check contrast later" path,
 * because the build fails first.
 *
 * ## Why the accents are in here now
 *
 * They were four constants outside the palette — `BeeCodeAccents.Success` and friends —
 * on the reasoning that a passing test is green in both schemes. Hue does not flip, so
 * that much was right; lightness does, and ignoring it shipped the same defect as the
 * code block. `#6BBF59` drawn as text on a light `Card` is **1.787:1**. Every difficulty
 * badge and every "All tests passed" in light mode was that ratio, and this suite could
 * not see it because the values were not in the type it walks. They are fields on
 * [BeeCodePalette] now, and [accentsAreLegibleAsTextEverywhereTheyAreDrawn] is the
 * assertion that would have caught it.
 *
 * ## Why the card is `surfaceContainerHighest`
 *
 * Load-bearing, and the first version of this file got it wrong: it asserted the inset
 * surface against `surfaceContainerHigh`, measured a comfortable 1.199:1, and passed —
 * while the device rendered 1.045:1, because a `Card` does not fill with that role.
 * `FilledCardTokens.ContainerColor` is `SurfaceContainerHighest`, read off the shipped
 * `material3-android` bytecode with `javap`, and the card pixel probed out of a device
 * screenshot is `#353024`, which is dark `HoneyDark.surfaceContainerHighest` exactly.
 *
 * A test asserting the wrong background is worse than no test: it reports the defect as
 * fixed. So these assertions name the role rather than a nearby one, and the note above
 * exists so a future edit that "tidies" it back has to argue with the bytecode.
 *
 * ## Why the numbers are what they are
 *
 * Text thresholds are WCAG AA: 4.5:1 for body text, 3.0:1 for large text. Those are
 * borrowed, not invented, and a learner practising at 5am is exactly who they protect.
 * [ThemeFamily.HIGH_CONTRAST] is held to AAA (7:1) instead — a family that advertises
 * maximum legibility and then merely matches the default one is a false promise, so the
 * claim is asserted rather than described.
 *
 * The *surface-against-surface* threshold is not a WCAG figure — WCAG has nothing to
 * say about two adjacent fills, since neither is text. 1.10:1 is chosen from the
 * failures it prevents: measured against the real card, the collisions were 1.012:1
 * (light `surfaceVariant`) and 1.045:1 (dark `surfaceBright`, the first attempted fix),
 * while `surface` gives 1.207:1 and 1.447:1. A floor of 1.10 sits above every observed
 * failure and below every observed fix, so it fails the bug and passes the remedy
 * rather than being tuned to whatever is currently in the file.
 */
class PaletteContrastTest {

    @Test
    fun anInsetSurfaceIsDistinctFromTheCardItSitsOn() {
        // A Card fills with surfaceContainerHighest — see this class's KDoc for how that
        // was established, and why asserting the neighbouring role instead is what let a
        // 1.045:1 boundary pass. The inset surface — code blocks, examples, test output,
        // empty chart bars — must be visibly not that.
        eachScheme { name, palette, _ ->
            assertContrastAtLeast(
                MIN_SURFACE_SEPARATION,
                palette.surface,
                palette.surfaceContainerHighest,
                "$name inset surface (surface) against a Card",
            )
        }
    }

    /**
     * The rejected candidates, named so a future change reads the history.
     *
     * Not a duplicate of the assertion above: that one says the *chosen* role is far
     * enough from a card, these say the rejected ones are not — so someone reaching for
     * `surfaceVariant` because it sounds right, or for the dim/bright pair because M3
     * describes them as off-ramp surfaces, gets a failure that explains itself rather
     * than a passing build and an invisible UI.
     *
     * Three entries, not four, and the missing one is the point. Dark `surfaceVariant` is
     * 1.135:1 against a real `Card` — above this floor. The original defect was *light
     * only* for that role, at 1.012:1, which is the screenshot in this class's KDoc; the
     * "dark was worse, 1.012:1" claim written alongside it was measured against
     * `surfaceContainerHigh` and was never true of a card. Asserting it here would be a
     * test that passes only because its expectation is wrong, which is the specific
     * mistake this file already made once.
     *
     * `surfaceBright` is checked only in dark and `surfaceDim` only in light, because
     * that is the pairing the first fix used: each was chosen for the scheme where it
     * moves *away* from the page, and in the other scheme it is the page colour itself.
     *
     * Scoped to [ThemeFamily.HONEY] deliberately. These are measurements of the specific
     * values that shipped the defect, not a rule the other families have to satisfy — the
     * high-contrast family's neutrals are far enough apart that its `surfaceVariant`
     * clears the floor honestly, and asserting otherwise would be demanding a family
     * reintroduce a collision to satisfy a historical note.
     */
    @Test
    fun theRejectedInsetCandidatesAreStillTooCloseToAHoneyCard() {
        val light = BeeCodePalette.HoneyLight
        val dark = BeeCodePalette.HoneyDark
        listOf(
            Triple("light surfaceVariant", light.surfaceVariant, light),
            Triple("light surfaceDim", light.surfaceDim, light),
            Triple("dark surfaceBright", dark.surfaceBright, dark),
        ).forEach { (what, candidate, palette) ->
            val ratio = contrast(candidate, palette.surfaceContainerHighest)
            assertTrue(
                ratio < MIN_SURFACE_SEPARATION,
                "The $what is now ${"%.3f".format(ratio)}:1 against a Card, which is far " +
                    "enough apart to be an inset surface. That is a fine change to make — " +
                    "but this test records *why* code surfaces do not use it, so update " +
                    "the reasoning on BeeCodePalette.surfaceContainerHighest rather than " +
                    "only this number.",
            )
        }
    }

    @Test
    fun bodyTextOnAnInsetSurfaceMeetsItsFloor() {
        // Code is body text: mostly onSurface, with onSurfaceVariant for secondary
        // lines. Both have to clear the floor on the fill chosen above.
        eachScheme { name, palette, floor ->
            assertContrastAtLeast(
                floor,
                palette.onSurface,
                palette.surface,
                "$name onSurface on an inset surface",
            )
            assertContrastAtLeast(
                floor,
                palette.onSurfaceVariant,
                palette.surface,
                "$name onSurfaceVariant on an inset surface",
            )
        }
    }

    /**
     * Every semantic accent, as text, on both fills it is drawn on.
     *
     * The assertion the old shared constants could not have passed. Difficulty badges sit
     * on a `Card`; run-outcome lines and per-test rows sit on either a card or the page,
     * depending on the client and the screen — so both backgrounds are checked rather
     * than the more forgiving one.
     *
     * These are held to the body floor, not the large-text 3.0:1, because that is what
     * they are: a badge reading "Easy" at 12sp is small text by any reading of the
     * guideline, and `Caption` is BeeCode's smallest scale.
     */
    @Test
    fun accentsAreLegibleAsTextEverywhereTheyAreDrawn() {
        eachScheme { name, palette, floor ->
            listOf(
                "accentSuccess" to palette.accentSuccess,
                "accentCaution" to palette.accentCaution,
                "accentDanger" to palette.accentDanger,
                "accentMuted" to palette.accentMuted,
            ).forEach { (role, accent) ->
                assertContrastAtLeast(floor, accent, palette.surface, "$name $role on the page")
                assertContrastAtLeast(
                    floor,
                    accent,
                    palette.surfaceContainerHighest,
                    "$name $role on a Card",
                )
            }
        }
    }

    /**
     * The accents stay distinguishable from each other, not merely from the background.
     *
     * Legibility and *meaning* are different requirements. Four accents that each clear
     * 4.5:1 against a card can still be four colours a learner cannot tell apart, which
     * is the failure that makes a difficulty badge useless — and the reason
     * [BeeCodeAccentGlyphs] exists. This is the weaker, secondary check: shape carries
     * the meaning, but the colours should not actively collide either.
     *
     * 1.20:1 rather than the surface floor: these are adjacent in a list rather than
     * adjacent in space, so the bar is "not the same colour" rather than "reads as an
     * edge". Success against Caution in the high-contrast dark family is the tightest
     * real pairing, and it is comfortably above this.
     */
    @Test
    fun theAccentsAreDistinguishableFromEachOther() {
        eachScheme { name, palette, _ ->
            val accents = listOf(
                "success" to palette.accentSuccess,
                "caution" to palette.accentCaution,
                "danger" to palette.accentDanger,
            )
            accents.forEachIndexed { i, (aName, a) ->
                accents.drop(i + 1).forEach { (bName, b) ->
                    val ratio = contrast(a, b)
                    assertTrue(
                        ratio >= MIN_ACCENT_SEPARATION,
                        "$name $aName and $bName are ${"%.3f".format(ratio)}:1 apart, below " +
                            "${"%.2f".format(MIN_ACCENT_SEPARATION)}:1 — ${hex(a)} and ${hex(b)}. " +
                            "They mark different states, so they should not read as one colour. " +
                            "Shape carries the meaning (BeeCodeAccentGlyphs), but these should " +
                            "not collide as well.",
                    )
                }
            }
        }
    }

    /**
     * A filled progress bar has to be visible against its own track.
     *
     * Not a text contrast, so AA does not strictly apply — but 3.0:1 is the large-text
     * threshold and a progress bar is a large shape, which makes it the honest floor to
     * borrow rather than a number picked to pass.
     *
     * Both fills are asserted because both appear. Material's default track is
     * `secondaryContainer`, which is 1.021:1 against a card in light — an unearned
     * achievement showed a bar with no visible extent — so the clients pass `surface`
     * explicitly, the same role the inset surfaces use. `secondaryContainer` still draws
     * the navigation bar's active pill, so it is checked too.
     */
    @Test
    fun aProgressBarIsVisibleAgainstItsTrack() {
        eachScheme { name, palette, _ ->
            assertContrastAtLeast(
                AA_LARGE,
                palette.primary,
                palette.surface,
                "$name progress bar against the track the clients set",
            )
            assertContrastAtLeast(
                AA_LARGE,
                palette.primary,
                palette.secondaryContainer,
                "$name progress bar against Material's default track",
            )
        }
    }

    /**
     * A progress track has to be visible against the card it sits on.
     *
     * The bar-against-track assertion above passes even when the track is invisible — a
     * bar at 30% then shows a coloured stub floating on a card with no extent behind it
     * to give it a scale, which is exactly what `secondaryContainer` did.
     */
    @Test
    fun aProgressTrackIsVisibleAgainstTheCardItSitsOn() {
        eachScheme { name, palette, _ ->
            assertContrastAtLeast(
                MIN_SURFACE_SEPARATION,
                palette.surface,
                palette.surfaceContainerHighest,
                "$name progress track against a Card",
            )
        }
    }

    @Test
    fun bodyTextOnEveryCardAndBackgroundMeetsItsFloor() {
        // The ordinary case, asserted because it is the one a palette edit is most
        // likely to break by accident: a warmer background is a one-character change.
        eachScheme { name, palette, floor ->
            listOf(
                "background" to palette.background,
                "surface" to palette.surface,
                "surfaceContainerLowest" to palette.surfaceContainerLowest,
                "surfaceContainerLow" to palette.surfaceContainerLow,
                "surfaceContainer" to palette.surfaceContainer,
                "surfaceContainerHigh" to palette.surfaceContainerHigh,
                "surfaceContainerHighest" to palette.surfaceContainerHighest,
            ).forEach { (role, fill) ->
                assertContrastAtLeast(floor, palette.onSurface, fill, "$name onSurface on $role")
                assertContrastAtLeast(
                    floor,
                    palette.onSurfaceVariant,
                    fill,
                    "$name onSurfaceVariant on $role",
                )
            }
        }
    }

    @Test
    fun everyOnRolePairedWithItsContainerMeetsItsFloor() {
        // Walked as pairs rather than spot-checked: a container whose text is
        // unreadable is the same defect as the code surface, and the palette has
        // eleven of these pairings to get wrong.
        eachScheme { name, palette, floor ->
            listOf(
                Triple("primary", palette.onPrimary, palette.primary),
                Triple("primaryContainer", palette.onPrimaryContainer, palette.primaryContainer),
                Triple("secondary", palette.onSecondary, palette.secondary),
                Triple(
                    "secondaryContainer",
                    palette.onSecondaryContainer,
                    palette.secondaryContainer,
                ),
                Triple("tertiary", palette.onTertiary, palette.tertiary),
                Triple("tertiaryContainer", palette.onTertiaryContainer, palette.tertiaryContainer),
                Triple("error", palette.onError, palette.error),
                Triple("errorContainer", palette.onErrorContainer, palette.errorContainer),
                Triple("inverseSurface", palette.inverseOnSurface, palette.inverseSurface),
                Triple("primaryFixed", palette.onPrimaryFixed, palette.primaryFixed),
                Triple("secondaryFixed", palette.onSecondaryFixed, palette.secondaryFixed),
                Triple("tertiaryFixed", palette.onTertiaryFixed, palette.tertiaryFixed),
            ).forEach { (role, on, container) ->
                assertContrastAtLeast(floor, on, container, "$name on$role on $role")
            }
        }
    }

    /**
     * A divider has to be a divider.
     *
     * `outlineVariant` draws every `HorizontalDivider` in both clients, and the
     * baseline value it used to fall back to was cool grey on a warm app. 1.10:1 is
     * the same surface-separation floor used above, for the same reason: a rule you
     * cannot see is not a rule, and this is the role whose entire job is being seen.
     */
    @Test
    fun aDividerIsVisibleOnEverySurfaceItIsDrawnOn() {
        eachScheme { name, palette, _ ->
            listOf(
                "surface" to palette.surface,
                // The role a Card actually fills with, which is where most dividers in
                // both clients are drawn.
                "surfaceContainerHighest" to palette.surfaceContainerHighest,
            ).forEach { (role, fill) ->
                assertContrastAtLeast(
                    MIN_SURFACE_SEPARATION,
                    palette.outlineVariant,
                    fill,
                    "$name outlineVariant on $role",
                )
            }
        }
    }

    /**
     * The high-contrast family is actually higher contrast than the default one.
     *
     * Every other test here checks a palette against an absolute floor, which a family
     * could satisfy while being no better than `HONEY` — and a "high contrast" theme that
     * is merely adequate is a promise Settings makes and the colours do not keep. This
     * compares the two directly, so the claim in [ThemeFamily.HIGH_CONTRAST]'s
     * description has to remain true rather than merely have been true when written.
     */
    @Test
    fun theHighContrastFamilyBeatsTheDefaultOne() {
        listOf(true, false).forEach { dark ->
            val scheme = if (dark) "dark" else "light"
            val hc = ThemeFamily.HIGH_CONTRAST.palette(dark)
            val honey = ThemeFamily.HONEY.palette(dark)
            val hcRatio = contrast(hc.onSurface, hc.surface)
            val honeyRatio = contrast(honey.onSurface, honey.surface)
            assertTrue(
                hcRatio > honeyRatio,
                "The high-contrast $scheme scheme puts body text at " +
                    "${"%.3f".format(hcRatio)}:1, which is not better than Honey's " +
                    "${"%.3f".format(honeyRatio)}:1. Settings calls this family " +
                    "\"${ThemeFamily.HIGH_CONTRAST.description}\", so it has to be.",
            )
        }
    }

    /** Every family supplies two distinct schemes — a family with one is a broken picker. */
    @Test
    fun everyFamilyHasTwoDistinctSchemes() {
        ThemeFamily.entries.forEach { family ->
            assertTrue(
                family.dark != family.light,
                "${family.label} uses the same palette for dark and light, so choosing " +
                    "either mode does nothing.",
            )
            assertTrue(
                contrast(family.dark.surface, family.light.surface) > MIN_SURFACE_SEPARATION,
                "${family.label}'s dark and light pages are nearly the same colour, which " +
                    "means one of the two is mislabelled.",
            )
        }
    }

    private companion object {
        /** WCAG AA for body text. */
        const val AA_BODY = 4.5

        /** WCAG AAA for body text. What [ThemeFamily.HIGH_CONTRAST] promises. */
        const val AAA_BODY = 7.0

        /** WCAG AA for large text and large shapes. */
        const val AA_LARGE = 3.0

        /**
         * The floor for two adjacent fills.
         *
         * Chosen from the evidence rather than from a standard: see this class's KDoc.
         * Measured against a real `Card`, the collisions were 1.012 and 1.045; the role
         * that fixes them is 1.207 and 1.447.
         */
        const val MIN_SURFACE_SEPARATION = 1.10

        /** The floor for two accents that mean different things. See the test's KDoc. */
        const val MIN_ACCENT_SEPARATION = 1.20

        /**
         * Run a block over every palette BeeCode can render, with the floor it must meet.
         *
         * The floor is per-family rather than a constant because [ThemeFamily.HIGH_CONTRAST]
         * advertises AAA. Passing it in — rather than letting each test look the family up —
         * is what keeps a new family from being added with a quietly lower bar.
         */
        fun eachScheme(block: (name: String, palette: BeeCodePalette, floor: Double) -> Unit) {
            ThemeFamily.entries.forEach { family ->
                val floor = if (family == ThemeFamily.HIGH_CONTRAST) AAA_BODY else AA_BODY
                listOf(true to "dark", false to "light").forEach { (dark, scheme) ->
                    block("${family.label} $scheme", family.palette(dark), floor)
                }
            }
        }

        fun assertContrastAtLeast(minimum: Double, foreground: Long, background: Long, what: String) {
            val ratio = contrast(foreground, background)
            if (ratio < minimum) {
                fail(
                    "$what is ${"%.3f".format(ratio)}:1, below the required " +
                        "${"%.2f".format(minimum)}:1 — ${hex(foreground)} on ${hex(background)}.",
                )
            }
        }

        fun hex(argb: Long): String = "#%06X".format(argb and 0xFFFFFF)

        /**
         * WCAG 2.x relative-luminance contrast.
         *
         * Implemented here rather than pulled in: it is six lines, and the alternative
         * is a dependency in `:shared` — which is deliberately free of them — for a
         * formula that has not changed since 2008.
         *
         * Alpha is ignored because every value in the palette is opaque. A translucent
         * fill has no single contrast ratio without knowing what is behind it, so
         * accepting one here would return a confidently wrong number.
         */
        fun contrast(a: Long, b: Long): Double {
            val la = relativeLuminance(a)
            val lb = relativeLuminance(b)
            val lighter = maxOf(la, lb)
            val darker = minOf(la, lb)
            return (lighter + 0.05) / (darker + 0.05)
        }

        fun relativeLuminance(argb: Long): Double {
            val r = channel(((argb shr 16) and 0xFF).toInt())
            val g = channel(((argb shr 8) and 0xFF).toInt())
            val b = channel((argb and 0xFF).toInt())
            return 0.2126 * r + 0.7152 * g + 0.0722 * b
        }

        fun channel(value: Int): Double {
            val c = value / 255.0
            return if (c <= 0.03928) c / 12.92 else Math.pow((c + 0.055) / 1.055, 2.4)
        }
    }
}
