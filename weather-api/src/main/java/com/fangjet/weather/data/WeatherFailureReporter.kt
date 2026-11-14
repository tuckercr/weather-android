package com.fangjet.weather.data

/** The weather services [WeatherRepository] asks, by their analytics name. */
enum class WeatherProvider(
    val key: String,
) {
    OPEN_METEO("open_meteo"),
    OPENWEATHER("openweather"),
}

/** What was asked for, by its analytics name. */
enum class WeatherRequestKind(
    val key: String,
) {
    CURRENT("current"),
    FORECAST("forecast"),
    AIR_QUALITY("air_quality"),
}

/**
 * Told about every failed request to a weather service, including the ones a fallback covered
 * (Open-Meteo's reading failing before OpenWeather's is used) and air quality failing quietly.
 * Supplied by the app (analytics' `weather_error`) so this module stays free of Firebase.
 */
fun interface WeatherFailureReporter {
    fun failed(
        provider: WeatherProvider,
        request: WeatherRequestKind,
        errorKey: String,
    )

    companion object {
        val None = WeatherFailureReporter { _, _, _ -> }
    }
}

/**
 * The category of a failed fetch, as the weather screen shows it and analytics reports it:
 * `offline`, `city_not_found` and so on, or `generic`.
 */
fun weatherErrorKey(error: Throwable): String =
    when (val message = error.message) {
        "missing_api_key", "bad_api_key", "empty_query",
        "city_not_found", "rate_limited", "server_error", "offline",
        -> message
        else -> "generic"
    }
