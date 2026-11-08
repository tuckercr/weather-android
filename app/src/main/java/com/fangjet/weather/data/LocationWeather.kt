package com.fangjet.weather.data

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits

/**
 * What a saved location is fetched with. [WeatherRepository] prefers the coordinates when there are
 * any and falls back on [cityQuery], the location's name. My Location has no label of its own, so
 * its [locationLabel] is empty: the phone's place lookup (or the provider) names it. The screen, the
 * background refresh and the notifications all fetch by this rule. Same as iOS's
 * `WeatherRepository.fetchCurrentWeather(for:units:)`.
 */
data class WeatherRequest(
    val locationLabel: String,
    val cityQuery: String,
    val latitude: Double?,
    val longitude: Double?,
)

val SavedLocation.weatherRequest: WeatherRequest
    get() = WeatherRequest(if (isCurrentLocation) "" else label, label, latitude, longitude)

/** [location]'s current reading, by [weatherRequest]. */
suspend fun WeatherRepository.fetchCurrentWeather(
    location: SavedLocation,
    units: WeatherUnits,
): Result<CurrentWeather> =
    location.weatherRequest.let { fetchCurrentWeather(units, it.locationLabel, it.cityQuery, it.latitude, it.longitude) }

/** [location]'s forecast, by [weatherRequest]. */
suspend fun WeatherRepository.fetchForecast(
    location: SavedLocation,
    units: WeatherUnits,
): Result<List<DayForecast>> = location.weatherRequest.let { fetchForecast(units, it.cityQuery, it.latitude, it.longitude) }
