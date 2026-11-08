package com.fangjet.weather.model

import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.weatherRequest
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class WeatherForecastViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `current weather for a city with coordinates asks by its location`() =
        runTest {
            var request: CurrentRequest? = null
            val savedCities = mutableListOf<String>()
            val viewModel = viewModel(
                resolveWeatherUnits = { WeatherUnits.IMPERIAL },
                fetchCurrentWeather = { units, location ->
                    request =
                        location.weatherRequest.let { CurrentRequest(units, it.locationLabel, it.cityQuery, it.latitude, it.longitude) }
                    Result.success(currentWeather(cityName = "Austin, TX, US", units = units))
                },
                setLastCity = { savedCities += it },
            )

            viewModel.refreshCurrent(
                SavedLocation(label = "Austin, TX, US", latitude = 30.2672, longitude = -97.7431),
            )
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                CurrentRequest(
                    units = WeatherUnits.IMPERIAL,
                    locationLabel = "Austin, TX, US",
                    cityQuery = "Austin, TX, US",
                    latitude = 30.2672,
                    longitude = -97.7431,
                ),
                request,
            )
            assertEquals(listOf("Austin, TX, US"), savedCities)
            assertEquals(
                LoadingState.Success(currentWeather(cityName = "Austin, TX, US", units = WeatherUnits.IMPERIAL)),
                viewModel.currentWeather.value,
            )
        }

    @Test
    fun `current weather without coordinates uses label as city query`() =
        runTest {
            var request: CurrentRequest? = null
            val viewModel = viewModel(
                fetchCurrentWeather = { units, location ->
                    request =
                        location.weatherRequest.let { CurrentRequest(units, it.locationLabel, it.cityQuery, it.latitude, it.longitude) }
                    Result.success(currentWeather(cityName = "Denver"))
                },
            )

            viewModel.refreshCurrent(
                SavedLocation(label = "Denver", latitude = null, longitude = null),
            )
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                CurrentRequest(
                    units = WeatherUnits.METRIC,
                    locationLabel = "Denver",
                    cityQuery = "Denver",
                    latitude = null,
                    longitude = null,
                ),
                request,
            )
        }

    @Test
    fun `latest current weather request wins when an earlier request finishes last`() =
        runTest {
            val firstRequest = CompletableDeferred<Result<CurrentWeather>>()
            val secondRequest = CompletableDeferred<Result<CurrentWeather>>()
            val requestedLabels = mutableListOf<String>()
            val savedCities = mutableListOf<String>()
            val viewModel = viewModel(
                fetchCurrentWeather = { units, location ->
                    requestedLabels += location.label
                    when (location.label) {
                        "Austin" -> firstRequest.await()
                        "Denver" -> secondRequest.await()
                        else -> error("Unexpected location ${location.label}")
                    }.map { it.copy(units = units) }
                },
                setLastCity = { savedCities += it },
            )

            viewModel.refreshCurrent(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            viewModel.refreshCurrent(SavedLocation(label = "Denver", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            secondRequest.complete(Result.success(currentWeather(cityName = "Denver")))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            firstRequest.complete(Result.success(currentWeather(cityName = "Austin")))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf("Austin", "Denver"), requestedLabels)
            assertEquals(LoadingState.Success(currentWeather(cityName = "Denver")), viewModel.currentWeather.value)
            assertEquals(listOf("Denver"), savedCities)
        }

    @Test
    fun `current weather network failure maps to retryable user message and can be cleared`() =
        runTest {
            val viewModel = viewModel(
                fetchCurrentWeather = { _, _ -> Result.failure(IOException("offline")) },
            )

            viewModel.refreshCurrent(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                LoadingState.Error("offline", canRetry = true),
                viewModel.currentWeather.value,
            )

            viewModel.clearCurrentError()
            assertEquals(LoadingState.Idle, viewModel.currentWeather.value)
        }

    @Test
    fun `forecast with coordinates passes lat-lon and null city query`() =
        runTest {
            var request: ForecastRequest? = null
            val expectedForecast = listOf(dayForecast(units = WeatherUnits.IMPERIAL))
            val viewModel = viewModel(
                resolveWeatherUnits = { WeatherUnits.IMPERIAL },
                fetchForecast = { units, location ->
                    request = location.weatherRequest.let { ForecastRequest(units, it.cityQuery, it.latitude, it.longitude) }
                    Result.success(expectedForecast)
                },
            )

            viewModel.refreshForecast(
                SavedLocation(label = "Austin, TX, US", latitude = 30.2672, longitude = -97.7431),
            )
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                ForecastRequest(
                    units = WeatherUnits.IMPERIAL,
                    cityQuery = "Austin, TX, US",
                    latitude = 30.2672,
                    longitude = -97.7431,
                ),
                request,
            )
            assertEquals(LoadingState.Success(expectedForecast), viewModel.forecast.value)
        }

    @Test
    fun `forecast maps network failure to retryable user message`() =
        runTest {
            val viewModel = viewModel(
                fetchForecast = { _, _ -> Result.failure(IOException("offline")) },
            )

            viewModel.refreshForecast(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                LoadingState.Error("offline", canRetry = true),
                viewModel.forecast.value,
            )

            viewModel.clearForecastError()
            assertEquals(LoadingState.Idle, viewModel.forecast.value)
        }

    @Test
    fun `background refresh current updates state on success without showing loading`() =
        runTest {
            val location = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)
            val fresh = currentWeather(cityName = "Austin")
            val viewModel = viewModel(
                fetchCurrentWeather = { _, _ -> Result.success(fresh) },
            )

            viewModel.backgroundRefreshCurrent(location)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Success(fresh), viewModel.currentWeather.value)
        }

    @Test
    fun `background refresh current silently ignores network failures`() =
        runTest {
            val location = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)
            val viewModel = viewModel(
                fetchCurrentWeather = { _, _ -> Result.failure(Exception("offline")) },
            )

            viewModel.backgroundRefreshCurrent(location)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Idle, viewModel.currentWeather.value)
        }

    @Test
    fun `background refresh forecast updates state on success without showing loading`() =
        runTest {
            val location = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)
            val fresh = listOf(dayForecast())
            val viewModel = viewModel(
                fetchForecast = { _, _ -> Result.success(fresh) },
            )

            viewModel.backgroundRefreshForecast(location)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Success(fresh), viewModel.forecast.value)
        }

    @Test
    fun `background refresh forecast silently ignores network failures`() =
        runTest {
            val location = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)
            val viewModel = viewModel(
                fetchForecast = { _, _ -> Result.failure(Exception("offline")) },
            )

            viewModel.backgroundRefreshForecast(location)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Idle, viewModel.forecast.value)
        }

    @Test
    fun `cached current weather is shown immediately while fetch is in flight`() =
        runTest {
            val cached = currentWeather(cityName = "Cached Austin")
            val fetchGate = CompletableDeferred<Result<CurrentWeather>>()
            val viewModel = viewModel(
                getCachedCurrentWeather = { cached },
                fetchCurrentWeather = { _, _ -> fetchGate.await() },
            )

            viewModel.refreshCurrent(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(LoadingState.Success(cached), viewModel.currentWeather.value)

            fetchGate.complete(Result.success(currentWeather(cityName = "Fresh Austin")))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoadingState.Success(currentWeather(cityName = "Fresh Austin")), viewModel.currentWeather.value)
        }

    @Test
    fun `network error is suppressed when cached current weather is available`() =
        runTest {
            val cached = currentWeather(cityName = "Austin")
            val viewModel = viewModel(
                getCachedCurrentWeather = { cached },
                fetchCurrentWeather = { _, _ -> Result.failure(Exception("offline")) },
            )

            viewModel.refreshCurrent(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Success(cached), viewModel.currentWeather.value)
        }

    @Test
    fun `failed refresh over cached weather flags it as stale until a refresh succeeds`() =
        runTest {
            val cached = currentWeather(cityName = "Austin")
            var result: Result<CurrentWeather> = Result.failure(Exception("offline"))
            val viewModel = viewModel(
                getCachedCurrentWeather = { cached },
                fetchCurrentWeather = { _, _ -> result },
            )
            val austin = SavedLocation(label = "Austin", latitude = null, longitude = null)

            viewModel.refreshCurrent(austin)
            settle()
            assertEquals("offline", viewModel.staleErrorKey.value)

            result = Result.success(currentWeather(cityName = "Austin"))
            viewModel.refreshCurrent(austin)
            settle()
            assertEquals(null, viewModel.staleErrorKey.value)
        }

    @Test
    fun `failed refresh with nothing cached shows the error, not the stale banner`() =
        runTest {
            val viewModel = viewModel(
                fetchCurrentWeather = { _, _ -> Result.failure(Exception("offline")) },
            )

            viewModel.refreshCurrent(SavedLocation(label = "Austin", latitude = null, longitude = null))
            settle()

            assertEquals(LoadingState.Error("offline", canRetry = true), viewModel.currentWeather.value)
            assertEquals(null, viewModel.staleErrorKey.value)
        }

    @Test
    fun `failed background refresh flags shown weather as stale, and switching city clears it`() =
        runTest {
            var result: Result<CurrentWeather> = Result.success(currentWeather(cityName = "Austin"))
            val viewModel = viewModel(fetchCurrentWeather = { _, _ -> result })
            val austin = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)

            viewModel.refreshCurrent(austin)
            settle()
            result = Result.failure(Exception("server_error"))
            viewModel.backgroundRefreshCurrent(austin)
            settle()
            assertEquals("server_error", viewModel.staleErrorKey.value)

            result = Result.failure(Exception("offline"))
            viewModel.refreshCurrent(SavedLocation(label = "Tokyo", latitude = 35.68, longitude = 139.65))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(null, viewModel.staleErrorKey.value)
        }

    @Test
    fun `network error is suppressed when cached forecast is available`() =
        runTest {
            val cached = listOf(dayForecast())
            val viewModel = viewModel(
                getCachedForecast = { cached },
                fetchForecast = { _, _ -> Result.failure(Exception("offline")) },
            )

            viewModel.refreshForecast(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Success(cached), viewModel.forecast.value)
        }

    @Test
    fun `concurrent same-location background refresh does not block foreground result`() =
        runTest {
            // Reproduces the indefinite-spinner bug: on screen entry both a foreground refresh
            // (location change) and a silent background refresh (ON_RESUME) fire for the SAME
            // location. The background refresh must not prevent the foreground refresh from
            // resolving the visible state, even when the background fetch fails.
            val foreground = CompletableDeferred<Result<CurrentWeather>>()
            val background = CompletableDeferred<Result<CurrentWeather>>()
            var call = 0
            val viewModel = viewModel(
                fetchCurrentWeather = { units, _ ->
                    val n = call++
                    (if (n == 0) foreground else background).await().map { it.copy(units = units) }
                },
            )
            val location = SavedLocation(label = "Austin", latitude = null, longitude = null)

            viewModel.refreshCurrent(location)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            viewModel.backgroundRefreshCurrent(location)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // The silent background fetch fails and is dropped.
            background.complete(Result.failure(IOException("offline")))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // The foreground fetch succeeds and must still be shown (spinner cleared).
            foreground.complete(Result.success(currentWeather(cityName = "Austin")))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LoadingState.Success(currentWeather(cityName = "Austin")), viewModel.currentWeather.value)
        }

    @Test
    fun `stale background current refresh does not overwrite newer foreground result`() =
        runTest {
            val backgroundRequest = CompletableDeferred<Result<CurrentWeather>>()
            val foregroundRequest = CompletableDeferred<Result<CurrentWeather>>()
            val viewModel = viewModel(
                fetchCurrentWeather = { units, location ->
                    when (location.label) {
                        "Austin" -> backgroundRequest.await()
                        "Denver" -> foregroundRequest.await()
                        else -> error("Unexpected location ${location.label}")
                    }.map { it.copy(units = units) }
                },
            )

            // A background refresh for Austin is in flight when the user switches to Denver.
            viewModel.backgroundRefreshCurrent(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            viewModel.refreshCurrent(SavedLocation(label = "Denver", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // Denver (the newer request) resolves first and is shown.
            foregroundRequest.complete(Result.success(currentWeather(cityName = "Denver")))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(LoadingState.Success(currentWeather(cityName = "Denver")), viewModel.currentWeather.value)

            // The stale Austin background fetch finishes last and must be dropped.
            backgroundRequest.complete(Result.success(currentWeather(cityName = "Austin")))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoadingState.Success(currentWeather(cityName = "Denver")), viewModel.currentWeather.value)
        }

    @Test
    fun `stale background forecast refresh does not overwrite newer foreground result`() =
        runTest {
            val austinForecast = listOf(dayForecast().copy(dateLabel = "Austin day"))
            val denverForecast = listOf(dayForecast().copy(dateLabel = "Denver day"))
            val backgroundRequest = CompletableDeferred<Result<List<DayForecast>>>()
            val foregroundRequest = CompletableDeferred<Result<List<DayForecast>>>()
            val viewModel = viewModel(
                fetchForecast = { _, location ->
                    when (location.label) {
                        "Austin" -> backgroundRequest.await()
                        "Denver" -> foregroundRequest.await()
                        else -> error("Unexpected city ${location.label}")
                    }
                },
            )

            viewModel.backgroundRefreshForecast(SavedLocation(label = "Austin", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            viewModel.refreshForecast(SavedLocation(label = "Denver", latitude = null, longitude = null))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            foregroundRequest.complete(Result.success(denverForecast))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(LoadingState.Success(denverForecast), viewModel.forecast.value)

            backgroundRequest.complete(Result.success(austinForecast))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(LoadingState.Success(denverForecast), viewModel.forecast.value)
        }

    @Test
    fun `current weather label is empty string for current location`() =
        runTest {
            var capturedLabel: String? = null
            val viewModel = viewModel(
                fetchCurrentWeather = { _, location ->
                    capturedLabel = location.weatherRequest.locationLabel
                    Result.success(currentWeather(cityName = "Detected City"))
                },
            )

            viewModel.refreshCurrent(
                SavedLocation(label = "My Location", latitude = 37.77, longitude = -122.42, isCurrentLocation = true),
            )
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals("", capturedLabel)
        }

    private fun viewModel(
        resolveWeatherUnits: suspend () -> WeatherUnits = { WeatherUnits.METRIC },
        fetchCurrentWeather: suspend (units: WeatherUnits, location: SavedLocation) -> Result<CurrentWeather> = { units, location ->
            Result.success(currentWeather(cityName = location.label, units = units))
        },
        fetchForecast: suspend (units: WeatherUnits, location: SavedLocation) -> Result<List<DayForecast>> = { units, _ ->
            Result.success(listOf(dayForecast(units = units)))
        },
        setLastCity: suspend (String) -> Unit = {},
        getCachedCurrentWeather: suspend (String) -> CurrentWeather? = { null },
        getCachedForecast: suspend (String) -> List<DayForecast>? = { null },
        setCachedCurrentWeather: suspend (String, CurrentWeather) -> Unit = { _, _ -> },
        setCachedForecast: suspend (String, List<DayForecast>) -> Unit = { _, _ -> },
        isWalkCardEnabled: () -> Boolean = { true },
        isHourlyForecastEnabled: () -> Boolean = { true },
        isNextRainEnabled: () -> Boolean = { true },
        isWeekForecastEnabled: () -> Boolean = { true },
        readRainOutlookSettings: () -> RainOutlookSettings = { RainOutlookSettings() },
        isTemperatureUnitsShown: () -> Boolean = { true },
        readPetWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
        foregroundRefreshMinMinutes: () -> Long = { 0L },
        nowMillis: () -> Long = { 0L },
        clock: () -> Long = { 1_000L },
    ) = WeatherForecastViewModel(
        repository = LocationWeatherRepository(
            resolveUnits = resolveWeatherUnits,
            fetchCurrentWeather = { location, units -> fetchCurrentWeather(units, location) },
            fetchForecast = { location, units -> fetchForecast(units, location) },
            readCachedWeather = getCachedCurrentWeather,
            readCachedForecast = getCachedForecast,
            writeCachedWeather = setCachedCurrentWeather,
            writeCachedForecast = setCachedForecast,
            setLastCity = setLastCity,
        ),
        isWalkCardEnabled = isWalkCardEnabled,
        isHourlyForecastEnabled = isHourlyForecastEnabled,
        isNextRainEnabled = isNextRainEnabled,
        isWeekForecastEnabled = isWeekForecastEnabled,
        readRainOutlookSettings = readRainOutlookSettings,
        isTemperatureUnitsShown = isTemperatureUnitsShown,
        readPetWeatherThresholds = readPetWeatherThresholds,
        foregroundRefreshMinMinutes = foregroundRefreshMinMinutes,
        nowMillis = nowMillis,
        clock = clock,
    )

    @Test
    fun `the clock only moves on with tick`() {
        var clock = 1_000L
        val vm = viewModel(clock = { clock })
        clock = 4_600L
        assertEquals(1_000L, vm.now.value)
        vm.tick()
        assertEquals(4_600L, vm.now.value)
    }

    @Test
    fun `the screen loads then shows the reading at the clock`() =
        runTest {
            val vm = viewModel()
            assertEquals(WeatherScreen.Loading, vm.screen.value)

            vm.refreshCurrent(SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74))
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            val state = (vm.screen.value as WeatherScreen.Weather).state
            assertEquals(1_000L, state.nowEpochSeconds)
            assertEquals("Austin", state.weather.cityName)
            assertNull(state.stale)
        }

    @Test
    fun `walk advice comes from the loaded forecast and is null until then`() =
        runTest {
            val hour = HourlySlot(
                timeLabel = "3 PM",
                iconCode = "01d",
                description = "clear",
                temperature = 18.0,
                feelsLike = 18.0,
                windSpeed = 1.0,
                windDeg = 0,
                humidity = 50,
                pressure = 1013,
                units = WeatherUnits.METRIC,
                localHour = 15,
                epochSeconds = 1_000L,
            )
            val vm = viewModel(fetchForecast = { units, _ ->
                Result.success(listOf(dayForecast(units).copy(hourlySlots = listOf(hour))))
            })
            val austin = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)
            vm.refreshCurrent(austin)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            val before = (vm.screen.value as WeatherScreen.Weather).state
            assertNull(before.walkAdvice)
            assertFalse(before.showsWalkCard)

            vm.refreshForecast(austin)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull((vm.screen.value as WeatherScreen.Weather).state.walkAdvice)
        }

    @Test
    fun `pet weather thresholds come from Remote Config`() {
        val custom = PetWeatherThresholds.validated(petHotC = 25.0)

        assertEquals(PetWeatherThresholds.DEFAULT, viewModel().screenConfig.thresholds)
        assertEquals(custom, viewModel(readPetWeatherThresholds = { custom }).screenConfig.thresholds)
    }

    // --- foreground_refresh_min_minutes ---

    private val austin = SavedLocation(label = "Austin", latitude = 30.27, longitude = -97.74)

    /** A view model whose fetches are counted, with a 10-minute throttle and a settable clock. */
    private class Throttled {
        var now = 0L
        var currentFetches = 0
        var forecastFetches = 0
    }

    private fun throttledViewModel(
        state: Throttled,
        minMinutes: Long = 10,
    ) = viewModel(
        fetchCurrentWeather = { _, _ ->
            state.currentFetches++
            Result.success(currentWeather(cityName = "Austin"))
        },
        fetchForecast = { units, _ ->
            state.forecastFetches++
            Result.success(listOf(dayForecast(units = units)))
        },
        foregroundRefreshMinMinutes = { minMinutes },
        nowMillis = { state.now },
    )

    private fun settle() = mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `foreground refresh is skipped within the window after a fetch`() =
        runTest {
            val state = Throttled()
            val viewModel = throttledViewModel(state)
            viewModel.backgroundRefreshCurrent(austin)
            viewModel.backgroundRefreshForecast(austin)
            settle()

            state.now += 9 * MINUTE
            viewModel.backgroundRefreshCurrent(austin)
            viewModel.backgroundRefreshForecast(austin)
            settle()

            assertEquals(1, state.currentFetches)
            assertEquals(1, state.forecastFetches)
        }

    @Test
    fun `foreground refresh runs again once the window has passed`() =
        runTest {
            val state = Throttled()
            val viewModel = throttledViewModel(state)
            viewModel.backgroundRefreshCurrent(austin)
            viewModel.backgroundRefreshForecast(austin)
            settle()

            state.now += 10 * MINUTE
            viewModel.backgroundRefreshCurrent(austin)
            viewModel.backgroundRefreshForecast(austin)
            settle()

            assertEquals(2, state.currentFetches)
            assertEquals(2, state.forecastFetches)
        }

    @Test
    fun `a zero window always refreshes`() =
        runTest {
            val state = Throttled()
            val viewModel = throttledViewModel(state, minMinutes = 0)

            repeat(3) {
                viewModel.backgroundRefreshCurrent(austin)
                viewModel.backgroundRefreshForecast(austin)
                settle()
            }

            assertEquals(3, state.currentFetches)
            assertEquals(3, state.forecastFetches)
        }

    @Test
    fun `explicit refresh is never throttled but counts as the last fetch`() =
        runTest {
            val state = Throttled()
            val viewModel = throttledViewModel(state)
            viewModel.refreshCurrent(austin)
            viewModel.refreshForecast(austin)
            settle()

            // Explicit refreshes inside the window still fetch…
            state.now += 1 * MINUTE
            viewModel.refreshCurrent(austin)
            viewModel.refreshForecast(austin)
            settle()
            assertEquals(2, state.currentFetches)
            assertEquals(2, state.forecastFetches)

            // …and their success throttles the next silent foreground refresh.
            state.now += 1 * MINUTE
            viewModel.backgroundRefreshCurrent(austin)
            viewModel.backgroundRefreshForecast(austin)
            settle()
            assertEquals(2, state.currentFetches)
            assertEquals(2, state.forecastFetches)
        }

    @Test
    fun `current weather and forecast are throttled separately and per city`() =
        runTest {
            val state = Throttled()
            val viewModel = throttledViewModel(state)
            val denver = SavedLocation(label = "Denver", latitude = 39.74, longitude = -104.99)
            viewModel.backgroundRefreshCurrent(austin)
            settle()

            viewModel.backgroundRefreshForecast(austin) // forecast never fetched: runs
            viewModel.backgroundRefreshCurrent(denver) // other city: runs
            viewModel.backgroundRefreshCurrent(austin) // just fetched: skipped
            settle()

            assertEquals(2, state.currentFetches)
            assertEquals(1, state.forecastFetches)
        }

    @Test
    fun `a failed fetch does not start the window`() =
        runTest {
            var currentFetches = 0
            val viewModel = viewModel(
                fetchCurrentWeather = { _, _ ->
                    currentFetches++
                    Result.failure(IOException("offline"))
                },
                foregroundRefreshMinMinutes = { 10 },
            )

            viewModel.backgroundRefreshCurrent(austin)
            settle()
            viewModel.backgroundRefreshCurrent(austin)
            settle()

            assertEquals(2, currentFetches)
        }

    @Test
    fun `walk card is shown by default and hidden when Remote Config turns it off`() {
        assertEquals(true, viewModel().screenConfig.walkCardEnabled)
        assertEquals(false, viewModel(isWalkCardEnabled = { false }).screenConfig.walkCardEnabled)
    }

    @Test
    fun `hourly strip, next-rain line and week list are shown by default and each can be turned off`() {
        val defaults = viewModel()
        assertEquals(true, defaults.screenConfig.hourlyForecastEnabled)
        assertEquals(true, defaults.screenConfig.nextRainEnabled)
        assertEquals(true, defaults.screenConfig.weekForecastEnabled)

        assertEquals(false, viewModel(isHourlyForecastEnabled = { false }).screenConfig.hourlyForecastEnabled)
        assertEquals(false, viewModel(isNextRainEnabled = { false }).screenConfig.nextRainEnabled)
        assertEquals(false, viewModel(isWeekForecastEnabled = { false }).screenConfig.weekForecastEnabled)
    }

    @Test
    fun `rain outlook settings come from Remote Config`() {
        assertEquals(RainOutlookSettings(), viewModel().screenConfig.rainOutlookSettings)
        val remote = RainOutlookSettings(enabled = false, hours = 6, minChance = 40)
        assertEquals(remote, viewModel(readRainOutlookSettings = { remote }).screenConfig.rainOutlookSettings)
    }

    @Test
    fun `temperatures show their unit by default and drop it when Remote Config turns it off`() {
        assertEquals(true, viewModel().temperatureUnitsShown)
        assertEquals(false, viewModel(isTemperatureUnitsShown = { false }).temperatureUnitsShown)
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

    private data class CurrentRequest(
        val units: WeatherUnits,
        val locationLabel: String,
        val cityQuery: String?,
        val latitude: Double?,
        val longitude: Double?,
    )

    private data class ForecastRequest(
        val units: WeatherUnits,
        val cityQuery: String?,
        val latitude: Double?,
        val longitude: Double?,
    )

    private companion object {
        fun currentWeather(
            cityName: String,
            units: WeatherUnits = WeatherUnits.METRIC,
        ) = CurrentWeather(
            cityName = cityName,
            conditionMain = "Clear",
            description = "Clear sky",
            iconCode = "01d",
            temperature = 72.5,
            feelsLike = 70.0,
            tempMin = 68.0,
            tempMax = 76.0,
            humidityPercent = 42,
            pressureHpa = 1013,
            windSpeed = 5.5,
            windDeg = 180,
            visibilityMeters = 10000,
            cloudPercent = 10,
            units = units,
        )

        fun dayForecast(units: WeatherUnits = WeatherUnits.METRIC) =
            DayForecast(
                dateLabel = "Mon, Jan 1",
                conditionMain = "Clouds",
                description = "cloudy",
                iconCode = "02d",
                temperature = 68.0,
                feelsLike = 67.0,
                tempMin = 60.0,
                tempMax = 72.0,
                units = units,
            )
    }
}

private const val MINUTE = 60_000L
