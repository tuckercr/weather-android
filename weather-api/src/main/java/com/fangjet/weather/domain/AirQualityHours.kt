package com.fangjet.weather.domain

import kotlinx.serialization.Serializable

/**
 * A city's hourly air quality as fetched, with when and on which scale, cached per city so it's
 * fetched at most every [MAX_AGE_SECONDS]: it changes slowly, and each fetch holds three days of
 * hours. Same rules as iOS's `AirQualityHours`.
 */
@Serializable
data class AirQualityHours(
    val fetchedAtEpochSeconds: Long,
    val scale: AirQuality.Scale,
    // Each hour's air quality by the hour's start, Unix seconds. Hours without an index are left out.
    val hours: Map<Long, AirQuality>,
) {
    /**
     * Whether a forecast refresh at [nowEpochSeconds] can use these hours instead of fetching: fetched
     * under [MAX_AGE_SECONDS] ago, on the city's [scale], and reaching from the hour in progress (the
     * tile's) to [COVERAGE_SECONDS] ahead.
     */
    fun reusable(
        scale: AirQuality.Scale,
        nowEpochSeconds: Long,
    ): Boolean {
        val age = nowEpochSeconds - fetchedAtEpochSeconds
        if (this.scale != scale || age < 0 || age >= MAX_AGE_SECONDS) return false
        val firstHourStart = hours.keys.minOrNull() ?: return false
        val lastHourEnd = hours.keys.max() + HOUR_SECONDS
        return firstHourStart <= nowEpochSeconds && lastHourEnd >= nowEpochSeconds + COVERAGE_SECONDS
    }

    companion object {
        /** How long fetched air quality is used before it's fetched again: 3 hours. */
        const val MAX_AGE_SECONDS = 3 * 60 * 60L

        /** How far ahead reused hours must reach: the next 24 hours, which the screen and walk planner read. */
        const val COVERAGE_SECONDS = 24 * 60 * 60L

        private const val HOUR_SECONDS = 60 * 60L
    }
}
