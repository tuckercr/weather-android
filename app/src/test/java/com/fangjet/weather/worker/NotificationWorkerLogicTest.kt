package com.fangjet.weather.worker

import androidx.work.ListenableWorker.Result
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.QuarterHour
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.ui.pets.PetMood
import com.fangjet.weather.ui.pets.WalkAdvice
import com.fangjet.weather.ui.pets.WalkRating
import com.fangjet.weather.util.NextRain
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationWorkerLogicTest {

    // --- result routing ---

    @Test
    fun `returns success immediately when no locations are saved`() =
        runTest {
            val posted = mutableListOf<Pair<String, String>>()
            val logic = logic(savedLocations = emptyList(), postNotification = { posted += it.title to it.body })

            val result = logic.doWork()

            assertEquals(Result.success(), result)
            assertEquals(emptyList<Pair<String, String>>(), posted)
        }

    @Test
    fun `returns retry when weather fetch fails`() =
        runTest {
            val logic = logic(
                fetchCurrentWeather = { _, _, _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
            )

            assertEquals(Result.retry(), logic.doWork())
        }

    @Test
    fun `returns success and posts notification when weather fetch succeeds`() =
        runTest {
            val posted = mutableListOf<Pair<String, String>>()
            val logic = logic(postNotification = { posted += it.title to it.body })

            assertEquals(Result.success(), logic.doWork())
            assertEquals(1, posted.size)
        }

    @Test
    fun `returns success without posting when permission is denied`() =
        runTest {
            val posted = mutableListOf<Pair<String, String>>()
            val logic = logic(
                hasNotificationPermission = { false },
                postNotification = { posted += it.title to it.body },
            )

            assertEquals(Result.success(), logic.doWork())
            assertEquals(emptyList<Pair<String, String>>(), posted)
        }

    // --- daily_weather_notifications_enabled ---

    @Test
    fun `daily weather notifications switched off posts nothing but still fetches and succeeds`() =
        runTest {
            val posted = mutableListOf<Pair<String, String>>()
            var fetches = 0
            val logic = logic(
                fetchCurrentWeather = { _, _, _, _ ->
                    fetches++
                    kotlin.Result.success(weather())
                },
                postNotification = { posted += it.title to it.body },
                isDailyWeatherNotificationEnabled = { false },
            )

            assertEquals(Result.success(), logic.doWork())
            assertEquals(emptyList<Pair<String, String>>(), posted)
            assertEquals(1, fetches)
        }

    @Test
    fun `daily weather notifications switched on posts as before`() =
        runTest {
            val posted = mutableListOf<Pair<String, String>>()
            val logic = logic(
                postNotification = { posted += it.title to it.body },
                isDailyWeatherNotificationEnabled = { true },
            )

            assertEquals(Result.success(), logic.doWork())
            assertEquals(1, posted.size)
        }

    @Test
    fun `a failing daily weather notifications switch falls back to posting`() =
        runTest {
            val posted = mutableListOf<Pair<String, String>>()
            val logic = logic(
                postNotification = { posted += it.title to it.body },
                isDailyWeatherNotificationEnabled = { throw IllegalStateException("Remote Config unavailable") },
            )

            assertEquals(Result.success(), logic.doWork())
            assertEquals(1, posted.size)
        }

    // --- fetch routing ---

    @Test
    fun `passes lat-lon and null label to fetch when location has coordinates`() =
        runTest {
            var capturedLat: Double? = "not-called".hashCode().toDouble()
            var capturedLon: Double? = "not-called".hashCode().toDouble()
            val logic = logic(
                savedLocations = listOf(london),
                fetchCurrentWeather = { _, _, lat, lon ->
                    capturedLat = lat
                    capturedLon = lon
                    kotlin.Result.success(weather())
                },
            )

            logic.doWork()

            assertEquals(london.latitude, capturedLat)
            assertEquals(london.longitude, capturedLon)
        }

    @Test
    fun `passes null lat-lon when location has no coordinates`() =
        runTest {
            var capturedLat: Double? = 99.0
            var capturedLon: Double? = 99.0
            val logic = logic(
                savedLocations = listOf(cityByName),
                fetchCurrentWeather = { _, _, lat, lon ->
                    capturedLat = lat
                    capturedLon = lon
                    kotlin.Result.success(weather(cityName = "Austin"))
                },
            )

            logic.doWork()

            assertNull(capturedLat)
            assertNull(capturedLon)
        }

    @Test
    fun `passes location label as query when location has no coordinates`() =
        runTest {
            var capturedLabel: String? = null
            val logic = logic(
                savedLocations = listOf(cityByName),
                fetchCurrentWeather = { _, label, _, _ ->
                    capturedLabel = label
                    kotlin.Result.success(weather(cityName = "Austin"))
                },
            )

            logic.doWork()

            assertEquals(cityByName.label, capturedLabel)
        }

    // --- notification content ---

    @Test
    fun `briefing title is the temperature, the sky and the API city name`() =
        runTest {
            var captured: WeatherNotification? = null
            val logic = logic(
                fetchCurrentWeather = { _, _, _, _ ->
                    kotlin.Result.success(weather(temperature = 86.4, cityName = "London, GB"))
                },
                postNotification = { captured = it },
            )

            logic.doWork()

            assertEquals("86° and clear in London, GB", captured?.title)
            assertEquals(NotificationKind.MORNING_BRIEFING, captured?.kind)
        }

    @Test
    fun `briefing body uses forecast high-low when forecast is available`() =
        runTest {
            var capturedBody: String? = null
            val logic = logic(
                fetchCurrentWeather = { _, _, _, _ ->
                    kotlin.Result.success(weather(tempMin = 50.0, tempMax = 60.0))
                },
                fetchForecast = { _, _, _, _ ->
                    kotlin.Result.success(listOf(forecast(tempMin = 45.0, tempMax = 99.0)))
                },
                postNotification = { capturedBody = it.body },
            )

            logic.doWork()

            // Should use forecast values (99/45), not weather values (60/50)
            assertEquals("High 99°, low 45°.", capturedBody)
        }

    @Test
    fun `briefing body ignores a forecast whose first day is tomorrow`() =
        runTest {
            var capturedBody: String? = null
            val logic = logic(
                fetchCurrentWeather = { _, _, _, _ ->
                    kotlin.Result.success(weather(tempMin = 50.0, tempMax = 60.0))
                },
                fetchForecast = { _, _, _, _ ->
                    kotlin.Result.success(listOf(forecast(tempMin = 45.0, tempMax = 99.0, dateLabel = "Tue, Jan 2")))
                },
                postNotification = { capturedBody = it.body },
            )

            logic.doWork()

            assertEquals("High 60°, low 50°.", capturedBody)
        }

    @Test
    fun `briefing body uses today's entry even when it is not first`() =
        runTest {
            var capturedBody: String? = null
            val logic = logic(
                fetchForecast = { _, _, _, _ ->
                    kotlin.Result.success(
                        listOf(
                            forecast(tempMin = 10.0, tempMax = 20.0, dateLabel = "Sun, Dec 31"),
                            forecast(tempMin = 45.0, tempMax = 99.0),
                        ),
                    )
                },
                postNotification = { capturedBody = it.body },
            )

            logic.doWork()

            assertEquals("High 99°, low 45°.", capturedBody)
        }

    @Test
    fun `briefing body falls back to current weather temps when forecast fails`() =
        runTest {
            var capturedBody: String? = null
            val logic = logic(
                fetchCurrentWeather = { _, _, _, _ ->
                    kotlin.Result.success(weather(tempMin = 50.0, tempMax = 99.0))
                },
                fetchForecast = { _, _, _, _ -> kotlin.Result.failure(RuntimeException("offline")) },
                postNotification = { capturedBody = it.body },
            )

            logic.doWork()

            assertEquals("High 99°, low 50°.", capturedBody)
        }

    @Test
    fun `the morning briefing switch posts nothing`() =
        runTest {
            var posted = false
            logic(postNotification = { posted = true }, isBriefingEnabled = false).doWork()
            assertFalse(posted)
        }

    // --- buildBriefingContent (pure formatter) ---

    @Test
    fun `the day's rain chance is mentioned from 40 percent, rounded`() {
        val (_, wet) = buildBriefingContent(weather(), forecast(tempMin = 48.0, tempMax = 91.0, rainChance = 57), walk = null)
        assertEquals("High 91°, low 48°, 60% chance of rain.", wet)
        val (_, dry) = buildBriefingContent(weather(), forecast(tempMin = 48.0, tempMax = 91.0, rainChance = 34), walk = null)
        assertEquals("High 91°, low 48°.", dry)
    }

    @Test
    fun `rain is named by its description, everything else by the sky`() {
        val (rainy, _) = buildBriefingContent(
            weather(temperature = 61.0, cityName = "Tokyo", conditionMain = "Rain", description = "light rain"),
            null,
            walk = null,
        )
        assertEquals("61° and light rain in Tokyo", rainy)
        val (cloudy, _) = buildBriefingContent(
            weather(temperature = 85.6, cityName = "Tokyo", conditionMain = "Clouds", cloudPercent = 40),
            null,
            walk = null,
        )
        assertEquals("86° and partly cloudy in Tokyo", cloudy)
    }

    @Test
    fun `with a dog the briefing says when to walk`() {
        fun body(walk: WalkAdvice) = buildBriefingContent(weather(tempMin = 50.0, tempMax = 70.0), null, walk).second

        assertEquals("High 70°, low 50°. Sunny says now's a great time for a walk.", body(WalkAdvice(WalkRating.GREAT, null)))
        assertEquals("High 70°, low 50°. Sunny's picked 1 PM for a walk.", body(WalkAdvice(WalkRating.OKAY, "1 PM")))
        assertEquals(
            "High 70°, low 50°. Not a good day for walkies.",
            body(WalkAdvice(WalkRating.OKAY, "7 AM", bestTimeIsTomorrow = true)),
        )
        assertEquals("High 70°, low 50°. Not a good day for walkies.", body(WalkAdvice(WalkRating.POOR, null)))
    }

    @Test
    fun `with a dog the briefing says when bad air shortens the walk`() {
        fun body(
            walk: WalkAdvice,
            badAir: String,
        ) = buildBriefingContent(weather(tempMin = 50.0, tempMax = 70.0), null, walk, badAir).second

        assertEquals(
            "High 70°, low 50°. Sunny says now's a great time for a walk. Smoky air today, so keep it short.",
            body(WalkAdvice(WalkRating.GREAT, null), "Smoky air today"),
        )
        assertEquals(
            "High 70°, low 50°. Sunny's picked 1 PM for a walk. High ozone today, so keep it short.",
            body(WalkAdvice(WalkRating.OKAY, "1 PM"), "High ozone today"),
        )
        assertEquals(
            "High 70°, low 50°. Poor air today, so potty breaks only.",
            body(WalkAdvice(WalkRating.BAD_AIR, null), "Poor air today"),
        )
    }

    @Test
    fun `bad air is named by the worst walking hour left today`() {
        fun phrase(vararg slots: HourlySlot) = badAirPhrase(listOf(forecast(slots = slots.toList())), 0, NOW, PetWeatherThresholds.DEFAULT)

        assertEquals("Smoky air today", phrase(slot(9, rain = 0, air = AirQuality.Pollutant.FINE_PARTICLES)))
        assertEquals(
            "High ozone today",
            phrase(
                slot(9, rain = 0, air = AirQuality.Pollutant.FINE_PARTICLES, index = 155),
                slot(15, rain = 0, air = AirQuality.Pollutant.OZONE, index = 210),
            ),
        )
        assertEquals("Poor air today", phrase(slot(9, rain = 0, air = AirQuality.Pollutant.NITROGEN_DIOXIDE)))
        // Clean air, an hour already over, and a night hour don't count.
        assertNull(phrase(slot(9, rain = 0)))
        assertNull(phrase(slot(7, rain = 0, air = AirQuality.Pollutant.FINE_PARTICLES)))
        assertNull(phrase(slot(23, rain = 0, air = AirQuality.Pollutant.FINE_PARTICLES)))
    }

    // --- walk time planning ---

    @Test
    fun `the morning run plans the walk nudge for the day's best time`() =
        runTest {
            var planned: Long? = null
            logic(
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(listOf(forecast(slots = wetMorningDryLater()))) },
                isWalkTimeEnabled = true,
                hasDog = true,
                scheduleWalkTime = { planned = it },
            ).doWork()

            assertEquals(DAY_START + 11 * HOUR, planned)
        }

    @Test
    fun `Remote Config can switch the walk nudge off for everyone`() =
        runTest {
            var planned: Long? = null
            logic(
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(listOf(forecast(slots = wetMorningDryLater()))) },
                isWalkTimeEnabled = true,
                isWalkTimeRemotelyEnabled = false,
                hasDog = true,
                scheduleWalkTime = { planned = it },
            ).doWork()
            assertEquals(null, planned)
        }

    @Test
    fun `no walk nudge without a dog, even with its switch on`() =
        runTest {
            var planned: Long? = null
            logic(
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(listOf(forecast(slots = wetMorningDryLater()))) },
                isWalkTimeEnabled = true,
                hasDog = false,
                scheduleWalkTime = { planned = it },
            ).doWork()
            assertEquals(null, planned)

            val dry = listOf(forecast(slots = (8..12).map { slot(it, rain = 0) }))
            logic(
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(dry) },
                isWalkTimeEnabled = true,
                isBriefingEnabled = false,
                hasDog = false,
                scheduleWalkTime = { planned = it },
            ).doWork()
            assertEquals(null, planned)
        }

    @Test
    fun `no walk nudge when its switch is off`() =
        runTest {
            var planned: Long? = null
            logic(
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(listOf(forecast(slots = wetMorningDryLater()))) },
                isWalkTimeEnabled = false,
                scheduleWalkTime = { planned = it },
            ).doWork()

            assertEquals(null, planned)
        }

    @Test
    fun `when now is the time, the briefing says so, or without it the nudge goes now`() =
        runTest {
            val dry = listOf(forecast(slots = (8..12).map { slot(it, rain = 0) }))
            var planned: Long? = null
            logic(fetchForecast = {
                _,
                _,
                _,
                _,
                ->
                kotlin.Result.success(dry)
            }, isWalkTimeEnabled = true, hasDog = true, scheduleWalkTime = { planned = it })
                .doWork()
            assertEquals(null, planned)

            logic(
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(dry) },
                isWalkTimeEnabled = true,
                isBriefingEnabled = false,
                hasDog = true,
                scheduleWalkTime = { planned = it },
            ).doWork()
            assertEquals(NOW, planned)
        }

    @Test
    fun `the walk nudge goes when now is still a great time, and not when it's raining`() =
        runTest {
            val posted = mutableListOf<WeatherNotification>()

            fun walkLogic(slots: List<HourlySlot>) =
                WalkTimeLogic(
                    getSavedLocations = { listOf(london) },
                    getUnits = { WeatherUnits.IMPERIAL },
                    fetchCurrentWeather = { _, _, _, _ -> kotlin.Result.success(weather(cityName = "London")) },
                    fetchForecast = { _, _, _, _ -> kotlin.Result.success(listOf(forecast(slots = slots))) },
                    hasNotificationPermission = { true },
                    postNotification = { posted += it },
                    isWalkTimeEnabled = { true },
                    nowEpochSeconds = { NOW },
                )

            walkLogic((8..12).map { slot(it, rain = 90) }).doWork()
            assertTrue(posted.isEmpty())

            walkLogic((8..12).map { slot(it, rain = 0) }).doWork()
            assertEquals(NotificationKind.WALK_TIME, posted.single().kind)
            assertEquals("Time for a walk", posted.single().title)
            assertEquals("72° and clear in London. Sunny's waiting by the door.", posted.single().body)
        }

    // --- rain alerts ---

    private fun rainFrom(startQuarter: Int) =
        listOf(forecast(quarters = (0 until 12).map { QuarterHour(NOW + it * 900L, if (it >= startQuarter) 0.5 else 0.0) }))

    @Test
    fun `rain alerts come within the hour before rain starts`() {
        assertEquals(NOW + 3 * 900, rainAlertFor(rainFrom(3), NOW, 0, lastAlertStart = null)?.epochSeconds)
        // 75 minutes away: too early.
        assertEquals(null, rainAlertFor(rainFrom(5), NOW, 0, lastAlertStart = null))
    }

    @Test
    fun `rain alerts stay quiet at night and for rain already alerted about`() {
        // NOW is 8:30 AM UTC; at UTC-10 it's 10:30 PM.
        assertEquals(null, rainAlertFor(rainFrom(2), NOW, -10 * 3600, lastAlertStart = null))
        assertEquals(null, rainAlertFor(rainFrom(2), NOW, 0, lastAlertStart = NOW - HOUR))
        assertEquals(NOW + 2 * 900, rainAlertFor(rainFrom(2), NOW, 0, lastAlertStart = NOW - 5 * HOUR)?.epochSeconds)
    }

    @Test
    fun `rain alert text names the time and the city, and mentions the walk only with a dog`() {
        val rain = NextRain.Starting(DAY_START + 15 * HOUR + 900, snow = false)
        val (title, body) = buildRainAlertContent(rain, "Knoxville", 0, hasDog = true)
        assertEquals("Rain starting around 3:15 PM", title.replace('\u202F', ' '))
        assertEquals("Rain on the way in Knoxville. Walk now or wait it out.", body)
        assertEquals(
            "Rain on the way in Knoxville. Misty's staying in.",
            buildRainAlertContent(rain, "Knoxville", 0, hasDog = false).second,
        )
        assertTrue(
            buildRainAlertContent(NextRain.Starting(DAY_START, snow = true), "Oslo", 0, hasDog = true).first.startsWith("Snow starting"),
        )
    }

    @Test
    fun `today matching uses the city's utc offset`() =
        runTest {
            var capturedOffset: Int? = null
            val logic = NotificationWorkerLogic(
                getSavedLocations = { listOf(london) },
                getUnits = { WeatherUnits.IMPERIAL },
                fetchCurrentWeather = { _, _, _, _ ->
                    kotlin.Result.success(weather(utcOffsetSeconds = -21600))
                },
                fetchForecast = { _, _, _, _ -> kotlin.Result.success(listOf(forecast())) },
                hasNotificationPermission = { true },
                postNotification = {},
                todayLabel = { offset ->
                    capturedOffset = offset
                    TODAY_LABEL
                },
            )

            logic.doWork()

            // The city's offset (Mountain Daylight, UTC-6h) is used to resolve "today", not the device's.
            assertEquals(-21600, capturedOffset)
        }

    @Test
    fun `the notification carries the pets' mood and hemisphere for its picture`() =
        runTest {
            val posted = mutableListOf<WeatherNotification>()
            // 72 °F and clear in the daytime: the sunny scene.
            logic(postNotification = { posted += it }).doWork()
            logic(
                savedLocations = listOf(SavedLocation(label = "Sydney", latitude = -33.9, longitude = 151.2)),
                fetchCurrentWeather = { _, _, _, _ -> kotlin.Result.success(weather().copy(iconCode = "01n")) },
                postNotification = { posted += it },
            ).doWork()

            assertEquals(PetMood.SUNNY, posted[0].mood)
            assertEquals(false, posted[0].southernHemisphere)
            assertEquals(PetMood.NIGHT, posted[1].mood)
            assertEquals(true, posted[1].southernHemisphere)
        }

    @Test
    fun `my location asks for the place's own name, a saved city keeps its label`() =
        runTest {
            val labels = mutableListOf<String>()
            val fetch: suspend (WeatherUnits, String, Double?, Double?) -> kotlin.Result<CurrentWeather> =
                { _, label, _, _ ->
                    labels += label
                    kotlin.Result.success(weather())
                }
            val here = SavedLocation(label = "My Location", latitude = 35.96, longitude = -83.92, isCurrentLocation = true)
            logic(savedLocations = listOf(here), fetchCurrentWeather = fetch).doWork()
            logic(savedLocations = listOf(cityByName), fetchCurrentWeather = fetch).doWork()

            assertEquals(listOf("", "Austin, TX"), labels)
        }

    // --- helpers ---

    private fun logic(
        savedLocations: List<SavedLocation> = listOf(london),
        units: WeatherUnits = WeatherUnits.IMPERIAL,
        fetchCurrentWeather: suspend (WeatherUnits, String, Double?, Double?) -> kotlin.Result<CurrentWeather> =
            { _, _, _, _ -> kotlin.Result.success(weather()) },
        fetchForecast: suspend (WeatherUnits, String, Double?, Double?) -> kotlin.Result<List<DayForecast>> =
            { _, _, _, _ -> kotlin.Result.success(listOf(forecast())) },
        hasNotificationPermission: () -> Boolean = { true },
        postNotification: (WeatherNotification) -> Unit = {},
        todayLabel: String = TODAY_LABEL,
        isDailyWeatherNotificationEnabled: () -> Boolean = { true },
        isBriefingEnabled: Boolean = true,
        isWalkTimeEnabled: Boolean = false,
        isWalkTimeRemotelyEnabled: Boolean = true,
        hasDog: Boolean = false,
        scheduleWalkTime: (Long) -> Unit = {},
    ) = NotificationWorkerLogic(
        getSavedLocations = { savedLocations },
        getUnits = { units },
        fetchCurrentWeather = fetchCurrentWeather,
        fetchForecast = fetchForecast,
        hasNotificationPermission = hasNotificationPermission,
        postNotification = postNotification,
        isDailyWeatherNotificationEnabled = isDailyWeatherNotificationEnabled,
        todayLabel = { _ -> todayLabel },
        isBriefingEnabled = { isBriefingEnabled },
        isWalkTimeEnabled = { isWalkTimeEnabled },
        isWalkTimeRemotelyEnabled = { isWalkTimeRemotelyEnabled },
        hasDog = { hasDog },
        scheduleWalkTime = scheduleWalkTime,
        nowEpochSeconds = { NOW },
    )

    private companion object {
        const val TODAY_LABEL = "Mon, Jan 1"
        const val DAY_START = 1_704_067_200L // Mon, Jan 1 2024, 00:00 UTC
        const val HOUR = 3600L
        const val NOW = DAY_START + 8 * HOUR + 30 * 60 // 8:30 AM

        fun slot(
            hour: Int,
            rain: Int,
            air: AirQuality.Pollutant? = null,
            index: Int = 160,
        ) = HourlySlot(
            timeLabel = if (hour < 12) {
                "$hour AM"
            } else if (hour == 12) {
                "12 PM"
            } else {
                "${hour - 12} PM"
            },
            iconCode = "01d",
            description = "clear",
            temperature = 70.0,
            feelsLike = 70.0,
            windSpeed = 3.0,
            windDeg = 0,
            humidity = 50,
            pressure = 1013,
            units = WeatherUnits.IMPERIAL,
            precipitationChance = rain,
            localHour = hour,
            epochSeconds = DAY_START + hour * HOUR,
            // Unhealthy on the US index by default, the default bad-air band.
            airQuality = air?.let { AirQuality(index, AirQuality.Scale.US, it) },
        )

        /** Rain until 11 AM, then dry. */
        fun wetMorningDryLater() = (8..14).map { slot(it, rain = if (it < 11) 90 else 0) }

        val london = SavedLocation(label = "My Location", latitude = 51.5, longitude = -0.1)
        val cityByName = SavedLocation(label = "Austin, TX", latitude = null, longitude = null)

        fun weather(
            cityName: String = "London",
            temperature: Double = 72.0,
            tempMin: Double = 60.0,
            tempMax: Double = 80.0,
            description: String = "clear sky",
            utcOffsetSeconds: Int = 0,
            conditionMain: String = "Clear",
            cloudPercent: Int = 0,
        ) = CurrentWeather(
            cityName = cityName,
            conditionMain = conditionMain,
            description = description,
            iconCode = "01d",
            temperature = temperature,
            feelsLike = temperature - 2,
            tempMin = tempMin,
            tempMax = tempMax,
            humidityPercent = 50,
            pressureHpa = 1013,
            windSpeed = 5.0,
            windDeg = 180,
            visibilityMeters = 10000,
            cloudPercent = cloudPercent,
            units = WeatherUnits.IMPERIAL,
            utcOffsetSeconds = utcOffsetSeconds,
        )

        fun forecast(
            tempMin: Double = 55.0,
            tempMax: Double = 85.0,
            dateLabel: String = TODAY_LABEL,
            rainChance: Int = 0,
            slots: List<HourlySlot> = emptyList(),
            quarters: List<QuarterHour> = emptyList(),
        ) = DayForecast(
            dateLabel = dateLabel,
            conditionMain = "Clear",
            description = "clear sky",
            iconCode = "01d",
            temperature = 70.0,
            feelsLike = 68.0,
            tempMin = tempMin,
            tempMax = tempMax,
            units = WeatherUnits.IMPERIAL,
            hourlySlots = slots,
            precipitationChance = rainChance,
            quarterHours = quarters,
        )
    }
}
