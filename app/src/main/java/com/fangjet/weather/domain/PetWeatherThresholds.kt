package com.fangjet.weather.domain

/**
 * The tunable numbers behind the pet moods and the walk advice, from Remote Config (see
 * `RemoteConfigRepository.petWeatherThresholds`). Temperatures are Celsius whatever units the
 * user has chosen; the mood and walk functions convert readings to Celsius before comparing.
 *
 * Always built through [validated], so every instance is in range and every pair is ordered.
 */
data class PetWeatherThresholds(
    /** Pavement at or above this burns paws: the walk rating becomes PAWS_HOT. */
    val hotPavementC: Double = 52.0,
    /** An hour is walkable only below this rain chance, in percent. */
    val maxRainChancePercent: Int = 30,
    /** An hour is walkable only between [comfortMinC] and [comfortMaxC], inclusive. */
    val comfortMinC: Double = 3.0,
    val comfortMaxC: Double = 27.0,
    /** Walks are only suggested between these local hours, inclusive (7 = 7 AM, 20 = 8 PM). */
    val walkHoursStart: Int = 7,
    val walkHoursEnd: Int = 20,
    /** At or above this the pets are HOT (and the walk rating PAWS_HOT). */
    val petHotC: Double = 30.0,
    /** At or below this the pets are COLD (and the walk rating TOO_COLD). */
    val petColdC: Double = 0.0,
    /** Pavement at or above this (and below [hotPavementC]) is getting hot: keep walks short. */
    val warmPavementC: Double = 45.0,
    /** Pavement below this may be icy or salted. */
    val icyPavementC: Double = 0.0,
    /** Pavement at or above this (about 135 F) is dangerously hot: worse than [hotPavementC]. */
    val dangerousPavementC: Double = 57.2,
    /**
     * Hours in this air quality band or worse aren't suggested for a walk: 3 is US "Unhealthy" and
     * Europe's "Poor" (`AirQuality.band`, 0 to 5).
     */
    val badAirBand: Int = 3,
    /** Wind at this Beaufort force or more (7, a near gale: about 31 mph or 50 km/h) isn't suggested for a walk. */
    val windyForce: Int = 7,
) {
    companion object {
        /** The compiled-in values; also the fallback for anything invalid. */
        val DEFAULT = PetWeatherThresholds()

        val HOT_PAVEMENT_RANGE = 40.0..70.0
        val WARM_PAVEMENT_RANGE = 30.0..65.0
        val ICY_PAVEMENT_RANGE = -20.0..10.0
        val DANGEROUS_PAVEMENT_RANGE = 45.0..80.0

        /** How far below [hotPavementC] a warm threshold at or above it is moved. */
        private const val WARM_PAVEMENT_GAP_C = 7.0

        /** How far above [hotPavementC] a dangerous threshold at or below it is moved. */
        private const val DANGEROUS_PAVEMENT_GAP_C = 5.0
        val MAX_RAIN_CHANCE_RANGE = 0..100
        val COMFORT_MIN_RANGE = -20.0..20.0
        val COMFORT_MAX_RANGE = 15.0..40.0
        val WALK_HOURS_RANGE = 0..23
        val PET_HOT_RANGE = 20.0..45.0
        val PET_COLD_RANGE = -20.0..10.0

        /** 0 would rule out every hour, even in good air. */
        val BAD_AIR_BAND_RANGE = 1..5

        /**
         * Builds thresholds from raw (possibly missing or bad) values. A null or out-of-range
         * value falls back to its default; then any pair that is not strictly ordered
         * (comfort min < max, walk start < end, pet cold < hot) falls back to the default pair.
         * A warm-pavement threshold at or above the hot one would never show, so it moves to
         * [WARM_PAVEMENT_GAP_C] below it instead, and a dangerous one at or below it moves to
         * [DANGEROUS_PAVEMENT_GAP_C] above it; the hot threshold drives the walk advice and is
         * left alone.
         */
        fun validated(
            hotPavementC: Double? = null,
            warmPavementC: Double? = null,
            icyPavementC: Double? = null,
            dangerousPavementC: Double? = null,
            maxRainChancePercent: Int? = null,
            comfortMinC: Double? = null,
            comfortMaxC: Double? = null,
            walkHoursStart: Int? = null,
            walkHoursEnd: Int? = null,
            petHotC: Double? = null,
            petColdC: Double? = null,
            badAirBand: Int? = null,
        ): PetWeatherThresholds {
            val d = DEFAULT
            val comfortMin = comfortMinC.inRangeOr(COMFORT_MIN_RANGE, d.comfortMinC)
            val comfortMax = comfortMaxC.inRangeOr(COMFORT_MAX_RANGE, d.comfortMaxC)
            val hoursStart = walkHoursStart.inRangeOr(WALK_HOURS_RANGE, d.walkHoursStart)
            val hoursEnd = walkHoursEnd.inRangeOr(WALK_HOURS_RANGE, d.walkHoursEnd)
            val hot = petHotC.inRangeOr(PET_HOT_RANGE, d.petHotC)
            val cold = petColdC.inRangeOr(PET_COLD_RANGE, d.petColdC)
            val comfortOrdered = comfortMin < comfortMax
            val hoursOrdered = hoursStart < hoursEnd
            val petOrdered = cold < hot
            val hotPavement = hotPavementC.inRangeOr(HOT_PAVEMENT_RANGE, d.hotPavementC)
            val warmPavement = warmPavementC.inRangeOr(WARM_PAVEMENT_RANGE, d.warmPavementC)
            val dangerousPavement = dangerousPavementC.inRangeOr(DANGEROUS_PAVEMENT_RANGE, d.dangerousPavementC)
            return PetWeatherThresholds(
                hotPavementC = hotPavement,
                warmPavementC = if (warmPavement < hotPavement) warmPavement else hotPavement - WARM_PAVEMENT_GAP_C,
                icyPavementC = icyPavementC.inRangeOr(ICY_PAVEMENT_RANGE, d.icyPavementC),
                dangerousPavementC =
                    if (dangerousPavement > hotPavement) dangerousPavement else hotPavement + DANGEROUS_PAVEMENT_GAP_C,
                maxRainChancePercent = maxRainChancePercent.inRangeOr(MAX_RAIN_CHANCE_RANGE, d.maxRainChancePercent),
                comfortMinC = if (comfortOrdered) comfortMin else d.comfortMinC,
                comfortMaxC = if (comfortOrdered) comfortMax else d.comfortMaxC,
                walkHoursStart = if (hoursOrdered) hoursStart else d.walkHoursStart,
                walkHoursEnd = if (hoursOrdered) hoursEnd else d.walkHoursEnd,
                petHotC = if (petOrdered) hot else d.petHotC,
                petColdC = if (petOrdered) cold else d.petColdC,
                badAirBand = badAirBand.inRangeOr(BAD_AIR_BAND_RANGE, d.badAirBand),
            )
        }

        private fun Double?.inRangeOr(
            range: ClosedFloatingPointRange<Double>,
            default: Double,
        ): Double = this?.takeIf { it.isFinite() && it in range } ?: default

        private fun Int?.inRangeOr(
            range: IntRange,
            default: Int,
        ): Int = this?.takeIf { it in range } ?: default
    }
}
