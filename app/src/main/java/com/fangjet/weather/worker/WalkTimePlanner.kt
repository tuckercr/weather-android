package com.fangjet.weather.worker

import android.content.Context
import com.fangjet.weather.data.LocationWeatherRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Plans today's walk nudge straight away when Walk time or the dog is turned on (Settings, or the
 * onboarding dog question), rather than at the next 8:30 run. The rules are [WalkTimeTodayLogic]'s.
 */
@Singleton
class WalkTimePlanner @Inject constructor(
    @ApplicationContext context: Context,
    preferencesRepository: PreferencesRepository,
    locationWeatherRepository: LocationWeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
    scheduler: WorkManagerScheduler,
) {
    private val logic = WalkTimeTodayLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        cachedWeather = locationWeatherRepository::cachedWeather,
        cachedForecast = locationWeatherRepository::cachedForecast,
        hasNotificationPermission = context::canPostNotifications,
        isWalkTimeRemotelyEnabled = remoteConfigRepository::walkTimeNotificationsEnabled,
        isWalkTimeEnabled = { preferencesRepository.notifyWalkTime.first() },
        hasDog = { preferencesRepository.showWalkCard.first() },
        lastWalkNudgeAt = preferencesRepository::lastWalkNudgeAt,
        petWeatherThresholds = remoteConfigRepository::petWeatherThresholds,
        scheduleWalkTime = scheduler::scheduleWalkTime,
    )

    /** Today's walk nudge, if it's still to come; call after saving the change. */
    suspend fun planToday() = logic.plan()
}
