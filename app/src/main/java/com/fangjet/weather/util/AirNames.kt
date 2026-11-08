package com.fangjet.weather.util

import androidx.annotation.StringRes
import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality

/**
 * What the pollutant behind bad air is called, for captions and notifications: "Smoky air" for fine
 * particles, "Dusty air" for coarse ones, "High ozone". Null for the rest, which each screen words
 * in its own way.
 */
@StringRes
fun airNameRes(pollutant: AirQuality.Pollutant?): Int? =
    when (pollutant) {
        AirQuality.Pollutant.FINE_PARTICLES -> R.string.walk_air_smoky
        AirQuality.Pollutant.COARSE_PARTICLES -> R.string.walk_air_dusty
        AirQuality.Pollutant.OZONE -> R.string.walk_air_ozone
        else -> null
    }

/** The name for bad air in [pollutant]'s name, "Poor air" for the rest, as the hero and the notifications say it. */
@StringRes
fun airNameOrPoorRes(pollutant: AirQuality.Pollutant?): Int = airNameRes(pollutant) ?: R.string.walk_air_poor
