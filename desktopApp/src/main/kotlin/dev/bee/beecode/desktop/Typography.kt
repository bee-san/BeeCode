package dev.bee.beecode.desktop

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.bee.beecode.design.BeeCodeTypeScale

/**
 * BeeCode's type scale, mapped onto Material's roles.
 *
 * The sizes live in [BeeCodeTypeScale] so Android gets the same ones. This maps them
 * onto the Material role names the UI already uses, which means the screens needed no
 * per-call size overrides — the hierarchy arrives through the theme.
 *
 * Roles BeeCode does not use are left at Material's defaults rather than being
 * assigned an arbitrary size. Unlike colour, an unset type role is not a
 * brand leak: it is a size nobody renders.
 */
internal fun beeCodeTypography(): Typography {
    val default = Typography()
    return default.copy(
        headlineLarge = BeeCodeTypeScale.Headline.toTextStyle(default.headlineLarge),
        headlineMedium = BeeCodeTypeScale.Headline.toTextStyle(default.headlineMedium),
        headlineSmall = BeeCodeTypeScale.Title.toTextStyle(default.headlineSmall),
        titleLarge = BeeCodeTypeScale.Title.toTextStyle(default.titleLarge),
        titleMedium = BeeCodeTypeScale.Subtitle.toTextStyle(default.titleMedium),
        // titleSmall is what every card heading in the app uses, and it used to be
        // 14sp against 12sp body — a 2sp gap, which reads as an accident rather than a
        // hierarchy. SectionLabel is smaller but bolder and letter-spaced, so it reads
        // as a label instead of competing with the prose underneath it.
        titleSmall = BeeCodeTypeScale.SectionLabel.toTextStyle(default.titleSmall),
        bodyLarge = BeeCodeTypeScale.Body.toTextStyle(default.bodyLarge),
        bodyMedium = BeeCodeTypeScale.Body.toTextStyle(default.bodyMedium),
        bodySmall = BeeCodeTypeScale.BodySmall.toTextStyle(default.bodySmall),
        labelLarge = BeeCodeTypeScale.Action.toTextStyle(default.labelLarge),
        labelMedium = BeeCodeTypeScale.Caption.toTextStyle(default.labelMedium),
        labelSmall = BeeCodeTypeScale.Caption.toTextStyle(default.labelSmall),
    )
}

/**
 * Apply a scale to a Material style, keeping everything the scale does not speak to.
 *
 * Built from the role's own default rather than a bare [TextStyle] so the font family
 * and platform text metrics Material set stay in place — replacing the style outright
 * silently drops them.
 */
private fun BeeCodeTypeScale.toTextStyle(base: TextStyle): TextStyle = base.copy(
    fontSize = sizeSp.sp,
    lineHeight = lineHeightSp.sp,
    fontWeight = FontWeight(weight),
    letterSpacing = letterSpacingSp.sp,
)

/** The monospace style for code, output, and test failures. */
@Composable
internal fun monoStyle(): TextStyle = MaterialTheme.typography.bodySmall.copy(
    fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
    // Code needs more leading than prose at the same size: a line of Python carries
    // more distinct glyph shapes per line than a sentence does.
    lineHeight = 20.sp,
)
