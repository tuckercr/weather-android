package com.fangjet.weather.data

import android.content.Context
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.RainOutlookSettings
import com.google.android.gms.tasks.OnCompleteListener
import com.google.android.gms.tasks.Task
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.remoteConfig
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class RemoteConfigRepositoryTest {

    private val context = mockk<Context>(relaxed = true)
    private val remoteConfig = mockk<FirebaseRemoteConfig>(relaxed = true)

    // Remote Config's own answer for a key nobody set: the STATIC source, reading false / 0.
    private val unset = configValue(source = FirebaseRemoteConfig.VALUE_SOURCE_STATIC, raw = "")
    private val values = mutableMapOf<String, FirebaseRemoteConfigValue>()
    private val fetchListener = slot<OnCompleteListener<Boolean>>()
    private val fetchTask = mockk<Task<Boolean>>(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(FirebaseApp::class)
        mockkStatic("com.google.firebase.remoteconfig.RemoteConfigKt")
        every { Firebase.remoteConfig } returns remoteConfig
        every { remoteConfig.getValue(any()) } answers { values[firstArg()] ?: unset }
        every { remoteConfig.fetchAndActivate() } returns fetchTask
        every { fetchTask.addOnCompleteListener(capture(fetchListener)) } returns fetchTask
    }

    @After
    fun tearDown() = unmockkAll()

    private fun firebaseInitialised(initialised: Boolean) {
        every { FirebaseApp.getApps(any()) } returns if (initialised) listOf(mockk()) else emptyList()
    }

    private fun set(
        key: String,
        raw: String,
    ) {
        values[key] = configValue(FirebaseRemoteConfig.VALUE_SOURCE_REMOTE, raw)
    }

    // --- without Firebase ---

    @Test
    fun `without Firebase every value is the compiled-in default`() {
        firebaseInitialised(false)
        val repo = RemoteConfigRepository(context)

        assertDefaults(repo)
    }

    @Test
    fun `a Firebase lookup that throws is treated as no Firebase`() {
        every { FirebaseApp.getApps(any()) } throws IllegalStateException("no google-services.json")
        val repo = RemoteConfigRepository(context)

        assertDefaults(repo)
    }

    @Test
    fun `Remote Config that fails to initialise falls back to the defaults`() {
        firebaseInitialised(true)
        every { Firebase.remoteConfig } throws IllegalStateException("boom")
        val repo = RemoteConfigRepository(context)

        assertDefaults(repo)
    }

    // --- with Firebase ---

    @Test
    fun `keys with no value yet keep the defaults rather than reading false or zero`() {
        firebaseInitialised(true)
        val repo = RemoteConfigRepository(context)

        assertDefaults(repo)
    }

    @Test
    fun `remote values are read when present`() {
        firebaseInitialised(true)
        set(RemoteConfigRepository.KEY_REFRESH_INTERVAL, "60")
        set(RemoteConfigRepository.KEY_RADAR_FRAME_INTERVAL, "400")
        set(RemoteConfigRepository.KEY_RADAR_ENABLED, "false")
        set(RemoteConfigRepository.KEY_WALK_CARD_ENABLED, "false")
        set(RemoteConfigRepository.KEY_HOURLY_FORECAST_ENABLED, "false")
        set(RemoteConfigRepository.KEY_NEXT_RAIN_ENABLED, "false")
        set(RemoteConfigRepository.KEY_WEEK_FORECAST_ENABLED, "false")
        set(RemoteConfigRepository.KEY_OPEN_METEO_ENABLED, "false")
        set(RemoteConfigRepository.KEY_DAILY_WEATHER_NOTIFICATIONS_ENABLED, "false")
        set(RemoteConfigRepository.KEY_WALK_TIME_NOTIFICATIONS_ENABLED, "false")
        set(RemoteConfigRepository.KEY_RAIN_NOTIFICATIONS_ENABLED, "false")
        set(RemoteConfigRepository.KEY_FOREGROUND_REFRESH_MIN_MINUTES, "15")
        set(RemoteConfigRepository.KEY_ANALYTICS_ENABLED, "false")
        val repo = RemoteConfigRepository(context)

        assertEquals(60L, repo.refreshIntervalMinutes())
        assertEquals(400L, repo.radarFrameIntervalMs())
        assertFalse(repo.radarEnabled())
        assertFalse(repo.walkCardEnabled())
        assertFalse(repo.hourlyForecastEnabled())
        assertFalse(repo.nextRainEnabled())
        assertFalse(repo.weekForecastEnabled())
        assertFalse(repo.openMeteoEnabled())
        assertFalse(repo.dailyWeatherNotificationsEnabled())
        assertFalse(repo.walkTimeNotificationsEnabled())
        assertFalse(repo.rainNotificationsEnabled())
        assertEquals(15L, repo.foregroundRefreshMinMinutes())
        assertFalse(repo.analyticsEnabled())
    }

    @Test
    fun `out of range radar frame interval falls back to the default`() {
        firebaseInitialised(true)
        val repo = RemoteConfigRepository(context)

        listOf("99", "5001", "-1").forEach { raw ->
            set(RemoteConfigRepository.KEY_RADAR_FRAME_INTERVAL, raw)
            assertEquals(raw, RemoteConfigRepository.DEFAULT_RADAR_FRAME_INTERVAL_MS, repo.radarFrameIntervalMs())
        }
        set(RemoteConfigRepository.KEY_RADAR_FRAME_INTERVAL, "100")
        assertEquals(100L, repo.radarFrameIntervalMs())
        set(RemoteConfigRepository.KEY_RADAR_FRAME_INTERVAL, "5000")
        assertEquals(5000L, repo.radarFrameIntervalMs())
    }

    @Test
    fun `out of range foreground refresh throttle falls back to always refreshing`() {
        firebaseInitialised(true)
        val repo = RemoteConfigRepository(context)

        set(RemoteConfigRepository.KEY_FOREGROUND_REFRESH_MIN_MINUTES, "1441")
        assertEquals(0L, repo.foregroundRefreshMinMinutes())
        set(RemoteConfigRepository.KEY_FOREGROUND_REFRESH_MIN_MINUTES, "-5")
        assertEquals(0L, repo.foregroundRefreshMinMinutes())
        set(RemoteConfigRepository.KEY_FOREGROUND_REFRESH_MIN_MINUTES, "1440")
        assertEquals(1440L, repo.foregroundRefreshMinMinutes())
    }

    @Test
    fun `unparsable values fall back to the defaults`() {
        firebaseInitialised(true)
        set(RemoteConfigRepository.KEY_REFRESH_INTERVAL, "soon")
        set(RemoteConfigRepository.KEY_RADAR_ENABLED, "maybe")
        set(RemoteConfigRepository.KEY_HOURLY_FORECAST_ENABLED, "off")
        set(RemoteConfigRepository.KEY_NEXT_RAIN_ENABLED, "")
        set(RemoteConfigRepository.KEY_WEEK_FORECAST_ENABLED, "0.5")
        val repo = RemoteConfigRepository(context)

        assertEquals(RemoteConfigRepository.DEFAULT_REFRESH_INTERVAL_MINUTES, repo.refreshIntervalMinutes())
        assertEquals(RemoteConfigRepository.DEFAULT_RADAR_ENABLED, repo.radarEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_HOURLY_FORECAST_ENABLED, repo.hourlyForecastEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_NEXT_RAIN_ENABLED, repo.nextRainEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_WEEK_FORECAST_ENABLED, repo.weekForecastEnabled())
    }

    @Test
    fun `rain outlook reads its switch and whole numbers in range`() {
        firebaseInitialised(true)
        set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_ENABLED, "false")
        set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_HOURS, "6")
        set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_MIN_CHANCE, "100.0")
        val repo = RemoteConfigRepository(context)

        assertEquals(RainOutlookSettings(enabled = false, hours = 6, minChance = 100), repo.rainOutlookSettings())
        set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_HOURS, "12")
        set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_MIN_CHANCE, "1")
        assertEquals(RainOutlookSettings(enabled = false, hours = 12, minChance = 1), repo.rainOutlookSettings())
    }

    @Test
    fun `rain outlook values out of range, fractional or junk fall back to the defaults`() {
        firebaseInitialised(true)
        val repo = RemoteConfigRepository(context)

        listOf("5", "13", "8.5", "-10", "lots", "").forEach { raw ->
            set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_HOURS, raw)
            assertEquals(raw, RainOutlookSettings().hours, repo.rainOutlookSettings().hours)
        }
        listOf("0", "101", "20.5", "-1", "some", "").forEach { raw ->
            set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_MIN_CHANCE, raw)
            assertEquals(raw, RainOutlookSettings().minChance, repo.rainOutlookSettings().minChance)
        }
        set(RemoteConfigRepository.KEY_RAIN_OUTLOOK_ENABLED, "perhaps")
        assertEquals(RainOutlookSettings().enabled, repo.rainOutlookSettings().enabled)
    }

    @Test
    fun `replacement API key is trimmed and blank means none`() {
        firebaseInitialised(true)
        val repo = RemoteConfigRepository(context)

        assertNull(repo.openWeatherApiKey())
        set(RemoteConfigRepository.KEY_OPENWEATHER_API_KEY, "   ")
        assertNull(repo.openWeatherApiKey())
        set(RemoteConfigRepository.KEY_OPENWEATHER_API_KEY, "  abc123 \n")
        assertEquals("abc123", repo.openWeatherApiKey())
    }

    @Test
    fun `pet thresholds read whole-number hours and ignore fractional ones`() {
        firebaseInitialised(true)
        set(RemoteConfigRepository.KEY_WALK_HOURS_START, "7.0")
        set(RemoteConfigRepository.KEY_WALK_MAX_RAIN_CHANCE, "40.5")
        val repo = RemoteConfigRepository(context)

        val thresholds = repo.petWeatherThresholds()

        assertEquals(7, thresholds.walkHoursStart)
        assertEquals(PetWeatherThresholds.DEFAULT.maxRainChancePercent, thresholds.maxRainChancePercent)
    }

    @Test
    fun `pet thresholds read remote pavement temperatures`() {
        firebaseInitialised(true)
        set(RemoteConfigRepository.KEY_WALK_HOT_PAVEMENT_C, "50")
        val repo = RemoteConfigRepository(context)

        assertEquals(50.0, repo.petWeatherThresholds().hotPavementC, 0.0)
    }

    @Test
    fun `analytics updates re-emit when a fetch activates a new kill switch value`() =
        runTest(UnconfinedTestDispatcher()) {
            firebaseInitialised(true)
            val repo = RemoteConfigRepository(context)
            val seen = mutableListOf<Boolean>()
            val job = launch { repo.analyticsEnabledUpdates.take(2).toList(seen) }

            set(RemoteConfigRepository.KEY_ANALYTICS_ENABLED, "false")
            fetchListener.captured.onComplete(fetchTask)
            job.join()

            assertEquals(listOf(true, false), seen)
        }

    @Test
    fun `a failed fetch keeps the current values`() =
        runTest {
            firebaseInitialised(true)
            every { fetchTask.isSuccessful } returns false
            every { fetchTask.exception } returns RuntimeException("offline")
            val repo = RemoteConfigRepository(context)

            fetchListener.captured.onComplete(fetchTask)

            assertTrue(repo.analyticsEnabledUpdates.first())
            assertTrue(repo.radarEnabled())
        }

    private fun assertDefaults(repo: RemoteConfigRepository) {
        assertEquals(RemoteConfigRepository.DEFAULT_REFRESH_INTERVAL_MINUTES, repo.refreshIntervalMinutes())
        assertEquals(RemoteConfigRepository.DEFAULT_RADAR_FRAME_INTERVAL_MS, repo.radarFrameIntervalMs())
        assertEquals(RemoteConfigRepository.DEFAULT_RADAR_ENABLED, repo.radarEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_WALK_CARD_ENABLED, repo.walkCardEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_HOURLY_FORECAST_ENABLED, repo.hourlyForecastEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_NEXT_RAIN_ENABLED, repo.nextRainEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_WEEK_FORECAST_ENABLED, repo.weekForecastEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_OPEN_METEO_ENABLED, repo.openMeteoEnabled())
        assertEquals(
            RemoteConfigRepository.DEFAULT_DAILY_WEATHER_NOTIFICATIONS_ENABLED,
            repo.dailyWeatherNotificationsEnabled(),
        )
        assertEquals(RemoteConfigRepository.DEFAULT_WALK_TIME_NOTIFICATIONS_ENABLED, repo.walkTimeNotificationsEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_RAIN_NOTIFICATIONS_ENABLED, repo.rainNotificationsEnabled())
        assertEquals(RemoteConfigRepository.DEFAULT_FOREGROUND_REFRESH_MIN_MINUTES, repo.foregroundRefreshMinMinutes())
        assertEquals(RemoteConfigRepository.DEFAULT_ANALYTICS_ENABLED, repo.analyticsEnabled())
        assertNull(repo.openWeatherApiKey())
        assertEquals(PetWeatherThresholds.DEFAULT, repo.petWeatherThresholds())
        assertEquals(RainOutlookSettings(enabled = true, hours = 10, minChance = 20), repo.rainOutlookSettings())
    }

    /** A value that parses like Remote Config's own: numbers and booleans throw on junk. */
    private fun configValue(
        source: Int,
        raw: String,
    ): FirebaseRemoteConfigValue =
        mockk {
            every { this@mockk.source } returns source
            every { asString() } returns raw
            every { asLong() } answers { raw.trim().toLong() }
            every { asDouble() } answers { raw.trim().toDouble() }
            every { asBoolean() } answers {
                when (raw.trim().lowercase()) {
                    "true" -> true
                    "false" -> false
                    else -> throw IllegalArgumentException("not a boolean: $raw")
                }
            }
        }
}
