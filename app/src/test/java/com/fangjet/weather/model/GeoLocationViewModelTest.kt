package com.fangjet.weather.model

import com.fangjet.weather.data.GeocodingRepository
import com.fangjet.weather.domain.CitySuggestion
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class GeoLocationViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val geocodingRepository = mockk<GeocodingRepository>(relaxed = true)

    @Test
    fun `short input clears suggestions without searching`() =
        runTest {
            val calls = mutableListOf<String>()
            coEvery { geocodingRepository.searchCities(any()) } answers {
                calls += firstArg<String>()
                Result.success(listOf(austinSuggestion))
            }
            val viewModel = GeoLocationViewModel(geocodingRepository)

            viewModel.onCityInputChange(" A ")
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.citySuggestions.value.isEmpty())
            assertFalse(viewModel.citySuggestLoading.value)
            assertTrue(calls.isEmpty())
        }

    @Test
    fun `latest city input wins when an earlier search is still running`() =
        runTest {
            val firstSearch = CompletableDeferred<Result<List<CitySuggestion>>>()
            val secondSearch = CompletableDeferred<Result<List<CitySuggestion>>>()
            val calls = mutableListOf<String>()

            coEvery { geocodingRepository.searchCities(any()) } coAnswers {
                val query = firstArg<String>()
                calls += query
                when (query) {
                    "Au" -> firstSearch.await()
                    "Austin" -> secondSearch.await()
                    else -> error("Unexpected query $query")
                }
            }
            val viewModel = GeoLocationViewModel(geocodingRepository)

            viewModel.onCityInputChange("Au")
            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(DEBOUNCE_MILLIS)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(listOf("Au"), calls)
            assertTrue(viewModel.citySuggestLoading.value)

            viewModel.onCityInputChange("Austin")
            firstSearch.complete(Result.success(listOf(staleSuggestion)))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertTrue(viewModel.citySuggestions.value.isEmpty())
            assertTrue(viewModel.citySuggestLoading.value)

            mainDispatcherRule.testDispatcher.scheduler.advanceTimeBy(DEBOUNCE_MILLIS)
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(listOf("Au", "Austin"), calls)

            secondSearch.complete(Result.success(listOf(austinSuggestion)))
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(listOf(austinSuggestion), viewModel.citySuggestions.value)
            assertFalse(viewModel.citySuggestLoading.value)
        }

    @Test
    fun `a failed search says so until the text changes`() =
        runTest {
            coEvery { geocodingRepository.searchCities(any()) } returns Result.failure(java.io.IOException())
            val viewModel = GeoLocationViewModel(geocodingRepository)

            viewModel.onCityInputChange("London")
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.searchFailed.value)
            assertTrue(viewModel.citySuggestions.value.isEmpty())
            assertFalse(viewModel.citySuggestLoading.value)

            viewModel.onCityInputChange("Londo")

            assertFalse(viewModel.searchFailed.value)
        }

    @Test
    fun `a search that finds nothing isn't a failure`() =
        runTest {
            coEvery { geocodingRepository.searchCities(any()) } returns Result.success(emptyList())
            val viewModel = GeoLocationViewModel(geocodingRepository)

            viewModel.onCityInputChange("Nashvile")
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            assertFalse(viewModel.searchFailed.value)
            assertTrue(viewModel.citySuggestions.value.isEmpty())
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

    private companion object {
        const val DEBOUNCE_MILLIS = 280L

        val austinSuggestion = CitySuggestion(
            label = "Austin, TX, US",
            weatherLat = 30.2672,
            weatherLon = -97.7431,
        )

        val staleSuggestion = CitySuggestion(
            label = "Aurora, CO, US",
            weatherLat = 39.7294,
            weatherLon = -104.8319,
        )
    }
}
