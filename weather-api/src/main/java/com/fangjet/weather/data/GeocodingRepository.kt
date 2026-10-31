package com.fangjet.weather.data

import com.fangjet.weather.data.remote.GeocodingApi
import com.fangjet.weather.data.remote.dto.GeocodingDirectDto
import com.fangjet.weather.di.WeatherNetworkModule
import com.fangjet.weather.domain.CitySuggestion
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class GeocodingRepository @Inject constructor(
    private val api: GeocodingApi,
    @param:Named(WeatherNetworkModule.OWM_API_KEY) private val apiKey: String,
) {

    suspend fun searchCities(query: String): Result<List<CitySuggestion>> {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return Result.failure(IllegalStateException("missing_api_key"))
        }
        val q = query.trim()
        if (q.length < 2) {
            return Result.success(emptyList())
        }
        return runCatching {
            // The geocoder can list a place twice a few metres apart ("Amsterdam, North Holland, NL"),
            // which looks broken. The first is its best match.
            api.directSearch(query = q, limit = 8, apiKey = key).map { it.toSuggestion() }.distinctBy { it.label }
        }
    }

    private fun GeocodingDirectDto.toSuggestion(): CitySuggestion {
        val labelParts = listOfNotNull(
            name,
            state?.takeIf { it.isNotBlank() },
            country,
        )
        val label = labelParts.joinToString(", ")

        return CitySuggestion(
            label = label,
            weatherLat = lat,
            weatherLon = lon,
        )
    }
}
