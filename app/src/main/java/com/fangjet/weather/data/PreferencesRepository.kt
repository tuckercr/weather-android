package com.fangjet.weather.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.domain.withMyLocationAt
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.weatherDataStore: DataStore<Preferences> by preferencesDataStore(name = "weather_prefs")

@Singleton
class PreferencesRepository internal constructor(
    // Injected directly by tests, which give each case its own file-backed store.
    private val dataStore: DataStore<Preferences>,
    private val json: Json,
) {
    @Inject constructor(
        @ApplicationContext context: Context,
        json: Json,
    ) : this(context.weatherDataStore, json)

    val hasSeenWelcome: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_HAS_SEEN_WELCOME] == true
    }

    val locationOnboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_LOCATION_ONBOARDING_DONE] == true
    }

    val notificationOnboardingDone: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFICATION_ONBOARDING_DONE] == true ||
            (prefs[KEY_NOTIFICATION_ONBOARDING_DONE] == null && prefs[KEY_LOCATION_ONBOARDING_DONE] == true)
    }

    val lastCity: Flow<String?> = dataStore.data.map { prefs ->
        prefs[KEY_LAST_CITY]?.takeIf { it.isNotBlank() }
    }

    val savedLocations: Flow<List<SavedLocation>> = dataStore.data.map { prefs ->
        val raw = prefs[KEY_SAVED_LOCATIONS] ?: return@map emptyList()
        runCatching { json.decodeFromString<List<SavedLocation>>(raw) }.getOrElse { emptyList() }
    }

    val activeLocationIndex: Flow<Int> = dataStore.data.map { prefs ->
        prefs[KEY_ACTIVE_LOCATION_INDEX] ?: 0
    }

    suspend fun setHasSeenWelcome(value: Boolean) {
        dataStore.edit { it[KEY_HAS_SEEN_WELCOME] = value }
    }

    suspend fun setLocationOnboardingDone() {
        dataStore.edit { it[KEY_LOCATION_ONBOARDING_DONE] = true }
    }

    suspend fun setNotificationOnboardingDone() {
        dataStore.edit { it[KEY_NOTIFICATION_ONBOARDING_DONE] = true }
    }

    suspend fun setLastCity(cityName: String) {
        dataStore.edit { it[KEY_LAST_CITY] = cityName }
    }

    suspend fun setSavedLocations(locations: List<SavedLocation>) {
        dataStore.edit { it[KEY_SAVED_LOCATIONS] = json.encodeToString(locations) }
    }

    /**
     * Moves My Location to [latitude], [longitude] in a single edit, after a real move
     * ([withMyLocationAt]). For background runs, which don't hold the app's copy of the list.
     */
    suspend fun moveMyLocation(
        latitude: Double,
        longitude: Double,
    ) {
        dataStore.edit { prefs ->
            val raw = prefs[KEY_SAVED_LOCATIONS] ?: return@edit
            val locations = runCatching { json.decodeFromString<List<SavedLocation>>(raw) }.getOrNull() ?: return@edit
            val moved = locations.withMyLocationAt(latitude, longitude) ?: return@edit
            prefs[KEY_SAVED_LOCATIONS] = json.encodeToString(moved)
        }
    }

    /**
     * "Follow my location" in Notification settings: background runs move My Location to where the
     * phone is first. It also needs "Allow all the time", which the switch asks for. Off until
     * turned on.
     */
    val followMyLocation: Flow<Boolean> = dataStore.data.map { it[KEY_FOLLOW_MY_LOCATION] == true }

    suspend fun setFollowMyLocation(enabled: Boolean) {
        dataStore.edit { it[KEY_FOLLOW_MY_LOCATION] = enabled }
    }

    suspend fun setActiveLocationIndex(index: Int) {
        dataStore.edit { it[KEY_ACTIVE_LOCATION_INDEX] = index }
    }

    suspend fun getCachedWeatherFor(locationKey: String): String? {
        val raw = dataStore.data.map { it[KEY_WEATHER_CACHE] }.first() ?: return null
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .getOrNull()
            ?.get(locationKey)
    }

    suspend fun setCachedWeatherFor(
        locationKey: String,
        weatherJson: String,
    ) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_WEATHER_CACHE]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?: emptyMap()
            prefs[KEY_WEATHER_CACHE] = json.encodeToString(existing + (locationKey to weatherJson))
        }
    }

    suspend fun getCachedForecastFor(locationKey: String): String? {
        val raw = dataStore.data.map { it[KEY_FORECAST_CACHE] }.first() ?: return null
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }
            .getOrNull()
            ?.get(locationKey)
    }

    suspend fun setCachedForecastFor(
        locationKey: String,
        forecastJson: String,
    ) {
        dataStore.edit { prefs ->
            val existing = prefs[KEY_FORECAST_CACHE]
                ?.let { runCatching { json.decodeFromString<Map<String, String>>(it) }.getOrNull() }
                ?: emptyMap()
            prefs[KEY_FORECAST_CACHE] =
                json.encodeToString(existing + (locationKey to forecastJson))
        }
    }

    val unitOverride: Flow<UnitOverride> = dataStore.data.map { prefs ->
        when (prefs[KEY_UNIT_OVERRIDE]) {
            UnitOverride.METRIC.name -> UnitOverride.METRIC
            UnitOverride.IMPERIAL.name -> UnitOverride.IMPERIAL
            else -> UnitOverride.SYSTEM
        }
    }

    suspend fun setUnitOverride(override: UnitOverride) {
        dataStore.edit { it[KEY_UNIT_OVERRIDE] = override.name }
    }

    /** "Share usage statistics" — the user's analytics opt-out. On unless they turn it off. */
    val shareUsageStatistics: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHARE_USAGE_STATISTICS] ?: true
    }

    suspend fun setShareUsageStatistics(enabled: Boolean) {
        dataStore.edit { it[KEY_SHARE_USAGE_STATISTICS] = enabled }
    }

    /** Whether the main screen shows "Best walk time". On unless they turn it off (no dog, say). */
    val showWalkCard: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_SHOW_WALK_CARD] ?: true
    }

    suspend fun setShowWalkCard(show: Boolean) {
        dataStore.edit { it[KEY_SHOW_WALK_CARD] = show }
    }

    /** When "Not now" was last tapped on the update banner, in epoch milliseconds. */
    val updateBannerDismissedAt: Flow<Long?> = dataStore.data.map { it[KEY_UPDATE_BANNER_DISMISSED_AT] }

    suspend fun setUpdateBannerDismissedAt(epochMillis: Long) {
        dataStore.edit { it[KEY_UPDATE_BANNER_DISMISSED_AT] = epochMillis }
    }

    /** The 8:30 AM weather briefing. On unless they turn it off. */
    val notifyMorningBriefing: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFY_MORNING_BRIEFING] ?: true
    }

    suspend fun setNotifyMorningBriefing(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_MORNING_BRIEFING] = enabled }
    }

    /**
     * A nudge at the day's best walk time. Until they choose, it follows the dog question (the walk
     * card's switch): on with a dog at home, off without.
     */
    val notifyWalkTime: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFY_WALK_TIME] ?: prefs[KEY_SHOW_WALK_CARD] ?: true
    }

    suspend fun setNotifyWalkTime(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_WALK_TIME] = enabled }
    }

    /** An alert when rain or snow is about to start. Off unless they turn it on. */
    val notifyRain: Flow<Boolean> = dataStore.data.map { prefs ->
        prefs[KEY_NOTIFY_RAIN] ?: false
    }

    suspend fun setNotifyRain(enabled: Boolean) {
        dataStore.edit { it[KEY_NOTIFY_RAIN] = enabled }
    }

    /** When the rain last alerted about starts, epoch seconds, so the same shower isn't alerted twice. */
    suspend fun lastRainAlertStart(): Long? = dataStore.data.first()[KEY_LAST_RAIN_ALERT_START]

    suspend fun setLastRainAlertStart(epochSeconds: Long) {
        dataStore.edit { it[KEY_LAST_RAIN_ALERT_START] = epochSeconds }
    }

    suspend fun clearCache() {
        dataStore.edit {
            it.remove(KEY_WEATHER_CACHE)
            it.remove(KEY_FORECAST_CACHE)
        }
    }

    suspend fun hasSeenWelcomeOnce(): Boolean = hasSeenWelcome.first()

    suspend fun locationOnboardingDoneOnce(): Boolean = locationOnboardingDone.first()

    suspend fun notificationOnboardingDoneOnce(): Boolean = notificationOnboardingDone.first()

    companion object {
        private val KEY_HAS_SEEN_WELCOME = booleanPreferencesKey("has_seen_welcome")
        private val KEY_LOCATION_ONBOARDING_DONE = booleanPreferencesKey("location_onboarding_done")
        private val KEY_NOTIFICATION_ONBOARDING_DONE = booleanPreferencesKey("notification_onboarding_done")
        private val KEY_LAST_CITY = stringPreferencesKey("last_city")
        private val KEY_SAVED_LOCATIONS = stringPreferencesKey("saved_locations")
        private val KEY_ACTIVE_LOCATION_INDEX = intPreferencesKey("active_location_index")
        private val KEY_WEATHER_CACHE = stringPreferencesKey("weather_cache")
        private val KEY_FORECAST_CACHE = stringPreferencesKey("forecast_cache")
        private val KEY_UNIT_OVERRIDE = stringPreferencesKey("unit_override")
        private val KEY_SHARE_USAGE_STATISTICS = booleanPreferencesKey("share_usage_statistics")
        private val KEY_SHOW_WALK_CARD = booleanPreferencesKey("show_walk_card")
        private val KEY_NOTIFY_MORNING_BRIEFING = booleanPreferencesKey("notify_morning_briefing")
        private val KEY_NOTIFY_WALK_TIME = booleanPreferencesKey("notify_walk_time")
        private val KEY_NOTIFY_RAIN = booleanPreferencesKey("notify_rain")
        private val KEY_FOLLOW_MY_LOCATION = booleanPreferencesKey("follow_my_location")
        private val KEY_LAST_RAIN_ALERT_START = longPreferencesKey("last_rain_alert_start")
        private val KEY_UPDATE_BANNER_DISMISSED_AT = longPreferencesKey("update_banner_dismissed_at")
    }
}
