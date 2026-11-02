package com.fangjet.weather.util

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import java.time.Instant
import java.time.ZoneOffset

/**
 * How old cached current conditions can be and still be shown as "now" when a refresh fails.
 * Older than this, the screen shows the cached forecast for the current hour instead.
 */
const val STALE_CURRENT_AFTER_SECONDS = 3 * 60 * 60L

/** True when this reading is too old to show as the current conditions; unknown age counts as fresh. */
fun CurrentWeather.isTooOldToShowAsNow(nowEpochSeconds: Long): Boolean =
    observedAtEpochSeconds?.let { nowEpochSeconds - it > STALE_CURRENT_AFTER_SECONDS } ?: false

/** A reading this old has its icon's day or night ending checked against the clock. */
const val DAY_NIGHT_RECHECK_AFTER_SECONDS = 30 * 60L

/**
 * [CurrentWeather.iconCode] with its day or night ending ("d" or "n") for [nowEpochSeconds] rather
 * than for when the reading was taken, from the city's sunrise and sunset by time of day. A reading
 * cached before sunset would otherwise put up the daytime hero after dark until the refresh lands.
 * Readings under half an hour old, and ones without sun times, keep OpenWeather's icon.
 */
fun CurrentWeather.iconCodeAt(nowEpochSeconds: Long): String {
    val observedAt = observedAtEpochSeconds ?: return iconCode
    val sunrise = sunriseEpoch ?: return iconCode
    val sunset = sunsetEpoch ?: return iconCode
    if (nowEpochSeconds - observedAt < DAY_NIGHT_RECHECK_AFTER_SECONDS) return iconCode
    if (!iconCode.endsWith("d") && !iconCode.endsWith("n")) return iconCode

    fun timeOfDay(epochSeconds: Long) = Math.floorMod(epochSeconds + offsetSeconds, SECONDS_PER_DAY)
    val now = timeOfDay(nowEpochSeconds)
    val rise = timeOfDay(sunrise)
    val set = timeOfDay(sunset)
    // Polar day or night, where the times can wrap past midnight: leave it to OpenWeather.
    if (rise >= set) return iconCode
    return iconCode.dropLast(1) + if (now < rise || now >= set) "n" else "d"
}

/**
 * The cached forecast's slot in progress at [nowEpochSeconds], as current conditions: temperature,
 * conditions, wind and humidity from the forecast, today's high and low, and everything else
 * (city, time zone, sunrise and sunset, observation time) from [weather]. Visibility isn't forecast,
 * so it's cleared. Null when the forecast doesn't cover now (it ran out, or has no timestamps).
 */
fun forecastAsCurrent(
    weather: CurrentWeather,
    forecast: List<DayForecast>,
    nowEpochSeconds: Long,
): CurrentWeather? {
    val slots = forecast
        .flatMap { day -> day.hourlySlots.map { day to it } }
        .filter { (_, slot) -> slot.epochSeconds != null }
        .sortedBy { (_, slot) -> slot.epochSeconds }
    val index = slots.indexOfLast { (_, slot) -> slot.epochSeconds!! <= nowEpochSeconds }
    if (index < 0) return null
    val (day, slot) = slots[index]
    // The slot must still be in progress: before the next one starts, or for the last slot, within
    // the longest slot length (3 hours).
    val end = slots.getOrNull(index + 1)?.second?.epochSeconds ?: (slot.epochSeconds!! + LONGEST_SLOT_SECONDS)
    if (nowEpochSeconds >= end) return null
    return weather.copy(
        conditionMain = conditionMainForIcon(slot.iconCode),
        description = slot.description,
        iconCode = slot.iconCode,
        temperature = slot.temperature,
        feelsLike = slot.feelsLike,
        tempMin = day.tempMin,
        tempMax = day.tempMax,
        humidityPercent = slot.humidity,
        pressureHpa = slot.pressure,
        windSpeed = slot.windSpeed,
        windDeg = slot.windDeg,
        visibilityMeters = null,
        units = slot.units,
    )
}

/** OpenWeather's condition group for an icon code ("10d" is rain), as `CurrentWeather.conditionMain`. */
internal fun conditionMainForIcon(iconCode: String): String =
    when (iconCode.take(2)) {
        "01" -> "Clear"
        "02", "03", "04" -> "Clouds"
        "09", "10" -> "Rain"
        "11" -> "Thunderstorm"
        "13" -> "Snow"
        "50" -> "Mist"
        else -> "Clouds"
    }

/** When a reading was taken, relative to today in the city's own date. */
sealed interface ReadingDay {
    data object Today : ReadingDay

    data object Yesterday : ReadingDay

    /** Earlier in the week: the day's short name, like "Sat". */
    data class Earlier(
        val dayName: String,
    ) : ReadingDay
}

fun readingDay(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
    nowEpochSeconds: Long,
): ReadingDay {
    val offset = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
    val then = Instant.ofEpochSecond(epochSeconds).atOffset(offset).toLocalDate()
    val today = Instant.ofEpochSecond(nowEpochSeconds).atOffset(offset).toLocalDate()
    return when (then) {
        today -> ReadingDay.Today
        today.minusDays(1) -> ReadingDay.Yesterday
        else -> ReadingDay.Earlier(then.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, java.util.Locale.getDefault()))
    }
}

private const val LONGEST_SLOT_SECONDS = 3 * 60 * 60L

private const val SECONDS_PER_DAY = 24 * 60 * 60L
