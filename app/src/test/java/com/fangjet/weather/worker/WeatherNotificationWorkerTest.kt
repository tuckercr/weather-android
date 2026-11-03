package com.fangjet.weather.worker

import android.content.Context
import androidx.work.ListenableWorker.Result
import androidx.work.WorkerParameters
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.domain.WeatherUnits
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The worker's wiring: which saved city, which units, and coordinates versus a city-name query.
 * The notification content, gating and walk planning are covered by [NotificationWorkerLogicTest];
 * posting needs the Android framework, so these runs keep the Remote Config switch off and stop
 * before it, and an empty forecast means there's no walk to plan.
 */
class WeatherNotificationWorkerTest {

    private val preferences = mockk<PreferencesRepository>()
    private val weather = mockk<WeatherRepository>()
    private val remoteConfig = mockk<RemoteConfigRepository>()
    private val scheduler = mockk<WorkManagerScheduler>(relaxed = true)
    private val currentWeather = mockk<CurrentWeather>(relaxed = true)

    private fun worker(
        locations: List<SavedLocation>,
        units: UnitOverride = UnitOverride.METRIC,
    ): WeatherNotificationWorker {
        every { preferences.savedLocations } returns flowOf(locations)
        every { preferences.unitOverride } returns flowOf(units)
        every { preferences.notifyMorningBriefing } returns flowOf(true)
        every { preferences.notifyWalkTime } returns flowOf(true)
        every { preferences.showWalkCard } returns flowOf(true)
        every { preferences.followMyLocation } returns flowOf(false)
        every { remoteConfig.dailyWeatherNotificationsEnabled() } returns false
        every { remoteConfig.petWeatherThresholds() } returns PetWeatherThresholds.DEFAULT
        coEvery { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) } returns
            kotlin.Result.success(currentWeather)
        coEvery { weather.fetchForecast(any(), any(), any(), any()) } returns kotlin.Result.success(emptyList())
        return WeatherNotificationWorker(
            mockk<Context>(relaxed = true),
            mockk<WorkerParameters>(relaxed = true),
            preferences,
            weather,
            remoteConfig,
            scheduler,
        )
    }

    @Test
    fun `a city with coordinates is fetched by latitude and longitude`() =
        runTest {
            val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)

            assertEquals(Result.success(), worker(listOf(london)).doWork())

            coVerify {
                weather.fetchCurrentWeather(WeatherUnits.METRIC, "London, GB", null, 51.5, -0.1)
                weather.fetchForecast(WeatherUnits.METRIC, null, 51.5, -0.1)
            }
        }

    @Test
    fun `a city without coordinates is fetched by its name`() =
        runTest {
            val springfield = SavedLocation(label = "Springfield", latitude = null, longitude = null)

            worker(listOf(springfield), units = UnitOverride.IMPERIAL).doWork()

            coVerify {
                weather.fetchCurrentWeather(WeatherUnits.IMPERIAL, "Springfield", "Springfield", null, null)
                weather.fetchForecast(WeatherUnits.IMPERIAL, "Springfield", null, null)
            }
        }

    @Test
    fun `my location goes with an empty label so the API names the place`() =
        runTest {
            val here = SavedLocation(label = "My Location", latitude = 37.4, longitude = -122.1, isCurrentLocation = true)

            worker(listOf(here)).doWork()

            coVerify { weather.fetchCurrentWeather(WeatherUnits.METRIC, "", null, 37.4, -122.1) }
        }

    @Test
    fun `only the notification city is fetched, My Location ahead of the first city`() =
        runTest {
            val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)
            val here = SavedLocation(label = "My Location", latitude = 37.4, longitude = -122.1, isCurrentLocation = true)

            worker(listOf(london, here)).doWork()

            coVerify(exactly = 1) { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) }
            coVerify { weather.fetchCurrentWeather(WeatherUnits.METRIC, "", null, 37.4, -122.1) }
        }

    @Test
    fun `with nothing to plan, no walk time is scheduled`() =
        runTest {
            val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)

            worker(listOf(london)).doWork()

            verify(exactly = 0) { scheduler.scheduleWalkTime(any()) }
        }

    @Test
    fun `a failed fetch asks WorkManager to retry`() =
        runTest {
            val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)
            val worker = worker(listOf(london))
            coEvery { weather.fetchCurrentWeather(any(), any(), any(), any(), any()) } returns
                kotlin.Result.failure(RuntimeException("offline"))

            assertEquals(Result.retry(), worker.doWork())
            // The retry stays a retry, not tomorrow's run.
            verify(exactly = 0) { scheduler.keepBriefingAtHalfPastEight() }
        }

    @Test
    fun `a finished run puts the next one back on 8-30`() =
        runTest {
            val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)

            assertEquals(Result.success(), worker(listOf(london)).doWork())

            verify { scheduler.keepBriefingAtHalfPastEight() }
        }
}
