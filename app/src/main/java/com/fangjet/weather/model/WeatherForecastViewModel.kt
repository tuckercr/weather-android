package com.fangjet.weather.model

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.resolveWeatherUnits
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class WeatherForecastViewModel internal constructor(
    private val resolveWeatherUnits: suspend () -> WeatherUnits,
    private val fetchCurrentWeather: suspend (
        units: WeatherUnits,
        locationLabel: String,
        cityQuery: String?,
        latitude: Double?,
        longitude: Double?,
    ) -> Result<CurrentWeather>,
    private val fetchForecast: suspend (
        units: WeatherUnits,
        cityQuery: String?,
        latitude: Double?,
        longitude: Double?,
    ) -> Result<List<DayForecast>>,
    private val setLastCity: suspend (String) -> Unit,
    // Per-city cache: key is SavedLocation.cacheKey
    private val getCachedCurrentWeather: suspend (locationKey: String) -> CurrentWeather?,
    private val getCachedForecast: suspend (locationKey: String) -> List<DayForecast>?,
    private val setCachedCurrentWeather: suspend (locationKey: String, json: String) -> Unit,
    private val setCachedForecast: suspend (locationKey: String, json: String) -> Unit,
    private val json: Json,
    private val onError: (errorKey: String) -> Unit = {},
    private val onSettingsOpened: () -> Unit = {},
    isWalkCardEnabled: () -> Boolean = { true },
    isAirQualityEnabled: () -> Boolean = { true },
    readTodayTiles: () -> TodayTileSwitches = { TodayTileSwitches() },
    readPetWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    // Remote Config foreground_refresh_min_minutes, read on every foreground resume. 0 = never throttle.
    private val foregroundRefreshMinMinutes: () -> Long = { 0L },
    // Monotonic clock in milliseconds; injectable for tests. Fetch times only live in this process.
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
) : ViewModel() {

    @Inject
    constructor(
        application: Application,
        preferencesRepository: PreferencesRepository,
        weatherRepository: WeatherRepository,
        injectedJson: Json,
        analyticsRepository: AnalyticsRepository,
        remoteConfigRepository: RemoteConfigRepository,
    ) : this(
        resolveWeatherUnits = {
            val override = preferencesRepository.unitOverride.first()
            application.resolveWeatherUnits(override)
        },
        fetchCurrentWeather = weatherRepository::fetchCurrentWeather,
        fetchForecast = weatherRepository::fetchForecast,
        setLastCity = preferencesRepository::setLastCity,
        getCachedCurrentWeather = { key ->
            val raw = preferencesRepository.getCachedWeatherFor(key)
            if (raw != null) {
                try {
                    injectedJson.decodeFromString<CurrentWeather>(raw)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        },
        getCachedForecast = { key ->
            val raw = preferencesRepository.getCachedForecastFor(key)
            if (raw != null) {
                try {
                    injectedJson.decodeFromString<List<DayForecast>>(raw)
                } catch (_: Exception) {
                    null
                }
            } else {
                null
            }
        },
        setCachedCurrentWeather = { key, weatherJson ->
            preferencesRepository.setCachedWeatherFor(
                key,
                weatherJson,
            )
        },
        setCachedForecast = { key, forecastJson ->
            preferencesRepository.setCachedForecastFor(
                key,
                forecastJson,
            )
        },
        json = injectedJson,
        onError = { key -> analyticsRepository.logWeatherError(key) },
        onSettingsOpened = { analyticsRepository.logSettingsOpened() },
        isWalkCardEnabled = remoteConfigRepository::walkCardEnabled,
        isAirQualityEnabled = remoteConfigRepository::airQualityEnabled,
        readTodayTiles = remoteConfigRepository::todayTiles,
        readPetWeatherThresholds = remoteConfigRepository::petWeatherThresholds,
        foregroundRefreshMinMinutes = remoteConfigRepository::foregroundRefreshMinMinutes,
    )

    /** Remote Config `walk_card_enabled`: false hides the "time to walk the dog" card. */
    val walkCardEnabled: Boolean = isWalkCardEnabled()

    /**
     * Remote Config `air_quality_enabled`. The fetch checks it too; this hides a reading in a
     * forecast cached before it went off.
     */
    val airQualityEnabled: Boolean = isAirQualityEnabled()

    /** Remote Config `tile_*_enabled`: which Today tiles show. */
    val todayTiles: TodayTileSwitches = readTodayTiles()

    /** Remote Config pet-mood and walk-advice thresholds (validated; Celsius). */
    val petWeatherThresholds: PetWeatherThresholds = readPetWeatherThresholds()

    private val _currentWeather = MutableStateFlow<LoadingState<CurrentWeather>>(LoadingState.Idle)
    val currentWeather: StateFlow<LoadingState<CurrentWeather>> = _currentWeather.asStateFlow()

    private val _forecast = MutableStateFlow<LoadingState<List<DayForecast>>>(LoadingState.Idle)
    val forecast: StateFlow<LoadingState<List<DayForecast>>> = _forecast.asStateFlow()

    // Set when a refresh fails while weather is already on screen, so the screen can say the data is
    // old and why; null once a refresh succeeds or the user moves to another city.
    private val _staleErrorKey = MutableStateFlow<String?>(null)
    val staleErrorKey: StateFlow<String?> = _staleErrorKey.asStateFlow()

    private val currentRefreshing = MutableStateFlow(false)
    private val forecastRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> =
        combine(currentRefreshing, forecastRefreshing) { a, b -> a || b }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val currentWeatherFetchGeneration = AtomicInteger(0)
    private val forecastFetchGeneration = AtomicInteger(0)

    // The location targeted by the most recent *foreground* refresh, per stream. A silent background
    // refresh drops its result if this has since changed (the user switched cities), but it does NOT
    // share the foreground generation counters — otherwise a background refresh for the same location
    // would invalidate the in-flight foreground refresh and leave the spinner hanging.
    private val currentTargetKey = MutableStateFlow<String?>(null)
    private val forecastTargetKey = MutableStateFlow<String?>(null)

    // When each location's current weather / forecast was last successfully fetched and shown, by
    // SavedLocation.cacheKey. In memory only: used to throttle the silent foreground-resume refresh.
    private val lastCurrentFetchMillis = ConcurrentHashMap<String, Long>()
    private val lastForecastFetchMillis = ConcurrentHashMap<String, Long>()

    /** Silent background refresh (foreground return). Updates data if fetch succeeds; never shows loading or errors. */
    fun backgroundRefreshCurrent(location: SavedLocation) {
        if (fetchedRecently(lastCurrentFetchMillis, location)) return
        viewModelScope.launch {
            val units = resolveWeatherUnits()
            val label = if (location.isCurrentLocation) "" else location.label
            val result = fetchForLocation(location) { lat, lon ->
                fetchCurrentWeather(units, label, null, lat, lon)
            } ?: fetchCurrentWeather(units, label, location.label, null, null)
            // Drop stale results only when the user has since switched to a different location.
            val target = currentTargetKey.value
            if (target != null && target != location.cacheKey) return@launch
            result.onSuccess { weather ->
                lastCurrentFetchMillis[location.cacheKey] = nowMillis()
                _currentWeather.value = LoadingState.Success(weather)
                _staleErrorKey.value = null
                setLastCity(weather.cityName)
                viewModelScope.launch {
                    setCachedCurrentWeather(
                        location.cacheKey,
                        json.encodeToString(CurrentWeather.serializer(), weather),
                    )
                }
            }
            result.onFailure { e ->
                if (_currentWeather.value is LoadingState.Success) _staleErrorKey.value = resolveErrorKey(e)
            }
        }
    }

    fun backgroundRefreshForecast(location: SavedLocation) {
        if (fetchedRecently(lastForecastFetchMillis, location)) return
        viewModelScope.launch {
            val units = resolveWeatherUnits()
            val result = fetchForLocation(location) { lat, lon ->
                fetchForecast(units, null, lat, lon)
            } ?: fetchForecast(units, location.label, null, null)
            // Drop stale results only when the user has since switched to a different location.
            val target = forecastTargetKey.value
            if (target != null && target != location.cacheKey) return@launch
            result.onSuccess { forecast ->
                lastForecastFetchMillis[location.cacheKey] = nowMillis()
                _forecast.value = LoadingState.Success(forecast)
                viewModelScope.launch {
                    setCachedForecast(location.cacheKey, encodeForecasts(forecast))
                }
            }
        }
    }

    /**
     * Full refresh triggered by location change or pull-to-refresh.
     * Shows cached data immediately if available (no loading flash), then silently
     * replaces with fresh data. Shows an error only when there is no cached data to fall back to.
     */
    fun refreshCurrent(location: SavedLocation) {
        if (currentTargetKey.value != location.cacheKey) _staleErrorKey.value = null
        currentTargetKey.value = location.cacheKey
        val fetchId = currentWeatherFetchGeneration.incrementAndGet()
        currentRefreshing.value = true
        viewModelScope.launch {
            val cached = getCachedCurrentWeather(location.cacheKey)
            _currentWeather.value = cached?.let { LoadingState.Success(it) } ?: LoadingState.Loading

            val units = resolveWeatherUnits()
            val label = if (location.isCurrentLocation) "" else location.label
            val result = fetchForLocation(location) { lat, lon ->
                fetchCurrentWeather(units, label, null, lat, lon)
            } ?: fetchCurrentWeather(units, label, location.label, null, null)

            if (fetchId != currentWeatherFetchGeneration.get()) return@launch
            result.fold(
                onSuccess = { weather ->
                    lastCurrentFetchMillis[location.cacheKey] = nowMillis()
                    setLastCity(weather.cityName)
                    viewModelScope.launch {
                        setCachedCurrentWeather(
                            location.cacheKey,
                            json.encodeToString(CurrentWeather.serializer(), weather),
                        )
                    }
                    _currentWeather.value = LoadingState.Success(weather)
                    _staleErrorKey.value = null
                },
                onFailure = { e ->
                    val key = resolveErrorKey(e)
                    onError(key)
                    // Keep cached data visible if available, flagged as old; only surface the error
                    // on its own when there's nothing to show.
                    if (cached == null) {
                        _currentWeather.value = LoadingState.Error(key, canRetry = resolveCanRetry(e))
                    } else {
                        _staleErrorKey.value = key
                    }
                },
            )
            // Only clear the indicator if no newer request has since taken over.
            if (fetchId == currentWeatherFetchGeneration.get()) {
                currentRefreshing.value = false
            }
        }
    }

    fun refreshForecast(location: SavedLocation) {
        forecastTargetKey.value = location.cacheKey
        val fetchId = forecastFetchGeneration.incrementAndGet()
        forecastRefreshing.value = true
        viewModelScope.launch {
            val cached = getCachedForecast(location.cacheKey)
            _forecast.value = cached?.let { LoadingState.Success(it) } ?: LoadingState.Loading

            val units = resolveWeatherUnits()
            val result = fetchForLocation(location) { lat, lon ->
                fetchForecast(units, null, lat, lon)
            } ?: fetchForecast(units, location.label, null, null)

            if (fetchId != forecastFetchGeneration.get()) return@launch
            result.fold(
                onSuccess = { forecast ->
                    lastForecastFetchMillis[location.cacheKey] = nowMillis()
                    viewModelScope.launch {
                        setCachedForecast(location.cacheKey, encodeForecasts(forecast))
                    }
                    _forecast.value = LoadingState.Success(forecast)
                },
                onFailure = { e ->
                    val key = resolveErrorKey(e)
                    onError(key)
                    if (cached == null) {
                        _forecast.value = LoadingState.Error(key, canRetry = resolveCanRetry(e))
                    }
                },
            )
            if (fetchId == forecastFetchGeneration.get()) {
                forecastRefreshing.value = false
            }
        }
    }

    fun logSettingsOpened() = onSettingsOpened()

    /**
     * True when the silent foreground-resume refresh should be skipped: this location was
     * successfully fetched (by any refresh) less than `foreground_refresh_min_minutes` ago.
     * Explicit refreshes never call this.
     */
    private fun fetchedRecently(
        lastFetchMillis: Map<String, Long>,
        location: SavedLocation,
    ): Boolean {
        val minMinutes = runCatching { foregroundRefreshMinMinutes() }.getOrDefault(0L)
        if (minMinutes <= 0L) return false
        val last = lastFetchMillis[location.cacheKey] ?: return false
        return nowMillis() - last < minMinutes * MILLIS_PER_MINUTE
    }

    fun clearCurrentError() {
        _currentWeather.update { if (it is LoadingState.Error) LoadingState.Idle else it }
    }

    fun clearForecastError() {
        _forecast.update { if (it is LoadingState.Error) LoadingState.Idle else it }
    }

    private fun resolveErrorKey(error: Throwable): String =
        when (error.message) {
            "missing_api_key", "bad_api_key", "empty_query",
            "city_not_found", "rate_limited", "server_error", "offline",
            -> error.message!!
            else -> "generic"
        }

    private fun resolveCanRetry(error: Throwable): Boolean =
        when (error.message) {
            "missing_api_key", "bad_api_key", "city_not_found", "empty_query" -> false
            else -> true
        }

    private fun encodeForecasts(forecast: List<DayForecast>): String =
        json.encodeToString(
            kotlinx.serialization.builtins.ListSerializer(DayForecast.serializer()),
            forecast,
        )

    private inline fun <T> fetchForLocation(
        location: SavedLocation,
        block: (lat: Double, lon: Double) -> T,
    ): T? =
        if (location.latitude != null && location.longitude != null) {
            block(location.latitude, location.longitude)
        } else {
            null
        }
}

private const val MILLIS_PER_MINUTE = 60_000L
