package com.fangjet.weather.util

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

// Today's forecast and its high and low, as the screen and the notifications both find them. Same
// as iOS's `isDay(of:in:)` and `TodayDetails.highLow`.

/**
 * Whether this is the day of [epochSeconds] in the city's time ([utcOffsetSeconds]): by its hours'
 * timestamps, or by its label for a forecast cached before they were recorded.
 */
fun DayForecast.isDayOf(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
): Boolean {
    val date = localDate(epochSeconds, utcOffsetSeconds)
    val start = hourlySlots.firstNotNullOfOrNull { it.epochSeconds }
    return if (start != null) {
        localDate(start, utcOffsetSeconds) == date
    } else {
        dateLabel == date.format(DateTimeFormatter.ofPattern("EEE, MMM d"))
    }
}

/** The forecast for the day of [epochSeconds] in the city's time; null beyond the forecast. */
fun List<DayForecast>.dayOf(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
): DayForecast? = firstOrNull { it.isDayOf(epochSeconds, utcOffsetSeconds) }

/**
 * Today's high and low: today's forecast, widened to take in the temperature now. OpenWeather's
 * current `temp_max`/`temp_min` are the spread across nearby stations at this moment, not the day's
 * range, so they're only the fallback until today's forecast is in. The hero and the morning
 * briefing both give these.
 */
fun todayHighLow(
    weather: CurrentWeather,
    today: DayForecast?,
): Pair<Double, Double> =
    if (today == null) {
        weather.tempMax to weather.tempMin
    } else {
        maxOf(today.tempMax, weather.temperature) to minOf(today.tempMin, weather.temperature)
    }

/** The date of [epochSeconds] in the city's time ([utcOffsetSeconds]). */
fun localDate(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
): LocalDate =
    Instant
        .ofEpochSecond(epochSeconds)
        .atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        .toLocalDate()
