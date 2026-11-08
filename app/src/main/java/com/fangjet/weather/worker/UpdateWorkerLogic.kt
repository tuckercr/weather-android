package com.fangjet.weather.worker

import androidx.work.ListenableWorker.Result
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.domain.notificationLocation
import kotlinx.coroutines.CancellationException
import timber.log.Timber

/**
 * Pure logic extracted from [WeatherUpdateWorker] so it can be unit-tested without Android
 * framework dependencies (Context, DataStore, WorkManager runtime, etc.).
 *
 * The units used for both fetches come from [getUnits], which the worker wires to honor the user's
 * unit override, so the background refresh never writes a different unit system into the cache than
 * a foreground refresh would.
 *
 * It refreshes the notification city (whose forecast the rain alert reads) and, when it's another
 * one, the city tab last viewed, which the app opens to, so that doesn't open on an old cache either.
 * The run's result goes by the notification city alone. Same as iOS's `WeatherBackgroundRefresher`.
 */
internal class UpdateWorkerLogic(
    private val getSavedLocations: suspend () -> List<SavedLocation>,
    // The city tab last viewed. Null refreshes only the notification city.
    private val getActiveIndex: suspend () -> Int? = { null },
    private val getUnits: suspend () -> WeatherUnits,
    // A saved location's weather, by the data layer's rule (WeatherRepository.fetchCurrentWeather(location)).
    private val fetchCurrentWeather: suspend (units: WeatherUnits, location: SavedLocation) -> kotlin.Result<CurrentWeather>,
    private val fetchForecast: suspend (units: WeatherUnits, location: SavedLocation) -> kotlin.Result<List<DayForecast>>,
    private val cacheCurrentWeather: suspend (cacheKey: String, weather: CurrentWeather) -> Unit,
    private val cacheForecast: suspend (cacheKey: String, forecast: List<DayForecast>) -> Unit,
    // After a refresh, with whatever came back: the rain alert checks it.
    private val afterRefresh: suspend (
        location: SavedLocation,
        weather: CurrentWeather?,
        forecast: List<DayForecast>?,
    ) -> Unit = { _, _, _ -> },
) {
    suspend fun doWork(): Result {
        val locations = getSavedLocations()
        val notificationCity = locations.notificationLocation() ?: return Result.success()
        val lastViewed = getActiveIndex()
            ?.let(locations::getOrNull)
            ?.takeIf { it.cacheKey != notificationCity.cacheKey }

        val units = getUnits()

        val notificationOk = refresh(notificationCity, units, alert = true)
        if (lastViewed != null) refresh(lastViewed, units, alert = false)
        return if (notificationOk) Result.success() else Result.retry()
    }

    /** Fetches and caches [location]; true when either fetch worked. [alert] runs [afterRefresh] too. */
    private suspend fun refresh(
        location: SavedLocation,
        units: WeatherUnits,
        alert: Boolean,
    ): Boolean {
        val currentResult = fetchCurrentWeather(units, location)
        val forecastResult = fetchForecast(units, location)

        val cacheKey = location.cacheKey

        val weatherOk = currentResult
            .onSuccess { weather -> cacheCurrentWeather(cacheKey, weather) }
            .isSuccess

        val forecastOk = forecastResult
            .onSuccess { forecast -> cacheForecast(cacheKey, forecast) }
            .isSuccess

        if (alert) {
            // The refresh has done its job by now: a failed alert mustn't turn it into a failed run.
            try {
                afterRefresh(location, currentResult.getOrNull(), forecastResult.getOrNull())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "after-refresh step failed")
            }
        }
        return weatherOk || forecastOk
    }
}
