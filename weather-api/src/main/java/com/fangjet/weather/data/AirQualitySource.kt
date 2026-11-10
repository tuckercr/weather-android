package com.fangjet.weather.data

import com.fangjet.weather.data.remote.dto.AirQualityHourlyDto
import com.fangjet.weather.data.remote.dto.AirQualityHourlyResponse
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.AirQuality.Pollutant
import com.fangjet.weather.domain.AirQuality.Scale
import com.fangjet.weather.domain.AirQualityHours
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/** Hourly air quality from Open-Meteo's air quality endpoint: free, keyless and worldwide. */
@Singleton
class AirQualitySource @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val json: Json,
) {
    /**
     * Each hour's air quality for the next two days (the walk planner's day ahead), by its start in
     * Unix seconds, with every index the tile needs for the hour in progress. Asks only for [scale]'s
     * fields when it's known (the city's, from the forecast's time zone), and for both otherwise.
     */
    suspend fun fetchHourly(
        latitude: Double,
        longitude: Double,
        scale: Scale?,
        nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
    ): Result<AirQualityHours> =
        get(
            latitude,
            longitude,
            mapOf(
                "hourly" to AirQualityHourlyDto.fields(scale).joinToString(","),
                "timeformat" to "unixtime",
                "forecast_days" to "2",
            ),
        ) { body ->
            val response = json.decodeFromString(AirQualityHourlyResponse.serializer(), body)
            val responseScale = scale ?: Scale.forTimeZone(response.timezone)
            AirQualityHours(nowEpochSeconds, responseScale, toHourly(response, responseScale))
        }

    private suspend fun <T> get(
        latitude: Double,
        longitude: Double,
        fields: Map<String, String>,
        parse: (String) -> T,
    ): Result<T> =
        withContext(Dispatchers.IO) {
            runCatching {
                val url = BASE_URL
                    .toHttpUrl()
                    .newBuilder()
                    .addQueryParameter("latitude", latitude.toString())
                    .addQueryParameter("longitude", longitude.toString())
                    // The response's time zone picks the scale (European or US) when it isn't given.
                    .addQueryParameter("timezone", "auto")
                    .apply { fields.forEach { (name, value) -> addQueryParameter(name, value) } }
                    .build()
                okHttpClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
                    if (!response.isSuccessful) error(if (response.code >= 500) "server_error" else "generic")
                    parse(response.body?.string() ?: error("invalid_payload"))
                }
            }
        }

    companion object {
        private const val BASE_URL = "https://air-quality-api.open-meteo.com/v1/air-quality"

        /**
         * Each hour's reading on [scale], by default the one for the response's time zone. Only that
         * scale's fields need to be in the response.
         */
        internal fun toHourly(
            response: AirQualityHourlyResponse,
            scale: Scale = Scale.forTimeZone(response.timezone),
        ): Map<Long, AirQuality> {
            val h = response.hourly
            return h.time
                .mapIndexedNotNull { row, time ->
                    AirQuality
                        .from(scale, h.pm25.getOrNull(row), h.pm10.getOrNull(row)) {
                            when (it) {
                                Scale.US -> h.usAqi.at(row) to listOf(
                                    Pollutant.COARSE_PARTICLES to h.usAqiPm10.at(row),
                                    Pollutant.FINE_PARTICLES to h.usAqiPm25.at(row),
                                    Pollutant.OZONE to h.usAqiOzone.at(row),
                                    Pollutant.NITROGEN_DIOXIDE to h.usAqiNitrogenDioxide.at(row),
                                    Pollutant.SULPHUR_DIOXIDE to h.usAqiSulphurDioxide.at(row),
                                    Pollutant.CARBON_MONOXIDE to h.usAqiCarbonMonoxide.at(row),
                                )
                                Scale.EUROPEAN -> h.europeanAqi.at(row) to listOf(
                                    Pollutant.COARSE_PARTICLES to h.europeanAqiPm10.at(row),
                                    Pollutant.FINE_PARTICLES to h.europeanAqiPm25.at(row),
                                    Pollutant.OZONE to h.europeanAqiOzone.at(row),
                                    Pollutant.NITROGEN_DIOXIDE to h.europeanAqiNitrogenDioxide.at(row),
                                    Pollutant.SULPHUR_DIOXIDE to h.europeanAqiSulphurDioxide.at(row),
                                )
                            }
                        }?.let { time to it }
                }.toMap()
        }

        // A field missing from the response, or shorter than `time`, counts as no reading.
        private fun List<Int?>.at(row: Int): Int? = getOrNull(row)
    }
}
