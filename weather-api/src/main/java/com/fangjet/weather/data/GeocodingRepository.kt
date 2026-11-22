package com.fangjet.weather.data

import com.fangjet.weather.data.remote.GeocodingApi
import com.fangjet.weather.data.remote.dto.GeocodingDirectDto
import com.fangjet.weather.di.WeatherNetworkModule
import com.fangjet.weather.domain.CitySuggestion
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import java.util.Locale


@Singleton
class GeocodingRepository @Inject constructor(
    private val api: GeocodingApi,
    @param:Named(WeatherNetworkModule.OWM_API_KEY) private val apiKey: String
) {

    private val excludedFeatureCodes = setOf("PPLH", "PPLQ", "PPLW")

    suspend fun searchCities(
        query: String,
        searchProvider: String? = null,
        userCountryCode: String? = Locale.getDefault().country,
        myLocationLat: Double? = null,
        myLocationLon: Double? = null,
        citySearchSizeRatio: Double = 50.0
    ): Result<List<CitySuggestion>> {
        val key = apiKey.trim()
        if (key.isEmpty()) {
            return Result.failure(IllegalStateException("missing_api_key"))
        }
        val q = query.trim()
        if (q.length < 2) {
            return Result.success(emptyList())
        }

        // 1. Rule B: Detect US ZIP codes (exactly 5 digits)
        val isUsZip = q.matches(Regex("^\\d{5}$"))

        // 2. Rule C: Block other postcodes (alphanumeric like "SW1A 1AA" or "M4B 1B3")
        if (!isUsZip && q.any { it.isDigit() } && q.any { it.isLetter() }) {
            return Result.success(emptyList()) // Short-circuit instantly to search nothing
        }

        // 3. Rule A: Handle text after a comma ("Richmond, North Yorkshire")
        var networkSearchQuery = q
        var regionFilter: String? = null

        if (q.contains(",")) {
            val parts = q.split(",")
            networkSearchQuery = parts[0].trim()           // Extract "Richmond"
            regionFilter = parts.getOrNull(1)?.trim()      // Keep "North Yorkshire"
        }

        if (searchProvider == "open_meteo") {
            try {
                // Attempt Open-Meteo prefix search
                val response = api.searchGeocodingMeteo(networkSearchQuery)
                if (response.isSuccessful && response.body() != null) {
                    val rawResults = response.body()!!.results ?: emptyList()
                    val processedSuggestions = processOpenMeteoResults(
                        rawResults = rawResults,
                        regionFilter = regionFilter,
                        userCountryCode = userCountryCode,
                        myLocationLat = myLocationLat,
                        myLocationLon = myLocationLon,
                        citySearchSizeRatio = citySearchSizeRatio
                    )
                    return Result.success(processedSuggestions)
                } else {
                    throw Exception("Open-Meteo network response failed")
                }
            } catch (_: Exception) {
                // FALLBACK: If Open-Meteo fails, execute original OpenWeather call
                return fetchOpenWeatherFallback(networkSearchQuery, key, regionFilter)
            }
        } else {
            // Default directly to OpenWeather
            return fetchOpenWeatherFallback(networkSearchQuery, key, regionFilter)
        }
    }

    private fun processOpenMeteoResults(
        rawResults: List<OpenMeteoCityDto>,
        regionFilter: String?,
        userCountryCode: String?,
        myLocationLat: Double?,
        myLocationLon: Double?,
        citySearchSizeRatio: Double
    ): List<CitySuggestion> {
        // Task 2.1: Filter for populated places only (exclude historical, abandoned, destroyed)
        val populatedOnly = rawResults.filter { dto ->
            val code = dto.feature_code
            code != null && code.startsWith("PPL") && code !in excludedFeatureCodes
        }

        // Task 2.2: Merge duplicate rows within 2 km (prioritise higher population)
        val sortedForMerge = populatedOnly.sortedByDescending { it.population ?: 0L }
        val merged = mutableListOf<OpenMeteoCityDto>()
        for (candidate in sortedForMerge) {
            val isDuplicate = merged.any { existing ->
                existing.name.equals(candidate.name, ignoreCase = true) &&
                        haversineKm(
                            existing.latitude, existing.longitude,
                            candidate.latitude, candidate.longitude
                        ) <= 2.0
            }
            if (!isDuplicate) {
                merged.add(candidate)
            }
        }

        // Task 3.3: Size guard (drop tiny same-name places smaller than largest / citySearchSizeRatio)
        val maxPopByName = merged
            .groupBy { it.name?.trim()?.lowercase() ?: "" }
            .mapValues { (_, list) -> list.maxOfOrNull { it.population ?: 0L } ?: 0L }

        val sizeFiltered = if (citySearchSizeRatio > 0.0) {
            merged.filter { dto ->
                val key = dto.name?.trim()?.lowercase() ?: ""
                val maxPop = maxPopByName[key] ?: 0L
                val pop = dto.population ?: 0L
                if (maxPop <= 0L || pop <= 0L) {
                    true
                } else {
                    pop >= (maxPop / citySearchSizeRatio)
                }
            }
        } else {
            merged
        }

        // Task 3.1 & 3.2: Sort — phone's country first, then nearest My Location (or population)
        val hasMyLocation = myLocationLat != null && myLocationLon != null
        val sorted = sizeFiltered.sortedWith(
            compareBy<OpenMeteoCityDto>(
                // 1. Phone's country first (0 = matches user country, 1 = other countries)
                { dto ->
                    if (!userCountryCode.isNullOrBlank() &&
                        dto.country_code.equals(userCountryCode, ignoreCase = true)
                    ) 0 else 1
                },
                // 2. Nearest My Location first when coordinates are available
                { dto ->
                    if (hasMyLocation) {
                        haversineKm(myLocationLat!!, myLocationLon!!, dto.latitude, dto.longitude)
                    } else {
                        0.0
                    }
                }
            ).thenByDescending { it.population ?: 0L }
        )

        // Task 2.3: Add county (admin2) when two or more results share the same base label
        val baseLabels = sorted.map { it.formatLabel(includeCounty = false) }
        val labelCounts = baseLabels.groupingBy { it }.eachCount()

        val suggestions = sorted.mapIndexed { index, dto ->
            val baseLabel = baseLabels[index]
            val hasSameLabelCollision = (labelCounts[baseLabel] ?: 0) > 1
            val finalLabel = if (hasSameLabelCollision) {
                dto.formatLabel(includeCounty = true)
            } else {
                baseLabel
            }

            CitySuggestion(
                label = finalLabel,
                weatherLat = dto.latitude,
                weatherLon = dto.longitude,
                name = dto.name ?: "",
                countryCode = dto.country_code ?: ""
            )
        }

        return if (!regionFilter.isNullOrBlank()) {
            suggestions.filter { it.label.contains(regionFilter, ignoreCase = true) }
        } else {
            suggestions
        }
    }

    private fun OpenMeteoCityDto.formatLabel(includeCounty: Boolean): String {
        val county = if (includeCounty) {
            admin2?.takeIf { it.isNotBlank() && !it.equals(name, ignoreCase = true) }
        } else {
            null
        }
        val labelParts = listOfNotNull(
            name,
            county,
            admin1?.takeIf { it.isNotBlank() },
            country
        )
        return labelParts.joinToString(", ")
    }

    private fun haversineKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = kotlin.math.sin(dLat / 2).let { it * it } +
                kotlin.math.cos(Math.toRadians(lat1)) *
                kotlin.math.cos(Math.toRadians(lat2)) *
                kotlin.math.sin(dLon / 2).let { it * it }
        return 2 * r * kotlin.math.asin(kotlin.math.sqrt(a))
    }

    private suspend fun fetchOpenWeatherFallback(
        networkSearchQuery: String,
        key: String,
        regionFilter: String?
    ): Result<List<CitySuggestion>> = runCatching {
        val apiResults = api.directSearch(query = networkSearchQuery, limit = 8, apiKey = key)
            .map { it.toSuggestion() }
            .distinctBy { it.label }

        if (regionFilter != null) {
            apiResults.filter { suggestion ->
                suggestion.label.contains(regionFilter, ignoreCase = true)
            }
        } else {
            apiResults
        }
    }

    private fun GeocodingDirectDto.toSuggestion(): CitySuggestion {
        val labelParts = listOfNotNull(
            name,
            state?.takeIf { it.isNotBlank() },
            country
        )
        val label = labelParts.joinToString(", ")

        return CitySuggestion(
            label = label,
            weatherLat = lat,
            weatherLon = lon,
            name = name,
            countryCode = country
        )
    }
}
// Data models representing Open-Meteo's API response structure
data class OpenMeteoGeocodingResponse(
    val results: List<OpenMeteoCityDto>?
)

data class OpenMeteoCityDto(
    val name: String?,
    val latitude: Double,
    val longitude: Double,
    val country: String?,
    val country_code: String?,
    val admin1: String?,       // State/province/region
    val admin2: String?,       // County/district (for Task 2 disambiguation)
    val feature_code: String?, // GeoNames feature code (for Task 2 filtering)
    val population: Long?      // Population (for merging & Task 3)
)