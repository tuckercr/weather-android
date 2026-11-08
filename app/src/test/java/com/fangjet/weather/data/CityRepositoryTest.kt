package com.fangjet.weather.data

import com.fangjet.weather.domain.CitySuggestion
import com.fangjet.weather.domain.SavedLocation
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class CityRepositoryTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val austin = SavedLocation(label = "Austin, TX", latitude = 30.27, longitude = -97.74)
    private val denver = SavedLocation(label = "Denver, CO", latitude = 39.74, longitude = -104.98)

    // --- init ---

    @Test
    fun `init loads saved locations and active index from DataStore`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                activeIndex = 1,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austin, denver), viewModel.locations.value)
            assertEquals(1, viewModel.activeIndex.value)
            assertEquals(denver, viewModel.activeLocation.value)
        }

    @Test
    fun `locationsLoaded stays false until the saved cities have been read`() =
        runTest {
            val viewModel = viewModel(savedLocations = emptyList())

            // An empty list at this point says nothing about whether there are cities.
            assertEquals(false, viewModel.locationsLoaded.value)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(true, viewModel.locationsLoaded.value)
        }

    @Test
    fun `init with no saved locations migrates from legacy lastCity`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = emptyList(),
                lastCity = "London",
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(1, viewModel.locations.value.size)
            assertEquals(
                "London",
                viewModel.locations.value
                    .first()
                    .label,
            )
            assertEquals(1, persisted.size)
        }

    @Test
    fun `init with no saved locations and no lastCity leaves locations empty`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = emptyList(),
                lastCity = null,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(emptyList<SavedLocation>(), viewModel.locations.value)
            assertNull(viewModel.activeLocation.value)
        }

    @Test
    fun `init clamps out-of-range active index`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin),
                activeIndex = 99,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(0, viewModel.activeIndex.value)
        }

    // --- addLocation ---

    @Test
    fun `addLocation appends new entry and makes it active`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = listOf(austin),
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.addLocation(denver)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austin, denver), viewModel.locations.value)
            assertEquals(1, viewModel.activeIndex.value)
            assertEquals(denver, viewModel.activeLocation.value)
            assertEquals(listOf(austin, denver), persisted.last())
        }

    @Test
    fun `addLocation with duplicate label switches to existing instead of duplicating`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.addLocation(austin)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austin, denver), viewModel.locations.value) // unchanged
            assertEquals(0, viewModel.activeIndex.value) // switched to austin
            assertEquals(austin, viewModel.activeLocation.value)
            assertEquals(true, persisted.isEmpty()) // no persistence on dedup
        }

    @Test
    fun `addLocation fires onCityAdded for new cities`() =
        runTest {
            var callCount = 0
            val viewModel = viewModel(
                savedLocations = listOf(austin),
                onCityAdded = { callCount++ },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.addLocation(denver)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, callCount)
        }

    @Test
    fun `addLocation does not fire onCityAdded for duplicates`() =
        runTest {
            var callCount = 0
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                onCityAdded = { callCount++ },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.addLocation(austin)
            assertEquals(0, callCount)
        }

    // --- removeLocation ---

    // --- updateCurrentLocation ---

    private val myLocation = SavedLocation("My Location", 40.0, -83.0, isCurrentLocation = true)

    @Test
    fun `updateCurrentLocation moves My Location after a real move and persists it`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = listOf(austin, myLocation),
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // About 11 km north.
            viewModel.updateCurrentLocation(myLocation.copy(latitude = 40.1, longitude = -83.0))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            val moved = myLocation.copy(latitude = 40.1)
            assertEquals(listOf(austin, moved), viewModel.locations.value)
            assertEquals(listOf(listOf(austin, moved)), persisted)
        }

    @Test
    fun `updateCurrentLocation says whether My Location moved`() =
        runTest {
            val viewModel = viewModel(savedLocations = listOf(myLocation))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertFalse(viewModel.updateCurrentLocation(myLocation.copy(latitude = 40.001)))
            assertTrue(viewModel.updateCurrentLocation(myLocation.copy(latitude = 40.1)))
        }

    // --- following a move made in storage ---

    @Test
    fun `a move made in storage while open is taken up`() =
        runTest {
            val stored = MutableStateFlow(listOf(austin, myLocation))
            val viewModel = viewModel(savedLocationsFlow = stored)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // Follow my location, while the screen is open.
            stored.value = listOf(austin, myLocation.copy(latitude = 40.1))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austin, myLocation.copy(latitude = 40.1)), viewModel.locations.value)
        }

    @Test
    fun `following the stored list changes nothing when My Location hasn't moved`() =
        runTest {
            val stored = MutableStateFlow(listOf(austin, myLocation))
            val viewModel = viewModel(savedLocationsFlow = stored)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // Another change in storage, not to My Location, is left to whoever made it.
            stored.value = listOf(myLocation, austin)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austin, myLocation), viewModel.locations.value)
        }

    @Test
    fun `updateCurrentLocation ignores GPS wobble under a kilometre`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = listOf(myLocation),
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            // About 110 m.
            viewModel.updateCurrentLocation(myLocation.copy(latitude = 40.001))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(myLocation), viewModel.locations.value)
            assertEquals(emptyList<List<SavedLocation>>(), persisted)
        }

    @Test
    fun `updateCurrentLocation does nothing without a saved My Location`() =
        runTest {
            val viewModel = viewModel(savedLocations = listOf(austin))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.updateCurrentLocation(myLocation)

            assertEquals(listOf(austin), viewModel.locations.value)
        }

    @Test
    fun `removeLocation removes entry at index and persists`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                activeIndex = 0,
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.removeLocation(1)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austin), viewModel.locations.value)
            assertEquals(listOf(austin), persisted.last())
        }

    @Test
    fun `removeLocation clamps active index when active tab is removed`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                activeIndex = 1,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.removeLocation(1) // remove the active (last) entry
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(0, viewModel.activeIndex.value)
            assertEquals(austin, viewModel.activeLocation.value)
        }

    @Test
    fun `removeLocation on last entry results in empty list and null activeLocation`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin),
                activeIndex = 0,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.removeLocation(0)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(emptyList<SavedLocation>(), viewModel.locations.value)
            assertNull(viewModel.activeLocation.value)
        }

    // --- setActiveIndex ---

    @Test
    fun `setActiveIndex updates index and persists`() =
        runTest {
            val persistedIndices = mutableListOf<Int>()
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                activeIndex = 0,
                onSetActiveIndex = { persistedIndices += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.setActiveIndex(1)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(1, viewModel.activeIndex.value)
            assertEquals(denver, viewModel.activeLocation.value)
            assertEquals(listOf(1), persistedIndices)
        }

    @Test
    fun `setActiveIndex is a no-op when index is unchanged`() =
        runTest {
            val persistedIndices = mutableListOf<Int>()
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver),
                activeIndex = 0,
                onSetActiveIndex = { persistedIndices += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.setActiveIndex(0)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(0, viewModel.activeIndex.value)
            assertEquals(true, persistedIndices.isEmpty())
        }

    // --- reorderLocations ---

    private val chicago = SavedLocation(label = "Chicago, IL", latitude = 41.88, longitude = -87.63)

    @Test
    fun `reorderLocations moves item and persists new order`() =
        runTest {
            val persisted = mutableListOf<List<SavedLocation>>()
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver, chicago),
                activeIndex = 0,
                onSetSavedLocations = { persisted += it },
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.reorderLocations(fromIndex = 0, toIndex = 2)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(denver, chicago, austin), viewModel.locations.value)
            assertEquals(listOf(denver, chicago, austin), persisted.last())
        }

    @Test
    fun `reorderLocations tracks active location when it moves`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver, chicago),
                activeIndex = 0,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.reorderLocations(fromIndex = 0, toIndex = 2)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(2, viewModel.activeIndex.value)
            assertEquals(austin, viewModel.activeLocation.value)
        }

    @Test
    fun `reorderLocations adjusts active index when item moves over it from below`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver, chicago),
                activeIndex = 1,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.reorderLocations(fromIndex = 2, toIndex = 0)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(chicago, austin, denver), viewModel.locations.value)
            assertEquals(2, viewModel.activeIndex.value)
            assertEquals(denver, viewModel.activeLocation.value)
        }

    @Test
    fun `reorderLocations adjusts active index when item moves over it from above`() =
        runTest {
            val viewModel = viewModel(
                savedLocations = listOf(austin, denver, chicago),
                activeIndex = 1,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.reorderLocations(fromIndex = 0, toIndex = 2)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(denver, chicago, austin), viewModel.locations.value)
            assertEquals(0, viewModel.activeIndex.value)
            assertEquals(denver, viewModel.activeLocation.value)
        }

    // --- fillInMissingCoordinates ---

    /** A city whose lookup failed when it was added gets its coordinates the next time the app opens. */
    @Test
    fun `a name-only city saved earlier is geocoded on launch`() =
        runTest {
            val geocodingRepo = mockk<GeocodingRepository>()
            coEvery { geocodingRepo.searchCities("London") } returns
                Result.success(listOf(CitySuggestion("London, England, GB", 51.51, -0.13)))
            var saved: List<SavedLocation>? = null
            val viewModel = viewModel(
                savedLocations = listOf(SavedLocation(label = "London", latitude = null, longitude = null)),
                onSetSavedLocations = { saved = it },
                geocodingRepo = geocodingRepo,
            )
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            val geocoded = SavedLocation(label = "London", latitude = 51.51, longitude = -0.13)
            assertEquals(listOf(geocoded), viewModel.locations.value)
            assertEquals(listOf(geocoded), saved)
        }

    @Test
    fun `retrying while a lookup is in flight does not look it up twice`() =
        runTest {
            val geocodingRepo = mockk<GeocodingRepository>()
            coEvery { geocodingRepo.searchCities(any()) } coAnswers {
                delay(1_000)
                Result.success(listOf(CitySuggestion("London, England, GB", 51.51, -0.13)))
            }
            val viewModel = viewModel(
                savedLocations = listOf(SavedLocation(label = "London", latitude = null, longitude = null)),
                geocodingRepo = geocodingRepo,
            )
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            viewModel.fillInMissingCoordinates()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { geocodingRepo.searchCities("London") }
            assertEquals(
                51.51,
                viewModel.locations.value
                    .single()
                    .latitude,
            )
        }

    // --- helpers ---

    private fun viewModel(
        savedLocations: List<SavedLocation> = emptyList(),
        // A live store, for moves made while the screen is open; otherwise [savedLocations] once.
        savedLocationsFlow: Flow<List<SavedLocation>>? = null,
        activeIndex: Int = 0,
        lastCity: String? = null,
        onSetSavedLocations: (List<SavedLocation>) -> Unit = {},
        onSetActiveIndex: (Int) -> Unit = {},
        onCityAdded: () -> Unit = {},
        geocodingRepo: GeocodingRepository = mockk<GeocodingRepository>(relaxed = true).also {
            coEvery { it.searchCities(any()) } returns Result.success(emptyList())
        },
    ): CityRepository {
        val repo = mockk<PreferencesRepository>(relaxed = true)
        every { repo.savedLocations } returns (savedLocationsFlow ?: flowOf(savedLocations))
        every { repo.activeLocationIndex } returns flowOf(activeIndex)
        every { repo.lastCity } returns flowOf(lastCity)
        coEvery { repo.setSavedLocations(any()) } answers {
            onSetSavedLocations(firstArg())
        }
        coEvery { repo.setActiveLocationIndex(any()) } answers {
            onSetActiveIndex(firstArg())
        }
        return CityRepository(repo, geocodingRepo, onCityAdded = onCityAdded)
    }

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

    @Test
    fun `My Location is checked for a move at most every 15 minutes`() =
        runTest {
            val viewModel = viewModel()
            val start = 1_000_000L

            assertTrue(viewModel.claimMyLocationCheck(start))
            assertFalse(viewModel.claimMyLocationCheck(start + 60_000))
            assertFalse(viewModel.claimMyLocationCheck(start + MY_LOCATION_CHECK_INTERVAL_MS - 1))
            assertTrue(viewModel.claimMyLocationCheck(start + MY_LOCATION_CHECK_INTERVAL_MS))
        }

    @Test
    fun `a check made by pull to refresh holds off the next on resume`() =
        runTest {
            val viewModel = viewModel()
            val pulled = 1_000_000L

            viewModel.noteMyLocationChecked(pulled)

            assertFalse(viewModel.claimMyLocationCheck(pulled + 60_000))
            assertTrue(viewModel.claimMyLocationCheck(pulled + MY_LOCATION_CHECK_INTERVAL_MS))
        }
}
