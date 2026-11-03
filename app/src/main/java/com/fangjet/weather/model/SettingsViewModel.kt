package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.worker.NotificationKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferencesRepository: PreferencesRepository,
    private val remoteConfigRepository: RemoteConfigRepository,
) : ViewModel() {

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

    /** For the Notifications row in Settings: how many of the shown switches are on. */
    val notificationsOnCount = combine(
        preferencesRepository.notifyMorningBriefing,
        preferencesRepository.notifyWalkTime,
        preferencesRepository.notifyRain,
        preferencesRepository.showWalkCard,
    ) { briefing, walk, rain, dog ->
        listOf(
            briefing && remotelyEnabled(NotificationKind.MORNING_BRIEFING),
            walk && dog && remotelyEnabled(NotificationKind.WALK_TIME),
            rain && remotelyEnabled(NotificationKind.RAIN),
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

    /** "Follow my location" as set. The screen shows it on only while "Allow all the time" is granted too. */
    val followMyLocation = preferencesRepository.followMyLocation
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun setFollowMyLocation(enabled: Boolean) {
        viewModelScope.launch { preferencesRepository.setFollowMyLocation(enabled) }
    }

    fun clearCache() {
        viewModelScope.launch { preferencesRepository.clearCache() }
    }
}
