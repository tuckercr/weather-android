package com.fangjet.weather.data

import com.fangjet.weather.analytics.CityAdded
import com.fangjet.weather.domain.SavedLocation
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf

/** A [CityRepository] over [locations] in storage, with no geocoding results, for the view model tests. */
internal fun testCityRepository(
    locations: List<SavedLocation> = emptyList(),
    activeIndex: Int = 0,
    onCityAdded: (CityAdded) -> Unit = {},
): CityRepository {
    val preferences = mockk<PreferencesRepository>(relaxed = true)
    every { preferences.savedLocations } returns flowOf(locations)
    every { preferences.activeLocationIndex } returns flowOf(activeIndex)
    every { preferences.lastCity } returns flowOf(null)
    val geocoding = mockk<GeocodingRepository>(relaxed = true)
    coEvery { geocoding.searchCities(any()) } returns Result.success(emptyList())
    return CityRepository(preferences, geocoding, onCityAdded = onCityAdded)
}

/** Where the phone is, faked: [here] while [granted], counting the reads. */
internal class FakePosition : DevicePositioning {
    var granted = true
    var here: SavedLocation? = null
    var reads = 0

    override fun hasLocationPermission() = granted

    override suspend fun knownPosition(): SavedLocation? = read()

    override suspend fun currentPosition(): SavedLocation? = read()

    private fun read(): SavedLocation? {
        reads++
        return here.takeIf { granted }
    }
}
