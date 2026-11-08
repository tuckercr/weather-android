package com.fangjet.weather

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkManager
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.domain.notificationLocation
import com.fangjet.weather.ui.pets.PetMood
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.worker.NotificationKind
import com.fangjet.weather.worker.WalkTimeWorker
import com.fangjet.weather.worker.WeatherNotification
import com.fangjet.weather.worker.WeatherNotificationWorker
import com.fangjet.weather.worker.WeatherUpdateWorker
import com.fangjet.weather.worker.buildRainAlertContent
import com.fangjet.weather.worker.postWeatherNotification
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.time.ZonedDateTime

/**
 * Debug builds only: fires notifications from adb, without waiting for 8:30 or the weather.
 *
 * - `--es mode sample --es kind briefing|walk|rain|snow` posts made-up text through the real
 *   posting code: the look, the pets, the channel and the tap.
 * - `--es mode run --es kind briefing|walk|rain` runs the real worker now (`rain` is the background
 *   refresh). It follows the real rules, so walk and rain may post nothing.
 *
 * adb shell am broadcast -n com.fangjet.weather.debug/com.fangjet.weather.DebugNotifications --es mode sample --es kind rain
 */
class DebugNotifications : BroadcastReceiver() {
    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Dependencies {
        fun preferences(): PreferencesRepository

        fun json(): Json
    }

    override fun onReceive(
        context: Context,
        intent: Intent,
    ) {
        val mode = intent.getStringExtra("mode") ?: "sample"
        val kind = intent.getStringExtra("kind") ?: "briefing"
        // The samples follow "I have a dog", as the real ones do.
        val dependencies = EntryPointAccessors.fromApplication(context, Dependencies::class.java)
        val preferences = dependencies.preferences()
        val hasDog = runBlocking { preferences.showWalkCard.first() }
        // The notification city's name, as the real ones use it, from its cached weather.
        val city = runBlocking {
            val location = preferences.savedLocations.first().notificationLocation()
            location
                ?.let { preferences.getCachedWeather(it.cacheKey)?.cityName }
                ?: location?.label?.takeUnless { location.isCurrentLocation }
                ?: "Knoxville"
        }
        when (mode) {
            "sample" -> sample(kind, hasDog, city)?.let(context::postWeatherNotification)
            "run" -> worker(kind)?.let { WorkManager.getInstance(context).enqueue(OneTimeWorkRequest.from(it)) }
            else -> null
        } ?: Log.w(TAG, "Unknown mode \"$mode\" or kind \"$kind\"")
    }

    private fun sample(
        kind: String,
        hasDog: Boolean,
        city: String,
    ) = when (kind) {
        "briefing" -> WeatherNotification(
            NotificationKind.MORNING_BRIEFING,
            "52° and partly cloudy in $city",
            "High 70°, low 45°, 60% chance of rain." + if (hasDog) " Sunny's picked 1 PM for a walk." else "",
            PetMood.CLOUDY,
            southernHemisphere = false,
        )
        "walk" -> WeatherNotification(
            NotificationKind.WALK_TIME,
            "Time for a walk",
            "64° and clear in $city. Sunny's waiting by the door.",
            PetMood.SUNNY,
            southernHemisphere = false,
        )
        "rain", "snow" -> {
            val snow = kind == "snow"
            // An hour from now, on the phone's clock.
            val now = ZonedDateTime.now()
            val start = now.plusHours(1).toEpochSecond()
            val (title, body) =
                buildRainAlertContent(NextRain.Starting(start, snow), city, now.offset.totalSeconds, hasDog)
            WeatherNotification(
                NotificationKind.RAIN,
                title,
                body,
                if (snow) PetMood.SNOW else PetMood.RAIN,
                southernHemisphere = false,
            )
        }
        else -> null
    }

    private fun worker(kind: String): Class<out ListenableWorker>? =
        when (kind) {
            "briefing" -> WeatherNotificationWorker::class.java
            "walk" -> WalkTimeWorker::class.java
            "rain" -> WeatherUpdateWorker::class.java
            else -> null
        }

    private companion object {
        const val TAG = "DebugNotifications"
    }
}
