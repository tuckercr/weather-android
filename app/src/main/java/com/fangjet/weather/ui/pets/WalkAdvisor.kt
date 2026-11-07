package com.fangjet.weather.ui.pets

import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * The walk card's headline category: [WalkCheck]'s verdict on now, and whether there's a better
 * time. WINDY is a gale or worse, STORMY thunderstorms now or likely, and DANGER hurricane force
 * wind, which the card shows as a warning even without a dog.
 */
enum class WalkRating { GREAT, OKAY, POOR, PAWS_HOT, TOO_COLD, BAD_AIR, WINDY, STORMY, DANGER }

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
 * the next calendar day in the city's own time zone. [bestTimeEpochSeconds] is when
 * [bestTimeLabel] starts, for the walk-time notification. [outlook] is set only when now is a great
 * time. [now] is the verdict on the hour under way, which [rating] sums up and whose reason the
 * card's caption gives.
 */
data class WalkAdvice(
    val rating: WalkRating,
    val bestTimeLabel: String?,
    val pavementNow: Double? = null,
    val bestTimeIsTomorrow: Boolean = false,
    val bestTimeEpochSeconds: Long? = null,
    val outlook: WalkOutlook? = null,
    val now: WalkCheck = WalkCheck.CLEAR,
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
// values; see PetWeatherThresholds. The rules themselves are in WalkCheck.kt. Defaults: pavement under 45 C (about 113 F; at 52 C, about
// 125 F, it burns paws in under a minute), under 30% rain, 3..27 C feels-like, and 7 AM..8 PM.

/**
 * [WalkCheck]'s verdict on the hour: fine or better. Feels-like rather than air temperature, since
 * humid heat and wind chill are what the dog feels, and pavement that's only getting hot already
 * rules an hour out, so the suggested hour is a cooler one.
 */
internal fun HourlySlot.isWalkable(t: PetWeatherThresholds): Boolean = walkCheckFor(this, t).isWalkable

/**
 * Bad air (potty breaks only or worse, by default from Unhealthy) in a walking hour: an hour the
 * walk advice would otherwise have weighed up. Unknown air counts as clean.
 */
internal fun HourlySlot.hasBadAirForWalking(t: PetWeatherThresholds): Boolean {
    val band = airQuality?.band ?: return false
    return isWalkingTime(t) && airWalkLevel(band, t) >= WalkLevel.POTTY_ONLY
}

/** Whether the current conditions are rain, drizzle, snow or a thunderstorm (as [petMoodFor] reads them). */
fun isPrecipitating(conditionMain: String): Boolean = conditionMain.lowercase() in setOf("thunderstorm", "snow", "rain", "drizzle")

/**
 * Works out when to walk the dog from the current feels-like temperature and the upcoming hourly slots
 * (the first slot is the next forecast window, so it stands in for "now"). Returns null when
 * there are no slots yet (forecast loading or failed), since there is nothing to advise on.
 *
 * [conditionNow] is the current observation's condition group ("Rain", "Thunderstorm"). The hourly
 * forecast can say the hour in progress is dry while it is raining, so when it's raining now, now
 * is never the walk time.
 * [sunsetEpochSeconds] is today's sunset, for the "gets dark" line. [darkNow] is the clock's word
 * on darkness: the hour in progress keeps its daytime icon for the whole hour the sun sets in, so
 * without it now could be "a great time" for up to an hour after dark.
 */
fun walkAdviceFor(
    currentFeelsLikeC: Double,
    upcoming: List<HourlySlot>,
    thresholds: PetWeatherThresholds = PetWeatherThresholds.DEFAULT,
    // The city's UTC offset (CurrentWeather.offsetSeconds): "tomorrow" means the city's tomorrow.
    utcOffsetSeconds: Int = 0,
    nowEpochSeconds: Long = Instant.now().epochSecond,
    conditionNow: String? = null,
    sunsetEpochSeconds: Long? = null,
    darkNow: Boolean = false,
): WalkAdvice? {
    val slots = upcoming
        .take(LOOKAHEAD_SLOTS)
        .filter { slot -> slot.epochSeconds.let { it == null || it < nowEpochSeconds + LOOKAHEAD_SECONDS } }
    if (slots.isEmpty()) return null
    val check = walkCheckFor(slots.first(), thresholds, currentFeelsLikeC, conditionNow, darkNow)
    val firstGood = slots.drop(1).firstOrNull { it.isWalkable(thresholds) }
    val rating = walkRating(check, hasBetterTime = firstGood != null)
    val bestSlot = if (rating == WalkRating.GREAT) null else firstGood
    val bestIsTomorrow = bestSlot?.epochSeconds?.let { isNextDay(it, nowEpochSeconds, utcOffsetSeconds) } == true
    val outlook = if (rating == WalkRating.GREAT) walkOutlook(slots, check, sunsetEpochSeconds, nowEpochSeconds, thresholds) else null
    return WalkAdvice(
        rating,
        bestSlot?.timeLabel,
        slots.first().pavementTemperature,
        bestIsTomorrow,
        bestSlot?.epochSeconds,
        outlook,
        check,
    )
}

/** Potty breaks only or worse says why; otherwise it's a walk now, a later walk, or no walk today. */
internal fun walkRating(
    check: WalkCheck,
    hasBetterTime: Boolean,
): WalkRating {
    if (check.level == WalkLevel.DANGER) return WalkRating.DANGER
    if (check.level >= WalkLevel.POTTY_ONLY) {
        when (check.reason) {
            is WalkReason.Pavement, WalkReason.Heat -> return WalkRating.PAWS_HOT
            is WalkReason.Air -> return WalkRating.BAD_AIR
            WalkReason.Cold -> return WalkRating.TOO_COLD
            is WalkReason.Wind -> return WalkRating.WINDY
            is WalkReason.Storms -> return WalkRating.STORMY
            else -> Unit
        }
    }
    return when {
        check.isWalkable -> WalkRating.GREAT
        hasBetterTime -> WalkRating.OKAY
        else -> WalkRating.POOR
    }
}

/**
 * For a walk now: when the good spell ends and why, if that's within a few hours. Otherwise air
 * that's not quite bad, or sunset coming up.
 */
private fun walkOutlook(
    slots: List<HourlySlot>,
    check: WalkCheck,
    sunsetEpochSeconds: Long?,
    nowEpochSeconds: Long,
    t: PetWeatherThresholds,
): WalkOutlook? {
    val end = slots.drop(1).firstOrNull { !it.isWalkable(t) }
    val change = end
        ?.takeIf { (it.epochSeconds ?: Long.MAX_VALUE) <= nowEpochSeconds + OUTLOOK_SECONDS }
        ?.let { walkChange(walkCheckFor(it, t)) }
    if (end != null && change != null) return WalkOutlook.Until(end.timeLabel, change)
    if (check.air?.level == WalkLevel.FINE) return WalkOutlook.ModerateAir
    val untilSunset = sunsetEpochSeconds?.minus(nowEpochSeconds) ?: return null
    if (untilSunset <= 0 || untilSunset > DARK_SOON_SECONDS) return null
    return WalkOutlook.DarkIn((untilSunset / 3600.0).roundToInt())
}

/**
 * Why an hour that isn't walkable isn't, as something worth saying. Null after dark and outside the
 * walking hours, which the sunset line covers.
 */
private fun walkChange(check: WalkCheck): WalkChange? {
    if (check.factors.any { it.reason == WalkReason.Dark }) return null
    return when (val reason = check.reason) {
        is WalkReason.Storms -> if (reason.likely) WalkChange.STORMS else WalkChange.MAYBE_STORMS
        is WalkReason.Rain -> if (reason.likely) WalkChange.RAIN else WalkChange.MAYBE_RAIN
        is WalkReason.Snow -> if (reason.likely) WalkChange.SNOW else WalkChange.MAYBE_SNOW
        is WalkReason.Wind -> WalkChange.WIND
        is WalkReason.Pavement -> WalkChange.HOT_PAVEMENT
        WalkReason.Heat -> WalkChange.HEAT
        is WalkReason.Air -> WalkChange.BAD_AIR
        WalkReason.Cold -> WalkChange.COLD
        WalkReason.Dark, null -> null
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
