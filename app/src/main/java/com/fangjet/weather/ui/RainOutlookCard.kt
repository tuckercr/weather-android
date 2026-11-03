package com.fangjet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.fangjet.weather.R
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.ui.theme.CatsDogsTheme
import com.fangjet.weather.util.RainBar
import com.fangjet.weather.util.RainOutlook
import com.fangjet.weather.util.RainOutlookHeadline
import com.fangjet.weather.util.RainTotal
import com.fangjet.weather.util.amountShown
import com.fangjet.weather.util.rainOutlook
import kotlin.math.roundToInt

private val CHART_HEIGHT = 110.dp
private val BAR_GAP = 4.dp
private val MIN_BAR_HEIGHT = 2.dp
private val BAR_SHAPE = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)

/** The other bars fade to this while one is tapped. */
private const val DIMMED_BAR_ALPHA = 0.4f

/**
 * "Rain likely around 4 PM": the chance of rain for each of the next few hours as bars, with a
 * dashed line at 50%. Tapping a bar shows its hour, chance and amount above it; tapping it again,
 * or anywhere else on the card, clears it. Rules in `util/RainOutlook.kt`.
 */
@Composable
internal fun RainOutlookCard(
    outlook: RainOutlook,
    units: WeatherUnits,
    modifier: Modifier = Modifier,
) {
    // A new forecast (or the clock moving on) starts with nothing selected.
    var selected by remember(outlook.bars) { mutableStateOf<Int?>(null) }
    Card(
        modifier = modifier
            .fillMaxWidth()
            // Taps on the bars are theirs; anywhere else on the card clears the selection.
            .pointerInput(outlook.bars) { detectTapGestures { selected = null } },
        colors = CardDefaults.cardColors(containerColor = seasonalCardColor()),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Text(
                text = headlineText(outlook.headline),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detailText(outlook),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(8.dp))
            RainChart(
                bars = outlook.bars,
                units = units,
                selected = selected,
                onBarTapped = { index -> selected = if (selected == index) null else index },
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.rain_outlook_caption),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun headlineText(headline: RainOutlookHeadline): String =
    when (headline) {
        is RainOutlookHeadline.NextRain -> headline.text
        is RainOutlookHeadline.Likely ->
            headline.hour?.let { stringResource(R.string.rain_outlook_likely_around, it) }
                ?: stringResource(R.string.rain_outlook_likely_now)
        is RainOutlookHeadline.Chance ->
            headline.hour?.let { stringResource(R.string.rain_outlook_chance_around, it) }
                ?: stringResource(R.string.rain_outlook_chance_now)
    }

/** "Drying out after 6 PM. About 2.5 mm in all." */
@Composable
private fun detailText(outlook: RainOutlook): String {
    val drying = stringResource(
        if (outlook.staysUp) R.string.rain_outlook_stays_up else R.string.rain_outlook_drying_out,
        outlook.lastWetHour,
    )
    val total = when (val total = outlook.total) {
        null -> null
        is RainTotal.Millimetres -> stringResource(R.string.rain_outlook_total_mm, total.amount)
        is RainTotal.Inches -> stringResource(R.string.rain_outlook_total_inches, total.amount)
        RainTotal.OneInch -> stringResource(R.string.rain_outlook_total_one_inch)
    }
    return listOfNotNull(drying, total).joinToString(" ")
}

/** A bar's amount with its unit, "2.5 mm" or "0.1 in", or null when it's too little to show. */
@Composable
private fun barAmountText(
    bar: RainBar,
    units: WeatherUnits,
): String? =
    bar.amountShown(units)?.let {
        stringResource(if (units == WeatherUnits.METRIC) R.string.format_rain_mm else R.string.format_rain_in, it)
    }

/**
 * The bars over a baseline, with a dashed line at 50% labelled in a gutter on the right (so the
 * label never sits on a bar). Above them, the tapped bar's label; below, every other hour.
 */
@Composable
private fun RainChart(
    bars: List<RainBar>,
    units: WeatherUnits,
    selected: Int?,
    onBarTapped: (Int) -> Unit,
) {
    val labelStyle = MaterialTheme.typography.bodySmall
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val lineColor = secondary.copy(alpha = 0.6f)
    val barColor = MaterialTheme.colorScheme.primary
    val halfwayLabel = stringResource(R.string.format_percent, 50)
    val measurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val gutter = remember(halfwayLabel, labelStyle, density) {
        with(density) {
            measurer
                .measure(halfwayLabel, labelStyle)
                .size.width
                .toDp()
        } + BAR_GAP
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Hidden from TalkBack: each bar says all of this itself.
        BarPositioned(
            index = selected,
            count = bars.size,
            gutter = gutter,
            modifier = Modifier.clearAndSetSemantics {},
        ) {
            val bar = selected?.let(bars::getOrNull)
            val amount = bar?.let { barAmountText(it, units) }
            Text(
                text = when {
                    bar == null -> ""
                    amount == null -> stringResource(R.string.rain_outlook_bar, bar.hour, bar.chance)
                    else -> stringResource(R.string.rain_outlook_bar_amount, bar.hour, bar.chance, amount)
                },
                style = labelStyle,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                minLines = 1,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        val halfway = size.height / 2
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, halfway),
                            end = Offset(size.width, halfway),
                            strokeWidth = stroke,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
                        )
                        drawLine(
                            color = lineColor,
                            start = Offset(0f, size.height - stroke / 2),
                            end = Offset(size.width, size.height - stroke / 2),
                            strokeWidth = stroke,
                        )
                    },
                horizontalArrangement = Arrangement.spacedBy(BAR_GAP),
                verticalAlignment = Alignment.Bottom,
            ) {
                bars.forEachIndexed { index, bar ->
                    val amount = barAmountText(bar, units)
                    val spoken = stringResource(R.string.rain_outlook_bar_spoken, bar.hour, bar.chance) +
                        amount?.let { stringResource(R.string.rain_outlook_bar_spoken_amount, it) }.orEmpty()
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .selectable(
                                selected = index == selected,
                                interactionSource = null,
                                indication = null,
                                onClick = { onBarTapped(index) },
                            ).semantics { contentDescription = spoken },
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(maxOf(MIN_BAR_HEIGHT, CHART_HEIGHT * (bar.chance / 100f)))
                                .background(
                                    color = if (selected == null ||
                                        selected == index
                                    ) {
                                        barColor
                                    } else {
                                        barColor.copy(alpha = DIMMED_BAR_ALPHA)
                                    },
                                    shape = BAR_SHAPE,
                                ),
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .width(gutter)
                    .fillMaxHeight()
                    .clearAndSetSemantics {},
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(text = halfwayLabel, style = labelStyle, color = secondary, maxLines = 1)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        HourLabels(bars = bars, gutter = gutter)
    }
}

/**
 * Lays out [content] (one child) centred over bar [index] of [count], kept inside the chart's
 * width; nothing shows for a null index, but the row keeps its height so the chart doesn't jump.
 */
@Composable
private fun BarPositioned(
    index: Int?,
    count: Int,
    gutter: Dp,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier.fillMaxWidth()) { measurables, constraints ->
        val placeable = measurables.single().measure(Constraints(maxWidth = constraints.maxWidth))
        layout(constraints.maxWidth, placeable.height) {
            if (index != null) {
                val centre = barCentre(index, count, constraints.maxWidth - gutter.roundToPx(), BAR_GAP.toPx())
                placeable.place(clampedStart(centre, placeable.width, constraints.maxWidth), 0)
            }
        }
    }
}

/**
 * "3PM" under every other bar from the first, each centred on its bar. At large text sizes a label
 * that would run into the one before is left out rather than overlapping it.
 */
@Composable
private fun HourLabels(
    bars: List<RainBar>,
    gutter: Dp,
) {
    val labelled = bars.indices.filter { it % 2 == 0 }
    Layout(
        content = {
            labelled.forEach { index ->
                Text(
                    text = bars[index].compactHour,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        },
        // Hidden from TalkBack: each bar names its hour.
        modifier = Modifier.fillMaxWidth().clearAndSetSemantics {},
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(maxWidth = width)) }
        val gap = BAR_GAP.toPx()
        layout(width, placeables.maxOfOrNull { it.height } ?: 0) {
            var free = 0
            placeables.forEachIndexed { i, placeable ->
                val centre = barCentre(labelled[i], bars.size, width - gutter.roundToPx(), gap)
                val x = clampedStart(centre, placeable.width, width)
                if (x >= free) {
                    placeable.place(x, 0)
                    free = x + placeable.width + gap.roundToInt()
                }
            }
        }
    }
}

/** The middle of bar [index] of [count] side by side across [width], [gap] apart. */
private fun barCentre(
    index: Int,
    count: Int,
    width: Int,
    gap: Float,
): Float {
    val barWidth = (width - gap * (count - 1)) / count
    return index * (barWidth + gap) + barWidth / 2
}

/** Where something [itemWidth] wide starts when centred on [centre], kept within [width]. */
private fun clampedStart(
    centre: Float,
    itemWidth: Int,
    width: Int,
): Int = (centre - itemWidth / 2f).roundToInt().coerceIn(0, (width - itemWidth).coerceAtLeast(0))

// --- Previews ---

private fun previewSlot(
    hour: String,
    chance: Int,
    mm: Double,
) = HourlySlot(
    timeLabel = hour,
    iconCode = "10d",
    description = "Light rain",
    temperature = 12.0,
    feelsLike = 11.0,
    windSpeed = 3.0,
    windDeg = 200,
    humidity = 80,
    pressure = 1008,
    units = WeatherUnits.METRIC,
    precipitationChance = chance,
    precipitationMm = mm,
)

@Preview(showBackground = true)
@Composable
private fun RainOutlookCardPreview() {
    val slots = listOf(
        previewSlot("3 PM", 10, 0.0),
        previewSlot("4 PM", 35, 0.2),
        previewSlot("5 PM", 70, 1.4),
        previewSlot("6 PM", 85, 2.6),
        previewSlot("7 PM", 60, 0.9),
        previewSlot("8 PM", 30, 0.1),
        previewSlot("9 PM", 15, 0.0),
        previewSlot("10 PM", 5, 0.0),
        previewSlot("11 PM", 0, 0.0),
        previewSlot("12 AM", 0, 0.0),
    )
    CatsDogsTheme {
        rainOutlook(slots, RainOutlookSettings(), WeatherUnits.METRIC, nextRainText = null)?.let {
            RainOutlookCard(outlook = it, units = WeatherUnits.METRIC, modifier = Modifier.padding(16.dp))
        }
    }
}
