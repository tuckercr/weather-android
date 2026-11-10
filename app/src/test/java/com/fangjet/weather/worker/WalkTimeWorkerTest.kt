package com.fangjet.weather.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.fangjet.weather.data.ForecastWithAirQuality
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.PetWeatherThresholds
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
 * The walk-time worker's wiring: whether it runs at all, which city, which units, and that what it
 * fetches is cached (through [LocationWeatherRepository]). The nudge's own rules are covered by
 * [NotificationWorkerLogicTest]; an empty forecast means there's no walk, so these runs stop before
 * posting, which needs the Android framework.
 */
class WalkTimeWorkerTest {
    private val preferences = mockk<PreferencesRepository>()
    private val weather = mockk<WeatherRepository>()
    private val remoteConfig = mockk<RemoteConfigRepository>()
    private val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)
    private val here = SavedLocation(label = "My Location", latitude = 37.4, longitude = -122.1, isCurrentLocation = true)

    private fun worker(
        locations: List<SavedLocation>,
        remotelyOn: Boolean = true,
        walkTimeOn: Boolean = true,
        hasDog: Boolean = true,
    ): WalkTimeWorker {
        every { preferences.savedLocations } returns flowOf(locations)
        every { preferences.unitOverride } returns flowOf(UnitOverride.METRIC)
        every { preferences.notifyWalkTime } returns flowOf(walkTimeOn)
        every { preferences.showWalkCard } returns flowOf(hasDog)
        coEvery { preferences.setCachedWeather(any(), any()) } just Runs
        coEvery { preferences.setCachedForecast(any(), any()) } just Runs
        every { remoteConfig.walkTimeNotificationsEnabled() } returns remotelyOn
        every { remoteConfig.petWeatherThresholds() } returns PetWeatherThresholds.DEFAULT
        coEvery { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) } returns
            kotlin.Result.success(mockk<CurrentWeather>(relaxed = true))
        coEvery { weather.fetchForecastWithAirQuality(any(), any(), any(), any(), any(), any()) } returns
            kotlin.Result.success(ForecastWithAirQuality(emptyList(), null))
        coEvery { preferences.getCachedAirQuality(any()) } returns null
        coEvery { preferences.getFetchTimes(any()) } returns null
        coEvery { preferences.setWeatherFetchedAt(any(), any()) } just Runs
        coEvery { preferences.setForecastFetchedAt(any(), any()) } just Runs
        val context = mockk<Context>(relaxed = true)
        return WalkTimeWorker(
            context,
            mockk<WorkerParameters>(relaxed = true),
            preferences,
            LocationWeatherRepository(context, preferences, weather),
            remoteConfig,
        )
    }

    @Test
    fun `it fetches the notification city, My Location first, in the user's units, and caches it`() =
        runTest {
            assertEquals(Result.success(), worker(listOf(london, here)).doWork())

            coVerify(exactly = 1) { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) }
            coVerify { weather.fetchCurrentWeather(WeatherUnits.METRIC, "", "My Location", 37.4, -122.1) }
            coVerify { preferences.setCachedWeather(here.cacheKey, any()) }
            coVerify { preferences.setCachedForecast(here.cacheKey, any()) }
        }

    @Test
    fun `turned off since it was planned, by the switch, the dog or Remote Config, it fetches nothing`() =
        runTest {
            for (w in listOf(
                worker(listOf(london), walkTimeOn = false),
                worker(listOf(london), hasDog = false),
                worker(listOf(london), remotelyOn = false),
            )) {
                assertEquals(Result.success(), w.doWork())
            }

            coVerify(exactly = 0) { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `a failed fetch asks WorkManager to retry`() =
        runTest {
            val worker = worker(listOf(london))
            coEvery { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) } returns
                kotlin.Result.failure(RuntimeException("offline"))

            assertEquals(Result.retry(), worker.doWork())
        }
}
