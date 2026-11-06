package com.fangjet.weather.data

import com.fangjet.weather.data.remote.dto.OpenMeteoCurrentResponse
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.WeatherUnits
import java.time.ZoneOffset
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Turns Open-Meteo's current reading into the hero's [CurrentWeather]. It's the forecast model's
 * value for the exact spot, so it agrees with the hourly strip. OpenWeather's reading leans on the
 * nearest weather station, which can be miles away and several degrees off, so it's only the
 * backup now. Same rules as iOS's `Services/OpenMeteoCurrent.swift`.
 */
internal object OpenMeteoCurrent {
    /** Null without a time or temperature, so [WeatherRepository] falls back to OpenWeather. */
    fun weather(
        response: OpenMeteoCurrentResponse,
        units: WeatherUnits,
        cityName: String,
    ): CurrentWeather? {
        val reading = response.current
        val time = reading.time ?: return null
        val temperature = reading.temperature ?: return null
        val condition = WmoWeatherCodes.condition(reading.weatherCode ?: 3, (reading.isDay ?: 1) == 1)
        val sun = OpenMeteoForecastSource
            .sunTimes(response.daily, ZoneOffset.ofTotalSeconds(response.utcOffsetSeconds))
            .values
            .firstOrNull()
        // Today's high and low come from the hourly forecast, so they can miss a reading between hours.
        val high = response.daily.temperatureMax
            .firstOrNull()
            ?.let { maxOf(it, temperature) } ?: temperature
        val low = response.daily.temperatureMin
            .firstOrNull()
            ?.let { minOf(it, temperature) } ?: temperature
        return CurrentWeather(
            cityName = cityName,
            conditionMain = condition.main,
            description = condition.description.replaceFirstChar { it.titlecase(Locale.getDefault()) },
            iconCode = condition.iconCode,
            temperature = temperature,
            feelsLike = reading.apparentTemperature ?: temperature,
            tempMin = low,
            tempMax = high,
            humidityPercent = reading.relativeHumidity ?: 0,
            pressureHpa = (reading.seaLevelPressure ?: 0.0).roundToInt(),
            windSpeed = reading.windSpeed ?: 0.0,
            windDeg = reading.windDirection ?: 0,
            windGust = reading.windGusts,
            visibilityMeters = reading.visibility?.roundToInt(),
            cloudPercent = reading.cloudCover ?: 0,
            units = units,
            sunriseEpoch = sun?.first,
            sunsetEpoch = sun?.second,
            utcOffsetSeconds = response.utcOffsetSeconds,
            observedAtEpochSeconds = time,
        )
    }
}
