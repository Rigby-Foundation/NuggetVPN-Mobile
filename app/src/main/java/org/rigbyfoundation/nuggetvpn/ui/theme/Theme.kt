package org.rigbyfoundation.nuggetvpn.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightColorScheme = lightColorScheme(
    primary = NuggetPrimaryLight,
    onPrimary = NuggetOnPrimaryLight,
    primaryContainer = NuggetSurfaceLight,
    onPrimaryContainer = NuggetPrimaryLight,
    secondary = NuggetSurfaceLight,
    onSecondary = NuggetOnSurfaceLight,
    secondaryContainer = NuggetSurfaceLight,
    onSecondaryContainer = NuggetOnSurfaceLight,
    background = NuggetBackgroundLight,
    onBackground = NuggetOnBackgroundLight,
    surface = NuggetBackgroundLight,
    onSurface = NuggetOnSurfaceLight,
    surfaceVariant = NuggetSurfaceVariantLight,
    onSurfaceVariant = NuggetMutedLight,
    outline = NuggetOutlineLight,
    outlineVariant = NuggetOutlineLight,
    error = NuggetDestructive,
    onError = Color.White,
    errorContainer = NuggetDestructiveLight,
    onErrorContainer = NuggetDestructive,
)

private val DarkColorScheme = darkColorScheme(
    primary = NuggetPrimaryDark,
    onPrimary = NuggetOnPrimaryDark,
    primaryContainer = NuggetSurfaceDark,
    onPrimaryContainer = NuggetPrimaryDark,
    secondary = NuggetSurfaceVariantDark,
    onSecondary = NuggetOnSurfaceDark,
    secondaryContainer = NuggetSurfaceVariantDark,
    onSecondaryContainer = NuggetOnSurfaceDark,
    background = NuggetBackgroundDark,
    onBackground = NuggetOnBackgroundDark,
    surface = NuggetBackgroundDark,
    onSurface = NuggetOnSurfaceDark,
    surfaceVariant = NuggetSurfaceVariantDark,
    onSurfaceVariant = NuggetMutedDark,
    outline = NuggetOutlineDark,
    outlineVariant = NuggetOutlineDark,
    error = NuggetDestructive,
    onError = Color.White,
    errorContainer = Color(0xFF3D1212),
    onErrorContainer = NuggetDestructive,
)

@Composable
fun NuggetVPNTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
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

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NuggetTypography,
        content = content
    )
}
