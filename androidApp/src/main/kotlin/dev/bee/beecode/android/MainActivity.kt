package dev.bee.beecode.android

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.bee.beecode.android.ui.BeeCodeApp
import dev.bee.beecode.android.ui.StudyViewModel

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
            BeeCodeTheme {
                Surface(color = MaterialTheme.colorScheme.background) {
                    BeeCodeApp(viewModel)
                }
            }
        }
    }
}

/**
 * BeeCode's colours.
 *
 * Honey amber on near-black, because the app is for practising at 5am and a bright
 * white screen at that hour is hostile. Contrast ratios are kept above 4.5:1
 * against their backgrounds for the accessibility baseline.
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
        surface = Color(0xFFFFF8F0),
        onSurface = Color(0xFF1E1B16),
        surfaceVariant = Color(0xFFEDE1CF),
        onSurfaceVariant = Color(0xFF4C4639),
        error = Color(0xFFBA1A1A),
        onError = Color(0xFFFFFFFF),
        outline = Color(0xFF7E7767),
    )
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) dark else light,
        content = content,
    )
}
