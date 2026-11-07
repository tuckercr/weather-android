package com.fangjet.weather.domain

import kotlinx.serialization.Serializable

/**
 * An hour's air quality: an index on the scale the city uses (Europe's, or the US one everywhere
 * else), and the pollutant behind it. Same rules as iOS's `AirQuality`. Serializable, as it's
 * cached with the forecast.
 */
@Serializable
data class AirQuality(
    val index: Int,
    val scale: Scale,
    // The pollutant with the highest sub-index; null when none were reported.
    val mainPollutant: Pollutant?,
) {
    enum class Scale(
        // Where each of the six bands starts, and where the last one is drawn to end.
        val bandEdges: List<Double>,
    ) {
        US(listOf(0.0, 50.0, 100.0, 150.0, 200.0, 300.0, 500.0)),
        EUROPEAN(listOf(0.0, 20.0, 40.0, 60.0, 80.0, 100.0, 150.0)),
        ;

        companion object {
            /** Europe's index for cities on a European clock, the US one everywhere else. */
            fun forTimeZone(identifier: String?): Scale = if (identifier?.startsWith("Europe/") == true) EUROPEAN else US
        }
    }

    enum class Pollutant { FINE_PARTICLES, COARSE_PARTICLES, OZONE, NITROGEN_DIOXIDE, SULPHUR_DIOXIDE, CARBON_MONOXIDE }

    /** 0 (good) to 5 (the worst band). */
    val band: Int
        get() = (1 until scale.bandEdges.size - 1).lastOrNull { index > scale.bandEdges[it] } ?: 0

    /** Where the index sits along the six equal bands of the tile's bar, 0 to 1. */
    val barPosition: Double
        get() {
            val edges = scale.bandEdges
            val within = (index - edges[band]) / (edges[band + 1] - edges[band])
            return ((band + within) / BANDS).coerceIn(0.0, 1.0)
        }

    companion object {
        const val BANDS = 6

        /**
         * Fine particles are under this share of PM10 in dust (Riyadh's dust storm on 11-07 was
         * 0.16), against 0.5 to 0.7 in city air and 0.8 or more in smoke.
         */
        const val DUSTY_FINE_SHARE = 0.35

        /** Below this much PM10 (µg/m³) the mix isn't worth naming as dust. */
        const val DUSTY_PM10 = 50.0

        /**
         * Picks the pollutant with the highest sub-index on [scale]. Null without an index. [pm25]
         * and [pm10] are the particle masses (µg/m³), which tell dust from smoke.
         */
        fun from(
            scale: Scale,
            pm25: Double? = null,
            pm10: Double? = null,
            subIndices: (Scale) -> Pair<Int?, List<Pair<Pollutant, Int?>>>,
        ): AirQuality? {
            val (index, pollutants) = subIndices(scale)
            if (index == null) return null
            // The first listed wins a tie. AirQualitySource lists coarse particles before fine ones:
            // when PM10 is as bad as PM2.5 the air is dusty, whereas smoke shows up far more in PM2.5.
            var main: Pair<Pollutant, Int>? = null
            for ((pollutant, value) in pollutants) {
                if (value != null && value > (main?.second ?: Int.MIN_VALUE)) main = pollutant to value
            }
            val pollutant = main?.first?.let {
                if (it == Pollutant.FINE_PARTICLES &&
                    isDusty(pm25, pm10)
                ) {
                    Pollutant.COARSE_PARTICLES
                } else {
                    it
                }
            }
            return AirQuality(index, scale, pollutant)
        }

        /**
         * Particles that are mostly coarse, by mass. A dust storm can push PM2.5's index off the top
         * of the scale while PM10's has none at all (Open-Meteo gives no US PM10 index above about
         * 600 µg/m³), so the indices alone would call it smoke. Same as iOS's `AirQuality.isDusty`.
         */
        fun isDusty(
            pm25: Double?,
            pm10: Double?,
        ): Boolean {
            if (pm25 == null || pm10 == null || pm10 < DUSTY_PM10) return false
            return pm25 < DUSTY_FINE_SHARE * pm10
        }
    }
}
