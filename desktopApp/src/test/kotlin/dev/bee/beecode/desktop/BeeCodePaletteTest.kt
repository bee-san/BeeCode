package dev.bee.beecode.desktop

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import dev.bee.beecode.design.BeeCodePalette
import dev.bee.beecode.design.ThemeFamily
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Every Material colour role is set from the shared palette.
 *
 * ## Why reflectively rather than by listing the roles
 *
 * The defect this covers is a role that is *absent* from `toColorScheme`, and no
 * hand-written list of assertions can catch that: the list would have the same gap as
 * the mapping, written by the same person in the same sitting. Both clients set 16 of
 * Material's 48 roles and the other 32 silently fell back to the M3 *baseline*, which is
 * purple — `surfaceContainerHighest` resolved to `#E6E0E9`, which is what every `Card`
 * fills with, and `outlineVariant` to `#CAC4D0`, which is what every divider draws. An
 * amber app whose actual surfaces were all lavender, and nothing failed.
 *
 * Asking `ColorScheme` itself what roles exist means a role added by a future Material
 * upgrade arrives as a *failure* rather than as a fresh patch of someone else's brand.
 * That is the whole reason this is a build-breaking test and not a code review note.
 *
 * ## Why plain Java reflection
 *
 * No kotlin-reflect on the test classpath, and none needed. `Color` is a value class, so
 * its accessors are name-mangled to `getPrimary-0d7_KjU` and return the raw `long` bits —
 * which is inconvenient to read but perfectly stable to match on, and the mangling is
 * itself the signal that distinguishes the 48 `Color` roles from `ColorScheme`'s other
 * members.
 */
class BeeCodePaletteTest {

    @Test
    fun everyRoleInEverySchemeComesFromItsOwnPalette() {
        // Every family and both schemes, not just the default pair. A family added with a
        // role copied from Honey by accident is exactly the kind of thing a two-scheme
        // version of this test would wave through, and the original drift was this shape:
        // desktop set `errorContainer` and Android did not, and light `surface` was
        // #FFFCF7 on one client and #FFF8F0 on the other.
        eachScheme { name, palette -> assertSchemeMatchesPalette(palette, name) }
    }

    @Test
    fun theSchemeAndThePaletteNameTheSameRoles() {
        // The guard against a Material upgrade. A new role appears here as a missing
        // palette field and fails, rather than quietly rendering in baseline purple; a
        // removed role appears as a palette field with nothing to map onto, which is dead
        // weight rather than a defect but is still worth being told about.
        val schemeRoles = colorRoleNames().toSet()
        val paletteRoles = BeeCodePalette.HoneyDark.javaClass.methods
            .mapNotNull { method ->
                val name = method.name
                if (method.parameterCount == 0 &&
                    method.returnType == java.lang.Long.TYPE &&
                    name.startsWith("get")
                ) {
                    name.removePrefix("get").replaceFirstChar { it.lowercase() }
                } else {
                    null
                }
            }
            .toSet()
            // The four semantic accents are palette fields with no Material role — pass,
            // caution, failure, absent. They live in the palette so the contrast suite
            // walks them (it did not, when they were global constants, and a 1.787:1
            // badge shipped), but they are deliberately not roles and must not be read
            // as ones Material has dropped.
            .filterNot { it.startsWith("accent") }
            .toSet()

        assertEquals(
            emptySet(),
            schemeRoles - paletteRoles,
            "Material has roles the palette does not name — these would fall back to the " +
                "M3 baseline, which is purple",
        )
        assertEquals(
            emptySet(),
            paletteRoles - schemeRoles,
            "the palette names roles Material does not have",
        )
        // Stated as a number too, so the count in BeeCodePalette's own KDoc is checked
        // rather than trusted. If this is ever not 48, that KDoc is out of date.
        assertEquals(48, schemeRoles.size, "Material 3 is expected to have 48 colour roles")
    }

    @Test
    fun noRoleIsLeftAtTheMaterialBaseline() {
        // Asked of Material rather than hand-copied from it. An earlier version of this test
        // asserted three literals — #E6E0E9, #CAC4D0, #E8DEF8 — and those are the *light*
        // baseline: mutating `toColorScheme` to `darkColorScheme(...)` with the container
        // ramp omitted left every one of them untriggered, because the dark baseline's
        // `surfaceContainerHighest` is #36343B. A test that names the wrong purple only
        // catches half the defect it claims to.
        //
        // Comparing against both baselines role by role also means a Material upgrade that
        // changes a baseline value cannot quietly make this test vacuous.
        eachScheme { name, palette ->
            val scheme = palette.toColorScheme()
            for (role in colorRoleNames()) {
                val ours = roleValue(scheme, role)
                // Pure black and pure white are excluded by *value*, not by role name.
                // Enumerating the actual coincidences found exactly nine, and every one is
                // #000000 or #FFFFFF: `scrim` in both schemes, because a dimming overlay has
                // one correct value; light `onPrimary`/`onSecondary`/`onTertiary`/`onError`,
                // because white is the only thing legible on a saturated fill; and light
                // `surfaceContainerLowest`, because the lowest container in a light scheme is
                // paper. A name-based exclusion list would keep excusing those roles after
                // they stopped being white — this rule stops excusing them the moment they do.
                if (ours in BLACK_AND_WHITE) continue
                assertTrue(
                    ours != roleValue(lightColorScheme(), role),
                    "$name scheme's $role is the M3 light baseline ($ours) — a role left " +
                        "unset renders in someone else's brand",
                )
                assertTrue(
                    ours != roleValue(darkColorScheme(), role),
                    "$name scheme's $role is the M3 dark baseline ($ours) — a role left " +
                        "unset renders in someone else's brand",
                )
            }
        }
    }

    @Test
    fun everyFamilysTwoSchemesAgreeOnTheFixedRolesAndDisagreeOnTheRest() {
        // Per family, because "the light scheme is not actually light" is a mistake each
        // new family can make independently.
        for (family in ThemeFamily.entries) {
            assertFixedRolesSurviveTheFlip(family)
        }
    }

    /**
     * The "fixed" roles exist precisely to survive a theme flip, so they must match across
     * a family's two palettes; everything else must not, or the light scheme is not
     * actually light. Asserted together because a copy-paste that duplicated the dark
     * palette wholesale would pass every other test in this file.
     */
    private fun assertFixedRolesSurviveTheFlip(family: ThemeFamily) {
        val dark = family.dark.toColorScheme()
        val light = family.light.toColorScheme()

        val differing = colorRoleNames().filter { role ->
            roleValue(dark, role) != roleValue(light, role)
        }
        val fixed = colorRoleNames().filter { it.contains("Fixed") }

        for (role in fixed) {
            assertEquals(
                roleValue(dark, role),
                roleValue(light, role),
                "${family.label}: $role is a fixed role and must not change with the theme",
            )
        }
        // Scrim is black in both by design — it is a dimming overlay, not a surface — so it
        // joins the fixed roles as a legitimate coincidence rather than a suspicious one.
        //
        // So does any role that is pure black or pure white in both schemes, and excusing
        // it by *value* rather than by name is what makes the rule hold for a family this
        // test did not anticipate. High contrast is the case: its two `primaryContainer`
        // values are both light amber, and at a 7:1 floor black is the only foreground
        // that clears it, so `onPrimaryContainer` is #000000 on both sides. That is the
        // scheme being honest about a constraint, not a palette copied from another. A
        // name-based exception would keep excusing the role after it stopped being black.
        val blackOrWhiteInBoth = colorRoleNames().filter { role ->
            roleValue(dark, role) in BLACK_AND_WHITE && roleValue(light, role) in BLACK_AND_WHITE
        }
        val expectedSame = (fixed + "scrim" + blackOrWhiteInBoth).toSet()
        assertEquals(
            emptySet(),
            colorRoleNames().toSet() - expectedSame - differing.toSet(),
            "${family.label}: these roles are identical in both schemes without being " +
                "fixed roles, which usually means one palette was copied from the other",
        )
    }

    /** Run [block] against every family's dark and light palette, with a label to fail with. */
    private fun eachScheme(block: (name: String, palette: BeeCodePalette) -> Unit) {
        for (family in ThemeFamily.entries) {
            block("${family.label} dark", family.dark)
            block("${family.label} light", family.light)
        }
    }

    /** Compare every one of [ColorScheme]'s roles against the palette field of the same name. */
    private fun assertSchemeMatchesPalette(palette: BeeCodePalette, name: String) {
        val scheme = palette.toColorScheme()
        for (role in colorRoleNames()) {
            assertEquals(
                Color(paletteValue(palette, role)),
                roleValue(scheme, role),
                "$name scheme's $role does not come from the palette",
            )
        }
    }

    private companion object {
        /**
         * The two values a palette may legitimately share with Material's baseline.
         *
         * Neither carries brand: they are what "opaque" and "nothing" look like.
         */
        val BLACK_AND_WHITE = setOf(Color(0xFF000000), Color(0xFFFFFFFF))

        /**
         * The names of [ColorScheme]'s `Color` properties.
         *
         * Found by the value-class mangling suffix, which is what separates the colour
         * roles from `ColorScheme`'s other members without needing them listed.
         */
        fun colorRoleNames(): List<String> = ColorScheme::class.java.methods
            .mapNotNull { method ->
                MANGLED_COLOR_GETTER.matchEntire(method.name)?.groupValues?.get(1)
            }
            .map { it.replaceFirstChar { first -> first.lowercase() } }
            .distinct()
            .sorted()

        val MANGLED_COLOR_GETTER = Regex("""get(\w+)-0d7_KjU""")

        /**
         * One role's colour, read through the mangled accessor.
         *
         * The raw `long` is `Color`'s packed representation, not an ARGB literal, so it is
         * rewrapped rather than compared numerically against a palette value.
         */
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
