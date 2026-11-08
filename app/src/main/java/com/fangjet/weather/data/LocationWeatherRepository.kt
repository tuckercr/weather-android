package com.fangjet.weather.data

import android.content.Context
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.resolveWeatherUnits
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
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
    private val fetchForecast: suspend (location: SavedLocation, units: WeatherUnits) -> Result<List<DayForecast>>,
    private val readCachedWeather: suspend (cacheKey: String) -> CurrentWeather?,
    private val readCachedForecast: suspend (cacheKey: String) -> List<DayForecast>?,
    private val writeCachedWeather: suspend (cacheKey: String, weather: CurrentWeather) -> Unit,
    private val writeCachedForecast: suspend (cacheKey: String, forecast: List<DayForecast>) -> Unit,
    private val setLastCity: suspend (cityName: String) -> Unit,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        preferencesRepository: PreferencesRepository,
        weatherRepository: WeatherRepository,
    ) : this(
        resolveUnits = { context.resolveWeatherUnits(preferencesRepository.unitOverride.first()) },
        fetchCurrentWeather = weatherRepository::fetchCurrentWeather,
        fetchForecast = weatherRepository::fetchForecast,
        readCachedWeather = preferencesRepository::getCachedWeather,
        readCachedForecast = preferencesRepository::getCachedForecast,
        writeCachedWeather = preferencesRepository::setCachedWeather,
        writeCachedForecast = preferencesRepository::setCachedForecast,
        setLastCity = preferencesRepository::setLastCity,
    )

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
        return fetchCurrentWeather(location, units).onSuccess { if (units() == units) writeCachedWeather(location.cacheKey, it) }
    }

    /** Fetches [location]'s forecast and caches it, unless the units changed meanwhile. */
    suspend fun refreshForecast(location: SavedLocation): Result<List<DayForecast>> {
        val units = units()
        return fetchForecast(location, units).onSuccess { if (units() == units) writeCachedForecast(location.cacheKey, it) }
    }

    /** Keeps the city last shown, which the city list falls back to when it has none. */
    suspend fun rememberShownCity(cityName: String) = setLastCity(cityName)
}
