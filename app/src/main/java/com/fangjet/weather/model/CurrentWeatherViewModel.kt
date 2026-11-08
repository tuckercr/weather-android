package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.domain.SavedLocation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

/** Where the phone is, for My Location. [LocationPermissionViewModel] provides it; tests fake it. */
interface DevicePositioning {
    fun hasLocationPermission(): Boolean

    /** The last known position, or a fresh fix without one: for the checks on returning to the app. */
    suspend fun knownPosition(): SavedLocation?

    /** A fresh fix, for pull to refresh. Null without permission or a fix. */
    suspend fun currentPosition(): SavedLocation?
}

/**
 * What the weather screen does, and in what order: refreshing the city on screen when it changes,
 * on returning to the app and every half hour, and moving My Location to where the phone is (or
 * adding it once location access turns up). The screen only tells it what happened. Made by the
 * screen with the activity's city list and weather view models, which outlive it. Same steps as
 * iOS's `CurrentWeatherViewModel`.
 */
class CurrentWeatherViewModel(
    private val cityList: CityListViewModel,
    private val weather: WeatherForecastViewModel,
    private val position: DevicePositioning,
) : ViewModel() {
    /** Location access at the last check, to spot it being granted later in system settings. */
    private var locationWasGranted: Boolean? = null

    /** A position is being read, so another check doesn't start one too. */
    private var locating = false

    private val pullLocating = MutableStateFlow(false)

    /** A fetch is under way, or pull to refresh is still waiting for the phone's position. */
    val isRefreshing: StateFlow<Boolean> =
        combine(weather.isRefreshing, pullLocating) { fetching, locating -> fetching || locating }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val failures = Channel<Unit>(Channel.CONFLATED)

    /** Adding My Location found no position, for the screen to say so. */
    val addMyLocationFailed: Flow<Unit> = failures.receiveAsFlow()

    // --- What happened on screen ---

    /**
     * The screen comes back to the foreground (and first shows): the clock moves on, the city on
     * screen is quietly refreshed, My Location follows the phone, and cities added by name get their
     * coordinates.
     */
    fun becameActive() {
        weather.tick()
        quietlyRefreshActiveCity()
        updateMyLocation()
        cityList.fillInMissingCoordinates()
    }

    /** Another city is on screen, including the first once the saved cities have loaded. */
    fun activeCityChanged() = refreshActiveCity()

    /** The retry buttons. */
    fun retry() = refreshActiveCity()

    /**
     * Pull to refresh. On My Location the phone's position is read first, whatever the 15-minute
     * limit, so My Location moves (by the usual 1 km rule) before its weather is fetched: a drive with
     * the screen on never resumes the app, so this is how to catch up by hand.
     */
    fun pulledToRefresh() {
        viewModelScope.launch {
            if (cityList.activeLocation.value?.isCurrentLocation == true) {
                pullLocating.value = true
                val here = position.currentPosition()
                pullLocating.value = false
                if (here != null) {
                    cityList.noteMyLocationChecked()
                    // A move changes the active city, which refreshes it.
                    if (cityList.updateCurrentLocation(here)) return@launch
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
            weather.tick()
            quietlyRefreshActiveCity()
        }
    }

    /**
     * While refreshes keep failing, the clock is re-read every few minutes so old weather gives way
     * to the forecast once it passes the cutoff. Run by the screen while there's an error.
     */
    suspend fun recheckWhileStale() {
        if (weather.staleErrorKey.value == null) return
        while (true) {
            delay(STALE_RECHECK_INTERVAL)
            weather.tick()
        }
    }

    // --- Steps ---

    private fun refreshActiveCity() {
        // A refresh moves "now" on too, so the hourly strip and captions update even when the
        // weather itself hasn't changed.
        val location = cityList.activeLocation.value
        if (location == null) {
            weather.tick()
            return
        }
        weather.refreshCurrent(location)
        weather.refreshForecast(location)
    }

    private fun quietlyRefreshActiveCity() {
        val location = cityList.activeLocation.value ?: return
        weather.backgroundRefreshCurrent(location)
        weather.backgroundRefreshForecast(location)
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
        val granted = position.hasLocationPermission()
        val newlyGranted = granted && locationWasGranted == false
        locationWasGranted = granted
        if (!granted || locating) return
        locating = true
        viewModelScope.launch {
            try {
                cityList.locationsLoaded.first { it }
                val adding = when {
                    hasMyLocation() -> if (cityList.claimMyLocationCheck()) false else return@launch
                    newlyGranted || cityList.locations.value.isEmpty() -> true
                    else -> return@launch
                }
                val here = position.knownPosition()
                when {
                    here == null -> if (adding) failures.trySend(Unit)
                    hasMyLocation() -> cityList.updateCurrentLocation(here)
                    // A position read only to move My Location, arriving after it was removed, adds nothing.
                    adding -> cityList.addLocation(here)
                }
            } finally {
                locating = false
            }
        }
    }

    private fun hasMyLocation() = cityList.locations.value.any { it.isCurrentLocation }

    companion object {
        /** The quiet refresh while the screen stays open, so a screen left on overnight keeps up. */
        val AUTO_REFRESH_INTERVAL = 30.minutes

        /** How often old weather is re-checked against the cutoff while refreshes keep failing. */
        val STALE_RECHECK_INTERVAL = 5.minutes
    }
}
