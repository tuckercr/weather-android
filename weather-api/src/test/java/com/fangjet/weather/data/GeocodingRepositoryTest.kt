package com.fangjet.weather.data

import com.fangjet.weather.data.remote.GeocodingApi
import com.fangjet.weather.data.remote.dto.GeocodingDirectDto
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GeocodingRepositoryTest {

    @Test
    fun `searchCities trims input and formats suggestion labels`() =
        runBlocking {
            val api = FakeGeocodingApi(
                response = listOf(
                    GeocodingDirectDto(
                        name = "Austin",
                        lat = 30.2672,
                        lon = -97.7431,
                        country = "US",
                        state = "TX",
                    ),
                    GeocodingDirectDto(
                        name = "London",
                        lat = 51.5072,
                        lon = -0.1276,
                        country = "GB",
                        state = "",
                    ),
                ),
            )
            val repository = GeocodingRepository(api, " test-key ")

            val suggestions = repository.searchCities("  Austin  ").getOrThrow()

            assertEquals("Austin", api.lastQuery)
            assertEquals(8, api.lastLimit)
            assertEquals("test-key", api.lastApiKey)
            assertEquals(2, suggestions.size)
            assertEquals("Austin, TX, US", suggestions.first().label)
            assertEquals(30.2672, suggestions.first().weatherLat, 0.0001)
            assertEquals(-97.7431, suggestions.first().weatherLon, 0.0001)
            assertEquals("London, GB", suggestions.last().label)
        }

    @Test
    fun `a place listed twice is suggested once at its first match`() =
        runBlocking {
            val api = FakeGeocodingApi(
                response = listOf(
                    GeocodingDirectDto(name = "Amsterdam", lat = 52.3728, lon = 4.8936, country = "NL", state = "North Holland"),
                    GeocodingDirectDto(name = "Amsterdam", lat = 52.3676, lon = 4.9041, country = "NL", state = "North Holland"),
                    GeocodingDirectDto(name = "Amsterdam", lat = 42.9387, lon = -74.1882, country = "US", state = "New York"),
                ),
            )

            val suggestions = GeocodingRepository(api, "test-key").searchCities("Amsterdam").getOrThrow()

            assertEquals(listOf("Amsterdam, North Holland, NL", "Amsterdam, New York, US"), suggestions.map { it.label })
            assertEquals(4.8936, suggestions.first().weatherLon, 0.0001)
        }

    @Test
    fun `searchCities returns empty result for short query without calling api`() =
        runBlocking {
            val api = FakeGeocodingApi()
            val repository = GeocodingRepository(api, "test-key")

            val suggestions = repository.searchCities(" a ").getOrThrow()

            assertTrue(suggestions.isEmpty())
            assertEquals(0, api.callCount)
        }

    @Test
    fun `searchCities with missing api key fails before calling api`() =
        runBlocking {
            val api = FakeGeocodingApi()
            val repository = GeocodingRepository(api, "   ")

            val result = repository.searchCities("Austin")

            val error = result.exceptionOrNull()
            assertEquals("missing_api_key", error?.message)
            assertEquals(0, api.callCount)
        }

    @Test
    fun `searchCities wraps api failures`() =
        runBlocking {
            val api = FakeGeocodingApi(throwable = IllegalStateException("backend down"))
            val repository = GeocodingRepository(api, "test-key")

            val result = repository.searchCities("Austin")

            val error = result.exceptionOrNull()
            assertEquals("backend down", error?.message)
            assertEquals(1, api.callCount)
        }

    private class FakeGeocodingApi(
        private val response: List<GeocodingDirectDto> = emptyList(),
        private val throwable: Throwable? = null,
    ) : GeocodingApi {
        var callCount = 0
        var lastQuery: String? = null
        var lastLimit: Int? = null
        var lastApiKey: String? = null

        override suspend fun directSearch(
            query: String,
            limit: Int,
            apiKey: String,
        ): List<GeocodingDirectDto> {
            callCount += 1
            lastQuery = query
            lastLimit = limit
            lastApiKey = apiKey
            throwable?.let { throw it }
            return response
        }
    }
}
