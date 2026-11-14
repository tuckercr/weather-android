package com.fangjet.weather.analytics

import com.fangjet.weather.data.AppPermissions
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.worker.NotificationKind
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsUserPropertiesTest {

    private val myLocation = SavedLocation("My Location", 40.0, -83.0, isCurrentLocation = true)
    private val london = SavedLocation("London, England, GB", 51.5, -0.1)

    private fun inputs(
        settings: UserPropertySettings = UserPropertySettings(),
        briefingRemotelyEnabled: Boolean = true,
        walkTimeRemotelyEnabled: Boolean = true,
        notificationsAllowed: Boolean = true,
        location: Boolean = true,
        backgroundLocation: Boolean = false,
        units: WeatherUnits = WeatherUnits.METRIC,
    ) = UserPropertyInputs(
        settings = settings,
        briefingRemotelyEnabled = briefingRemotelyEnabled,
        walkTimeRemotelyEnabled = walkTimeRemotelyEnabled,
        notificationsAllowed = notificationsAllowed,
        location = location,
        backgroundLocation = backgroundLocation,
        units = units,
    )

    private fun properties(inputs: UserPropertyInputs) = AnalyticsUserProperties.of(inputs)

    @Test
    fun `every property, for a typical setup`() {
        assertEquals(
            mapOf(
                "has_dog" to "yes",
                "briefing_on" to "yes",
                "walk_time_on" to "yes",
                "location_access" to "while_using",
                "follow_location" to "no",
                "units" to "metric",
                "city_count" to "1",
            ),
            properties(inputs(UserPropertySettings(locations = listOf(myLocation)))),
        )
    }

    @Test
    fun `briefings and walk nudges are on only when they'd arrive`() {
        fun on(inputs: UserPropertyInputs) = properties(inputs).let { it["briefing_on"] to it["walk_time_on"] }

        assertEquals("yes" to "yes", on(inputs()))
        assertEquals("no" to "no", on(inputs(notificationsAllowed = false)))
        assertEquals("no" to "yes", on(inputs(UserPropertySettings(briefingSwitch = false))))
        assertEquals("no" to "yes", on(inputs(briefingRemotelyEnabled = false)))
        assertEquals("yes" to "no", on(inputs(UserPropertySettings(walkTimeSwitch = false))))
        assertEquals("yes" to "no", on(inputs(walkTimeRemotelyEnabled = false)))
        // Walk time needs a dog, whatever its own switch says.
        assertEquals("yes" to "no", on(inputs(UserPropertySettings(hasDog = false))))
    }

    @Test
    fun `location access and following`() {
        fun access(
            location: Boolean,
            background: Boolean,
        ) = properties(inputs(location = location, backgroundLocation = background))["location_access"]
        assertEquals("none", access(location = false, background = false))
        assertEquals("while_using", access(location = true, background = false))
        assertEquals("always", access(location = true, background = true))

        fun follow(
            on: Boolean,
            background: Boolean,
            locations: List<SavedLocation>,
        ) = properties(
            inputs(UserPropertySettings(followMyLocation = on, locations = locations), backgroundLocation = background),
        )["follow_location"]
        assertEquals("yes", follow(on = true, background = true, locations = listOf(myLocation)))
        // Not effective without "Allow all the time", or without a My Location to move.
        assertEquals("no", follow(on = true, background = false, locations = listOf(myLocation)))
        assertEquals("no", follow(on = true, background = true, locations = listOf(london)))
        assertEquals("no", follow(on = false, background = true, locations = listOf(myLocation)))
    }

    @Test
    fun `units are the ones used, and cities are counted in bands`() {
        assertEquals("imperial", properties(inputs(units = WeatherUnits.IMPERIAL))["units"])
        assertEquals("metric", properties(inputs(units = WeatherUnits.METRIC))["units"])
        assertEquals(listOf("0", "1", "2-3", "2-3", "4+", "4+"), listOf(0, 1, 2, 3, 4, 12).map(AnalyticsUserProperties::cityCount))
    }

    // --- When they're set ---

    private class FakePermissions(
        var notifications: Boolean = true,
        var location: Boolean = false,
    ) : AppPermissions {
        override fun location() = location

        override fun backgroundLocation() = false

        override fun notifications() = notifications

        override fun backgroundLocationRefusals() = 0

        override fun noteBackgroundLocationAnswer(granted: Boolean) = Unit
    }

    @Test
    fun `properties are set once the gates are applied, and again on changes and returns to the app`() =
        runTest {
            val analytics = mockk<AnalyticsRepository>(relaxed = true)
            val collectionEnabled = MutableStateFlow<Boolean?>(null)
            val settings = MutableStateFlow(UserPropertySettings())
            val permissions = FakePermissions()
            val updater = AnalyticsUserPropertiesUpdater(
                settings = settings,
                collectionEnabled = collectionEnabled,
                remoteConfigActivations = MutableStateFlow(0),
                remotelyEnabled = { it != NotificationKind.RAIN },
                permissions = permissions,
                resolveUnits = { if (it == UnitOverride.IMPERIAL) WeatherUnits.IMPERIAL else WeatherUnits.METRIC },
                analytics = analytics,
            )
            updater.start(backgroundScope)
            runCurrent()
            // Not before the gates have been applied, nor while they're closed.
            verify(exactly = 0) { analytics.setUserProperties(any()) }
            collectionEnabled.value = false
            runCurrent()
            verify(exactly = 0) { analytics.setUserProperties(any()) }

            // "Share usage statistics" turned on.
            collectionEnabled.value = true
            runCurrent()
            verify(exactly = 1) { analytics.setUserProperties(match { it["units"] == "metric" && it["location_access"] == "none" }) }

            settings.value = UserPropertySettings(unitOverride = UnitOverride.IMPERIAL)
            runCurrent()
            verify(exactly = 1) { analytics.setUserProperties(match { it["units"] == "imperial" }) }

            // Allowed in system settings, then back in the app.
            permissions.location = true
            updater.refresh()
            runCurrent()
            verify(exactly = 1) { analytics.setUserProperties(match { it["location_access"] == "while_using" }) }
        }
}
