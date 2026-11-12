package com.fangjet.weather.domain

import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.fromCurrentSlot
import com.fangjet.weather.util.isDarkAt
import com.fangjet.weather.util.windForce
import com.fangjet.weather.util.withSunTimesToday
import java.time.Instant
import java.time.ZoneOffset
import kotlin.math.roundToInt

/**
 * The walk card's headline category: [WalkCheck]'s verdict on now, and whether there's a better
 * time. WINDY is a gale or worse, STORMY thunderstorms now or likely, and DANGER hurricane force
 * wind now, which the card shows as a warning even without a dog. Hurricane force wind later is
 * [WalkWarning.Hurricane], not DANGER: the rating is about the hour under way.
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
 * A warning about the next few hours, apart from the verdict on now: hurricane force wind or likely
 * thunderstorms from [timeLabel] (an hour, such as "6 PM"). Same as iOS's `WalkWarning`.
 */
sealed interface WalkWarning {
    val timeLabel: String

    data class Hurricane(
        override val timeLabel: String,
    ) : WalkWarning

    data class Storms(
        override val timeLabel: String,
    ) : WalkWarning
}

/**
 * A spell of walkable hours: from [startLabel] to [endLabel], the first hour after it that isn't
 * walkable (null when the forecast ends first). [isTomorrow] when it starts on the city's next day.
 * Same as iOS's `WalkWindow`.
 */
data class WalkWindow(
    val startLabel: String,
    val endLabel: String? = null,
    val isTomorrow: Boolean = false,
)

/**
 * [bestTimeLabel] is null when now is a good time, or when there is no good window soon.
 * [pavementNow] is the estimated pavement temperature for the current hour, in the forecast's
 * units, when the source provides it. [bestTimeIsTomorrow] is true when [bestTimeLabel] falls on
 * the next calendar day in the city's own time zone. [bestTimeEpochSeconds] is when
 * [bestTimeLabel] starts, for the walk-time notification. [outlook] is set only when now is a great
 * time. [now] is the verdict on the hour under way, which [rating] sums up and whose reason the
 * card's caption gives. [warning] is hurricane force wind or likely storms in the next few hours that
 * aren't already happening now. [bestTimeEndLabel] is when the walkable spell from the best time
 * ends (the first hour after it that isn't walkable), null when the forecast ends first.
 * [nextWindow] is the spell after that one (or after the one under way, for a walk now), within the
 * day ahead: a second option for people who can't go at the first.
 */
data class WalkAdvice(
    val rating: WalkRating,
    val bestTimeLabel: String?,
    val pavementNow: Double? = null,
    val bestTimeIsTomorrow: Boolean = false,
    val bestTimeEpochSeconds: Long? = null,
    val outlook: WalkOutlook? = null,
    val now: WalkCheck = WalkCheck.CLEAR,
    val warning: WalkWarning? = null,
    val bestTimeEndLabel: String? = null,
    val nextWindow: WalkWindow? = null,
) {
    /** Hurricane force wind now or ahead: the card turns into a warning, shown even without a dog. */
    val isDanger: Boolean get() = rating == WalkRating.DANGER || warning is WalkWarning.Hurricane
}

private const val LOOKAHEAD_SLOTS = 24 // a day of hourly slots
private const val LOOKAHEAD_SECONDS = 24 * 60 * 60L // and never more than a day, for 3-hourly slots

/** A walk window is only described when it closes within this long; further out it isn't news. */
private const val OUTLOOK_SECONDS = 6 * 60 * 60L

/** Sunset this close is worth a mention. */
private const val DARK_SOON_SECONDS = 3 * 60 * 60L

/** Hurricane force wind or likely storms forecast this soon are worth a warning: time to get everyone inside. */
private const val DANGER_AHEAD_SECONDS = 6 * 60 * 60L

/** A chance of rain at or above this is "rain"; from the walk limit up to it, "maybe rain". */
internal const val LIKELY_RAIN_CHANCE = 60

// The walk thresholds (pavement heat, rain chance, comfort range, walking hours) are Remote Config
// values; see PetWeatherThresholds. The rules themselves are in WalkCheck.kt. Defaults: pavement under 40 C (about 104 F; at 46 C, about
// 115 F, it can burn paws), under 30% rain, 3..27 C feels-like, and 7 AM..8 PM.

/**
 * [WalkCheck]'s verdict on the hour: fine or better. Feels-like rather than air temperature, since
 * humid heat and wind chill are what the dog feels, and pavement that's only getting hot already
 * rules an hour out, so the suggested hour is a cooler one.
 */
internal fun HourlySlot.isWalkable(t: PetWeatherThresholds): Boolean = isWalkableHour(this, t)

/**
 * Bad air (potty breaks only or worse, by default from Unhealthy) in a walking hour: an hour the
 * walk advice would otherwise have weighed up. Unknown air counts as clean.
 */
internal fun HourlySlot.hasBadAirForWalking(t: PetWeatherThresholds): Boolean {
    if (!t.airQualityEnabled) return false
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
 * is never the walk time. [windForceNow] and [gustForceNow] are the current reading's wind and
 * gusts, as the Wind tile shows them. Hurricane force wind or likely storms in the next few hours
 * are a [WalkWarning], leaving the verdict on now alone. Without a dog ([hasDog] false) the verdict on now leaves the pavement out.
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
    windForceNow: WindForce? = null,
    gustForceNow: WindForce? = null,
    sunsetEpochSeconds: Long? = null,
    darkNow: Boolean = false,
    hasDog: Boolean = true,
): WalkAdvice? {
    val slots = upcoming
        .take(LOOKAHEAD_SLOTS)
        .filter { slot -> slot.epochSeconds.let { it == null || it < nowEpochSeconds + LOOKAHEAD_SECONDS } }
    if (slots.isEmpty()) return null
    val check = walkCheckFor(slots.first(), thresholds, currentFeelsLikeC, conditionNow, windForceNow, gustForceNow, darkNow, hasDog)
    val firstGoodIndex = slots.indices.drop(1).firstOrNull { slots[it].isWalkable(thresholds) }
    val rating = walkRating(check, hasBetterTime = firstGoodIndex != null)
    val bestIndex = if (rating == WalkRating.GREAT) null else firstGoodIndex
    val bestSlot = bestIndex?.let { slots[it] }

    // The spell open now (a walk now) or the best time's spell ends at the first hour after it that
    // isn't walkable; the next spell is the walkable hour after that, if the day ahead has one.
    fun spellEnd(start: Int) = upcoming.indices.drop(start + 1).firstOrNull { !upcoming[it].isWalkable(thresholds) }
    val spellEnd = (if (rating == WalkRating.GREAT) 0 else bestIndex)?.let(::spellEnd)
    val nextWindow = spellEnd
        ?.let { end -> slots.indices.drop(end + 1).firstOrNull { slots[it].isWalkable(thresholds) } }
        ?.let { start ->
            WalkWindow(
                startLabel = slots[start].timeLabel,
                endLabel = spellEnd(start)?.let { upcoming[it].timeLabel },
                isTomorrow = slots[start].epochSeconds?.let { isNextDay(it, nowEpochSeconds, utcOffsetSeconds) } == true,
            )
        }
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
        walkWarning(slots, check, nowEpochSeconds, thresholds),
        bestTimeEndLabel = if (bestIndex == null) null else spellEnd?.let { upcoming[it].timeLabel },
        nextWindow = nextWindow,
    )
}

/**
 * Hurricane force wind, or else likely thunderstorms, in the hours after the one under way and
 * within [DANGER_AHEAD_SECONDS], when now doesn't already have it. None at all under hurricane force
 * now. An hour without a time (a forecast
 * cached before they were kept) isn't counted as soon.
 */
private fun walkWarning(
    slots: List<HourlySlot>,
    check: WalkCheck,
    nowEpochSeconds: Long,
    t: PetWeatherThresholds,
): WalkWarning? {
    val ahead = slots.drop(1).filter { (it.epochSeconds ?: Long.MAX_VALUE) <= nowEpochSeconds + DANGER_AHEAD_SECONDS }
    // Hurricane force now is already the card's warning: nothing ahead is worth adding.
    if (check.level >= WalkLevel.DANGER) return null
    ahead.firstOrNull { isHurricaneForce(it) }?.let { return WalkWarning.Hurricane(it.timeLabel) }
    if (check.factors.none { it.reason is WalkReason.Storms }) {
        ahead.firstOrNull { hasLikelyStorms(it, t) }?.let { return WalkWarning.Storms(it.timeLabel) }
    }
    return null
}

/**
 * The advice for [weather] (the reading on screen, or the forecast hour standing in for an old one)
 * at [nowEpochSeconds]: the reading for the hour under way (feels-like, condition, wind and gusts,
 * darkness by today's sunset) and [forecast]'s hours from then on. What the weather view model gives
 * the screen and the notification worker uses, so the two can't drift; null when the forecast has no
 * hours left.
 */
fun walkAdviceForReading(
    weather: CurrentWeather,
    forecast: List<DayForecast>,
    thresholds: PetWeatherThresholds,
    nowEpochSeconds: Long,
    hasDog: Boolean = true,
): WalkAdvice? {
    val today = weather.withSunTimesToday(nowEpochSeconds)
    return walkAdviceFor(
        weather.units.toCelsius(weather.feelsLike),
        forecast.flatMap { it.hourlySlots }.fromCurrentSlot(nowEpochSeconds),
        thresholds,
        utcOffsetSeconds = weather.offsetSeconds,
        nowEpochSeconds = nowEpochSeconds,
        conditionNow = weather.conditionMain,
        windForceNow = windForce(weather.windSpeed, weather.units),
        gustForceNow = weather.windGust?.let { windForce(it, weather.units) },
        sunsetEpochSeconds = today.sunsetEpoch,
        darkNow = today.isDarkAt(nowEpochSeconds),
        hasDog = hasDog,
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

/** With a dog, when Remote Config allows the card ([walkCardEnabled] covers both); a danger warning shows for everyone. */
internal fun walkCardShown(
    advice: WalkAdvice,
    walkCardEnabled: Boolean,
): Boolean = walkCardEnabled || advice.isDanger
