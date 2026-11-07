package com.fangjet.weather.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Open-Meteo's hourly air quality forecast with `timeformat=unixtime`: one request feeds both the
 * walk planner and the Air quality tile, which shows the hour in progress.
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
        const val FIELDS =
            "us_aqi,us_aqi_pm2_5,us_aqi_pm10,us_aqi_ozone,us_aqi_nitrogen_dioxide,us_aqi_sulphur_dioxide," +
                "us_aqi_carbon_monoxide,european_aqi,european_aqi_pm2_5,european_aqi_pm10,european_aqi_ozone," +
                "european_aqi_nitrogen_dioxide,european_aqi_sulphur_dioxide,pm2_5,pm10"
    }
}
