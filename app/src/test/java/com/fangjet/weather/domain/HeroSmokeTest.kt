package com.fangjet.weather.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** When the hero is drawn smoky. Same cases as iOS's HeroSmokeTests. */
class HeroSmokeTest {
    private fun walk(vararg factors: WalkFactor) = WalkCheck(factors.toList())

    private fun air(
        band: Int,
        pollutant: AirQuality.Pollutant?,
        level: WalkLevel,
    ) = WalkFactor(WalkReason.Air(band, pollutant), level)

    @Test
    fun hazeWhenSmokeMakesItPottyBreaksOnly() {
        assertEquals(HeroSmoke.HAZE, heroSmoke(walk(air(3, AirQuality.Pollutant.FINE_PARTICLES, WalkLevel.POTTY_ONLY)), "Clear"))
        assertEquals(HeroSmoke.HAZE, heroSmoke(walk(air(4, AirQuality.Pollutant.FINE_PARTICLES, WalkLevel.POTTY_ONLY)), "Clouds"))
    }

    @Test
    fun ashAtTheWorstBand() {
        assertEquals(HeroSmoke.ASH, heroSmoke(walk(air(5, AirQuality.Pollutant.FINE_PARTICLES, WalkLevel.STAY_IN)), "Clear"))
    }

    @Test
    fun notForAirThatsOnlyACaveatOrForOtherPollutants() {
        assertNull(heroSmoke(walk(air(2, AirQuality.Pollutant.FINE_PARTICLES, WalkLevel.FINE)), "Clear"))
        assertNull(heroSmoke(walk(air(3, AirQuality.Pollutant.COARSE_PARTICLES, WalkLevel.POTTY_ONLY)), "Clear"))
        assertNull(heroSmoke(walk(air(5, AirQuality.Pollutant.OZONE, WalkLevel.STAY_IN)), "Clear"))
        assertNull(heroSmoke(walk(), "Clear"))
        assertNull(heroSmoke(null, "Haze"))
    }

    @Test
    fun findsTheSmokeBehindAWorseReason() {
        val stormy = walk(
            WalkFactor(WalkReason.Storms(likely = true), WalkLevel.STAY_IN),
            air(3, AirQuality.Pollutant.FINE_PARTICLES, WalkLevel.POTTY_ONLY),
        )
        assertEquals(HeroSmoke.HAZE, heroSmoke(stormy, "Thunderstorm"))
    }

    @Test
    fun openWeathersSmokeAndAshConditions() {
        assertEquals(HeroSmoke.HAZE, heroSmoke(null, "Smoke"))
        assertEquals(HeroSmoke.ASH, heroSmoke(null, "Ash"))
        assertEquals(HeroSmoke.ASH, heroSmoke(walk(air(5, AirQuality.Pollutant.FINE_PARTICLES, WalkLevel.STAY_IN)), "Smoke"))
    }
}
