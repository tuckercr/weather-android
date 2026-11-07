package com.fangjet.weather.ui.pets

import androidx.annotation.StringRes
import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.pavementBand
import com.fangjet.weather.util.windForce

/**
 * How good a time it is to walk the dog, best first. Every screen that speaks about walks (the walk
 * card, the hero's caption, the Air Quality tile, the notifications) reads it from [WalkCheck]
 * rather than judging the weather itself, so they can't disagree. Same as iOS's `WalkLevel`.
 */
enum class WalkLevel {
    /** Nothing in the way. */
    GREAT,

    /** A walk, with a caveat: air that's fine for most dogs but not older or flat-faced ones. */
    FINE,

    /** Not now, but nothing dangerous: rain, a near gale, warm pavement, chilly or warm air, dark. */
    WAIT,

    /** Out for the essentials and straight back: hot pavement, too hot or cold, bad air, a gale. */
    POTTY_ONLY,

    /** Keep pets inside: dangerously hot pavement, the worst air, thunderstorms, storm force wind. */
    STAY_IN,

    /** A danger to everyone, dog or not: hurricane force wind. */
    DANGER,
}

/**
 * Why a walk isn't great, with what a card needs to say so ("Smoky air", "Gale force winds").
 * [order] is the order reasons at the same level are given in: rain before warm pavement, so a wet
 * hour isn't put down to hot paws, and heat before air before cold, as the walk card has always put
 * them.
 */
sealed interface WalkReason {
    val order: Int

    /** Thunderstorms now, or a chance of them at or above the walk limit ([likely] from [LIKELY_RAIN_CHANCE]). */
    data class Storms(
        val likely: Boolean,
    ) : WalkReason {
        override val order get() = 0
    }

    data class Rain(
        val likely: Boolean,
    ) : WalkReason {
        override val order get() = 1
    }

    data class Snow(
        val likely: Boolean,
    ) : WalkReason {
        override val order get() = 2
    }

    data class Wind(
        val force: WindForce,
    ) : WalkReason {
        override val order get() = 3
    }

    data class Pavement(
        val band: PavementBand,
    ) : WalkReason {
        override val order get() = 4
    }

    /** Feels-like above the comfort range. */
    data object Heat : WalkReason {
        override val order get() = 5
    }

    /** The air quality band (0 good to 5 worst) and the pollutant behind it, when known. */
    data class Air(
        val band: Int,
        val pollutant: AirQuality.Pollutant?,
    ) : WalkReason {
        override val order get() = 6
    }

    /** Feels-like below the comfort range. */
    data object Cold : WalkReason {
        override val order get() = 7
    }

    /** Before sunrise, after sunset, or outside the walking hours. */
    data object Dark : WalkReason {
        override val order get() = 8
    }
}

/**
 * What the pollutant behind bad air is called, for captions and notifications: "Smoky air" for fine
 * particles, "Dusty air" for coarse ones, "High ozone". Null for the rest, which each screen words
 * in its own way.
 */
@StringRes
fun airNameRes(pollutant: AirQuality.Pollutant?): Int? =
    when (pollutant) {
        AirQuality.Pollutant.FINE_PARTICLES -> R.string.walk_air_smoky
        AirQuality.Pollutant.COARSE_PARTICLES -> R.string.walk_air_dusty
        AirQuality.Pollutant.OZONE -> R.string.walk_air_ozone
        else -> null
    }

data class WalkFactor(
    val reason: WalkReason,
    val level: WalkLevel,
)

/** The verdict on one hour: each thing that keeps a walk from great, worst first. */
class WalkCheck(
    factors: List<WalkFactor>,
) {
    val factors: List<WalkFactor> = factors.sortedWith(compareByDescending<WalkFactor> { it.level }.thenBy { it.reason.order })

    /** Great when nothing's in the way, otherwise the worst factor's level. */
    val level: WalkLevel get() = factors.firstOrNull()?.level ?: WalkLevel.GREAT

    /** The worst factor's reason; null when it's great. */
    val reason: WalkReason? get() = factors.firstOrNull()?.reason

    /** Fine or better: a walk now. */
    val isWalkable: Boolean get() = level <= WalkLevel.FINE

    /** The air factor, when the air keeps the walk from great. */
    val air: WalkFactor? get() = factors.firstOrNull { it.reason is WalkReason.Air }

    /** The pavement factor, when the pavement keeps the walk from great. */
    val pavement: WalkFactor? get() = factors.firstOrNull { it.reason is WalkReason.Pavement }

    override fun equals(other: Any?): Boolean = other is WalkCheck && other.factors == factors

    override fun hashCode(): Int = factors.hashCode()

    override fun toString(): String = "WalkCheck($factors)"

    companion object {
        val CLEAR = WalkCheck(emptyList())
    }
}

/**
 * The walk rules: one small rule per factor, and the check that puts them together. The hourly
 * look-ahead ([walkAdviceFor]), the notifications and the tiles all use these.
 *
 * The verdict on [slot]. For the hour under way, [feelsLikeC] is the current reading's feels-like
 * (what the dog feels now, rather than the hour's forecast), [conditionNow] the current condition
 * group ("Rain", "Thunderstorm"), since the hour's forecast can say dry while it's raining, and
 * [darkNow] the clock's word on darkness, since the hour the sun sets in keeps its daytime icon.
 */
fun walkCheckFor(
    slot: HourlySlot,
    thresholds: PetWeatherThresholds,
    feelsLikeC: Double? = null,
    conditionNow: String? = null,
    darkNow: Boolean = false,
): WalkCheck {
    val feels = feelsLikeC ?: slot.units.toCelsius(slot.feelsLike)
    val factors = mutableListOf<WalkFactor>()

    fun add(
        reason: WalkReason,
        level: WalkLevel,
    ) {
        if (level > WalkLevel.GREAT) factors += WalkFactor(reason, level)
    }
    if (darkNow || !slot.isWalkingTime(thresholds)) add(WalkReason.Dark, WalkLevel.WAIT)
    precipitation(slot, conditionNow, thresholds)?.let { (reason, level) -> add(reason, level) }
    val force = windForce(slot.windSpeed, slot.units)
    add(WalkReason.Wind(force), windWalkLevel(force, thresholds))
    slot.airQuality?.let { add(WalkReason.Air(it.band, it.mainPollutant), airWalkLevel(it.band, thresholds)) }
    slot.pavementTemperature?.let { pavement ->
        val band = pavementBand(slot.units.toCelsius(pavement), slot.groundWet, feels, thresholds)
        add(WalkReason.Pavement(band), pavementWalkLevel(band))
    }
    when {
        feels >= thresholds.petHotC -> add(WalkReason.Heat, WalkLevel.POTTY_ONLY)
        feels > thresholds.comfortMaxC -> add(WalkReason.Heat, WalkLevel.WAIT)
        feels <= thresholds.petColdC -> add(WalkReason.Cold, WalkLevel.POTTY_ONLY)
        feels < thresholds.comfortMinC -> add(WalkReason.Cold, WalkLevel.WAIT)
    }
    return WalkCheck(factors)
}

/**
 * The Remote Config band (by default 3, US "Unhealthy" and Europe's "Poor") and up is potty breaks
 * only, and the worst band (5) keeps pets inside. From 2 up to the band below it is a walk with a
 * caveat for older and flat-faced dogs. 0 and 1 are nothing to mention.
 */
fun airWalkLevel(
    band: Int,
    t: PetWeatherThresholds,
): WalkLevel =
    when {
        band >= 5 -> WalkLevel.STAY_IN
        band >= t.badAirBand -> WalkLevel.POTTY_ONLY
        band >= 2 -> WalkLevel.FINE
        else -> WalkLevel.GREAT
    }

/** Too windy for a walk from `windyForce` (a near gale), then the Beaufort scale's own steps. */
fun windWalkLevel(
    force: WindForce,
    t: PetWeatherThresholds,
): WalkLevel =
    when {
        force >= WindForce.HURRICANE -> WalkLevel.DANGER
        force >= WindForce.STORM -> WalkLevel.STAY_IN
        force >= WindForce.GALE -> WalkLevel.POTTY_ONLY
        force.ordinal >= t.windyForce -> WalkLevel.WAIT
        else -> WalkLevel.GREAT
    }

fun pavementWalkLevel(band: PavementBand): WalkLevel =
    when (band) {
        PavementBand.DANGEROUS -> WalkLevel.STAY_IN
        PavementBand.TOO_HOT -> WalkLevel.POTTY_ONLY
        PavementBand.WARM -> WalkLevel.WAIT
        PavementBand.SAFE, PavementBand.PAVEMENT_OK, PavementBand.FREEZING, PavementBand.ICY -> WalkLevel.GREAT
    }

/**
 * Rain, snow or storms now ([conditionNow]), or a chance of them at or above the walk limit. Storms
 * that are happening or likely keep pets inside; the rest is a wait for a dry spell.
 */
private fun precipitation(
    slot: HourlySlot,
    conditionNow: String?,
    t: PetWeatherThresholds,
): Pair<WalkReason, WalkLevel>? {
    when (conditionNow?.lowercase()) {
        "thunderstorm" -> return WalkReason.Storms(likely = true) to WalkLevel.STAY_IN
        "snow" -> return WalkReason.Snow(likely = true) to WalkLevel.WAIT
        "rain", "drizzle" -> return WalkReason.Rain(likely = true) to WalkLevel.WAIT
    }
    if (slot.precipitationChance < t.maxRainChancePercent) return null
    val likely = slot.precipitationChance >= LIKELY_RAIN_CHANCE
    return when (slot.iconCode.take(2)) {
        "11" -> WalkReason.Storms(likely) to if (likely) WalkLevel.STAY_IN else WalkLevel.WAIT
        "13" -> WalkReason.Snow(likely) to WalkLevel.WAIT
        else -> WalkReason.Rain(likely) to WalkLevel.WAIT
    }
}

/**
 * Within the walking hours and in daylight. The walking hours alone are a fixed clock window, so on
 * a winter morning 7 AM can be before sunrise; a night icon ("n", the same signal that puts the pets
 * to sleep) rules it out.
 */
internal fun HourlySlot.isWalkingTime(t: PetWeatherThresholds): Boolean =
    (localHour?.let { it in t.walkHoursStart..t.walkHoursEnd } ?: true) && !iconCode.endsWith("n")
