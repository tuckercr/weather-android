package com.fangjet.weather.util

import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.WeatherUnits
import kotlin.math.ln
import kotlin.math.roundToInt

// The rules behind the Today tiles, kept free of Compose so they can be tested. Same rules as
// iOS's Utilities/TodayDetails.swift.

/** How far you can see, in the bands the Sky tile names. */
enum class VisibilityBand { EXCELLENT, GOOD, MODERATE, POOR }

/** OpenWeather reports visibility up to 10 km and no further, so 10 km means "at least 10 km". */
fun visibilityBand(meters: Int): VisibilityBand =
    when {
        meters >= 10_000 -> VisibilityBand.EXCELLENT
        meters >= 5_000 -> VisibilityBand.GOOD
        meters >= 1_000 -> VisibilityBand.MODERATE
        else -> VisibilityBand.POOR
    }

enum class DistanceUnit { KILOMETERS, MILES, METERS }

data class Distance(
    val value: Double,
    val unit: DistanceUnit,
    val decimals: Int,
)

private const val METERS_PER_MILE = 1609.344

/**
 * Visibility as a number to show beside its band, or null at OpenWeather's 10 km cap, where
 * "Excellent visibility" says it all (and 10 km would read as an odd 6.2 mi). Whole kilometres
 * or miles, except under 1 km: tenths of a mile, or metres to the nearest 100.
 */
fun visibilityDistance(
    meters: Int,
    inMiles: Boolean,
): Distance? {
    if (visibilityBand(meters) == VisibilityBand.EXCELLENT) return null
    val miles = meters / METERS_PER_MILE
    return when {
        inMiles && meters < 1_000 -> Distance((miles * 10).roundToInt().coerceAtLeast(1) / 10.0, DistanceUnit.MILES, 1)
        inMiles -> Distance(miles.roundToInt().coerceAtLeast(1).toDouble(), DistanceUnit.MILES, 0)
        meters < 1_000 -> Distance(((meters / 100.0).roundToInt().coerceAtLeast(1) * 100).toDouble(), DistanceUnit.METERS, 0)
        else -> Distance((meters / 1_000.0).roundToInt().toDouble(), DistanceUnit.KILOMETERS, 0)
    }
}

/** The Sky tile's word for the sky. */
enum class SkyCondition { CLEAR, PARTLY_CLOUDY, MOSTLY_CLOUDY, OVERCAST, MIST, HAZE, FOG, SMOKE, DUST }

/**
 * Mist, haze, fog, smoke and dust come from OpenWeather's condition group, since they are what
 * limits the view; otherwise the cloud cover decides. Without a cloud cover (the forecast standing
 * in for an old reading has none), the icon's cloud level decides instead.
 */
fun skyCondition(
    conditionMain: String,
    cloudPercent: Int?,
    iconCode: String,
): SkyCondition {
    when (conditionMain.lowercase()) {
        "mist" -> return SkyCondition.MIST
        "haze" -> return SkyCondition.HAZE
        "fog" -> return SkyCondition.FOG
        "smoke", "ash" -> return SkyCondition.SMOKE
        "dust", "sand" -> return SkyCondition.DUST
    }
    if (cloudPercent == null) {
        return when (iconCode.take(2)) {
            "01" -> SkyCondition.CLEAR
            "02" -> SkyCondition.PARTLY_CLOUDY
            "03" -> SkyCondition.MOSTLY_CLOUDY
            else -> SkyCondition.OVERCAST
        }
    }
    return when {
        cloudPercent <= 10 -> SkyCondition.CLEAR
        cloudPercent <= 50 -> SkyCondition.PARTLY_CLOUDY
        cloudPercent <= 84 -> SkyCondition.MOSTLY_CLOUDY
        else -> SkyCondition.OVERCAST
    }
}

/**
 * What the pavement means for paws, from the Remote Config thresholds the walk advice uses.
 * [PAVEMENT_OK] is pavement that's fine while the air itself is too hot or too cold for paws, so
 * the tile doesn't call it "Safe for paws" beside a walk card saying the opposite.
 */
enum class PavementBand { DANGEROUS, TOO_HOT, WARM, SAFE, PAVEMENT_OK, FREEZING, ICY }

/**
 * Below freezing it's only [PavementBand.ICY] after rain or snow ([groundWet]); dry, it's just cold.
 * [feelsLikeC] is the air's feels-like temperature, compared with the pets' hot and cold limits.
 */
fun pavementBand(
    pavementC: Double,
    groundWet: Boolean,
    feelsLikeC: Double,
    thresholds: PetWeatherThresholds,
): PavementBand =
    when {
        pavementC >= thresholds.dangerousPavementC -> PavementBand.DANGEROUS
        pavementC >= thresholds.hotPavementC -> PavementBand.TOO_HOT
        pavementC >= thresholds.warmPavementC -> PavementBand.WARM
        pavementC < thresholds.icyPavementC -> if (groundWet) PavementBand.ICY else PavementBand.FREEZING
        feelsLikeC >= thresholds.petHotC || feelsLikeC <= thresholds.petColdC -> PavementBand.PAVEMENT_OK
        else -> PavementBand.SAFE
    }

private val NUMBER_BEFORE_UNIT = Regex("""(\d)[ \u202F](?=°|%|mph\b|m/s|km\b|mi\b|m\b|AM\b|PM\b)""")

/**
 * Joins each number to its unit with a no-break space, so large text never leaves "°F", "mph" or
 * "PM" stranded on a line of its own. A time formatter's narrow no-break space before AM/PM is
 * replaced too, as on iOS, where SwiftUI still wraps at it.
 */
fun keepUnitsTogether(text: String): String = text.replace(NUMBER_BEFORE_UNIT, "$1\u00A0")

/** Dew point in Celsius (Magnus formula); null when the humidity is 0, where it's undefined. */
fun dewPointC(
    temperatureC: Double,
    humidityPercent: Int,
): Double? {
    if (humidityPercent <= 0) return null
    val a = 17.62
    val b = 243.12
    val gamma = ln(humidityPercent.coerceAtMost(100) / 100.0) + a * temperatureC / (b + temperatureC)
    return b * gamma / (a - gamma)
}

/** The Beaufort scale's names, which the Wind tile shows under the speed. */
enum class WindForce {
    CALM,
    LIGHT_AIR,
    LIGHT_BREEZE,
    GENTLE_BREEZE,
    MODERATE_BREEZE,
    FRESH_BREEZE,
    STRONG_BREEZE,
    NEAR_GALE,
    GALE,
    STRONG_GALE,
    STORM,
    VIOLENT_STORM,
    HURRICANE,
}

private const val METERS_PER_SECOND_PER_MPH = 0.44704

// The WMO upper bounds of each force, in m/s.
private val BEAUFORT_UPPER_BOUNDS = listOf(0.3, 1.6, 3.4, 5.5, 8.0, 10.8, 13.9, 17.2, 20.8, 24.5, 28.5, 32.7)

fun windForce(
    speed: Double,
    units: WeatherUnits,
): WindForce {
    val metersPerSecond = if (units == WeatherUnits.IMPERIAL) speed * METERS_PER_SECOND_PER_MPH else speed
    val index = BEAUFORT_UPPER_BOUNDS.indexOfFirst { metersPerSecond < it }.let { if (it == -1) BEAUFORT_UPPER_BOUNDS.size else it }
    return WindForce.entries[index]
}

/** Where the day is between sunrise and sunset, for the Daylight tile's arc. */
sealed interface Daylight {
    data class BeforeSunrise(
        val secondsUntil: Long,
    ) : Daylight

    data class Daytime(
        val secondsLeft: Long,
        /** 0 at sunrise, 1 at sunset. */
        val fraction: Float,
    ) : Daylight

    data object AfterSunset : Daylight
}

fun daylight(
    nowEpochSeconds: Long,
    sunriseEpochSeconds: Long,
    sunsetEpochSeconds: Long,
): Daylight =
    when {
        nowEpochSeconds < sunriseEpochSeconds -> Daylight.BeforeSunrise(sunriseEpochSeconds - nowEpochSeconds)
        nowEpochSeconds >= sunsetEpochSeconds || sunsetEpochSeconds <= sunriseEpochSeconds -> Daylight.AfterSunset
        else -> Daylight.Daytime(
            secondsLeft = sunsetEpochSeconds - nowEpochSeconds,
            fraction = (nowEpochSeconds - sunriseEpochSeconds).toFloat() / (sunsetEpochSeconds - sunriseEpochSeconds),
        )
    }

/** What the UV tile says under the index. */
sealed interface UvLine {
    /** Today's peak is still ahead: "Peak 8 at 1 PM". */
    data class Peak(
        val uv: Int,
        val timeLabel: String,
    ) : UvLine

    /** From a high peak (6 and up) on, with a dog: "Shade for pale noses", in the pets' colour. */
    data object PaleNoses : UvLine

    data object AtPeak : UvLine

    data object EasingOff : UvLine
}

/** The UV tile's line from today's highest-UV hour; null without one. Same rules as iOS. */
fun uvLine(
    peak: HourlySlot?,
    nowEpochSeconds: Long,
    hasDog: Boolean,
): UvLine? {
    val uv = peak?.uvIndex?.roundToInt() ?: return null
    val start = peak.epochSeconds ?: return UvLine.Peak(uv, peak.timeLabel)
    return when {
        start > nowEpochSeconds -> UvLine.Peak(uv, peak.timeLabel)
        hasDog && uv >= 6 -> UvLine.PaleNoses
        start + 3600 > nowEpochSeconds -> UvLine.AtPeak
        else -> UvLine.EasingOff
    }
}
