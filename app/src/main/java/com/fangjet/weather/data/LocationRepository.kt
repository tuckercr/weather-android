package com.fangjet.weather.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import com.fangjet.weather.domain.SavedLocation
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.suspendCancellableCoroutine
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Where the phone is, for My Location. [LocationPermissionViewModel] provides it; tests fake it. */
interface DevicePositioning {
    fun hasLocationPermission(): Boolean

    /** The last known position, or a fresh fix without one: for the checks on returning to the app. */
    suspend fun knownPosition(): SavedLocation?

    /** A fresh fix, for pull to refresh. Null without permission or a fix. */
    suspend fun currentPosition(): SavedLocation?
}

/**
 * The phone's location and what the app has been allowed: the one owner of Play services' location
 * client, the permission checks, and what earlier requests came back with (Android doesn't say once
 * it has stopped asking). The location screens' view models, the weather screen's and the
 * background runs all go through it. Same job as iOS's `LocationRepository`.
 */
@Singleton
class LocationRepository internal constructor(
    private val hasPermissionProvider: () -> Boolean,
    private val hasBackgroundPermissionProvider: () -> Boolean,
    private val fetcher: LocationFetcher,
    private val denials: LocationDenialStore,
    private val backgroundAnswers: BackgroundAnswerStore,
) : DevicePositioning {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        hasPermissionProvider = { context.hasLocationPermission() },
        hasBackgroundPermissionProvider = { context.hasBackgroundLocationPermission() },
        fetcher = PlayServicesLocationFetcher(context),
        denials = SharedPreferencesLocationDenialStore(context),
        backgroundAnswers = SharedPreferencesBackgroundAnswerStore(context),
    )

    override fun hasLocationPermission(): Boolean = hasPermissionProvider()

    /** "Allow all the time": location for background runs, which Follow my location needs. */
    fun hasBackgroundLocationPermission(): Boolean = hasBackgroundPermissionProvider()

    /**
     * Whether asking would still show the system dialog. Android doesn't say once it has stopped
     * (a second "Don't allow", or "Don't allow" chosen in system settings), so this goes on what
     * the earlier requests came back with; see [permissionAnswered].
     */
    fun canRequestPermission(): Boolean = !hasLocationPermission() && !denials.permanentlyDenied

    /**
     * The system dialog's answer. [rationaleBefore]/[rationaleAfter] are
     * `shouldShowRequestPermissionRationale` either side of the request: Android only shows the
     * dialog again after a refusal while it's true, so a refusal that leaves it false, following
     * an earlier refusal or one it was true for, means it won't ask any more. A dialog dismissed
     * without an answer leaves it false too, which is why a first refusal alone doesn't count.
     */
    fun permissionAnswered(
        granted: Boolean,
        rationaleBefore: Boolean,
        rationaleAfter: Boolean,
    ) {
        if (granted) {
            forgetRefusals()
            return
        }
        if (!rationaleAfter && (rationaleBefore || denials.deniedBefore)) denials.permanentlyDenied = true
        denials.deniedBefore = true
    }

    /**
     * The last position the phone has, or a fresh fix without one: for adding My Location and its
     * checks on returning to the app. Null without permission or a fix. Access granted after all
     * (in system settings, say) forgets any earlier refusal.
     */
    override suspend fun knownPosition(): SavedLocation? {
        if (!hasLocationPermission()) return null
        forgetRefusals()
        return runCatching { fetcher.tryLastLocation() ?: fetcher.tryCurrentLocation() }.getOrNull()?.toSavedLocation()
    }

    /**
     * The device's position now, waited for, for pull to refresh on My Location: a fresh fix (at most
     * 30 seconds old), not the last known one, which can be from anywhere. Null without permission
     * or a fix. Same as iOS's `currentPosition`.
     */
    override suspend fun currentPosition(): SavedLocation? {
        if (!hasLocationPermission()) return null
        return runCatching { fetcher.tryCurrentLocation() }.getOrNull()?.toSavedLocation()
    }

    /** For a background run (Follow my location): a fresh fix if there is one, otherwise the last known. */
    internal suspend fun backgroundPosition(): DeviceLocation? = fetcher.tryCurrentLocation() ?: fetcher.tryLastLocation()

    /**
     * How many times "Allow all the time" was asked for and not given. Android stops opening its page
     * after two, so Follow my location then opens the app's settings instead.
     */
    fun backgroundLocationRefusals(): Int = backgroundAnswers.refusals

    fun noteBackgroundLocationAnswer(granted: Boolean) {
        backgroundAnswers.refusals = if (granted) 0 else backgroundAnswers.refusals + 1
    }

    private fun forgetRefusals() {
        if (denials.deniedBefore || denials.permanentlyDenied) {
            denials.permanentlyDenied = false
            denials.deniedBefore = false
        }
    }

    private fun DeviceLocation.toSavedLocation() =
        SavedLocation(label = PlaceNaming.MY_LOCATION_LABEL, latitude = latitude, longitude = longitude, isCurrentLocation = true)
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

private const val LOCATION_PERMISSION_PREFS = "location_permission"
private const val KEY_BACKGROUND_REFUSALS = "background_refusals"

/** What earlier permission requests came back with, kept across launches. */
internal interface LocationDenialStore {
    var deniedBefore: Boolean
    var permanentlyDenied: Boolean
}

private class SharedPreferencesLocationDenialStore(
    context: Context,
) : LocationDenialStore {
    private val prefs = context.getSharedPreferences(LOCATION_PERMISSION_PREFS, Context.MODE_PRIVATE)

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

/** How many times "Allow all the time" was refused, kept across launches. */
internal interface BackgroundAnswerStore {
    var refusals: Int
}

private class SharedPreferencesBackgroundAnswerStore(
    context: Context,
) : BackgroundAnswerStore {
    private val prefs = context.getSharedPreferences(LOCATION_PERMISSION_PREFS, Context.MODE_PRIVATE)

    override var refusals: Int
        get() = prefs.getInt(KEY_BACKGROUND_REFUSALS, 0)
        set(value) = prefs.edit { putInt(KEY_BACKGROUND_REFUSALS, value) }
}
