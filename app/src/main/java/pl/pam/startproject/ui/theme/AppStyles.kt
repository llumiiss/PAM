package pl.pam.startproject.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp

/**
 * Centralne tokeny stylu aplikacji (odstępy, rozmiary, wybrane style tekstu i kolory).
 * Ekrany powinny używać tych wartości zamiast "twardych" liczb.
 */
object AppDimens {
    val ScreenPaddingHorizontal = 16.dp
    val ScreenPaddingVertical = 12.dp
    val ScreenBottomPadding = 24.dp
    val CardPadding = 16.dp
    val CardPaddingLarge = 20.dp
    val SectionGap = 16.dp
    val ContentGap = 12.dp
    val ContentGapSmall = 10.dp
    val ChipGap = 8.dp
    val IconTextGap = 8.dp
    val StatGap = 4.dp
}

object AppText {
    @Composable
    fun screenTitle(): TextStyle = MaterialTheme.typography.titleLarge

    @Composable
    fun sectionTitle(): TextStyle = MaterialTheme.typography.titleMedium

    @Composable
    fun sectionLabel(): TextStyle = MaterialTheme.typography.labelLarge

    @Composable
    fun body(): TextStyle = MaterialTheme.typography.bodyMedium

    @Composable
    fun bodySmall(): TextStyle = MaterialTheme.typography.bodySmall

    @Composable
    fun value(): TextStyle = MaterialTheme.typography.headlineSmall

    @Composable
    fun statLabel(): TextStyle = MaterialTheme.typography.labelSmall
}

object AppColors {
    @Composable
    fun secondaryText(): Color = MaterialTheme.colorScheme.onSurfaceVariant

    @Composable
    fun error(): Color = MaterialTheme.colorScheme.error

    @Composable
    fun success(): Color = MaterialTheme.colorScheme.primary
}
