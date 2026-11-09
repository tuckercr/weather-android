package com.fangjet.weather.model

import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.Daylight
import com.fangjet.weather.util.MistyLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Which Today tiles show and what they say. Same cases as iOS's `TodayTilesStateTests`. */
class TodayTilesStateTest {
    private val noon = 1_704_067_200L + 12 * 3600

    @Test
    fun `the small tiles go in order and Paw Check needs a pavement value`() {
        val withPavement = tiles(pavementNow = 40.0, air = AirQuality(20, AirQuality.Scale.EUROPEAN, null))
        assertEquals(listOf("FeelsLike", "Wind", "Fluff", "PawCheck", "Air"), withPavement.small.map { it::class.simpleName })

        assertEquals(listOf("FeelsLike", "Wind", "Fluff"), tiles(pavementNow = null).small.map { it::class.simpleName })
    }

    @Test
    fun `UV has its own tile when switched on or when Daylight can't carry it`() {
        // Off by default: Daylight carries it.
        val carried = tiles(uvIndex = 6.0)
        assertFalse(carried.small.any { it is TodayTile.Uv })
        assertEquals(6.0, carried.daylight?.uvIndex)

        val ownTile = tiles(uvIndex = 6.0, switches = TodayTileSwitches(uv = true))
        assertTrue(ownTile.small.any { it is TodayTile.Uv })
        assertNull(ownTile.daylight?.uvIndex)

        // No sun times, so no Daylight tile: UV gets its own.
        val noDaylight = tiles(uvIndex = 6.0, sunTimes = false)
        assertNull(noDaylight.daylight)
        assertTrue(noDaylight.small.any { it is TodayTile.Uv })
    }

    @Test
    fun `Daylight says where the day is`() {
        val daylight = tiles().daylight?.daylight as Daylight.Daytime
        assertEquals(5 * 3600L, daylight.secondsLeft)
        assertEquals(0.5f, daylight.fraction, 0.01f)
    }

    @Test
    fun `bad air reads bad by the walk rules with a dog and by band without`() {
        // Unhealthy on the US index: potty breaks only for a dog, and band 3 without one.
        val unhealthy = AirQuality(160, AirQuality.Scale.US, AirQuality.Pollutant.FINE_PARTICLES)
        assertEquals(AirTone.BAD, airTile(tiles(air = unhealthy, hasDog = true))?.tone)
        assertEquals(AirTone.BAD, airTile(tiles(air = unhealthy, hasDog = false))?.tone)
        assertEquals(AirTone.GOOD, airTile(tiles(air = AirQuality(20, AirQuality.Scale.US, null)))?.tone)
    }

    @Test
    fun `Misty's View leaves out visibility for a forecast hour`() {
        assertEquals(10_000, tiles().sky?.visibilityMeters)
        assertNull(tiles(showingForecast = true).sky?.visibilityMeters)
    }

    @Test
    fun `Misty's View picks the night lines after sunset and the day lines before it`() {
        // Sunset is 5 PM: clear at 10 km.
        assertEquals(MistyLine.WHOLE_STREET, tiles(now = noon + 5 * 3600 - 60).sky?.mistyLine)
        assertEquals(MistyLine.STARS, tiles(now = noon + 5 * 3600).sky?.mistyLine)
        // And before sunrise.
        assertEquals(MistyLine.STARS, tiles(now = noon - 6 * 3600).sky?.mistyLine)
        // No visibility (a forecast hour standing in), no line.
        assertNull(tiles(now = noon + 6 * 3600, showingForecast = true).sky?.mistyLine)
    }

    private fun tiles(
        pavementNow: Double? = null,
        uvIndex: Double? = null,
        air: AirQuality? = null,
        hasDog: Boolean = true,
        sunTimes: Boolean = true,
        showingForecast: Boolean = false,
        switches: TodayTileSwitches = TodayTileSwitches(),
        now: Long = noon,
    ) = todayTilesState(
        weather = CurrentWeather(
            cityName = "London",
            conditionMain = "Clear",
            description = "clear sky",
            iconCode = "01d",
            temperature = 15.0,
            feelsLike = 14.0,
            tempMin = 10.0,
            tempMax = 18.0,
            humidityPercent = 50,
            pressureHpa = 1013,
            windSpeed = 3.0,
            windDeg = 180,
            visibilityMeters = 10_000,
            cloudPercent = 0,
            units = WeatherUnits.METRIC,
            sunriseEpoch = if (sunTimes) noon - 5 * 3600 else null,
            sunsetEpoch = if (sunTimes) noon + 5 * 3600 else null,
            utcOffsetSeconds = 0,
        ),
        showingForecast = showingForecast,
        pavementNow = pavementNow,
        groundWet = false,
        uvIndex = uvIndex,
        uvPeak = null,
        airQuality = air,
        hasDog = hasDog,
        nowEpochSeconds = now,
        thresholds = PetWeatherThresholds.DEFAULT,
        switches = switches,
    )

    private fun airTile(state: TodayTilesState) = state.small.filterIsInstance<TodayTile.Air>().firstOrNull()
}
