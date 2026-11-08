package com.fangjet.weather.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class PetWeatherThresholdsTest {

    private val default = PetWeatherThresholds.DEFAULT

    @Test
    fun `defaults match the previous hard-coded values`() {
        assertEquals(
            PetWeatherThresholds(
                hotPavementC = 46.0,
                maxRainChancePercent = 30,
                comfortMinC = 3.0,
                comfortMaxC = 27.0,
                walkHoursStart = 7,
                walkHoursEnd = 20,
                petHotC = 30.0,
                petColdC = 0.0,
            ),
            default,
        )
    }

    @Test
    fun `missing values fall back to the defaults`() {
        assertEquals(default, PetWeatherThresholds.validated())
    }

    @Test
    fun `in-range values are used, including the range ends`() {
        val t = PetWeatherThresholds.validated(
            hotPavementC = 40.0,
            maxRainChancePercent = 100,
            comfortMinC = -20.0,
            comfortMaxC = 40.0,
            walkHoursStart = 0,
            walkHoursEnd = 23,
            petHotC = 45.0,
            petColdC = -20.0,
        )

        // A hot-pavement limit of 40 moves the default warm limit (40) to 7 below it.
        assertEquals(PetWeatherThresholds(40.0, 100, -20.0, 40.0, 0, 23, 45.0, -20.0, warmPavementC = 33.0), t)
    }

    @Test
    fun `each out-of-range value falls back to its own default`() {
        assertEquals(default.hotPavementC, PetWeatherThresholds.validated(hotPavementC = 39.9).hotPavementC, 0.0)
        assertEquals(default.hotPavementC, PetWeatherThresholds.validated(hotPavementC = 70.1).hotPavementC, 0.0)
        assertEquals(default.maxRainChancePercent, PetWeatherThresholds.validated(maxRainChancePercent = -1).maxRainChancePercent)
        assertEquals(default.maxRainChancePercent, PetWeatherThresholds.validated(maxRainChancePercent = 101).maxRainChancePercent)
        assertEquals(default.comfortMinC, PetWeatherThresholds.validated(comfortMinC = -21.0).comfortMinC, 0.0)
        assertEquals(default.comfortMaxC, PetWeatherThresholds.validated(comfortMaxC = 41.0).comfortMaxC, 0.0)
        assertEquals(default.walkHoursStart, PetWeatherThresholds.validated(walkHoursStart = -1).walkHoursStart)
        assertEquals(default.walkHoursEnd, PetWeatherThresholds.validated(walkHoursEnd = 24).walkHoursEnd)
        assertEquals(default.petHotC, PetWeatherThresholds.validated(petHotC = 19.0).petHotC, 0.0)
        assertEquals(default.petColdC, PetWeatherThresholds.validated(petColdC = 11.0).petColdC, 0.0)
    }

    @Test
    fun `non-finite values fall back to the defaults`() {
        val t = PetWeatherThresholds.validated(
            hotPavementC = Double.NaN,
            comfortMinC = Double.NEGATIVE_INFINITY,
            petHotC = Double.POSITIVE_INFINITY,
        )

        assertEquals(default, t)
    }

    @Test
    fun `an out-of-range value does not disturb the valid ones`() {
        val t = PetWeatherThresholds.validated(hotPavementC = 99.0, maxRainChancePercent = 50)

        assertEquals(default.copy(maxRainChancePercent = 50), t)
    }

    @Test
    fun `inverted comfort range reverts both ends to the defaults`() {
        val t = PetWeatherThresholds.validated(comfortMinC = 20.0, comfortMaxC = 15.0)

        assertEquals(default.comfortMinC, t.comfortMinC, 0.0)
        assertEquals(default.comfortMaxC, t.comfortMaxC, 0.0)
    }

    @Test
    fun `equal comfort ends also revert, since the pair must be strictly ordered`() {
        val t = PetWeatherThresholds.validated(comfortMinC = 18.0, comfortMaxC = 18.0)

        assertEquals(default, t)
    }

    @Test
    fun `inverted walk hours revert both to the defaults`() {
        val t = PetWeatherThresholds.validated(walkHoursStart = 21, walkHoursEnd = 6)

        assertEquals(default.walkHoursStart, t.walkHoursStart)
        assertEquals(default.walkHoursEnd, t.walkHoursEnd)
    }

    @Test
    fun `pet thresholds keep cold below hot`() {
        // The valid ranges (cold -20..10, hot 20..45) cannot overlap, so an in-range pair is always
        // ordered: the tightest one is kept as is, and an out-of-range "inversion" is caught by the
        // range check first (cold 25 -> 0) before the pair check runs.
        assertEquals(
            PetWeatherThresholds(petHotC = 20.0, petColdC = 10.0),
            PetWeatherThresholds.validated(petHotC = 20.0, petColdC = 10.0),
        )
        assertEquals(
            PetWeatherThresholds(petHotC = 20.0, petColdC = 0.0),
            PetWeatherThresholds.validated(petHotC = 20.0, petColdC = 25.0),
        )
    }

    @Test
    fun `one valid side of a pair is kept when it still orders with the other side's default`() {
        // walk_comfort_min_c = 20 is in range and below the default max of 27.
        val t = PetWeatherThresholds.validated(comfortMinC = 20.0)

        assertEquals(20.0, t.comfortMinC, 0.0)
        assertEquals(default.comfortMaxC, t.comfortMaxC, 0.0)
    }

    @Test
    fun `a valid side that conflicts with the other side's default reverts the pair`() {
        // walk_hours_start = 21 is in range, but not before the default end hour of 20.
        val t = PetWeatherThresholds.validated(walkHoursStart = 21)

        assertEquals(default.walkHoursStart, t.walkHoursStart)
        assertEquals(default.walkHoursEnd, t.walkHoursEnd)
    }

    @Test
    fun `pavement tile thresholds default to 40 and 0 and use in-range values`() {
        assertEquals(40.0, default.warmPavementC, 0.0)
        assertEquals(0.0, default.icyPavementC, 0.0)

        val t = PetWeatherThresholds.validated(warmPavementC = 40.0, icyPavementC = -5.0)

        assertEquals(40.0, t.warmPavementC, 0.0)
        assertEquals(-5.0, t.icyPavementC, 0.0)
        assertEquals(default, PetWeatherThresholds.validated(warmPavementC = 80.0, icyPavementC = 20.0))
    }

    @Test
    fun `a warm pavement threshold at or above the hot one moves below it, leaving the hot one alone`() {
        // walk_hot_pavement_c = 40 is valid; the default warm threshold of 40 would never show.
        val t = PetWeatherThresholds.validated(hotPavementC = 40.0)

        assertEquals(40.0, t.hotPavementC, 0.0)
        assertEquals(33.0, t.warmPavementC, 0.0)
    }

    @Test
    fun `dangerous pavement defaults to 135 F and uses in-range values`() {
        assertEquals(57.2, default.dangerousPavementC, 0.0)
        assertEquals(60.0, PetWeatherThresholds.validated(dangerousPavementC = 60.0).dangerousPavementC, 0.0)
        assertEquals(57.2, PetWeatherThresholds.validated(dangerousPavementC = 90.0).dangerousPavementC, 0.0)
    }

    @Test
    fun `a dangerous pavement threshold at or below the hot one moves above it, leaving the hot one alone`() {
        // walk_hot_pavement_c = 60 is valid; the default dangerous threshold of 57.2 would never show.
        val t = PetWeatherThresholds.validated(hotPavementC = 60.0)

        assertEquals(60.0, t.hotPavementC, 0.0)
        assertEquals(65.0, t.dangerousPavementC, 0.0)
    }
}
