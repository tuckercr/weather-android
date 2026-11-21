package com.fangjet.weather.analytics

import com.fangjet.weather.data.RemoteConfigRepository
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's analytics events. The whole catalogue is the `log…` functions below, so the
 * privacy policy can be checked against the code line by line.
 *
 * Gates, in order — an event is sent only if every one passes:
 *  1. Remote Config kill switch `analytics_enabled` — false drops every event on every
 *     install, without an app update ([RemoteConfigRepository.analyticsEnabled]).
 *  2. The user's own "Share usage statistics" setting (Settings → About). Also enforced at
 *     the SDK level by [AnalyticsCollectionController], so Firebase's automatic events stop too.
 *  3. Firebase itself: without `google-services.json` the backend is a no-op.
 *
 * Every call is wrapped: analytics must never be able to crash a feature.
 */
@Singleton
class AnalyticsRepository @Inject constructor(
    private val backend: AnalyticsBackend,
    private val remoteConfig: RemoteConfigRepository,
    private val collection: AnalyticsCollectionController,
) {

    fun logOnboardingComplete() = log(EVENT_ONBOARDING_COMPLETE)

    /**fun logCityAdded() = log(EVENT_CITY_ADDED)*/

    fun logCityAdded(source: String, provider: String) =
        log(
            event = EVENT_CITY_ADDED,
            params = mapOf(
                PARAM_SOURCE to source,
                PARAM_PROVIDER to provider
            )
        )

    fun logSettingsOpened() = log(EVENT_SETTINGS_OPENED)

    fun logWeatherError(errorKey: String) = log(EVENT_WEATHER_ERROR, mapOf(PARAM_ERROR_KEY to errorKey))

    private fun log(
        event: String,
        params: Map<String, String> = emptyMap(),
    ) {
        runCatching {
            if (shouldSend()) backend.logEvent(event, params)
        }.onFailure { Timber.w(it, "Analytics event %s dropped", event) }
    }

    private fun shouldSend(): Boolean = remoteConfig.analyticsEnabled() && collection.shareUsageStatistics == true

    companion object {
        const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"
        const val EVENT_CITY_ADDED = "city_added"
        const val EVENT_SETTINGS_OPENED = "settings_opened"
        const val EVENT_WEATHER_ERROR = "weather_error"
        const val PARAM_ERROR_KEY = "error_key"
    }
}
const val PARAM_ERROR_KEY = "error_key"

// 🌟 Add these two lines here:
const val PARAM_SOURCE = "source"
const val PARAM_PROVIDER = "provider"