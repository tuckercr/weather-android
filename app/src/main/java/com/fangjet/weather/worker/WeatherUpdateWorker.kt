package com.fangjet.weather.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    preferencesRepository: PreferencesRepository,
    locationWeatherRepository: LocationWeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
) : CoroutineWorker(context, workerParams) {

    private val rainAlerts = RainAlertLogic(
        isEnabled = {
            remoteConfigRepository.rainNotificationsEnabled() &&
                preferencesRepository.notifyRain.first() &&
                context.canPostNotifications()
        },
        hasDog = { preferencesRepository.showWalkCard.first() },
        lastAlertStart = { preferencesRepository.lastRainAlertStart() },
        setLastAlertStart = preferencesRepository::setLastRainAlertStart,
        postNotification = context::postWeatherNotification,
    )

    private val logic = UpdateWorkerLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        getActiveIndex = { preferencesRepository.activeLocationIndex.first() },
        refreshWeather = locationWeatherRepository::refreshWeather,
        refreshForecast = locationWeatherRepository::refreshForecast,
        afterRefresh = rainAlerts::check,
    )

    private val follower = myLocationFollower(context, preferencesRepository)

    override suspend fun doWork(): Result {
        follower.follow()
        return logic.doWork()
    }
}
