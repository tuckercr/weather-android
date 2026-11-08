package com.fangjet.weather.ui

import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.isCalmWind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalmWindTest {

    @Test
    fun `a speed that would show as 0 m per s is calm`() {
        assertTrue(isCalmWind(0.0, WeatherUnits.METRIC))
        assertTrue(isCalmWind(0.04, WeatherUnits.METRIC))
    }

    @Test
    fun `anything that shows as 0_1 m per s or more is not`() {
        assertFalse(isCalmWind(0.05, WeatherUnits.METRIC))
        assertFalse(isCalmWind(3.2, WeatherUnits.METRIC))
    }

    @Test
    fun `in whole mph, anything that rounds to 0 is calm`() {
        assertTrue(isCalmWind(0.4, WeatherUnits.IMPERIAL))
        assertFalse(isCalmWind(0.5, WeatherUnits.IMPERIAL))
    }
}
