package com.fangjet.weather.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.createBitmap
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.fangjet.weather.domain.RadarFrame
import com.fangjet.weather.domain.RadarTimeline
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import javax.inject.Inject
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.tan
import android.graphics.Canvas as AndroidCanvas

/**
 * RainViewer's public radar tiles are only served up to zoom 7 ([RadarTimeline.MAX_ZOOM]); higher
 * zooms return a "Zoom Level Not Supported" placeholder. Regional zoom suits precipitation radar.
 */
const val RADAR_ZOOM = RadarTimeline.MAX_ZOOM
const val RADAR_TILE_PX = 256

/**
 * The map tile a location falls in at [RADAR_ZOOM], and where in it ([subFracX], [subFracY], 0 to
 * 1): the radar card centres on that point within a 3 by 3 block of tiles. Same as iOS's
 * `RadarTile`.
 */
data class RadarTile(
    val tileX: Int,
    val tileY: Int,
    val subFracX: Double,
    val subFracY: Double,
) {
    companion object {
        fun from(
            lat: Double,
            lon: Double,
            zoom: Int = RADAR_ZOOM,
        ): RadarTile {
            val n = 2.0.pow(zoom.toDouble())
            val exactX = (lon + 180.0) / 360.0 * n
            val latRad = Math.toRadians(lat)
            val exactY = (1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / Math.PI) / 2.0 * n
            val tileX = exactX.toInt()
            val tileY = exactY.toInt()
            return RadarTile(tileX = tileX, tileY = tileY, subFracX = exactX - tileX, subFracY = exactY - tileY)
        }
    }
}

/**
 * Whether RainViewer has radar where the card is centred. Its coverage layer is transparent where
 * there is radar and opaque black where there isn't; there its radar tiles are empty, which would
 * read as "no rain". Same as iOS's `RadarCoverage`.
 */
object RadarCoverage {
    /**
     * Covered unless the coverage pixel at the city is at all opaque. Null (no coverage image, or
     * no pixel there) counts as covered, so a failed coverage load leaves the card as it was.
     */
    fun isCovered(alphaAt: Int?): Boolean = alphaAt == null || alphaAt <= 0

    /** The city's pixel in the stitched 3 by 3 image: the point the card centres on. */
    fun cityPixel(tile: RadarTile): Pair<Int, Int> {
        val last = 3 * RADAR_TILE_PX - 1
        val x = ((1 + tile.subFracX) * RADAR_TILE_PX).toInt().coerceIn(0, last)
        val y = ((1 + tile.subFracY) * RADAR_TILE_PX).toInt().coerceIn(0, last)
        return x to y
    }
}

/** The stitched coverage layer around a city, and whether the city itself has radar. */
data class RadarCoverageMap(
    val image: Bitmap,
    val cityCovered: Boolean,
)

/**
 * Loads the radar card's images: the OpenStreetMap tiles around a location, and each radar frame's
 * tiles over them, stitched into one bitmap per 3 by 3 block. Tiles go through Coil, which caches
 * them. The data layer for `RadarViewModel`; same as iOS's `RadarTileLoader`.
 */
class RadarTileLoader
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) {
        /** The OpenStreetMap tiles around [tile]. */
        suspend fun baseMap(tile: RadarTile): Bitmap? =
            stitchTilesWithRetry(tile) { x, y -> "https://tile.openstreetmap.org/$RADAR_ZOOM/$x/$y.png" }

        /** [frame]'s radar tiles around [tile]. */
        suspend fun overlay(
            frame: RadarFrame,
            timeline: RadarTimeline,
            tile: RadarTile,
        ): Bitmap? = stitchTilesWithRetry(tile) { x, y -> timeline.tileUrl(frame, RADAR_ZOOM, x, y) }

        /**
         * RainViewer's coverage layer around [tile], and whether the city's own pixel has radar;
         * null when none of it loads, so the card shows as it did without it.
         */
        suspend fun coverage(
            timeline: RadarTimeline,
            tile: RadarTile,
        ): RadarCoverageMap? {
            val image = stitchTilesWithRetry(tile) { x, y -> timeline.coverageTileUrl(RADAR_ZOOM, x, y) } ?: return null
            val (x, y) = RadarCoverage.cityPixel(tile)
            return RadarCoverageMap(image, RadarCoverage.isCovered(android.graphics.Color.alpha(image.getPixel(x, y))))
        }

        /**
         * Stitches the 3x3 tile grid, retrying with backoff while any tile fails so a network blip
         * doesn't leave permanent holes. Coil caches the tiles that did load, so a retry only
         * refetches the missing ones. After the last attempt a partial image beats none.
         */
        private suspend fun stitchTilesWithRetry(
            tile: RadarTile,
            urlBuilder: (x: Int, y: Int) -> String,
        ): Bitmap? {
            repeat(STITCH_ATTEMPTS - 1) { attempt ->
                stitchTiles(tile, requireAll = true, urlBuilder)?.let { return it }
                delay(STITCH_RETRY_BASE_MS shl attempt)
            }
            return stitchTiles(tile, requireAll = false, urlBuilder)
        }

        /** Returns null when every tile failed, or when [requireAll] and any tile failed. */
        private suspend fun stitchTiles(
            tile: RadarTile,
            requireAll: Boolean,
            urlBuilder: (x: Int, y: Int) -> String,
        ): Bitmap? =
            withContext(Dispatchers.IO) {
                val jobs = (-1..1).flatMap { row ->
                    (-1..1).map { col ->
                        async { Triple(col + 1, row + 1, loadTileBitmap(urlBuilder(tile.tileX + col, tile.tileY + row))) }
                    }
                }
                val results = jobs.awaitAll()
                if (results.all { it.third == null }) return@withContext null
                if (requireAll && results.any { it.third == null }) return@withContext null

                val stitched = createBitmap(3 * RADAR_TILE_PX, 3 * RADAR_TILE_PX)
                val canvas = AndroidCanvas(stitched)
                val paint = Paint()
                results.forEach { (col, row, bmp) ->
                    if (bmp != null) canvas.drawBitmap(bmp, (col * RADAR_TILE_PX).toFloat(), (row * RADAR_TILE_PX).toFloat(), paint)
                }
                stitched
            }

        private suspend fun loadTileBitmap(url: String): Bitmap? {
            val request = ImageRequest
                .Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            val result = Coil.imageLoader(context).execute(request)
            return ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
        }
    }

private const val STITCH_ATTEMPTS = 4
private const val STITCH_RETRY_BASE_MS = 1_000L
