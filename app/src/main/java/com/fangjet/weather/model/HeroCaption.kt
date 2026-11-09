package com.fangjet.weather.model

import androidx.annotation.ArrayRes
import com.fangjet.weather.R
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.WalkCheck
import com.fangjet.weather.domain.WalkLevel
import com.fangjet.weather.domain.WalkReason
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.airNameRes
import com.fangjet.weather.util.skyCondition
import java.time.Instant
import java.time.ZoneOffset

/**
 * Which caption the hero shows, worked out with the screen state: the walk's reason when it's one
 * the scene can't show (bad air, hot pavement, a gale or worse), then a seasonal theme's caption on
 * its day, otherwise one of the mood's captions. The words come from string resources, so this says
 * which, and the screen says it. Same choice as iOS's `PetMood.heroCaption`.
 */
sealed interface HeroCaption {
    data class Walk(
        val text: WalkText,
    ) : HeroCaption

    data class Theme(
        val text: String,
    ) : HeroCaption

    /**
     * One of [captions], by the city's [day] of the year so it varies without flickering. When
     * [leaveOutAfternoonOnly] (before noon in the city), the afternoon-only captions are left out.
     * When [leaveOutWalkSuggestions] (now isn't the time for a walk), the captions that suggest one
     * are left out, and if that leaves none, [noDogCaptions] stand in.
     */
    data class Weather(
        @param:ArrayRes val captions: Int,
        @param:ArrayRes val noDogCaptions: Int,
        val leaveOutWalkSuggestions: Boolean,
        val leaveOutAfternoonOnly: Boolean,
        val day: Int,
    ) : HeroCaption
}

/**
 * The hero's caption for [mood] at [nowEpochSeconds], by the city's day and hour. Without a dog
 * ([hasDog] false), the captions leave out walks. A cloudy mood under a [partlyCloudy] sky gets
 * brighter captions than a grey one.
 */
fun heroCaption(
    walk: WalkCheck?,
    themeCaption: String?,
    mood: PetMood,
    nowEpochSeconds: Long,
    utcOffsetSeconds: Int,
    hasDog: Boolean,
    partlyCloudy: Boolean,
): HeroCaption {
    val local = Instant.ofEpochSecond(nowEpochSeconds).atOffset(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
    val day = local.dayOfYear
    walk?.let { heroWalkCaptions(it, hasDog) }?.let { return HeroCaption.Walk(it[day % it.size]) }
    themeCaption?.let { return HeroCaption.Theme(it) }
    val nightPart = nightPartAt(local.hour)
    return HeroCaption.Weather(
        captions = mood.captionsRes(nightPart, hasDog = hasDog, partlyCloudy = partlyCloudy),
        noDogCaptions = mood.captionsRes(nightPart, hasDog = false, partlyCloudy = partlyCloudy),
        leaveOutWalkSuggestions = walk != null && rulesOutWalkNow(walk),
        leaveOutAfternoonOnly = local.hour < 12,
        day = day,
    )
}

/**
 * Whether the cloudy mood's captions are the partly cloudy ones: the sky as Misty's View reads it
 * (no [cloudPercent] when the forecast's hour stands in) is clear or partly cloudy. Same rule as
 * iOS's `PetMood.isPartlyCloudy`.
 */
fun partlyCloudyCaptions(
    conditionMain: String,
    cloudPercent: Int?,
    iconCode: String,
): Boolean = skyCondition(conditionMain, cloudPercent, iconCode).let { it == SkyCondition.CLEAR || it == SkyCondition.PARTLY_CLOUDY }

/**
 * The part of the night it is, by the city's local hour. The night scene runs from sunset to
 * sunrise, so any night hour before noon is before dawn and any after noon is evening.
 */
enum class NightPart { EVENING, LATE_NIGHT, BEFORE_DAWN }

fun nightPartAt(localHour: Int): NightPart =
    when (localHour) {
        23, in 0 until 5 -> NightPart.LATE_NIGHT
        in 5 until 12 -> NightPart.BEFORE_DAWN
        else -> NightPart.EVENING
    }

@ArrayRes
internal fun PetMood.captionsRes(
    nightPart: NightPart,
    hasDog: Boolean,
    partlyCloudy: Boolean,
): Int =
    when (this) {
        PetMood.FAIR -> if (hasDog) R.array.pet_caption_fair else R.array.pet_caption_fair_no_dog
        PetMood.SUNNY -> if (hasDog) R.array.pet_caption_sunny else R.array.pet_caption_sunny_no_dog
        PetMood.CLOUDY -> when {
            partlyCloudy -> if (hasDog) R.array.pet_caption_partly_cloudy else R.array.pet_caption_partly_cloudy_no_dog
            else -> if (hasDog) R.array.pet_caption_cloudy else R.array.pet_caption_cloudy_no_dog
        }
        PetMood.RAIN -> if (hasDog) R.array.pet_caption_rain else R.array.pet_caption_rain_no_dog
        PetMood.STORM -> R.array.pet_caption_storm
        PetMood.SNOW -> if (hasDog) R.array.pet_caption_snow else R.array.pet_caption_snow_no_dog
        PetMood.HOT -> if (hasDog) R.array.pet_caption_hot else R.array.pet_caption_hot_no_dog
        PetMood.COLD -> if (hasDog) R.array.pet_caption_cold else R.array.pet_caption_cold_no_dog
        PetMood.NIGHT -> when (nightPart) {
            NightPart.EVENING -> if (hasDog) R.array.pet_caption_night_evening else R.array.pet_caption_night_evening_no_dog
            NightPart.LATE_NIGHT -> R.array.pet_caption_night_late
            NightPart.BEFORE_DAWN ->
                if (hasDog) R.array.pet_caption_night_before_dawn else R.array.pet_caption_night_before_dawn_no_dog
        }
    }

/**
 * [options] without the [afternoonOnly] captions when [leaveOutAfternoonOnly] (before noon), and
 * without the captions that suggest a walk when [leaveOutWalkSuggestions] (now isn't the time), or
 * [noDog] (without the afternoon-only ones too) if that leaves none.
 */
fun captionOptions(
    options: List<String>,
    noDog: List<String>,
    suggestions: Set<String>,
    leaveOutWalkSuggestions: Boolean,
    afternoonOnly: Set<String> = emptySet(),
    leaveOutAfternoonOnly: Boolean = false,
): List<String> {
    val timely = { list: List<String> -> if (leaveOutAfternoonOnly) list.filterNot { it in afternoonOnly } else list }
    val now = timely(options)
    if (!leaveOutWalkSuggestions) return now
    return now.filterNot { it in suggestions }.ifEmpty { timely(noDog) }
}

/** The captions that suggest a walk now, left out when now isn't the time. */
val WALK_SUGGESTIONS = listOf(
    R.string.caption_fair_walkies,
    R.string.caption_cloudy_keen,
    R.string.caption_cloudy_stroll,
    R.string.caption_evening_last_stroll,
    R.string.caption_evening_lap,
    R.string.caption_partly_cloudy_sniffing,
    R.string.caption_partly_cloudy_ready,
)

/** The captions that only make sense from noon in the city ("Nap weather"), left out before. */
val AFTERNOON_ONLY = listOf(
    R.string.caption_cloudy_nap,
)

/** Anything but the dark at a wait or worse. */
internal fun rulesOutWalkNow(walk: WalkCheck): Boolean = walk.factors.any { it.reason != WalkReason.Dark && it.level >= WalkLevel.WAIT }

/**
 * When [walk] rules out more than a wait for a reason the scene can't show (the air, the pavement,
 * a gale or worse), the hero's caption gives the reason instead of the weather: "Smoky air today.
 * Quick potty breaks, then back on the sofa." Null otherwise. Without a dog the check has no
 * pavement in it.
 * It takes the place of a seasonal theme's caption too. Same captions as iOS's
 * `PetMood.walkCaptions`.
 */
internal fun heroWalkCaptions(
    walk: WalkCheck,
    hasDog: Boolean,
): List<WalkText>? {
    val worst = walk.factors.firstOrNull()?.takeIf { it.level >= WalkLevel.POTTY_ONLY } ?: return null
    return when (val reason = worst.reason) {
        is WalkReason.Wind -> when {
            reason.force >= WindForce.HURRICANE -> listOf(WalkText(R.string.hero_hurricane))
            reason.force >= WindForce.STORM -> listOf(WalkText(R.string.hero_storm_wind_1), WalkText(R.string.hero_storm_wind_2))
            hasDog -> listOf(WalkText(R.string.hero_gale_1), WalkText(R.string.hero_gale_2))
            else -> listOf(WalkText(R.string.hero_gale_no_dog_1), WalkText(R.string.hero_gale_no_dog_2))
        }
        is WalkReason.Air -> {
            val air = WalkText(airNameRes(reason.pollutant) ?: R.string.walk_air_poor)
            if (hasDog) {
                listOf(WalkText(R.string.hero_air_1, listOf(air)), WalkText(R.string.hero_air_2))
            } else {
                listOf(WalkText(R.string.hero_air_no_dog_1, listOf(air)), WalkText(R.string.hero_air_no_dog_2))
            }
        }
        is WalkReason.Pavement -> listOf(WalkText(R.string.hero_pavement_1), WalkText(R.string.hero_pavement_2))
        else -> null
    }
}
