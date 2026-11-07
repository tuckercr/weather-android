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

/** Each tile's icon and label ("Feels like"): the brand colour unless today's theme sets its own. */
@Composable
fun seasonalTileHeaderColor(): Color =
    LocalSeasonalTheme.current
        ?.let { if (isDark()) it.colors.tileHeaderDark else it.colors.tileHeaderLight }
        ?.let(::rgb)
        ?: MaterialTheme.colorScheme.primary

@Composable
private fun isDark(): Boolean = MaterialTheme.colorScheme.surface.luminance() < 0.5f

private fun rgb(value: Int): Color = Color(0xFF000000 or value.toLong())
