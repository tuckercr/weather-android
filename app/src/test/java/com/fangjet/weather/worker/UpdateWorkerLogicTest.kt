package com.fangjet.weather.worker

import androidx.work.ListenableWorker.Result
import com.fangjet.weather.data.testLocationWeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UpdateWorkerLogicTest {

    // --- result routing ---

    @Test
    fun `returns success immediately when no locations are saved`() =
        runTest {
            val cached = mutableListOf<String>()
            val logic = logic(savedLocations = emptyList(), cacheCurrentWeather = { k, _ -> cached += k })

            assertEquals(Result.success(), logic.doWork())
            assertEquals(emptyList<String>(), cached)
        }

    @Test
    fun `returns success when only current weather succeeds`() =
        runTest {
            val logic = logic(
                fetchForecast = { _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
            )

            assertEquals(Result.success(), logic.doWork())
        }

    @Test
    fun `returns success when only forecast succeeds`() =
        runTest {
            val logic = logic(
                fetchCurrentWeather = { _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
            )

            assertEquals(Result.success(), logic.doWork())
        }

    @Test
    fun `returns retry when both fetches fail`() =
        runTest {
            val logic = logic(
                fetchCurrentWeather = { _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
                fetchForecast = { _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
            )

            assertEquals(Result.retry(), logic.doWork())
        }

    // --- caching ---

    @Test
    fun `caches current weather and forecast under the location cache key on success`() =
        runTest {
            val cachedWeather = mutableListOf<Pair<String, CurrentWeather>>()
            val cachedForecast = mutableListOf<Pair<String, List<DayForecast>>>()
            val logic = logic(
                savedLocations = listOf(london),
                cacheCurrentWeather = { k, w -> cachedWeather += k to w },
                cacheForecast = { k, f -> cachedForecast += k to f },
            )

            logic.doWork()

            assertEquals(listOf(london.cacheKey), cachedWeather.map { it.first })
            assertEquals(listOf(london.cacheKey), cachedForecast.map { it.first })
        }

    @Test
    fun `does not cache when a fetch fails`() =
        runTest {
            val cachedWeather = mutableListOf<String>()
            val cachedForecast = mutableListOf<String>()
            val logic = logic(
                fetchCurrentWeather = { _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
                fetchForecast = { _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
                cacheCurrentWeather = { k, _ -> cachedWeather += k },
                cacheForecast = { k, _ -> cachedForecast += k },
            )

            logic.doWork()

            assertEquals(emptyList<String>(), cachedWeather)
            assertEquals(emptyList<String>(), cachedForecast)
        }

    // --- units propagation (regression guard for the override bug) ---

    @Test
    fun `resolved units are passed to both the current and forecast fetches`() =
        runTest {
            // A US-locale device whose system default would resolve to IMPERIAL, but the user
            // picked METRIC: getUnits returns METRIC and that MUST reach both fetches, otherwise
            // the worker writes the wrong unit system into the cache.
            var currentUnits: WeatherUnits? = null
            var forecastUnits: WeatherUnits? = null
            val logic = logic(
                units = WeatherUnits.METRIC,
                fetchCurrentWeather = { units, _ ->
                    currentUnits = units
                    kotlin.Result.success(weather(units = units))
                },
                fetchForecast = { units, _ ->
                    forecastUnits = units
                    kotlin.Result.success(listOf(forecast(units = units)))
                },
            )

            logic.doWork()

            assertEquals(WeatherUnits.METRIC, currentUnits)
            assertEquals(WeatherUnits.METRIC, forecastUnits)
        }

    // --- fetch routing ---

    @Test
    fun `passes lat-lon to fetches when location has coordinates`() =
        runTest {
            var capturedLat: Double? = 99.0
            var capturedLon: Double? = 99.0
            val logic = logic(
                savedLocations = listOf(london),
                fetchCurrentWeather = { _, loc ->
                    capturedLat = loc.latitude
                    capturedLon = loc.longitude
                    kotlin.Result.success(weather())
                },
            )

            logic.doWork()

            assertEquals(london.latitude, capturedLat)
            assertEquals(london.longitude, capturedLon)
        }

    @Test
    fun `passes null lat-lon and label as query when location has no coordinates`() =
        runTest {
            var capturedLat: Double? = 99.0
            var capturedLon: Double? = 99.0
            var capturedLabel: String? = null
            val logic = logic(
                savedLocations = listOf(cityByName),
                fetchCurrentWeather = { _, loc ->
                    capturedLat = loc.latitude
                    capturedLon = loc.longitude
                    capturedLabel = loc.label
                    kotlin.Result.success(weather(cityName = "Austin"))
                },
            )

            logic.doWork()

            assertNull(capturedLat)
            assertNull(capturedLon)
            assertEquals(cityByName.label, capturedLabel)
        }

    @Test
    fun `refreshes the notification city, My Location first, not the tab last viewed`() =
        runTest {
            val myLocation = SavedLocation("My Location", 40.0, -83.0, isCurrentLocation = true)
            val capturedLats = mutableListOf<Double?>()

            fun logicFor(locations: List<SavedLocation>) =
                logic(
                    savedLocations = locations,
                    fetchCurrentWeather = { _, loc ->
                        capturedLats += loc.latitude
                        kotlin.Result.success(weather())
                    },
                )

            logicFor(listOf(london, myLocation)).doWork()
            logicFor(listOf(london, cityByName)).doWork()

            assertEquals(listOf(40.0, london.latitude), capturedLats)
        }

    @Test
    fun `also refreshes the tab last viewed when it isn't the notification city, without an alert`() =
        runTest {
            val cachedWeather = mutableListOf<String>()
            val cachedForecast = mutableListOf<String>()
            val alerted = mutableListOf<String>()
            val logic = logic(
                savedLocations = listOf(london, cityByName),
                activeIndex = 1,
                cacheCurrentWeather = { key, _ -> cachedWeather += key },
                cacheForecast = { key, _ -> cachedForecast += key },
                afterRefresh = { location, _, _ -> alerted += location.cacheKey },
            )

            assertEquals(Result.success(), logic.doWork())
            assertEquals(listOf(london.cacheKey, cityByName.cacheKey), cachedWeather)
            assertEquals(listOf(london.cacheKey, cityByName.cacheKey), cachedForecast)
            // The rain alert is only ever for the notification city.
            assertEquals(listOf(london.cacheKey), alerted)
        }

    @Test
    fun `the notification city is fetched once when it's also the tab last viewed`() =
        runTest {
            var fetches = 0
            val logic = logic(
                savedLocations = listOf(london, cityByName),
                activeIndex = 0,
                fetchCurrentWeather = { u, _ ->
                    fetches++
                    kotlin.Result.success(weather(units = u))
                },
            )

            logic.doWork()

            assertEquals(1, fetches)
        }

    @Test
    fun `the result goes by the notification city, not the tab last viewed`() =
        runTest {
            val offline = kotlin.Result.failure<Nothing>(RuntimeException("offline"))

            fun failingFor(location: SavedLocation) =
                logic(
                    savedLocations = listOf(london, cityByName),
                    activeIndex = 1,
                    fetchCurrentWeather = { u, loc ->
                        if (loc.label == location.label) offline else kotlin.Result.success(weather(units = u))
                    },
                    fetchForecast = { u, loc ->
                        if (loc.label == location.label) offline else kotlin.Result.success(listOf(forecast(units = u)))
                    },
                )

            assertEquals(Result.success(), failingFor(cityByName).doWork())
            assertEquals(Result.retry(), failingFor(london).doWork())
        }

    @Test
    fun `a failing rain alert step does not fail the refresh`() =
        runTest {
            val cachedKeys = mutableListOf<String>()
            val logic = logic(
                cacheCurrentWeather = { key, _ -> cachedKeys += key },
                afterRefresh = { _, _, _ -> throw IllegalStateException("notification failed") },
            )

            assertEquals(Result.success(), logic.doWork())
            assertEquals(listOf(london.cacheKey), cachedKeys)
        }

    // --- helpers ---

    private fun logic(
        savedLocations: List<SavedLocation> = listOf(london),
        activeIndex: Int? = null,
        units: WeatherUnits = WeatherUnits.IMPERIAL,
        fetchCurrentWeather: suspend (WeatherUnits, SavedLocation) -> kotlin.Result<CurrentWeather> =
            { u, _ -> kotlin.Result.success(weather(units = u)) },
        fetchForecast: suspend (WeatherUnits, SavedLocation) -> kotlin.Result<List<DayForecast>> =
            { u, _ -> kotlin.Result.success(listOf(forecast(units = u))) },
        cacheCurrentWeather: suspend (String, CurrentWeather) -> Unit = { _, _ -> },
        cacheForecast: suspend (String, List<DayForecast>) -> Unit = { _, _ -> },
        afterRefresh: suspend (SavedLocation, CurrentWeather?, List<DayForecast>?) -> Unit = { _, _, _ -> },
    ): UpdateWorkerLogic {
        // The real repository over fake fetches: it resolves the units and does the caching.
        val repository = testLocationWeatherRepository(units, fetchCurrentWeather, fetchForecast, cacheCurrentWeather, cacheForecast)
        return UpdateWorkerLogic(
            getSavedLocations = { savedLocations },
            getActiveIndex = { activeIndex },
            refreshWeather = repository::refreshWeather,
            refreshForecast = repository::refreshForecast,
            afterRefresh = afterRefresh,
        )
    }

    private companion object {
        val london = SavedLocation(label = "My Location", latitude = 51.5, longitude = -0.1)
        val cityByName = SavedLocation(label = "Austin, TX", latitude = null, longitude = null)

        fun weather(
            cityName: String = "London",
            units: WeatherUnits = WeatherUnits.IMPERIAL,
        ) = CurrentWeather(
            cityName = cityName,
            conditionMain = "Clear",
            description = "clear sky",
            iconCode = "01d",
            temperature = 72.0,
            feelsLike = 70.0,
            tempMin = 60.0,
            tempMax = 80.0,
            humidityPercent = 50,
            pressureHpa = 1013,
            windSpeed = 5.0,
            windDeg = 180,
            visibilityMeters = 10000,
            cloudPercent = 0,
            units = units,
        )

        fun forecast(units: WeatherUnits = WeatherUnits.IMPERIAL) =
            DayForecast(
                dateLabel = "Mon, Jan 1",
                conditionMain = "Clear",
                description = "clear sky",
                iconCode = "01d",
                temperature = 70.0,
                feelsLike = 68.0,
                tempMin = 55.0,
                tempMax = 85.0,
                units = units,
            )
    }
}
