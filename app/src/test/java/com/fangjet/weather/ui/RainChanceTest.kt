package com.fangjet.weather.ui

import com.fangjet.weather.util.displayedRainChance
import org.junit.Assert.assertEquals
import org.junit.Test

class RainChanceTest {

    @Test
    fun `under 10 percent is not shown`() {
        assertEquals(null, displayedRainChance(0))
        assertEquals(null, displayedRainChance(1))
        assertEquals(null, displayedRainChance(9))
    }

    @Test
    fun `from 10 percent it rounds to the nearest 10`() {
        assertEquals(10, displayedRainChance(10))
        assertEquals(10, displayedRainChance(14))
        assertEquals(40, displayedRainChance(37))
        assertEquals(50, displayedRainChance(45))
        assertEquals(100, displayedRainChance(96))
        assertEquals(100, displayedRainChance(100))
    }
}
