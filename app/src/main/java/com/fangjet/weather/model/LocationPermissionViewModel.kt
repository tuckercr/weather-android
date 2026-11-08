package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.data.LocationRepository
import com.fangjet.weather.domain.SavedLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

sealed class LocationFetchState {
    object Idle : LocationFetchState()

    object Locating : LocationFetchState()

    data class Located(
        val location: SavedLocation,
    ) : LocationFetchState()

    object PermissionDenied : LocationFetchState()

    object Failed : LocationFetchState()
}

/**
 * The location steps a screen takes: onboarding's "Use my location", Add location's "Add My
 * Location" and Settings' request. The location itself and what's been allowed come from
 * [LocationRepository].
 */
@HiltViewModel
class LocationPermissionViewModel
    @Inject
    constructor(
        private val location: LocationRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LocationFetchState>(LocationFetchState.Idle)
        val state: StateFlow<LocationFetchState> = _state.asStateFlow()

        fun hasLocationPermission(): Boolean = location.hasLocationPermission()

        fun canRequestPermission(): Boolean = location.canRequestPermission()

        private val _myLocationOffered = MutableStateFlow(false)

        /** Whether "Add My Location" can do anything: access is on, or Android will still ask. */
        val myLocationOffered: StateFlow<Boolean> = _myLocationOffered.asStateFlow()

        /** Re-reads access: on every resume, since it changes in system settings. */
        fun refreshAccess() {
            _myLocationOffered.value = hasLocationPermission() || canRequestPermission()
        }

        /**
         * "Use my location": fetches the position when allowed (the outcome arrives in [state]), and
         * otherwise reports a denial when Android won't ask any more. True when the screen should show
         * the system dialog, whose answer goes to [onPermissionResult].
         */
        fun useMyLocation(): Boolean =
            when {
                hasLocationPermission() -> {
                    fetchLocation()
                    false
                }
                canRequestPermission() -> true
                else -> {
                    onPermissionDenied()
                    false
                }
            }

        /** Adding My Location from Add location is under way: its result goes to [myLocationAdded] or [addMyLocationFailed]. */
        private var adding = false
        private val added = Channel<SavedLocation>(Channel.BUFFERED)
        private val addFailures = Channel<Unit>(Channel.CONFLATED)

        /** My Location found for "Add My Location", once. */
        val myLocationAdded: Flow<SavedLocation> = added.receiveAsFlow()

        /** "Add My Location" found no position, for the screen to say so. */
        val addMyLocationFailed: Flow<Unit> = addFailures.receiveAsFlow()

        /** "Add My Location": as [useMyLocation], with the result as a one-off [myLocationAdded]. */
        fun addMyLocation(): Boolean {
            adding = true
            return useMyLocation().also { askFirst -> if (!askFirst && !hasLocationPermission()) adding = false }
        }

        /** Back to idle once a [LocationFetchState.Located] has been acted on, so it isn't acted on twice. */
        fun consumeLocated() {
            if (_state.value is LocationFetchState.Located) _state.value = LocationFetchState.Idle
        }

        /** The system dialog's answer: a grant fetches the position, a refusal is remembered. See [LocationRepository.permissionAnswered]. */
        fun onPermissionResult(
            granted: Boolean,
            rationaleBefore: Boolean,
            rationaleAfter: Boolean,
        ) {
            location.permissionAnswered(granted, rationaleBefore, rationaleAfter)
            refreshAccess()
            if (granted) {
                fetchLocation()
                return
            }
            adding = false
            onPermissionDenied()
        }

        fun onPermissionDenied() {
            _state.value = LocationFetchState.PermissionDenied
        }

        fun fetchLocation() {
            if (!hasLocationPermission()) {
                Timber.w("fetchLocation called but permission not granted")
                _state.value = LocationFetchState.PermissionDenied
                return
            }
            _state.value = LocationFetchState.Locating
            viewModelScope.launch {
                val here = location.knownPosition()
                when {
                    here == null -> {
                        Timber.w("fetchLocation: no position")
                        _state.value = LocationFetchState.Failed
                        if (adding) {
                            adding = false
                            addFailures.trySend(Unit)
                        }
                    }
                    adding -> {
                        adding = false
                        added.trySend(here)
                        _state.value = LocationFetchState.Idle
                    }
                    else -> _state.value = LocationFetchState.Located(here)
                }
            }
        }
    }
