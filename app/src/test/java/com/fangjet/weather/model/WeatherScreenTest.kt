package com.fangjet.weather.model

import com.fangjet.weather.R
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DateRule
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.SeasonalTheme
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

    // --- Hero caption ---

    @Test
    fun `a cloudy hero's captions follow the sky as Misty's View reads it`() {
        fun captions(
            cloudPercent: Int,
            iconCode: String,
        ): HeroCaption.Weather {
            val state = weatherState(make(LoadingState.Success(reading(noon, iconCode, "Clouds", cloudPercent))))
            assertEquals(PetMood.CLOUDY, state.mood)
            return state.heroCaption as HeroCaption.Weather
        }
        val fewClouds = captions(20, "02d")
        assertEquals(R.array.pet_caption_partly_cloudy, fewClouds.captions)
        assertEquals(R.array.pet_caption_partly_cloudy_no_dog, fewClouds.noDogCaptions)
        assertEquals(R.array.pet_caption_cloudy, captions(70, "04d").captions)
        val overcast = captions(100, "04d")
        assertEquals(R.array.pet_caption_cloudy, overcast.captions)
        assertEquals(R.array.pet_caption_cloudy_no_dog, overcast.noDogCaptions)
    }

    // --- Helpers ---

    // --- Seasonal theme by the clock ---

    // A theme on the readings' day, 1 January.
    private val newYear = SeasonalTheme(
        id = "newyear",
        dates = DateRule.Fixed(1, 1),
        colors = SeasonalTheme.Colors(0x111111, 0x222222, 0x333333, 0x444444, 0x555555, 0x666666, null, null),
        captions = SeasonalTheme.Captions(day = listOf("Day"), night = listOf("Night")),
        decorations = emptySet(),
    )

    private fun themed(
        now: Long,
        iconCode: String,
        conditionMain: String = "Clear",
    ) = weatherState(
        make(
            LoadingState.Success(reading(observedAt = now, iconCode = iconCode, conditionMain = conditionMain)),
            now = now,
            config = WeatherScreenConfig(seasonalThemes = listOf(newYear)),
        ),
    )

    @Test
    fun `a rainy evening on the theme's day gets the night look`() {
        val state = themed(dayStart + 19 * hour, iconCode = "10n", conditionMain = "Rain")
        assertEquals(PetMood.RAIN, state.mood)
        assertTrue(state.dark)
        assertEquals(HeroCaption.Theme("Night"), state.heroCaption)
    }

    @Test
    fun `3 AM on the theme's day is dark with the day caption`() {
        val state = themed(dayStart + 3 * hour, iconCode = "01n")
        assertTrue(state.dark)
        assertEquals(HeroCaption.Theme("Day"), state.heroCaption)
    }

    @Test
    fun `noon on the theme's day gets the day look`() {
        val state = themed(noon, iconCode = "01d")
        assertFalse(state.dark)
        assertEquals(HeroCaption.Theme("Day"), state.heroCaption)
    }

    @Test
    fun `an old reading from before sunset is dark by the clock`() {
        // Cached at 4 PM, shown at 8 PM: the hero and the theme both go by the clock.
        val state = weatherState(
            make(
                LoadingState.Success(reading(observedAt = dayStart + 16 * hour, iconCode = "10d", conditionMain = "Rain")),
                now = dayStart + 20 * hour,
                config = WeatherScreenConfig(seasonalThemes = listOf(newYear)),
            ),
        )
        assertTrue(state.dark)
        assertEquals(HeroCaption.Theme("Night"), state.heroCaption)
    }

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
        conditionMain: String = "Clear",
        cloudPercent: Int = 10,
    ) = CurrentWeather(
        cityName = "Austin",
        conditionMain = conditionMain,
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
        cloudPercent = cloudPercent,
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
