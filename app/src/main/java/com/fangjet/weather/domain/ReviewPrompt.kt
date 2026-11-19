package com.fangjet.weather.domain

import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/*
 * When to ask for a Play Store rating. Only Play's in-app review sheet is used, and only at a happy
 * moment for someone who keeps coming back. Same rules, Remote Config keys and defaults as iOS's
 * `Domain/ReviewPrompt.swift`.
 */

/** The happy moments that can ask. [key] is the name in `review_prompt_moments`. */
enum class ReviewPromptMoment(
    val key: String,
) {
    /** The walk card says "Now is a great time". */
    GREAT_WALK("great_walk"),

    /** The pets in their sunny or fair mood, by day: for people without a dog too. */
    PLEASANT_WEATHER("pleasant_weather"),
}

/** The Remote Config values, validated by `RemoteConfigRepository.reviewPromptSettings`. */
data class ReviewPromptSettings(
    /** Off for the first release: turned on from the console once it's been tried on a phone. */
    val enabled: Boolean = false,
    /** Days since install before asking; never on the day of install, whatever this says. */
    val minDaysInstalled: Int = 7,
    /** Different days the weather screen was shown before asking. */
    val minActiveDays: Int = 5,
    val moments: Set<ReviewPromptMoment> = ReviewPromptMoment.entries.toSet(),
    /** The wait after a moment, with the screen unchanged, before asking. */
    val delaySeconds: Int = 3,
    val oncePerVersion: Boolean = true,
    val cooldownDays: Int = 120,
    /** Asks in all, ever; 0 means no limit. */
    val maxRequests: Int = 3,
    /** Whether a visit that started from a notification can ask. */
    val onNotificationOpen: Boolean = false,
) {
    companion object {
        val DEFAULT = ReviewPromptSettings()

        val MIN_DAYS_INSTALLED_RANGE = 1..365
        val MIN_ACTIVE_DAYS_RANGE = 1..100
        val DELAY_SECONDS_RANGE = 0..30
        val COOLDOWN_DAYS_RANGE = 30..365
        val MAX_REQUESTS_RANGE = 0..20

        /**
         * `review_prompt_moments`: comma-separated names; unknown ones are ignored and an empty list
         * never asks. Null (not set) gives every moment.
         */
        fun momentsFromList(list: String?): Set<ReviewPromptMoment> {
            if (list == null) return DEFAULT.moments
            return list
                .split(",")
                .mapNotNull { name -> ReviewPromptMoment.entries.firstOrNull { it.key == name.trim() } }
                .toSet()
        }
    }
}

/** What's saved on the phone between launches. Times are epoch milliseconds. */
@Serializable
data class ReviewPromptHistory(
    /** Different days the weather screen was shown. */
    val activeDays: Int = 0,
    /** The start of the last of those days, by the phone's time zone. */
    val lastActiveDay: Long? = null,
    /** Times the review sheet was asked for (Play never says whether it showed). */
    val requests: Int = 0,
    val lastRequestAt: Long? = null,
    val lastRequestVersion: String? = null,
)

/** This visit to the app: since it last came to the foreground. */
data class ReviewPromptVisit(
    /** Opened by tapping one of the app's notifications. */
    val fromNotification: Boolean = false,
    /** Something covered the app in this visit: a permission request or another system dialog. */
    val interrupted: Boolean = false,
)

object ReviewPromptPolicy {
    /** [history] with today counted, if it wasn't already. */
    fun recordingActiveDay(
        history: ReviewPromptHistory,
        nowMillis: Long,
        zone: ZoneId,
    ): ReviewPromptHistory {
        val today = localDate(nowMillis, zone)
        if (history.lastActiveDay != null && localDate(history.lastActiveDay, zone) == today) return history
        return history.copy(
            activeDays = history.activeDays + 1,
            lastActiveDay = today.atStartOfDay(zone).toInstant().toEpochMilli(),
        )
    }

    /** [history] after asking now, in app version [version]. */
    fun recordingRequest(
        history: ReviewPromptHistory,
        nowMillis: Long,
        version: String,
    ): ReviewPromptHistory =
        history.copy(
            requests = history.requests + 1,
            lastRequestAt = nowMillis,
            lastRequestVersion = version,
        )

    /** True when [moment], on screen now, may ask for a rating. */
    fun shouldAsk(
        moment: ReviewPromptMoment,
        history: ReviewPromptHistory,
        settings: ReviewPromptSettings,
        installedAtMillis: Long,
        nowMillis: Long,
        version: String,
        visit: ReviewPromptVisit,
        zone: ZoneId,
    ): Boolean {
        if (!settings.enabled || moment !in settings.moments) return false
        if (visit.interrupted || (visit.fromNotification && !settings.onNotificationOpen)) return false
        if (days(installedAtMillis, nowMillis, zone) < maxOf(1, settings.minDaysInstalled)) return false
        if (history.activeDays < settings.minActiveDays) return false
        if (settings.maxRequests != 0 && history.requests >= settings.maxRequests) return false
        if (settings.oncePerVersion && history.lastRequestVersion == version) return false
        val last = history.lastRequestAt
        if (last != null && days(last, nowMillis, zone) < settings.cooldownDays) return false
        return true
    }

    /** Calendar days from [startMillis] to [endMillis]: 1 from any time yesterday. */
    fun days(
        startMillis: Long,
        endMillis: Long,
        zone: ZoneId,
    ): Long = ChronoUnit.DAYS.between(localDate(startMillis, zone), localDate(endMillis, zone))

    private fun localDate(
        millis: Long,
        zone: ZoneId,
    ): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
