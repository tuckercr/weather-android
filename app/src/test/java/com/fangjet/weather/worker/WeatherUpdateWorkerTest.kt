package com.fangjet.weather.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.fangjet.weather.data.LocationRepository
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.domain.WeatherUnits
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The background update's wiring: the notification city and the tab last viewed, in the user's
 * units, cached through [LocationWeatherRepository]. Which cities and the result are covered by
 * [UpdateWorkerLogicTest] and the rain alert by [NotificationWorkerLogicTest]; Remote Config keeps
 * rain alerts off here, as posting needs the Android framework.
 */
class WeatherUpdateWorkerTest {
    private val preferences = mockk<PreferencesRepository>()
    private val weather = mockk<WeatherRepository>()
    private val remoteConfig = mockk<RemoteConfigRepository>()
    private val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)
    private val springfield = SavedLocation(label = "Springfield", latitude = null, longitude = null)

    private fun worker(
        locations: List<SavedLocation>,
        activeIndex: Int = 0,
    ): WeatherUpdateWorker {
        every { preferences.savedLocations } returns flowOf(locations)
        every { preferences.activeLocationIndex } returns flowOf(activeIndex)
        every { preferences.unitOverride } returns flowOf(UnitOverride.IMPERIAL)
        every { preferences.followMyLocation } returns flowOf(false)
        every { preferences.notifyRain } returns flowOf(true)
        coEvery { preferences.setCachedWeather(any(), any()) } just Runs
        coEvery { preferences.setCachedForecast(any(), any()) } just Runs
        every { remoteConfig.rainNotificationsEnabled() } returns false
        coEvery { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) } returns
            kotlin.Result.success(mockk<CurrentWeather>(relaxed = true))
        coEvery { weather.fetchForecast(any(), any(), any(), any()) } returns kotlin.Result.success(emptyList())
        val context = mockk<Context>(relaxed = true)
        return WeatherUpdateWorker(
            context,
            mockk<WorkerParameters>(relaxed = true),
            preferences,
            LocationWeatherRepository(context, preferences, weather),
            remoteConfig,
            mockk<LocationRepository>(relaxed = true),
        )
    }

    @Test
    fun `it refreshes and caches the notification city and the tab last viewed`() =
        runTest {
            assertEquals(Result.success(), worker(listOf(london, springfield), activeIndex = 1).doWork())

            coVerify { weather.fetchCurrentWeather(WeatherUnits.IMPERIAL, "London, GB", "London, GB", 51.5, -0.1) }
            coVerify { weather.fetchCurrentWeather(WeatherUnits.IMPERIAL, "Springfield", "Springfield", null, null) }
            coVerify { preferences.setCachedWeather(london.cacheKey, any()) }
            coVerify { preferences.setCachedWeather(springfield.cacheKey, any()) }
            coVerify { preferences.setCachedForecast(london.cacheKey, any()) }
            coVerify { preferences.setCachedForecast(springfield.cacheKey, any()) }
        }

    @Test
    fun `when the notification city can't be fetched, WorkManager retries`() =
        runTest {
            val worker = worker(listOf(london))
            coEvery { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) } returns
                kotlin.Result.failure(RuntimeException("offline"))
            coEvery { weather.fetchForecast(any(), any(), any(), any()) } returns
                kotlin.Result.failure(RuntimeException("offline"))

            assertEquals(Result.retry(), worker.doWork())
            coVerify(exactly = 0) { preferences.setCachedWeather(any(), any()) }
        }
}
