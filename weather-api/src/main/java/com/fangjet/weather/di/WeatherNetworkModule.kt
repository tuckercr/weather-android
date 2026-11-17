package com.fangjet.weather.di

import com.fangjet.weather.data.remote.GeocodingApi
import com.fangjet.weather.data.remote.OpenWeatherApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object WeatherNetworkModule {

    /** Qualifier name for the OpenWeatherMap API key string binding. */
    const val OWM_API_KEY = "owm_api_key"

    /** Qualifier name for the Open-Meteo commercial API key string binding (blank uses the free hosts). */
    const val OPEN_METEO_API_KEY = "open_meteo_api_key"

    private const val BASE_URL = "https://api.openweathermap.org/"

    @Provides
    @Singleton
    fun provideJson(): Json =
        Json {
            ignoreUnknownKeys = true
            coerceInputValues = true
        }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient =
        OkHttpClient
            .Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

    @Provides
    @Singleton
    fun provideRetrofit(
        okHttpClient: OkHttpClient,
        json: Json,
    ): Retrofit {
        val contentType = "application/json".toMediaType()
        return Retrofit
            .Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(json.asConverterFactory(contentType))
            .build()
    }

    @Provides
    @Singleton
    fun provideOpenWeatherApi(retrofit: Retrofit): OpenWeatherApi = retrofit.create(OpenWeatherApi::class.java)

    @Provides
    @Singleton
    fun provideGeocodingApi(retrofit: Retrofit): GeocodingApi = retrofit.create(GeocodingApi::class.java)
}
