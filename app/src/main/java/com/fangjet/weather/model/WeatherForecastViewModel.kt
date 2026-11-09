package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.data.CityRepository
import com.fangjet.weather.data.DevicePositioning
import com.fangjet.weather.data.LocationRepository
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
import com.fangjet.weather.domain.UnitOverride
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import javax.inject.Inject
import kotlin.time.Duration.Companion.minutes

/**
 * The weather screen: what it shows for the city on screen, and what it does and in what order.
 * It refreshes that city when it changes, on returning to the app and every half hour; moves My
 * Location to where the phone is (or adds it once location access turns up); and works out the
 * screen state from the cache and what comes back. The screen only tells it what happened. Same
 * as iOS's `WeatherForecastViewModel`.
 */
@HiltViewModel
class WeatherForecastViewModel internal constructor(
    // The saved locations' weather: the cache, and refreshes that fill it.
    private val repository: LocationWeatherRepository,
    // The saved cities and which one is on screen.
    private val cities: CityRepository,
    // Where the phone is, for My Location.
    private val position: DevicePositioning,
    private val onError: (errorKey: String) -> Unit = {},
    private val onSettingsOpened: () -> Unit = {},
    private val isWalkCardEnabled: () -> Boolean = { true },
    private val isHourlyForecastEnabled: () -> Boolean = { true },
    private val isNextRainEnabled: () -> Boolean = { true },
    private val isWeekForecastEnabled: () -> Boolean = { true },
    private val readRainOutlookSettings: () -> RainOutlookSettings = { RainOutlookSettings() },
    isTemperatureUnitsShown: () -> Boolean = { true },
    private val isAirQualityEnabled: () -> Boolean = { true },
    private val readTodayTiles: () -> TodayTileSwitches = { TodayTileSwitches() },
    private val readSeasonalThemes: () -> List<SeasonalTheme> = { emptyList() },
    private val readPetWeatherThresholds: () -> PetWeatherThresholds = { PetWeatherThresholds.DEFAULT },
    private val isRadarEnabled: () -> Boolean = { true },
    // Ticks each time newly fetched Remote Config values are activated, so the switches above are
    // read again and a fetch that lands while the app is open applies without a restart.
    remoteConfigActivations: Flow<Int> = flowOf(0),
    // Settings' Dog walking switch.
    showWalkCard: Flow<Boolean> = flowOf(true),
    // Settings' units: miles or kilometres follow it, or the device's region with System.
    unitOverride: Flow<UnitOverride> = flowOf(UnitOverride.SYSTEM),
    // Remote Config foreground_refresh_min_minutes, read on every foreground resume. 0 = never throttle.
    private val foregroundRefreshMinMinutes: () -> Long = { 0L },
    // Monotonic clock in milliseconds; injectable for tests. Fetch times only live in this process.
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    // The wall clock in epoch seconds, for the screen's "now"; injectable for tests.
    private val clock: () -> Long = { Instant.now().epochSecond },
) : ViewModel() {

    @Inject
    constructor(
        locationWeatherRepository: LocationWeatherRepository,
        cityRepository: CityRepository,
        locationRepository: LocationRepository,
        preferencesRepository: PreferencesRepository,
        analyticsRepository: AnalyticsRepository,
        remoteConfigRepository: RemoteConfigRepository,
    ) : this(
        repository = locationWeatherRepository,
        cities = cityRepository,
        position = locationRepository,
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
        remoteConfigActivations = remoteConfigRepository.activationCount,
        showWalkCard = preferencesRepository.showWalkCard,
        unitOverride = preferencesRepository.unitOverride,
        foregroundRefreshMinMinutes = remoteConfigRepository::foregroundRefreshMinMinutes,
    )

    /**
     * Remote Config `temperature_units_shown`: false shows temperatures as "89°", without the unit.
     * Read again on each activation.
     */
    val temperatureUnitsShown: StateFlow<Boolean> =
        remoteConfigActivations
            .map { isTemperatureUnitsShown() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, isTemperatureUnitsShown())

    /**
     * The Remote Config switches and settings the screen goes by, read again on each activation.
     * Air quality: the fetch checks `air_quality_enabled` too; this hides a reading in a forecast
     * cached before it went off.
     */
    val screenConfig: StateFlow<WeatherScreenConfig> =
        remoteConfigActivations
            .map { readScreenConfig() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, readScreenConfig())

    private fun readScreenConfig() =
        WeatherScreenConfig(
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

    /**
     * Settings' units, for miles or kilometres in Misty's View. The screen applies the device's
     * region with System, so a region change (which recreates the activity) applies at once.
     */
    val unitOverride: StateFlow<UnitOverride> =
        unitOverride.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitOverride.SYSTEM)

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
    private val pullLocating = MutableStateFlow(false)

    /** A fetch is under way, or pull to refresh is still waiting for the phone's position. */
    val isRefreshing: StateFlow<Boolean> =
        combine(currentRefreshing, forecastRefreshing, pullLocating) { current, forecast, locating -> current || forecast || locating }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** The saved cities, for the tabs. */
    val locations: StateFlow<List<SavedLocation>> = cities.locations

    /** The tab on screen. */
    val activeIndex: StateFlow<Int> = cities.activeIndex

    private val addFailures = Channel<Unit>(Channel.CONFLATED)

    /** Adding My Location found no position, for the screen to say so. */
    val addMyLocationFailed: Flow<Unit> = addFailures.receiveAsFlow()

    /** Location access at the last check, to spot it being granted later in system settings. */
    private var locationWasGranted: Boolean? = null

    /** A position is being read, so another check doesn't start one too. */
    private var locating = false

    /**
     * What the screen shows for the active city at the screen's clock: the reading (or the forecast
     * hour standing in for an old one), the hero, the walk verdict, the tiles and the sections the
     * settings switch on. Worked out again when any of those changes, Remote Config included.
     */
    val screen: StateFlow<WeatherScreen> =
        combine(
            combine(_currentWeather, _forecast, _staleErrorKey, ::Triple),
            _now,
            showWalkCard,
            screenConfig,
        ) { (current, forecast, stale), now, hasDog, config ->
            weatherScreen(current, forecast, stale, now, hasDog, config)
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

    // --- What happened on screen ---

    /**
     * The screen comes back to the foreground (and first shows): the clock moves on, the city on
     * screen is quietly refreshed, My Location follows the phone, and cities added by name get their
     * coordinates.
     */
    fun becameActive() {
        tick()
        quietlyRefreshActiveCity()
        updateMyLocation()
        cities.fillInMissingCoordinates()
    }

    /** A city tab was picked. */
    fun selectCity(index: Int) = cities.setActiveIndex(index)

    /** The retry buttons. */
    fun retry() = refreshActiveCity()

    /**
     * Pull to refresh. On My Location the phone's position is read first, whatever the 15-minute
     * limit, so My Location moves (by the usual 1 km rule) before its weather is fetched: a drive with
     * the screen on never resumes the app, so this is how to catch up by hand.
     */
    fun pulledToRefresh() {
        viewModelScope.launch {
            if (cities.activeLocation.value?.isCurrentLocation == true) {
                pullLocating.value = true
                val here = position.currentPosition()
                pullLocating.value = false
                if (here != null) {
                    cities.noteMyLocationChecked()
                    // A move changes the active city, which refreshes it.
                    if (cities.updateCurrentLocation(here)) return@launch
                }
            }
            refreshActiveCity()
        }
    }

    // --- While the screen is up ---

    /** The quiet refresh every half hour, run by the screen while it's resumed, until cancelled. */
    suspend fun refreshWhileActive() {
        while (true) {
            delay(AUTO_REFRESH_INTERVAL)
            tick()
            quietlyRefreshActiveCity()
        }
    }

    /**
     * While refreshes keep failing, the clock is re-read every few minutes so old weather gives way
     * to the forecast once it passes the cutoff. Run by the screen while there's an error.
     */
    suspend fun recheckWhileStale() {
        if (staleErrorKey.value == null) return
        while (true) {
            delay(STALE_RECHECK_INTERVAL)
            tick()
        }
    }

    // --- Steps ---

    private fun refreshActiveCity() {
        // A refresh moves "now" on too, so the hourly strip and captions update even when the
        // weather itself hasn't changed.
        val location = cities.activeLocation.value
        if (location == null) {
            tick()
            return
        }
        refreshCurrent(location)
        refreshForecast(location)
    }

    private fun quietlyRefreshActiveCity() {
        val location = cities.activeLocation.value ?: return
        backgroundRefreshCurrent(location)
        backgroundRefreshForecast(location)
    }

    /**
     * Each time the screen resumes, with location access on:
     * - re-reads the position while My Location is saved (at most every 15 minutes), so it follows
     *   the device rather than staying where it was added;
     * - otherwise adds it once access turns up after onboarding went without it (granted later in
     *   system settings, say). Only when there are no cities yet, or access has just changed to
     *   granted, so someone who removed My Location on purpose doesn't get it back every launch.
     *   It waits for the saved cities to load: before then the empty list would read as "none yet".
     */
    private fun updateMyLocation() {
        // While a read is under way this check is skipped whole, so a grant made meanwhile is still
        // "newly granted" next time.
        if (locating) return
        val granted = position.hasLocationPermission()
        val newlyGranted = granted && locationWasGranted == false
        locationWasGranted = granted
        if (!granted) return
        locating = true
        viewModelScope.launch {
            try {
                cities.locationsLoaded.first { it }
                val adding = when {
                    hasMyLocation() -> if (cities.claimMyLocationCheck()) false else return@launch
                    newlyGranted || cities.locations.value.isEmpty() -> true
                    else -> return@launch
                }
                val here = position.knownPosition()
                when {
                    here == null -> if (adding) addFailures.trySend(Unit)
                    hasMyLocation() -> cities.updateCurrentLocation(here)
                    // A position read only to move My Location, arriving after it was removed, adds nothing.
                    adding -> cities.addLocation(here)
                }
            } finally {
                locating = false
            }
        }
    }

    private fun hasMyLocation() = cities.locations.value.any { it.isCurrentLocation }

    // --- Fetching ---

    /** Silent background refresh (foreground return). Updates data if fetch succeeds; never shows loading or errors. */
    internal fun backgroundRefreshCurrent(location: SavedLocation) {
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

    internal fun backgroundRefreshForecast(location: SavedLocation) {
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
    internal fun refreshCurrent(location: SavedLocation) {
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

    internal fun refreshForecast(location: SavedLocation) {
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

    init {
        // Another city on screen (a tab, an added city, My Location moving, the saved cities
        // loading): a real refetch. Last in the class, so everything a refresh uses is set up.
        viewModelScope.launch { cities.activeLocation.filterNotNull().collect { refreshActiveCity() } }
    }

    companion object {
        /** The quiet refresh while the screen stays open, so a screen left on overnight keeps up. */
        val AUTO_REFRESH_INTERVAL = 30.minutes

        /** How often old weather is re-checked against the cutoff while refreshes keep failing. */
        val STALE_RECHECK_INTERVAL = 5.minutes
    }
}

private const val MILLIS_PER_MINUTE = 60_000L
