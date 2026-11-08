package com.fangjet.weather.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.util.airNameOrPoorRes
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

/** The 8:30 AM run: posts the morning briefing and plans the day's walk-time nudge. */
@HiltWorker
class WeatherNotificationWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    preferencesRepository: PreferencesRepository,
    locationWeatherRepository: LocationWeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
    private val scheduler: WorkManagerScheduler,
) : CoroutineWorker(context, workerParams) {

    private val logic = NotificationWorkerLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        refreshWeather = locationWeatherRepository::refreshWeather,
        refreshForecast = locationWeatherRepository::refreshForecast,
        hasNotificationPermission = context::canPostNotifications,
        postNotification = context::postWeatherNotification,
        airName = { context.getString(airNameOrPoorRes(it)) },
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
    private val follower = myLocationFollower(context, preferencesRepository)

    override suspend fun doWork(): Result {
        // Where the phone is now, so the briefing isn't for the last place the app was opened.
        follower.follow()
        val result = logic.doWork()
        // Result.retry() equals any retry; its Retry class is internal to WorkManager (RestrictedApi).
        if (result != Result.retry()) scheduler.keepBriefingAtHalfPastEight()
        return result
    }
}
