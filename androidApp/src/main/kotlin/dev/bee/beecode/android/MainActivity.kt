package dev.bee.beecode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.StudyViewModel
import dev.bee.beecode.design.BeeCodePalette
import dev.bee.beecode.design.ThemeChoice
import dev.bee.beecode.design.resolvesToDark

/**
 * The single Activity.
 *
 * `configChanges` in the manifest keeps rotation from recreating it, so an
 * in-flight Python run and the learner's unsaved keystrokes survive turning the
 * phone. The draft is still written eagerly regardless — a configuration change is
 * the mild case, and a low-memory process kill is the one that matters.
 */
class MainActivity : ComponentActivity() {

    private val viewModel: StudyViewModel by lazy {
        val application = application as BeeCodeApplication
        ViewModelProvider(
            this,
            viewModelFactory {
                initializer { StudyViewModel(application.profile) }
            },
        )[StudyViewModel::class.java]
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            // Read as state rather than once: the theme control lives *inside* this tree, so
            // a plain read would store the learner's choice and go on rendering the old
            // palette until the next launch — a setting that looks broken while working.
            val choice by viewModel.themeChoice.collectAsStateWithLifecycle()
            BeeCodeTheme(choice) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BeeCodeApp(viewModel)
                }
            }
        }
    }
}

/**
 * BeeCode's theme, from the palette both clients share.
 *
 * ## What this replaced
 *
 * Two hand-written schemes declared here, setting 14 of Material's 48 colour roles
 * through the `darkColorScheme`/`lightColorScheme` factories — whose remaining
 * parameters default to the M3 *baseline*, which is purple. So `surfaceContainerHighest`
 * (what every `Card` fills with), `outlineVariant` (every divider), and
 * `secondaryContainer` (the navigation bar's active pill) were all lavender on an amber
 * app, and nothing could catch it.
 *
 * They had also drifted from desktop's copy of the same intent: `surface` was `#1C1A15`
 * here and `#12100C` there, and light `background` was `#FFF8F0` against `#FFF8EC`. Two
 * clients cannot be checked against each other while each declares its own colours in a
 * composable — `BeeCodePaletteTest` on desktop and `AndroidThemeTest` here now assert
 * both mappings against the one palette, so a divergence fails a build.
 *
 * @param systemIsDark what the platform reports. Android always knows, unlike desktop
 *   Linux where skiko returns UNKNOWN — so this takes the OS signal directly rather than
 *   going through [ThemeChoice.resolvesToDark]'s null case.
 */
@Composable
fun BeeCodeTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    systemIsDark: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val palette = if (choice.resolvesToDark(systemIsDark)) {
        BeeCodePalette.Dark
    } else {
        BeeCodePalette.Light
    }
    MaterialTheme(
        colorScheme = palette.toColorScheme(),
        typography = beeCodeTypography(),
        content = content,
    )
}

/**
 * Map the shared palette onto Material's scheme.
 *
 * All 48 roles, via the `ColorScheme` constructor rather than the `darkColorScheme`
 * factory. That choice is load-bearing: the factory's parameters all have baseline
 * defaults, so omitting a role compiles and renders purple. The constructor has no
 * defaults, so omitting one is a compile error — the class of bug this replaced cannot
 * be reintroduced silently.
 *
 * Deliberately identical in structure to desktop's `toColorScheme`. The duplication is
 * unavoidable — `:shared` is a plain JVM module and cannot hold a Compose type an
 * Android client could consume — and [AndroidThemeTest] is what keeps the two honest.
 */
internal fun BeeCodePalette.toColorScheme(): ColorScheme = ColorScheme(
    primary = Color(primary),
    onPrimary = Color(onPrimary),
    primaryContainer = Color(primaryContainer),
    onPrimaryContainer = Color(onPrimaryContainer),
    inversePrimary = Color(inversePrimary),
    secondary = Color(secondary),
    onSecondary = Color(onSecondary),
    secondaryContainer = Color(secondaryContainer),
    onSecondaryContainer = Color(onSecondaryContainer),
    tertiary = Color(tertiary),
    onTertiary = Color(onTertiary),
    tertiaryContainer = Color(tertiaryContainer),
    onTertiaryContainer = Color(onTertiaryContainer),
    background = Color(background),
    onBackground = Color(onBackground),
    surface = Color(surface),
    onSurface = Color(onSurface),
    surfaceVariant = Color(surfaceVariant),
    onSurfaceVariant = Color(onSurfaceVariant),
    surfaceTint = Color(surfaceTint),
    inverseSurface = Color(inverseSurface),
    inverseOnSurface = Color(inverseOnSurface),
    error = Color(error),
    onError = Color(onError),
    errorContainer = Color(errorContainer),
    onErrorContainer = Color(onErrorContainer),
    outline = Color(outline),
    outlineVariant = Color(outlineVariant),
    scrim = Color(scrim),
    surfaceBright = Color(surfaceBright),
    surfaceDim = Color(surfaceDim),
    surfaceContainer = Color(surfaceContainer),
    surfaceContainerHigh = Color(surfaceContainerHigh),
    surfaceContainerHighest = Color(surfaceContainerHighest),
    surfaceContainerLow = Color(surfaceContainerLow),
    surfaceContainerLowest = Color(surfaceContainerLowest),
    primaryFixed = Color(primaryFixed),
    primaryFixedDim = Color(primaryFixedDim),
    onPrimaryFixed = Color(onPrimaryFixed),
    onPrimaryFixedVariant = Color(onPrimaryFixedVariant),
    secondaryFixed = Color(secondaryFixed),
    secondaryFixedDim = Color(secondaryFixedDim),
    onSecondaryFixed = Color(onSecondaryFixed),
    onSecondaryFixedVariant = Color(onSecondaryFixedVariant),
    tertiaryFixed = Color(tertiaryFixed),
    tertiaryFixedDim = Color(tertiaryFixedDim),
    onTertiaryFixed = Color(onTertiaryFixed),
    onTertiaryFixedVariant = Color(onTertiaryFixedVariant),
)
