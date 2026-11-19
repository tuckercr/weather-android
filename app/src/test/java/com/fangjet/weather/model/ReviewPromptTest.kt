package com.fangjet.weather.model

import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.ReviewPromptHistory
import com.fangjet.weather.domain.ReviewPromptMoment
import com.fangjet.weather.domain.ReviewPromptPolicy
import com.fangjet.weather.domain.ReviewPromptSettings
import com.fangjet.weather.domain.ReviewPromptVisit
import com.fangjet.weather.domain.WeatherUnits
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.time.ZoneOffset

/**
 * The ratings prompt: the rule, its Remote Config values, what the weather screen counts as a happy
 * moment, and the view model's timing. Same cases as iOS's `ReviewPromptTests`.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class ReviewPromptTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val zone = ZoneOffset.UTC
    private val hour = 3_600L

    // Mon 1 Jan 2024, 00:00 UTC, in epoch seconds.
    private val dayStart = 1_704_067_200L
    private val installed = (dayStart + 10 * hour) * 1_000
    private val on = ReviewPromptSettings.DEFAULT.copy(enabled = true)

    /** Epoch milliseconds, [days] after the 1st at [hours] o'clock. */
    private fun date(
        days: Double,
        hours: Int = 12,
    ): Long = (dayStart + hours * hour) * 1_000 + (days * 86_400_000).toLong()

    private fun shouldAsk(
        moment: ReviewPromptMoment = ReviewPromptMoment.GREAT_WALK,
        history: ReviewPromptHistory = ReviewPromptHistory(activeDays = 5),
        settings: ReviewPromptSettings = on,
        now: Long = date(7.0),
        version: String = "1.1",
        visit: ReviewPromptVisit = ReviewPromptVisit(),
    ): Boolean = ReviewPromptPolicy.shouldAsk(moment, history, settings, installed, now, version, visit, zone)

    // --- The rule ---

    @Test
    fun `asks someone who keeps coming back at a happy moment`() {
        assertTrue(shouldAsk())
        assertTrue(shouldAsk(moment = ReviewPromptMoment.PLEASANT_WEATHER))
    }

    @Test
    fun `off by default and when switched off`() {
        assertFalse(ReviewPromptSettings.DEFAULT.enabled)
        assertFalse(shouldAsk(settings = ReviewPromptSettings.DEFAULT))
    }

    @Test
    fun `waits for the days installed by the calendar`() {
        // Installed at 10 AM on the 1st: the 7th day after starts at midnight on the 8th.
        assertFalse(shouldAsk(now = date(6.0, hours = 23)))
        assertTrue(shouldAsk(now = date(7.0, hours = 0)))
    }

    @Test
    fun `never on the day of install even with no wait`() {
        val settings = on.copy(minDaysInstalled = 0)
        assertFalse(shouldAsk(settings = settings, now = date(0.0, hours = 23)))
        assertTrue(shouldAsk(settings = settings, now = date(1.0, hours = 0)))
    }

    @Test
    fun `waits for the active days`() {
        assertFalse(shouldAsk(history = ReviewPromptHistory(activeDays = 4)))
        assertTrue(shouldAsk(history = ReviewPromptHistory(activeDays = 5)))
    }

    @Test
    fun `only the moments switched on`() {
        val settings = on.copy(moments = setOf(ReviewPromptMoment.PLEASANT_WEATHER))
        assertFalse(shouldAsk(moment = ReviewPromptMoment.GREAT_WALK, settings = settings))
        assertTrue(shouldAsk(moment = ReviewPromptMoment.PLEASANT_WEATHER, settings = settings))
        assertFalse(shouldAsk(moment = ReviewPromptMoment.PLEASANT_WEATHER, settings = settings.copy(moments = emptySet())))
    }

    @Test
    fun `not after an interruption or from a notification`() {
        assertFalse(shouldAsk(visit = ReviewPromptVisit(interrupted = true)))
        assertFalse(shouldAsk(visit = ReviewPromptVisit(fromNotification = true)))
        assertTrue(shouldAsk(settings = on.copy(onNotificationOpen = true), visit = ReviewPromptVisit(fromNotification = true)))
    }

    @Test
    fun `once per version`() {
        val askedInThisVersion =
            ReviewPromptHistory(activeDays = 50, requests = 1, lastRequestAt = date(-300.0), lastRequestVersion = "1.1")
        assertFalse(shouldAsk(history = askedInThisVersion, now = date(7.0)))
        assertTrue(shouldAsk(history = askedInThisVersion, version = "1.2"))
        assertTrue(shouldAsk(history = askedInThisVersion, settings = on.copy(oncePerVersion = false)))
    }

    @Test
    fun `the cooldown between asks`() {
        val asked = ReviewPromptHistory(activeDays = 50, requests = 1, lastRequestAt = date(7.0), lastRequestVersion = "1.0")
        assertFalse(shouldAsk(history = asked, now = date(126.0)))
        assertTrue(shouldAsk(history = asked, now = date(127.0)))
    }

    @Test
    fun `the limit on asks in all`() {
        val three = ReviewPromptHistory(activeDays = 50, requests = 3, lastRequestAt = date(-400.0), lastRequestVersion = "1.0")
        assertFalse(shouldAsk(history = three))
        assertTrue(shouldAsk(history = three, settings = on.copy(maxRequests = 0)))
    }

    @Test
    fun `counts each calendar day once`() {
        var history = ReviewPromptHistory()
        history = ReviewPromptPolicy.recordingActiveDay(history, date(0.0, hours = 8), zone)
        history = ReviewPromptPolicy.recordingActiveDay(history, date(0.0, hours = 23), zone)
        history = ReviewPromptPolicy.recordingActiveDay(history, date(1.0, hours = 0), zone)
        history = ReviewPromptPolicy.recordingActiveDay(history, date(4.0), zone)
        assertEquals(3, history.activeDays)
        assertEquals(date(4.0, hours = 0), history.lastActiveDay)
    }

    // --- Remote Config values ---

    @Test
    fun `Remote Config values are validated`() {
        val read = RemoteConfigRepository.reviewPromptSettings(
            enabled = true,
            minDaysInstalled = 3.0,
            minActiveDays = 2.0,
            moments = "pleasant_weather, nonsense",
            delaySeconds = 5.0,
            oncePerVersion = false,
            cooldownDays = 60.0,
            maxRequests = 0.0,
            onNotificationOpen = true,
        )
        assertEquals(
            ReviewPromptSettings(
                enabled = true,
                minDaysInstalled = 3,
                minActiveDays = 2,
                moments = setOf(ReviewPromptMoment.PLEASANT_WEATHER),
                delaySeconds = 5,
                oncePerVersion = false,
                cooldownDays = 60,
                maxRequests = 0,
                onNotificationOpen = true,
            ),
            read,
        )

        val bad = RemoteConfigRepository.reviewPromptSettings(
            enabled = true,
            minDaysInstalled = 0.0,
            minActiveDays = 2.5,
            moments = null,
            delaySeconds = 60.0,
            oncePerVersion = true,
            cooldownDays = 1.0,
            maxRequests = -1.0,
            onNotificationOpen = false,
        )
        assertEquals(on, bad)

        val none = RemoteConfigRepository.reviewPromptSettings(
            enabled = true,
            minDaysInstalled = null,
            minActiveDays = null,
            moments = "",
            delaySeconds = null,
            oncePerVersion = true,
            cooldownDays = null,
            maxRequests = null,
            onNotificationOpen = false,
        )
        assertEquals(emptySet<ReviewPromptMoment>(), none.moments)
    }

    @Test
    fun `moment names are the same as iOS's`() {
        assertEquals(listOf("great_walk", "pleasant_weather"), ReviewPromptMoment.entries.map { it.key })
    }

    // --- The weather screen's moments ---

    @Test
    fun `a great walk for dog owners`() {
        assertEquals(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK), screen(forecast = goodDay()))
    }

    @Test
    fun `pleasant weather without a dog`() {
        assertEquals(
            ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.PLEASANT_WEATHER),
            screen(forecast = goodDay(), hasDog = false),
        )
        assertEquals(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.PLEASANT_WEATHER), screen())
    }

    @Test
    fun `no moment in storms, at night or with old weather`() {
        val noMoment = ReviewPromptScreen(visible = true)
        assertEquals(noMoment, screen(icon = "11d", condition = "Thunderstorm"))
        assertEquals(noMoment, screen(icon = "01n", now = dayStart + 22 * hour))
        assertEquals(noMoment, screen(observedAt = dayStart + 11 * hour, staleErrorKey = "offline"))
        assertEquals(noMoment, screen(isRefreshing = true))
        assertEquals(noMoment, screen(forecastError = true))
    }

    @Test
    fun `nothing when the screen is covered`() {
        assertEquals(ReviewPromptScreen.HIDDEN, screen(inFront = false))
        assertEquals(ReviewPromptScreen.HIDDEN, ReviewPromptScreen.of(WeatherScreen.Loading, isRefreshing = false, inFront = true))
    }

    // --- The view model ---

    @Test
    fun `asks after the delay and records it`() =
        runTest {
            val model = harness()
            model.subject.screenChanged(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK))
            advanceBy(2_999)
            assertEquals(emptyList<ReviewPromptMoment>(), model.requests)
            advanceBy(1)
            assertEquals(listOf(ReviewPromptMoment.GREAT_WALK), model.requests)
            assertEquals(listOf(ReviewPromptMoment.GREAT_WALK to 1), model.logged)
            assertEquals(1, model.history.requests)
            assertEquals("1.1", model.history.lastRequestVersion)
            // Once per version.
            model.subject.screenChanged(ReviewPromptScreen.HIDDEN)
            model.subject.screenChanged(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.PLEASANT_WEATHER))
            settle()
            assertEquals(1, model.requests.size)
        }

    @Test
    fun `not if the screen changes during the delay`() =
        runTest {
            val model = harness()
            model.subject.screenChanged(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK))
            advanceBy(1_000)
            model.subject.screenChanged(ReviewPromptScreen(visible = true))
            settle()
            assertEquals(emptyList<ReviewPromptMoment>(), model.requests)
        }

    @Test
    fun `counts the days the weather was shown`() =
        runTest {
            val model = harness(activeDays = 0)
            model.subject.screenChanged(ReviewPromptScreen(visible = true))
            settle()
            model.now = date(8.0)
            model.subject.screenChanged(ReviewPromptScreen.HIDDEN)
            model.subject.screenChanged(ReviewPromptScreen(visible = true))
            settle()
            assertEquals(2, model.history.activeDays)
        }

    @Test
    fun `an interruption lasts until the next visit`() =
        runTest {
            val model = harness()
            model.subject.becameInactive()
            model.subject.screenChanged(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK))
            settle()
            assertEquals(0, model.requests.size)

            model.subject.enteredBackground()
            model.now = date(7.0, hours = 14)
            model.subject.becameActive()
            settle()
            assertEquals(1, model.requests.size)
        }

    @Test
    fun `not in a visit from a notification`() =
        runTest {
            val model = harness()
            model.subject.notificationOpened()
            model.subject.screenChanged(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK))
            settle()
            assertEquals(0, model.requests.size)
        }

    @Test
    fun `forced in a debug build asks once per launch whatever the counts and settings say`() =
        runTest {
            val model = harness(activeDays = 0, settings = ReviewPromptSettings.DEFAULT, forced = true)
            model.subject.screenChanged(ReviewPromptScreen(visible = true))
            settle()
            assertEquals(listOf(ReviewPromptMoment.PLEASANT_WEATHER), model.requests)
            model.subject.screenChanged(ReviewPromptScreen.HIDDEN)
            model.subject.screenChanged(ReviewPromptScreen(visible = true, moment = ReviewPromptMoment.GREAT_WALK))
            settle()
            assertEquals(1, model.requests.size)
        }

    // --- Helpers ---

    private fun screen(
        icon: String = "01d",
        condition: String = "Clear",
        observedAt: Long? = null,
        now: Long = dayStart + 12 * hour,
        forecast: List<DayForecast>? = null,
        forecastError: Boolean = false,
        staleErrorKey: String? = null,
        hasDog: Boolean = true,
        isRefreshing: Boolean = false,
        inFront: Boolean = true,
    ): ReviewPromptScreen {
        val reading = CurrentWeather(
            cityName = "Austin",
            conditionMain = condition,
            description = "clear sky",
            iconCode = icon,
            temperature = 12.0,
            feelsLike = 12.0,
            tempMin = 8.0,
            tempMax = 15.0,
            humidityPercent = 50,
            pressureHpa = 1013,
            windSpeed = 2.0,
            windDeg = 90,
            visibilityMeters = 10_000,
            cloudPercent = 5,
            units = WeatherUnits.METRIC,
            sunriseEpoch = dayStart + 7 * hour,
            sunsetEpoch = dayStart + 17 * hour,
            utcOffsetSeconds = 0,
            observedAtEpochSeconds = observedAt ?: now,
        )
        val forecastState: LoadingState<List<DayForecast>> = when {
            forecastError -> LoadingState.Error("network", canRetry = true)
            forecast != null -> LoadingState.Success(forecast)
            else -> LoadingState.Idle
        }
        val weatherScreen = weatherScreen(
            current = LoadingState.Success(reading),
            forecast = forecastState,
            staleErrorKey = staleErrorKey,
            nowEpochSeconds = now,
            hasDog = hasDog,
            config = WeatherScreenConfig(),
        )
        return ReviewPromptScreen.of(weatherScreen, isRefreshing = isRefreshing, inFront = inFront)
    }

    /** A clear, cool day, every hour fine for a walk. */
    private fun goodDay(): List<DayForecast> {
        val slots = (0 until 24).map { index ->
            HourlySlot(
                timeLabel = index.toString(),
                iconCode = "01d",
                description = "clear",
                temperature = 12.0,
                feelsLike = 12.0,
                windSpeed = 2.0,
                windDeg = 0,
                humidity = 50,
                pressure = 1013,
                units = WeatherUnits.METRIC,
                epochSeconds = dayStart + index * hour,
            )
        }
        return listOf(
            DayForecast(
                dateLabel = "Mon, Jan 1",
                conditionMain = "Clear",
                description = "clear",
                iconCode = "01d",
                temperature = 12.0,
                feelsLike = 12.0,
                tempMin = 8.0,
                tempMax = 15.0,
                units = WeatherUnits.METRIC,
                hourlySlots = slots,
            ),
        )
    }

    private fun advanceBy(millis: Long) {
        mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(millis)
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    }

    /** Lets the pending work, its delay included, run, and the activity's collector see what it sent. */
    private fun settle() {
        mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
        mainDispatcherRule.testDispatcher.scheduler.runCurrent()
    }

    /** The view model with a fake clock and in-memory preferences. */
    private inner class Harness(
        activeDays: Int,
        settings: ReviewPromptSettings,
        forced: Boolean,
        scope: TestScope,
    ) {
        var now = date(7.0)
        var history = ReviewPromptHistory(activeDays = activeDays)
        val logged = mutableListOf<Pair<ReviewPromptMoment, Int>>()
        val requests = mutableListOf<ReviewPromptMoment>()

        private val preferences = mockk<PreferencesRepository> {
            coEvery { reviewPromptHistory() } answers { history }
            coEvery { updateReviewPromptHistory(any()) } answers {
                history = firstArg<(ReviewPromptHistory) -> ReviewPromptHistory>()(history)
                history
            }
        }

        val subject = ReviewPromptViewModel(
            preferences = preferences,
            settings = { settings },
            version = "1.1",
            now = { now },
            zone = { zone },
            logRequest = { moment, number -> logged += moment to number },
            installedAtMillis = installed,
            forced = forced,
        )

        init {
            scope.backgroundScope.launch { subject.requests.collect { requests += it } }
        }
    }

    private fun TestScope.harness(
        activeDays: Int = 5,
        settings: ReviewPromptSettings = on,
        forced: Boolean = false,
    ) = Harness(activeDays, settings, forced, this)

    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
