package com.fangjet.weather.ui.pets

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pets
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fangjet.weather.R
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
import kotlin.math.roundToInt

/**
 * "Best walk time" card: when to take the dog out. The sentence carries the advice; the paw icon is
 * tinted by the rating as a glance cue (deliberately not a chip, which read as a button), and a
 * short caption says why when it's too hot or too cold, naming the pavement only when that's the reason.
 */
@Composable
fun WalkCard(
    advice: WalkAdvice,
    modifier: Modifier = Modifier,
) {
    val detail = walkDetail(advice).resolve()
    val caption = walkCaption(advice)?.resolve()
    val tint = ratingTint(advice.rating)
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
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.walk_title),
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

/** A string resource and its format arguments, chosen without Compose so the choice is unit-testable. */
internal data class WalkText(
    @param:StringRes val resId: Int,
    val args: List<Any> = emptyList(),
)

@Composable
private fun WalkText.resolve(): String = stringResource(resId, *args.toTypedArray())

/** The card's sentence: walk now, around a time (today or tomorrow), or no good time soon. */
internal fun walkDetail(advice: WalkAdvice): WalkText =
    when {
        advice.rating == WalkRating.GREAT -> WalkText(R.string.walk_now)
        advice.bestTimeLabel != null && advice.bestTimeIsTomorrow ->
            WalkText(R.string.walk_around_tomorrow, listOf(advice.bestTimeLabel))
        advice.bestTimeLabel != null -> WalkText(R.string.walk_around, listOf(advice.bestTimeLabel))
        else -> WalkText(R.string.walk_none)
    }

/**
 * Why it's not a good time, when that's heat or cold: the pavement temperature when the pavement is
 * the reason, otherwise the air. Null for the other ratings.
 */
internal fun walkCaption(advice: WalkAdvice): WalkText? {
    val pavement = advice.pavementNow?.roundToInt()
    return when {
        pavement == null -> null
        advice.rating == WalkRating.PAWS_HOT && advice.pavementBand == PavementBand.DANGEROUS ->
            WalkText(R.string.walk_pavement_dangerous, listOf(pavement))
        advice.rating == WalkRating.PAWS_HOT && advice.pavementBand == PavementBand.TOO_HOT ->
            WalkText(R.string.walk_pavement, listOf(pavement))
        advice.rating == WalkRating.OKAY && advice.pavementBand == PavementBand.WARM ->
            WalkText(R.string.walk_pavement_warm, listOf(pavement))
        else -> null
    } ?: when (advice.rating) {
        // Hot or cold air, not the pavement.
        WalkRating.PAWS_HOT -> WalkText(R.string.walk_too_hot)
        WalkRating.TOO_COLD -> WalkText(R.string.walk_too_cold)
        WalkRating.BAD_AIR -> WalkText(R.string.walk_bad_air)
        else -> null
    }
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
        WalkRating.PAWS_HOT, WalkRating.BAD_AIR -> if (dark) WalkHotBg else WalkHotText
        WalkRating.TOO_COLD -> if (dark) WalkColdBg else WalkColdText
    }
}
