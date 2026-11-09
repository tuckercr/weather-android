package com.fangjet.weather.domain

/** A single radar frame: a snapshot timestamp and the tile path that serves it. */
data class RadarFrame(
    val timeEpochSeconds: Long,
    val path: String,
    /** True for nowcast (future) frames, false for observed past frames. */
    val isForecast: Boolean,
)

/**
 * An ordered radar animation timeline from RainViewer: observed past frames followed by
 * short-range nowcast frames, all sharing a single tile [host].
 */
data class RadarTimeline(
    val host: String,
    val frames: List<RadarFrame>,
) {
    /** Builds the tile URL for [frame] at the given slippy-map tile coordinates. */
    fun tileUrl(
        frame: RadarFrame,
        z: Int,
        x: Int,
        y: Int,
    ): String = "$host${frame.path}/$TILE_SIZE/$z/$x/$y/$COLOR_SCHEME/${SMOOTH}_$SNOW.png"

    /**
     * RainViewer's coverage layer at the given tile: transparent where there is radar, opaque
     * black where there isn't (much of Africa, central Asia and the oceans, where the radar tiles
     * are empty and would read as "no rain"). Same as iOS's `RadarTimeline.coverageTileURL`.
     */
    fun coverageTileUrl(
        z: Int,
        x: Int,
        y: Int,
    ): String = "$host/v2/coverage/0/$TILE_SIZE/$z/$x/$y/0/0_0.png"

    companion object {
        /**
         * Highest slippy-map zoom RainViewer's public radar tiles are served at; higher zooms return
         * a "Zoom Level Not Supported" placeholder. Callers request tiles at this zoom.
         */
        const val MAX_ZOOM = 7

        private const val TILE_SIZE = 256

        // RainViewer color scheme 4 (Universal Blue -> heavy). smooth=1 (interpolated),
        // snow=1 (render snow distinctly).
        private const val COLOR_SCHEME = 4
        private const val SMOOTH = 1
        private const val SNOW = 1
    }
}
