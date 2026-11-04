package com.fangjet.weather.data

import com.fangjet.weather.data.remote.dto.OpenMeteoDailyDto
import com.fangjet.weather.data.remote.dto.OpenMeteoHourlyDto
import com.fangjet.weather.data.remote.dto.OpenMeteoMinutely15Dto
import com.fangjet.weather.data.remote.dto.OpenMeteoResponse
import com.fangjet.weather.domain.QuarterHour
import com.fangjet.weather.domain.WeatherUnits
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenMeteoForecastSourceTest {

    private val dayStart = 1_704_067_200L // 2024-01-01T00:00:00Z

    private fun response(
        hours: Int,
        offset: Int = 0,
        zone: String? = null,
        start: Long = dayStart,
        temp: Double = 20.0,
        radiation: Double = 0.0,
        isDay: Int = 1,
    ) = OpenMeteoResponse(
        utcOffsetSeconds = offset,
        timezone = zone,
        hourly = OpenMeteoHourlyDto(
            time = List(hours) { start + it * 3600L },
            temperature = List(hours) { temp },
            weatherCode = List(hours) { 61 },
            isDay = List(hours) { isDay },
            uvIndex = List(hours) { it.toDouble() },
            shortwaveRadiation = List(hours) { radiation },
            precipitationProbability = List(hours) { 40 },
        ),
    )

    @Test
    fun `drops hours before the current hour`() {
        // "Now" is 05:30, so the 00:00-04:00 hours are gone and the first slot is 05:00.
        val now = dayStart + 5 * 3600 + 1800
        val days = OpenMeteoForecastSource.toDayForecasts(response(24), WeatherUnits.METRIC, now)
        assertEquals(19, days.flatMap { it.hourlySlots }.size)
        assertEquals(
            "5 AM",
            days
                .first()
                .hourlySlots
                .first()
                .timeLabel,
        )
    }

    @Test
    fun `today's high and low cover the whole day, not just the hours left`() {
        // 24 hours peaking at 2 PM (25°) with a 2 AM low (5°); now is 9 PM.
        val temps = List(24) { hour -> 15.0 + 10.0 * Math.sin((hour - 8) * Math.PI / 12) }
        val r = response(24)
        val response = r.copy(hourly = r.hourly.copy(temperature = temps))
        val now = dayStart + 21 * 3600

        val today = OpenMeteoForecastSource.toDayForecasts(response, WeatherUnits.METRIC, now).first()

        assertEquals("9 PM", today.hourlySlots.first().timeLabel)
        assertEquals(temps.max(), today.tempMax, 0.0001)
        assertEquals(temps[14], today.tempMax, 0.0001)
        assertEquals(temps.min(), today.tempMin, 0.0001)
    }

    @Test
    fun `groups and labels hours in the city's own timezone`() {
        // UTC+10: midnight UTC is 10 AM local on the same date, and 14:00 UTC is midnight next day.
        val days = OpenMeteoForecastSource.toDayForecasts(response(24, offset = 36_000), WeatherUnits.METRIC, dayStart)
        assertEquals(
            "10 AM",
            days
                .first()
                .hourlySlots
                .first()
                .timeLabel,
        )
        assertEquals(2, days.size)
    }

    @Test
    fun `maps weather code, rain chance, and daily peak uv`() {
        val day = OpenMeteoForecastSource.toDayForecasts(response(6), WeatherUnits.METRIC, dayStart).single()
        assertEquals("Rain", day.conditionMain)
        assertEquals(40, day.precipitationChance)
        assertEquals(5.0, day.uvIndexMax ?: -1.0, 0.0001)
        assertEquals("10d", day.hourlySlots.first().iconCode)
    }

    @Test
    fun `pavement heat is estimated in the requested units`() {
        // 86F = 30C air; 800 W/m2 adds 28C -> 58C = 136.4F.
        val slot = OpenMeteoForecastSource
            .toDayForecasts(response(1, temp = 86.0, radiation = 800.0), WeatherUnits.IMPERIAL, dayStart)
            .single()
            .hourlySlots
            .single()
        assertEquals(136.4, slot.pavementTemperature ?: 0.0, 0.01)
    }

    @Test
    fun `keeps the current hour in a half-hour timezone`() {
        // India (+5:30): slots start at xx:30 UTC. At 10:40 local (05:10 UTC) the 10:00 local slot
        // (04:30 UTC) is the current hour and must stay.
        val start = dayStart - 1800 // 23:30 UTC = 05:00 local
        val now = dayStart + 5 * 3600 + 600
        val first = OpenMeteoForecastSource
            .toDayForecasts(response(24, offset = 19_800, zone = "Asia/Kolkata", start = start), WeatherUnits.METRIC, now)
            .first()
            .hourlySlots
            .first()
        assertEquals("10 AM", first.timeLabel)
        assertEquals(10, first.localHour)
    }

    @Test
    fun `labels follow a DST change inside the forecast window`() {
        // London leaves BST at 01:00 UTC on 2026-10-25. Fetched before, with offset +3600, the
        // 12:00 UTC slot after the change must read noon, not 1 PM.
        val noonUtcAfter = 1_792_929_600L // 2026-10-25T12:00:00Z
        val days = OpenMeteoForecastSource.toDayForecasts(
            response(1, offset = 3600, zone = "Europe/London", start = noonUtcAfter),
            WeatherUnits.METRIC,
            noonUtcAfter,
        )
        assertEquals(
            12,
            days
                .single()
                .hourlySlots
                .single()
                .localHour,
        )
    }

    @Test
    fun `missing uv stays unknown rather than zero`() {
        val r = response(2)
        val noUv = r.copy(hourly = r.hourly.copy(uvIndex = emptyList()))
        val day = OpenMeteoForecastSource.toDayForecasts(noUv, WeatherUnits.METRIC, dayStart).single()
        assertEquals(null, day.hourlySlots.first().uvIndex)
        assertEquals(null, day.uvIndexMax)
    }

    @Test
    fun `hours missing a temperature are skipped`() {
        val r = response(3)
        val gappy = r.copy(hourly = r.hourly.copy(temperature = listOf(20.0, null, 21.0)))
        val slots = OpenMeteoForecastSource.toDayForecasts(gappy, WeatherUnits.METRIC, dayStart).single().hourlySlots
        assertEquals(2, slots.size)
    }

    @Test
    fun `ground is wet from rain in the 12 hours before an hour or during it, or from lying snow`() {
        val r = response(24)
        // 0.4 mm fell in the hour before 03:00, i.e. from 02:00 to 03:00.
        val rained = r.hourly.copy(precipitation = List(24) { if (it == 3) 0.4 else 0.0 })
        val wet = (0 until 24).map { OpenMeteoForecastSource.groundWet(rained, it) }

        // 01:00's hour ends at 02:00, before the rain: dry. 02:00's hour is the rain itself.
        assertEquals(false, wet[1])
        assertEquals(true, wet[2])
        // The 12 hours before 14:00 still take in the rain; the 12 before 15:00 start after it.
        assertEquals(true, wet[14])
        assertEquals(false, wet[15])

        val snowy = r.hourly.copy(snowDepth = List(24) { 0.02 })
        assertEquals(true, OpenMeteoForecastSource.groundWet(snowy, 10))
        assertEquals(false, OpenMeteoForecastSource.groundWet(r.hourly, 10))
    }

    @Test
    fun `slots carry whether the ground is wet`() {
        val r = response(3)
        val rained = r.copy(hourly = r.hourly.copy(precipitation = listOf(0.0, 0.0, 1.0)))
        val slots = OpenMeteoForecastSource.toDayForecasts(rained, WeatherUnits.METRIC, dayStart).single().hourlySlots
        assertEquals(listOf(false, true, true), slots.map { it.groundWet })
    }

    @Test
    fun `slots carry the rain amount from the same index as their chance`() {
        val r = response(3)
        val rained = r.copy(
            hourly = r.hourly.copy(
                precipitationProbability = listOf(10, 70, 40),
                precipitation = listOf(0.0, 2.5, null),
            ),
        )
        val slots = OpenMeteoForecastSource.toDayForecasts(rained, WeatherUnits.METRIC, dayStart).single().hourlySlots

        assertEquals(listOf(10, 70, 40), slots.map { it.precipitationChance })
        assertEquals(listOf(0.0, 2.5, null), slots.map { it.precipitationMm })
    }

    @Test
    fun `15-minute amounts become quarters starting 15 minutes before their time, on the first day`() {
        val r = response(3).copy(
            minutely15 = OpenMeteoMinutely15Dto(
                time = listOf(dayStart + 900, dayStart + 1800, dayStart + 2700),
                precipitation = listOf(0.0, 0.4, null),
                snowfall = listOf(0.0, 0.2, 0.0),
            ),
        )
        val days = OpenMeteoForecastSource.toDayForecasts(r, WeatherUnits.METRIC, dayStart)

        assertEquals(
            listOf(QuarterHour(dayStart, 0.0, 0.0), QuarterHour(dayStart + 900, 0.4, 0.2)),
            days.first().quarterHours,
        )
    }

    @Test
    fun `each day carries its sunrise and sunset`() {
        val day = 86_400L
        val r = response(48).copy(
            daily = OpenMeteoDailyDto(
                time = listOf(dayStart - day, dayStart, dayStart + day),
                sunrise = listOf(dayStart - day + 7 * 3600, dayStart + 7 * 3600 + 120, dayStart + day + 7 * 3600 + 180),
                sunset = listOf(dayStart - day + 16 * 3600, dayStart + 16 * 3600 + 2400, dayStart + day + 16 * 3600 + 2460),
            ),
        )
        val days = OpenMeteoForecastSource.toDayForecasts(r, WeatherUnits.METRIC, dayStart)

        assertEquals(listOf(dayStart + 7 * 3600 + 120, dayStart + day + 7 * 3600 + 180), days.map { it.sunriseEpoch })
        assertEquals(listOf(dayStart + 16 * 3600 + 2400, dayStart + day + 16 * 3600 + 2460), days.map { it.sunsetEpoch })
    }

    @Test
    fun `a day the sun doesn't rise or set has no sun times`() {
        // Open-Meteo puts both on the day's midnight in a polar night, and the sunset on the next
        // midnight in a polar day.
        val day = 86_400L
        val r = response(48).copy(
            daily = OpenMeteoDailyDto(
                time = listOf(dayStart, dayStart + day),
                sunrise = listOf(dayStart, dayStart + day),
                sunset = listOf(dayStart, dayStart + 2 * day),
            ),
        )
        val days = OpenMeteoForecastSource.toDayForecasts(r, WeatherUnits.METRIC, dayStart)

        assertEquals(2, days.size)
        assertEquals(listOf(null, null), days.map { it.sunriseEpoch })
        assertEquals(listOf(null, null), days.map { it.sunsetEpoch })
    }

    @Test
    fun `sun times follow the city's own days`() {
        // UTC+10: the local day starting at 14:00 UTC has its sunrise at 20:30 UTC (6:30 AM local).
        val r = response(24, offset = 36_000).copy(
            daily = OpenMeteoDailyDto(
                time = listOf(dayStart - 36_000, dayStart + 50_400),
                sunrise = listOf(dayStart - 36_000 + 6 * 3600, dayStart + 50_400 + 6 * 3600 + 1800),
                sunset = listOf(dayStart - 36_000 + 18 * 3600, dayStart + 50_400 + 18 * 3600),
            ),
        )
        val days = OpenMeteoForecastSource.toDayForecasts(r, WeatherUnits.METRIC, dayStart)

        assertEquals(listOf(dayStart - 36_000 + 6 * 3600, dayStart + 50_400 + 6 * 3600 + 1800), days.map { it.sunriseEpoch })
    }
}
