package com.fangjet.weather.data

/**
 * Names My Location ("Knoxville") for the hero and notifications, which Open-Meteo's reading
 * doesn't. The app's `DevicePlaceNames` asks the phone. Same rules as iOS's `Services/PlaceNames.swift`.
 */
fun interface PlaceNaming {
    /** Null when the lookup finds nothing or fails (offline, or asked too often). */
    suspend fun name(
        latitude: Double,
        longitude: Double,
    ): String?

    companion object {
        /** My Location's label, and its name in the hero when the phone can't name the place. */
        const val MY_LOCATION_LABEL = "My Location"

        /** No lookup, so My Location reads [MY_LOCATION_LABEL]. */
        val None = PlaceNaming { _, _ -> null }
    }
}
