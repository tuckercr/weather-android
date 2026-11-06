@file:OptIn(kotlinx.serialization.InternalSerializationApi::class)

package com.fangjet.weather.domain

import kotlinx.serialization.Serializable

@Serializable
data class CurrentWeather(
    /** Shown in the UI: matches what the user searched (when provided), not necessarily OpenWeather's `name`. */
    val cityName: String,
    val conditionMain: String,
    val description: String,
    val iconCode: String,
    val temperature: Double,
    val feelsLike: Double,
    val tempMin: Double,
    val tempMax: Double,
    val humidityPercent: Int,
    val pressureHpa: Int,
    val windSpeed: Double,
    val windDeg: Int,
    val visibilityMeters: Int?,
    val cloudPercent: Int,
    val units: WeatherUnits,
    val sunriseEpoch: Long? = null,
    val sunsetEpoch: Long? = null,
    /**
     * The location's offset from UTC, in seconds; null in data cached before it was recorded.
     * Read [offsetSeconds] instead, which falls back sensibly.
     */
    val utcOffsetSeconds: Int? = null,
    /**
     * When OpenWeather took this reading, in epoch seconds; null in data cached before it was
     * recorded. Shown as "Showing weather from ..." when a refresh fails and cached data stays up.
     */
    val observedAtEpochSeconds: Long? = null,
    /**
     * The strongest gusts now, in the wind speed's units; null when the provider doesn't say, and
     * in data cached before it was recorded.
     */
    val windGust: Double? = null,
) {
    /**
     * Offset for rendering the location's local time. Unknown offsets use the device's own zone,
     * which is right for "My Location" and closer than UTC for most saved cities.
     */
    val offsetSeconds: Int
        get() = utcOffsetSeconds
            ?: java.time.ZoneId
                .systemDefault()
                .rules
                .getOffset(java.time.Instant.now())
                .totalSeconds
}

/** A single 3-hour forecast slot used in the day-detail hourly breakdown. */
@Serializable
data class HourlySlot(
    val timeLabel: String,
    val iconCode: String,
    val description: String,
    val temperature: Double,
    val feelsLike: Double,
    val windSpeed: Double,
    val windDeg: Int,
    val humidity: Int,
    val pressure: Int,
    val units: WeatherUnits,
    /** Probability of precipitation as a percentage, 0..100. */
    val precipitationChance: Int = 0,
    /**
     * The hour's forecast rain and snow, in mm of water whatever the [units]. Null when unknown: the
     * OpenWeatherMap 3-hourly fallback, and forecasts cached before it existed.
     */
    val precipitationMm: Double? = null,
    /** UV index (0..11+); null when the source doesn't provide it. */
    val uvIndex: Double? = null,
    /** Estimated sun-baked pavement temperature in [units]; null when unknown. */
    val pavementTemperature: Double? = null,
    /** Hour of day (0..23) in the location's own timezone; null in data cached before it existed. */
    val localHour: Int? = null,
    /** Start of the slot, Unix epoch seconds; null in data cached before it existed. */
    val epochSeconds: Long? = null,
    /**
     * Rain or snow in the 12 hours before the slot or during it, or snow on the ground: below
     * freezing, the pavement may be icy or salted. False when the source doesn't say.
     */
    val groundWet: Boolean = false,
    /**
     * The hour's air quality on the city's scale. Null when unknown, which the walk planner treats
     * as clean air.
     */
    val airQuality: AirQuality? = null,
)

@Serializable
data class DayForecast(
    val dateLabel: String,
    val conditionMain: String,
    val description: String,
    val iconCode: String,
    val temperature: Double,
    val feelsLike: Double,
    val tempMin: Double,
    val tempMax: Double,
    val units: WeatherUnits,
    /** All 3-hour slots for this calendar day, ordered chronologically. */
    val hourlySlots: List<HourlySlot> = emptyList(),
    /** Highest probability of precipitation across the day's slots, as a percentage, 0..100. */
    val precipitationChance: Int = 0,
    /** Peak UV index across the day's slots; null when the source doesn't provide UV. */
    val uvIndexMax: Double? = null,
    /**
     * The next few hours in 15-minute steps, from the one in progress, for "Rain starting around
     * 3:15 PM". Only on the first day (it reaches a few hours ahead, whatever the date), and empty
     * when the source doesn't provide it.
     */
    val quarterHours: List<QuarterHour> = emptyList(),
    /**
     * The day's sunrise and sunset, for the hourly strip. Null when the sun doesn't rise or set that
     * day (polar night or day), when the source doesn't say (the OpenWeather 3-hourly fallback), or
     * in a forecast cached before they were kept.
     */
    val sunriseEpoch: Long? = null,
    val sunsetEpoch: Long? = null,
)

/** Rain and snow over the 15 minutes from [epochSeconds]. */
@Serializable
data class QuarterHour(
    val epochSeconds: Long,
    /** Rain and snow together, in mm of water. */
    val precipitationMm: Double,
    val snowfallCm: Double = 0.0,
)
