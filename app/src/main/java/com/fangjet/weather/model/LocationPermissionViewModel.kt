package com.fangjet.weather.model

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.data.PlaceNaming
import com.fangjet.weather.domain.SavedLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import javax.inject.Inject
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

sealed class LocationFetchState {
    object Idle : LocationFetchState()

    object Locating : LocationFetchState()

    data class Located(
        val location: SavedLocation,
    ) : LocationFetchState()

    object PermissionDenied : LocationFetchState()

    object Failed : LocationFetchState()
}

@HiltViewModel
class LocationPermissionViewModel internal constructor(
    application: Application,
    private val hasLocationPermissionProvider: () -> Boolean,
    private val locationFetcher: LocationFetcher,
    private val denials: LocationDenialStore,
) : AndroidViewModel(application) {

    @Inject
    constructor(application: Application) : this(
        application = application,
        hasLocationPermissionProvider = { application.hasLocationPermission() },
        locationFetcher = PlayServicesLocationFetcher(application),
        denials = SharedPreferencesLocationDenialStore(application),
    )

    private val _state = MutableStateFlow<LocationFetchState>(LocationFetchState.Idle)
    val state: StateFlow<LocationFetchState> = _state.asStateFlow()

    fun hasLocationPermission(): Boolean = hasLocationPermissionProvider()

    /** Back to idle once a [LocationFetchState.Located] has been acted on, so it isn't acted on twice. */
    fun consumeLocated() {
        if (_state.value is LocationFetchState.Located) _state.value = LocationFetchState.Idle
    }

    /**
     * Whether asking would still show the system dialog. Android doesn't say once it has stopped
     * (a second "Don't allow", or "Don't allow" chosen in system settings), so this goes on what
     * the earlier requests came back with; see [onPermissionResult].
     */
    fun canRequestPermission(): Boolean = !hasLocationPermission() && !denials.permanentlyDenied

    /**
     * The system dialog's answer. [rationaleBefore]/[rationaleAfter] are
     * `shouldShowRequestPermissionRationale` either side of the request: Android only shows the
     * dialog again after a refusal while it's true, so a refusal that leaves it false, following
     * an earlier refusal or one it was true for, means it won't ask any more. A dialog dismissed
     * without an answer leaves it false too, which is why a first refusal alone doesn't count.
     */
    fun onPermissionResult(
        granted: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
    ) {
        if (granted) {
            denials.deniedBefore = false
            denials.permanentlyDenied = false
            fetchLocation()
            return
        }
        if (!rationaleAfter && (rationaleBefore || denials.deniedBefore)) denials.permanentlyDenied = true
        denials.deniedBefore = true
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
        // Granted after all, in system settings say: any earlier refusal no longer counts.
        if (denials.deniedBefore || denials.permanentlyDenied) {
            denials.permanentlyDenied = false
            denials.deniedBefore = false
        }
        Timber.d("fetchLocation: starting")
        _state.value = LocationFetchState.Locating
        viewModelScope.launch {
            try {
                Timber.d("fetchLocation: trying last known location")
                val location = locationFetcher.tryLastLocation().also {
                    Timber.d("fetchLocation: lastLocation = $it")
                } ?: locationFetcher.tryCurrentLocation().also {
                    Timber.d("fetchLocation: currentLocation = $it")
                }
                if (location != null) {
                    Timber.d("fetchLocation: success lat=${location.latitude} lon=${location.longitude}")
                    _state.value = LocationFetchState.Located(
                        SavedLocation(
                            label = PlaceNaming.MY_LOCATION_LABEL,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            isCurrentLocation = true,
                        ),
                    )
                } else {
                    Timber.w("fetchLocation: both last and current location returned null")
                    _state.value = LocationFetchState.Failed
                }
            } catch (e: Exception) {
                Timber.e(e, "fetchLocation: exception")
                _state.value = LocationFetchState.Failed
            }
        }
    }
}

internal data class DeviceLocation(
    val latitude: Double,
    val longitude: Double,
)

internal interface LocationFetcher {
    suspend fun tryLastLocation(): DeviceLocation?

    suspend fun tryCurrentLocation(): DeviceLocation?
}

/** The device's position from Play services. Also read by background runs ("Follow my location"). */
internal class PlayServicesLocationFetcher(
    context: Context,
) : LocationFetcher {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @Suppress("MissingPermission")
    override suspend fun tryLastLocation(): DeviceLocation? =
        suspendCancellableCoroutine { cont ->
            fusedClient.lastLocation
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                .addOnCanceledListener { cont.cancel() }
        }?.toDeviceLocation()

    @Suppress("MissingPermission")
    override suspend fun tryCurrentLocation(): DeviceLocation? =
        suspendCancellableCoroutine { cont ->
            val request = CurrentLocationRequest
                .Builder()
                .setPriority(Priority.PRIORITY_BALANCED_POWER_ACCURACY)
                .setMaxUpdateAgeMillis(30_000)
                .setDurationMillis(10_000)
                .build()
            fusedClient
                .getCurrentLocation(request, null)
                .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                .addOnFailureListener { e -> if (cont.isActive) cont.resumeWithException(e) }
                .addOnCanceledListener { cont.cancel() }
        }?.toDeviceLocation()
}

/** "While using the app" location, precise or approximate. */
internal fun Context.hasLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(
        this,
        Manifest.permission.ACCESS_FINE_LOCATION,
    ) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) ==
        PackageManager.PERMISSION_GRANTED

/** "Allow all the time": location for background runs, which "Follow my location" needs. */
internal fun Context.hasBackgroundLocationPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) ==
        PackageManager.PERMISSION_GRANTED

private fun android.location.Location.toDeviceLocation() =
    DeviceLocation(
        latitude = latitude,
        longitude = longitude,
    )

/**
 * How many times "Allow all the time" was asked for and not given. Android stops opening its page
 * after two, so Follow my location then opens the app's settings instead.
 */
internal fun Context.backgroundLocationRefusals(): Int =
    getSharedPreferences(LOCATION_PERMISSION_PREFS, Context.MODE_PRIVATE).getInt(KEY_BACKGROUND_REFUSALS, 0)

internal fun Context.noteBackgroundLocationAnswer(granted: Boolean) {
    getSharedPreferences(LOCATION_PERMISSION_PREFS, Context.MODE_PRIVATE).edit {
        putInt(KEY_BACKGROUND_REFUSALS, if (granted) 0 else backgroundLocationRefusals() + 1)
    }
}

private const val LOCATION_PERMISSION_PREFS = "location_permission"
private const val KEY_BACKGROUND_REFUSALS = "background_refusals"

/** What earlier permission requests came back with, kept across launches. */
internal interface LocationDenialStore {
    var deniedBefore: Boolean
    var permanentlyDenied: Boolean
}

private class SharedPreferencesLocationDenialStore(
    application: Application,
) : LocationDenialStore {
    private val prefs = application.getSharedPreferences(LOCATION_PERMISSION_PREFS, Context.MODE_PRIVATE)

    override var deniedBefore: Boolean
        get() = prefs.getBoolean(KEY_DENIED_BEFORE, false)
        set(value) = prefs.edit { putBoolean(KEY_DENIED_BEFORE, value) }

    override var permanentlyDenied: Boolean
        get() = prefs.getBoolean(KEY_PERMANENTLY_DENIED, false)
        set(value) = prefs.edit { putBoolean(KEY_PERMANENTLY_DENIED, value) }

    private companion object {
        const val KEY_DENIED_BEFORE = "denied_before"
        const val KEY_PERMANENTLY_DENIED = "permanently_denied"
    }
}
