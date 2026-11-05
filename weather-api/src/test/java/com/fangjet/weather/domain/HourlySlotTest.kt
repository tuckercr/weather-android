package com.fangjet.weather.domain

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HourlySlotTest {

    // As the app's own (WeatherNetworkModule), which reads the cache.
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
    }

    @Test
    fun `a forecast cached before rain amounts decodes with none`() {
        // A day as the cache held it before slots carried an amount.
        val old =
            """[{"dateLabel":"Mon, Jan 1","conditionMain":"Rain","description":"Light rain","iconCode":"10d",
            "temperature":12.0,"feelsLike":11.0,"tempMin":9.0,"tempMax":14.0,"units":"METRIC",
            "hourlySlots":[{"timeLabel":"4 PM","iconCode":"10d","description":"Light rain","temperature":12.0,
            "feelsLike":11.0,"windSpeed":3.0,"windDeg":200,"humidity":80,"pressure":1008,"units":"METRIC",
            "precipitationChance":70,"localHour":16,"epochSeconds":1704124800}],"precipitationChance":70}]"""

        val slot = json
            .decodeFromString(ListSerializer(DayForecast.serializer()), old)
            .single()
            .hourlySlots
            .single()

        assertNull(slot.precipitationMm)
        assertEquals(70, slot.precipitationChance)
    }

    @Test
    fun `the rain amount is cached with the forecast`() {
        val slot =
            HourlySlot(
                "4 PM",
                "10d",
                "Light rain",
                12.0,
                11.0,
                3.0,
                200,
                80,
                1008,
                WeatherUnits.METRIC,
                precipitationChance = 70,
                precipitationMm = 2.5,
            )

        assertEquals(slot, json.decodeFromString(HourlySlot.serializer(), json.encodeToString(HourlySlot.serializer(), slot)))
    }
}
