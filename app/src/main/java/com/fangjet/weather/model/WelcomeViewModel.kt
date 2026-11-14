package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.worker.NotificationKind
import com.fangjet.weather.worker.WalkTimePlanner
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/** Where a returning or new user starts: the dog question only follows the welcome, in the same run. */
enum class OnboardingStep { WELCOME, NOTIFICATIONS, LOCATION, DONE }

/** What "Stay in the loop" promises: the walk nudge with a dog, rain alerts while Remote Config has them on. */
data class NotificationPromise(
    val hasDog: Boolean = true,
    val walkTimeEnabled: Boolean = true,
    val rainEnabled: Boolean = true,
)

data class OnboardingState(
    val hasSeenWelcome: Boolean,
    val notificationOnboardingDone: Boolean,
    val locationOnboardingDone: Boolean,
)

@HiltViewModel
class WelcomeViewModel internal constructor(
    private val hasSeenWelcomeOnce: suspend () -> Boolean,
    private val notificationOnboardingDoneOnce: suspend () -> Boolean,
    private val locationOnboardingDoneOnce: suspend () -> Boolean,
    private val setHasSeenWelcome: suspend (Boolean) -> Unit,
    private val setNotificationOnboardingDone: suspend () -> Unit,
    private val setLocationOnboardingDone: suspend () -> Unit,
    private val setShowWalkCard: suspend (Boolean) -> Unit = {},
    private val onOnboardingComplete: () -> Unit = {},
    // The dog answer as saved, for a restored app's notification screen.
    private val hasDogOnce: suspend () -> Boolean = { true },
    // Remote Config's switch for each kind of notification.
    private val remotelyEnabled: (NotificationKind) -> Boolean = { true },
    // Ticks on each Remote Config activation: the first launch's fetch often lands mid-onboarding.
    private val remoteConfigActivations: Flow<Int> = flowOf(0),
    // Plans today's walk nudge once there's a dog (WalkTimePlanner.planToday).
    private val planWalkTimeToday: suspend () -> Unit = {},
) : ViewModel() {

    @Inject
    constructor(
        preferencesRepository: PreferencesRepository,
        analyticsRepository: AnalyticsRepository,
        remoteConfigRepository: RemoteConfigRepository,
        walkTimePlanner: WalkTimePlanner,
    ) : this(
        hasSeenWelcomeOnce = preferencesRepository::hasSeenWelcomeOnce,
        notificationOnboardingDoneOnce = preferencesRepository::notificationOnboardingDoneOnce,
        locationOnboardingDoneOnce = preferencesRepository::locationOnboardingDoneOnce,
        setHasSeenWelcome = preferencesRepository::setHasSeenWelcome,
        setNotificationOnboardingDone = preferencesRepository::setNotificationOnboardingDone,
        setLocationOnboardingDone = preferencesRepository::setLocationOnboardingDone,
        setShowWalkCard = preferencesRepository::setShowWalkCard,
        onOnboardingComplete = analyticsRepository::logOnboardingComplete,
        hasDogOnce = { preferencesRepository.showWalkCard.first() },
        remotelyEnabled = remoteConfigRepository::notificationsEnabled,
        remoteConfigActivations = remoteConfigRepository.activationCount,
        planWalkTimeToday = walkTimePlanner::planToday,
    )

    /** null while the prefs read is in-flight. */
    private val _onboardingState = MutableStateFlow<OnboardingState?>(null)
    val onboardingState: StateFlow<OnboardingState?> = _onboardingState.asStateFlow()

    /** Where to start: null while the prefs read is in flight. */
    val step: StateFlow<OnboardingStep?> =
        _onboardingState
            .map { state ->
                when {
                    state == null -> null
                    !state.hasSeenWelcome -> OnboardingStep.WELCOME
                    !state.notificationOnboardingDone -> OnboardingStep.NOTIFICATIONS
                    !state.locationOnboardingDone -> OnboardingStep.LOCATION
                    else -> OnboardingStep.DONE
                }
            }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _notificationPromise = MutableStateFlow(NotificationPromise())

    /** What the notification screen promises, from the dog answer and Remote Config. */
    val notificationPromise: StateFlow<NotificationPromise> = _notificationPromise.asStateFlow()

    init {
        readRemoteSwitches()
        viewModelScope.launch { remoteConfigActivations.collect { readRemoteSwitches() } }
        viewModelScope.launch { _notificationPromise.update { it.copy(hasDog = hasDogOnce()) } }
        viewModelScope.launch {
            _onboardingState.value = OnboardingState(
                hasSeenWelcome = hasSeenWelcomeOnce(),
                notificationOnboardingDone = notificationOnboardingDoneOnce(),
                locationOnboardingDone = locationOnboardingDoneOnce(),
            )
        }
    }

    private fun readRemoteSwitches() {
        _notificationPromise.update {
            it.copy(
                walkTimeEnabled = remotelyEnabled(NotificationKind.WALK_TIME),
                rainEnabled = remotelyEnabled(NotificationKind.RAIN),
            )
        }
    }

    fun completeWelcome() {
        viewModelScope.launch {
            setHasSeenWelcome(true)
            _onboardingState.value = _onboardingState.value?.copy(hasSeenWelcome = true)
        }
    }

    /** "Is there a dog at home?": the walk card only shows when there is. */
    fun answerHasDog(hasDog: Boolean) {
        _notificationPromise.update { it.copy(hasDog = hasDog) }
        viewModelScope.launch {
            setShowWalkCard(hasDog)
            // Walk time follows the dog until it's chosen: today's nudge, if there's a forecast yet.
            if (hasDog) planWalkTimeToday()
        }
    }

    fun completeNotificationOnboarding() {
        viewModelScope.launch {
            setNotificationOnboardingDone()
            _onboardingState.value = _onboardingState.value?.copy(notificationOnboardingDone = true)
        }
    }

    fun completeLocationOnboarding() {
        viewModelScope.launch {
            setLocationOnboardingDone()
            _onboardingState.value = _onboardingState.value?.copy(locationOnboardingDone = true)
            onOnboardingComplete()
        }
    }
}
