package com.fangjet.weather.model

import androidx.annotation.StringRes
import com.fangjet.weather.R
import com.fangjet.weather.domain.WalkAdvice
import com.fangjet.weather.domain.WalkChange
import com.fangjet.weather.domain.WalkLevel
import com.fangjet.weather.domain.WalkOutlook
import com.fangjet.weather.domain.WalkRating
import com.fangjet.weather.domain.WalkReason
import com.fangjet.weather.domain.WalkWarning
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.airNameRes
import kotlin.math.roundToInt

/** A string resource and its format arguments, chosen without Compose so the choice is unit-testable. */
data class WalkText(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
)

/**
 * What the walk card says: the sentence that carries the advice and a short caption with
 * [com.fangjet.weather.domain.WalkCheck]'s reason when it's more than a wait (the pavement, heat or
 * cold, the air by its pollutant, a gale or storms), or the outlook after a walk now. Hurricane force
 * wind, now or in the next few hours, turns it into a warning, which shows even without a dog, and
 * likely storms in the next few hours are added to the caption. Built with the screen state; the
 * card says it. Same wording as iOS's `WalkCardContent`.
 */
data class WalkCardContent(
    /** Picks the paw's tint. */
    val rating: WalkRating,
    /** The warning look: the Danger title, a triangle and the danger tint. */
    val isDanger: Boolean,
    val headline: WalkText,
    val caption: WalkText?,
)

fun walkCardContent(advice: WalkAdvice) =
    WalkCardContent(
        rating = advice.rating,
        isDanger = advice.isDanger,
        headline = walkDetail(advice),
        caption = walkCaption(advice),
    )

/**
 * The card's sentence: hurricane force wind now or from an hour, walk now, around a time (today or
 * tomorrow), or no good time soon.
 */
internal fun walkDetail(advice: WalkAdvice): WalkText =
    when {
        advice.rating == WalkRating.DANGER -> WalkText(R.string.walk_danger_hurricane)
        advice.warning is WalkWarning.Hurricane -> WalkText(R.string.walk_danger_hurricane_from, listOf(advice.warning.timeLabel))
        advice.rating == WalkRating.GREAT -> WalkText(R.string.walk_now)
        advice.bestTimeLabel != null && advice.bestTimeIsTomorrow ->
            WalkText(R.string.walk_around_tomorrow, listOf(advice.bestTimeLabel))
        advice.bestTimeLabel != null -> WalkText(R.string.walk_around, listOf(advice.bestTimeLabel))
        else -> WalkText(R.string.walk_none)
    }

/**
 * "Get everyone inside before then." under hurricane force wind ahead. Otherwise the caption for now
 * ([nowCaption]), with likely storms ahead after it ("Too hot for paws. Storms from 4 PM."), unless
 * it's already the outlook that ends in storms.
 */
internal fun walkCaption(advice: WalkAdvice): WalkText? {
    val warning = advice.warning
    if (warning is WalkWarning.Hurricane && advice.rating != WalkRating.DANGER) return WalkText(R.string.walk_danger_get_inside)
    val caption = nowCaption(advice)
    if (warning !is WalkWarning.Storms) return caption
    val storms = WalkText(R.string.walk_storms_from, listOf(warning.timeLabel))
    val outlook = advice.outlook
    val stormsOutlook = advice.rating == WalkRating.GREAT &&
        outlook is WalkOutlook.Until &&
        (outlook.change == WalkChange.STORMS || outlook.change == WalkChange.MAYBE_STORMS)
    return when {
        caption == null -> storms
        stormsOutlook -> caption
        else -> WalkText(R.string.walk_caption_and_more, listOf(caption, storms))
    }
}

/**
 * The outlook after a walk now; otherwise the reason now isn't the time, when it's worth more than a
 * wait (or it's the pavement, which says how hot it is now).
 */
private fun nowCaption(advice: WalkAdvice): WalkText? {
    if (advice.rating == WalkRating.GREAT) return advice.outlook?.let(::outlookText)
    val reason = advice.now.reason ?: return null
    val severe = advice.now.level >= WalkLevel.POTTY_ONLY
    return when {
        reason is WalkReason.Pavement -> {
            val pavement = advice.pavementNow?.roundToInt() ?: return null
            when (reason.band) {
                PavementBand.DANGEROUS -> WalkText(R.string.walk_pavement_dangerous, listOf(pavement))
                PavementBand.TOO_HOT -> WalkText(R.string.walk_pavement, listOf(pavement))
                PavementBand.WARM -> WalkText(R.string.walk_pavement_warm, listOf(pavement))
                else -> null
            }
        }
        !severe -> null
        reason == WalkReason.Heat -> WalkText(R.string.walk_too_hot)
        reason == WalkReason.Cold -> WalkText(R.string.walk_too_cold)
        reason is WalkReason.Air -> {
            val air = WalkText(airNameRes(reason.pollutant) ?: R.string.walk_air_bad)
            val res = if (advice.now.level >= WalkLevel.STAY_IN) R.string.walk_air_stay_in else R.string.walk_air_potty
            WalkText(res, listOf(air))
        }
        reason is WalkReason.Wind -> when {
            reason.force >= WindForce.HURRICANE -> WalkText(R.string.walk_danger_stay_inside)
            reason.force >= WindForce.STORM -> WalkText(R.string.walk_storm_wind)
            else -> WalkText(R.string.walk_gale)
        }
        reason is WalkReason.Storms && advice.now.level >= WalkLevel.STAY_IN -> WalkText(R.string.walk_storms)
        else -> null
    }
}

/** After a walk now: when the good spell ends and why, sunset coming up, or so-so air. */
internal fun outlookText(outlook: WalkOutlook): WalkText =
    when (outlook) {
        is WalkOutlook.Until -> WalkText(untilRes(outlook.change), listOf(outlook.timeLabel))
        is WalkOutlook.DarkIn ->
            when (outlook.hours) {
                0 -> WalkText(R.string.walk_dark_soon)
                1 -> WalkText(R.string.walk_dark_in_an_hour)
                else -> WalkText(R.string.walk_dark_in_hours, listOf(outlook.hours))
            }
        WalkOutlook.ModerateAir -> WalkText(R.string.walk_moderate_air)
    }

@StringRes
private fun untilRes(change: WalkChange): Int =
    when (change) {
        WalkChange.STORMS -> R.string.walk_until_storms
        WalkChange.MAYBE_STORMS -> R.string.walk_until_maybe_storms
        WalkChange.RAIN -> R.string.walk_until_rain
        WalkChange.MAYBE_RAIN -> R.string.walk_until_maybe_rain
        WalkChange.SNOW -> R.string.walk_until_snow
        WalkChange.MAYBE_SNOW -> R.string.walk_until_maybe_snow
        WalkChange.WIND -> R.string.walk_until_wind
        WalkChange.HEAT -> R.string.walk_until_heat
        WalkChange.HOT_PAVEMENT -> R.string.walk_until_hot_pavement
        WalkChange.COLD -> R.string.walk_until_cold
        WalkChange.BAD_AIR -> R.string.walk_until_bad_air
    }
