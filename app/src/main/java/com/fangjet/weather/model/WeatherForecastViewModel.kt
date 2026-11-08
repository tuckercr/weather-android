package com.fangjet.weather.model

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.SeasonalTheme
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.util.usesMilesForDistance
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject

@HiltViewModel
class WeatherForecastViewModel internal constructor(
    // The saved locations' weather: the cache, and refreshes that fill it.
    private val repository: LocationWeatherRepository,
    private val onError: (errorKey: String) -> Unit = {},
    private val onSettingsOpened: () -> Unit = {},
    isWalkCardEnabled: () -> Boolean = { true },
    isHourlyForecastEnabled: () -> Boolean = { true },
    isNextRainEnabled: () -> Boolean = { true },
    isWeekForecastEnabled: () -> Boolean = { true },
    readRainOutlookSettings: () -> RainOutlookSettings = { RainOutlookSettings() },
    isTemperatureUnitsShown: () -> Boolean = { true },
    isAirQualityEnabled: () -> Boolean = { true },
    readTodayTiles: () -> TodayTileSwitches = { TodayTileSwitches() },
    readSeasonalThemes: () -> List<SeasonalTheme> = { emptyList() },
    readPetWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    isRadarEnabled: () -> Boolean = { true },
    // Settings' Dog walking switch.
    showWalkCard: Flow<Boolean> = flowOf(true),
    // Visibility in miles rather than kilometres: the user's units, or the device's.
    distanceInMiles: Flow<Boolean> = flowOf(false),
    // Remote Config foreground_refresh_min_minutes, read on every foreground resume. 0 = never throttle.
    private val foregroundRefreshMinMinutes: () -> Long = { 0L },
    // Monotonic clock in milliseconds; injectable for tests. Fetch times only live in this process.
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    // The wall clock in epoch seconds, for the screen's "now"; injectable for tests.
    private val clock: () -> Long = { Instant.now().epochSecond },
) : ViewModel() {

    @Inject
    constructor(
        application: Application,
        locationWeatherRepository: LocationWeatherRepository,
        preferencesRepository: PreferencesRepository,
        analyticsRepository: AnalyticsRepository,
        remoteConfigRepository: RemoteConfigRepository,
    ) : this(
        repository = locationWeatherRepository,
        onError = { key -> analyticsRepository.logWeatherError(key) },
        onSettingsOpened = { analyticsRepository.logSettingsOpened() },
        isWalkCardEnabled = remoteConfigRepository::walkCardEnabled,
        isHourlyForecastEnabled = remoteConfigRepository::hourlyForecastEnabled,
        isNextRainEnabled = remoteConfigRepository::nextRainEnabled,
        isWeekForecastEnabled = remoteConfigRepository::weekForecastEnabled,
        readRainOutlookSettings = remoteConfigRepository::rainOutlookSettings,
        isTemperatureUnitsShown = remoteConfigRepository::temperatureUnitsShown,
        isAirQualityEnabled = remoteConfigRepository::airQualityEnabled,
        readTodayTiles = remoteConfigRepository::todayTiles,
        readSeasonalThemes = remoteConfigRepository::seasonalThemes,
        readPetWeatherThresholds = remoteConfigRepository::petWeatherThresholds,
        isRadarEnabled = remoteConfigRepository::radarEnabled,
        showWalkCard = preferencesRepository.showWalkCard,
        distanceInMiles = preferencesRepository.unitOverride.map(application::usesMilesForDistance),
        foregroundRefreshMinMinutes = remoteConfigRepository::foregroundRefreshMinMinutes,
    )

    /** Remote Config `temperature_units_shown`: false shows temperatures as "89°", without the unit. */
    val temperatureUnitsShown: Boolean = isTemperatureUnitsShown()

    /**
     * The Remote Config switches and settings the screen goes by, read once. Air quality: the fetch
     * checks `air_quality_enabled` too; this hides a reading in a forecast cached before it went off.
     */
    val screenConfig = WeatherScreenConfig(
        thresholds = readPetWeatherThresholds(),
        seasonalThemes = readSeasonalThemes(),
        nextRainEnabled = isNextRainEnabled(),
        rainOutlookSettings = readRainOutlookSettings(),
        airQualityEnabled = isAirQualityEnabled(),
        hourlyForecastEnabled = isHourlyForecastEnabled(),
        radarEnabled = isRadarEnabled(),
        weekForecastEnabled = isWeekForecastEnabled(),
        walkCardEnabled = isWalkCardEnabled(),
        todayTiles = readTodayTiles(),
    )

    /** Visibility in miles rather than kilometres, for Misty's View. */
    val distanceInMiles: StateFlow<Boolean> =
        distanceInMiles.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _now = MutableStateFlow(clock())

    /**
     * The screen's clock: what "now" is for everything shown. It moves on with [tick] (on refreshes,
     * returning to the app and a regular timer), so past hours drop off together.
     */
    val now: StateFlow<Long> = _now.asStateFlow()

    /** Moves the screen's clock on to the time now. */
    fun tick() {
        _now.value = clock()
    }

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

    /**
     * What the screen shows for the active city at the screen's clock: the reading (or the forecast
     * hour standing in for an old one), the hero, the walk verdict, the tiles and the sections the
     * settings switch on.
     */
    val screen: StateFlow<WeatherScreen> =
        combine(_currentWeather, _forecast, _staleErrorKey, _now, showWalkCard) { current, forecast, stale, now, hasDog ->
            weatherScreen(current, forecast, stale, now, hasDog, screenConfig)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, WeatherScreen.Loading)

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
            val result = repository.refreshWeather(location)
            // Drop stale results only when the user has since switched to a different location.
            val target = currentTargetKey.value
            if (target != null && target != location.cacheKey) return@launch
            result.onSuccess { weather ->
                lastCurrentFetchMillis[location.cacheKey] = nowMillis()
                _currentWeather.value = LoadingState.Success(weather)
                _staleErrorKey.value = null
                repository.rememberShownCity(weather.cityName)
            }
            result.onFailure { e ->
                if (_currentWeather.value is LoadingState.Success) _staleErrorKey.value = resolveErrorKey(e)
            }
        }
    }

    fun backgroundRefreshForecast(location: SavedLocation) {
        if (fetchedRecently(lastForecastFetchMillis, location)) return
        viewModelScope.launch {
            val result = repository.refreshForecast(location)
            // Drop stale results only when the user has since switched to a different location.
            val target = forecastTargetKey.value
            if (target != null && target != location.cacheKey) return@launch
            result.onSuccess { forecast ->
                lastForecastFetchMillis[location.cacheKey] = nowMillis()
                _forecast.value = LoadingState.Success(forecast)
            }
        }
    }

    /**
     * Full refresh triggered by location change or pull-to-refresh.
     * Shows cached data immediately if available (no loading flash), then silently
     * replaces with fresh data. Shows an error only when there is no cached data to fall back to.
     */
    fun refreshCurrent(location: SavedLocation) {
        // A refresh also moves "now" on, so the hourly strip and captions update even when the
        // weather itself hasn't changed.
        tick()
        if (currentTargetKey.value != location.cacheKey) _staleErrorKey.value = null
        currentTargetKey.value = location.cacheKey
        val fetchId = currentWeatherFetchGeneration.incrementAndGet()
        currentRefreshing.value = true
        viewModelScope.launch {
            val cached = repository.cachedWeather(location)
            _currentWeather.value = cached?.let { LoadingState.Success(it) } ?: LoadingState.Loading

            val result = repository.refreshWeather(location)

            if (fetchId != currentWeatherFetchGeneration.get()) return@launch
            result.fold(
                onSuccess = { weather ->
                    lastCurrentFetchMillis[location.cacheKey] = nowMillis()
                    repository.rememberShownCity(weather.cityName)
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
        tick()
        forecastTargetKey.value = location.cacheKey
        val fetchId = forecastFetchGeneration.incrementAndGet()
        forecastRefreshing.value = true
        viewModelScope.launch {
            val cached = repository.cachedForecast(location)
            _forecast.value = cached?.let { LoadingState.Success(it) } ?: LoadingState.Loading

            val result = repository.refreshForecast(location)

            if (fetchId != forecastFetchGeneration.get()) return@launch
            result.fold(
                onSuccess = { forecast ->
                    lastForecastFetchMillis[location.cacheKey] = nowMillis()
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
}

private const val MILLIS_PER_MINUTE = 60_000L
