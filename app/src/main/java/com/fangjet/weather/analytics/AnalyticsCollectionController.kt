package com.fangjet.weather.analytics

import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Keeps Firebase Analytics collection in step with the user's "Share usage statistics"
 * setting and the Remote Config `analytics_enabled` kill switch: collection is on only
 * when both are on.
 *
 * Done at the SDK level (`setAnalyticsCollectionEnabled`) rather than only by filtering our
 * own events, so Firebase's automatic events (first_open, session_start, screen_view…) stop
 * too — an opt-out that only silenced our custom events would be a half-truth.
 * Started once from the Application; re-applied whenever either input changes.
 */
@Singleton
class AnalyticsCollectionController @Inject constructor(
    private val preferences: PreferencesRepository,
    private val remoteConfig: RemoteConfigRepository,
    private val backend: AnalyticsBackend,
) {
    /**
     * The user's latest "Share usage statistics" choice, or null until the preference has been
     * read. [AnalyticsRepository] holds an event logged before then until it's known, so an
     * opted-out user's choice is honoured even for an event fired in the first moments after launch.
     */
    val shareUsageStatistics: Boolean?
        get() = shareUsage.value

    private val shareUsage = MutableStateFlow<Boolean?>(null)

    /**
     * [shareUsageStatistics] once the preference has been read, waiting for it at launch (a tap on a
     * notification can log before then), or null if it hasn't come within a few seconds.
     */
    suspend fun awaitShareUsageStatistics(): Boolean? =
        shareUsage.value ?: withTimeoutOrNull(PREFERENCE_WAIT_MILLIS) { shareUsage.filterNotNull().first() }

    private val _collectionEnabled = MutableStateFlow<Boolean?>(null)

    /**
     * The SDK flag as last applied: null until the first time, then true while both gates are open.
     * User properties are set after it turns true, so the gates are applied first.
     */
    val collectionEnabled: StateFlow<Boolean?> = _collectionEnabled.asStateFlow()

    fun start(scope: CoroutineScope) {
        scope.launch {
            combine(
                preferences.shareUsageStatistics.onEach { shareUsage.value = it },
                remoteConfig.analyticsEnabledUpdates,
            ) { userOptedIn, killSwitchOn -> userOptedIn && killSwitchOn }
                .distinctUntilChanged()
                .catch { Timber.w(it, "Analytics collection state unavailable; leaving SDK flag as is") }
                .collect { enabled ->
                    backend.setCollectionEnabled(enabled)
                    _collectionEnabled.value = enabled
                }
        }
    }

    private companion object {
        const val PREFERENCE_WAIT_MILLIS = 5_000L
    }
}
