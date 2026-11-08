package com.fangjet.weather.ui.pets

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
import com.fangjet.weather.domain.WalkRating
import com.fangjet.weather.model.WalkCardContent
import com.fangjet.weather.model.WalkText
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

/**
 * "Best walk time" card: when to take the dog out, as [WalkCardContent] words it. The paw icon is
 * tinted by the rating as a glance cue (deliberately not a chip, which read as a button).
 */
@Composable
fun WalkCard(
    content: WalkCardContent,
    modifier: Modifier = Modifier,
) {
    val detail = content.headline.resolve()
    val caption = content.caption?.resolve()
    val tint = ratingTint(content.rating)
    val danger = content.isDanger
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

/** An argument that is itself a [WalkText] (an air's name) is resolved first. */
@Composable
internal fun WalkText.resolve(): String = stringResource(resId, *args.map { if (it is WalkText) it.resolve() else it }.toTypedArray())

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
