package com.fangjet.weather.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fangjet.weather.R
import com.fangjet.weather.data.RADAR_TILE_PX
import com.fangjet.weather.data.RadarTile
import com.fangjet.weather.domain.RadarTimeline
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.model.LoadingState
import com.fangjet.weather.model.RadarViewModel
import com.fangjet.weather.ui.theme.RadarLegendColors
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RadarCard(
    location: SavedLocation?,
    modifier: Modifier = Modifier,
    utcOffsetSeconds: Int = 0,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.End,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        RadarMapCard(location = location, utcOffsetSeconds = utcOffsetSeconds)
        if (location?.latitude != null && location.longitude != null) {
            RadarCredits()
        }
    }
}

/**
 * OpenStreetMap's licence requires visible attribution linking to its copyright page, and
 * RainViewer asks to be credited for its free radar data. Kept at labelSmall's default 11sp
 * whatever the system font size, so the credit stays a small footnote under the map.
 */
@Composable
private fun RadarCredits() {
    // dp converted to sp at the current font scale renders the same size at every scale.
    val (fontSize, lineHeight) = with(LocalDensity.current) { 11.dp.toSp() to 16.dp.toSp() }
    val linkStyle = TextLinkStyles(style = SpanStyle(color = MaterialTheme.colorScheme.onSurfaceVariant))
    val osm = stringResource(R.string.radar_credit_osm)
    val rainViewer = stringResource(R.string.radar_credit_rainviewer)
    Text(
        text = buildAnnotatedString {
            withLink(LinkAnnotation.Url("https://www.openstreetmap.org/copyright", linkStyle)) { append(osm) }
            append(" · ")
            withLink(LinkAnnotation.Url("https://www.rainviewer.com", linkStyle)) { append(rainViewer) }
        },
        style = MaterialTheme.typography.labelSmall.copy(fontSize = fontSize, lineHeight = lineHeight),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.End,
    )
}

@Composable
private fun RadarMapCard(
    location: SavedLocation?,
    utcOffsetSeconds: Int,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        if (location?.latitude == null || location.longitude == null) {
            CenteredMessage(
                text = if (location == null) {
                    stringResource(R.string.radar_no_location)
                } else {
                    stringResource(R.string.radar_unavailable_coords)
                },
            )
            return@Card
        }

        // Obtained here (not as a default arg) so the coordinate-less path above never needs Hilt.
        val viewModel: RadarViewModel = hiltViewModel()
        val tileInfo = remember(location.latitude, location.longitude) {
            RadarTile.from(location.latitude, location.longitude)
        }
        // The view model loads the city's map and the frames' overlays.
        LaunchedEffect(viewModel, tileInfo) { viewModel.show(tileInfo) }
        // Opening the screen, coming back to the app or switching city: fetch the frames if they're
        // missing or stale, wherever the card is on screen (load() is a no-op while they're fresh).
        // While the screen stays open, keep them current, but only while the card is in view.
        // Lives below the coordinate-less early return, which is the path the UI tests render.
        val lifecycleOwner = LocalLifecycleOwner.current
        LaunchedEffect(viewModel, lifecycleOwner, tileInfo) {
            lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
                viewModel.load()
                while (true) {
                    delay(RadarViewModel.RECHECK_INTERVAL_MS)
                    viewModel.recheck()
                }
            }
        }
        val timelineState by viewModel.timeline.collectAsStateWithLifecycle()
        val baseMap by viewModel.baseMap.collectAsStateWithLifecycle()
        val overlays by viewModel.overlays.collectAsStateWithLifecycle()

        RadarView(
            // The card starts below the fold on a phone. boundsInWindow() is clipped by the
            // scrolling column, so it's empty while the card is scrolled out of sight.
            modifier = Modifier.onGloballyPositioned { viewModel.setVisible(it.boundsInWindow().height > 0f) },
            tileInfo = tileInfo,
            baseMap = baseMap,
            overlays = overlays,
            timelineState = timelineState,
            frameIntervalMs = viewModel.frameIntervalMs,
            utcOffsetSeconds = utcOffsetSeconds,
            onRetry = viewModel::retry,
        )
    }
}

@Composable
private fun CenteredMessage(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(340.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}

@Composable
private fun RadarView(
    tileInfo: RadarTile,
    baseMap: Bitmap?,
    overlays: Map<String, Bitmap>,
    timelineState: LoadingState<RadarTimeline>,
    frameIntervalMs: Long,
    utcOffsetSeconds: Int,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val timeline = (timelineState as? LoadingState.Success)?.data
    val frames = timeline?.frames.orEmpty()

    // Start on the most recent observed frame ("now").
    val nowIndex = remember(frames) {
        frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
    }
    var frameIndex by remember(frames) { mutableIntStateOf(nowIndex) }
    var playing by remember(frames) { mutableStateOf(true) }

    LaunchedEffect(playing, frames, frameIntervalMs) {
        if (!playing || frames.isEmpty()) return@LaunchedEffect
        while (true) {
            delay(frameIntervalMs)
            frameIndex = (frameIndex + 1) % frames.size
        }
    }

    val baseImage = remember(baseMap) { baseMap?.asImageBitmap() }
    val overlay = frames.getOrNull(frameIndex)?.let { overlays[it.path] }
    val currentOverlay = remember(overlay) { overlay?.asImageBitmap() }
    val frameLabel = frames.getOrNull(frameIndex)?.let { frame ->
        frameTimeLabel(
            epochSeconds = frame.timeEpochSeconds,
            utcOffsetSeconds = utcOffsetSeconds,
            isNow = frameIndex == nowIndex,
            isForecast = frame.isForecast,
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        RadarCanvas(tileInfo = tileInfo, baseMap = baseImage, overlay = currentOverlay)

        // Timestamp chip (top-start), rendered in the selected city's local time (via its UTC
        // offset) rather than the device's timezone.
        frameLabel?.let { label ->
            OverlayChip(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        // Legend (top-end)
        RadarLegend(modifier = Modifier.align(Alignment.TopEnd))

        // Status / controls (bottom)
        when (timelineState) {
            is LoadingState.Success ->
                RadarControls(
                    modifier = Modifier.align(Alignment.BottomCenter),
                    playing = playing,
                    frameIndex = frameIndex,
                    frameCount = frames.size,
                    frameLabel = frameLabel,
                    onTogglePlay = { playing = !playing },
                    onScrub = {
                        playing = false
                        frameIndex = it
                    },
                )

            is LoadingState.Error ->
                OverlayChip(modifier = Modifier.align(Alignment.BottomCenter)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.radar_unavailable),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        TextButton(onClick = onRetry) {
                            Text(stringResource(R.string.action_retry))
                        }
                    }
                }

            else ->
                OverlayChip(modifier = Modifier.align(Alignment.BottomCenter)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Text(
                            text = stringResource(R.string.radar_loading),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
        }
    }
}

@Composable
private fun RadarCanvas(
    tileInfo: RadarTile,
    baseMap: ImageBitmap?,
    overlay: ImageBitmap?,
) {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .clip(MaterialTheme.shapes.medium),
    ) {
        val bitmapTotalPx = (3 * RADAR_TILE_PX).toFloat()
        val tileDisplayPx = maxOf(size.width, size.height) / 2f
        val scale = tileDisplayPx / RADAR_TILE_PX

        val locBmpX = (1 + tileInfo.subFracX.toFloat()) * RADAR_TILE_PX
        val locBmpY = (1 + tileInfo.subFracY.toFloat()) * RADAR_TILE_PX

        val offsetX = size.width / 2f - locBmpX * scale
        val offsetY = size.height / 2f - locBmpY * scale

        val dst = IntOffset(offsetX.toInt(), offsetY.toInt())
        val dstSize = IntSize((bitmapTotalPx * scale).toInt(), (bitmapTotalPx * scale).toInt())

        baseMap?.let { drawImage(it, dstOffset = dst, dstSize = dstSize) }
        overlay?.let { drawImage(it, dstOffset = dst, dstSize = dstSize, alpha = 0.8f) }
    }
}

@Composable
private fun OverlayChip(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class) // the Slider overload with a custom thumb
@Composable
private fun RadarControls(
    playing: Boolean,
    frameIndex: Int,
    frameCount: Int,
    frameLabel: String?,
    onTogglePlay: () -> Unit,
    onScrub: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val sliderDescription = stringResource(R.string.radar_frame)
        // A full 48dp layout: the default 40dp one relies on overflow for its touch area, and the
        // slider beside it would take part of that.
        IconButton(onClick = onTogglePlay, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(
                    if (playing) R.string.radar_pause else R.string.radar_play,
                ),
            )
        }
        // The slider is as tall as its thumb; the default 44dp one leaves it short of the 48dp
        // minimum touch target.
        val interactionSource = remember { MutableInteractionSource() }
        Slider(
            value = frameIndex.toFloat(),
            onValueChange = { onScrub(it.roundToInt().coerceIn(0, (frameCount - 1).coerceAtLeast(0))) },
            valueRange = 0f..(frameCount - 1).coerceAtLeast(1).toFloat(),
            interactionSource = interactionSource,
            thumb = {
                SliderDefaults.Thumb(interactionSource = interactionSource, thumbSize = DpSize(4.dp, 48.dp))
            },
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp)
                .semantics {
                    contentDescription = sliderDescription
                    frameLabel?.let { stateDescription = it }
                },
        )
    }
}

@Composable
private fun RadarLegend(modifier: Modifier = Modifier) {
    // At least 96dp wide, wider when large text makes the labels need it (they'd wrap mid-word).
    Column(
        modifier = modifier
            .padding(8.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f))
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 96.dp)
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.horizontalGradient(RadarLegendColors)),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(stringResource(R.string.radar_legend_light), style = MaterialTheme.typography.labelSmall, maxLines = 1)
            Spacer(modifier = Modifier.weight(1f))
            Text(stringResource(R.string.radar_legend_heavy), style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

/**
 * Labels a radar frame with its wall-clock time in the selected city's timezone (via
 * [utcOffsetSeconds]), tagging the current frame as "Now" and nowcast frames as "Forecast".
 */
private fun frameTimeLabel(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
    isNow: Boolean,
    isForecast: Boolean,
): String {
    val time = Instant
        .ofEpochSecond(epochSeconds)
        .atZone(ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        .format(DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault()))
    return when {
        isNow -> "Now · $time"
        isForecast -> "Forecast · $time"
        else -> time
    }
}
