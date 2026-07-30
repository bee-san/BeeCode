package dev.bee.beecode.design

import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * Contrast assertions on [BeeCodePalette].
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
 * ## Why the card is `surfaceContainerHighest`
 *
 * Load-bearing, and the first version of this file got it wrong: it asserted the inset
 * surface against `surfaceContainerHigh`, measured a comfortable 1.199:1, and passed —
 * while the device rendered 1.045:1, because a `Card` does not fill with that role.
 * `FilledCardTokens.ContainerColor` is `SurfaceContainerHighest`, read off the shipped
 * `material3-android` bytecode with `javap`, and the card pixel probed out of a device
 * screenshot is `#353024`, which is dark `surfaceContainerHighest` exactly.
 *
 * A test asserting the wrong background is worse than no test: it reports the defect as
 * fixed. So these assertions name the role rather than a nearby one, and the note above
 * exists so a future edit that "tidies" it back has to argue with the bytecode.
 *
 * ## Why the numbers are what they are
 *
 * Text thresholds are WCAG AA: 4.5:1 for body text, 3.0:1 for large text. Those are
 * borrowed, not invented, and a learner practising at 5am is exactly who they protect.
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
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
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
     */
    @Test
    fun theRejectedInsetCandidatesAreStillTooCloseToACard() {
        listOf(
            Triple("light surfaceVariant", BeeCodePalette.Light.surfaceVariant, BeeCodePalette.Light),
            Triple("light surfaceDim", BeeCodePalette.Light.surfaceDim, BeeCodePalette.Light),
            Triple("dark surfaceBright", BeeCodePalette.Dark.surfaceBright, BeeCodePalette.Dark),
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
    fun bodyTextOnAnInsetSurfaceMeetsAa() {
        // Code is body text: mostly onSurface, with onSurfaceVariant for secondary
        // lines. Both have to clear AA on the fill chosen above.
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
            assertContrastAtLeast(
                AA_BODY,
                palette.onSurface,
                palette.surface,
                "$name onSurface on an inset surface",
            )
            assertContrastAtLeast(
                AA_BODY,
                palette.onSurfaceVariant,
                palette.surface,
                "$name onSurfaceVariant on an inset surface",
            )
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
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
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
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
            assertContrastAtLeast(
                MIN_SURFACE_SEPARATION,
                palette.surface,
                palette.surfaceContainerHighest,
                "$name progress track against a Card",
            )
        }
    }

    @Test
    fun bodyTextOnEveryCardAndBackgroundMeetsAa() {
        // The ordinary case, asserted because it is the one a palette edit is most
        // likely to break by accident: a warmer background is a one-character change.
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
            listOf(
                "background" to palette.background,
                "surface" to palette.surface,
                "surfaceContainerLowest" to palette.surfaceContainerLowest,
                "surfaceContainerLow" to palette.surfaceContainerLow,
                "surfaceContainer" to palette.surfaceContainer,
                "surfaceContainerHigh" to palette.surfaceContainerHigh,
                "surfaceContainerHighest" to palette.surfaceContainerHighest,
            ).forEach { (role, fill) ->
                assertContrastAtLeast(
                    AA_BODY,
                    palette.onSurface,
                    fill,
                    "$name onSurface on $role",
                )
                assertContrastAtLeast(
                    AA_BODY,
                    palette.onSurfaceVariant,
                    fill,
                    "$name onSurfaceVariant on $role",
                )
            }
        }
    }

    @Test
    fun everyOnRolePairedWithItsContainerMeetsAa() {
        // Walked as pairs rather than spot-checked: a container whose text is
        // unreadable is the same defect as the code surface, and the palette has
        // eleven of these pairings to get wrong.
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
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
                assertContrastAtLeast(AA_BODY, on, container, "$name on$role on $role")
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
        listOf(
            "light" to BeeCodePalette.Light,
            "dark" to BeeCodePalette.Dark,
        ).forEach { (name, palette) ->
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

    private companion object {
        /** WCAG AA for body text. */
        const val AA_BODY = 4.5

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
