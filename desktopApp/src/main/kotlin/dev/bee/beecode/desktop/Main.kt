package dev.bee.beecode.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.design.BeeCodePalette
import dev.bee.beecode.design.ThemeChoice
import dev.bee.beecode.design.resolvesToDark
import dev.bee.beecode.design.themeChoice
import dev.bee.beecode.persistence.SettingsRepository
import java.io.File

/**
 * The desktop entry point.
 *
 * Owns the two decisions the shared code deliberately leaves to a client: where the
 * profile lives, and which Python interpreter to use.
 */
fun main() {
    // Open the profile *before* entering Compose's application builder. The builder
    // composes immediately and touches AWT for the display density, so anything
    // created inside it runs after that point — meaning a failure to open the
    // database would surface as a Compose error rather than as itself.
    val profile = Startup.openProfile()
    application {
        BeeCodeWindow(profile, onExit = ::exitApplication)
    }
}

@Composable
private fun BeeCodeWindow(profile: BeeCodeProfile, onExit: () -> Unit) {
    val state = rememberWindowState(width = 1180.dp, height = 820.dp)

    // Read once at launch, then held here so switching it in Settings recolours the
    // window immediately rather than at the next launch.
    var theme by remember { mutableStateOf(profile.settings.themeChoice()) }

    Window(
        onCloseRequest = onExit,
        state = state,
        title = "BeeCode",
    ) {
        // Close the database on window disposal so WAL is checkpointed into the
        // main file. Without it a backup taken by copying the file could miss the
        // most recent reviews.
        //
        // The in-flight draft is written by the Problem pane on Back and on finalize,
        // and there is no way to close the window from inside a Problem without
        // passing through one of them.
        DisposableEffect(Unit) {
            onDispose { profile.close() }
        }

        BeeCodeTheme(choice = theme) {
            Surface(color = MaterialTheme.colorScheme.background) {
                DesktopApp(profile, theme = theme, onThemeChange = { theme = it })
            }
        }
    }
}

/**
 * Where study state lives.
 *
 * Follows each platform's convention rather than dropping a dotfile in `$HOME`:
 * `XDG_DATA_HOME` on Linux, `Library/Application Support` on macOS, `%APPDATA%` on
 * Windows. A learner should be able to find and back up their own profile.
 */
internal fun profileDirectory(): File {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    val home = File(System.getProperty("user.home"))
    return when {
        os.contains("win") -> {
            val appData = System.getenv("APPDATA")
            if (appData != null) File(appData, "BeeCode") else File(home, "BeeCode")
        }
        os.contains("mac") -> File(home, "Library/Application Support/BeeCode")
        else -> {
            val xdg = System.getenv("XDG_DATA_HOME")
            if (xdg != null) File(xdg, "beecode") else File(home, ".local/share/beecode")
        }
    }
}

internal const val PACK_RESOURCE = "/dev/bee/beecode/problems.json"

/** Keys the desktop Settings screen writes. Shared with the repository's contract. */
internal val PYTHON_EXECUTABLE_KEY = SettingsRepository.KEY_PYTHON_EXECUTABLE

/**
 * BeeCode's theme.
 *
 * The colours come from [BeeCodePalette] in `:shared`, so the two clients cannot
 * drift — they had already, before that existed. The typography scale is in
 * [beeCodeTypography]. This function's only job is choosing a scheme.
 *
 * @param choice the learner's preference. Defaults to following the OS.
 * @param systemIsDark what the platform reports, or null when it does not know. The
 *   default deliberately does *not* use [isSystemInDarkTheme] directly: on Linux
 *   skiko's `currentSystemTheme` is `UNKNOWN`, which that function reports as "not
 *   dark", so BeeCode booted into its light scheme on every Linux desktop. Passing
 *   null for an unknown signal lets [resolvesToDark] choose dark instead.
 */
@Composable
fun BeeCodeTheme(
    choice: ThemeChoice = ThemeChoice.SYSTEM,
    systemIsDark: Boolean? = systemDarkOrNull(),
    content: @Composable () -> Unit,
) {
    val palette = if (choice.resolvesToDark(systemIsDark)) BeeCodePalette.Dark else BeeCodePalette.Light
    MaterialTheme(
        colorScheme = palette.toColorScheme(),
        typography = beeCodeTypography(),
        content = content,
    )
}

/**
 * Whether the OS is in dark mode, or null when it will not say.
 *
 * skiko exposes three states and `isSystemInDarkTheme()` collapses them to two, which
 * loses exactly the distinction that matters here. Verified on this host: `os.name =
 * Linux`, `currentSystemTheme = UNKNOWN`.
 */
@Composable
private fun systemDarkOrNull(): Boolean? = when (org.jetbrains.skiko.currentSystemTheme) {
    org.jetbrains.skiko.SystemTheme.DARK -> true
    org.jetbrains.skiko.SystemTheme.LIGHT -> false
    // Not `isSystemInDarkTheme()`: that maps UNKNOWN to false.
    org.jetbrains.skiko.SystemTheme.UNKNOWN -> null
}

/**
 * Map the shared palette onto Material's scheme.
 *
 * Every one of the 48 roles is assigned. Only 16 used to be, and the rest fell back
 * to the M3 baseline — which is purple, and is what made `Card` lavender and every
 * divider purple-grey on a warm cream background. [BeeCodePaletteTest] walks the
 * result reflectively and fails if any role does not match the palette, so a role
 * added by a Material upgrade cannot silently reintroduce the baseline.
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
