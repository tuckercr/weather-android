package com.fangjet.weather.data

import android.content.Context
import android.location.Address
import android.location.Geocoder
import android.os.Build
import com.fangjet.weather.domain.MY_LOCATION_MOVE_METERS
import com.fangjet.weather.domain.distanceMeters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlin.coroutines.resume

/**
 * The phone's own reverse geocoding (Google's here, Apple's on iOS). The last name is kept and
 * reused until My Location moves [MY_LOCATION_MOVE_METERS], so the lookup isn't asked on every
 * refresh. Same rules as iOS's `Services/PlaceNames.swift`.
 */
class DevicePlaceNames(
    private val load: suspend () -> Remembered?,
    private val save: suspend (Remembered) -> Unit,
    private val lookUp: suspend (latitude: Double, longitude: Double) -> String?,
) : PlaceNaming {
    @Serializable
    data class Remembered(
        val name: String,
        val latitude: Double,
        val longitude: Double,
    )

    override suspend fun name(
        latitude: Double,
        longitude: Double,
    ): String? {
        load()?.let { remembered ->
            if (distanceMeters(remembered.latitude, remembered.longitude, latitude, longitude) < MY_LOCATION_MOVE_METERS) {
                return remembered.name
            }
        }
        val name = lookUp(latitude, longitude)?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        save(Remembered(name, latitude, longitude))
        return name
    }

    companion object {
        private const val LOOKUP_TIMEOUT_MS = 10_000L

        /**
         * The town, else the county, else the state: never a street address. Null on phones
         * without Google's lookup service, offline, or when it gives up.
         */
        suspend fun geocode(
            context: Context,
            latitude: Double,
            longitude: Double,
        ): String? {
            if (!Geocoder.isPresent()) return null
            val geocoder = Geocoder(context)
            val address = withTimeoutOrNull(LOOKUP_TIMEOUT_MS) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    suspendCancellableCoroutine { continuation ->
                        geocoder.getFromLocation(
                            latitude,
                            longitude,
                            1,
                            object : Geocoder.GeocodeListener {
                                override fun onGeocode(addresses: MutableList<Address>) {
                                    if (continuation.isActive) continuation.resume(addresses.firstOrNull())
                                }

                                override fun onError(errorMessage: String?) {
                                    if (continuation.isActive) continuation.resume(null)
                                }
                            },
                        )
                    }
                } else {
                    withContext(Dispatchers.IO) {
                        @Suppress("DEPRECATION")
                        runCatching { geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull() }.getOrNull()
                    }
                }
            }
            return listOf(address?.locality, address?.subAdminArea, address?.adminArea).firstOrNull { !it.isNullOrBlank() }
        }
    }
}
