package com.fangjet.weather.data

import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.AirQualityHours
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LocationWeatherRepositoryTest {
    private val london = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)

    @Test
    fun `refreshes fetch in the user's units and cache what comes back under the location's key`() =
        runTest {
            val fetchedUnits = mutableListOf<WeatherUnits>()
            val cachedWeather = mutableListOf<String>()
            val cachedForecast = mutableListOf<String>()
            // A US-locale phone set to metric in Settings: the cache must be in metric.
            val repository = testLocationWeatherRepository(
                units = WeatherUnits.METRIC,
                fetchCurrentWeather = { units, _ ->
                    fetchedUnits += units
                    Result.success(weather(units))
                },
                fetchForecast = { units, _ ->
                    fetchedUnits += units
                    Result.success(listOf(day(units)))
                },
                cacheCurrentWeather = { key, _ -> cachedWeather += key },
                cacheForecast = { key, _ -> cachedForecast += key },
            )

            repository.refreshWeather(london)
            repository.refreshForecast(london)

            assertEquals(listOf(WeatherUnits.METRIC, WeatherUnits.METRIC), fetchedUnits)
            assertEquals(listOf(london.cacheKey), cachedWeather)
            assertEquals(listOf(london.cacheKey), cachedForecast)
        }

    @Test
    fun `a failed fetch caches nothing and the city shown is only kept when asked`() =
        runTest {
            val cached = mutableListOf<String>()
            var lastCity: String? = null
            val repository = testLocationWeatherRepository(
                fetchCurrentWeather = { _, _ -> Result.failure(RuntimeException("offline")) },
                fetchForecast = { _, _ -> Result.failure(RuntimeException("offline")) },
                cacheCurrentWeather = { key, _ -> cached += key },
                cacheForecast = { key, _ -> cached += key },
                setLastCity = { lastCity = it },
            )

            repository.refreshWeather(london)
            repository.refreshForecast(london)
            assertEquals(emptyList<String>(), cached)
            assertNull(lastCity)

            repository.rememberShownCity("London")
            assertEquals("London", lastCity)
        }

    @Test
    fun `the cache is read by the location's key`() =
        runTest {
            val repository = testLocationWeatherRepository(
                fetchCurrentWeather = { _, _ -> Result.failure(RuntimeException()) },
                fetchForecast = { _, _ -> Result.failure(RuntimeException()) },
                getCachedCurrentWeather = { key -> if (key == london.cacheKey) weather(WeatherUnits.IMPERIAL) else null },
            )

            assertEquals("London", repository.cachedWeather(london)?.cityName)
            assertNull(repository.cachedWeather(SavedLocation(label = "Paris", latitude = 48.9, longitude = 2.4)))
        }

    @Test
    fun `a fetch made in units since changed isn't cached`() =
        runTest {
            var units = WeatherUnits.IMPERIAL
            val cached = mutableListOf<String>()
            val repository = testLocationWeatherRepository(
                fetchCurrentWeather = { u, _ ->
                    // The user switches to metric while the imperial fetch is out.
                    units = WeatherUnits.METRIC
                    Result.success(weather(u))
                },
                fetchForecast = { u, _ -> Result.success(listOf(day(u))) },
                cacheCurrentWeather = { key, _ -> cached += key },
                resolveUnits = { units },
            )

            repository.refreshWeather(london)
            assertEquals(emptyList<String>(), cached)
        }

    @Test
    fun `cached air quality goes to the forecast fetch, and only new air quality is cached, whatever the units`() =
        runTest {
            val old = AirQualityHours(1_000L, AirQuality.Scale.EUROPEAN, emptyMap())
            val new = old.copy(fetchedAtEpochSeconds = 2_000L)
            var answer = new
            var units = WeatherUnits.METRIC
            val offered = mutableListOf<AirQualityHours?>()
            val cachedAir = mutableListOf<AirQualityHours>()
            val cachedForecast = mutableListOf<String>()
            val repository = LocationWeatherRepository(
                resolveUnits = { units },
                fetchCurrentWeather = { _, u -> Result.success(weather(u)) },
                fetchForecast = { _, u, cached ->
                    offered += cached
                    // The user switches to imperial while the first fetch is out.
                    units = WeatherUnits.IMPERIAL
                    Result.success(ForecastWithAirQuality(listOf(day(u)), answer))
                },
                readCachedWeather = { null },
                readCachedForecast = { null },
                writeCachedWeather = { _, _ -> },
                writeCachedForecast = { key, _ -> cachedForecast += key },
                setLastCity = {},
                readCachedAirQuality = { key -> old.takeIf { key == london.cacheKey } },
                writeCachedAirQuality = { _, air -> cachedAir += air },
            )

            repository.refreshForecast(london)
            answer = old
            repository.refreshForecast(london)

            assertEquals(listOf(old, old), offered)
            // Air quality doesn't depend on the units: cached though the first forecast wasn't. The
            // second reused it, so it isn't written again.
            assertEquals(listOf(new), cachedAir)
            assertEquals(listOf(london.cacheKey), cachedForecast)
        }

    // --- background runs skip fresh data ---

    @Test
    fun `fetch times count when both were fetched under 30 minutes ago`() {
        val now = 10_000L
        val limit = LocationWeatherRepository.BACKGROUND_FRESH_SECONDS
        assertEquals(30 * 60L, limit)
        assertTrue(FetchTimes(now - limit + 1, now).bothFetchedWithin(limit, now))
        assertFalse(FetchTimes(now - limit, now).bothFetchedWithin(limit, now))
        assertFalse(FetchTimes(now, now - limit).bothFetchedWithin(limit, now))
        assertFalse(FetchTimes(weatherEpochSeconds = now).bothFetchedWithin(limit, now))
        assertFalse(FetchTimes(forecastEpochSeconds = now).bothFetchedWithin(limit, now))
        assertFalse(FetchTimes(now + 60, now).bothFetchedWithin(limit, now))
    }

    @Test
    fun `refreshes record their fetch times, and the cache is fresh while both are under 30 minutes old`() =
        runTest {
            var now = 10_000L
            var times = FetchTimes()
            var units = WeatherUnits.METRIC
            val repository = LocationWeatherRepository(
                resolveUnits = { units },
                fetchCurrentWeather = { _, u -> Result.success(weather(u)) },
                fetchForecast = { _, u, _ -> Result.success(ForecastWithAirQuality(listOf(day(u)), null)) },
                readCachedWeather = { weather(WeatherUnits.METRIC) },
                readCachedForecast = { listOf(day(WeatherUnits.METRIC)) },
                writeCachedWeather = { _, _ -> },
                writeCachedForecast = { _, _ -> },
                setLastCity = {},
                readFetchTimes = { times },
                writeWeatherFetchedAt = { _, at -> times = times.copy(weatherEpochSeconds = at) },
                writeForecastFetchedAt = { _, at -> times = times.copy(forecastEpochSeconds = at) },
                nowEpochSeconds = { now },
            )

            // A cache without fetch times is old.
            assertNull(repository.freshCache(london))
            repository.refreshWeather(london)
            assertNull("Only the reading is fresh", repository.freshCache(london))
            repository.refreshForecast(london)
            assertEquals(FetchTimes(10_000L, 10_000L), times)
            assertEquals("London", repository.freshCache(london)?.weather?.cityName)

            now += LocationWeatherRepository.BACKGROUND_FRESH_SECONDS - 1
            assertNotNull(repository.freshCache(london))
            // Units switched since: the cache is in the old ones.
            units = WeatherUnits.IMPERIAL
            assertNull(repository.freshCache(london))
            units = WeatherUnits.METRIC
            now += 1
            assertNull(repository.freshCache(london))
        }

    private fun weather(units: WeatherUnits) =
        CurrentWeather(
            cityName = "London",
            conditionMain = "Clear",
            description = "clear sky",
            iconCode = "01d",
            temperature = 15.0,
            feelsLike = 14.0,
            tempMin = 10.0,
            tempMax = 18.0,
            humidityPercent = 50,
            pressureHpa = 1013,
            windSpeed = 3.0,
            windDeg = 180,
            visibilityMeters = 10000,
            cloudPercent = 0,
            units = units,
        )

    private fun day(units: WeatherUnits) =
        DayForecast(
            dateLabel = "Mon, Jan 1",
            conditionMain = "Clear",
            description = "clear sky",
            iconCode = "01d",
            temperature = 15.0,
            feelsLike = 14.0,
            tempMin = 10.0,
            tempMax = 18.0,
            units = units,
        )
}
