package com.fangjet.weather.data

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Where an Open-Meteo request goes. With the commercial plan's key it goes to the customer host
 * ("customer-" before the free host's name) with the key as `apikey`; with no key it goes to the
 * free host, as local debug builds without the key do. Same rules as iOS's
 * `Services/OpenMeteoEndpoint.swift`.
 */
object OpenMeteoEndpoint {
    const val FORECAST = "https://api.open-meteo.com/v1/forecast"
    const val AIR_QUALITY = "https://air-quality-api.open-meteo.com/v1/air-quality"

    /** A builder for [freeUrl], moved to the customer host with the key when [apiKey] isn't blank. */
    fun builder(
        freeUrl: String,
        apiKey: String,
    ): HttpUrl.Builder {
        val key = apiKey.trim()
        val url = freeUrl.toHttpUrl()
        if (key.isEmpty()) return url.newBuilder()
        return url
            .newBuilder()
            .host("customer-${url.host}")
            .addQueryParameter("apikey", key)
    }
}
