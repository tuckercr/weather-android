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
 * What the walk card says: the title, the sentence that carries the advice (a walk now, or the best
 * time's spell of good hours, "11 AM to 1 PM") and a short caption: the next spell as a second
 * option ("Or 5 to 8 PM."), then [com.fangjet.weather.domain.WalkCheck]'s reason when it's more than
 * a wait (the pavement, heat or cold, the air by its pollutant, a gale or storms) or else what clears
 * by the best time ("The rain's gone by then."), or the outlook after a walk now. Hurricane force
 * wind, now or in the next few hours, turns it into a warning, which shows even without a dog, and
 * likely storms in the next few hours are added to the caption. Built with the screen state; the
 * card says it. Same wording as iOS's `WalkCardContent`.
 */
data class WalkCardContent(
    /** Picks the paw's tint. */
    val rating: WalkRating,
    /** The warning look: the Danger title, a triangle and the danger tint. */
    val isDanger: Boolean,
    @param:StringRes val title: Int,
    val headline: WalkText,
    val caption: WalkText?,
)

fun walkCardContent(advice: WalkAdvice) =
    WalkCardContent(
        rating = advice.rating,
        isDanger = advice.isDanger,
        title = when {
            advice.isDanger -> R.string.walk_danger_title
            secondWindow(advice) != null -> R.string.walk_title_two
            else -> R.string.walk_title
        },
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
        advice.bestTimeLabel != null && advice.bestTimeEndLabel != null -> {
            val range = hourRange(advice.bestTimeLabel, advice.bestTimeEndLabel)
            if (advice.bestTimeIsTomorrow) WalkText(R.string.walk_window_tomorrow, listOf(range)) else range
        }
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
    // After a walk now the outlook comes first ("Good until 4 PM, then rain. Good again 6 to 8 PM.");
    // after a later best time, the second option ("Or 5 to 8 PM. The rain's gone by then.").
    val parts = if (advice.rating == WalkRating.GREAT) {
        listOfNotNull(nowCaption(advice), secondWindow(advice))
    } else {
        listOfNotNull(secondWindow(advice), nowCaption(advice) ?: clearsText(advice))
    }
    val caption = parts.reduceOrNull { first, then -> WalkText(R.string.walk_caption_and_more, listOf(first, then)) }
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

/** "11 AM to 1 PM", or "5 to 8 PM" when both are in the same half of the day. */
internal fun hourRange(
    start: String,
    end: String,
): WalkText {
    val startParts = start.split(" ", limit = 2)
    val endParts = end.split(" ", limit = 2)
    val from = if (startParts.size == 2 && endParts.size == 2 && startParts[1] == endParts[1]) startParts[0] else start
    return WalkText(R.string.walk_range, listOf(from, end))
}

/**
 * The next spell of good walking hours, as a second option: "Or 5 to 8 PM." after a later best time,
 * "Good again 5 to 8 PM." after a walk now whose spell ends within a few hours. Null otherwise, and
 * under a warning.
 */
internal fun secondWindow(advice: WalkAdvice): WalkText? {
    val window = advice.nextWindow
    if (advice.isDanger || window == null) return null
    val time = window.endLabel?.let { hourRange(window.startLabel, it) } ?: WalkText(R.string.walk_window_around, listOf(window.startLabel))
    if (advice.rating == WalkRating.GREAT) {
        if (advice.outlook !is WalkOutlook.Until) return null
        return WalkText(if (window.isTomorrow) R.string.walk_again_tomorrow else R.string.walk_again, listOf(time))
    }
    if (advice.bestTimeLabel == null) return null
    val tomorrow = window.isTomorrow && !advice.bestTimeIsTomorrow
    return WalkText(if (tomorrow) R.string.walk_or_tomorrow else R.string.walk_or, listOf(time))
}

/**
 * Why the best time is better than now, when now is only a wait for a reason that clears: "The
 * rain's gone by then." Null after dark or outside the walking hours, and for a walk now.
 */
private fun clearsText(advice: WalkAdvice): WalkText? {
    if (advice.rating == WalkRating.GREAT || advice.bestTimeLabel == null) return null
    val res = when (advice.now.reason) {
        is WalkReason.Rain -> R.string.walk_clears_rain
        is WalkReason.Snow -> R.string.walk_clears_snow
        is WalkReason.Storms -> R.string.walk_clears_storms
        WalkReason.Heat -> R.string.walk_clears_heat
        WalkReason.Cold -> R.string.walk_clears_cold
        is WalkReason.Wind -> R.string.walk_clears_wind
        is WalkReason.Pavement -> R.string.walk_clears_pavement
        is WalkReason.Air -> R.string.walk_clears_air
        else -> return null
    }
    return WalkText(res)
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
