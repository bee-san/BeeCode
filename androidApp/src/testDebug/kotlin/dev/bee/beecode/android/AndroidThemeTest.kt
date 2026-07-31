package dev.bee.beecode.android

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.bee.beecode.design.BeeCodePalette
import dev.bee.beecode.design.BeeCodeTypeScale
import dev.bee.beecode.design.ThemeFamily
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every Material colour role on Android is set from the shared palette.
 *
 * The counterpart to desktop's `BeeCodePaletteTest`, and deliberately the same
 * assertions. That symmetry is the point: `:shared` is a plain JVM module and cannot hold
 * a Compose `ColorScheme` an Android client could consume, so the mapping is necessarily
 * written twice — and two hand-written mappings of the same palette are exactly what
 * drifted before. `surface` was `#1C1A15` here and `#12100C` on desktop, and light
 * `background` was `#FFF8F0` against `#FFF8EC`; nothing could catch it, because a scheme
 * built in one module's composable is not comparable to one built in another's.
 *
 * Now both are compared against the palette instead of against each other, which is the
 * same guarantee without the coupling.
 *
 * Plain JUnit rather than Robolectric: this composes nothing and needs no Android
 * runtime, so it runs in milliseconds and cannot be flaky.
 */
class AndroidThemeTest {

    @Test
    fun everyRoleInEverySchemeComesFromItsOwnPalette() {
        // Every family and both of its schemes. A family added with a role left copied from
        // Honey is exactly what a two-scheme version of this test would wave through.
        for (family in ThemeFamily.entries) {
            assertSchemeMatchesPalette(family.dark, "${family.label} dark")
            assertSchemeMatchesPalette(family.light, "${family.label} light")
        }
    }

    @Test
    fun theSchemeAndThePaletteNameTheSameRoles() {
        // The guard against a Material upgrade. A role Material adds shows up here as a
        // missing palette field and fails the build, rather than quietly rendering in
        // baseline purple on phones only.
        val schemeRoles = colorRoleNames().toSet()
        val paletteRoles = BeeCodePalette.HoneyDark.javaClass.methods
            .mapNotNull { method ->
                if (method.parameterCount == 0 &&
                    method.returnType == java.lang.Long.TYPE &&
                    method.name.startsWith("get")
                ) {
                    method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                } else {
                    null
                }
            }
            // The four semantic accents are palette fields with no Material role. They sit
            // in the palette so the contrast suite walks them — it did not when they were
            // global constants, and a 1.787:1 difficulty badge shipped — but they are not
            // roles Material has dropped.
            .filterNot { it.startsWith("accent") }
            .toSet()

        assertEquals(
            "Material has roles the palette does not name — these fall back to the M3 " +
                "baseline, which is purple",
            emptySet<String>(),
            schemeRoles - paletteRoles,
        )
        assertEquals(48, schemeRoles.size)
    }

    @Test
    fun noRoleIsLeftAtTheMaterialBaseline() {
        // The defect this replaced, asserted directly. Android used the
        // `darkColorScheme(...)`/`lightColorScheme(...)` factories and set 14 of the 48
        // roles; the other 34 took the factories' baseline defaults. So every `Card` filled
        // with #E6E0E9 lavender, every divider drew #CAC4D0, and the navigation bar's
        // active pill was #E8DEF8 — on an app whose whole identity is honey amber.
        //
        // Both baselines are checked, not just the one matching the scheme, because the
        // wrong-baseline mistake is easy to make and produces a test that reads as
        // thorough while catching half the cases.
        val schemes = ThemeFamily.entries.flatMap {
            listOf("${it.label} dark" to it.dark, "${it.label} light" to it.light)
        }
        for ((name, palette) in schemes) {
            val scheme = palette.toColorScheme()
            for (role in colorRoleNames()) {
                val ours = roleValue(scheme, role)
                // Pure black and pure white are excused by value rather than by role name:
                // they carry no brand, and enumerating the real coincidences found every one
                // of them to be one or the other (scrim, the light `on*` family on saturated
                // fills, and light `surfaceContainerLowest`). A name list would keep excusing
                // a role after it stopped being white.
                if (ours in BLACK_AND_WHITE) continue
                assertTrue(
                    "$name scheme's $role is the M3 light baseline — a role left unset " +
                        "renders in someone else's brand",
                    ours != roleValue(lightColorScheme(), role),
                )
                assertTrue(
                    "$name scheme's $role is the M3 dark baseline — a role left unset " +
                        "renders in someone else's brand",
                    ours != roleValue(darkColorScheme(), role),
                )
            }
        }
    }

    @Test
    fun theTypeScaleIsAppliedRatherThanMaterialsDefault() {
        // Android used Material's baseline type scale untouched while desktop had been
        // retuned for density, so the two clients disagreed about how large a Problem title
        // is. Asserted against the shared scale, which is the thing that makes them agree.
        val typography = beeCodeTypography()
        assertEquals(BeeCodeTypeScale.Title.sizeSp, typography.titleLarge.fontSize.value, 0f)
        assertEquals(BeeCodeTypeScale.Subtitle.sizeSp, typography.titleMedium.fontSize.value, 0f)
        assertEquals(BeeCodeTypeScale.SectionLabel.sizeSp, typography.titleSmall.fontSize.value, 0f)
        assertEquals(BeeCodeTypeScale.Body.sizeSp, typography.bodyLarge.fontSize.value, 0f)
        assertEquals(BeeCodeTypeScale.BodySmall.sizeSp, typography.bodySmall.fontSize.value, 0f)
        assertEquals(BeeCodeTypeScale.Action.sizeSp, typography.labelLarge.fontSize.value, 0f)
        // Line height too, and not as an afterthought: a size applied without its leading
        // gives cramped or airy text at the right size, which reads as a spacing bug.
        assertEquals(BeeCodeTypeScale.Body.lineHeightSp, typography.bodyLarge.lineHeight.value, 0f)
        // And the weight, which is the one the section labels depend on — `SectionLabel` is
        // 13sp bold, and a card heading that lost its weight is indistinguishable from the
        // body text underneath it.
        assertEquals(BeeCodeTypeScale.SectionLabel.weight, typography.titleSmall.fontWeight?.weight)

        // Finally, what Material set that the scale does not speak to must survive. Building
        // each style from a bare `TextStyle` instead of copying the role's default drops the
        // font family and `PlatformTextStyle`'s line-height behaviour silently.
        assertEquals(Typography().bodySmall.fontFamily, typography.bodySmall.fontFamily)
        assertEquals(Typography().bodySmall.platformStyle, typography.bodySmall.platformStyle)
    }

    private fun assertSchemeMatchesPalette(palette: BeeCodePalette, name: String) {
        val scheme = palette.toColorScheme()
        for (role in colorRoleNames()) {
            assertEquals(
                "$name scheme's $role does not come from the palette",
                Color(paletteValue(palette, role)),
                roleValue(scheme, role),
            )
        }
    }

    private companion object {
        /** The two values a palette may legitimately share with Material's baseline. */
        val BLACK_AND_WHITE = setOf(Color(0xFF000000), Color(0xFFFFFFFF))

        /**
         * The names of [ColorScheme]'s `Color` properties.
         *
         * `Color` is a value class, so its accessors are name-mangled to
         * `getPrimary-0d7_KjU` and return the raw packed `long`. Inconvenient to read, but
         * the mangling is itself what separates the 48 colour roles from `ColorScheme`'s
         * other members — no list of role names needed, which is the whole point.
         */
        fun colorRoleNames(): List<String> = ColorScheme::class.java.methods
            .mapNotNull { Regex("""get(\w+)-0d7_KjU""").matchEntire(it.name)?.groupValues?.get(1) }
            .map { it.replaceFirstChar { first -> first.lowercase() } }
            .distinct()
            .sorted()

        fun roleValue(scheme: ColorScheme, role: String): Color {
            val getter = ColorScheme::class.java
                .getMethod("get${role.replaceFirstChar { it.uppercase() }}-0d7_KjU")
            return Color((getter.invoke(scheme) as Long).toULong())
        }

        fun paletteValue(palette: BeeCodePalette, role: String): Long {
            val getter = BeeCodePalette::class.java
                .getMethod("get${role.replaceFirstChar { it.uppercase() }}")
            return getter.invoke(palette) as Long
        }
    }
}
