package com.fangjet.weather.util

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StaleWeatherTest {

    // 2026-01-01T12:00:00Z
    private val noon = 1_767_268_800L
    private val hour = 3_600L

    @Test
    fun `a reading up to three hours old still counts as now`() {
        val reading = weather(observedAt = noon)

        assertFalse(reading.isTooOldToShowAsNow(noon + 3 * hour))
        assertTrue(reading.isTooOldToShowAsNow(noon + 3 * hour + 1))
    }

    @Test
    fun `a reading of unknown age counts as now`() {
        assertFalse(weather(observedAt = null).isTooOldToShowAsNow(noon + 48 * hour))
    }

    @Test
    fun `a day reading cached before sunset shows night after it`() {
        val reading = weather(observedAt = noon, sunrise = noon - 5 * hour, sunset = noon + 5 * hour)

        assertEquals("01d", reading.iconCodeAt(noon + 4 * hour))
        assertEquals("01n", reading.iconCodeAt(noon + 7 * hour))
    }

    @Test
    fun `a night reading from the evening before shows day after sunrise`() {
        // Taken at 22:00 the day before, with that day's sunrise and sunset.
        val reading = weather(
            observedAt = noon - 14 * hour,
            iconCode = "04n",
            sunrise = noon - 29 * hour,
            sunset = noon - 19 * hour,
        )

        assertEquals("04n", reading.iconCodeAt(noon - 6 * hour))
        assertEquals("04d", reading.iconCodeAt(noon))
    }

    @Test
    fun `a recent reading keeps OpenWeather's icon`() {
        val reading = weather(observedAt = noon + 5 * hour - 10 * 60, sunrise = noon - 5 * hour, sunset = noon + 5 * hour)

        assertEquals("01d", reading.iconCodeAt(noon + 5 * hour + 10 * 60))
    }

    @Test
    fun `without sun times the icon stands`() {
        assertEquals("01d", weather(observedAt = noon).iconCodeAt(noon + 9 * hour))
    }

    @Test
    fun `day and night go by the city's own clock`() {
        // UTC+10: sunrise 06:00 and sunset 18:00 local are 20:00 the day before and 08:00 UTC.
        val reading = weather(
            observedAt = noon - 7 * hour,
            sunrise = noon - 16 * hour,
            sunset = noon - 4 * hour,
            offset = 10 * 3600,
        )

        assertEquals("01n", reading.iconCodeAt(noon - 2 * hour))
    }

    @Test
    fun `the forecast hour in progress stands in for an old reading`() {
        val forecast = listOf(
            day(tempMin = 2.0, tempMax = 9.0, slots = listOf(slot(noon, 5.0, "04d"), slot(noon + hour, 7.0, "10d"))),
        )

        val now = weather(observedAt = noon - 20 * hour)
        val shown = forecastAsCurrent(now, forecast, nowEpochSeconds = noon + hour + 10 * 60)!!

        assertEquals(7.0, shown.temperature, 0.0)
        assertEquals("10d", shown.iconCode)
        assertEquals("Rain", shown.conditionMain)
        assertEquals(2.0, shown.tempMin, 0.0)
        assertEquals(9.0, shown.tempMax, 0.0)
        assertNull("Visibility isn't forecast", shown.visibilityMeters)
        assertEquals("Austin", shown.cityName)
        assertEquals(now.observedAtEpochSeconds, shown.observedAtEpochSeconds)
    }

    @Test
    fun `a three-hourly forecast covers the hours inside its slots`() {
        val forecast = listOf(day(slots = listOf(slot(noon, 5.0), slot(noon + 3 * hour, 8.0))))

        assertEquals(5.0, forecastAsCurrent(weather(noon), forecast, noon + 2 * hour)!!.temperature, 0.0)
    }

    @Test
    fun `no stand-in once the forecast has run out, or before it starts`() {
        val forecast = listOf(day(slots = listOf(slot(noon, 5.0), slot(noon + hour, 6.0))))

        assertNull(forecastAsCurrent(weather(noon), forecast, noon + 4 * hour + 1))
        assertNull(forecastAsCurrent(weather(noon), forecast, noon - 1))
        assertNull(forecastAsCurrent(weather(noon), emptyList(), noon))
    }

    @Test
    fun `icon codes map to OpenWeather condition groups`() {
        assertEquals("Clear", conditionMainForIcon("01n"))
        assertEquals("Clouds", conditionMainForIcon("03d"))
        assertEquals("Rain", conditionMainForIcon("09d"))
        assertEquals("Thunderstorm", conditionMainForIcon("11d"))
        assertEquals("Snow", conditionMainForIcon("13n"))
        assertEquals("Mist", conditionMainForIcon("50d"))
    }

    @Test
    fun `reading day is today, yesterday or the day's name, in the city's own date`() {
        // 2026-01-01 is a Thursday. 23:30 UTC on the 1st is already the 2nd in Tokyo (UTC+9).
        assertEquals(ReadingDay.Today, readingDay(noon, 0, noon + 2 * hour))
        assertEquals(ReadingDay.Yesterday, readingDay(noon, 0, noon + 24 * hour))
        assertEquals(ReadingDay.Earlier("Thu"), readingDay(noon, 0, noon + 72 * hour))
        assertEquals(ReadingDay.Yesterday, readingDay(noon, 9 * 3600, noon + 11 * hour + 30 * 60 + 9 * hour))
    }

    @Test
    fun `sun times from an earlier day move onto today`() {
        // Cached yesterday evening with yesterday's sun (07:00 and 16:30, UTC), shown at 10:00 today.
        val dayStart = noon - 12 * hour
        val reading = weather(
            observedAt = dayStart - 3 * hour,
            iconCode = "01n",
            sunrise = dayStart - 24 * hour + 7 * hour,
            sunset = dayStart - 24 * hour + 16 * hour + 1800,
        )
        val moved = reading.withSunTimesToday(dayStart + 10 * hour)

        assertEquals(dayStart + 7 * hour, moved.sunriseEpoch)
        assertEquals(dayStart + 16 * hour + 1800, moved.sunsetEpoch)
        assertFalse(moved.isDarkAt(dayStart + 10 * hour))
        // Left as it was, yesterday's sunset would say it's dark at 10 AM.
        assertTrue(reading.isDarkAt(dayStart + 10 * hour))
    }

    @Test
    fun `today's or tomorrow's sun times are left alone`() {
        val dayStart = noon - 12 * hour
        val today = weather(observedAt = dayStart + 9 * hour, sunrise = dayStart + 7 * hour, sunset = dayStart + 16 * hour)
        assertEquals(today, today.withSunTimesToday(dayStart + 21 * hour))
        val tomorrows = weather(observedAt = dayStart + 21 * hour, sunrise = dayStart + 31 * hour, sunset = dayStart + 40 * hour)
        assertEquals(tomorrows, tomorrows.withSunTimesToday(dayStart + 21 * hour))
    }

    @Test
    fun `it's dark before sunrise and from sunset by the clock`() {
        val dayStart = noon - 12 * hour
        val reading = weather(observedAt = dayStart + 9 * hour, sunrise = dayStart + 7 * hour, sunset = dayStart + 16 * hour)
        assertTrue(reading.isDarkAt(dayStart + 6 * hour))
        assertFalse(reading.isDarkAt(dayStart + 12 * hour))
        assertTrue(reading.isDarkAt(dayStart + 16 * hour + 300))
        // Without sun times it's left to the forecast.
        assertFalse(weather(observedAt = dayStart).isDarkAt(dayStart + 23 * hour))
    }

    private fun weather(
        observedAt: Long?,
        iconCode: String = "01d",
        sunrise: Long? = null,
        sunset: Long? = null,
        offset: Int = 0,
    ) = CurrentWeather(
        cityName = "Austin",
        conditionMain = "Clear",
        description = "Clear sky",
        iconCode = iconCode,
        temperature = 20.0,
        feelsLike = 20.0,
        tempMin = 15.0,
        tempMax = 25.0,
        humidityPercent = 40,
        pressureHpa = 1013,
        windSpeed = 3.0,
        windDeg = 90,
        visibilityMeters = 10000,
        cloudPercent = 10,
        units = WeatherUnits.METRIC,
        sunriseEpoch = sunrise,
        sunsetEpoch = sunset,
        utcOffsetSeconds = offset,
        observedAtEpochSeconds = observedAt,
    )

    private fun day(
        tempMin: Double = 0.0,
        tempMax: Double = 10.0,
        slots: List<HourlySlot>,
    ) = DayForecast(
        dateLabel = "Thu, Jan 1",
        conditionMain = "Clouds",
        description = "cloudy",
        iconCode = "03d",
        temperature = 5.0,
        feelsLike = 5.0,
        tempMin = tempMin,
        tempMax = tempMax,
        units = WeatherUnits.METRIC,
        hourlySlots = slots,
    )

    private fun slot(
        epochSeconds: Long,
        temperature: Double,
        iconCode: String = "01d",
    ) = HourlySlot(
        timeLabel = epochSeconds.toString(),
        iconCode = iconCode,
        description = "forecast",
        temperature = temperature,
        feelsLike = temperature,
        windSpeed = 2.0,
        windDeg = 0,
        humidity = 60,
        pressure = 1010,
        units = WeatherUnits.METRIC,
        epochSeconds = epochSeconds,
    )
}
