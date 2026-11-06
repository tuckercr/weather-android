package com.fangjet.weather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Response of Open-Meteo's /v1/forecast with `timeformat=unixtime` and `timezone=auto`. */
@Serializable
data class OpenMeteoResponse(
    // The location's offset from UTC, in seconds (timezone=auto resolves it from the coordinates).
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    // IANA zone name (e.g. "Europe/London"), so DST changes inside the forecast window are honoured.
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("hourly") val hourly: OpenMeteoHourlyDto = OpenMeteoHourlyDto(),
    @SerialName("minutely_15") val minutely15: OpenMeteoMinutely15Dto = OpenMeteoMinutely15Dto(),
    @SerialName("daily") val daily: OpenMeteoDailyDto = OpenMeteoDailyDto(),
)

/**
 * Parallel arrays, one entry per local day from [time][i], its midnight. In a polar night both
 * times are that midnight, and in a polar day the sunset is the next one.
 */
@Serializable
data class OpenMeteoDailyDto(
    @SerialName("time") val time: List<Long> = emptyList(),
    @SerialName("sunrise") val sunrise: List<Long?> = emptyList(),
    @SerialName("sunset") val sunset: List<Long?> = emptyList(),
    // Asked for only with the current reading, for the day's high and low.
    @SerialName("temperature_2m_max") val temperatureMax: List<Double?> = emptyList(),
    @SerialName("temperature_2m_min") val temperatureMin: List<Double?> = emptyList(),
)

/**
 * Response of Open-Meteo's /v1/forecast asked for the `current` reading and today's `daily` values,
 * with `timeformat=unixtime` and `timezone=auto`.
 */
@Serializable
data class OpenMeteoCurrentResponse(
    @SerialName("utc_offset_seconds") val utcOffsetSeconds: Int = 0,
    @SerialName("current") val current: OpenMeteoCurrentDto = OpenMeteoCurrentDto(),
    @SerialName("daily") val daily: OpenMeteoDailyDto = OpenMeteoDailyDto(),
)

/** The model's reading for the current 15 minutes, starting at [time]. Visibility is in metres whatever the units. */
@Serializable
data class OpenMeteoCurrentDto(
    @SerialName("time") val time: Long? = null,
    @SerialName("temperature_2m") val temperature: Double? = null,
    @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
    @SerialName("relative_humidity_2m") val relativeHumidity: Int? = null,
    @SerialName("weather_code") val weatherCode: Int? = null,
    @SerialName("cloud_cover") val cloudCover: Int? = null,
    @SerialName("pressure_msl") val seaLevelPressure: Double? = null,
    @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    @SerialName("wind_direction_10m") val windDirection: Int? = null,
    @SerialName("wind_gusts_10m") val windGusts: Double? = null,
    @SerialName("visibility") val visibility: Double? = null,
    @SerialName("is_day") val isDay: Int? = null,
)

/**
 * Parallel arrays in 15-minute steps. Like the hourly ones, the amounts at [time][i] are totals for
 * the 15 minutes *before* it: rain and snow in mm water, snowfall in cm.
 */
@Serializable
data class OpenMeteoMinutely15Dto(
    @SerialName("time") val time: List<Long> = emptyList(),
    @SerialName("precipitation") val precipitation: List<Double?> = emptyList(),
    @SerialName("snowfall") val snowfall: List<Double?> = emptyList(),
)

/** Parallel arrays: index i of every list describes the hour starting at [time][i]. */
@Serializable
data class OpenMeteoHourlyDto(
    @SerialName("time") val time: List<Long> = emptyList(),
    @SerialName("temperature_2m") val temperature: List<Double?> = emptyList(),
    @SerialName("apparent_temperature") val apparentTemperature: List<Double?> = emptyList(),
    @SerialName("precipitation_probability") val precipitationProbability: List<Int?> = emptyList(),
    @SerialName("weather_code") val weatherCode: List<Int?> = emptyList(),
    @SerialName("wind_speed_10m") val windSpeed: List<Double?> = emptyList(),
    @SerialName("wind_direction_10m") val windDirection: List<Int?> = emptyList(),
    @SerialName("relative_humidity_2m") val relativeHumidity: List<Int?> = emptyList(),
    @SerialName("pressure_msl") val seaLevelPressure: List<Double?> = emptyList(),
    @SerialName("uv_index") val uvIndex: List<Double?> = emptyList(),
    @SerialName("shortwave_radiation") val shortwaveRadiation: List<Double?> = emptyList(),
    @SerialName("is_day") val isDay: List<Int?> = emptyList(),
    // Rain and snow over the hour before [time][i], in mm; snow lying on the ground, in metres.
    @SerialName("precipitation") val precipitation: List<Double?> = emptyList(),
    @SerialName("snow_depth") val snowDepth: List<Double?> = emptyList(),
)
