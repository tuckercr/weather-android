package com.fangjet.weather.data

import com.fangjet.weather.data.remote.dto.OpenMeteoCurrentResponse
import com.fangjet.weather.data.remote.dto.OpenMeteoDailyDto
import com.fangjet.weather.data.remote.dto.OpenMeteoHourlyDto
import com.fangjet.weather.data.remote.dto.OpenMeteoResponse
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.QuarterHour
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import javax.inject.Inject
import javax.inject.Singleton

/**
 * True hourly forecast from Open-Meteo (free, no API key), including UV index and the solar
 * radiation used to estimate pavement heat. Needs coordinates; name-only cities fall back to
 * OpenWeatherMap in [WeatherRepository].
 */
@Singleton
class OpenMeteoForecastSource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    suspend fun fetch(
        units: WeatherUnits,
        latitude: Double,
        longitude: Double,
    ): Result<List<DayForecast>> =
        get(
            units,
            latitude,
            longitude,
            mapOf(
                "hourly" to HOURLY_FIELDS,
                // Each day's sunrise and sunset, for the hourly strip, which reaches into tomorrow.
                "daily" to "sunrise,sunset",
                "forecast_days" to FORECAST_DAYS.toString(),
                // Yesterday too, so the pavement and wet ground remember last night's weather.
                "past_days" to "1",
                // A few hours of 15-minute rain and snow from now, for "Rain starting around…"
                // (a little more than the 2 hours shown, so a cached forecast still covers them).
                "minutely_15" to "precipitation,snowfall",
                "forecast_minutely_15" to QUARTER_HOURS.toString(),
                "past_minutely_15" to "0",
            ),
        ) { body ->
            val parsed = json.decodeFromString(OpenMeteoResponse.serializer(), body)
            toDayForecasts(parsed, units, System.currentTimeMillis() / 1000)
        }

    /** The reading now, for the hero, with today's high, low, sunrise and sunset ([OpenMeteoCurrent]). */
    suspend fun fetchCurrent(
        units: WeatherUnits,
        latitude: Double,
        longitude: Double,
    ): Result<OpenMeteoCurrentResponse> =
        get(
            units,
            latitude,
            longitude,
            mapOf(
                "current" to CURRENT_FIELDS,
                "daily" to "sunrise,sunset,temperature_2m_max,temperature_2m_min",
                "forecast_days" to "1",
            ),
        ) { body -> json.decodeFromString(OpenMeteoCurrentResponse.serializer(), body) }

    private suspend fun <T> get(
        units: WeatherUnits,
        latitude: Double,
        longitude: Double,
        fields: Map<String, String>,
        parse: (String) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = BASE_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("latitude", latitude.toString())
                    .addQueryParameter("longitude", longitude.toString())
                    .apply { fields.forEach { (name, value) -> addQueryParameter(name, value) } }
                    .addQueryParameter("timezone", "auto")
                    .addQueryParameter("timeformat", "unixtime")
                    .addQueryParameter("temperature_unit", if (units == WeatherUnits.IMPERIAL) "fahrenheit" else "celsius")
                    .addQueryParameter("wind_speed_unit", if (units == WeatherUnits.IMPERIAL) "mph" else "ms")
                    .build()
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error(if (response.code >= 500) "server_error" else "generic")
                    parse(response.body?.string() ?: error("invalid_payload"))
                }
            }
        }

    companion object {
        private const val BASE_URL = "https://api.open-meteo.com/v1/forecast"
        private const val FORECAST_DAYS = 7
        private const val HOURLY_FIELDS =
            "temperature_2m,apparent_temperature,precipitation_probability,weather_code," +
                "wind_speed_10m,wind_direction_10m,relative_humidity_2m,pressure_msl," +
                "uv_index,shortwave_radiation,is_day,precipitation,snow_depth"
        private const val CURRENT_FIELDS =
            "temperature_2m,apparent_temperature,relative_humidity_2m,weather_code," +
                "cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m,visibility,is_day"

        private const val QUARTER_HOURS = 16
        private const val QUARTER_HOUR_SECONDS = 15 * 60L

        /** How far back rain or snow counts towards wet (and, below freezing, icy) ground. */
        private const val WET_GROUND_HOURS = 12

        /**
         * Converts the parallel hourly arrays into day forecasts in the city's own timezone,
         * dropping hours before the current one so hourly views start at "now".
         */
        internal fun toDayForecasts(
            response: OpenMeteoResponse,
            units: WeatherUnits,
            nowEpochSeconds: Long,
        ): List<DayForecast> {
            val h = response.hourly
            val zone = response.timezone
                ?.let { runCatching { ZoneId.of(it) }.getOrNull() }
                ?: ZoneOffset.ofTotalSeconds(response.utcOffsetSeconds)
            // Slots start on local hour boundaries, which aren't UTC hour boundaries in half-hour
            // zones (India +5:30, Adelaide +9:30, Nepal +5:45), so floor "now" in local time.
            val offset = zone.rules.getOffset(Instant.ofEpochSecond(nowEpochSeconds)).totalSeconds
            val localNow = nowEpochSeconds + offset
            val currentHourStart = localNow - Math.floorMod(localNow, 3600L) - offset
            // Worked out over every hour sent (from yesterday's midnight), including the ones dropped
            // below, so the pavement remembers the night and the morning's sun.
            val pavementC = PavementHeat.series(
                airC = h.time.indices.map { i -> h.temperature.getOrNull(i)?.let(units::toCelsius) },
                shortwaveRadiationWm2 = h.time.indices.map { i -> h.shortwaveRadiation.getOrNull(i) },
                isDay = h.time.indices.map { i -> h.isDay.getOrNull(i) == 1 },
            )
            // Each local day's range over all of its hours, including the ones dropped below, so
            // late in the evening today's high is still the afternoon's, not the next few hours'.
            val localDate = { epoch: Long -> Instant.ofEpochSecond(epoch).atZone(zone).toLocalDate() }
            val dayRanges = h.time.indices
                .mapNotNull { i -> h.temperature.getOrNull(i)?.let { localDate(h.time[i]) to it } }
                .groupBy({ it.first }, { it.second })
                .mapValues { (_, temps) -> temps.min() to temps.max() }
            val slots = h.time.indices.mapNotNull { i ->
                val epoch = h.time[i]
                val temp = h.temperature.getOrNull(i) ?: return@mapNotNull null
                if (epoch < currentHourStart) return@mapNotNull null
                val isDay = h.isDay.getOrNull(i) == 1
                val condition = WmoWeatherCodes.condition(h.weatherCode.getOrNull(i) ?: 3, isDay)
                ForecastAggregator.Slot(
                    epochSeconds = epoch,
                    temperature = temp,
                    feelsLike = h.apparentTemperature.getOrNull(i) ?: temp,
                    tempMin = dayRanges[localDate(epoch)]?.first ?: temp,
                    tempMax = dayRanges[localDate(epoch)]?.second ?: temp,
                    conditionMain = condition.main,
                    description = condition.description,
                    iconCode = condition.iconCode,
                    windSpeed = h.windSpeed.getOrNull(i) ?: 0.0,
                    windDeg = h.windDirection.getOrNull(i) ?: 0,
                    humidity = h.relativeHumidity.getOrNull(i) ?: 0,
                    pressure = h.seaLevelPressure.getOrNull(i)?.toInt() ?: 0,
                    pop = (h.precipitationProbability.getOrNull(i) ?: 0) / 100.0,
                    // From the same index as the chance (both are Open-Meteo's figures for the hour
                    // before the time), so the rain outlook's bars pair a chance with its own amount.
                    precipitationMm = h.precipitation.getOrNull(i),
                    uvIndex = h.uvIndex.getOrNull(i),
                    pavementTemperature = pavementC[i]?.let(units::fromCelsius),
                    groundWet = groundWet(h, i),
                )
            }
            val sunTimes = sunTimes(response.daily, zone)
            val days = ForecastAggregator.aggregate(slots, zone, units).map { day ->
                val firstHour = day.hourlySlots.firstOrNull()?.epochSeconds
                val sun = firstHour?.let { sunTimes[localDate(it)] }
                if (sun == null) day else day.copy(sunriseEpoch = sun.first, sunsetEpoch = sun.second)
            }
            val quarters = quarterHours(response)
            return if (quarters.isEmpty() || days.isEmpty()) days else listOf(days.first().copy(quarterHours = quarters)) + days.drop(1)
        }

        /**
         * Each local day's sunrise and sunset, by its date. A time on a midnight is dropped: that's
         * how Open-Meteo marks a day the sun doesn't rise (both on its midnight) or set (the sunset
         * on the next).
         */
        internal fun sunTimes(
            daily: OpenMeteoDailyDto,
            zone: ZoneId,
        ): Map<LocalDate, Pair<Long?, Long?>> {
            fun real(epoch: Long?) = epoch?.takeUnless { Instant.ofEpochSecond(it).atZone(zone).toLocalTime() == LocalTime.MIDNIGHT }
            return daily.time.indices.associate { i ->
                Instant.ofEpochSecond(daily.time[i]).atZone(zone).toLocalDate() to
                    (real(daily.sunrise.getOrNull(i)) to real(daily.sunset.getOrNull(i)))
            }
        }

        /** Each amount is for the 15 minutes before its time, so its quarter starts 15 minutes earlier. */
        internal fun quarterHours(response: OpenMeteoResponse): List<QuarterHour> {
            val m = response.minutely15
            return m.time.indices.mapNotNull { i ->
                val precipitation = m.precipitation.getOrNull(i) ?: return@mapNotNull null
                QuarterHour(m.time[i] - QUARTER_HOUR_SECONDS, precipitation, m.snowfall.getOrNull(i) ?: 0.0)
            }
        }

        /**
         * Rain or snow in the [WET_GROUND_HOURS] before hour [i] starts or during it, or snow lying.
         * Precipitation at time t is the total for the hour before t, so hour i's own falls at i + 1.
         */
        internal fun groundWet(
            h: OpenMeteoHourlyDto,
            i: Int,
        ): Boolean =
            (h.snowDepth.getOrNull(i) ?: 0.0) > 0.0 ||
                (i - WET_GROUND_HOURS + 1..i + 1).any { (h.precipitation.getOrNull(it) ?: 0.0) > 0.0 }
    }
}
