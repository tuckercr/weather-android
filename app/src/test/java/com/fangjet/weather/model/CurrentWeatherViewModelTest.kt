package com.fangjet.weather.model

import com.fangjet.weather.data.GeocodingRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.testLocationWeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/** The weather screen's steps. Same cases as iOS's `CurrentWeatherViewModelTests`. */
@OptIn(ExperimentalCoroutinesApi::class)
class CurrentWeatherViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val austin = SavedLocation(label = "Austin, TX, US", latitude = 30.27, longitude = -97.74)
    private val myLocation = SavedLocation(label = "My Location", latitude = 40.0, longitude = -83.0, isCurrentLocation = true)

    /** About 110 km north of [myLocation]: far enough to move it. */
    private val movedHere = SavedLocation(label = "My Location", latitude = 41.0, longitude = -83.0, isCurrentLocation = true)

    private val fetched = mutableListOf<String>()
    private val position = FakePosition()

    // --- Refreshing ---

    @Test
    fun `the city on screen and retries are refetched`() =
        runTest {
            val viewModel = viewModel(listOf(austin))
            idle()

            viewModel.activeCityChanged()
            viewModel.retry()
            idle()

            assertEquals(listOf(austin.label, austin.label), fetched)
        }

    @Test
    fun `the half-hourly refresh runs while the screen is up`() =
        runTest {
            val viewModel = viewModel(listOf(austin))
            idle()
            val job = launch { viewModel.refreshWhileActive() }

            advanceTimeBy(CurrentWeatherViewModel.AUTO_REFRESH_INTERVAL * 2 + kotlin.time.Duration.parse("1s"))
            job.cancel()
            idle()

            assertEquals(2, fetched.size)
        }

    @Test
    fun `the stale recheck only runs while there's an error`() =
        runTest {
            val viewModel = viewModel(listOf(austin))
            idle()

            // No error: it returns at once rather than looping.
            viewModel.recheckWhileStale()
        }

    // --- My Location ---

    @Test
    fun `My Location follows the phone at most every 15 minutes`() =
        runTest {
            position.here = movedHere
            val (viewModel, cityList) = viewModelAndCities(listOf(myLocation, austin))
            idle()

            viewModel.becameActive()
            idle()
            viewModel.becameActive()
            idle()

            assertEquals(1, position.reads)
            assertEquals(movedHere, cityList.locations.value.first())
        }

    @Test
    fun `My Location is added when there are no cities yet`() =
        runTest {
            position.here = myLocation
            val (viewModel, cityList) = viewModelAndCities(emptyList())

            viewModel.becameActive()
            idle()

            assertEquals(listOf(myLocation.label), cityList.locations.value.map { it.label })
        }

    @Test
    fun `My Location removed on purpose isn't added back`() =
        runTest {
            position.here = myLocation
            val (viewModel, cityList) = viewModelAndCities(listOf(austin))

            viewModel.becameActive()
            idle()
            viewModel.becameActive()
            idle()

            assertEquals(0, position.reads)
            assertEquals(listOf(austin), cityList.locations.value)
        }

    @Test
    fun `My Location is added when access is granted later`() =
        runTest {
            position.here = myLocation
            position.granted = false
            val (viewModel, cityList) = viewModelAndCities(listOf(austin))

            viewModel.becameActive()
            idle()
            position.granted = true
            viewModel.becameActive()
            idle()

            assertTrue(cityList.locations.value.any { it.isCurrentLocation })
        }

    @Test
    fun `adding My Location without a position says so`() =
        runTest {
            position.here = null
            val (viewModel, _) = viewModelAndCities(emptyList())
            var failures = 0
            val job = launch { viewModel.addMyLocationFailed.collect { failures++ } }

            viewModel.becameActive()
            idle()
            job.cancel()

            assertEquals(1, failures)
        }

    @Test
    fun `pull to refresh on My Location reads the position whatever the interval`() =
        runTest {
            position.here = myLocation
            val (viewModel, cityList) = viewModelAndCities(listOf(myLocation))
            idle()
            viewModel.becameActive()
            idle()

            position.here = movedHere
            viewModel.pulledToRefresh()
            idle()

            assertEquals(2, position.reads)
            assertEquals(listOf(movedHere), cityList.locations.value)
        }

    // --- Helpers ---

    private fun kotlinx.coroutines.test.TestScope.idle() = mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

    private fun viewModel(locations: List<SavedLocation>) = viewModelAndCities(locations).first

    private fun viewModelAndCities(locations: List<SavedLocation>): Pair<CurrentWeatherViewModel, CityListViewModel> {
        val preferences = mockk<PreferencesRepository>(relaxed = true)
        every { preferences.savedLocations } returns flowOf(locations)
        every { preferences.activeLocationIndex } returns flowOf(0)
        every { preferences.lastCity } returns flowOf(null)
        val geocoding = mockk<GeocodingRepository>(relaxed = true)
        coEvery { geocoding.searchCities(any()) } returns Result.success(emptyList())
        val cityList = CityListViewModel(preferences, geocoding, onCityAdded = {})
        val weather = WeatherForecastViewModel(
            repository = testLocationWeatherRepository(
                fetchCurrentWeather = { units, location ->
                    fetched += location.label
                    Result.success(reading(units))
                },
                fetchForecast = { _, _ -> Result.success(emptyList()) },
            ),
        )
        return CurrentWeatherViewModel(cityList, weather, position) to cityList
    }

    private fun reading(units: WeatherUnits) =
        CurrentWeather(
            cityName = "Austin",
            conditionMain = "Clear",
            description = "clear sky",
            iconCode = "01d",
            temperature = 20.0,
            feelsLike = 20.0,
            tempMin = 15.0,
            tempMax = 25.0,
            humidityPercent = 40,
            pressureHpa = 1013,
            windSpeed = 3.0,
            windDeg = 90,
            visibilityMeters = 10000,
            cloudPercent = 10,
            units = units,
        )

    private class FakePosition : DevicePositioning {
        var granted = true
        var here: SavedLocation? = null
        var reads = 0

        override fun hasLocationPermission() = granted

        override suspend fun knownPosition(): SavedLocation? = read()

        override suspend fun currentPosition(): SavedLocation? = read()

        private fun read(): SavedLocation? {
            reads++
            return here.takeIf { granted }
        }
    }

    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
