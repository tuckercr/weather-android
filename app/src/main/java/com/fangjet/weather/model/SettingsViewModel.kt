package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.analytics.AnalyticsPermission
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.analytics.PermissionAskedFrom
import com.fangjet.weather.data.AppPermissions
import com.fangjet.weather.data.DevicePermissions
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.worker.NotificationKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** What the app has been allowed, as Settings shows it. Re-read on every resume. */
data class PermissionState(
    val location: Boolean = false,
    val backgroundLocation: Boolean = false,
    val notifications: Boolean = false,
)

/** What turning on Follow my location asks Android for next, which only the screen can launch. */
enum class FollowMyLocationRequest {
    /** Already allowed all the time: nothing to ask. */
    NONE,

    /** "While using the app" first: Android only offers "Allow all the time" after it. */
    FOREGROUND_LOCATION,
    BACKGROUND_LOCATION,

    /** Refused twice: Android won't open its page again, so the app's settings open instead. */
    APP_SETTINGS,
}

@HiltViewModel
class SettingsViewModel internal constructor(
    private val preferencesRepository: PreferencesRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
    private val permissions: AppPermissions,
    // permission_changed: the app asked for a permission here and the answer came back.
    private val onPermissionChanged: (AnalyticsPermission, granted: Boolean, PermissionAskedFrom) -> Unit = { _, _, _ -> },
) : ViewModel() {
    @Inject
    constructor(
        preferencesRepository: PreferencesRepository,
        remoteConfigRepository: RemoteConfigRepository,
        permissions: DevicePermissions,
        analytics: AnalyticsRepository,
    ) : this(preferencesRepository, remoteConfigRepository, permissions as AppPermissions, analytics::logPermissionChanged)

    private val _permissionState = MutableStateFlow(readPermissions())

    /** What's allowed, as at the last [refreshPermissions]. */
    val permissionState: StateFlow<PermissionState> = _permissionState.asStateFlow()

    /** Re-reads the permissions: on every resume, since they change in system settings. */
    fun refreshPermissions() {
        _permissionState.value = readPermissions()
        backFromAppSettings()
    }

    /** What a trip to the app's system settings page was for, until the user is back. */
    private var appSettingsTrip: Pair<AnalyticsPermission, PermissionAskedFrom>? = null

    /**
     * A row for a permission that's off (Settings' location, Notifications' notifications) is opening
     * the app's settings page, where it can be allowed: the next resume brings the answer.
     */
    fun openingAppSettingsFor(permission: AnalyticsPermission) {
        appSettingsTrip = permission to PermissionAskedFrom.SETTINGS
    }

    /** Back from the app's settings page: whether the permission it was opened for is on now. */
    private fun backFromAppSettings() {
        val (permission, from) = appSettingsTrip ?: return
        appSettingsTrip = null
        val granted = when (permission) {
            AnalyticsPermission.NOTIFICATIONS -> permissions.notificationsEnabled()
            AnalyticsPermission.LOCATION -> permissions.location()
            AnalyticsPermission.LOCATION_ALWAYS -> permissions.backgroundLocation()
        }
        onPermissionChanged(permission, granted, from)
    }

    private fun readPermissions() =
        PermissionState(
            location = permissions.location(),
            backgroundLocation = permissions.backgroundLocation(),
            notifications = permissions.notifications(),
        )

    val unitOverride = preferencesRepository.unitOverride
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UnitOverride.SYSTEM)

    fun setUnitOverride(override: UnitOverride) {
        viewModelScope.launch { preferencesRepository.setUnitOverride(override) }
    }

    /** "Share usage statistics". Deliberately not itself tracked by analytics. */
    val shareUsageStatistics = preferencesRepository.shareUsageStatistics
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setShareUsageStatistics(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setShareUsageStatistics(enabled) }
    }

    /**
     * "I have a dog": the walk card, Paw check, the briefing's walk sentence and Walk time. Walk time
     * keeps its own value, so turning the dog back on brings it back as it was.
     */
    val showWalkCard = preferencesRepository.showWalkCard
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)

    fun setShowWalkCard(show: Boolean) {
        viewModelScope.launch { preferencesRepository.setShowWalkCard(show) }
    }

    /** The notification switches: each kind can be turned off on its own. */
    val notifyMorningBriefing = preferencesRepository.notifyMorningBriefing
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifyWalkTime = preferencesRepository.notifyWalkTime
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), true)
    val notifyRain = preferencesRepository.notifyRain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /**
     * Whether a kind's switch shows in Settings: not while Remote Config has it off for everyone
     * (Walk time also needs a dog, which the screen checks). A hidden switch keeps its value.
     */
    internal fun remotelyEnabled(kind: NotificationKind) = remoteConfigRepository.notificationsEnabled(kind)

    private fun readRemotelyEnabledKinds() = NotificationKind.entries.filter(::remotelyEnabled).toSet()

    /**
     * The kinds whose switches show ([remotelyEnabled]), read again on each Remote Config
     * activation, so a fetch that lands while Settings is open applies at once.
     */
    internal val remotelyEnabledKinds: StateFlow<Set<NotificationKind>> = remoteConfigRepository.activationCount
        .map { readRemotelyEnabledKinds() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), readRemotelyEnabledKinds())

    /** For the Notifications row in Settings: how many of the shown switches are on. */
    val notificationsOnCount = combine(
        preferencesRepository.notifyMorningBriefing,
        preferencesRepository.notifyWalkTime,
        preferencesRepository.notifyRain,
        preferencesRepository.showWalkCard,
        remotelyEnabledKinds,
    ) { briefing, walk, rain, dog, remote ->
        listOf(
            briefing && NotificationKind.MORNING_BRIEFING in remote,
            walk && dog && NotificationKind.WALK_TIME in remote,
            rain && NotificationKind.RAIN in remote,
        ).count { it }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    fun setNotifyMorningBriefing(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyMorningBriefing(enabled) }
    }

    fun setNotifyWalkTime(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyWalkTime(enabled) }
    }

    fun setNotifyRain(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setNotifyRain(enabled) }
    }

    /** Whether there's a My Location to follow: Follow my location only shows then. */
    val hasMyLocation = preferencesRepository.savedLocations
        .map { locations -> locations.any(SavedLocation::isCurrentLocation) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Follow my location as the switch shows it: on only while it's set and "Allow all the time" is granted. */
    val followsMyLocation = combine(preferencesRepository.followMyLocation, _permissionState) { on, allowed ->
        on && allowed.backgroundLocation
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _explainingFollowMyLocation = MutableStateFlow(false)

    /** Turning Follow my location on without "Allow all the time" explains first, as Play requires. */
    val explainingFollowMyLocation: StateFlow<Boolean> = _explainingFollowMyLocation.asStateFlow()

    /** Off turns it straight off; on with "Allow all the time" already granted has nothing to explain. */
    fun setFollowMyLocation(on: Boolean) {
        when {
            !on -> saveFollowMyLocation(false)
            permissions.backgroundLocation() -> saveFollowMyLocation(true)
            else -> _explainingFollowMyLocation.value = true
        }
    }

    /**
     * "Continue" on the explanation: it's switched on, and this says what to ask Android for. Android 11
     * and later open the app's location page in system settings rather than a dialog.
     */
    fun confirmFollowMyLocation(): FollowMyLocationRequest {
        _explainingFollowMyLocation.value = false
        saveFollowMyLocation(true)
        return when {
            permissions.backgroundLocation() -> FollowMyLocationRequest.NONE
            !permissions.location() -> FollowMyLocationRequest.FOREGROUND_LOCATION
            permissions.backgroundLocationRefusals() >= 2 -> FollowMyLocationRequest.APP_SETTINGS
            else -> FollowMyLocationRequest.BACKGROUND_LOCATION
        }.also { request ->
            // The app's settings page answers when the user is back ([refreshPermissions]).
            if (request == FollowMyLocationRequest.APP_SETTINGS) {
                appSettingsTrip = AnalyticsPermission.LOCATION_ALWAYS to PermissionAskedFrom.FOLLOW_MY_LOCATION
            }
        }
    }

    /** The answer to "While using the app", asked first when turning Follow my location on without it. */
    fun foregroundLocationAnswered(granted: Boolean) {
        onPermissionChanged(AnalyticsPermission.LOCATION, granted, PermissionAskedFrom.FOLLOW_MY_LOCATION)
        refreshPermissions()
    }

    fun dismissFollowMyLocationExplanation() {
        _explainingFollowMyLocation.value = false
    }

    /** The answer to "Allow all the time", counted so the third try opens the app's settings. */
    fun backgroundLocationAnswered(granted: Boolean) {
        permissions.noteBackgroundLocationAnswer(granted)
        onPermissionChanged(AnalyticsPermission.LOCATION_ALWAYS, granted, PermissionAskedFrom.FOLLOW_MY_LOCATION)
        refreshPermissions()
    }

    private fun saveFollowMyLocation(on: Boolean) {
        viewModelScope.launch { preferencesRepository.setFollowMyLocation(on) }
    }

    fun clearCache() {
        viewModelScope.launch { preferencesRepository.clearCache() }
    }
}
