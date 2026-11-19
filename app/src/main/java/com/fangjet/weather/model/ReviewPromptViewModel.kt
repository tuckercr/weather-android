package com.fangjet.weather.model

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.BuildConfig
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.ReviewPromptMoment
import com.fangjet.weather.domain.ReviewPromptPolicy
import com.fangjet.weather.domain.ReviewPromptSettings
import com.fangjet.weather.domain.ReviewPromptVisit
import com.fangjet.weather.domain.WalkRating
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.ZoneId
import javax.inject.Inject

/**
 * What the weather screen shows, as far as the ratings prompt cares: whether it's the screen in
 * front, and the happy moment on it, if any. Same as iOS's `ReviewPromptScreen`.
 */
data class ReviewPromptScreen(
    /** The weather screen is in front with weather on it: counts as a day the app was used. */
    val visible: Boolean = false,
    /** Set only when the weather is fresh and nothing is in the way. */
    val moment: ReviewPromptMoment? = null,
) {
    companion object {
        val HIDDEN = ReviewPromptScreen()

        /**
         * Bad verdicts that rule a moment out even when the sky looks nice: hot pavement, bad air,
         * gales, storms and hurricane force wind.
         */
        private val BLOCKING_RATINGS =
            setOf(WalkRating.PAWS_HOT, WalkRating.BAD_AIR, WalkRating.WINDY, WalkRating.STORMY, WalkRating.DANGER)

        /**
         * [inFront] is false while anything covers the weather screen: another destination
         * (Settings, Add location), the day detail sheet, the update banner or screen, or onboarding.
         */
        fun of(
            screen: WeatherScreen,
            isRefreshing: Boolean,
            inFront: Boolean,
        ): ReviewPromptScreen {
            if (!inFront || screen !is WeatherScreen.Weather) return HIDDEN
            val state = screen.state
            val visible = ReviewPromptScreen(visible = true)
            if (state.stale != null || state.showingForecast || isRefreshing) return visible
            if (state.week is WeekSection.Error || state.mood == PetMood.STORM) return visible
            val advice = state.walkAdvice
            if (advice != null) {
                if (advice.rating in BLOCKING_RATINGS || advice.warning != null) return visible
                if (state.showsWalkCard && advice.rating == WalkRating.GREAT) {
                    return ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK)
                }
            }
            if (!state.dark && (state.mood == PetMood.SUNNY || state.mood == PetMood.FAIR)) {
                return ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.PLEASANT_WEATHER)
            }
            return visible
        }
    }
}

/**
 * Asks for a Play Store rating at the right moment ([ReviewPromptPolicy]). The weather screen says
 * what's on it and the activity says the app's comings and goings; when it's time, [requests]
 * emits and the activity launches Play's review flow. Same rules as iOS's `ReviewPromptViewModel`.
 */
@HiltViewModel
class ReviewPromptViewModel internal constructor(
    private val preferences: PreferencesRepository,
    private val settings: () -> ReviewPromptSettings,
    private val version: String,
    private val now: () -> Long,
    private val zone: () -> ZoneId,
    private val logRequest: (ReviewPromptMoment, Int) -> Unit,
    private val installedAtMillis: Long,
    private val forced: Boolean,
) : ViewModel() {
    @Inject constructor(
        @ApplicationContext context: Context,
        preferences: PreferencesRepository,
        remoteConfig: RemoteConfigRepository,
        analytics: AnalyticsRepository,
    ) : this(
        preferences = preferences,
        settings = remoteConfig::reviewPromptSettings,
        version = BuildConfig.VERSION_NAME,
        now = System::currentTimeMillis,
        zone = ZoneId::systemDefault,
        logRequest = analytics::logReviewPromptRequested,
        // The real install date, also for people updating from a build before the prompt existed.
        installedAtMillis = runCatching { context.packageManager.getPackageInfo(context.packageName, 0).firstInstallTime }
            .getOrDefault(System.currentTimeMillis()),
        forced = BuildConfig.DEBUG && debugForce,
    )

    private val requestChannel = Channel<ReviewPromptMoment>(Channel.CONFLATED)

    /** Emits each time Play's review flow should be launched. */
    val requests: Flow<ReviewPromptMoment> = requestChannel.receiveAsFlow()

    private var screen = ReviewPromptScreen.HIDDEN
    private var visitStartedAt = now()
    private var visitInterrupted = false
    private var wentToBackground = false
    private var notificationOpenedAt: Long? = null
    private var pending: Job? = null
    private var forcedThisLaunch = false

    /** The weather screen changed what it shows. */
    fun screenChanged(screen: ReviewPromptScreen) {
        if (screen == this.screen) return
        this.screen = screen
        evaluate()
    }

    /** One of the app's notifications was tapped to open it. */
    fun notificationOpened() {
        notificationOpenedAt = now()
    }

    /** Something covered the app (a permission request, another system dialog, another app in split screen). */
    fun becameInactive() {
        visitInterrupted = true
        pending?.cancel()
    }

    fun enteredBackground() {
        wentToBackground = true
        pending?.cancel()
    }

    /** Back in front: a new visit if the app had gone to the background. */
    fun becameActive() {
        if (!wentToBackground) return
        wentToBackground = false
        visitInterrupted = false
        visitStartedAt = now()
        evaluate()
    }

    /** Counts the day and, at a moment that may ask, asks after the delay if nothing has changed. */
    private fun evaluate() {
        val screen = screen
        pending?.cancel()
        pending = null
        if (!screen.visible) return
        pending = viewModelScope.launch {
            withContext(NonCancellable) {
                preferences.updateReviewPromptHistory { ReviewPromptPolicy.recordingActiveDay(it, now(), zone()) }
            }
            val moment = (if (forced) screen.moment ?: ReviewPromptMoment.PLEASANT_WEATHER else screen.moment) ?: return@launch
            if (!mayAsk(moment)) return@launch
            val delaySeconds = if (forced) ReviewPromptSettings.DEFAULT.delaySeconds else settings().delaySeconds
            delay(delaySeconds * 1_000L)
            if (this@ReviewPromptViewModel.screen != screen || !mayAsk(moment)) return@launch
            withContext(NonCancellable) { ask(moment) }
        }
    }

    private val visit: ReviewPromptVisit
        get() {
            // A tap's intent can land a moment before or after the visit starts, so allow a little slack.
            val fromNotification = notificationOpenedAt?.let { it >= visitStartedAt - NOTIFICATION_SLACK_MILLIS } ?: false
            return ReviewPromptVisit(fromNotification = fromNotification, interrupted = visitInterrupted)
        }

    private suspend fun mayAsk(moment: ReviewPromptMoment): Boolean {
        if (forced) return !forcedThisLaunch
        return ReviewPromptPolicy.shouldAsk(
            moment = moment,
            history = preferences.reviewPromptHistory(),
            settings = settings(),
            installedAtMillis = installedAtMillis,
            nowMillis = now(),
            version = version,
            visit = visit,
            zone = zone(),
        )
    }

    private suspend fun ask(moment: ReviewPromptMoment) {
        forcedThisLaunch = forced
        val history = preferences.updateReviewPromptHistory { ReviewPromptPolicy.recordingRequest(it, now(), version) }
        logRequest(moment, history.requests)
        requestChannel.trySend(moment)
    }

    companion object {
        private const val NOTIFICATION_SLACK_MILLIS = 30_000L

        /**
         * Debug builds only: set by `adb shell am start -S ... --ez reviewPromptForce true`, to ask once
         * per launch, a few seconds after the weather shows, whatever the counts and Remote Config
         * say. Same as iOS's `-reviewPromptForce YES`.
         */
        internal var debugForce: Boolean = false
    }
}
