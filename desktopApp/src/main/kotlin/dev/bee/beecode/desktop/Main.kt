package dev.bee.beecode.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import dev.bee.beecode.app.BeeCodeProfile
import dev.bee.beecode.app.ProblemCatalogue
import dev.bee.beecode.persistence.SettingsRepository
import dev.bee.beecode.python.jvm.ProcessPythonRunner
import java.io.File

/**
 * The desktop entry point.
 *
 * Owns the two decisions the shared code deliberately leaves to a client: where the
 * profile lives, and which Python interpreter to use.
 */
fun main() = application {
    val profile = rememberProfile()
    val state = rememberWindowState(width = 1180.dp, height = 820.dp)

    Window(
        onCloseRequest = ::exitApplication,
        state = state,
        title = "BeeCode",
    ) {
        // Close the database on window disposal so WAL is checkpointed into the
        // main file. Without it a backup taken by copying the file could miss the
        // most recent reviews.
        DisposableEffect(Unit) {
            onDispose { profile.close() }
        }

        BeeCodeTheme {
            Surface(color = MaterialTheme.colorScheme.background) {
                DesktopApp(profile)
            }
        }
    }
}

/**
 * Open the learner's profile.
 *
 * Not a composable `remember`: the profile owns a database handle and must be
 * created exactly once for the process, before any window exists.
 */
private fun rememberProfile(): BeeCodeProfile {
    val dataDirectory = profileDirectory()
    dataDirectory.mkdirs()

    val catalogue = ProblemCatalogue.fromResource(PACK_RESOURCE)

    // The interpreter the learner chose in Settings, if any. Read before the
    // profile so a chosen path applies from the first run of the session.
    val databaseFile = File(dataDirectory, "beecode.db")
    val bootstrap = BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = catalogue,
        runner = ProcessPythonRunner(),
    )
    val chosen = bootstrap.settings.pythonExecutable()
    if (chosen == null) return bootstrap

    // A custom interpreter was configured, so reopen with it. Reopening rather than
    // mutating keeps the runner immutable for the profile's lifetime, which is what
    // makes a run's recorded runner identity trustworthy.
    bootstrap.close()
    return BeeCodeProfile.open(
        databasePath = databaseFile.absolutePath,
        catalogue = catalogue,
        runner = ProcessPythonRunner(pythonExecutable = chosen),
    )
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
 * BeeCode's colours, identical to Android's.
 *
 * Honey amber on near-black: the app is for practising at 5am, and a bright white
 * screen at that hour is hostile.
 */
@Composable
fun BeeCodeTheme(content: @Composable () -> Unit) {
    val dark = darkColorScheme(
        primary = Color(0xFFF2B32C),
        onPrimary = Color(0xFF241A00),
        primaryContainer = Color(0xFF3A2E0A),
        onPrimaryContainer = Color(0xFFFFDF9E),
        secondary = Color(0xFFD3C4A0),
        background = Color(0xFF14120E),
        onBackground = Color(0xFFE9E2D4),
        surface = Color(0xFF1C1A15),
        onSurface = Color(0xFFE9E2D4),
        surfaceVariant = Color(0xFF2A2720),
        onSurfaceVariant = Color(0xFFCEC6B4),
        error = Color(0xFFFFB4A4),
        onError = Color(0xFF5F1600),
        errorContainer = Color(0xFF5A2318),
        onErrorContainer = Color(0xFFFFDAD4),
        outline = Color(0xFF98917F),
    )
    val light = lightColorScheme(
        primary = Color(0xFF7A5900),
        onPrimary = Color(0xFFFFFFFF),
        primaryContainer = Color(0xFFFFDF9E),
        onPrimaryContainer = Color(0xFF261A00),
        secondary = Color(0xFF6A5D3F),
        background = Color(0xFFFFF8F0),
        onBackground = Color(0xFF1E1B16),
        surface = Color(0xFFFFFCF7),
        onSurface = Color(0xFF1E1B16),
        surfaceVariant = Color(0xFFEDE1CF),
        onSurfaceVariant = Color(0xFF4C4639),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        errorContainer = Color(0xFFFFDAD6),
        onErrorContainer = Color(0xFF410002),
        outline = Color(0xFF7E7767),
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark else light,
        content = content,
    )
}
