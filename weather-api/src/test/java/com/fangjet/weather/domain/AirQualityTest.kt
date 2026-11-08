package com.fangjet.weather.domain

import com.fangjet.weather.data.AirQualitySource
import com.fangjet.weather.data.remote.dto.AirQualityHourlyResponse
import com.fangjet.weather.data.withAirQuality
import com.fangjet.weather.domain.AirQuality.Pollutant
import com.fangjet.weather.domain.AirQuality.Scale
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class AirQualityTest {
    private fun us(index: Int) = AirQuality(index, Scale.US, null)

    private fun european(index: Int) = AirQuality(index, Scale.EUROPEAN, null)

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `US bands change above each breakpoint`() {
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5, 5),
            listOf(0, 50, 51, 100, 101, 150, 151, 200, 201, 300, 301, 500).map { us(it).band },
        )
    }

    @Test
    fun `European bands change every twenty`() {
        assertEquals(
            listOf(0, 0, 1, 1, 2, 2, 3, 3, 4, 4, 5),
            listOf(0, 20, 21, 40, 41, 60, 61, 80, 81, 100, 101).map { european(it).band },
        )
    }

    @Test
    fun `bar position splits the bar into six equal bands`() {
        assertEquals(0.0, us(0).barPosition, 0.001)
        assertEquals(1.0 / 12, us(25).barPosition, 0.001)
        assertEquals(2.0 / 6, us(100).barPosition, 0.001)
        assertEquals(1.5 / 6, european(30).barPosition, 0.001)
        assertEquals(1.0, us(900).barPosition, 0.001)
    }

    @Test
    fun `European cities use the European scale`() {
        assertEquals(Scale.EUROPEAN, Scale.forTimeZone("Europe/London"))
        assertEquals(Scale.US, Scale.forTimeZone("America/New_York"))
        assertEquals(Scale.US, Scale.forTimeZone(null))
    }

    @Test
    fun `parses each hour's index and names the biggest pollutant for the city's scale`() {
        val response = json.decodeFromString(
            AirQualityHourlyResponse.serializer(),
            """{"timezone":"Europe/London","hourly":{"time":[100,3700],"us_aqi":[67,40],"us_aqi_pm2_5":[67,40],
            "european_aqi":[50,90],"european_aqi_pm2_5":[45,90],"european_aqi_nitrogen_dioxide":[50,10]}}""",
        )

        assertEquals(
            mapOf(
                100L to AirQuality(50, Scale.EUROPEAN, Pollutant.NITROGEN_DIOXIDE),
                3700L to AirQuality(90, Scale.EUROPEAN, Pollutant.FINE_PARTICLES),
            ),
            AirQualitySource.toHourly(response),
        )
    }

    @Test
    fun `a tie goes to the first listed pollutant`() {
        val response = json.decodeFromString(
            AirQualityHourlyResponse.serializer(),
            """{"timezone":"America/New_York","hourly":{"time":[100],"us_aqi":[46],"us_aqi_pm2_5":[46],"us_aqi_ozone":[46]}}""",
        )

        assertEquals(Pollutant.FINE_PARTICLES, AirQualitySource.toHourly(response)[100L]?.mainPollutant)
    }

    @Test
    fun `a dust storm is dust, not smoke`() {
        // Riyadh on 11-07: PM2.5's index off the top of the scale and none for PM10, but six times
        // as much PM10 as PM2.5 by mass.
        val response = json.decodeFromString(
            AirQualityHourlyResponse.serializer(),
            """{"timezone":"Asia/Riyadh","hourly":{"time":[100,3700],"us_aqi":[504,500],"us_aqi_pm2_5":[504,500],""" +
                """"us_aqi_pm10":[null,500],"pm2_5":[517.8,null],"pm10":[3173.9,null]}}""",
        )

        val hourly = AirQualitySource.toHourly(response)
        assertEquals(Pollutant.COARSE_PARTICLES, hourly[100L]?.mainPollutant)
        // Both indices topped out: a tie goes to coarse particles.
        assertEquals(Pollutant.COARSE_PARTICLES, hourly[3700L]?.mainPollutant)
    }

    @Test
    fun `smoke and city air stay fine particles`() {
        assertEquals(false, AirQuality.isDusty(117.0, 127.0)) // Lahore's smoke
        assertEquals(false, AirQuality.isDusty(98.0, 204.0)) // New Delhi
        assertEquals(true, AirQuality.isDusty(128.0, 561.0)) // Kuwait City's dust
        assertEquals(false, AirQuality.isDusty(5.0, 30.0)) // too little to call
        assertEquals(false, AirQuality.isDusty(null, 300.0))
    }

    @Test
    fun `an hour without an index has no reading`() {
        val response = json.decodeFromString(
            AirQualityHourlyResponse.serializer(),
            """{"timezone":"America/New_York","hourly":{"time":[100,3700],"us_aqi":[null,30]}}""",
        )

        assertEquals(mapOf(3700L to AirQuality(30, Scale.US, null)), AirQualitySource.toHourly(response))
    }

    @Test
    fun `each slot gets its hour's air quality`() {
        val slot = HourlySlot("1 AM", "01n", "clear", 10.0, 10.0, 1.0, 0, 50, 1013, WeatherUnits.METRIC, epochSeconds = 100)
        val day =
            DayForecast(
                "Mon",
                "Clear",
                "clear",
                "01d",
                10.0,
                10.0,
                5.0,
                15.0,
                WeatherUnits.METRIC,
                hourlySlots = listOf(slot, slot.copy(epochSeconds = 3700)),
            )
        val air = AirQuality(172, Scale.US, Pollutant.OZONE)

        val slots = withAirQuality(listOf(day), mapOf(3700L to air)).single().hourlySlots.map { it.airQuality }

        assertEquals(listOf(null, air), slots)
    }

    @Test
    fun `air quality is cached with the forecast`() {
        val slot = HourlySlot(
            "1 AM",
            "01n",
            "clear",
            10.0,
            10.0,
            1.0,
            0,
            50,
            1013,
            WeatherUnits.METRIC,
            airQuality = AirQuality(120, Scale.EUROPEAN, Pollutant.OZONE),
        )

        assertEquals(slot, json.decodeFromString(HourlySlot.serializer(), json.encodeToString(HourlySlot.serializer(), slot)))
    }
}
