package com.fangjet.weather.util

import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.ui.formatEpochTime
import com.fangjet.weather.ui.formatShortEpochTime
import org.junit.Assert.assertEquals
import org.junit.Test

/** Same cases as iOS's `SunEventsTests`. */
class SunEventsTest {

    // 2024-01-01T00:00:00Z; the slots and sun times here are in UTC.
    private val dayStart = 1_704_067_200L

    private fun at(
        hour: Int,
        minute: Int = 0,
    ) = dayStart + hour * 3600L + minute * 60L

    @Test
    fun `a sunset sits between the hours it falls between`() {
        val items = hourlyStrip(slots(17..20), listOf(SunEvent(sunrise = false, epochSeconds = at(18, 50))), at(17, 20))

        assertEquals(listOf("17", "18", "sunset", "19", "20"), ids(items))
    }

    @Test
    fun `one on the hour goes before that hour`() {
        val items = hourlyStrip(slots(5..8), listOf(SunEvent(sunrise = true, epochSeconds = at(7))), at(5))

        assertEquals(listOf("5", "6", "sunrise", "7", "8"), ids(items))
    }

    @Test
    fun `ones already past or beyond the last hour are left out`() {
        val events = listOf(
            // Ten minutes ago, though its hour is still on the strip.
            SunEvent(sunrise = false, epochSeconds = at(18, 10)),
            // After the last hour starts, so not between two of them.
            SunEvent(sunrise = true, epochSeconds = at(22, 30)),
        )

        assertEquals(listOf("18", "19", "20", "21", "22"), ids(hourlyStrip(slots(18..22), events, at(18, 20))))
    }

    @Test
    fun `the evening's strip has tonight's sunset and tomorrow's sunrise`() {
        val forecast = listOf(day(at(7, 2), at(16, 40)), day(at(24 + 7, 3), at(24 + 16, 41)))
        val events = sunEvents(forecast, reading(at(7), at(16)))
        val items = ids(hourlyStrip(slots(15..32), events, at(15, 30)))

        assertEquals(listOf(at(7, 2), at(16, 40), at(31, 3), at(40, 41)), events.map { it.epochSeconds })
        assertEquals(listOf("sunset", "sunrise"), items.filterNot { id -> id.all { it.isDigit() } })
        assertEquals(listOf("16", "sunset", "17"), items.subList(1, 4))
        assertEquals(listOf("31", "sunrise", "32"), items.subList(17, 20))
    }

    @Test
    fun `without the forecast's sun times the reading's are used`() {
        assertEquals(
            listOf(SunEvent(sunrise = true, epochSeconds = at(7)), SunEvent(sunrise = false, epochSeconds = at(16))),
            sunEvents(listOf(day(null, null)), reading(at(7), at(16))),
        )
        // A sunrise that isn't before the sunset isn't a day.
        assertEquals(emptyList<SunEvent>(), sunEvents(emptyList(), reading(at(16), at(16))))
        assertEquals(emptyList<SunEvent>(), sunEvents(emptyList(), reading(null, null)))
    }

    @Test
    fun `the time is short on the strip and in full for TalkBack`() {
        assertEquals("6:50", formatShortEpochTime(at(18, 50), 0))
        assertEquals("6:50 PM", formatEpochTime(at(18, 50), 0))
        assertEquals("6:05", formatShortEpochTime(at(6, 5), 0))
    }

    @Test
    fun `the line runs on beside the rain chances only when an hour either side shows one`() {
        val wet = slots(16..19).map { it.copy(precipitationChance = if (it.epochSeconds == at(18)) 30 else 0) }
        val items = hourlyStrip(wet, listOf(SunEvent(sunrise = false, epochSeconds = at(17, 49))), at(16))
        val shows = { slot: HourlySlot -> slot.precipitationChance >= 10 }

        assertEquals(listOf("16", "17", "sunset", "18", "19"), ids(items))
        // 6 PM, after the sunset, shows 30%.
        assertEquals(true, items.besideRainChance(2, shows))
        // A dry strip, or rain only further along, keeps it short.
        val dry = hourlyStrip(slots(16..19), listOf(SunEvent(sunrise = false, epochSeconds = at(17, 49))), at(16))
        assertEquals(false, dry.besideRainChance(2, shows))
        val later = slots(16..21).map { it.copy(precipitationChance = if (it.epochSeconds == at(21)) 80 else 0) }
        assertEquals(false, hourlyStrip(later, listOf(SunEvent(false, at(17, 49))), at(16)).besideRainChance(2, shows))
    }

    private fun ids(items: List<HourlyStripItem>) =
        items.map { item ->
            when (item) {
                is HourlyStripItem.Hour -> ((item.slot.epochSeconds!! - dayStart) / 3600).toString()
                is HourlyStripItem.Sun -> if (item.event.sunrise) "sunrise" else "sunset"
            }
        }

    private fun slots(hours: IntRange) = hours.map { slot(at(it)) }

    private fun slot(epochSeconds: Long) =
        HourlySlot(
            timeLabel = epochSeconds.toString(),
            iconCode = "01d",
            description = "clear",
            temperature = 20.0,
            feelsLike = 20.0,
            windSpeed = 2.0,
            windDeg = 0,
            humidity = 60,
            pressure = 1010,
            units = WeatherUnits.METRIC,
            epochSeconds = epochSeconds,
        )

    private fun day(
        sunrise: Long?,
        sunset: Long?,
    ) = DayForecast(
        dateLabel = "day",
        conditionMain = "Clear",
        description = "Clear",
        iconCode = "01d",
        temperature = 20.0,
        feelsLike = 20.0,
        tempMin = 10.0,
        tempMax = 25.0,
        units = WeatherUnits.METRIC,
        sunriseEpoch = sunrise,
        sunsetEpoch = sunset,
    )

    private fun reading(
        sunrise: Long?,
        sunset: Long?,
    ) = CurrentWeather(
        cityName = "Here",
        conditionMain = "Clear",
        description = "Clear",
        iconCode = "01d",
        temperature = 20.0,
        feelsLike = 20.0,
        tempMin = 10.0,
        tempMax = 25.0,
        humidityPercent = 60,
        pressureHpa = 1010,
        windSpeed = 2.0,
        windDeg = 0,
        visibilityMeters = null,
        cloudPercent = 0,
        units = WeatherUnits.METRIC,
        sunriseEpoch = sunrise,
        sunsetEpoch = sunset,
        utcOffsetSeconds = 0,
    )
}
