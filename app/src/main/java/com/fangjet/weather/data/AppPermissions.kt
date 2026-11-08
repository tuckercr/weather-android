package com.fangjet.weather.data

import android.content.Context
import com.fangjet.weather.worker.canPostNotifications
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/**
 * What the app has been allowed, for Settings, and how often "Allow all the time" was refused:
 * Android stops opening its page after two. Read by [com.fangjet.weather.model.SettingsViewModel]
 * rather than the screens; faked in tests.
 */
interface AppPermissions {
    /** "While using the app" location, precise or approximate. */
    fun location(): Boolean

    /** "Allow all the time": location for background runs, which Follow my location needs. */
    fun backgroundLocation(): Boolean

    fun notifications(): Boolean

    fun backgroundLocationRefusals(): Int

    fun noteBackgroundLocationAnswer(granted: Boolean)
}

class DevicePermissions
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val locationRepository: LocationRepository,
    ) : AppPermissions {
        override fun location() = locationRepository.hasLocationPermission()

        override fun backgroundLocation() = locationRepository.hasBackgroundLocationPermission()

        override fun notifications() = context.canPostNotifications()

        override fun backgroundLocationRefusals() = locationRepository.backgroundLocationRefusals()

        override fun noteBackgroundLocationAnswer(granted: Boolean) = locationRepository.noteBackgroundLocationAnswer(granted)
    }
