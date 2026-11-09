package com.fangjet.weather.ui

import androidx.annotation.StringRes
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringArrayResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.model.AirTone
import com.fangjet.weather.model.DaylightTile
import com.fangjet.weather.model.SkyTile
import com.fangjet.weather.model.TodayTile
import com.fangjet.weather.model.TodayTilesState
import com.fangjet.weather.ui.theme.PetSun
import com.fangjet.weather.ui.theme.WalkColdBg
import com.fangjet.weather.ui.theme.WalkColdText
import com.fangjet.weather.ui.theme.WalkGreatBg
import com.fangjet.weather.ui.theme.WalkGreatText
import com.fangjet.weather.ui.theme.WalkHotBg
import com.fangjet.weather.ui.theme.WalkHotText
import com.fangjet.weather.ui.theme.WalkOkayBg
import com.fangjet.weather.ui.theme.WalkOkayText
import com.fangjet.weather.util.Daylight
import com.fangjet.weather.util.DistanceUnit
import com.fangjet.weather.util.FeelsLikeBand
import com.fangjet.weather.util.FluffLevel
import com.fangjet.weather.util.MistyLine
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.UvLine
import com.fangjet.weather.util.VisibilityBand
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.daylight
import com.fangjet.weather.util.formatEpochTime
import com.fangjet.weather.util.keepUnitsTogether
import com.fangjet.weather.util.pawScaleStep
import com.fangjet.weather.util.visibilityBand
import com.fangjet.weather.util.visibilityDistance
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Today's details as a grid of tiles: feels like, wind, humidity, pavement, UV and air quality in pairs (an
 * odd one out takes the full width), then daylight (with the UV index, when UV has no tile) and Misty's View
 * (the sky and how far she can see) across the full width. Each tile has a plain fact under its number; only
 * Fluff Factor, Paw Check, UV, Air Quality and Misty's View speak for the pets. Remote Config can hide any of them. Same layout as iOS's TodayTiles.
 */
@Composable
internal fun TodayTiles(
    // Which tiles show and what each says, worked out with the screen state.
    state: TodayTilesState,
    modifier: Modifier = Modifier,
) {
    // Two tiles side by side leave too little room for large text, so it gets one per row.
    val perRow = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 1 else 2
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        // An odd tile out (usually Air quality, when Paw check is showing) takes the full width
        // rather than leaving a gap beside it.
        val small = state.small
        val paired = if (small.size % perRow == 0) small.size else small.size - 1
        EqualTileGrid(columns = perRow) {
            small.take(paired).forEach { SmallTile(it, Modifier) }
        }
        small.drop(paired).forEach { SmallTile(it, Modifier.fillMaxWidth()) }
        state.daylight?.let { DaylightTileCard(it) }
        state.sky?.let { SkyTileCard(it) }
    }
}

@Composable
private fun SmallTile(
    tile: TodayTile,
    modifier: Modifier,
) {
    when (tile) {
        is TodayTile.FeelsLike -> FeelsLikeTile(tile, modifier)
        is TodayTile.Wind -> WindTile(tile, modifier)
        is TodayTile.Fluff -> HumidityTile(tile, modifier)
        is TodayTile.PawCheck -> PavementTile(tile, modifier)
        is TodayTile.Uv -> UvTile(tile, modifier)
        is TodayTile.Air -> AirQualityTile(tile, modifier)
    }
}

/**
 * The small tiles in rows of [columns]. Side by side, every tile is as tall as the tallest, so the
 * grid reads as one block however much each tile holds; one per row, each keeps its own height.
 */
@Composable
private fun EqualTileGrid(
    columns: Int,
    content: @Composable () -> Unit,
) {
    Layout(content = content) { measurables, constraints ->
        val gap = TILE_GAP.roundToPx()
        val width = (constraints.maxWidth - gap * (columns - 1)) / columns
        val natural = measurables.map { it.maxIntrinsicHeight(width) }
        val heights = if (columns > 1) natural.map { natural.max() } else natural
        val placeables = measurables.mapIndexed { i, measurable -> measurable.measure(Constraints.fixed(width, heights[i])) }
        val rowHeights = heights.chunked(columns).map { it.max() }
        val height = rowHeights.sum() + gap * (rowHeights.size - 1).coerceAtLeast(0)
        layout(constraints.maxWidth, height) {
            var y = 0
            placeables.chunked(columns).forEachIndexed { row, rowPlaceables ->
                rowPlaceables.forEachIndexed { column, placeable -> placeable.placeRelative(column * (width + gap), y) }
                y += rowHeights[row] + gap
            }
        }
    }
}

@Composable
private fun Tile(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    // Extra detail at the right of the label row, such as the UV index on the Daylight tile.
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(seasonalCardColor())
            .padding(horizontal = 14.dp, vertical = 12.dp)
            .semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            val headerColor = seasonalTileHeaderColor()
            Icon(icon, contentDescription = null, tint = headerColor, modifier = Modifier.size(16.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = headerColor)
            if (trailing != null) {
                Spacer(Modifier.weight(1f))
                Text(
                    keepUnitsTogether(trailing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        content()
    }
}

@Composable
private fun TileValue(
    text: String,
    modifier: Modifier = Modifier,
) {
    Text(keepUnitsTogether(text), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = modifier)
}

@Composable
private fun TileNote(
    text: String,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    // What a screen reader says instead of [text], when the text alone reads badly ("From the NW").
    spoken: String? = null,
) {
    Text(
        keepUnitsTogether(text),
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        modifier = if (spoken != null) Modifier.clearAndSetSemantics { this.text = AnnotatedString(spoken) } else Modifier,
    )
}

/** The feels-like temperature with its band ("Hot"), the band bar, and how far it is from the actual temperature. */
@Composable
private fun FeelsLikeTile(
    tile: TodayTile.FeelsLike,
    modifier: Modifier,
) {
    Tile(Icons.Default.Thermostat, stringResource(R.string.label_feels_like), modifier) {
        // The same size as the other tiles' values, as Air Quality has its band beside it.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TileValue(formatTemperature(tile.feelsLike, tile.units), Modifier.alignByBaseline())
            Text(
                stringResource(tile.band.nameRes()),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = feelsLikeTint(tile.band),
                maxLines = 1,
                modifier = Modifier.alignByBaseline(),
            )
        }
        BandBar(
            bands = feelsLikeBands(),
            position = tile.barPosition.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clearAndSetSemantics {},
        )
        TileNote(
            when {
                tile.gap > 0 -> stringResource(R.string.feels_like_warmer, tile.gap)
                tile.gap < 0 -> stringResource(R.string.feels_like_cooler, -tile.gap)
                else -> stringResource(R.string.feels_like_same)
            },
        )
    }
}

/** Cold to hot. The two blues are lighter on the dark theme, where the darker one barely shows. */
@Composable
private fun feelsLikeBands(): List<Color> {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return listOf(
        if (dark) Color(0xFF378ADD) else Color(0xFF185FA5),
        if (dark) Color(0xFF85B7EB) else Color(0xFF378ADD),
        Color(0xFF4CAF50),
        Color(0xFFF08C2E),
        Color(0xFFD94141),
    )
}

/** The walk card's shades: blue for cold and chilly, green for mild, amber for warm, red for hot. */
@Composable
private fun feelsLikeTint(band: FeelsLikeBand): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (band) {
        FeelsLikeBand.COLD, FeelsLikeBand.CHILLY -> if (dark) WalkColdBg else WalkColdText
        FeelsLikeBand.MILD -> if (dark) WalkGreatBg else WalkGreatText
        FeelsLikeBand.WARM -> if (dark) WalkOkayBg else WalkOkayText
        FeelsLikeBand.HOT -> if (dark) WalkHotBg else WalkHotText
    }
}

@StringRes
private fun FeelsLikeBand.nameRes(): Int =
    when (this) {
        FeelsLikeBand.COLD -> R.string.feels_like_cold
        FeelsLikeBand.CHILLY -> R.string.feels_like_chilly
        FeelsLikeBand.MILD -> R.string.feels_like_mild
        FeelsLikeBand.WARM -> R.string.feels_like_warm
        FeelsLikeBand.HOT -> R.string.feels_like_hot
    }

/** The speed beside a windsock whose droop shows the strength, with where the wind comes from and any gusts that stand out underneath. */
@Composable
private fun WindTile(
    tile: TodayTile.Wind,
    modifier: Modifier,
) {
    Tile(Icons.Default.Air, stringResource(R.string.label_wind), modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TileValue(formatWind(tile.speed, tile.units))
            Spacer(Modifier.weight(1f))
            Windsock(droopDegrees = tile.droopDegrees, modifier = Modifier.size(40.dp))
        }
        if (tile.calm) {
            TileNote(stringResource(R.string.wind_calm))
        } else {
            // The windsock shows how strong it is, so the note gives where it comes from; TalkBack
            // also hears the wind's name.
            val from = stringArrayResource(R.array.compass_points)[tile.directionIndex]
            val fromSpoken = stringArrayResource(R.array.compass_points_spoken)[tile.directionIndex]
            val name = stringResource(tile.force.nameRes()).lowercase()
            val gust = tile.gust?.let { formatWind(it, tile.units) }
            TileNote(
                if (gust == null) {
                    stringResource(R.string.today_wind_from, from)
                } else {
                    stringResource(R.string.today_wind_from_gusts, from, gust.substringBefore(' '))
                },
                spoken = if (gust == null) {
                    stringResource(R.string.today_wind_from_spoken, fromSpoken, name)
                } else {
                    stringResource(R.string.today_wind_from_gusts_spoken, fromSpoken, name, gust)
                },
            )
        }
    }
}

/**
 * A windsock on its pole, side on: limp when calm, flying straight out in a strong breeze. Five
 * stripes, orange and white, narrowing to the tail. Same drawing as iOS's `Windsock`.
 */
@Composable
private fun Windsock(
    droopDegrees: Double,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val pole = if (dark) Color(0xFFB4B2A9) else Color(0xFF5F5E5A)
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        // Low enough that the mouth clears the top when it flies straight out.
        val pivot = Offset(3.dp.toPx(), 7.dp.toPx())
        drawLine(
            pole,
            start = Offset(pivot.x, 1.5.dp.toPx()),
            end = Offset(pivot.x, size.height - 1.5.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
            cap = StrokeCap.Round,
        )
        val length = minOf(size.width - pivot.x - 2.dp.toPx(), size.height - pivot.y - 2.dp.toPx())
        val mouth = length * 0.17f
        val tail = length * 0.08f
        // Drawn flying straight out to the right of the pivot, then turned down by the droop.
        rotate(degrees = droopDegrees.toFloat(), pivot = pivot) {
            for (stripe in 0 until WINDSOCK_STRIPES) {
                val x0 = pivot.x + length * stripe / WINDSOCK_STRIPES
                val x1 = pivot.x + length * (stripe + 1) / WINDSOCK_STRIPES
                val h0 = mouth + (tail - mouth) * stripe / WINDSOCK_STRIPES
                val h1 = mouth + (tail - mouth) * (stripe + 1) / WINDSOCK_STRIPES
                val band = Path().apply {
                    moveTo(x0, pivot.y - h0)
                    lineTo(x1, pivot.y - h1)
                    lineTo(x1, pivot.y + h1)
                    lineTo(x0, pivot.y + h0)
                    close()
                }
                drawPath(band, if (stripe % 2 == 0) WINDSOCK_ORANGE else Color.White)
                drawPath(band, WINDSOCK_SEAM, style = Stroke(width = 0.6.dp.toPx()))
            }
        }
    }
}

/**
 * Fluff Factor: the humidity with the dew point under it, beside Misty, whose fur fluffs up as the
 * dew point rises, and her line about it.
 */
private const val WINDSOCK_STRIPES = 5
private val WINDSOCK_ORANGE = Color(0xFFF08C2E)
private val WINDSOCK_SEAM = Color(0xFFB3621C)

@Composable
private fun HumidityTile(
    tile: TodayTile.Fluff,
    modifier: Modifier,
) {
    Tile(Icons.Default.WaterDrop, stringResource(R.string.label_fluff_factor), modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                val spoken = stringResource(R.string.format_humidity_spoken, tile.humidityPercent)
                TileValue(
                    stringResource(R.string.format_percent, tile.humidityPercent),
                    modifier = Modifier.clearAndSetSemantics { contentDescription = spoken },
                )
                if (tile.dewPoint != null) {
                    Text(
                        stringResource(R.string.today_dew_point, formatTemperature(tile.dewPoint, tile.units, unitShown = false)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
            FluffyMisty(tile.level, Modifier.size(width = 48.dp, height = 42.dp))
        }
        TileNote(stringResource(fluffLineRes(tile.level, tile.hasDog)))
    }
}

/** Misty's line on the Fluff Factor tile. With a dog, muggy says the pup is panting: dogs cool down by panting, which works badly in muggy air. */
@StringRes
internal fun fluffLineRes(
    level: FluffLevel,
    hasDog: Boolean,
): Int =
    when (level) {
        FluffLevel.DRY -> R.string.fluff_dry
        FluffLevel.COMFY -> R.string.fluff_comfy
        FluffLevel.STICKY -> R.string.fluff_sticky
        FluffLevel.MUGGY -> if (hasDog) R.string.fluff_muggy_dog else R.string.fluff_muggy
        FluffLevel.SOUPY -> R.string.fluff_soupy
    }

/** How far the tufts reach past Misty's head, by [FluffLevel]. */
private val FLUFF_TUFT_REACH = listOf(0f, 0f, 1.4f, 2.6f, 4f)

/**
 * Misty's head, as in the hero, with her fur sleek in dry air and fluffing up as it gets muggy: a
 * ring of tufts round her head grows with each [FluffLevel], and dry air gives her static sparks.
 * Drawn on a 60 x 52 grid. Same drawing as iOS's `FluffyMisty`.
 */
@Composable
private fun FluffyMisty(
    level: FluffLevel,
    modifier: Modifier = Modifier,
) {
    // Lighter on the dark card, where her usual grey sinks into it.
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val fur = if (dark) Color(0xFF8C8B85) else Color(0xFF5F5E5A)
    Canvas(modifier = modifier.clearAndSetSemantics {}) {
        val scale = minOf(size.width / 60f, size.height / 52f)
        val originX = (size.width - 60f * scale) / 2f
        val originY = (size.height - 52f * scale) / 2f

        fun point(
            x: Float,
            y: Float,
        ) = Offset(originX + x * scale, originY + y * scale)

        fun poly(
            color: Color,
            points: List<Pair<Float, Float>>,
        ) {
            val path = Path()
            points.forEachIndexed { index, (x, y) ->
                val at = point(x, y)
                if (index == 0) path.moveTo(at.x, at.y) else path.lineTo(at.x, at.y)
            }
            path.close()
            drawPath(path, color)
        }

        fun oval(
            color: Color,
            cx: Float,
            cy: Float,
            rx: Float,
            ry: Float,
        ) = drawOval(color, topLeft = point(cx - rx, cy - ry), size = Size(rx * 2 * scale, ry * 2 * scale))

        val reach = FLUFF_TUFT_REACH[level.ordinal]
        if (reach > 0f) {
            val tufts = 18
            poly(
                fur,
                (0 until tufts).map { index ->
                    val angle = index.toDouble() / tufts * 2 * PI - PI / 2
                    val radius = 17f + if (index % 2 == 0) 0f else reach
                    (30f + radius * cos(angle).toFloat()) to (31f + radius * sin(angle).toFloat())
                },
            )
        }
        poly(fur, listOf(15f to 22f, 18f to 6f, 27f to 17f))
        poly(fur, listOf(33f to 17f, 42f to 6f, 45f to 22f))
        poly(Color(0xFFED93B1), listOf(18f to 19f, 19.5f to 11f, 24f to 17f))
        poly(Color(0xFFED93B1), listOf(36f to 17f, 40.5f to 11f, 42f to 19f))
        oval(fur, 30f, 31f, 16f, 16f)
        poly(Color(0xFFF1EFE8), listOf(28.5f to 17f, 31.5f to 17f, 34f to 33f, 26f to 33f))
        oval(Color(0xFFF1EFE8), 26f, 36f, 6f, 5f)
        oval(Color(0xFFF1EFE8), 34f, 36f, 6f, 5f)
        oval(Color(0xFF10324A), 24.5f, 28f, 2f, 2f)
        oval(Color(0xFF10324A), 35.5f, 28f, 2f, 2f)
        poly(Color(0xFFE8849A), listOf(28.2f to 32f, 31.8f to 32f, 30f to 34.3f))
        if (level == FluffLevel.DRY) {
            // Static sparks either side.
            for (side in listOf(
                listOf(5f to 12f, 9f to 15f, 6f to 17f, 10f to 21f),
                listOf(55f to 12f, 51f to 15f, 54f to 17f, 50f to 21f),
            )) {
                val spark = Path()
                side.forEachIndexed { index, (x, y) ->
                    val at = point(x, y)
                    if (index == 0) spark.moveTo(at.x, at.y) else spark.lineTo(at.x, at.y)
                }
                drawPath(spark, Color(0xFFEF9F27), style = Stroke(width = 1.4f * scale, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
private fun PavementTile(
    tile: TodayTile.PawCheck,
    modifier: Modifier,
) {
    val band = tile.band
    Tile(Icons.Default.Pets, stringResource(R.string.label_pavement), modifier) {
        // An estimate for dark asphalt in direct sun, so a whole number, and said to be one.
        TileValue(stringResource(R.string.format_about_temperature, formatTemperature(tile.pavement, tile.units)))
        PawScale(step = band.pawScaleStep)
        TileNote(
            text = stringResource(
                when (band) {
                    PavementBand.DANGEROUS -> R.string.pavement_dangerous
                    PavementBand.TOO_HOT -> R.string.pavement_too_hot
                    PavementBand.WARM -> R.string.pavement_warm
                    PavementBand.SAFE -> R.string.pavement_safe
                    PavementBand.PAVEMENT_OK -> R.string.pavement_ok
                    PavementBand.FREEZING -> R.string.pavement_freezing
                    PavementBand.ICY -> R.string.pavement_icy
                },
            ),
            color = pavementTint(band),
        )
    }
}

/**
 * Same shades as the walk card's ratings: dark text on the light theme, pale on the dark. Blue for
 * icy and below freezing, as on the paw scale.
 */
@Composable
private fun pavementTint(band: PavementBand): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (band) {
        PavementBand.SAFE -> if (dark) WalkGreatBg else WalkGreatText
        PavementBand.WARM, PavementBand.PAVEMENT_OK -> if (dark) WalkOkayBg else WalkOkayText
        PavementBand.FREEZING, PavementBand.ICY -> if (dark) WalkColdBg else WalkColdText
        PavementBand.TOO_HOT, PavementBand.DANGEROUS -> if (dark) WalkHotBg else WalkHotText
    }
}

/**
 * The Paw check tile's five paws, icy to dangerous: the pavement's own full size and colour, the
 * rest small and faded. The note says it in words, so TalkBack skips it. Same colours as iOS's
 * `PawScale`.
 */
@Composable
private fun PawScale(
    step: Int,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    Row(
        modifier = modifier.clearAndSetSemantics {},
        horizontalArrangement = Arrangement.spacedBy(5.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        (if (dark) PAW_SCALE_DARK else PAW_SCALE_LIGHT).forEachIndexed { index, color ->
            Icon(
                imageVector = Icons.Default.Pets,
                contentDescription = null,
                // A little stronger on the dark card, where faint colours turn muddy.
                tint = if (index == step) color else color.copy(alpha = if (dark) 0.4f else 0.3f),
                modifier = Modifier.size(if (index == step) 22.dp else 15.dp),
            )
        }
    }
}

/**
 * Blue, green, orange, red and maroon, like the Feels Like and Air Quality bars. The blue and the
 * maroon are lighter on the dark theme, where the darker ones barely show.
 */
private val PAW_SCALE_LIGHT = listOf(Color(0xFF378ADD), Color(0xFF4CAF50), Color(0xFFF08C2E), Color(0xFFD94141), Color(0xFF7A2230))
private val PAW_SCALE_DARK = listOf(Color(0xFF85B7EB), Color(0xFF4CAF50), Color(0xFFF08C2E), Color(0xFFD94141), Color(0xFFC25A68))

@Composable
private fun petLineTint(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) WalkGreatBg else WalkGreatText

@Composable
private fun AirQualityTile(
    tile: TodayTile.Air,
    modifier: Modifier,
) {
    val air = tile.air
    val bandName = stringArrayResource(
        if (air.scale == AirQuality.Scale.EUROPEAN) R.array.air_quality_bands_european else R.array.air_quality_bands_us,
    )[air.band]
    val line = stringArrayResource(if (tile.hasDog) R.array.air_quality_lines_dog else R.array.air_quality_lines)[tile.lineIndex]
    val pollutant = air.mainPollutant?.let {
        stringResource(
            R.string.air_quality_mostly,
            stringArrayResource(R.array.air_quality_pollutants)[it.ordinal],
        )
    }
    Tile(Icons.Default.Grain, stringResource(R.string.label_air_quality), modifier) {
        // On one baseline, as Feels Like's "49 °F Mild": the index and its band are different sizes.
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TileValue(air.index.toString(), Modifier.alignByBaseline())
            Text(
                bandName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.alignByBaseline(),
            )
        }
        BandBar(
            bands = AIR_QUALITY_BANDS,
            position = air.barPosition.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clearAndSetSemantics {},
        )
        // The pollutant behind it is for TalkBack only: another line would stretch every tile in
        // the grid, which all take the tallest one's height.
        TileNote(line, color = airTint(tile.tone), spoken = listOfNotNull(pollutant, line).joinToString(". "))
    }
}

/**
 * Bands of equal width, such as the air quality bands from good to worst, with a mark where the
 * reading sits.
 */
@Composable
private fun BandBar(
    bands: List<Color>,
    position: Float,
    modifier: Modifier = Modifier,
) {
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.height(12.dp)) {
        val barHeight = 6.dp.toPx()
        val top = (size.height - barHeight) / 2
        val bandWidth = size.width / bands.size
        clipPath(
            Path().apply {
                addRoundRect(
                    RoundRect(
                        0f,
                        top,
                        size.width,
                        top + barHeight,
                        CornerRadius(barHeight / 2),
                    ),
                )
            },
        ) {
            bands.forEachIndexed { i, color ->
                drawRect(color, Offset(i * bandWidth, top), Size(bandWidth + 1, barHeight))
            }
        }
        val markWidth = 4.dp.toPx()
        val x = (size.width * position - markWidth / 2).coerceIn(0f, size.width - markWidth)
        drawRoundRect(
            mark,
            Offset(x, 0f),
            Size(markWidth, size.height),
            CornerRadius(markWidth / 2),
        )
    }
}

/** The six air quality bands, good to worst. */
private val AIR_QUALITY_BANDS = listOf(
    Color(0xFF4CAF50),
    Color(0xFFE6C531),
    Color(0xFFF08C2E),
    Color(0xFFD94141),
    Color(0xFF8E4AA8),
    Color(0xFF7A2230),
)

/** The walk card's shades again: green for good air, amber for so-so, red for bad. */
@Composable
private fun airTint(tone: AirTone): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (tone) {
        AirTone.BAD -> if (dark) WalkHotBg else WalkHotText
        AirTone.GOOD -> if (dark) WalkGreatBg else WalkGreatText
        AirTone.SO_SO -> if (dark) WalkOkayBg else WalkOkayText
    }
}

@Composable
private fun UvTile(
    tile: TodayTile.Uv,
    modifier: Modifier,
) {
    val uv = tile.uvIndex
    val line = tile.line
    Tile(Icons.Default.LightMode, stringResource(R.string.label_uv_index), modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TileValue(uv.roundToInt().toString())
            Text(
                stringResource(uvLevelRes(uv)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (line) {
            is UvLine.Peak -> TileNote(stringResource(R.string.uv_peak, line.uv, line.timeLabel))
            UvLine.PaleNoses -> TileNote(stringResource(R.string.uv_pale_noses), color = petLineTint())
            UvLine.AtPeak -> TileNote(stringResource(R.string.uv_at_peak))
            UvLine.EasingOff -> TileNote(stringResource(R.string.uv_easing_off))
            null -> Unit
        }
    }
}

@Composable
private fun DaylightTileCard(tile: DaylightTile) {
    val state = tile.daylight
    val sunrise = tile.sunrise
    val sunset = tile.sunset
    val utcOffsetSeconds = tile.utcOffsetSeconds
    val uvIndex = tile.uvIndex
    val uvNote = uvIndex?.let { stringResource(R.string.today_uv, it.roundToInt(), stringResource(uvLevelRes(it))) }
    Tile(Icons.Default.WbSunny, stringResource(R.string.label_daylight), trailing = uvNote) {
        SunArc(
            fraction = when (state) {
                is Daylight.BeforeSunrise -> 0f
                is Daylight.Daytime -> state.fraction
                Daylight.AfterSunset -> 1f
            },
            up = state is Daylight.Daytime,
            modifier = Modifier
                .fillMaxWidth()
                .height(44.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TileNote(formatEpochTime(sunrise, utcOffsetSeconds))
            Text(
                text = when (state) {
                    is Daylight.BeforeSunrise -> stringResource(R.string.today_sunrise_in, formatDuration(state.secondsUntil))
                    is Daylight.Daytime -> stringResource(R.string.today_daylight_left, formatDuration(state.secondsLeft))
                    Daylight.AfterSunset -> stringResource(R.string.today_sun_set)
                },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            TileNote(formatEpochTime(sunset, utcOffsetSeconds))
        }
    }
}

/** The sun's path from sunrise (left) to sunset (right), solid up to now, with the sun on it. */
@Composable
private fun SunArc(
    fraction: Float,
    up: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val sun = if (up) PetSun else PetSun.copy(alpha = 0.45f)
    Canvas(modifier = modifier) {
        val inset = 6.dp.toPx()
        val start = Offset(inset, size.height - 2.dp.toPx())
        val end = Offset(size.width - inset, size.height - 2.dp.toPx())
        // The control point sits above the frame so the curve's peak reaches near the top.
        val control = Offset(size.width / 2, -size.height + 4.dp.toPx() * 2)
        drawPath(
            Path().apply {
                moveTo(start.x, start.y)
                quadraticTo(control.x, control.y, end.x, end.y)
            },
            track,
            style = Stroke(width = 1.5.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))),
        )
        val t = fraction.coerceIn(0f, 1f)
        // de Casteljau: the part of the curve from sunrise to t is itself a quadratic.
        val partControl = lerp(start, control, t)
        val now = lerp(partControl, lerp(control, end, t), t)
        drawPath(
            Path().apply {
                moveTo(start.x, start.y)
                quadraticTo(partControl.x, partControl.y, now.x, now.y)
            },
            sun,
            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round),
        )
        drawCircle(sun, radius = 6.dp.toPx(), center = now)
    }
}

private fun lerp(
    a: Offset,
    b: Offset,
    t: Float,
): Offset = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)

@Composable
private fun SkyTileCard(tile: SkyTile) {
    val sky = tile.sky
    val visibility = tile.visibilityMeters
    val band = tile.visibilityBand
    Tile(Icons.Default.Visibility, stringResource(R.string.label_sky)) {
        val skyText = stringResource(sky.nameRes())
        Text(
            text = keepUnitsTogether(
                when {
                    visibility == null || band == null -> skyText
                    else -> {
                        val distance = visibilityDistance(visibility, LocalDistanceInMiles.current)
                        if (distance == null) {
                            stringResource(R.string.today_sky_visibility, skyText, stringResource(band.nameRes()))
                        } else {
                            val amount = when (distance.unit) {
                                DistanceUnit.MILES -> if (distance.decimals == 0) {
                                    stringResource(R.string.format_visibility_whole_mi, distance.value.roundToInt())
                                } else {
                                    stringResource(R.string.format_visibility_tenths_mi, distance.value)
                                }
                                DistanceUnit.KILOMETERS -> stringResource(R.string.format_visibility_whole_km, distance.value.roundToInt())
                                DistanceUnit.METERS -> stringResource(R.string.format_visibility_m, distance.value.roundToInt())
                            }
                            stringResource(R.string.today_sky_visibility_distance, skyText, stringResource(band.nameRes()), amount)
                        }
                    }
                },
            ),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        tile.mistyLine?.let { TileNote(stringResource(it.stringRes()), color = petLineTint()) }
    }
}

@Composable
private fun formatDuration(seconds: Long): String {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
        hours == 0 -> stringResource(R.string.duration_minutes, minutes)
        minutes == 0 -> stringResource(R.string.duration_hours, hours)
        else -> stringResource(R.string.duration_hours_minutes, hours, minutes)
    }
}

@StringRes
private fun SkyCondition.nameRes(): Int =
    when (this) {
        SkyCondition.CLEAR -> R.string.sky_clear
        SkyCondition.PARTLY_CLOUDY -> R.string.sky_partly_cloudy
        SkyCondition.MOSTLY_CLOUDY -> R.string.sky_mostly_cloudy
        SkyCondition.OVERCAST -> R.string.sky_overcast
        SkyCondition.MIST -> R.string.sky_mist
        SkyCondition.HAZE -> R.string.sky_haze
        SkyCondition.FOG -> R.string.sky_fog
        SkyCondition.SMOKE -> R.string.sky_smoke
        SkyCondition.DUST -> R.string.sky_dust
    }

@StringRes
private fun VisibilityBand.nameRes(): Int =
    when (this) {
        VisibilityBand.EXCELLENT -> R.string.visibility_excellent
        VisibilityBand.GOOD -> R.string.visibility_good
        VisibilityBand.MODERATE -> R.string.visibility_moderate
        VisibilityBand.POOR -> R.string.visibility_poor
    }

@StringRes
private fun MistyLine.stringRes(): Int =
    when (this) {
        MistyLine.WHOLE_STREET -> R.string.misty_visibility_excellent
        MistyLine.DOWN_THE_BLOCK -> R.string.misty_visibility_good
        MistyLine.END_OF_STREET_FUZZY -> R.string.misty_visibility_moderate
        MistyLine.PAST_THE_GARDEN -> R.string.misty_visibility_poor
        MistyLine.STREETLIGHTS_FUZZY -> R.string.misty_night_streetlights_fuzzy
        MistyLine.STARS -> R.string.misty_night_stars
        MistyLine.FEW_STARS -> R.string.misty_night_few_stars
        MistyLine.NO_STARS -> R.string.misty_night_no_stars
    }

@StringRes
private fun WindForce.nameRes(): Int =
    when (this) {
        WindForce.CALM -> R.string.wind_calm
        WindForce.LIGHT_AIR -> R.string.wind_light_air
        WindForce.LIGHT_BREEZE -> R.string.wind_light_breeze
        WindForce.GENTLE_BREEZE -> R.string.wind_gentle_breeze
        WindForce.MODERATE_BREEZE -> R.string.wind_moderate_breeze
        WindForce.FRESH_BREEZE -> R.string.wind_fresh_breeze
        WindForce.STRONG_BREEZE -> R.string.wind_strong_breeze
        WindForce.NEAR_GALE -> R.string.wind_near_gale
        WindForce.GALE -> R.string.wind_gale
        WindForce.STRONG_GALE -> R.string.wind_strong_gale
        WindForce.STORM -> R.string.wind_storm
        WindForce.VIOLENT_STORM -> R.string.wind_violent_storm
        WindForce.HURRICANE -> R.string.wind_hurricane
    }

private val TILE_GAP = 8.dp

/** At this font scale (150%) and above, the small tiles go one per row. */
private const val LARGE_FONT_SCALE = 1.5f
