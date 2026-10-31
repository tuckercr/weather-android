package com.fangjet.weather.data

import android.content.Context
import com.fangjet.weather.R
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SeasonalTheme
import com.fangjet.weather.domain.SeasonalThemes
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.worker.NotificationKind
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue
import com.google.firebase.remoteconfig.remoteConfig
import com.google.firebase.remoteconfig.remoteConfigSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Server-tunable values from Firebase Remote Config (project `cats-dogs-weather`; the
 * console parameters live in `firebase/remoteconfig.template.json`).
 *
 * ## Never crash, fall back to defaults
 *
 * Firebase is optional: a checkout without `google-services.json` never initialises
 * [FirebaseApp], and in that case this class is an inert pass-through that returns the
 * compiled-in defaults below. With Firebase present it seeds the in-app defaults from
 * `res/xml/remote_config_defaults.xml`, reads the last activated values immediately on
 * cold start, and refreshes in the background. Every read is guarded: a missing, unparsable
 * or out-of-range value falls back to the default rather than throwing.
 */
@Singleton
class RemoteConfigRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Bumped whenever new values become readable (in-app defaults landed, fetch activated).
    // Declared before remoteConfig: its completion listeners write here.
    private val activations = MutableStateFlow(0)

    /** Non-null only when Firebase actually initialised. */
    private val remoteConfig: FirebaseRemoteConfig? = initRemoteConfig()

    fun refreshIntervalMinutes(): Long = long(KEY_REFRESH_INTERVAL) ?: DEFAULT_REFRESH_INTERVAL_MINUTES

    /** Delay between radar animation frames, in milliseconds. Clamped to a sane range. */
    fun radarFrameIntervalMs(): Long =
        long(KEY_RADAR_FRAME_INTERVAL)
            ?.takeIf { it in MIN_RADAR_FRAME_INTERVAL_MS..MAX_RADAR_FRAME_INTERVAL_MS }
            ?: DEFAULT_RADAR_FRAME_INTERVAL_MS

    /**
     * Radar kill switch. False hides the radar section on the current-weather screen and skips
     * the RainViewer timeline and tile fetches — for when RainViewer or OpenStreetMap tiles
     * become unavailable.
     */
    fun radarEnabled(): Boolean = boolean(KEY_RADAR_ENABLED) ?: DEFAULT_RADAR_ENABLED

    /** False hides the "time to walk the dog" card on the current-weather screen. */
    fun walkCardEnabled(): Boolean = boolean(KEY_WALK_CARD_ENABLED) ?: DEFAULT_WALK_CARD_ENABLED

    /**
     * Forecast provider for cities with coordinates: true uses Open-Meteo, false falls back to
     * the OpenWeatherMap 3-hourly forecast by lat/lon.
     */
    fun openMeteoEnabled(): Boolean = boolean(KEY_OPEN_METEO_ENABLED) ?: DEFAULT_OPEN_METEO_ENABLED

    /**
     * False stops fetching air quality: no Air quality tile, and the walk planner ignores the air.
     * Air quality comes from Open-Meteo, so it also needs [openMeteoEnabled].
     */
    fun airQualityEnabled(): Boolean = boolean(KEY_AIR_QUALITY_ENABLED) ?: DEFAULT_AIR_QUALITY_ENABLED

    /**
     * The seasonal themes to choose from: Remote Config's `seasonal_themes` when it's a valid list,
     * else the list shipped in res/raw/seasonal_themes.json; none when `seasonal_themes_enabled` is
     * off.
     */
    fun seasonalThemes(): List<SeasonalTheme> {
        if (!(boolean(KEY_SEASONAL_THEMES_ENABLED) ?: DEFAULT_SEASONAL_THEMES_ENABLED)) return emptyList()
        return string(KEY_SEASONAL_THEMES)?.takeIf { it.isNotBlank() }?.let(SeasonalThemes::parse) ?: builtInSeasonalThemes
    }

    private val builtInSeasonalThemes: List<SeasonalTheme> by lazy {
        runCatching {
            context.resources
                .openRawResource(R.raw.seasonal_themes)
                .bufferedReader()
                .use { it.readText() }
        }.getOrNull()?.let(SeasonalThemes::parse).orEmpty()
    }

    /** One switch per Today tile, so any can be hidden for everyone. Air quality has its own, above. */
    fun todayTiles(): TodayTileSwitches {
        val defaults = TodayTileSwitches()
        return TodayTileSwitches(
            feelsLike = boolean(KEY_TILE_FEELS_LIKE_ENABLED) ?: defaults.feelsLike,
            wind = boolean(KEY_TILE_WIND_ENABLED) ?: defaults.wind,
            humidity = boolean(KEY_TILE_HUMIDITY_ENABLED) ?: defaults.humidity,
            pawCheck = boolean(KEY_TILE_PAW_CHECK_ENABLED) ?: defaults.pawCheck,
            uv = boolean(KEY_TILE_UV_ENABLED) ?: defaults.uv,
            daylight = boolean(KEY_TILE_DAYLIGHT_ENABLED) ?: defaults.daylight,
            sky = boolean(KEY_TILE_SKY_ENABLED) ?: defaults.sky,
        )
    }

    /**
     * Replacement OpenWeather API key, so a leaked key can be rotated without an app update.
     * Null when unset or blank; the key built into the app is used then. Not a secret: Remote
     * Config values reach every device.
     */
    fun openWeatherApiKey(): String? =
        value(KEY_OPENWEATHER_API_KEY)
            ?.let { runCatching { it.asString() }.getOrNull() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    /** The morning briefing. False skips posting it (the 8:30 run still plans the walk nudge). */
    fun dailyWeatherNotificationsEnabled(): Boolean =
        boolean(KEY_DAILY_WEATHER_NOTIFICATIONS_ENABLED) ?: DEFAULT_DAILY_WEATHER_NOTIFICATIONS_ENABLED

    /** The Walk time nudge. False stops planning and posting it. */
    fun walkTimeNotificationsEnabled(): Boolean = boolean(KEY_WALK_TIME_NOTIFICATIONS_ENABLED) ?: DEFAULT_WALK_TIME_NOTIFICATIONS_ENABLED

    /** Rain alerts. False stops posting them. */
    fun rainNotificationsEnabled(): Boolean = boolean(KEY_RAIN_NOTIFICATIONS_ENABLED) ?: DEFAULT_RAIN_NOTIFICATIONS_ENABLED

    /** Each kind of notification can be switched off for everyone; Settings then hides its switch. */
    internal fun notificationsEnabled(kind: NotificationKind): Boolean =
        when (kind) {
            NotificationKind.MORNING_BRIEFING -> dailyWeatherNotificationsEnabled()
            NotificationKind.WALK_TIME -> walkTimeNotificationsEnabled()
            NotificationKind.RAIN -> rainNotificationsEnabled()
        }

    /**
     * Minimum minutes between silent foreground-resume refreshes of the same city. 0 always
     * refreshes; values outside 0..1440 fall back to 0. Explicit refreshes are never throttled.
     */
    fun foregroundRefreshMinMinutes(): Long =
        long(KEY_FOREGROUND_REFRESH_MIN_MINUTES)
            ?.takeIf { it in 0L..MAX_FOREGROUND_REFRESH_MIN_MINUTES }
            ?: DEFAULT_FOREGROUND_REFRESH_MIN_MINUTES

    /**
     * Pet-mood and walk-advice thresholds (Celsius, whatever the user's units). Each value is
     * range-checked and each pair kept ordered by [PetWeatherThresholds.validated]; anything
     * invalid falls back to [PetWeatherThresholds.DEFAULT].
     */
    fun petWeatherThresholds(): PetWeatherThresholds =
        runCatching {
            PetWeatherThresholds.validated(
                hotPavementC = double(KEY_WALK_HOT_PAVEMENT_C),
                warmPavementC = double(KEY_WALK_WARM_PAVEMENT_C),
                icyPavementC = double(KEY_WALK_ICY_PAVEMENT_C),
                dangerousPavementC = double(KEY_WALK_DANGEROUS_PAVEMENT_C),
                maxRainChancePercent = wholeNumber(KEY_WALK_MAX_RAIN_CHANCE),
                comfortMinC = double(KEY_WALK_COMFORT_MIN_C),
                comfortMaxC = double(KEY_WALK_COMFORT_MAX_C),
                walkHoursStart = wholeNumber(KEY_WALK_HOURS_START),
                walkHoursEnd = wholeNumber(KEY_WALK_HOURS_END),
                petHotC = double(KEY_PET_HOT_THRESHOLD_C),
                petColdC = double(KEY_PET_COLD_THRESHOLD_C),
                badAirBand = wholeNumber(KEY_WALK_BAD_AIR_BAND),
            )
        }.getOrDefault(PetWeatherThresholds.DEFAULT)

    /**
     * Analytics kill switch. False drops every analytics event and turns Firebase Analytics
     * collection off on every install, without an app update.
     */
    fun analyticsEnabled(): Boolean = boolean(KEY_ANALYTICS_ENABLED) ?: DEFAULT_ANALYTICS_ENABLED

    /** Counts activations of newly fetched values, so callers can read Remote Config again. */
    val activationCount: Flow<Int> = activations

    /**
     * Below this version the app is blocked until it's updated; below the recommended one a banner
     * asks. Separate from iOS's keys. Null (unset or blank) never asks.
     */
    fun androidMinSupportedVersion(): String? = string(KEY_ANDROID_MIN_SUPPORTED_VERSION)

    fun androidMinRecommendedVersion(): String? = string(KEY_ANDROID_MIN_RECOMMENDED_VERSION)

    /** [analyticsEnabled], re-emitted whenever newly activated Remote Config values may have changed it. */
    val analyticsEnabledUpdates: Flow<Boolean> =
        activations.map { analyticsEnabled() }.distinctUntilChanged()

    private fun initRemoteConfig(): FirebaseRemoteConfig? {
        if (runCatching { FirebaseApp.getApps(context).isEmpty() }.getOrDefault(true)) {
            Timber.i("Firebase not initialised; using local Remote Config defaults")
            return null
        }
        return runCatching {
            Firebase.remoteConfig.also { rc ->
                rc.setConfigSettingsAsync(
                    remoteConfigSettings {
                        // Fetch new values at most every hour in production.
                        minimumFetchIntervalInSeconds = 3600
                    },
                )
                rc.setDefaultsAsync(R.xml.remote_config_defaults).addOnCompleteListener { onActivated() }
                rc.fetchAndActivate().addOnCompleteListener { task ->
                    if (!task.isSuccessful) {
                        // Network failure, throttling, etc.: keep whatever was already activated.
                        Timber.w(task.exception, "Remote Config fetch failed; keeping current values")
                    }
                    onActivated()
                }
            }
        }.onFailure { Timber.w(it, "Remote Config unavailable; using local defaults") }
            .getOrNull()
    }

    private fun onActivated() = activations.update { it + 1 }

    /**
     * Only values that actually exist (activated from the server or seeded from the in-app
     * defaults). A key with no value reports the STATIC source, where `asBoolean()` would
     * return `false` and `asLong()` `0` — on a cold first launch, before `setDefaultsAsync`
     * has landed, that would switch analytics off. Returning null falls back to the default.
     */
    private fun value(key: String): FirebaseRemoteConfigValue? =
        runCatching {
            remoteConfig?.getValue(key)?.takeIf { it.source != FirebaseRemoteConfig.VALUE_SOURCE_STATIC }
        }.getOrNull()

    private fun long(key: String): Long? = value(key)?.let { runCatching { it.asLong() }.getOrNull() }

    private fun double(key: String): Double? = value(key)?.let { runCatching { it.asDouble() }.getOrNull() }

    /** A NUMBER that must be a whole number ("7" or "7.0"); "7.5" or junk reads as missing. */
    private fun wholeNumber(key: String): Int? = double(key)?.takeIf { it.isFinite() && it == Math.floor(it) }?.toInt()

    private fun string(key: String): String? =
        value(key)
            ?.let { runCatching { it.asString() }.getOrNull() }
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    private fun boolean(key: String): Boolean? = value(key)?.let { runCatching { it.asBoolean() }.getOrNull() }

    companion object {
        const val KEY_REFRESH_INTERVAL = "weather_refresh_interval_minutes"
        const val DEFAULT_REFRESH_INTERVAL_MINUTES = 30L

        const val KEY_RADAR_FRAME_INTERVAL = "radar_frame_interval_ms"
        const val DEFAULT_RADAR_FRAME_INTERVAL_MS = 850L
        private const val MIN_RADAR_FRAME_INTERVAL_MS = 100L
        private const val MAX_RADAR_FRAME_INTERVAL_MS = 5000L

        const val KEY_DAILY_WEATHER_NOTIFICATIONS_ENABLED = "daily_weather_notifications_enabled"
        const val DEFAULT_DAILY_WEATHER_NOTIFICATIONS_ENABLED = true
        const val KEY_WALK_TIME_NOTIFICATIONS_ENABLED = "walk_time_notifications_enabled"
        const val DEFAULT_WALK_TIME_NOTIFICATIONS_ENABLED = true
        const val KEY_RAIN_NOTIFICATIONS_ENABLED = "rain_notifications_enabled"
        const val DEFAULT_RAIN_NOTIFICATIONS_ENABLED = true

        const val KEY_FOREGROUND_REFRESH_MIN_MINUTES = "foreground_refresh_min_minutes"
        const val DEFAULT_FOREGROUND_REFRESH_MIN_MINUTES = 0L
        private const val MAX_FOREGROUND_REFRESH_MIN_MINUTES = 1440L

        const val KEY_WALK_HOT_PAVEMENT_C = "walk_hot_pavement_c"
        const val KEY_WALK_WARM_PAVEMENT_C = "walk_warm_pavement_c"
        const val KEY_WALK_ICY_PAVEMENT_C = "walk_icy_pavement_c"
        const val KEY_WALK_DANGEROUS_PAVEMENT_C = "walk_dangerous_pavement_c"
        const val KEY_WALK_MAX_RAIN_CHANCE = "walk_max_rain_chance"
        const val KEY_WALK_COMFORT_MIN_C = "walk_comfort_min_c"
        const val KEY_WALK_COMFORT_MAX_C = "walk_comfort_max_c"
        const val KEY_WALK_HOURS_START = "walk_hours_start"
        const val KEY_WALK_HOURS_END = "walk_hours_end"
        const val KEY_PET_HOT_THRESHOLD_C = "pet_hot_threshold_c"
        const val KEY_PET_COLD_THRESHOLD_C = "pet_cold_threshold_c"
        const val KEY_WALK_BAD_AIR_BAND = "walk_bad_air_band"

        const val KEY_ANALYTICS_ENABLED = "analytics_enabled"
        const val DEFAULT_ANALYTICS_ENABLED = true

        const val KEY_RADAR_ENABLED = "radar_enabled"
        const val DEFAULT_RADAR_ENABLED = true

        const val KEY_WALK_CARD_ENABLED = "walk_card_enabled"
        const val DEFAULT_WALK_CARD_ENABLED = true

        const val KEY_OPEN_METEO_ENABLED = "open_meteo_enabled"

        const val KEY_AIR_QUALITY_ENABLED = "air_quality_enabled"
        const val KEY_TILE_FEELS_LIKE_ENABLED = "tile_feels_like_enabled"
        const val KEY_TILE_WIND_ENABLED = "tile_wind_enabled"
        const val KEY_TILE_HUMIDITY_ENABLED = "tile_humidity_enabled"
        const val KEY_TILE_PAW_CHECK_ENABLED = "tile_paw_check_enabled"
        const val KEY_TILE_UV_ENABLED = "tile_uv_enabled"
        const val KEY_TILE_DAYLIGHT_ENABLED = "tile_daylight_enabled"
        const val KEY_TILE_SKY_ENABLED = "tile_sky_enabled"
        const val KEY_SEASONAL_THEMES_ENABLED = "seasonal_themes_enabled"
        const val KEY_SEASONAL_THEMES = "seasonal_themes"
        const val DEFAULT_SEASONAL_THEMES_ENABLED = true
        const val DEFAULT_AIR_QUALITY_ENABLED = true

        const val KEY_OPENWEATHER_API_KEY = "openweather_api_key"

        const val KEY_ANDROID_MIN_SUPPORTED_VERSION = "android_min_supported_version"
        const val KEY_ANDROID_MIN_RECOMMENDED_VERSION = "android_min_recommended_version"
        const val DEFAULT_OPEN_METEO_ENABLED = true
    }
}
