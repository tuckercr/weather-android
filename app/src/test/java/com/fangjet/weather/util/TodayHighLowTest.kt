package com.fangjet.weather.util

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayHighLowTest {
    // OpenWeather's current min and max are the spread across nearby stations right now.
    private val weather =
        CurrentWeather(
            cityName = "San Francisco",
            conditionMain = "Clouds",
            description = "scattered clouds",
            iconCode = "03d",
            temperature = 66.0,
            feelsLike = 65.0,
            tempMin = 64.0,
            tempMax = 70.0,
            humidityPercent = 60,
            pressureHpa = 1015,
            windSpeed = 10.0,
            windDeg = 270,
            visibilityMeters = 10000,
            cloudPercent = 40,
            units = WeatherUnits.IMPERIAL,
        )

    private fun today(
        high: Double,
        low: Double,
    ) = DayForecast("Fri, Oct 30", "Clouds", "scattered clouds", "03d", high, high, low, high, WeatherUnits.IMPERIAL)

    @Test
    fun `uses today's forecast high and low`() {
        assertEquals(68.0 to 52.0, todayHighLow(weather, today(high = 68.0, low = 52.0)))
    }

    @Test
    fun `widens the range to take in the temperature now`() {
        assertEquals(66.0 to 55.0, todayHighLow(weather, today(high = 65.0, low = 55.0)))
        assertEquals(74.0 to 66.0, todayHighLow(weather, today(high = 74.0, low = 67.0)))
    }

    @Test
    fun `falls back to the current reading's range without today's forecast`() {
        assertEquals(70.0 to 64.0, todayHighLow(weather, null))
    }
}
