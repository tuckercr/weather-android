package com.fangjet.weather.data

import com.fangjet.weather.data.remote.dto.OpenMeteoCurrentResponse
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class OpenMeteoCurrentTest {
    private val json = Json { ignoreUnknownKeys = true }

    private fun response(body: String = OPEN_METEO_CURRENT_BODY) = json.decodeFromString(OpenMeteoCurrentResponse.serializer(), body)

    private fun withCurrent(
        replace: String,
        with: String,
    ) = response(OPEN_METEO_CURRENT_BODY.replace(replace, with))

    @Test
    fun `maps the reading`() {
        val weather = OpenMeteoCurrent.weather(response(), WeatherUnits.IMPERIAL, "Knoxville")!!

        assertEquals("Knoxville", weather.cityName)
        assertEquals(63.3, weather.temperature, 0.001)
        assertEquals(63.8, weather.feelsLike, 0.001)
        assertEquals("Clear", weather.conditionMain)
        assertEquals("Clear sky", weather.description)
        assertEquals("01d", weather.iconCode)
        assertEquals(62, weather.humidityPercent)
        assertEquals(1015, weather.pressureHpa)
        assertEquals(1.1, weather.windSpeed, 0.001)
        assertEquals(267, weather.windDeg)
        assertEquals(9.8, weather.windGust ?: 0.0, 0.001)
        assertEquals(29_300, weather.visibilityMeters)
        assertEquals(5, weather.cloudPercent)
        assertEquals(WeatherUnits.IMPERIAL, weather.units)
        assertEquals(65.6, weather.tempMax, 0.001)
        assertEquals(48.0, weather.tempMin, 0.001)
        assertEquals(1_793_966_580L, weather.sunriseEpoch)
        assertEquals(1_794_004_500L, weather.sunsetEpoch)
        assertEquals(-18_000, weather.utcOffsetSeconds)
        assertEquals(1_793_986_200L, weather.observedAtEpochSeconds)
    }

    @Test
    fun `night icon`() {
        val weather = OpenMeteoCurrent.weather(withCurrent("\"is_day\":1", "\"is_day\":0"), WeatherUnits.METRIC, "")
        assertEquals("01n", weather?.iconCode)
    }

    @Test
    fun `today's range holds the reading`() {
        val above = OpenMeteoCurrent.weather(withCurrent("[65.6]", "[60.0]"), WeatherUnits.IMPERIAL, "")
        assertEquals(63.3, above!!.tempMax, 0.001)
        val below = OpenMeteoCurrent.weather(withCurrent("[48.0]", "[64.0]"), WeatherUnits.IMPERIAL, "")
        assertEquals(63.3, below!!.tempMin, 0.001)
    }

    @Test
    fun `no reading without a time or temperature`() {
        assertNull(OpenMeteoCurrent.weather(withCurrent("\"temperature_2m\":63.3,", ""), WeatherUnits.METRIC, ""))
        assertNull(OpenMeteoCurrent.weather(withCurrent("\"time\":1793986200,", ""), WeatherUnits.METRIC, ""))
    }
}
