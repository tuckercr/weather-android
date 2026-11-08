package com.fangjet.weather.worker

import android.app.NotificationManager
import android.content.Context
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import javax.inject.Inject

/**
 * One channel per notification type, so each can be silenced on its own in system settings. The
 * briefing channel is made at launch; Walk time only once there's a dog (the onboarding answer or
 * Settings), so Android's list doesn't offer it to people without one; Rain alerts only while
 * Remote Config has them on (otherwise the first alert makes it). A channel is never deleted:
 * turning the dog off later leaves it, with the user's choices for it.
 */
class NotificationChannels
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        private val preferencesRepository: PreferencesRepository,
        private val remoteConfigRepository: RemoteConfigRepository,
    ) {
        /** At launch, and again whenever the dog turns up, while [scope] lives. */
        fun start(scope: CoroutineScope) {
            // The 9 AM / 1 PM / 7 PM briefings' channel, replaced by these.
            context.getSystemService(NotificationManager::class.java).deleteNotificationChannel(LEGACY_CHANNEL_ID)
            val rainAlerts = remoteConfigRepository.rainNotificationsEnabled()
            channelsNeeded(hasDog = false, rainAlerts).forEach(context::createNotificationChannel)
            preferencesRepository.showWalkCard
                .filter { it }
                .onEach { channelsNeeded(hasDog = true, rainAlerts).forEach(context::createNotificationChannel) }
                .launchIn(scope)
        }

        companion object {
            /** The channels there should be: Walk time only with a dog, Rain alerts only while they're on. */
            internal fun channelsNeeded(
                hasDog: Boolean,
                rainAlerts: Boolean,
            ): List<NotificationKind> =
                NotificationKind.entries.filter {
                    (it != NotificationKind.WALK_TIME || hasDog) && (it != NotificationKind.RAIN || rainAlerts)
                }

            const val CHANNEL_MORNING_BRIEFING = "morning_briefing"
            const val CHANNEL_WALK_TIME = "walk_time"
            const val CHANNEL_RAIN = "rain_alerts"
            private const val LEGACY_CHANNEL_ID = "weather_daily"
        }
    }
