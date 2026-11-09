package com.fangjet.weather.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.util.asStrings
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Runs once at the walk time the morning run picked, and nudges if it's still a good time. */
@HiltWorker
class WalkTimeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    preferencesRepository: PreferencesRepository,
    locationWeatherRepository: LocationWeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
) : CoroutineWorker(context, workerParams) {

    private val logic = WalkTimeLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        refreshWeather = locationWeatherRepository::refreshWeather,
        refreshForecast = locationWeatherRepository::refreshForecast,
        hasNotificationPermission = context::canPostNotifications,
        postNotification = context::postWeatherNotification,
        strings = context.resources.asStrings(),
        // Checked again when it fires: the dog, or Remote Config, may have turned it off since.
        isWalkTimeEnabled = {
            remoteConfigRepository.walkTimeNotificationsEnabled() &&
                preferencesRepository.notifyWalkTime.first() &&
                preferencesRepository.showWalkCard.first()
        },
        petWeatherThresholds = remoteConfigRepository::petWeatherThresholds,
    )

    override suspend fun doWork() = logic.doWork()
}
