package com.fangjet.weather.data

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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
