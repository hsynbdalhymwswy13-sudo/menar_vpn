package io.nekohasekai.sfa.compose.menar.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MenarColors = darkColorScheme(
    primary = Color(0xFFFF3344),
    onPrimary = Color(0xFF120307),
    primaryContainer = Color(0xFF4A1118),
    onPrimaryContainer = Color(0xFFFFD9DD),

    secondary = Color(0xFF9AA8B8),
    onSecondary = Color(0xFF101010),
    secondaryContainer = Color(0xFF34373D),
    onSecondaryContainer = Color(0xFFE0E3E7),

    tertiary = Color(0xFFB8BDC6),
    onTertiary = Color(0xFF101216),
    tertiaryContainer = Color(0xFF3A3E46),
    onTertiaryContainer = Color(0xFFE6E8EC),

    background = Color(0xFF050A12),
    onBackground = Color(0xFFF2F7FC),

    surface = Color(0xFF08111C),
    onSurface = Color(0xFFF2F7FC),

    surfaceVariant = Color(0xFF101D2B),
    onSurfaceVariant = Color(0xFFB7C7D8),

    outline = Color(0xFF294054),
    outlineVariant = Color(0xFF172A3A),

    error = Color(0xFFFF5A5F),
    onError = Color.White,
    errorContainer = Color(0xFF5C1518),
    onErrorContainer = Color(0xFFFFDAD9),
)

@Composable
fun MenarTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = MenarColors,
        content = content,
    )
}
