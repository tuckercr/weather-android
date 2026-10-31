package com.fangjet.weather.util

import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TodayDetailsTest {

    @Test
    fun `visibility bands`() {
        assertEquals(VisibilityBand.EXCELLENT, visibilityBand(10_000))
        assertEquals(VisibilityBand.GOOD, visibilityBand(9_999))
        assertEquals(VisibilityBand.GOOD, visibilityBand(5_000))
        assertEquals(VisibilityBand.MODERATE, visibilityBand(4_999))
        assertEquals(VisibilityBand.MODERATE, visibilityBand(1_000))
        assertEquals(VisibilityBand.POOR, visibilityBand(999))
    }

    @Test
    fun `visibility at the 10 km cap has no number, so it never reads 6_2 mi`() {
        assertNull(visibilityDistance(10_000, inMiles = true))
        assertNull(visibilityDistance(10_000, inMiles = false))
    }

    @Test
    fun `visibility is whole miles or kilometres from 1 km up`() {
        assertEquals(Distance(4.0, DistanceUnit.MILES, 0), visibilityDistance(6_500, inMiles = true))
        assertEquals(Distance(6.0, DistanceUnit.MILES, 0), visibilityDistance(9_999, inMiles = true))
        assertEquals(Distance(1.0, DistanceUnit.MILES, 0), visibilityDistance(1_000, inMiles = true))
        assertEquals(Distance(7.0, DistanceUnit.KILOMETERS, 0), visibilityDistance(6_500, inMiles = false))
    }

    @Test
    fun `under 1 km visibility is tenths of a mile or metres to the nearest 100`() {
        assertEquals(Distance(0.5, DistanceUnit.MILES, 1), visibilityDistance(800, inMiles = true))
        assertEquals(Distance(0.1, DistanceUnit.MILES, 1), visibilityDistance(20, inMiles = true))
        assertEquals(Distance(800.0, DistanceUnit.METERS, 0), visibilityDistance(780, inMiles = false))
        assertEquals(Distance(100.0, DistanceUnit.METERS, 0), visibilityDistance(20, inMiles = false))
    }

    @Test
    fun `obscured skies are named for what's in the air, the rest by cloud cover`() {
        assertEquals(SkyCondition.FOG, skyCondition("Fog", 100, "50d"))
        assertEquals(SkyCondition.MIST, skyCondition("Mist", 100, "50d"))
        assertEquals(SkyCondition.SMOKE, skyCondition("Smoke", 0, "50d"))
        assertEquals(SkyCondition.DUST, skyCondition("Sand", 0, "50d"))
        assertEquals(SkyCondition.CLEAR, skyCondition("Clear", 0, "01d"))
        assertEquals(SkyCondition.CLEAR, skyCondition("Clouds", 10, "02d"))
        assertEquals(SkyCondition.PARTLY_CLOUDY, skyCondition("Clouds", 50, "03d"))
        assertEquals(SkyCondition.MOSTLY_CLOUDY, skyCondition("Clouds", 84, "04d"))
        assertEquals(SkyCondition.OVERCAST, skyCondition("Rain", 100, "10d"))
    }

    @Test
    fun `without a cloud cover the icon decides`() {
        assertEquals(SkyCondition.CLEAR, skyCondition("Clear", null, "01n"))
        assertEquals(SkyCondition.PARTLY_CLOUDY, skyCondition("Clouds", null, "02d"))
        assertEquals(SkyCondition.MOSTLY_CLOUDY, skyCondition("Clouds", null, "03d"))
        assertEquals(SkyCondition.OVERCAST, skyCondition("Rain", null, "10d"))
    }

    @Test
    fun `pavement bands follow the walk thresholds`() {
        val t = PetWeatherThresholds.DEFAULT
        assertEquals(PavementBand.DANGEROUS, pavementBand(57.2, false, 20.0, t))
        assertEquals(PavementBand.TOO_HOT, pavementBand(57.1, false, 20.0, t))
        assertEquals(PavementBand.TOO_HOT, pavementBand(52.0, false, 20.0, t))
        assertEquals(PavementBand.WARM, pavementBand(51.9, false, 20.0, t))
        assertEquals(PavementBand.WARM, pavementBand(45.0, false, 20.0, t))
        assertEquals(PavementBand.SAFE, pavementBand(44.9, false, 20.0, t))
        assertEquals(PavementBand.SAFE, pavementBand(0.0, true, 0.1, t))
    }

    @Test
    fun `pavement is only safe for paws when the air is too`() {
        val t = PetWeatherThresholds.DEFAULT
        // A muggy night: the pavement has cooled, but it feels 31C, past the 30C pets' limit.
        assertEquals(PavementBand.PAVEMENT_OK, pavementBand(29.0, false, 31.0, t))
        // Just above freezing, but -4C in the wind.
        assertEquals(PavementBand.PAVEMENT_OK, pavementBand(2.0, false, -4.0, t))
        // Too-hot pavement still says so, whatever the air.
        assertEquals(PavementBand.TOO_HOT, pavementBand(53.0, false, 31.0, t))
    }

    @Test
    fun `below freezing the pavement is only icy after rain or snow`() {
        val t = PetWeatherThresholds.DEFAULT
        assertEquals(PavementBand.ICY, pavementBand(-0.1, true, -3.0, t))
        assertEquals(PavementBand.FREEZING, pavementBand(-0.1, false, -3.0, t))
    }

    @Test
    fun `dew point`() {
        assertEquals(6.0, dewPointC(8.3, 86)!!, 0.1)
        assertEquals(20.0, dewPointC(20.0, 100)!!, 0.01)
        assertNull(dewPointC(20.0, 0))
    }

    @Test
    fun `wind force uses the Beaufort scale in either unit`() {
        assertEquals(WindForce.CALM, windForce(0.2, WeatherUnits.METRIC))
        assertEquals(WindForce.LIGHT_AIR, windForce(1.0, WeatherUnits.IMPERIAL))
        assertEquals(WindForce.LIGHT_AIR, windForce(1.5, WeatherUnits.METRIC))
        assertEquals(WindForce.LIGHT_BREEZE, windForce(1.6, WeatherUnits.METRIC))
        assertEquals(WindForce.GENTLE_BREEZE, windForce(10.0, WeatherUnits.IMPERIAL))
        assertEquals(WindForce.GALE, windForce(20.0, WeatherUnits.METRIC))
        assertEquals(WindForce.HURRICANE, windForce(40.0, WeatherUnits.METRIC))
    }

    @Test
    fun `daylight before sunrise, during the day and after sunset`() {
        val sunrise = 1_000L
        val sunset = 1_000L + 10 * 3600

        assertEquals(Daylight.BeforeSunrise(600), daylight(sunrise - 600, sunrise, sunset))
        assertEquals(Daylight.Daytime(secondsLeft = 2 * 3600, fraction = 0.8f), daylight(sunset - 2 * 3600, sunrise, sunset))
        assertEquals(Daylight.Daytime(secondsLeft = 10 * 3600, fraction = 0f), daylight(sunrise, sunrise, sunset))
        assertEquals(Daylight.AfterSunset, daylight(sunset, sunrise, sunset))
    }

    @Test
    fun `numbers keep their units on the same line`() {
        assertEquals("Actual 56.3\u00A0°F", keepUnitsTogether("Actual 56.3 °F"))
        assertEquals("3.4\u00A0mph", keepUnitsTogether("3.4 mph"))
        assertEquals("5.0\u00A0m/s", keepUnitsTogether("5.0 m/s"))
        assertEquals("Clear · Good visibility, 4\u00A0mi", keepUnitsTogether("Clear · Good visibility, 4 mi"))
        assertEquals("800\u00A0m", keepUnitsTogether("800 m"))
        assertEquals("6:45\u00A0PM", keepUnitsTogether("6:45 PM"))
        assertEquals("7:53\u00A0AM", keepUnitsTogether("7:53\u202FAM"))
        // Words after numbers are left alone.
        assertEquals("2 h 15 min left", keepUnitsTogether("2 h 15 min left"))
    }

    private fun uvSlot(
        uv: Double,
        epoch: Long,
    ) = HourlySlot(
        timeLabel = "1 PM",
        iconCode = "01d",
        description = "clear",
        temperature = 25.0,
        feelsLike = 25.0,
        windSpeed = 2.0,
        windDeg = 0,
        humidity = 40,
        pressure = 1013,
        units = WeatherUnits.METRIC,
        uvIndex = uv,
        epochSeconds = epoch,
    )

    @Test
    fun `the UV tile says when today's peak is while it's ahead`() {
        assertEquals(UvLine.Peak(8, "1 PM"), uvLine(uvSlot(7.6, 13 * 3600L), 10 * 3600L, hasDog = true))
    }

    @Test
    fun `from a high peak on the UV tile minds pale noses with a dog`() {
        val peak = uvSlot(7.0, 13 * 3600L)
        assertEquals(UvLine.PaleNoses, uvLine(peak, 16 * 3600L, hasDog = true))
        assertEquals(UvLine.AtPeak, uvLine(peak, 13 * 3600L + 600, hasDog = false))
        assertEquals(UvLine.EasingOff, uvLine(peak, 16 * 3600L, hasDog = false))
        assertEquals(UvLine.EasingOff, uvLine(uvSlot(4.0, 13 * 3600L), 16 * 3600L, hasDog = true))
        assertNull(uvLine(null, 0, hasDog = true))
    }

    @Test
    fun `every tile shows by default except UV`() {
        val switches = TodayTileSwitches()
        assertTrue(switches.feelsLike && switches.wind && switches.humidity && switches.pawCheck && switches.daylight && switches.sky)
        assertFalse(switches.uv)
    }
}
