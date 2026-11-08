package com.fangjet.weather.ui.pets

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.WalkAdvice
import com.fangjet.weather.domain.WalkChange
import com.fangjet.weather.domain.WalkLevel
import com.fangjet.weather.domain.WalkOutlook
import com.fangjet.weather.domain.WalkRating
import com.fangjet.weather.domain.WalkReason
import com.fangjet.weather.ui.seasonalCardColor
import com.fangjet.weather.ui.theme.WalkColdBg
import com.fangjet.weather.ui.theme.WalkColdText
import com.fangjet.weather.ui.theme.WalkGreatBg
import com.fangjet.weather.ui.theme.WalkGreatText
import com.fangjet.weather.ui.theme.WalkHotBg
import com.fangjet.weather.ui.theme.WalkHotText
import com.fangjet.weather.ui.theme.WalkOkayBg
import com.fangjet.weather.ui.theme.WalkOkayText
import com.fangjet.weather.ui.theme.WalkPoorBg
import com.fangjet.weather.ui.theme.WalkPoorText
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.WindForce
import kotlin.math.roundToInt

/**
 * "Best walk time" card: when to take the dog out. The sentence carries the advice; the paw icon is
 * tinted by the rating as a glance cue (deliberately not a chip, which read as a button), and a
 * short caption gives [WalkCheck]'s reason when it's more than a wait: the pavement, heat or cold,
 * the air (by its pollutant), a gale or storms. Hurricane force wind turns it into a warning, which
 * shows even without a dog.
 */
@Composable
fun WalkCard(
    advice: WalkAdvice,
    modifier: Modifier = Modifier,
) {
    val detail = walkDetail(advice).resolve()
    val caption = walkCaption(advice)?.resolve()
    val tint = ratingTint(advice.rating)
    val danger = advice.rating == WalkRating.DANGER
    // Baloo 2 leaves more room under its baseline than the small title leaves above its capitals,
    // so when the headline is the last line the text sits high beside the paw. Lowered to match.
    val headlineLastOffset = with(LocalDensity.current) { 3.sp.toDp() }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = seasonalCardColor()),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (danger) Icons.Default.Warning else Icons.Default.Pets,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f).offset(y = if (caption == null) headlineLastOffset else 0.dp)) {
                Text(
                    text = stringResource(if (danger) R.string.walk_danger_title else R.string.walk_title),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = detail,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (caption != null) {
                    Text(
                        text = caption,
                        style = MaterialTheme.typography.bodySmall,
                        color = tint,
                    )
                }
            }
        }
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

/** A string resource and its format arguments, chosen without Compose so the choice is unit-testable. */
internal data class WalkText(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
)

/** An argument that is itself a [WalkText] (an air's name) is resolved first. */
@Composable
internal fun WalkText.resolve(): String = stringResource(resId, *args.map { if (it is WalkText) it.resolve() else it }.toTypedArray())

/** The card's sentence: walk now, around a time (today or tomorrow), or no good time soon. */
internal fun walkDetail(advice: WalkAdvice): WalkText =
    when {
        advice.rating == WalkRating.DANGER -> WalkText(R.string.walk_danger_hurricane)
        advice.rating == WalkRating.GREAT -> WalkText(R.string.walk_now)
        advice.bestTimeLabel != null && advice.bestTimeIsTomorrow ->
            WalkText(R.string.walk_around_tomorrow, listOf(advice.bestTimeLabel))
        advice.bestTimeLabel != null -> WalkText(R.string.walk_around, listOf(advice.bestTimeLabel))
        else -> WalkText(R.string.walk_none)
    }

/**
 * The outlook after a walk now; otherwise the reason now isn't the time, when it's worth more than a
 * wait (or it's the pavement, which says how hot it is now).
 */
internal fun walkCaption(advice: WalkAdvice): WalkText? {
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

/**
 * The rating's colour: the dark shade on the light theme's card and the pale one on the dark
 * theme's, so the paw and the caption stay clear (at least 4.5:1) on either.
 */
@Composable
private fun ratingTint(rating: WalkRating): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (rating) {
        WalkRating.GREAT -> if (dark) WalkGreatBg else WalkGreatText
        WalkRating.OKAY -> if (dark) WalkOkayBg else WalkOkayText
        WalkRating.POOR -> if (dark) WalkPoorBg else WalkPoorText
        WalkRating.PAWS_HOT, WalkRating.BAD_AIR, WalkRating.WINDY, WalkRating.STORMY, WalkRating.DANGER ->
            if (dark) WalkHotBg else WalkHotText
        WalkRating.TOO_COLD -> if (dark) WalkColdBg else WalkColdText
    }
}
