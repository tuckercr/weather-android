package com.fangjet.weather.analytics

import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherProvider
import com.fangjet.weather.data.WeatherRequestKind
import com.fangjet.weather.worker.NotificationKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's analytics events and user properties. The whole catalogue is the `log…` functions
 * and [setUserProperties] below, so the privacy policy can be checked against the code line by
 * line. Every value is a fixed category (`AnalyticsValues.kt`): never places,
 * coordinates, city names or typed text.
 *
 * Gates, in order — an event or user property is sent only if every one passes:
 *  1. Remote Config kill switch `analytics_enabled` — false drops every event on every
 *     install, without an app update ([RemoteConfigRepository.analyticsEnabled]).
 *  2. The user's own "Share usage statistics" setting (Settings → About). Also enforced at
 *     the SDK level by [AnalyticsCollectionController], so Firebase's automatic events stop too.
 *     An event logged before the setting has been read at launch waits for it.
 *  3. Firebase itself: without `google-services.json` the backend is a no-op.
 *
 * Every call is wrapped: analytics must never be able to crash a feature.
 */
@Singleton
class AnalyticsRepository internal constructor(
    private val backend: AnalyticsBackend,
    private val remoteConfig: RemoteConfigRepository,
    private val collection: AnalyticsCollectionController,
    // Where an event waits for the user's setting to be read at launch.
    private val scope: CoroutineScope,
) {
    @Inject
    constructor(
        backend: AnalyticsBackend,
        remoteConfig: RemoteConfigRepository,
        collection: AnalyticsCollectionController,
    ) : this(backend, remoteConfig, collection, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    /** An onboarding screen was shown: once per step per showing. */
    fun logOnboardingStep(step: OnboardingStepShown) = log(EVENT_ONBOARDING_STEP, mapOf(PARAM_STEP to step.key))

    /** The last onboarding step finished or was skipped, with its answers. */
    fun logOnboardingComplete(
        hasDog: Boolean,
        notifications: OnboardingPermissionResult,
        location: OnboardingPermissionResult,
    ) = log(
        EVENT_ONBOARDING_COMPLETE,
        mapOf(
            PARAM_HAS_DOG to yesNo(hasDog),
            PARAM_NOTIFICATIONS to notifications.key,
            PARAM_LOCATION to location.key,
        ),
    )

    /** A new location was saved: how, and which search found it. Never which place. */
    fun logCityAdded(added: CityAdded) =
        log(EVENT_CITY_ADDED, mapOf(PARAM_SOURCE to added.source.key, PARAM_PROVIDER to added.provider.key))

    fun logSettingsOpened() = log(EVENT_SETTINGS_OPENED)

    /**
     * Outside onboarding, the app asked for [permission] (a system dialog, or a trip to the app's
     * system settings page) and the answer came back.
     */
    fun logPermissionChanged(
        permission: AnalyticsPermission,
        granted: Boolean,
        from: PermissionAskedFrom,
    ) = log(
        EVENT_PERMISSION_CHANGED,
        mapOf(
            PARAM_PERMISSION to permission.key,
            PARAM_RESULT to if (granted) RESULT_GRANTED else RESULT_DENIED,
            PARAM_FROM to from.key,
        ),
    )

    /** The app was opened by a tap on one of its notifications. */
    internal fun logNotificationOpened(kind: NotificationKind) = log(EVENT_NOTIFICATION_OPENED, mapOf(PARAM_KIND to kind.openedKey))

    /** A request to a weather service failed, even if a fallback or the cache covered it. */
    fun logWeatherError(
        errorKey: String,
        provider: WeatherProvider,
        request: WeatherRequestKind,
    ) = log(
        EVENT_WEATHER_ERROR,
        mapOf(PARAM_ERROR_KEY to errorKey, PARAM_PROVIDER to provider.key, PARAM_REQUEST to request.key),
    )

    /** The user properties ([AnalyticsUserProperties]), behind the same gates as the events. */
    fun setUserProperties(properties: Map<String, String>) {
        runCatching {
            if (shouldSend()) properties.forEach { (name, value) -> backend.setUserProperty(name, value) }
        }.onFailure { Timber.w(it, "User properties dropped") }
    }

    private fun log(
        event: String,
        params: Map<String, String> = emptyMap(),
    ) {
        runCatching {
            if (collection.shareUsageStatistics == null) {
                // Launched moments ago (a notification tapped, say): wait for the user's choice.
                scope.launch { runCatching { if (shouldSend(collection.awaitShareUsageStatistics())) backend.logEvent(event, params) } }
            } else if (shouldSend()) {
                backend.logEvent(event, params)
            }
        }.onFailure { Timber.w(it, "Analytics event %s dropped", event) }
    }

    private fun shouldSend(shareUsageStatistics: Boolean? = collection.shareUsageStatistics): Boolean =
        shareUsageStatistics == true && remoteConfig.analyticsEnabled()

    private fun yesNo(value: Boolean) = if (value) YES else NO

    companion object {
        const val EVENT_ONBOARDING_STEP = "onboarding_step"
        const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"
        const val EVENT_CITY_ADDED = "city_added"
        const val EVENT_SETTINGS_OPENED = "settings_opened"
        const val EVENT_PERMISSION_CHANGED = "permission_changed"
        const val EVENT_NOTIFICATION_OPENED = "notification_opened"
        const val EVENT_WEATHER_ERROR = "weather_error"

        const val PARAM_STEP = "step"
        const val PARAM_HAS_DOG = "has_dog"
        const val PARAM_NOTIFICATIONS = "notifications"
        const val PARAM_LOCATION = "location"
        const val PARAM_SOURCE = "source"
        const val PARAM_PROVIDER = "provider"
        const val PARAM_PERMISSION = "permission"
        const val PARAM_RESULT = "result"
        const val PARAM_FROM = "from"
        const val PARAM_KIND = "kind"
        const val PARAM_ERROR_KEY = "error_key"
        const val PARAM_REQUEST = "request"

        const val YES = "yes"
        const val NO = "no"
        const val RESULT_GRANTED = "granted"
        const val RESULT_DENIED = "denied"
    }
}
