package com.fangjet.weather.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val LightColorScheme = lightColorScheme(
    primary = SkyBlueDark,
    onPrimary = androidx.compose.ui.graphics.Color.White,
    primaryContainer = SkyBlueContainer,
    onPrimaryContainer = SkyBlueDark,
    secondary = BrandYellow,
    onSecondary = OnBrandYellow,
    background = SurfaceLight,
    surface = SurfaceLight,
    surfaceVariant = SkyBlueSurface,
    onSurfaceVariant = OnSurfaceVariantLight,
    surfaceContainerLowest = SurfaceContainerLowestLight,
    surfaceContainerLow = SurfaceContainerLowLight,
    surfaceContainer = SurfaceContainerMidLight,
    surfaceContainerHigh = SurfaceContainerHighLight,
    surfaceContainerHighest = SurfaceContainerHighestLight,
    surfaceBright = SurfaceLight,
    surfaceDim = SurfaceContainerHighestLight,
    outline = OutlineLight,
    outlineVariant = OutlineVariantLight,
    inverseSurface = InverseSurfaceLight,
    inverseOnSurface = InverseOnSurfaceLight,
    inversePrimary = SkyBlueNight,
)

private val DarkColorScheme = darkColorScheme(
    primary = SkyBlueNight,
    onPrimary = SurfaceDark,
    primaryContainer = SurfaceContainerDark,
    onPrimaryContainer = SkyBlueNight,
    secondary = BrandYellowNight,
    onSecondary = SurfaceDark,
    background = SurfaceDark,
    surface = SurfaceDark,
    surfaceVariant = SurfaceContainerDark,
    onSurfaceVariant = OnSurfaceVariantDark,
    surfaceContainerLowest = SurfaceContainerLowestDark,
    surfaceContainerLow = SurfaceContainerLowDark,
    surfaceContainer = SurfaceContainerMidDark,
    surfaceContainerHigh = SurfaceContainerDark,
    surfaceContainerHighest = SurfaceContainerHighestDark,
    surfaceBright = SurfaceContainerHighestDark,
    surfaceDim = SurfaceDark,
    outline = OutlineDark,
    outlineVariant = OutlineVariantDark,
    inverseSurface = InverseSurfaceDark,
    inverseOnSurface = InverseOnSurfaceDark,
    inversePrimary = SkyBlueDark,
)

@Composable
fun CatsDogsTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
        typography = Typography,
        content = content,
    )
}
