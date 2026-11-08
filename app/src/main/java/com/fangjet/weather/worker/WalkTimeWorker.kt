package com.fangjet.weather.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.data.fetchCurrentWeather
import com.fangjet.weather.data.fetchForecast
import com.fangjet.weather.util.resolveWeatherUnits
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** Runs once at the walk time the morning run picked, and nudges if it's still a good time. */
@HiltWorker
class WalkTimeWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    preferencesRepository: PreferencesRepository,
    weatherRepository: WeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
) : CoroutineWorker(context, workerParams) {

    private val logic = WalkTimeLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        getUnits = {
            val override = preferencesRepository.unitOverride.first()
            context.resolveWeatherUnits(override)
        },
        fetchCurrentWeather = { units, location -> weatherRepository.fetchCurrentWeather(location, units) },
        fetchForecast = { units, location -> weatherRepository.fetchForecast(location, units) },
        hasNotificationPermission = context::canPostNotifications,
        postNotification = context::postWeatherNotification,
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
