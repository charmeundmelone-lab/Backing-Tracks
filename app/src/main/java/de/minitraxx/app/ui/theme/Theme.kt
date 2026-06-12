package de.minitraxx.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val StageDark = darkColorScheme(
    primary = Color(0xFF6FD3A6),
    onPrimary = Color(0xFF003822),
    secondary = Color(0xFF8AB4F8),
    onSecondary = Color(0xFF062E6F),
    background = Color(0xFF0E1113),
    onBackground = Color(0xFFE4E7E9),
    surface = Color(0xFF15191C),
    onSurface = Color(0xFFE4E7E9),
    surfaceVariant = Color(0xFF1F2528),
    onSurfaceVariant = Color(0xFFB8C0C6),
    error = Color(0xFFFF7A6E),
)

/** Bühnen-Theme: immer dunkel, hoher Kontrast. */
@Composable
fun MiniTraxxTheme(content: @Composable () -> Unit) {
    // isSystemInDarkTheme bewusst ignoriert — auf der Bühne ist dunkel Pflicht.
    isSystemInDarkTheme()
    MaterialTheme(colorScheme = StageDark, content = content)
}
