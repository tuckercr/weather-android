package com.fangjet.weather.worker

import android.content.Context
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.model.DeviceLocation
import com.fangjet.weather.model.PlayServicesLocationFetcher
import com.fangjet.weather.model.hasBackgroundLocationPermission
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import timber.log.Timber

/**
 * "Follow my location": before a background run reads the saved cities, moves My Location to where
 * the phone is now, after a real move ([com.fangjet.weather.domain.withMyLocationAt]). It needs the
 * switch on and "Allow all the time". Without them My Location only moves while the app is open,
 * so the briefing can be for the last place the app was opened. Same as iOS's `MyLocationFollower`.
 */
internal class MyLocationFollower(
    private val isOn: suspend () -> Boolean,
    private val hasBackgroundPermission: () -> Boolean,
    private val readLocation: suspend () -> DeviceLocation?,
    private val moveMyLocation: suspend (latitude: Double, longitude: Double) -> Unit,
) {
    /** Never fails the run: without a position, My Location stays where it was. */
    suspend fun follow() {
        if (!isOn() || !hasBackgroundPermission()) return
        val here = try {
            readLocation()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "couldn't read the location")
            null
        } ?: return
        moveMyLocation(here.latitude, here.longitude)
    }
}

internal fun myLocationFollower(
    context: Context,
    preferencesRepository: PreferencesRepository,
): MyLocationFollower {
    // Only made when there's a position to read: most runs never need Play services' client.
    val fetcher by lazy { PlayServicesLocationFetcher(context) }
    return MyLocationFollower(
        isOn = { preferencesRepository.followMyLocation.first() },
        hasBackgroundPermission = context::hasBackgroundLocationPermission,
        // A recent fix if there is one, otherwise a fresh one (up to 10 seconds), then the last known.
        readLocation = { fetcher.tryCurrentLocation() ?: fetcher.tryLastLocation() },
        moveMyLocation = preferencesRepository::moveMyLocation,
    )
}
