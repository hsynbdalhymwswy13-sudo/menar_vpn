package io.nekohasekai.sfa.compose.theme

import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val MenarDarkColorScheme =
    darkColorScheme(
        primary = MenarRed,
        onPrimary = MenarWhite,

        primaryContainer = MenarRedDark,
        onPrimaryContainer = MenarWhite,

        secondary = MenarRedLight,
        onSecondary = MenarBlack,

        secondaryContainer = MenarNavyLight,
        onSecondaryContainer = MenarWhite,

        tertiary = InfoBlue,
        onTertiary = MenarWhite,

        background = MenarBlack,
        onBackground = MenarWhite,

        surface = MenarNavy,
        onSurface = MenarWhite,

        surfaceVariant = MenarNavyLight,
        onSurfaceVariant = MenarTextSecondary,

        outline = MenarBorder,
        outlineVariant = MenarBorder,
    )

@Composable
fun Theme(
    darkTheme: Boolean = true,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    // MENAR uses its own fixed dark palette.
    // Dynamic Android colors are intentionally disabled.
    val colorScheme = MenarDarkColorScheme

    val view = LocalView.current

    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect

            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }

            window.statusBarColor = MenarBlack.value.toInt()
            window.navigationBarColor = MenarBlack.value.toInt()
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
