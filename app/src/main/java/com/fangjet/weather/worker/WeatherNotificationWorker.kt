package com.fangjet.weather.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.util.resolveWeatherUnits
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** The 8:30 AM run: posts the morning briefing and plans the day's walk-time nudge. */
@HiltWorker
class WeatherNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    preferencesRepository: PreferencesRepository,
    weatherRepository: WeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
    private val scheduler: WorkManagerScheduler,
) : CoroutineWorker(context, workerParams) {

    private val logic = NotificationWorkerLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        getUnits = {
            val override = preferencesRepository.unitOverride.first()
            context.resolveWeatherUnits(override)
        },
        fetchCurrentWeather = { units, label, lat, lon ->
            if (lat != null && lon != null) {
                weatherRepository.fetchCurrentWeather(units, label, latitude = lat, longitude = lon)
            } else {
                weatherRepository.fetchCurrentWeather(units, label, cityQuery = label)
            }
        },
        fetchForecast = { units, label, lat, lon ->
            if (lat != null && lon != null) {
                weatherRepository.fetchForecast(units, latitude = lat, longitude = lon)
            } else {
                weatherRepository.fetchForecast(units, cityQuery = label)
            }
        },
        hasNotificationPermission = context::canPostNotifications,
        postNotification = context::postWeatherNotification,
        isDailyWeatherNotificationEnabled = remoteConfigRepository::dailyWeatherNotificationsEnabled,
        isWalkTimeRemotelyEnabled = remoteConfigRepository::walkTimeNotificationsEnabled,
        petWeatherThresholds = remoteConfigRepository::petWeatherThresholds,
        isBriefingEnabled = { preferencesRepository.notifyMorningBriefing.first() },
        hasDog = { preferencesRepository.showWalkCard.first() },
        isWalkTimeEnabled = { preferencesRepository.notifyWalkTime.first() },
        scheduleWalkTime = scheduler::scheduleWalkTime,
    )

    // Tomorrow's run back on 8:30, however late this one was. Not on a retry: that would move the
    // retry itself to tomorrow.
    override suspend fun doWork(): Result {
        val result = logic.doWork()
        if (result !is Result.Retry) scheduler.keepBriefingAtHalfPastEight()
        return result
    }
}
