package com.fangjet.weather.data.remote

import com.fangjet.weather.data.OpenMeteoGeocodingResponse
import com.fangjet.weather.data.remote.dto.GeocodingDirectDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface GeocodingApi {

    @GET("geo/1.0/direct")
    suspend fun directSearch(
        @Query(value = "q") query: String,
        @Query(value = "limit") limit: Int = 8,
        @Query(value = "appid") apiKey: String,
    ): List<GeocodingDirectDto>

    @GET("https://open-meteo.com")
    suspend fun searchGeocodingMeteo(
        @Query("name") name: String,
        @Query("count") count: Int = 10,
        @Query("language") language: String = "en",
        @Query("format") format: String = "json"
    ): Response<OpenMeteoGeocodingResponse>
}