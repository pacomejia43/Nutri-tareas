package com.nutritareas.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = PinkPrimaryDark,
    onPrimary = OnPinkPrimaryDark,
    primaryContainer = PinkPrimaryContainerDark,
    onPrimaryContainer = OnPinkPrimaryContainerDark,
    secondary = RoseSecondaryDark,
    onSecondary = OnRoseSecondaryDark,
    secondaryContainer = RoseSecondaryContainerDark,
    onSecondaryContainer = OnRoseSecondaryContainerDark,
    tertiary = RoseTertiaryDark,
    onTertiary = OnRoseTertiaryDark,
    tertiaryContainer = RoseTertiaryContainerDark,
    onTertiaryContainer = OnRoseTertiaryContainerDark,
    background = BackgroundDark,
    onBackground = OnBackgroundDark,
    surface = SurfaceDark,
    onSurface = OnSurfaceDark,
    surfaceVariant = SurfaceVariantDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    outline = OutlineDark,
)

private val LightColors = lightColorScheme(
    primary = PinkPrimaryLight,
    onPrimary = OnPinkPrimaryLight,
    primaryContainer = PinkPrimaryContainerLight,
    onPrimaryContainer = OnPinkPrimaryContainerLight,
    secondary = RoseSecondaryLight,
    onSecondary = OnRoseSecondaryLight,
    secondaryContainer = RoseSecondaryContainerLight,
    onSecondaryContainer = OnRoseSecondaryContainerLight,
    tertiary = RoseTertiaryLight,
    onTertiary = OnRoseTertiaryLight,
    tertiaryContainer = RoseTertiaryContainerLight,
    onTertiaryContainer = OnRoseTertiaryContainerLight,
    background = BackgroundLight,
    onBackground = OnBackgroundLight,
    surface = SurfaceLight,
    onSurface = OnSurfaceLight,
    surfaceVariant = SurfaceVariantLight,
    onSurfaceVariant = OnSurfaceVariantLight,
    outline = OutlineLight,
)

@Composable
fun NutriTareasTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Off by default: the app has a deliberate pastel pink + white brand palette, and letting
    // Android 12+ recolor everything from the user's wallpaper (dynamic color) would defeat it.
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = NutriTareasTypography,
        content = content,
    )
}
