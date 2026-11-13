package com.fangjet.weather.di

import com.fangjet.weather.analytics.AnalyticsBackend
import com.fangjet.weather.analytics.AnalyticsRepository
import com.fangjet.weather.analytics.FirebaseAnalyticsBackend
import com.fangjet.weather.data.WeatherFailureReporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class AnalyticsModule {

    @Binds
    abstract fun bindAnalyticsBackend(impl: FirebaseAnalyticsBackend): AnalyticsBackend

    companion object {
        /** Every failed weather request, foreground and background, as `weather_error`. */
        @Provides
        @Singleton
        fun provideWeatherFailureReporter(analytics: AnalyticsRepository): WeatherFailureReporter =
            WeatherFailureReporter { provider, request, errorKey -> analytics.logWeatherError(errorKey, provider, request) }
    }
}
