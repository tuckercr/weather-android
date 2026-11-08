package com.fangjet.weather.util

// Plain formatting shared by the screens and the notifications.

/**
 * A chance of rain as shown: nothing under 10%, where it's noise (most dry hours forecast 1%), and
 * otherwise to the nearest 10%, like Apple and Google Weather, since the forecast isn't more precise.
 */
fun displayedRainChance(percent: Int): Int? = if (percent < 10) null else (percent + 5) / 10 * 10

fun formatEpochTime(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
): String {
    val localTime = java.time.Instant
        .ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        .toLocalTime()
    return localTime.format(
        java.time.format.DateTimeFormatter
            .ofPattern("h:mm a"),
    )
}

/** A speed that would show as 0 (whole mph, or m/s to one decimal): written as a plain 0, with no direction. */
fun isCalmWind(
    speed: Double,
    units: com.fangjet.weather.domain.WeatherUnits,
): Boolean = speed < (if (units == com.fangjet.weather.domain.WeatherUnits.IMPERIAL) 0.5 else 0.05)

/** The compass point a wind comes from, 0 (N) to 7 (NW) clockwise; indexes `R.array.compass_points_spoken`. */
fun windDirectionIndex(deg: Int): Int = ((deg + 22) / 45) % 8
