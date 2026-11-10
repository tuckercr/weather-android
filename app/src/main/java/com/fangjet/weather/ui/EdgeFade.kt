package com.fangjet.weather.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.ScrollableState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/** How wide each faded edge of a sideways-scrolling row is. */
val EDGE_FADE_WIDTH = 24.dp

private const val EDGE_FADE_MILLIS = 200

/**
 * How far each edge of a sideways-scrolling list is faded (0 to 1), animated: the end while it can
 * scroll forward, the start once it has scrolled away from the start. At either end, or when
 * everything fits, that side is 0. Same rule as iOS's hourly strip.
 */
class ScrollEdgeFades(
    val start: () -> Float,
    val end: () -> Float,
)

@Composable
fun rememberScrollEdgeFades(state: ScrollableState): ScrollEdgeFades {
    val start = animateFloatAsState(
        targetValue = if (state.canScrollBackward) 1f else 0f,
        animationSpec = tween(EDGE_FADE_MILLIS),
        label = "edgeFadeStart",
    )
    val end = animateFloatAsState(
        targetValue = if (state.canScrollForward) 1f else 0f,
        animationSpec = tween(EDGE_FADE_MILLIS),
        label = "edgeFadeEnd",
    )
    return remember(start, end) { ScrollEdgeFades(start = { start.value }, end = { end.value }) }
}

/**
 * Fades the content out over [width] at each edge by [fades]. A mask on the content rather than
 * a coloured overlay, so it works on any card colour, seasonal theme, light or dark; it's only
 * drawn, so TalkBack reads the same. The fades are read while drawing, so scrolling and the
 * animation redraw without recomposing.
 */
fun Modifier.scrollEdgeFade(
    fades: ScrollEdgeFades,
    width: Dp = EDGE_FADE_WIDTH,
): Modifier =
    this
        // Offscreen, so the mask below cuts only this content and not the card behind it.
        .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
        .drawWithContent {
            drawContent()
            val fadePx = width.toPx().coerceAtMost(size.width / 2)
            if (fadePx <= 0f) return@drawWithContent
            val start = fades.start()
            val end = fades.end()
            val rtl = layoutDirection == LayoutDirection.Rtl
            val left = if (rtl) end else start
            val right = if (rtl) start else end
            if (left > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 1f - left),
                        1f to Color.Black,
                        startX = 0f,
                        endX = fadePx,
                    ),
                    size = Size(fadePx, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
            if (right > 0f) {
                drawRect(
                    brush = Brush.horizontalGradient(
                        0f to Color.Black,
                        1f to Color.Black.copy(alpha = 1f - right),
                        startX = size.width - fadePx,
                        endX = size.width,
                    ),
                    topLeft = Offset(size.width - fadePx, 0f),
                    size = Size(fadePx, size.height),
                    blendMode = BlendMode.DstIn,
                )
            }
        }
