package com.fangjet.weather.data

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits

/** A [LocationWeatherRepository] over fake fetches and cache writes, for the view model and worker tests. */
internal fun testLocationWeatherRepository(
    units: WeatherUnits = WeatherUnits.IMPERIAL,
    fetchCurrentWeather: suspend (WeatherUnits, SavedLocation) -> Result<CurrentWeather>,
    fetchForecast: suspend (WeatherUnits, SavedLocation) -> Result<List<DayForecast>>,
    cacheCurrentWeather: suspend (String, CurrentWeather) -> Unit = { _, _ -> },
    cacheForecast: suspend (String, List<DayForecast>) -> Unit = { _, _ -> },
    getCachedCurrentWeather: suspend (String) -> CurrentWeather? = { null },
    getCachedForecast: suspend (String) -> List<DayForecast>? = { null },
    setLastCity: suspend (String) -> Unit = {},
    resolveUnits: suspend () -> WeatherUnits = { units },
) = LocationWeatherRepository(
    resolveUnits = resolveUnits,
    fetchCurrentWeather = { location, u -> fetchCurrentWeather(u, location) },
    fetchForecast = { location, u -> fetchForecast(u, location) },
    readCachedWeather = getCachedCurrentWeather,
    readCachedForecast = getCachedForecast,
    writeCachedWeather = cacheCurrentWeather,
    writeCachedForecast = cacheForecast,
    setLastCity = setLastCity,
)
