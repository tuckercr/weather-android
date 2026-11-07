package com.fangjet.weather.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import com.fangjet.weather.R
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.RainOutlookSettings
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.model.LoadingState
import com.fangjet.weather.ui.theme.CatsDogsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class CurrentWeatherScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun emptyStateShowsPromptAndAddCityInvokesCallback() {
        var addClicks = 0
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = emptyList(),
                    activeIndex = 0,
                    weatherState = LoadingState.Idle,
                    onTabSelected = {},
                    onAddCityClick = { addClicks++ },
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.empty_cities_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_add_city)).performClick()

        assertEquals(1, addClicks)
    }

    @Test
    fun errorStateShowsMessageAndRetryInvokesCallback() {
        var retries = 0
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Error("offline", canRetry = true),
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.error_title_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.error_offline)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.action_try_again)).performClick()

        assertEquals(1, retries)
    }

    @Test
    fun staleCachedWeatherShowsWhenItIsFromAndWhy() {
        // An hour ago, in a city where it's around midday now, so the reading is always from today
        // (one from before midnight shows as "yesterday at").
        val now = Instant.now().epochSecond
        val offset = middayOffset(now)
        val observedAt = now - 3_600
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(
                        weather().copy(observedAtEpochSeconds = observedAt, utcOffsetSeconds = offset),
                    ),
                    staleErrorKey = "offline",
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Showing weather from ${localTime(observedAt, offset)}. " + string(R.string.error_offline) + " " +
                    string(R.string.error_pull_to_retry),
            ).assertIsDisplayed()
    }

    @Test
    fun weatherPastTheCutoffGivesWayToTheForecastForThisHour() {
        val now = Instant.now().epochSecond
        val observedAt = yesterdayNoonUtc()
        val thisHour = hourlySlot(timeLabel = "Now").copy(epochSeconds = now - 600, temperature = 21.0)
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(
                        weather().copy(observedAtEpochSeconds = observedAt, utcOffsetSeconds = 0),
                    ),
                    forecastState = LoadingState.Success(listOf(dayForecast("Today", listOf(thisHour)))),
                    staleErrorKey = "offline",
                    radarEnabled = false,
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule
            .onNodeWithText(
                "Showing the forecast from yesterday at ${utcTime(observedAt)}. " + string(R.string.error_offline) + " " +
                    string(R.string.error_pull_to_retry),
            ).assertIsDisplayed()
        // Just the degree sign, by default (Remote Config temperature_units_shown), so the hourly
        // strip's hour reads the same: the hero's comes first.
        composeRule.onAllNodesWithText("21°").onFirst().assertIsDisplayed()
    }

    @Test
    fun weatherPastTheCutoffWithNoForecastShowsTheErrorAndWhenItWasLastUpdated() {
        val observedAt = yesterdayNoonUtc()
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(
                        weather().copy(observedAtEpochSeconds = observedAt, utcOffsetSeconds = 0),
                    ),
                    staleErrorKey = "offline",
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.error_title_offline)).assertIsDisplayed()
        composeRule.onNodeWithText("Last updated yesterday at ${utcTime(observedAt)}.").performScrollTo().assertIsDisplayed()
    }

    // Always "yesterday" in UTC and always past the 3-hour cutoff, whatever the time of day.
    private fun yesterdayNoonUtc(): Long =
        LocalDate
            .now(ZoneOffset.UTC)
            .minusDays(1)
            .atTime(12, 0)
            .toEpochSecond(ZoneOffset.UTC)

    // A whole-hour UTC offset at which it's between 11 AM and 1 PM at [epochSeconds].
    private fun middayOffset(epochSeconds: Long): Int = ((12 * 3_600 - Math.floorMod(epochSeconds, 86_400L)) / 3_600 * 3_600).toInt()

    private fun utcTime(epochSeconds: Long): String = localTime(epochSeconds, 0)

    private fun localTime(
        epochSeconds: Long,
        offsetSeconds: Int,
    ): String =
        Instant
            .ofEpochSecond(epochSeconds)
            .atOffset(ZoneOffset.ofTotalSeconds(offsetSeconds))
            .format(DateTimeFormatter.ofPattern("h:mm a"))

    @Test
    fun forecastErrorIsSurfacedInUpcomingSectionWithRetry() {
        var retries = 0
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    // Null coordinates keep RadarCard in its static branch (no animation loop).
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(weather()),
                    forecastState = LoadingState.Error("offline", canRetry = true),
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = { retries++ },
                )
            }
        }

        composeRule.onNodeWithText(string(R.string.action_retry)).performScrollTo().performClick()

        assertEquals(1, retries)
    }

    @Test
    fun forecastSuccessRendersUpcomingDay() {
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(weather()),
                    forecastState = LoadingState.Success(listOf(dayForecast(dateLabel = "Mon, Jan 1"))),
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("Mon, Jan 1").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun hourlyStripRendersWhenForecastHasHourlySlots() {
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(weather()),
                    forecastState = LoadingState.Success(
                        listOf(
                            dayForecast(
                                dateLabel = "Mon, Jan 1",
                                hourlySlots = listOf(hourlySlot(timeLabel = "9 AM")),
                            ),
                        ),
                    ),
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("9 AM").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun precipitationChanceIsShownInHourlyStrip() {
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(weather()),
                    forecastState = LoadingState.Success(
                        listOf(
                            dayForecast(
                                dateLabel = "Mon, Jan 1",
                                hourlySlots = listOf(
                                    hourlySlot(timeLabel = "9 AM", precipitationChance = 80),
                                ),
                            ),
                        ),
                    ),
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }

        composeRule.onNodeWithText("80%").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rainOutlookShowsWhenAnHourReachesTheChance() {
        setRainOutlookContent(RainOutlookSettings())

        composeRule.onNodeWithText(string(R.string.rain_outlook_likely_now)).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun rainOutlookIsHiddenWhenSwitchedOff() {
        setRainOutlookContent(RainOutlookSettings(enabled = false))

        composeRule.onAllNodesWithText(string(R.string.rain_outlook_likely_now)).assertCountEquals(0)
    }

    private fun setRainOutlookContent(settings: RainOutlookSettings) {
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(weather()),
                    forecastState = LoadingState.Success(
                        listOf(
                            dayForecast(
                                dateLabel = "Mon, Jan 1",
                                hourlySlots = listOf(hourlySlot(timeLabel = "9 AM", precipitationChance = 80)),
                            ),
                        ),
                    ),
                    radarEnabled = false,
                    rainOutlookSettings = settings,
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }
    }

    @Test
    fun radarSectionIsShownByDefault() {
        setRadarContent(radarEnabled = true)

        // Null coordinates: the card's own message stands for it.
        composeRule.onNodeWithText(radarUnavailable()).performScrollTo().assertIsDisplayed()
    }

    @Test
    fun radarSectionIsHiddenWhenRadarIsDisabled() {
        setRadarContent(radarEnabled = false)

        composeRule.onAllNodesWithText(radarUnavailable()).assertCountEquals(0)
    }

    private fun radarUnavailable() = composeRule.activity.getString(R.string.radar_unavailable_coords)

    private fun setRadarContent(radarEnabled: Boolean) {
        composeRule.setContent {
            CatsDogsTheme {
                CurrentWeatherScreen(
                    locations = listOf(london),
                    activeIndex = 0,
                    weatherState = LoadingState.Success(weather()),
                    radarEnabled = radarEnabled,
                    onTabSelected = {},
                    onAddCityClick = {},
                    onRetry = {},
                )
            }
        }
    }

    private companion object {
        // No coordinates: keeps the radar tile static so the screen reaches idle for assertions.
        val london = SavedLocation(label = "London, GB", latitude = null, longitude = null)

        fun weather() =
            CurrentWeather(
                cityName = "London",
                conditionMain = "Clouds",
                description = "Broken clouds",
                iconCode = "04d",
                temperature = 15.0,
                feelsLike = 14.2,
                tempMin = 12.0,
                tempMax = 17.5,
                humidityPercent = 72,
                pressureHpa = 1012,
                windSpeed = 4.1,
                windDeg = 225,
                visibilityMeters = 9000,
                cloudPercent = 75,
                units = WeatherUnits.METRIC,
            )

        fun dayForecast(
            dateLabel: String,
            hourlySlots: List<HourlySlot> = emptyList(),
        ) = DayForecast(
            dateLabel = dateLabel,
            conditionMain = "Clouds",
            description = "cloudy",
            iconCode = "02d",
            temperature = 14.0,
            feelsLike = 13.0,
            tempMin = 10.0,
            tempMax = 16.0,
            units = WeatherUnits.METRIC,
            hourlySlots = hourlySlots,
        )

        fun hourlySlot(
            timeLabel: String,
            precipitationChance: Int = 0,
        ) = HourlySlot(
            timeLabel = timeLabel,
            iconCode = "02d",
            description = "Cloudy",
            temperature = 14.0,
            feelsLike = 13.0,
            windSpeed = 3.0,
            windDeg = 180,
            humidity = 60,
            pressure = 1012,
            units = WeatherUnits.METRIC,
            precipitationChance = precipitationChance,
        )
    }
}
