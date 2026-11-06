package com.fangjet.weather.domain

/**
 * A row from the geocoder shown while searching; [weatherLat]/[weatherLon] are used for the
 * forecasts when the user picks this suggestion. [label] is what's saved ("Malmö, Skåne County,
 * SE"); the Add location screen shows [name] above [detail].
 */
data class CitySuggestion(
    val label: String,
    val weatherLat: Double,
    val weatherLon: Double,
    /** The place's own name ("Malmö"): the start of [label]. */
    val name: String = label.substringBefore(',').trim(),
    /** Its ISO country code ("SE"): the end of [label]. */
    val countryCode: String = label.substringAfterLast(',', "").trim(),
    /** People living there, when the provider says. OpenWeather never does. */
    val population: Long? = null,
) {
    /** The rest of [label] after the name: "Skåne County, SE". */
    val detail: String
        get() = label.removePrefix(name).removePrefix(",").trim()

    /** The name and country, for the Add location button: "Malmö, SE". */
    val shortLabel: String
        get() = if (countryCode.isEmpty()) name else "$name, $countryCode"
}
