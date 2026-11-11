package com.fangjet.weather.data

import com.fangjet.weather.data.remote.OpenWeatherApi
import com.fangjet.weather.data.remote.dto.OpenWeatherErrorResponse
import com.fangjet.weather.di.WeatherNetworkModule
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.AirQualityHours
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.serialization.json.Json
import retrofit2.HttpException
import java.io.IOException
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    private val api: OpenWeatherApi,
    private val openMeteo: OpenMeteoForecastSource,
    @param:Named(WeatherNetworkModule.OWM_API_KEY) private val apiKey: String,
    private val zoneId: ZoneId,
    private val json: Json,
    private val forecastSourcePolicy: ForecastSourcePolicy = ForecastSourcePolicy.OpenMeteoPreferred,
    // Hourly air quality for the tile and the walk planner; null leaves it out (tests).
    private val airQuality: AirQualitySource? = null,
    // Names My Location when its reading comes from Open-Meteo.
    private val placeNames: PlaceNaming = PlaceNaming.None,
) {

    /**
     * With coordinates, Open-Meteo's reading for the exact spot, which agrees with the hourly strip.
     * OpenWeather's is the backup: when Open-Meteo fails or Remote Config switches it off, and for
     * name-only cities. An empty [locationLabel] (My Location) is named by the phone.
     */
    suspend fun fetchCurrentWeather(
        units: WeatherUnits,
        locationLabel: String = "",
        cityQuery: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Result<CurrentWeather> {
        if (latitude != null && longitude != null && openMeteoEnabled()) {
            openMeteoCurrent(units, locationLabel, latitude, longitude)?.let { return Result.success(it) }
        }
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return Result.failure(IllegalStateException("missing_api_key"))
        }
        if (latitude != null && longitude != null) {
            return runCatching {
                val response = api.currentWeather(
                    cityQuery = null,
                    latitude = latitude,
                    longitude = longitude,
                    apiKey = key,
                    units = units.units,
                )
                mapCurrent(response, units, locationLabel)
            }.mapApiFailure()
        }
        val q = cityQuery?.trim().orEmpty()
        if (q.isEmpty()) {
            return Result.failure(IllegalStateException("empty_query"))
        }
        return runCatching {
            val response = api.currentWeather(
                cityQuery = q,
                latitude = null,
                longitude = null,
                apiKey = key,
                units = units.units,
            )
            mapCurrent(response, units, locationLabel)
        }.mapApiFailure()
    }

    suspend fun fetchForecast(
        units: WeatherUnits,
        cityQuery: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
    ): Result<List<DayForecast>> = fetchForecastWithAirQuality(units, cityQuery, latitude, longitude).map { it.days }

    /**
     * The forecast, with each hour's air quality on it, and the air quality hours themselves for the
     * caller to cache. [cachedAirQuality] (the city's last, from the cache) is used instead of a
     * fetch while [AirQualityHours.reusable]. Same rules as iOS's `WeatherRepository`.
     */
    suspend fun fetchForecastWithAirQuality(
        units: WeatherUnits,
        cityQuery: String? = null,
        latitude: Double? = null,
        longitude: Double? = null,
        cachedAirQuality: AirQualityHours? = null,
        nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
    ): Result<ForecastWithAirQuality> {
        val hasCoordinates = latitude != null && longitude != null
        // Prefer Open-Meteo (true hourly, UV, pavement heat) whenever we have coordinates,
        // unless Remote Config has switched it off.
        if (hasCoordinates && openMeteoEnabled()) {
            val forecast = openMeteo.fetch(units, latitude, longitude).mapApiFailure().getOrElse { return Result.failure(it) }
            // Air quality comes after, on the scale for the forecast's time zone, for the tile and the
            // walk planner; without it the forecast still stands.
            val air = airQualityHours(latitude, longitude, forecast.timeZone, cachedAirQuality, nowEpochSeconds())
            return Result.success(ForecastWithAirQuality(withAirQuality(forecast.days, air?.hours.orEmpty()), air))
        }
        return openWeatherForecast(units, cityQuery, latitude, longitude).map { ForecastWithAirQuality(it, null) }
    }

    /**
     * [cached] while it's [AirQualityHours.reusable] on the scale for [timeZone], otherwise a fetch
     * of that scale alone (both when the zone isn't known). Null when air quality is off or fails.
     */
    private suspend fun airQualityHours(
        latitude: Double,
        longitude: Double,
        timeZone: String?,
        cached: AirQualityHours?,
        nowEpochSeconds: Long,
    ): AirQualityHours? {
        val source = airQuality?.takeIf { runCatching { forecastSourcePolicy.airQualityEnabled() }.getOrDefault(true) } ?: return null
        val scale = timeZone?.let(AirQuality.Scale::forTimeZone)
        if (scale != null && cached?.reusable(scale, nowEpochSeconds) == true) return cached
        return source.fetchHourly(latitude, longitude, scale, nowEpochSeconds).getOrNull()
    }

    /** OpenWeatherMap's 3-hourly forecast: by coordinates when Open-Meteo is switched off, else by name. */
    private suspend fun openWeatherForecast(
        units: WeatherUnits,
        cityQuery: String?,
        latitude: Double?,
        longitude: Double?,
    ): Result<List<DayForecast>> {
        val hasCoordinates = latitude != null && longitude != null
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return Result.failure(IllegalStateException("missing_api_key"))
        }
        if (hasCoordinates) {
            // Open-Meteo switched off: the OpenWeatherMap 3-hourly forecast, by coordinates.
            return runCatching {
                val response = api.forecast(
                    cityQuery = null,
                    latitude = latitude,
                    longitude = longitude,
                    apiKey = key,
                    units = units.units,
                )
                mapForecast(response, units)
            }.mapApiFailure()
        }
        val q = cityQuery?.trim().orEmpty()
        if (q.isEmpty()) {
            return Result.failure(IllegalStateException("empty_query"))
        }
        return runCatching {
            val response = api.forecast(
                cityQuery = q,
                latitude = null,
                longitude = null,
                apiKey = key,
                units = units.units,
            )
            mapForecast(response, units)
        }.mapApiFailure()
    }

    /**
     * Null when Open-Meteo fails or sends no reading. The place is named only after a reading
     * arrives, so a failure doesn't spend one of the phone's lookups.
     */
    private suspend fun openMeteoCurrent(
        units: WeatherUnits,
        locationLabel: String,
        latitude: Double,
        longitude: Double,
    ): CurrentWeather? {
        val response = openMeteo.fetchCurrent(units, latitude, longitude).getOrNull() ?: return null
        val weather = OpenMeteoCurrent.weather(response, units, cityName = "") ?: return null
        val cityName = locationLabel.trim().ifEmpty {
            runCatching { placeNames.name(latitude, longitude) }.getOrNull() ?: PlaceNaming.MY_LOCATION_LABEL
        }
        return weather.copy(cityName = cityName)
    }

    /** A failing policy never breaks forecasts: fall back to the default, Open-Meteo. */
    private fun openMeteoEnabled(): Boolean = runCatching { forecastSourcePolicy.openMeteoEnabled() }.getOrDefault(true)

    private fun mapForecast(
        response: com.fangjet.weather.data.remote.dto.ForecastResponse,
        units: WeatherUnits,
    ): List<DayForecast> {
        val slots = response.list.map { item ->
            val w = item.weather.firstOrNull()
                ?: throw IllegalStateException("invalid_payload")
            ForecastAggregator.Slot(
                epochSeconds = item.dt,
                temperature = item.main.temp,
                feelsLike = item.main.feelsLike,
                tempMin = item.main.tempMin,
                tempMax = item.main.tempMax,
                conditionMain = w.main,
                description = w.description,
                iconCode = w.icon,
                windSpeed = item.wind.speed,
                windDeg = item.wind.deg,
                humidity = item.main.humidity,
                pressure = item.main.pressure,
                pop = item.pop,
            )
        }
        // Group and label in the city's own local time (from the API), not the device timezone,
        // so a remote city's forecast days/hours read in that city's clock.
        val cityZone = response.city?.let { java.time.ZoneOffset.ofTotalSeconds(it.timezone) } ?: zoneId
        return ForecastAggregator.aggregate(slots, cityZone, units)
    }

    private fun mapCurrent(
        response: com.fangjet.weather.data.remote.dto.CurrentWeatherResponse,
        units: WeatherUnits,
        locationLabel: String,
    ): CurrentWeather {
        val w = response.weather.firstOrNull() ?: throw IllegalStateException("invalid_payload")
        val displayCity = locationLabel.trim().takeIf { it.isNotEmpty() } ?: response.name
        return CurrentWeather(
            cityName = displayCity,
            conditionMain = w.main,
            description = w.description.replaceFirstChar { char ->
                char.titlecase(java.util.Locale.getDefault())
            },
            iconCode = w.icon,
            temperature = response.main.temp,
            feelsLike = response.main.feelsLike,
            tempMin = response.main.tempMin,
            tempMax = response.main.tempMax,
            humidityPercent = response.main.humidity,
            pressureHpa = response.main.pressure,
            windSpeed = response.wind.speed,
            windDeg = response.wind.deg,
            windGust = response.wind.gust,
            visibilityMeters = response.visibility,
            cloudPercent = response.clouds?.all ?: 0,
            units = units,
            sunriseEpoch = response.sys?.sunrise?.takeIf { it > 0L },
            sunsetEpoch = response.sys?.sunset?.takeIf { it > 0L },
            utcOffsetSeconds = response.timezone,
            observedAtEpochSeconds = response.dt?.takeIf { it > 0L },
        )
    }

    private fun <T> Result<T>.mapApiFailure(): Result<T> =
        fold(
            onSuccess = { Result.success(it) },
            onFailure = { throwable ->
                Result.failure(mapThrowable(throwable))
            },
        )

    private fun mapThrowable(throwable: Throwable): Throwable =
        when (throwable) {
            is IOException -> IOException("offline", throwable)
            is HttpException -> {
                val code = throwable.code()
                val body = throwable
                    .response()
                    ?.errorBody()
                    ?.string()
                    .orEmpty()
                val parsed =
                    runCatching { json.decodeFromString<OpenWeatherErrorResponse>(body) }.getOrNull()
                val message = if (parsed?.message?.isNotBlank() == true) {
                    when (code) {
                        401, 403 -> "bad_api_key"
                        404 -> "city_not_found"
                        429 -> "rate_limited"
                        in 500..599 -> "server_error"
                        else -> parsed.message
                    }
                } else {
                    "http_$code"
                }
                IllegalStateException(message, throwable)
            }

            else -> throwable
        }
}

/** A forecast and the air quality hours put on it, which the caller caches with their fetch time. */
data class ForecastWithAirQuality(
    val days: List<DayForecast>,
    // Null when there's none: air quality off or failed, or a forecast from OpenWeatherMap.
    val airQuality: AirQualityHours?,
)

/** Puts each hour's air quality on its slot. */
internal fun withAirQuality(
    days: List<DayForecast>,
    byHour: Map<Long, AirQuality>,
): List<DayForecast> {
    if (byHour.isEmpty()) return days
    return days.map { day ->
        day.copy(hourlySlots = day.hourlySlots.map { slot -> slot.copy(airQuality = slot.epochSeconds?.let { byHour[it] }) })
    }
}
