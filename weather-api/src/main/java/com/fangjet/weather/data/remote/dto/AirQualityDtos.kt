package com.fangjet.weather.data.remote.dto

import com.fangjet.weather.domain.AirQuality.Scale
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open-Meteo's hourly air quality forecast with `timeformat=unixtime`: one request feeds both the
 * walk planner and the Air quality tile, which shows the hour in progress. A response may hold only
 * one scale's fields ([AirQualityHourlyDto.fields]).
 */
@Serializable
data class AirQualityHourlyResponse(
    @SerialName("timezone") val timezone: String? = null,
    @SerialName("hourly") val hourly: AirQualityHourlyDto = AirQualityHourlyDto(),
)

/** One list per field, an entry per hour. */
@Serializable
data class AirQualityHourlyDto(
    @SerialName("time") val time: List<Long> = emptyList(),
    @SerialName("us_aqi") val usAqi: List<Int?> = emptyList(),
    @SerialName("us_aqi_pm2_5") val usAqiPm25: List<Int?> = emptyList(),
    @SerialName("us_aqi_pm10") val usAqiPm10: List<Int?> = emptyList(),
    @SerialName("us_aqi_ozone") val usAqiOzone: List<Int?> = emptyList(),
    @SerialName("us_aqi_nitrogen_dioxide") val usAqiNitrogenDioxide: List<Int?> = emptyList(),
    @SerialName("us_aqi_sulphur_dioxide") val usAqiSulphurDioxide: List<Int?> = emptyList(),
    @SerialName("us_aqi_carbon_monoxide") val usAqiCarbonMonoxide: List<Int?> = emptyList(),
    @SerialName("european_aqi") val europeanAqi: List<Int?> = emptyList(),
    @SerialName("european_aqi_pm2_5") val europeanAqiPm25: List<Int?> = emptyList(),
    @SerialName("european_aqi_pm10") val europeanAqiPm10: List<Int?> = emptyList(),
    @SerialName("european_aqi_ozone") val europeanAqiOzone: List<Int?> = emptyList(),
    @SerialName("european_aqi_nitrogen_dioxide") val europeanAqiNitrogenDioxide: List<Int?> = emptyList(),
    @SerialName("european_aqi_sulphur_dioxide") val europeanAqiSulphurDioxide: List<Int?> = emptyList(),
    // Particle masses (µg/m³), which tell dust from smoke.
    @SerialName("pm2_5") val pm25: List<Double?> = emptyList(),
    @SerialName("pm10") val pm10: List<Double?> = emptyList(),
) {
    companion object {
        // Each scale's index and the sub-indices behind it; pm2_5 and pm10 (masses) tell dust from smoke.
        private val US_FIELDS = listOf(
            "us_aqi",
            "us_aqi_pm2_5",
            "us_aqi_pm10",
            "us_aqi_ozone",
            "us_aqi_nitrogen_dioxide",
            "us_aqi_sulphur_dioxide",
            "us_aqi_carbon_monoxide",
        )
        private val EUROPEAN_FIELDS = listOf(
            "european_aqi",
            "european_aqi_pm2_5",
            "european_aqi_pm10",
            "european_aqi_ozone",
            "european_aqi_nitrogen_dioxide",
            "european_aqi_sulphur_dioxide",
        )
        private val MASS_FIELDS = listOf("pm2_5", "pm10")

        /**
         * The hourly fields to ask for: only [scale]'s (9 for the US, 8 for Europe), which keeps the
         * request to one of Open-Meteo's API calls (a request over 10 variables counts as more), or
         * both scales' (15) when the city's scale isn't known. Same lists as iOS's `AirQualitySource`.
         */
        fun fields(scale: Scale?): List<String> =
            when (scale) {
                Scale.US -> US_FIELDS + MASS_FIELDS
                Scale.EUROPEAN -> EUROPEAN_FIELDS + MASS_FIELDS
                null -> US_FIELDS + EUROPEAN_FIELDS + MASS_FIELDS
            }
    }
}
