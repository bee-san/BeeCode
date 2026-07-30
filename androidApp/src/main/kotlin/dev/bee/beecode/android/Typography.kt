package dev.bee.beecode.android

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import dev.bee.beecode.design.BeeCodeTypeScale

/**
 * BeeCode's type scale, mapped onto Material's roles.
 *
 * The sizes live in [BeeCodeTypeScale] so both clients get the same ones, and the mapping
 * here is deliberately identical to desktop's. Before this, Android used Material's
 * baseline scale untouched, which meant the two clients disagreed about how large a
 * Problem title is — the desktop was retuned for density and the phone was not.
 *
 * Roles BeeCode does not use are left at Material's defaults rather than being given an
 * arbitrary size. Unlike colour, an unset type role is not a brand leak: it is a size
 * nobody renders.
 */
internal fun beeCodeTypography(): Typography {
    val default = Typography()
    return default.copy(
        headlineLarge = BeeCodeTypeScale.Headline.toTextStyle(default.headlineLarge),
        headlineMedium = BeeCodeTypeScale.Headline.toTextStyle(default.headlineMedium),
        headlineSmall = BeeCodeTypeScale.Title.toTextStyle(default.headlineSmall),
        titleLarge = BeeCodeTypeScale.Title.toTextStyle(default.titleLarge),
        titleMedium = BeeCodeTypeScale.Subtitle.toTextStyle(default.titleMedium),
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
 * Built from the role's own default rather than a bare [TextStyle] so the font family and
 * platform text metrics Material set stay in place — replacing the style outright
 * silently drops them, and on Android that includes `PlatformTextStyle`'s line-height
 * behaviour, which changes vertical rhythm across the whole app.
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
    fontFamily = FontFamily.Monospace,
    // Code needs more leading than prose at the same size: a line of Python carries more
    // distinct glyph shapes per line than a sentence does.
    lineHeight = 20.sp,
)
