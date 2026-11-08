package com.fangjet.weather.data

import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.withMyLocationAt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The saved cities and which one is on screen: read once from storage, then kept here, with every
 * change written back. Adding a city by name geocodes it; My Location moves after a real move, by
 * the app or by Follow my location in the background. The one copy every screen's view model reads,
 * so Settings, Manage Locations and the weather screen always agree. Same job as iOS's
 * `CityRepository`.
 */
@Singleton
class CityRepository internal constructor(
    private val preferencesRepository: PreferencesRepository,
    private val geocodingRepository: GeocodingRepository,
    private val onCityAdded: () -> Unit,
    // Outlives the screens: a city being added or geocoded is saved even if the screen goes.
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate),
) {
    @Inject
    constructor(
        preferencesRepository: PreferencesRepository,
        geocodingRepository: GeocodingRepository,
        analyticsRepository: AnalyticsRepository,
    ) : this(
        preferencesRepository = preferencesRepository,
        geocodingRepository = geocodingRepository,
        onCityAdded = analyticsRepository::logCityAdded,
    )

    private val _locations = MutableStateFlow<List<SavedLocation>>(emptyList())
    val locations: StateFlow<List<SavedLocation>> = _locations.asStateFlow()

    /** False until the saved cities have been read: an empty [locations] before then means nothing. */
    private val _locationsLoaded = MutableStateFlow(false)
    val locationsLoaded: StateFlow<Boolean> = _locationsLoaded.asStateFlow()

    private val _activeIndex = MutableStateFlow(0)
    val activeIndex: StateFlow<Int> = _activeIndex.asStateFlow()

    val activeLocation: StateFlow<SavedLocation?> = combine(_locations, _activeIndex) { locs, idx ->
        locs.getOrNull(idx)
    }.stateIn(scope, SharingStarted.Eagerly, null)

    init {
        scope.launch {
            var locs = preferencesRepository.savedLocations.first()
            val idx = preferencesRepository.activeLocationIndex.first()

            // Migrate from legacy single-city storage
            if (locs.isEmpty()) {
                val lastCity = preferencesRepository.lastCity.first()
                if (!lastCity.isNullOrBlank()) {
                    locs = listOf(SavedLocation(label = lastCity, latitude = null, longitude = null))
                    preferencesRepository.setSavedLocations(locs)
                }
            }

            _locations.value = locs
            _activeIndex.value = idx.coerceIn(0, (locs.size - 1).coerceAtLeast(0))
            _locationsLoaded.value = true
            fillInMissingCoordinates()
            // Follow my location (and the background workers) move My Location in storage, even
            // while the app is open: take the move up, so the screen follows.
            preferencesRepository.savedLocations.collect(::followStoredMyLocation)
        }
    }

    /** Labels being geocoded now, so launch and a quick return to the app don't look one up twice. */
    private val geocodingLabels = mutableSetOf<String>()

    /**
     * Geocodes every name-only city. Runs on launch and whenever the app comes back, so a city whose
     * lookup failed when it was added (offline, say) still gets its radar later. The entered label
     * is kept so the duplicate check in [addLocation] still matches it.
     */
    fun fillInMissingCoordinates() {
        _locations.value
            .filter { it.latitude == null || it.longitude == null }
            .forEach { location ->
                val label = location.label
                if (!geocodingLabels.add(label)) return@forEach
                scope.launch {
                    try {
                        val suggestion = geocodingRepository.searchCities(label).getOrNull()?.firstOrNull()
                            ?: return@launch
                        // The list may have changed while geocoding was in flight.
                        val index = _locations.value.indexOfFirst { it.label == label && it.latitude == null }
                        if (index < 0) return@launch
                        val updated = _locations.value.toMutableList()
                        updated[index] = updated[index].copy(
                            latitude = suggestion.weatherLat,
                            longitude = suggestion.weatherLon,
                        )
                        _locations.value = updated
                        preferencesRepository.setSavedLocations(updated)
                    } finally {
                        geocodingLabels.remove(label)
                    }
                }
            }
    }

    fun addLocation(location: SavedLocation) {
        // Switch to an existing identical entry rather than duplicating
        val existingIdx = _locations.value.indexOfFirst { it.label == location.label }
        if (existingIdx >= 0) {
            setActiveIndex(existingIdx)
            return
        }
        scope.launch {
            val locationToAdd = if (location.latitude == null || location.longitude == null) {
                // Geocode location by name to get coordinates
                geocodingRepository
                    .searchCities(location.label)
                    .getOrNull()
                    ?.firstOrNull()
                    ?.let { suggestion ->
                        location.copy(
                            latitude = suggestion.weatherLat,
                            longitude = suggestion.weatherLon,
                        )
                    } ?: location
            } else {
                location
            }

            val updated = _locations.value + locationToAdd
            val newIdx = updated.size - 1
            _locations.value = updated
            _activeIndex.value = newIdx
            onCityAdded()
            preferencesRepository.setSavedLocations(updated)
            preferencesRepository.setActiveLocationIndex(newIdx)
        }
    }

    /** Moves My Location to where the device is now, after a real move ([withMyLocationAt]). True when it moved. */
    fun updateCurrentLocation(location: SavedLocation): Boolean {
        val latitude = location.latitude ?: return false
        val longitude = location.longitude ?: return false
        val updated = _locations.value.withMyLocationAt(latitude, longitude) ?: return false
        _locations.value = updated
        scope.launch { preferencesRepository.setSavedLocations(updated) }
        return true
    }

    /**
     * Takes up My Location's position as stored, where Follow my location moves it, so a move made
     * while the app is open shows at once. Same as iOS's `followStoredMyLocation`.
     */
    internal fun followStoredMyLocation(stored: List<SavedLocation>) {
        val storedMine = stored.firstOrNull { it.isCurrentLocation } ?: return
        val index = _locations.value.indexOfFirst { it.isCurrentLocation }
        if (index < 0 || _locations.value[index] == storedMine) return
        _locations.value = _locations.value.toMutableList().apply { set(index, storedMine) }
    }

    private var myLocationCheckedAt: Long? = null

    /**
     * Whether it's time to check whether My Location has moved, and if so, notes the time: at
     * most every [MY_LOCATION_CHECK_INTERVAL_MS], so switching in and out of the app doesn't fetch
     * the position each time. Adding My Location doesn't ask; it always fetches.
     */
    fun claimMyLocationCheck(nowMillis: Long = System.currentTimeMillis()): Boolean {
        val last = myLocationCheckedAt
        if (last != null && nowMillis - last < MY_LOCATION_CHECK_INTERVAL_MS) return false
        myLocationCheckedAt = nowMillis
        return true
    }

    /**
     * A check made anyway (pull to refresh asks whatever the interval), so returning to the app
     * straight after doesn't make another.
     */
    fun noteMyLocationChecked(nowMillis: Long = System.currentTimeMillis()) {
        myLocationCheckedAt = nowMillis
    }

    fun removeLocation(index: Int) {
        val updated = _locations.value.toMutableList().apply { removeAt(index) }
        val newIdx = _activeIndex.value.coerceIn(0, (updated.size - 1).coerceAtLeast(0))
        _locations.value = updated
        _activeIndex.value = newIdx
        scope.launch {
            preferencesRepository.setSavedLocations(updated)
            preferencesRepository.setActiveLocationIndex(newIdx)
        }
    }

    fun setActiveIndex(index: Int) {
        if (index == _activeIndex.value) return
        _activeIndex.value = index
        scope.launch {
            preferencesRepository.setActiveLocationIndex(index)
        }
    }

    fun reorderLocations(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val updated = _locations.value.toMutableList()
        val item = updated.removeAt(fromIndex)
        updated.add(toIndex, item)

        // Update active index if needed
        val newActiveIndex = when {
            _activeIndex.value == fromIndex -> toIndex
            fromIndex < _activeIndex.value && toIndex >= _activeIndex.value -> _activeIndex.value - 1
            fromIndex > _activeIndex.value && toIndex <= _activeIndex.value -> _activeIndex.value + 1
            else -> _activeIndex.value
        }

        _locations.value = updated
        _activeIndex.value = newActiveIndex
        scope.launch {
            preferencesRepository.setSavedLocations(updated)
            preferencesRepository.setActiveLocationIndex(newActiveIndex)
        }
    }
}

internal const val MY_LOCATION_CHECK_INTERVAL_MS = 15 * 60 * 1000L
