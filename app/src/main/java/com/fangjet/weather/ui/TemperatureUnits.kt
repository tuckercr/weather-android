package com.fangjet.weather.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.staticCompositionLocalOf
import com.fangjet.weather.R
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.WeatherUnits
import kotlin.math.roundToInt

/**
 * Whether temperatures show their unit ("89 °F") or just the degree sign ("89°", the default): Remote
 * Config `temperature_units_shown`, provided by CurrentWeatherRoute. [formatTemperature] reads it.
 */
val LocalTemperatureUnitsShown = staticCompositionLocalOf { RemoteConfigRepository.DEFAULT_TEMPERATURE_UNITS_SHOWN }

/** A temperature's string resource and its whole degrees, chosen without Compose so it's unit-testable. */
internal data class TemperatureText(
    @param:StringRes val resId: Int,
    val degrees: Int,
)

/** Whole degrees, with the unit ("89 °F", "-3 °C") or without it ("89°"); the number is the same either way. */
internal fun temperatureText(
    value: Double,
    units: WeatherUnits,
    unitShown: Boolean,
): TemperatureText {
    val resId =
        if (!unitShown) {
            R.string.format_temperature_no_unit
        } else {
            when (units) {
                WeatherUnits.METRIC -> R.string.format_temperature_c
                WeatherUnits.IMPERIAL -> R.string.format_temperature_f
            }
        }
    return TemperatureText(resId, value.roundToInt())
}
