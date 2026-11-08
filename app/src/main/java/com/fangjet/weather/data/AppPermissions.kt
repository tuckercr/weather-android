package com.fangjet.weather.data

import android.content.Context
import com.fangjet.weather.model.backgroundLocationRefusals
import com.fangjet.weather.model.hasBackgroundLocationPermission
import com.fangjet.weather.model.hasLocationPermission
import com.fangjet.weather.model.noteBackgroundLocationAnswer
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
    ) : AppPermissions {
        override fun location() = context.hasLocationPermission()

        override fun backgroundLocation() = context.hasBackgroundLocationPermission()

        override fun notifications() = context.canPostNotifications()

        override fun backgroundLocationRefusals() = context.backgroundLocationRefusals()

        override fun noteBackgroundLocationAnswer(granted: Boolean) = context.noteBackgroundLocationAnswer(granted)
    }
