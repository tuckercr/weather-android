package com.fangjet.weather.data

import android.annotation.SuppressLint
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.WeatherUnits
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Collapses OpenWeatherMap 3-hour forecast samples into one representative row per calendar day
 * (sample closest to local noon) while also preserving all slots for the hourly detail view.
 */
object ForecastAggregator {

    private val dayLabelFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EEE, MMM d")

    // "3 PM" / "3 AM" — compact label for horizontal hourly scroll
    @SuppressLint("ConstantLocale")
    private val timeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("h a", Locale.getDefault())

    data class Slot(
        val epochSeconds: Long,
        val temperature: Double,
        val feelsLike: Double,
        val tempMin: Double = 0.0,
        val tempMax: Double = 0.0,
        val conditionMain: String,
        val description: String,
        val iconCode: String,
        val windSpeed: Double = 0.0,
        val windDeg: Int = 0,
        val humidity: Int = 0,
        val pressure: Int = 0,
        val pop: Double = 0.0,
        val precipitationMm: Double? = null,
        val uvIndex: Double? = null,
        val pavementTemperature: Double? = null,
        val groundWet: Boolean = false,
    )

    fun aggregate(
        slots: List<Slot>,
        zoneId: ZoneId,
        units: WeatherUnits,
    ): List<DayForecast> {
        if (slots.isEmpty()) return emptyList()

        val byDay = slots.groupBy { slot ->
            ZonedDateTime.ofInstant(Instant.ofEpochSecond(slot.epochSeconds), zoneId).toLocalDate()
        }

        return byDay.keys.sorted().map { date ->
            val daySlots = byDay[date].orEmpty()
            val noonMinutes = 12 * 60
            val representative = daySlots.minBy { slot ->
                val zdt = ZonedDateTime.ofInstant(Instant.ofEpochSecond(slot.epochSeconds), zoneId)
                val minutes = zdt.hour * 60 + zdt.minute
                abs(minutes - noonMinutes)
            }
            val dailyMin = daySlots.minOf { it.tempMin.takeIf { v -> v != 0.0 } ?: it.temperature }
            val dailyMax = daySlots.maxOf { it.tempMax.takeIf { v -> v != 0.0 } ?: it.temperature }

            val hourlySlots = daySlots
                .sortedBy { it.epochSeconds }
                .map { slot ->
                    val zdt =
                        ZonedDateTime.ofInstant(Instant.ofEpochSecond(slot.epochSeconds), zoneId)
                    HourlySlot(
                        timeLabel = zdt.format(timeFormatter),
                        iconCode = slot.iconCode,
                        description = slot.description.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        temperature = slot.temperature,
                        feelsLike = slot.feelsLike,
                        windSpeed = slot.windSpeed,
                        windDeg = slot.windDeg,
                        humidity = slot.humidity,
                        pressure = slot.pressure,
                        units = units,
                        precipitationChance = (slot.pop * 100).roundToInt(),
                        precipitationMm = slot.precipitationMm,
                        uvIndex = slot.uvIndex,
                        pavementTemperature = slot.pavementTemperature,
                        groundWet = slot.groundWet,
                        localHour = zdt.hour,
                        epochSeconds = slot.epochSeconds,
                    )
                }

            val dailyPop = daySlots.maxOfOrNull { it.pop } ?: 0.0

            DayForecast(
                dateLabel = date.atStartOfDay(zoneId).format(dayLabelFormatter),
                conditionMain = representative.conditionMain,
                description = representative.description.replaceFirstChar { it.titlecase(Locale.getDefault()) },
                iconCode = representative.iconCode,
                temperature = representative.temperature,
                feelsLike = representative.feelsLike,
                tempMin = dailyMin,
                tempMax = dailyMax,
                units = units,
                hourlySlots = hourlySlots,
                precipitationChance = (dailyPop * 100).roundToInt(),
                uvIndexMax = daySlots.mapNotNull { it.uvIndex }.maxOrNull(),
            )
        }
    }
}
