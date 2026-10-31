package com.fangjet.weather.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.fangjet.weather.domain.SeasonalTheme

/** Today's seasonal theme for the city on screen, or null. Provided by CurrentWeatherContent. */
val LocalSeasonalTheme = staticCompositionLocalOf<SeasonalTheme?> { null }

fun SeasonalTheme.heroBackground(night: Boolean): Color = rgb(if (night) colors.heroNight else colors.heroDay)

fun SeasonalTheme.heroInk(night: Boolean): Color = rgb(if (night) colors.inkNight else colors.inkDay)

/** A card's background, warmed by today's theme when there is one. */
@Composable
fun seasonalCardColor(): Color =
    LocalSeasonalTheme.current?.let { rgb(if (isDark()) it.colors.cardDark else it.colors.cardLight) }
        ?: MaterialTheme.colorScheme.surfaceVariant

/** Section headers ("HOURLY", "TODAY"). */
@Composable
fun seasonalSectionHeaderColor(): Color =
    LocalSeasonalTheme.current?.let { rgb(if (isDark()) it.colors.accentDark else it.colors.accentLight) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

/** Each tile's icon and label ("Feels like"). */
@Composable
fun seasonalTileHeaderColor(): Color =
    LocalSeasonalTheme.current?.let { rgb(if (isDark()) it.colors.tileHeaderDark else it.colors.tileHeaderLight) }
        ?: MaterialTheme.colorScheme.primary

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun rgb(value: Int): Color = Color(0xFF000000 or value.toLong())
