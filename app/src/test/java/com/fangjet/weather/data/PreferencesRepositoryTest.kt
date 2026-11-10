package com.fangjet.weather.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.AirQualityHours
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class PreferencesRepositoryTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val json = Json { ignoreUnknownKeys = true }

    private fun TestScope.store() =
        PreferenceDataStoreFactory.create(scope = CoroutineScope(backgroundScope.coroutineContext)) {
            tmp.newFile("prefs.preferences_pb").also { it.delete() }
        }

    private fun TestScope.repository() = PreferencesRepository(store(), json)

    // --- defaults on a fresh install ---

    @Test
    fun `fresh install has nothing seen, no cities, and the opt-in defaults on`() =
        runTest {
            val prefs = repository()

            assertFalse(prefs.hasSeenWelcomeOnce())
            assertFalse(prefs.locationOnboardingDoneOnce())
            assertFalse(prefs.notificationOnboardingDoneOnce())
            assertNull(prefs.lastCity.first())
            assertEquals(emptyList<SavedLocation>(), prefs.savedLocations.first())
            assertEquals(0, prefs.activeLocationIndex.first())
            assertEquals(UnitOverride.SYSTEM, prefs.unitOverride.first())
            assertTrue(prefs.shareUsageStatistics.first())
            assertTrue(prefs.showWalkCard.first())
        }

    // --- onboarding ---

    @Test
    fun `onboarding flags persist once set`() =
        runTest {
            val prefs = repository()

            prefs.setHasSeenWelcome(true)
            prefs.setLocationOnboardingDone()
            prefs.setNotificationOnboardingDone()

            assertTrue(prefs.hasSeenWelcomeOnce())
            assertTrue(prefs.locationOnboardingDoneOnce())
            assertTrue(prefs.notificationOnboardingDoneOnce())
        }

    @Test
    fun `an install that finished location onboarding before the notification step skips it`() =
        runTest {
            val prefs = repository()

            prefs.setLocationOnboardingDone()

            assertTrue(prefs.notificationOnboardingDoneOnce())
        }

    @Test
    fun `notification onboarding is still due when location onboarding is not done`() =
        runTest {
            assertFalse(repository().notificationOnboardingDoneOnce())
        }

    // --- cities ---

    @Test
    fun `saved locations round-trip in order`() =
        runTest {
            val prefs = repository()
            val cities = listOf(
                SavedLocation(label = "", latitude = 37.4, longitude = -122.1, isCurrentLocation = true),
                SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1),
                SavedLocation(label = "Springfield", latitude = null, longitude = null),
            )

            prefs.setSavedLocations(cities)
            prefs.setActiveLocationIndex(2)

            assertEquals(cities, prefs.savedLocations.first())
            assertEquals(2, prefs.activeLocationIndex.first())
        }

    @Test
    fun `corrupt saved locations read as an empty list instead of crashing`() =
        runTest {
            val store = store()
            store.edit { it[stringPreferencesKey("saved_locations")] = "{not json" }

            assertEquals(emptyList<SavedLocation>(), PreferencesRepository(store, json).savedLocations.first())
        }

    @Test
    fun `a blank last city reads as none`() =
        runTest {
            val prefs = repository()

            prefs.setLastCity("   ")
            assertNull(prefs.lastCity.first())

            prefs.setLastCity("Paris")
            assertEquals("Paris", prefs.lastCity.first())
        }

    @Test
    fun `My Location's fallback name never becomes the last city`() =
        runTest {
            val prefs = repository()

            prefs.setLastCity("Paris")
            prefs.setLastCity("My Location")

            assertEquals("Paris", prefs.lastCity.first())
        }

    @Test
    fun `My Location's place name is remembered with where it was looked up`() =
        runTest {
            val prefs = repository()
            assertNull(prefs.rememberedPlaceName())

            prefs.setRememberedPlaceName(DevicePlaceNames.Remembered("Knoxville", 35.9168, -83.9597))

            assertEquals(DevicePlaceNames.Remembered("Knoxville", 35.9168, -83.9597), prefs.rememberedPlaceName())
        }

    // --- weather and forecast caches ---

    @Test
    fun `the weather and forecast are cached as themselves, and an unreadable entry is none`() =
        runTest {
            val prefs = repository()
            val reading = CurrentWeather(
                cityName = "London",
                conditionMain = "Clouds",
                description = "Overcast",
                iconCode = "04d",
                temperature = 9.0,
                feelsLike = 7.0,
                tempMin = 6.0,
                tempMax = 11.0,
                humidityPercent = 80,
                pressureHpa = 1012,
                windSpeed = 4.0,
                windDeg = 220,
                visibilityMeters = 10_000,
                cloudPercent = 90,
                units = WeatherUnits.METRIC,
            )
            val day = DayForecast(
                dateLabel = "Sat, Nov 7",
                conditionMain = "Clouds",
                description = "overcast",
                iconCode = "04d",
                temperature = 9.0,
                feelsLike = 7.0,
                tempMin = 6.0,
                tempMax = 11.0,
                units = WeatherUnits.METRIC,
            )

            prefs.setCachedWeather("london", reading)
            prefs.setCachedForecast("london", listOf(day))
            prefs.setCachedWeatherFor("tokyo", "not json")

            assertEquals(reading, prefs.getCachedWeather("london"))
            assertEquals(listOf(day), prefs.getCachedForecast("london"))
            assertNull(prefs.getCachedWeather("tokyo"))
            assertNull(prefs.getCachedForecast("tokyo"))
        }

    @Test
    fun `caches are kept per location and do not overwrite each other`() =
        runTest {
            val prefs = repository()

            prefs.setCachedWeatherFor("london", "{\"w\":1}")
            prefs.setCachedWeatherFor("tokyo", "{\"w\":2}")
            prefs.setCachedForecastFor("london", "[1]")

            assertEquals("{\"w\":1}", prefs.getCachedWeatherFor("london"))
            assertEquals("{\"w\":2}", prefs.getCachedWeatherFor("tokyo"))
            assertEquals("[1]", prefs.getCachedForecastFor("london"))
            assertNull(prefs.getCachedForecastFor("tokyo"))
            assertNull(prefs.getCachedWeatherFor("paris"))
        }

    @Test
    fun `writing a location's cache again replaces it`() =
        runTest {
            val prefs = repository()

            prefs.setCachedWeatherFor("london", "old")
            prefs.setCachedWeatherFor("london", "new")

            assertEquals("new", prefs.getCachedWeatherFor("london"))
        }

    @Test
    fun `a corrupt cache reads as a miss and the next write starts it afresh`() =
        runTest {
            val store = store()
            store.edit {
                it[stringPreferencesKey("weather_cache")] = "garbage"
                it[stringPreferencesKey("forecast_cache")] = "garbage"
            }
            val prefs = PreferencesRepository(store, json)

            assertNull(prefs.getCachedWeatherFor("london"))
            assertNull(prefs.getCachedForecastFor("london"))

            prefs.setCachedWeatherFor("london", "fresh")
            prefs.setCachedForecastFor("london", "fresh")
            assertEquals("fresh", prefs.getCachedWeatherFor("london"))
            assertEquals("fresh", prefs.getCachedForecastFor("london"))
        }

    @Test
    fun `clearCache drops both caches and keeps the cities`() =
        runTest {
            val prefs = repository()
            val city = SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1)
            prefs.setSavedLocations(listOf(city))
            prefs.setCachedWeatherFor("london", "w")
            prefs.setCachedForecastFor("london", "f")

            prefs.clearCache()

            assertNull(prefs.getCachedWeatherFor("london"))
            assertNull(prefs.getCachedForecastFor("london"))
            assertEquals(listOf(city), prefs.savedLocations.first())
        }

    @Test
    fun `air quality is cached per location with its fetch time and scale, and cleared with the rest`() =
        runTest {
            val prefs = repository()
            val air = AirQualityHours(1_000L, AirQuality.Scale.EUROPEAN, mapOf(3_600L to AirQuality(30, AirQuality.Scale.EUROPEAN, null)))

            prefs.setCachedAirQuality("london", air)

            assertEquals(air, prefs.getCachedAirQuality("london"))
            assertNull(prefs.getCachedAirQuality("paris"))
            prefs.clearCache()
            assertNull(prefs.getCachedAirQuality("london"))
        }

    // --- settings ---

    @Test
    fun `unit override round-trips every choice`() =
        runTest {
            val prefs = repository()

            UnitOverride.entries.forEach { choice ->
                prefs.setUnitOverride(choice)
                assertEquals(choice, prefs.unitOverride.first())
            }
        }

    @Test
    fun `an unknown stored unit falls back to the system setting`() =
        runTest {
            val store = store()
            store.edit { it[stringPreferencesKey("unit_override")] = "KELVIN" }

            assertEquals(UnitOverride.SYSTEM, PreferencesRepository(store, json).unitOverride.first())
        }

    @Test
    fun `usage statistics and walk card switches persist when turned off`() =
        runTest {
            val prefs = repository()

            prefs.setShareUsageStatistics(false)
            prefs.setShowWalkCard(false)

            assertFalse(prefs.shareUsageStatistics.first())
            assertFalse(prefs.showWalkCard.first())
        }

    @Test
    fun `active index defaults to zero until one is stored`() =
        runTest {
            val store = store()
            assertEquals(0, PreferencesRepository(store, json).activeLocationIndex.first())

            store.edit { it[intPreferencesKey("active_location_index")] = 3 }
            assertEquals(3, PreferencesRepository(store, json).activeLocationIndex.first())
        }
}
