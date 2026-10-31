package com.fangjet.weather.ui.pets

import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.pavementBand
import com.fangjet.weather.util.windForce
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

enum class WalkRating { GREAT, OKAY, POOR, PAWS_HOT, TOO_COLD, BAD_AIR }

/** What changes after a walk window that's open now, for the card's second line. */
sealed interface WalkOutlook {
    /** Good until [timeLabel], then [change]. */
    data class Until(
        val timeLabel: String,
        val change: WalkChange,
    ) : WalkOutlook

    /** Sunset in about [hours] (0 is under half an hour). */
    data class DarkIn(
        val hours: Int,
    ) : WalkOutlook

    /** Air a band short of bad: fine for most dogs, not for older or flat-faced ones. */
    data object ModerateAir : WalkOutlook
}

/**
 * Why the walk window ends. The MAYBE kinds are a chance of rain at or above the walk limit but
 * under [LIKELY_RAIN_CHANCE].
 */
enum class WalkChange { STORMS, MAYBE_STORMS, RAIN, MAYBE_RAIN, SNOW, MAYBE_SNOW, WIND, HEAT, HOT_PAVEMENT, COLD, BAD_AIR }

/**
 * [bestTimeLabel] is null when now is a good time, or when there is no good window soon.
 * [pavementNow] is the estimated pavement temperature for the current hour, in the forecast's
 * units, when the source provides it. [bestTimeIsTomorrow] is true when [bestTimeLabel] falls on
 * the next calendar day in the city's own time zone. [pavementBand] is what [pavementNow] means for
 * paws (the Today tile's band), so the caption can say whether the pavement is the problem.
 * [bestTimeEpochSeconds] is when [bestTimeLabel] starts, for the walk-time notification.
 * [outlook] is set only when now is a great time.
 */
data class WalkAdvice(
    val rating: WalkRating,
    val bestTimeLabel: String?,
    val pavementNow: Double? = null,
    val bestTimeIsTomorrow: Boolean = false,
    val pavementBand: PavementBand? = null,
    val bestTimeEpochSeconds: Long? = null,
    val outlook: WalkOutlook? = null,
)

private const val LOOKAHEAD_SLOTS = 24 // a day of hourly slots
private const val LOOKAHEAD_SECONDS = 24 * 60 * 60L // and never more than a day, for 3-hourly slots

/** A walk window is only described when it closes within this long; further out it isn't news. */
private const val OUTLOOK_SECONDS = 6 * 60 * 60L

/** Sunset this close is worth a mention. */
private const val DARK_SOON_SECONDS = 3 * 60 * 60L

/** A chance of rain at or above this is "rain"; from the walk limit up to it, "maybe rain". */
internal const val LIKELY_RAIN_CHANCE = 60

// The walk thresholds (pavement heat, rain chance, comfort range, walking hours) are Remote Config
// values; see PetWeatherThresholds. Defaults: pavement under 45 C (about 113 F; at 52 C, about
// 125 F, it burns paws in under a minute), under 30% rain, 3..27 C feels-like, and 7 AM..8 PM.

/**
 * Nobody wants walk advice for 2 AM: only suggest slots within the walking hours that are also in
 * daylight. The walking hours alone are a fixed clock window, so on a winter morning 7 AM can be
 * before sunrise; a night icon ("n", the same signal that puts the pets to sleep) rules it out.
 */
private fun HourlySlot.isDaytime(t: PetWeatherThresholds): Boolean =
    (localHour?.let { it in t.walkHoursStart..t.walkHoursEnd } ?: true) && !iconCode.endsWith("n")

private fun HourlySlot.pavementC(): Double? = pavementTemperature?.let { units.toCelsius(it) }

private fun HourlySlot.isTooWindy(t: PetWeatherThresholds): Boolean = windForce(windSpeed, units).ordinal >= t.windyForce

/**
 * Feels-like rather than air temperature: humid heat and wind chill are what the dog feels. Pavement
 * that's only getting hot already rules an hour out, so the suggested hour is a cooler one.
 */
private fun HourlySlot.isWalkable(t: PetWeatherThresholds): Boolean {
    val c = units.toCelsius(feelsLike)
    val pavementOk = (pavementC() ?: 0.0) < t.warmPavementC
    return isDaytime(t) &&
        !hasBadAir(t) &&
        !isTooWindy(t) &&
        pavementOk &&
        precipitationChance < t.maxRainChancePercent &&
        c in t.comfortMinC..t.comfortMaxC
}

/**
 * Air at the Remote Config band or worse (by default Unhealthy, the Air quality tile's "Potty breaks
 * only today"). Unknown counts as clean.
 */
private fun HourlySlot.hasBadAir(t: PetWeatherThresholds): Boolean = (airQuality?.band ?: 0) >= t.badAirBand

/** Bad air in a walking hour: an hour the walk advice would otherwise have weighed up. */
internal fun HourlySlot.hasBadAirForWalking(t: PetWeatherThresholds): Boolean = isDaytime(t) && hasBadAir(t)

/** Whether the current conditions are rain, drizzle, snow or a thunderstorm (as [petMoodFor] reads them). */
fun isPrecipitating(conditionMain: String): Boolean = conditionMain.lowercase() in setOf("thunderstorm", "snow", "rain", "drizzle")

/**
 * Works out when to walk the dog from the current feels-like temperature and the upcoming hourly slots
 * (the first slot is the next forecast window, so it stands in for "now"). Returns null when
 * there are no slots yet (forecast loading or failed), since there is nothing to advise on.
 *
 * [precipitatingNow] is the current observation. The hourly forecast can say the hour in progress
 * is dry while it is raining, so when it's raining now, now is never the walk time.
 * [sunsetEpochSeconds] is today's sunset, for the "gets dark" line.
 */
fun walkAdviceFor(
    currentFeelsLikeC: Double,
    upcoming: List<HourlySlot>,
    thresholds: PetWeatherThresholds = PetWeatherThresholds.DEFAULT,
    // The city's UTC offset (CurrentWeather.offsetSeconds): "tomorrow" means the city's tomorrow.
    utcOffsetSeconds: Int = 0,
    nowEpochSeconds: Long = Instant.now().epochSecond,
    precipitatingNow: Boolean = false,
    sunsetEpochSeconds: Long? = null,
): WalkAdvice? {
    val slots = upcoming
        .take(LOOKAHEAD_SLOTS)
        .filter { slot -> slot.epochSeconds.let { it == null || it < nowEpochSeconds + LOOKAHEAD_SECONDS } }
    if (slots.isEmpty()) return null
    val nowWalkable = !precipitatingNow && slots.first().isWalkable(thresholds)
    val firstGood = (if (precipitatingNow) slots.drop(1) else slots).firstOrNull { it.isWalkable(thresholds) }
    val pavementNow = slots.first().pavementTemperature
    val pavementHotNow = (slots.first().pavementC() ?: 0.0) >= thresholds.hotPavementC
    val rating = when {
        currentFeelsLikeC >= thresholds.petHotC || pavementHotNow -> WalkRating.PAWS_HOT
        slots.first().hasBadAir(thresholds) -> WalkRating.BAD_AIR
        currentFeelsLikeC <= thresholds.petColdC -> WalkRating.TOO_COLD
        nowWalkable -> WalkRating.GREAT
        firstGood != null -> WalkRating.OKAY
        else -> WalkRating.POOR
    }
    val bestSlot = if (rating == WalkRating.GREAT) null else firstGood
    val bestIsTomorrow = bestSlot?.epochSeconds?.let { isNextDay(it, nowEpochSeconds, utcOffsetSeconds) } == true
    val band = slots.first().pavementC()?.let { pavementBand(it, slots.first().groundWet, currentFeelsLikeC, thresholds) }
    val outlook = if (rating == WalkRating.GREAT) walkOutlook(slots, sunsetEpochSeconds, nowEpochSeconds, thresholds) else null
    return WalkAdvice(rating, bestSlot?.timeLabel, pavementNow, bestIsTomorrow, band, bestSlot?.epochSeconds, outlook)
}

/**
 * For a walk now: when the good spell ends and why, if that's within a few hours. Otherwise air
 * that's not quite bad, or sunset coming up.
 */
private fun walkOutlook(
    slots: List<HourlySlot>,
    sunsetEpochSeconds: Long?,
    nowEpochSeconds: Long,
    t: PetWeatherThresholds,
): WalkOutlook? {
    val end = slots.drop(1).firstOrNull { !it.isWalkable(t) }
    val change = end?.takeIf { (it.epochSeconds ?: Long.MAX_VALUE) <= nowEpochSeconds + OUTLOOK_SECONDS }?.changeHere(t)
    if (end != null && change != null) return WalkOutlook.Until(end.timeLabel, change)
    val band = slots.first().airQuality?.band ?: 0
    if (band >= 2 && band < t.badAirBand) return WalkOutlook.ModerateAir
    val untilSunset = sunsetEpochSeconds?.minus(nowEpochSeconds) ?: return null
    if (untilSunset <= 0 || untilSunset > DARK_SOON_SECONDS) return null
    return WalkOutlook.DarkIn((untilSunset / 3600.0).roundToInt())
}

/**
 * Why this slot isn't walkable, as something worth saying. Null for darkness and the end of the
 * walking hours, which the sunset line covers.
 */
private fun HourlySlot.changeHere(t: PetWeatherThresholds): WalkChange? {
    if (!isDaytime(t)) return null
    if (precipitationChance >= t.maxRainChancePercent) {
        val likely = precipitationChance >= LIKELY_RAIN_CHANCE
        return when (iconCode.take(2)) {
            "11" -> if (likely) WalkChange.STORMS else WalkChange.MAYBE_STORMS
            "13" -> if (likely) WalkChange.SNOW else WalkChange.MAYBE_SNOW
            else -> if (likely) WalkChange.RAIN else WalkChange.MAYBE_RAIN
        }
    }
    val feelsLikeC = units.toCelsius(feelsLike)
    return when {
        isTooWindy(t) -> WalkChange.WIND
        hasBadAir(t) -> WalkChange.BAD_AIR
        (pavementC() ?: 0.0) >= t.warmPavementC -> WalkChange.HOT_PAVEMENT
        feelsLikeC > t.comfortMaxC -> WalkChange.HEAT
        feelsLikeC < t.comfortMinC -> WalkChange.COLD
        else -> null
    }
}

/** True when [epochSeconds] falls on the calendar day after [nowEpochSeconds], in the city's zone. */
private fun isNextDay(
    epochSeconds: Long,
    nowEpochSeconds: Long,
    utcOffsetSeconds: Int,
): Boolean {
    val zone = ZoneOffset.ofTotalSeconds(utcOffsetSeconds)
    // atOffset().toLocalDate() rather than LocalDate.ofInstant, which needs API 31.
    val today = Instant.ofEpochSecond(nowEpochSeconds).atOffset(zone).toLocalDate()
    return Instant.ofEpochSecond(epochSeconds).atOffset(zone).toLocalDate() == today.plusDays(1)
}
