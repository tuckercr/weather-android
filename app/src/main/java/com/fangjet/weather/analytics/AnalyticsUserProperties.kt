package com.fangjet.weather.analytics

import android.content.Context
import com.fangjet.weather.data.AppPermissions
import com.fangjet.weather.data.DevicePermissions
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.resolveWeatherUnits
import com.fangjet.weather.worker.NotificationKind
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/** The settings the user properties are worked out from, as stored. */
data class UserPropertySettings(
    val hasDog: Boolean = true,
    val briefingSwitch: Boolean = true,
    val walkTimeSwitch: Boolean = true,
    val followMyLocation: Boolean = false,
    val unitOverride: UnitOverride = UnitOverride.SYSTEM,
    val locations: List<SavedLocation> = emptyList(),
)

/** Everything the user properties say, read at one moment. */
data class UserPropertyInputs(
    val settings: UserPropertySettings,
    val briefingRemotelyEnabled: Boolean,
    val walkTimeRemotelyEnabled: Boolean,
    val notificationsAllowed: Boolean,
    val location: Boolean,
    val backgroundLocation: Boolean,
    // The units actually used: Settings' choice, or the device's with System.
    val units: WeatherUnits,
)

/**
 * The analytics user properties: fixed categories describing how the app is set up, for filtering
 * reports. Same names and values as iOS.
 */
object AnalyticsUserProperties {
    const val HAS_DOG = "has_dog"
    const val BRIEFING_ON = "briefing_on"
    const val WALK_TIME_ON = "walk_time_on"
    const val LOCATION_ACCESS = "location_access"
    const val FOLLOW_LOCATION = "follow_location"
    const val UNITS = "units"
    const val CITY_COUNT = "city_count"

    /**
     * Briefings and walk nudges count as on only when they'd actually arrive: the switch, Remote
     * Config and notification permission (and for walks, a dog). Follow my location only while it
     * can work: "Allow all the time" and a My Location to move.
     */
    fun of(inputs: UserPropertyInputs): Map<String, String> {
        val settings = inputs.settings
        val briefingOn = settings.briefingSwitch && inputs.briefingRemotelyEnabled && inputs.notificationsAllowed
        val walkTimeOn = settings.hasDog && settings.walkTimeSwitch && inputs.walkTimeRemotelyEnabled && inputs.notificationsAllowed
        val following = settings.followMyLocation &&
            inputs.backgroundLocation &&
            settings.locations.any(SavedLocation::isCurrentLocation)
        return mapOf(
            HAS_DOG to yesNo(settings.hasDog),
            BRIEFING_ON to yesNo(briefingOn),
            WALK_TIME_ON to yesNo(walkTimeOn),
            LOCATION_ACCESS to when {
                !inputs.location -> "none"
                inputs.backgroundLocation -> "always"
                else -> "while_using"
            },
            FOLLOW_LOCATION to yesNo(following),
            UNITS to if (inputs.units == WeatherUnits.IMPERIAL) "imperial" else "metric",
            CITY_COUNT to cityCount(settings.locations.size),
        )
    }

    internal fun cityCount(count: Int): String =
        when {
            count <= 0 -> "0"
            count == 1 -> "1"
            count <= 3 -> "2-3"
            else -> "4+"
        }

    private fun yesNo(value: Boolean) = if (value) AnalyticsRepository.YES else AnalyticsRepository.NO
}

/**
 * Sets the user properties ([AnalyticsUserProperties]) once the analytics gates have been applied
 * at launch, again when "Share usage statistics" is turned on, whenever a setting or the city list
 * changes or Remote Config is activated, and on each return to the app ([refresh]), where changes
 * made in system settings (permissions) are picked up. Nothing is set while a gate is closed.
 */
@Singleton
class AnalyticsUserPropertiesUpdater internal constructor(
    private val settings: Flow<UserPropertySettings>,
    private val collectionEnabled: Flow<Boolean?>,
    private val remoteConfigActivations: Flow<Int>,
    private val remotelyEnabled: (NotificationKind) -> Boolean,
    private val permissions: AppPermissions,
    private val resolveUnits: (UnitOverride) -> WeatherUnits,
    private val analytics: AnalyticsRepository,
) {
    @Inject
    constructor(
        @ApplicationContext context: Context,
        preferences: PreferencesRepository,
        remoteConfig: RemoteConfigRepository,
        permissions: DevicePermissions,
        collection: AnalyticsCollectionController,
        analytics: AnalyticsRepository,
    ) : this(
        settings = settingsOf(preferences),
        collectionEnabled = collection.collectionEnabled,
        remoteConfigActivations = remoteConfig.activationCount,
        remotelyEnabled = remoteConfig::notificationsEnabled,
        permissions = permissions,
        resolveUnits = context::resolveWeatherUnits,
        analytics = analytics,
    )

    private val refreshes = MutableStateFlow(0)

    /** The app came back to the foreground: permissions may have changed in system settings. */
    fun refresh() = refreshes.update { it + 1 }

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(collectionEnabled, settings, remoteConfigActivations, refreshes) { enabled, settings, _, _ -> enabled to settings }
                .catch { Timber.w(it, "User properties unavailable") }
                .collect { (enabled, settings) ->
                    if (enabled == true) runCatching { analytics.setUserProperties(AnalyticsUserProperties.of(inputs(settings))) }
                }
        }
    }

    private fun inputs(settings: UserPropertySettings) =
        UserPropertyInputs(
            settings = settings,
            briefingRemotelyEnabled = remotelyEnabled(NotificationKind.MORNING_BRIEFING),
            walkTimeRemotelyEnabled = remotelyEnabled(NotificationKind.WALK_TIME),
            notificationsAllowed = permissions.notificationsEnabled(),
            location = permissions.location(),
            backgroundLocation = permissions.backgroundLocation(),
            units = resolveUnits(settings.unitOverride),
        )

    private companion object {
        fun settingsOf(preferences: PreferencesRepository): Flow<UserPropertySettings> =
            combine(
                combine(preferences.showWalkCard, preferences.notifyMorningBriefing, preferences.notifyWalkTime, ::Triple),
                preferences.followMyLocation,
                preferences.unitOverride,
                preferences.savedLocations,
            ) { (hasDog, briefing, walk), follow, units, locations ->
                UserPropertySettings(hasDog, briefing, walk, follow, units, locations)
            }
    }
}
