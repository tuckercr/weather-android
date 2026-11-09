package com.fangjet.weather.util

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot

/**
 * A sunrise or a sunset, shown in the hourly strip as a slim marker between the hours it falls
 * between ("6:50" between 6 PM and 7 PM). Same rules as iOS's `Utilities/SunEvents.swift`.
 */
data class SunEvent(
    val sunrise: Boolean,
    val epochSeconds: Long,
)

/** One cell of the hourly strip: an hour, or a sunrise or sunset between two of them. */
sealed interface HourlyStripItem {
    data class Hour(
        val slot: HourlySlot,
    ) : HourlyStripItem

    data class Sun(
        val event: SunEvent,
    ) : HourlyStripItem
}

/**
 * The forecast's sunrises and sunsets, from Open-Meteo. When it has none (the OpenWeather 3-hourly
 * fallback, or a forecast cached before they were kept), today's from [weather], the current
 * reading with its sun times moved to today, so the strip has no tomorrow's sunrise.
 */
fun sunEvents(
    forecast: List<DayForecast>,
    weather: CurrentWeather,
): List<SunEvent> {
    val fromForecast = forecast
        .flatMap { day ->
            listOfNotNull(
                day.sunriseEpoch?.let { SunEvent(sunrise = true, epochSeconds = it) },
                day.sunsetEpoch?.let { SunEvent(sunrise = false, epochSeconds = it) },
            )
        }.sortedBy { it.epochSeconds }
    if (fromForecast.isNotEmpty()) return fromForecast
    val sunrise = weather.sunriseEpoch ?: return emptyList()
    val sunset = weather.sunsetEpoch ?: return emptyList()
    // A sunrise after the sunset isn't a day (polar day or night), as for the Daylight tile.
    if (sunrise >= sunset) return emptyList()
    return listOf(SunEvent(sunrise = true, epochSeconds = sunrise), SunEvent(sunrise = false, epochSeconds = sunset))
}

/**
 * The hours with each sunrise and sunset still to come placed before the first hour starting at or
 * after it, so a 6:50 sunset sits between 6 PM and 7 PM, and one at 7:00 before 7 PM. One before the
 * first hour or after the last starts isn't between two of them, so it's left out.
 */
fun hourlyStrip(
    slots: List<HourlySlot>,
    events: List<SunEvent>,
    nowEpochSeconds: Long,
): List<HourlyStripItem> {
    val before = events
        .filter { it.epochSeconds > nowEpochSeconds }
        .mapNotNull { event ->
            val index = slots.indexOfFirst { slot -> slot.epochSeconds.let { it != null && it >= event.epochSeconds } }
            if (index > 0) index to event else null
        }.groupBy({ it.first }, { it.second })
    return slots.flatMapIndexed { index, slot ->
        before[index].orEmpty().sortedBy { it.epochSeconds }.map { HourlyStripItem.Sun(it) } + HourlyStripItem.Hour(slot)
    }
}
