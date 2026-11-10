package com.fangjet.weather.data

import android.content.Context
import com.fangjet.weather.domain.AirQualityHours
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.resolveWeatherUnits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.Serializable
import javax.inject.Inject

/**
 * The weather for the saved locations, cache first: what's cached for a location, and refreshes
 * that fetch it in the user's units and cache what comes back. The one way the screen, the
 * background refresh and the notifications get weather, so they always agree on the units and the
 * cache. Same as iOS's `LocationWeatherRepository`.
 */
class LocationWeatherRepository internal constructor(
    private val resolveUnits: suspend () -> WeatherUnits,
    // A saved location's weather, by the data layer's rule (WeatherRepository.fetchCurrentWeather(location)).
    private val fetchCurrentWeather: suspend (location: SavedLocation, units: WeatherUnits) -> Result<CurrentWeather>,
    // The forecast, reusing the cached air quality when it can (WeatherRepository.fetchForecast(location)).
    private val fetchForecast: suspend (
        location: SavedLocation,
        units: WeatherUnits,
        cachedAirQuality: AirQualityHours?,
    ) -> Result<ForecastWithAirQuality>,
    private val readCachedWeather: suspend (cacheKey: String) -> CurrentWeather?,
    private val readCachedForecast: suspend (cacheKey: String) -> List<DayForecast>?,
    private val writeCachedWeather: suspend (cacheKey: String, weather: CurrentWeather) -> Unit,
    private val writeCachedForecast: suspend (cacheKey: String, forecast: List<DayForecast>) -> Unit,
    private val setLastCity: suspend (cityName: String) -> Unit,
    private val readCachedAirQuality: suspend (cacheKey: String) -> AirQualityHours? = { null },
    private val writeCachedAirQuality: suspend (cacheKey: String, airQuality: AirQualityHours) -> Unit = { _, _ -> },
    // When the cached reading and forecast were fetched, for background runs to skip fresh ones.
    private val readFetchTimes: suspend (cacheKey: String) -> FetchTimes? = { null },
    private val writeWeatherFetchedAt: suspend (cacheKey: String, epochSeconds: Long) -> Unit = { _, _ -> },
    private val writeForecastFetchedAt: suspend (cacheKey: String, epochSeconds: Long) -> Unit = { _, _ -> },
    private val nowEpochSeconds: () -> Long = { System.currentTimeMillis() / 1000 },
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository,
        weatherRepository: WeatherRepository,
    ) : this(
        resolveUnits = { context.resolveWeatherUnits(preferencesRepository.unitOverride.first()) },
        fetchCurrentWeather = weatherRepository::fetchCurrentWeather,
        fetchForecast = { location, units, air -> weatherRepository.fetchForecast(location, units, air) },
        readCachedWeather = preferencesRepository::getCachedWeather,
        readCachedForecast = preferencesRepository::getCachedForecast,
        writeCachedWeather = preferencesRepository::setCachedWeather,
        writeCachedForecast = preferencesRepository::setCachedForecast,
        setLastCity = preferencesRepository::setLastCity,
        readCachedAirQuality = preferencesRepository::getCachedAirQuality,
        writeCachedAirQuality = preferencesRepository::setCachedAirQuality,
        readFetchTimes = preferencesRepository::getFetchTimes,
        writeWeatherFetchedAt = preferencesRepository::setWeatherFetchedAt,
        writeForecastFetchedAt = preferencesRepository::setForecastFetchedAt,
    )

    companion object {
        /**
         * Background runs skip a location whose reading and forecast were both fetched less than
         * this long ago: 30 minutes. Foreground refreshes always fetch.
         */
        const val BACKGROUND_FRESH_SECONDS = 30 * 60L
    }

    /** The user's units: Settings' choice, or the device's. */
    suspend fun units(): WeatherUnits = resolveUnits()

    suspend fun cachedWeather(location: SavedLocation): CurrentWeather? = readCachedWeather(location.cacheKey)

    suspend fun cachedForecast(location: SavedLocation): List<DayForecast>? = readCachedForecast(location.cacheKey)

    /**
     * Fetches [location]'s current reading and caches it. A fetch the screen has since moved on from
     * is still cached (it's that location's weather), unless the units changed while it was fetched.
     */
    suspend fun refreshWeather(location: SavedLocation): Result<CurrentWeather> {
        val units = units()
        // Not cached if the units changed while it was fetched: the cache stays in the new ones.
        return fetchCurrentWeather(location, units).onSuccess {
            if (units() == units) {
                writeCachedWeather(location.cacheKey, it)
                writeWeatherFetchedAt(location.cacheKey, nowEpochSeconds())
            }
        }
    }

    /**
     * Fetches [location]'s forecast and caches it, unless the units changed meanwhile. Its air
     * quality is reused from the cache for up to 3 hours ([AirQualityHours.reusable]), and cached
     * whatever the units, which it doesn't depend on.
     */
    suspend fun refreshForecast(location: SavedLocation): Result<List<DayForecast>> {
        val units = units()
        val cachedAirQuality = readCachedAirQuality(location.cacheKey)
        return fetchForecast(location, units, cachedAirQuality)
            .onSuccess { fetched ->
                fetched.airQuality?.takeIf { it != cachedAirQuality }?.let { writeCachedAirQuality(location.cacheKey, it) }
                if (units() == units) {
                    writeCachedForecast(location.cacheKey, fetched.days)
                    writeForecastFetchedAt(location.cacheKey, nowEpochSeconds())
                }
            }.map { it.days }
    }

    /**
     * For background runs: [location]'s cached reading and forecast when both were fetched under
     * [BACKGROUND_FRESH_SECONDS] ago in the user's units, to use as they are instead of fetching.
     * Null when either is older, has no fetch time (a cache from before they were kept) or is
     * missing: refresh. Same rule as iOS's `LocationWeatherRepository`.
     */
    suspend fun freshCache(location: SavedLocation): CachedLocationWeather? {
        if (readFetchTimes(location.cacheKey)?.bothFetchedWithin(BACKGROUND_FRESH_SECONDS, nowEpochSeconds()) != true) return null
        val weather = cachedWeather(location) ?: return null
        val forecast = cachedForecast(location) ?: return null
        val units = units()
        // Units switched since: the cache is in the old ones.
        if (weather.units != units || forecast.any { it.units != units }) return null
        return CachedLocationWeather(weather, forecast)
    }

    /** Keeps the city last shown, which the city list falls back to when it has none. */
    suspend fun rememberShownCity(cityName: String) = setLastCity(cityName)
}

/** A location's cached reading and forecast, fresh enough for a background run to use as they are. */
data class CachedLocationWeather(
    val weather: CurrentWeather,
    val forecast: List<DayForecast>,
)

/**
 * When a location's cached reading and forecast were fetched, in epoch seconds, each null until a
 * fetch is cached with its time. Persisted per location ([PreferencesRepository.getFetchTimes]).
 */
@Serializable
data class FetchTimes(
    val weatherEpochSeconds: Long? = null,
    val forecastEpochSeconds: Long? = null,
) {
    /** Both fetched under [seconds] before [nowEpochSeconds]; a missing time counts as old. */
    fun bothFetchedWithin(
        seconds: Long,
        nowEpochSeconds: Long,
    ): Boolean = listOf(weatherEpochSeconds, forecastEpochSeconds).all { it != null && nowEpochSeconds - it in 0 until seconds }
}
