package pl.pam.startproject.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val LightColorScheme = lightColorScheme(
    primary = PamPrimary,
    onPrimary = PamOnPrimary,
    primaryContainer = PamPrimaryContainer,
    onPrimaryContainer = PamPrimary,
    secondary = PamSecondary,
    onSecondary = Color(0xFFFFFFFF),
    surface = PamSurface,
    onSurface = Color(0xFF1A1C1E),
    surfaceContainerHighest = PamSurfaceVariant,
    surfaceContainerHigh = Color(0xFFE3E8EE),
    surfaceContainer = PamSurfaceVariant,
    outline = PamOutline,
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
)

private val DarkColorScheme = darkColorScheme(
    primary = PamPrimaryDark,
    onPrimary = Color(0xFF003258),
    primaryContainer = Color(0xFF004A77),
    onPrimaryContainer = PamPrimaryDark,
    secondary = PamSecondaryDark,
    onSecondary = Color(0xFF5F1600),
    surface = PamSurfaceDark,
    onSurface = Color(0xFFE2E2E6),
    surfaceContainerHighest = PamSurfaceVariantDark,
    surfaceContainerHigh = Color(0xFF252D38),
    surfaceContainer = PamSurfaceVariantDark,
    outline = Color(0xFF8A96A8),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun StartProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
