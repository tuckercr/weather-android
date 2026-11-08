package com.fangjet.weather.model

import android.app.Application
import com.fangjet.weather.domain.SavedLocation
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
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
class LocationPermissionViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `use my location asks only while Android still will, and otherwise reports a denial`() {
        assertEquals(true, viewModel(hasLocationPermission = false).useMyLocation())
        val refused = viewModel(hasLocationPermission = false, denials = FakeDenialStore(permanentlyDenied = true))
        assertEquals(false, refused.useMyLocation())
        assertEquals(LocationFetchState.PermissionDenied, refused.state.value)
    }

    @Test
    fun `adding my location hands over the position once, or says it failed`() =
        runTest {
            val here = viewModel(locationFetcher = FakeLocationFetcher(lastLocation = DeviceLocation(30.2672, -97.7431)))
            val added = mutableListOf<Double?>()
            val job = launch { here.myLocationAdded.collect { added += it.latitude } }
            assertEquals(false, here.addMyLocation())
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(listOf<Double?>(30.2672), added)
            // Not left as Located for another screen to act on.
            assertEquals(LocationFetchState.Idle, here.state.value)
            job.cancel()

            val nowhere = viewModel(locationFetcher = FakeLocationFetcher())
            var failures = 0
            val failed = launch { nowhere.addMyLocationFailed.collect { failures++ } }
            nowhere.addMyLocation()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertEquals(1, failures)
            failed.cancel()
        }

    @Test
    fun `my location is offered while access is on or Android will still ask`() {
        val allowed = viewModel()
        allowed.refreshAccess()
        assertEquals(true, allowed.myLocationOffered.value)
        val refused = viewModel(hasLocationPermission = false, denials = FakeDenialStore(permanentlyDenied = true))
        refused.refreshAccess()
        assertEquals(false, refused.myLocationOffered.value)
    }

    @Test
    fun `fetchLocation without permission denies immediately and does not query location`() =
        runTest {
            val fetcher = FakeLocationFetcher(
                lastLocation = DeviceLocation(latitude = 30.2672, longitude = -97.7431),
            )
            val viewModel = viewModel(
                hasLocationPermission = false,
                locationFetcher = fetcher,
            )

            viewModel.fetchLocation()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LocationFetchState.PermissionDenied, viewModel.state.value)
            assertEquals(0, fetcher.lastLocationCallCount)
            assertEquals(0, fetcher.currentLocationCallCount)
        }

    @Test
    fun `the known position is the last one, or a fresh fix without one, and forgets earlier refusals`() =
        runTest {
            val denials = FakeDenialStore(deniedBefore = true, permanentlyDenied = true)
            val last = viewModel(
                locationFetcher = FakeLocationFetcher(
                    lastLocation = DeviceLocation(latitude = 30.2672, longitude = -97.7431),
                    currentLocation = DeviceLocation(latitude = 39.7392, longitude = -104.9903),
                ),
                denials = denials,
            )
            val fresh =
                viewModel(
                    locationFetcher = FakeLocationFetcher(currentLocation = DeviceLocation(latitude = 39.7392, longitude = -104.9903)),
                )

            assertEquals(30.2672, last.knownPosition()?.latitude)
            assertEquals(39.7392, fresh.knownPosition()?.latitude)
            assertEquals(null, viewModel(hasLocationPermission = false).knownPosition())
            assertEquals(false, denials.deniedBefore || denials.permanentlyDenied)
            // It leaves the state for the Add location screen alone.
            assertEquals(LocationFetchState.Idle, last.state.value)
        }

    @Test
    fun `fetchLocation uses cached last location when available`() =
        runTest {
            val fetcher = FakeLocationFetcher(
                lastLocation = DeviceLocation(latitude = 30.2672, longitude = -97.7431),
                currentLocation = DeviceLocation(latitude = 39.7392, longitude = -104.9903),
            )
            val viewModel = viewModel(locationFetcher = fetcher)

            viewModel.fetchLocation()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                LocationFetchState.Located(
                    SavedLocation(
                        label = "My Location",
                        latitude = 30.2672,
                        longitude = -97.7431,
                        isCurrentLocation = true,
                    ),
                ),
                viewModel.state.value,
            )
            assertEquals(1, fetcher.lastLocationCallCount)
            assertEquals(0, fetcher.currentLocationCallCount)
        }

    @Test
    fun `fetchLocation falls back to current location when cached location is missing`() =
        runTest {
            val fetcher = FakeLocationFetcher(
                lastLocation = null,
                currentLocation = DeviceLocation(latitude = 39.7392, longitude = -104.9903),
            )
            val viewModel = viewModel(locationFetcher = fetcher)

            viewModel.fetchLocation()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(
                LocationFetchState.Located(
                    SavedLocation(
                        label = "My Location",
                        latitude = 39.7392,
                        longitude = -104.9903,
                        isCurrentLocation = true,
                    ),
                ),
                viewModel.state.value,
            )
            assertEquals(1, fetcher.lastLocationCallCount)
            assertEquals(1, fetcher.currentLocationCallCount)
        }

    @Test
    fun `fetchLocation fails when no location source returns coordinates`() =
        runTest {
            val fetcher = FakeLocationFetcher(
                lastLocation = null,
                currentLocation = null,
            )
            val viewModel = viewModel(locationFetcher = fetcher)

            viewModel.fetchLocation()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertEquals(LocationFetchState.Failed, viewModel.state.value)
            assertEquals(1, fetcher.lastLocationCallCount)
            assertEquals(1, fetcher.currentLocationCallCount)
        }

    @Test
    fun `currentPosition asks for a fresh fix, not the last known one, and leaves the state alone`() =
        runTest {
            val fetcher = FakeLocationFetcher(
                lastLocation = DeviceLocation(35.96, -83.92),
                currentLocation = DeviceLocation(36.03, -84.1),
            )
            val viewModel = viewModel(locationFetcher = fetcher)

            val here = viewModel.currentPosition()

            assertEquals(SavedLocation("My Location", 36.03, -84.1, isCurrentLocation = true), here)
            assertEquals(0, fetcher.lastLocationCallCount)
            assertEquals(LocationFetchState.Idle, viewModel.state.value)
        }

    @Test
    fun `currentPosition is null without permission or a fix`() =
        runTest {
            val fetcher = FakeLocationFetcher(currentLocation = DeviceLocation(36.03, -84.1))

            assertNull(viewModel(hasLocationPermission = false, locationFetcher = fetcher).currentPosition())
            assertEquals(0, fetcher.currentLocationCallCount)
            assertNull(viewModel(locationFetcher = FakeLocationFetcher()).currentPosition())
        }

    @Test
    fun `consumeLocated returns a located result to idle, so it isn't acted on twice`() =
        runTest {
            val viewModel = viewModel(locationFetcher = FakeLocationFetcher(lastLocation = DeviceLocation(40.0, -83.0)))
            viewModel.fetchLocation()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.state.value is LocationFetchState.Located)

            viewModel.consumeLocated()

            assertEquals(LocationFetchState.Idle, viewModel.state.value)
        }

    @Test
    fun `consumeLocated leaves other states alone`() {
        val viewModel = viewModel()
        viewModel.onPermissionDenied()

        viewModel.consumeLocated()

        assertEquals(LocationFetchState.PermissionDenied, viewModel.state.value)
    }

    // --- permission answers ---

    @Test
    fun `a first refusal can still be asked again`() {
        val viewModel = viewModel(hasLocationPermission = false)

        viewModel.onPermissionResult(granted = false, rationaleBefore = false, rationaleAfter = true)

        assertEquals(LocationFetchState.PermissionDenied, viewModel.state.value)
        assertTrue(viewModel.canRequestPermission())
    }

    @Test
    fun `a second refusal stops the asking`() {
        val viewModel = viewModel(hasLocationPermission = false)
        viewModel.onPermissionResult(granted = false, rationaleBefore = false, rationaleAfter = true)

        viewModel.onPermissionResult(granted = false, rationaleBefore = true, rationaleAfter = false)

        assertFalse(viewModel.canRequestPermission())
    }

    @Test
    fun `a dialog dismissed without an answer doesn't stop the asking`() {
        val viewModel = viewModel(hasLocationPermission = false)

        viewModel.onPermissionResult(granted = false, rationaleBefore = false, rationaleAfter = false)

        assertTrue(viewModel.canRequestPermission())
    }

    @Test
    fun `a refusal with no dialog after an earlier one stops the asking`() {
        // "Don't allow" chosen in system settings: Android refuses at once, rationale stays false.
        val viewModel = viewModel(hasLocationPermission = false)
        viewModel.onPermissionResult(granted = false, rationaleBefore = false, rationaleAfter = false)

        viewModel.onPermissionResult(granted = false, rationaleBefore = false, rationaleAfter = false)

        assertFalse(viewModel.canRequestPermission())
    }

    @Test
    fun `a grant clears earlier refusals and fetches the location`() =
        runTest {
            val denials = FakeDenialStore(deniedBefore = true, permanentlyDenied = true)
            val fetcher = FakeLocationFetcher(lastLocation = DeviceLocation(40.0, -83.0))
            val viewModel = viewModel(locationFetcher = fetcher, denials = denials)

            viewModel.onPermissionResult(granted = true, rationaleBefore = false, rationaleAfter = false)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.state.value is LocationFetchState.Located)
            assertFalse(denials.deniedBefore)
            assertFalse(denials.permanentlyDenied)
        }

    @Test
    fun `nothing to ask for while access is on`() {
        assertFalse(viewModel(hasLocationPermission = true).canRequestPermission())
    }

    @Test
    fun `onPermissionDenied records denied state`() {
        val viewModel = viewModel()

        viewModel.onPermissionDenied()

        assertEquals(LocationFetchState.PermissionDenied, viewModel.state.value)
    }

    private fun viewModel(
        hasLocationPermission: Boolean = true,
        locationFetcher: LocationFetcher = FakeLocationFetcher(),
        denials: LocationDenialStore = FakeDenialStore(),
    ) = LocationPermissionViewModel(
        application = mockk<Application>(relaxed = true),
        hasLocationPermissionProvider = { hasLocationPermission },
        locationFetcher = locationFetcher,
        denials = denials,
    )

    private class FakeDenialStore(
        override var deniedBefore: Boolean = false,
        override var permanentlyDenied: Boolean = false,
    ) : LocationDenialStore

    private class FakeLocationFetcher(
        private val lastLocation: DeviceLocation? = null,
        private val currentLocation: DeviceLocation? = null,
    ) : LocationFetcher {
        var lastLocationCallCount = 0
        var currentLocationCallCount = 0

        override suspend fun tryLastLocation(): DeviceLocation? {
            lastLocationCallCount += 1
            return lastLocation
        }

        override suspend fun tryCurrentLocation(): DeviceLocation? {
            currentLocationCallCount += 1
            return currentLocation
        }
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
}
