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
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.ui.theme.PetSun
import com.fangjet.weather.ui.theme.WalkGreatBg
import com.fangjet.weather.ui.theme.WalkGreatText
import com.fangjet.weather.ui.theme.WalkHotBg
import com.fangjet.weather.ui.theme.WalkHotText
import com.fangjet.weather.ui.theme.WalkOkayBg
import com.fangjet.weather.ui.theme.WalkOkayText
import com.fangjet.weather.util.Daylight
import com.fangjet.weather.util.DistanceUnit
import com.fangjet.weather.util.PavementBand
import com.fangjet.weather.util.SkyCondition
import com.fangjet.weather.util.UvLine
import com.fangjet.weather.util.VisibilityBand
import com.fangjet.weather.util.WindForce
import com.fangjet.weather.util.daylight
import com.fangjet.weather.util.dewPointC
import com.fangjet.weather.util.keepUnitsTogether
import com.fangjet.weather.util.pavementBand
import com.fangjet.weather.util.skyCondition
import com.fangjet.weather.util.uvLine
import com.fangjet.weather.util.visibilityBand
import com.fangjet.weather.util.visibilityDistance
import com.fangjet.weather.util.windForce
import kotlin.math.roundToInt

/**
 * Today's details as a grid of tiles: feels like, wind, humidity, pavement, UV and air quality in pairs (an
 * odd one out takes the full width), then daylight (with the UV index, when UV has no tile) and the sky across
 * the full width. Each tile has a plain fact under its number; only the pavement, UV, air and sky tiles speak
 * for the pets. Remote Config can hide any of them. Same layout as iOS's TodayTiles.
 */
@Composable
internal fun TodayTiles(
    weather: CurrentWeather,
    // True when [weather] is the cached forecast's current hour standing in for an old reading:
    // there's no cloud cover or visibility to show.
    showingForecast: Boolean,
    // The hour in progress's pavement estimate, in [weather]'s units; null when not forecast.
    pavementNow: Double?,
    // Rain or snow lately, or snow lying: below freezing the pavement tile warns of ice.
    groundWet: Boolean,
    // Today's UV index, or null after dark or when not forecast.
    uvIndex: Double?,
    // Today's hour with the highest UV, for the UV tile's peak.
    uvPeak: HourlySlot?,
    // Null until it's fetched, or when it can't be.
    airQuality: AirQuality?,
    // Air quality speaks of walks with a dog, of people without one.
    hasDog: Boolean,
    nowEpochSeconds: Long,
    thresholds: PetWeatherThresholds,
    modifier: Modifier = Modifier,
    switches: TodayTileSwitches = TodayTileSwitches(),
) {
    val units = weather.units
    val sunrise = weather.sunriseEpoch
    val sunset = weather.sunsetEpoch
    val showsDaylight = switches.daylight && sunrise != null && sunset != null
    // UV gets its own tile when switched on, or when there's no Daylight tile to carry it.
    val showsUvTile = uvIndex != null && (switches.uv || !showsDaylight)
    val small = buildList<@Composable (Modifier) -> Unit> {
        if (switches.feelsLike) add { FeelsLikeTile(weather, it) }
        if (switches.wind) add { WindTile(weather, it) }
        if (switches.humidity) add { HumidityTile(weather, it) }
        if (switches.pawCheck && pavementNow != null) {
            add { PavementTile(pavementNow, groundWet, weather.feelsLike, units, thresholds, it) }
        }
        if (showsUvTile && uvIndex != null) add { UvTile(uvIndex, uvLine(uvPeak, nowEpochSeconds, hasDog), it) }
        if (airQuality != null) add { AirQualityTile(airQuality, hasDog, it) }
    }
    // Two tiles side by side leave too little room for large text, so it gets one per row.
    val perRow = if (LocalDensity.current.fontScale >= LARGE_FONT_SCALE) 1 else 2
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(TILE_GAP)) {
        // An odd tile out (usually Air quality, when Paw check is showing) takes the full width
        // rather than leaving a gap beside it.
        val paired = if (small.size % perRow == 0) small.size else small.size - 1
        EqualTileGrid(columns = perRow) {
            small.take(paired).forEach { tile -> tile(Modifier) }
        }
        small.drop(paired).forEach { tile -> tile(Modifier.fillMaxWidth()) }
        if (showsDaylight && sunrise != null && sunset != null) {
            DaylightTile(
                daylight(nowEpochSeconds, sunrise, sunset),
                sunrise,
                sunset,
                weather.offsetSeconds,
                uvIndex.takeUnless { showsUvTile },
            )
        }
        if (switches.sky) SkyTile(weather, showingForecast)
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
private fun TileValue(text: String) {
    Text(keepUnitsTogether(text), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
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

@Composable
private fun FeelsLikeTile(
    weather: CurrentWeather,
    modifier: Modifier,
) {
    Tile(Icons.Default.Thermostat, stringResource(R.string.label_feels_like), modifier) {
        TileValue(formatTemperature(weather.feelsLike, weather.units))
        TileNote(stringResource(R.string.today_actual, formatTemperature(weather.temperature, weather.units)))
    }
}

@Composable
private fun WindTile(
    weather: CurrentWeather,
    modifier: Modifier,
) {
    val force = windForce(weather.windSpeed, weather.units)
    val calm = force == WindForce.CALM || isCalmWind(weather.windSpeed, weather.units)
    Tile(Icons.Default.Air, stringResource(R.string.label_wind), modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            WindCompass(
                fromDegrees = weather.windDeg.takeUnless { calm },
                modifier = Modifier.size(36.dp),
            )
            TileValue(formatWind(weather.windSpeed, weather.units))
        }
        // The arrow shows the direction, so the note is just the wind's name; TalkBack, which can't
        // see the arrow, hears the direction too.
        TileNote(
            if (calm) stringResource(R.string.wind_calm) else stringResource(force.nameRes()),
            spoken = if (calm) {
                null
            } else {
                stringResource(
                    R.string.today_wind_from_spoken,
                    stringArrayResource(R.array.compass_points_spoken)[windDirectionIndex(weather.windDeg)],
                    stringResource(force.nameRes()),
                )
            },
        )
    }
}

/**
 * A compass dial with N at the top and an arrow pointing the way the wind blows, as Apple and
 * Google Weather draw it: a wind from the NW points SE. No arrow when it's calm.
 */
@Composable
private fun WindCompass(
    fromDegrees: Int?,
    modifier: Modifier = Modifier,
) {
    val ring = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
    val arrow = MaterialTheme.colorScheme.primary
    val textMeasurer = rememberTextMeasurer()
    val north = MaterialTheme.typography.labelSmall.copy(color = arrow, fontSize = 8.sp)
    Canvas(modifier = modifier) {
        val radius = size.minDimension / 2 - 1.dp.toPx()
        drawCircle(ring, radius = radius, style = Stroke(width = 1.5.dp.toPx()))
        val label = textMeasurer.measure("N", north)
        drawText(label, topLeft = Offset(center.x - label.size.width / 2f, center.y - radius + 1.dp.toPx()))
        if (fromDegrees != null) {
            // Drawn pointing north, then turned to point downwind (from + 180°).
            rotate(degrees = fromDegrees + 180f, pivot = center) {
                val tip = Offset(center.x, center.y - radius * 0.5f)
                val tail = Offset(center.x, center.y + radius * 0.5f)
                drawLine(arrow, start = tail, end = tip, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                val head = radius * 0.32f
                val path = Path().apply {
                    moveTo(tip.x, tip.y - 2.dp.toPx())
                    lineTo(tip.x - head * 0.7f, tip.y + head * 0.6f)
                    lineTo(tip.x + head * 0.7f, tip.y + head * 0.6f)
                    close()
                }
                drawPath(path, arrow)
            }
        }
    }
}

@Composable
private fun HumidityTile(
    weather: CurrentWeather,
    modifier: Modifier,
) {
    Tile(Icons.Default.WaterDrop, stringResource(R.string.label_humidity), modifier) {
        TileValue(stringResource(R.string.format_percent, weather.humidityPercent))
        LinearProgressIndicator(
            progress = { weather.humidityPercent / 100f },
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                // The percentage above already says it.
                .clearAndSetSemantics {},
            drawStopIndicator = {},
        )
        dewPointC(weather.units.toCelsius(weather.temperature), weather.humidityPercent)?.let { dewC ->
            TileNote(stringResource(R.string.today_dew_point, formatTemperature(weather.units.fromCelsius(dewC), weather.units)))
        }
    }
}

@Composable
private fun PavementTile(
    pavement: Double,
    groundWet: Boolean,
    feelsLike: Double,
    units: WeatherUnits,
    thresholds: PetWeatherThresholds,
    modifier: Modifier,
) {
    val band = pavementBand(units.toCelsius(pavement), groundWet, units.toCelsius(feelsLike), thresholds)
    Tile(Icons.Default.Pets, stringResource(R.string.label_pavement), modifier) {
        // An estimate for dark asphalt in direct sun, so a whole number, and said to be one.
        TileValue(
            stringResource(
                if (units == WeatherUnits.METRIC) R.string.format_about_temperature_c else R.string.format_about_temperature_f,
                pavement.roundToInt(),
            ),
        )
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

/** Same shades as the walk card's ratings: dark text on the light theme, pale on the dark. */
@Composable
private fun pavementTint(band: PavementBand): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (band) {
        PavementBand.SAFE -> if (dark) WalkGreatBg else WalkGreatText
        PavementBand.WARM, PavementBand.PAVEMENT_OK, PavementBand.FREEZING, PavementBand.ICY -> if (dark) WalkOkayBg else WalkOkayText
        PavementBand.TOO_HOT, PavementBand.DANGEROUS -> if (dark) WalkHotBg else WalkHotText
    }
}

@Composable
private fun petLineTint(): Color = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) WalkGreatBg else WalkGreatText

@Composable
private fun AirQualityTile(
    air: AirQuality,
    hasDog: Boolean,
    modifier: Modifier,
) {
    val bandName = stringArrayResource(
        if (air.scale == AirQuality.Scale.EUROPEAN) R.array.air_quality_bands_european else R.array.air_quality_bands_us,
    )[air.band]
    val line = stringArrayResource(if (hasDog) R.array.air_quality_lines_dog else R.array.air_quality_lines)[air.band]
    val pollutant = air.mainPollutant?.let {
        stringResource(
            R.string.air_quality_mostly,
            stringArrayResource(R.array.air_quality_pollutants)[it.ordinal],
        )
    }
    Tile(Icons.Default.Grain, stringResource(R.string.label_air_quality), modifier) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            TileValue(air.index.toString())
            Text(
                bandName,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        AirQualityBar(
            position = air.barPosition.toFloat(),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clearAndSetSemantics {},
        )
        // The pollutant behind it is for TalkBack only: another line would stretch every tile in
        // the grid, which all take the tallest one's height.
        TileNote(line, color = airTint(air.band), spoken = listOfNotNull(pollutant, line).joinToString(". "))
    }
}

/** The six air quality bands, good to worst, with a mark where the reading sits. */
@Composable
private fun AirQualityBar(
    position: Float,
    modifier: Modifier = Modifier,
) {
    val mark = MaterialTheme.colorScheme.onSurface
    Canvas(modifier.height(12.dp)) {
        val barHeight = 6.dp.toPx()
        val top = (size.height - barHeight) / 2
        val bandWidth = size.width / AirQuality.BANDS
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
            AIR_QUALITY_BANDS.forEachIndexed { i, color ->
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

private val AIR_QUALITY_BANDS = listOf(
    Color(0xFF4CAF50),
    Color(0xFFE6C531),
    Color(0xFFF08C2E),
    Color(0xFFD94141),
    Color(0xFF8E4AA8),
    Color(0xFF7A2230),
)

/** The walk card's shades again: green for good air, amber for so-so, red from unhealthy up. */
@Composable
private fun airTint(band: Int): Color {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    return when (band) {
        0 -> if (dark) WalkGreatBg else WalkGreatText
        1, 2 -> if (dark) WalkOkayBg else WalkOkayText
        else -> if (dark) WalkHotBg else WalkHotText
    }
}

@Composable
private fun UvTile(
    uv: Double,
    line: UvLine?,
    modifier: Modifier,
) {
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
private fun DaylightTile(
    state: Daylight,
    sunrise: Long,
    sunset: Long,
    utcOffsetSeconds: Int,
    uvIndex: Double?,
) {
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
private fun SkyTile(
    weather: CurrentWeather,
    showingForecast: Boolean,
) {
    val sky = skyCondition(weather.conditionMain, weather.cloudPercent.takeUnless { showingForecast }, weather.iconCode)
    val visibility = weather.visibilityMeters.takeUnless { showingForecast }
    val band = visibility?.let(::visibilityBand)
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
        if (band != null) TileNote(stringResource(band.mistyRes()), color = petLineTint())
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
private fun VisibilityBand.mistyRes(): Int =
    when (this) {
        VisibilityBand.EXCELLENT -> R.string.misty_visibility_excellent
        VisibilityBand.GOOD -> R.string.misty_visibility_good
        VisibilityBand.MODERATE -> R.string.misty_visibility_moderate
        VisibilityBand.POOR -> R.string.misty_visibility_poor
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
