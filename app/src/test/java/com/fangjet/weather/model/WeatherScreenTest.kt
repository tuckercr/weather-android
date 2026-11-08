package com.fangjet.weather.model

import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.util.HourlyStripItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Same cases as iOS's `WeatherScreenTests`. */
class WeatherScreenTest {
    private val hour = 3_600L

    // Mon 1 Jan 2024, 00:00 UTC; the readings are in UTC.
    private val dayStart = 1_704_067_200L
    private val noon = dayStart + 12 * hour

    // --- Loading, errors and old readings ---

    @Test
    fun `loading and errors pass through`() {
        assertEquals(WeatherScreen.Loading, make(LoadingState.Loading))
        assertEquals(
            WeatherScreen.Error("network", canRetry = true),
            make(LoadingState.Error("network", canRetry = true)),
        )
    }

    @Test
    fun `a fresh reading shows with no banner`() {
        val state = weatherState(make(LoadingState.Success(reading(observedAt = noon))))
        assertFalse(state.showingForecast)
        assertNull(state.stale)
    }

    @Test
    fun `an old reading while refreshes fail shows with the banner`() {
        val state = weatherState(make(LoadingState.Success(reading(observedAt = noon - hour)), staleErrorKey = "network"))
        assertEquals("network", state.stale?.errorKey)
        assertFalse(state.showingForecast)
    }

    @Test
    fun `past the cutoff the forecast hour stands in`() {
        val forecast = listOf(day(slots = (0 until 24).map { slot(dayStart + it * hour, temperature = 30.0) }))
        val state = weatherState(
            make(
                LoadingState.Success(reading(observedAt = noon - 5 * hour)),
                forecast = LoadingState.Success(forecast),
                staleErrorKey = "network",
            ),
        )
        assertTrue(state.showingForecast)
        assertEquals(30.0, state.weather.temperature, 0.0)
        assertEquals(20.0, state.stale?.reading?.temperature ?: 0.0, 0.0)
    }

    @Test
    fun `past the cutoff with no forecast hour is the error with the old reading`() {
        val old = reading(observedAt = noon - 5 * hour)
        val screen = make(LoadingState.Success(old), staleErrorKey = "network") as WeatherScreen.Error
        assertEquals("network", screen.errorKey)
        assertTrue(screen.canRetry)
        assertEquals(old, screen.oldReading)
    }

    // --- Today ---

    @Test
    fun `today goes by the city's date not the label`() {
        // The label is stale, but the hours are today's.
        val today = day(label = "Sun, Dec 31", slots = listOf(slot(dayStart + 13 * hour, temperature = 20.0)))
            .copy(tempMin = 5.0, tempMax = 25.0)
        val state = weatherState(make(LoadingState.Success(reading(observedAt = noon)), forecast = LoadingState.Success(listOf(today))))
        assertEquals("Sun, Dec 31", state.today?.dateLabel)
        assertEquals(25, state.high)
        assertEquals(5, state.low)
    }

    @Test
    fun `after midnight yesterday is not today`() {
        // A forecast cached yesterday: its first day has gone, so the week starts with it too.
        val yesterday = day(slots = listOf(slot(dayStart - 10 * hour, temperature = 20.0)))
        val state = weatherState(make(LoadingState.Success(reading(observedAt = noon)), forecast = LoadingState.Success(listOf(yesterday))))
        assertNull(state.today)
    }

    @Test
    fun `the week leaves out today`() {
        val today = day(slots = listOf(slot(dayStart + 13 * hour, temperature = 20.0)))
        val tomorrow = day(label = "Tue, Jan 2", slots = listOf(slot(dayStart + 37 * hour, temperature = 20.0)))
        val state = weatherState(
            make(LoadingState.Success(reading(observedAt = noon)), forecast = LoadingState.Success(listOf(today, tomorrow))),
        )
        assertEquals(listOf("Tue, Jan 2"), (state.week as WeekSection.Days).days.map { it.dateLabel })
    }

    @Test
    fun `the week shows loading and errors and can be switched off`() {
        val current = LoadingState.Success(reading(observedAt = noon))
        assertEquals(WeekSection.Loading, weatherState(make(current, forecast = LoadingState.Loading)).week)
        assertEquals(
            WeekSection.Error("network", canRetry = false),
            weatherState(make(current, forecast = LoadingState.Error("network", canRetry = false))).week,
        )
        val off = WeatherScreenConfig(weekForecastEnabled = false)
        assertNull(weatherState(make(current, forecast = LoadingState.Loading, config = off)).week)
    }

    // --- Hourly strip ---

    @Test
    fun `the hourly strip starts at the hour in progress and can be switched off`() {
        val forecast = LoadingState.Success(
            listOf(day(slots = (0 until 48).map { slot(dayStart + it * hour, temperature = it.toDouble()) })),
        )
        val current = LoadingState.Success(reading(observedAt = noon))
        val at = noon + 30 * 60
        val hours = weatherState(make(current, forecast = forecast, now = at))
            .hourlyStrip
            .filterIsInstance<HourlyStripItem.Hour>()
            .map { it.slot }
        assertEquals(noon, hours.first().epochSeconds)
        assertEquals(HOURLY_STRIP_COUNT, hours.size)

        val off = WeatherScreenConfig(hourlyForecastEnabled = false)
        assertTrue(weatherState(make(current, forecast = forecast, now = at, config = off)).hourlyStrip.isEmpty())
    }

    // --- Walk and tiles ---

    @Test
    fun `the walk card waits for the forecast`() {
        val current = LoadingState.Success(reading(observedAt = noon))
        val loading = weatherState(make(current, forecast = LoadingState.Loading))
        assertNull(loading.walkAdvice)
        assertFalse(loading.showsWalkCard)

        val forecast = listOf(day(slots = (0 until 24).map { slot(dayStart + it * hour, temperature = 18.0) }))
        assertNotNull(weatherState(make(current, forecast = LoadingState.Success(forecast))).walkAdvice)
    }

    @Test
    fun `the pavement tile needs a dog and air quality its switch`() {
        val hot = slot(noon, temperature = 35.0).copy(
            pavementTemperature = 55.0,
            airQuality = AirQuality(index = 30, scale = AirQuality.Scale.EUROPEAN, mainPollutant = null),
        )
        val forecast = LoadingState.Success(listOf(day(slots = listOf(hot))))
        val current = LoadingState.Success(reading(observedAt = noon))

        val withDog = weatherState(make(current, forecast = forecast))
        assertEquals(55.0, withDog.tiles.pavementNow ?: 0.0, 0.0)
        assertNotNull(withDog.tiles.airQuality)

        assertNull(weatherState(make(current, forecast = forecast, hasDog = false)).tiles.pavementNow)
        val off = WeatherScreenConfig(airQualityEnabled = false)
        assertNull(weatherState(make(current, forecast = forecast, config = off)).tiles.airQuality)
    }

    @Test
    fun `UV is left out at night`() {
        val night = dayStart + 22 * hour
        val uv = slot(night, temperature = 10.0).copy(uvIndex = 3.0)
        val state = weatherState(
            make(
                LoadingState.Success(reading(observedAt = night, iconCode = "01n")),
                forecast = LoadingState.Success(listOf(day(slots = listOf(uv)))),
                now = night,
            ),
        )
        assertNull(state.tiles.uvIndex)
    }

    // --- Helpers ---

    private fun make(
        current: LoadingState<CurrentWeather>,
        forecast: LoadingState<List<DayForecast>> = LoadingState.Idle,
        staleErrorKey: String? = null,
        now: Long = noon,
        hasDog: Boolean = true,
        config: WeatherScreenConfig = WeatherScreenConfig(),
    ) = weatherScreen(current, forecast, staleErrorKey, now, hasDog, config)

    private fun weatherState(screen: WeatherScreen): WeatherScreenState = (screen as WeatherScreen.Weather).state

    private fun reading(
        observedAt: Long,
        iconCode: String = "01d",
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
        visibilityMeters = 10_000,
        cloudPercent = 10,
        units = WeatherUnits.METRIC,
        sunriseEpoch = dayStart + 7 * hour,
        sunsetEpoch = dayStart + 17 * hour,
        utcOffsetSeconds = 0,
        observedAtEpochSeconds = observedAt,
    )

    private fun day(
        label: String = "Mon, Jan 1",
        slots: List<HourlySlot>,
    ) = DayForecast(
        dateLabel = label,
        conditionMain = "Clear",
        description = "clear",
        iconCode = "01d",
        temperature = 18.0,
        feelsLike = 18.0,
        tempMin = 10.0,
        tempMax = 22.0,
        units = WeatherUnits.METRIC,
        hourlySlots = slots,
    )

    private fun slot(
        epoch: Long,
        temperature: Double,
    ) = HourlySlot(
        timeLabel = epoch.toString(),
        iconCode = "01d",
        description = "clear",
        temperature = temperature,
        feelsLike = temperature,
        windSpeed = 2.0,
        windDeg = 0,
        humidity = 60,
        pressure = 1010,
        units = WeatherUnits.METRIC,
        epochSeconds = epoch,
    )
}
