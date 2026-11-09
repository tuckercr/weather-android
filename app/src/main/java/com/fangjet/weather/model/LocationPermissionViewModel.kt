package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.data.LocationRepository
import com.fangjet.weather.data.PermissionAnswer
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

        /**
         * "Use my location": fetches the position when allowed (the outcome arrives in [state]), and
         * otherwise returns true for the screen to make the system request, whose answer goes to
         * [onPermissionResult]. It asks even after a refusal that seemed final, since the user may
         * have picked "Ask every time" in system settings since and Android doesn't say: the
         * answer tells, and sends the user to the app's settings when Android won't ask.
         */
        fun useMyLocation(): Boolean {
            if (!hasLocationPermission()) return true
            fetchLocation()
            return false
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
            return useMyLocation()
        }

        /** Back to idle once a [LocationFetchState.Located] has been acted on, so it isn't acted on twice. */
        fun consumeLocated() {
            if (_state.value is LocationFetchState.Located) _state.value = LocationFetchState.Idle
        }

        private val settingsRequests = Channel<Unit>(Channel.CONFLATED)

        /** Android refused without asking: the screen opens the app's settings page, then calls [appSettingsOpened]. */
        val appSettingsRequested: Flow<Unit> = settingsRequests.receiveAsFlow()

        private enum class SettingsTrip { NONE, OPENED, AWAY }

        private var settingsTrip = SettingsTrip.NONE

        /** The app's settings page is opening, for the step under way to carry on when the user is back. */
        fun appSettingsOpened() {
            settingsTrip = SettingsTrip.OPENED
        }

        /**
         * The screen stopped: with the settings page opening, the user is now on it. The resume that
         * follows the system request's answer comes before this, so it doesn't count as the return.
         */
        fun leftApp() {
            if (settingsTrip == SettingsTrip.OPENED) settingsTrip = SettingsTrip.AWAY
        }

        /**
         * Re-reads access on every resume. Back from the app's settings page, access granted there
         * finishes the step under way (adding My Location, or onboarding's), and none is a refusal.
         * The location read forgets earlier refusals: see [LocationRepository.knownPosition].
         */
        fun refreshAccess() {
            if (settingsTrip != SettingsTrip.AWAY) return
            settingsTrip = SettingsTrip.NONE
            if (hasLocationPermission()) {
                fetchLocation()
            } else {
                adding = false
                onPermissionDenied()
            }
        }

        /** The system request is going out: see [LocationRepository.permissionRequested]. */
        fun permissionRequestStarted() {
            location.permissionRequested()
        }

        /**
         * The system request's answer: a grant fetches the position, a refusal is remembered, and a
         * refusal without a dialog sends the user to the app's settings. See [LocationRepository.permissionAnswered].
         */
        fun onPermissionResult(
            granted: Boolean,
            rationaleBefore: Boolean,
            rationaleAfter: Boolean,
        ) {
            when (location.permissionAnswered(granted, rationaleBefore, rationaleAfter)) {
                PermissionAnswer.GRANTED -> fetchLocation()
                PermissionAnswer.REFUSED -> {
                    adding = false
                    onPermissionDenied()
                }
                PermissionAnswer.REFUSED_WITHOUT_ASKING -> settingsRequests.trySend(Unit)
            }
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
