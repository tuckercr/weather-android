package com.fangjet.weather.data

import com.fangjet.weather.domain.SavedLocation
import org.junit.Assert.assertEquals
import org.junit.Test

/** The one rule the screen, the background refresh and the notifications fetch a saved location by. */
class LocationWeatherTest {
    @Test
    fun `a saved city keeps its label and goes by its coordinates, with its name to fall back on`() {
        assertEquals(
            WeatherRequest("London, GB", "London, GB", 51.5, -0.1),
            SavedLocation(label = "London, GB", latitude = 51.5, longitude = -0.1).weatherRequest,
        )
    }

    @Test
    fun `a city saved by name only goes by its name`() {
        assertEquals(
            WeatherRequest("Springfield", "Springfield", null, null),
            SavedLocation(label = "Springfield", latitude = null, longitude = null).weatherRequest,
        )
    }

    @Test
    fun `my location has an empty label, so the place is named by the phone`() {
        val here = SavedLocation(label = "My Location", latitude = 37.4, longitude = -122.1, isCurrentLocation = true)
        assertEquals(WeatherRequest("", "My Location", 37.4, -122.1), here.weatherRequest)
    }
}
