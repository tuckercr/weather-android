package com.fangjet.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarCoverageTest {

    @Test
    fun `a transparent coverage pixel has radar`() {
        assertTrue(RadarCoverage.isCovered(alphaAt = 0))
    }

    @Test
    fun `any opacity at the city means no radar there`() {
        assertFalse(RadarCoverage.isCovered(alphaAt = 255))
        assertFalse(RadarCoverage.isCovered(alphaAt = 1))
    }

    @Test
    fun `no coverage pixel counts as covered, so a failed load changes nothing`() {
        assertTrue(RadarCoverage.isCovered(alphaAt = null))
    }

    @Test
    fun `the city's pixel is where the card centres, in the middle tile of the stitched image`() {
        assertEquals(256 to 256, RadarCoverage.cityPixel(RadarTile(tileX = 10, tileY = 20, subFracX = 0.0, subFracY = 0.0)))
        assertEquals(384 to 448, RadarCoverage.cityPixel(RadarTile(tileX = 10, tileY = 20, subFracX = 0.5, subFracY = 0.75)))
        assertEquals(511 to 511, RadarCoverage.cityPixel(RadarTile(tileX = 10, tileY = 20, subFracX = 0.999, subFracY = 0.999)))
    }

    @Test
    fun `london falls inside its middle tile`() {
        val (x, y) = RadarCoverage.cityPixel(RadarTile.from(51.5074, -0.1278))
        assertTrue(x in 256..511)
        assertTrue(y in 256..511)
    }
}
