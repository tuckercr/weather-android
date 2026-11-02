package com.fangjet.weather.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherRepository
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.ui.pets.PetMood
import com.fangjet.weather.util.resolveWeatherUnits
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json

@HiltWorker
class WeatherUpdateWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted workerParams: WorkerParameters,
    preferencesRepository: PreferencesRepository,
    weatherRepository: WeatherRepository,
    remoteConfigRepository: RemoteConfigRepository,
    json: Json,
) : CoroutineWorker(context, workerParams) {

    private val logic = UpdateWorkerLogic(
        getSavedLocations = { preferencesRepository.savedLocations.first() },
        getActiveIndex = { preferencesRepository.activeLocationIndex.first() },
        getUnits = {
            // Honor the user's unit override so the background worker never writes a different
            // unit system into the cache than a foreground refresh would.
            val override = preferencesRepository.unitOverride.first()
            context.resolveWeatherUnits(override)
        },
        fetchCurrentWeather = { units, label, lat, lon ->
            if (lat != null && lon != null) {
                weatherRepository.fetchCurrentWeather(units, label, latitude = lat, longitude = lon)
            } else {
                weatherRepository.fetchCurrentWeather(units, label, cityQuery = label)
            }
        },
        fetchForecast = { units, label, lat, lon ->
            if (lat != null && lon != null) {
                weatherRepository.fetchForecast(units, latitude = lat, longitude = lon)
            } else {
                weatherRepository.fetchForecast(units, cityQuery = label)
            }
        },
        cacheCurrentWeather = { key, weather ->
            preferencesRepository.setCachedWeatherFor(
                key,
                json.encodeToString<CurrentWeather>(weather),
            )
        },
        cacheForecast = { key, forecast ->
            preferencesRepository.setCachedForecastFor(
                key,
                json.encodeToString<List<DayForecast>>(forecast),
            )
        },
        afterRefresh = { location, weather, forecast ->
            if (weather != null &&
                forecast != null &&
                remoteConfigRepository.rainNotificationsEnabled() &&
                preferencesRepository.notifyRain.first() &&
                context.canPostNotifications()
            ) {
                rainAlertFor(
                    forecast,
                    System.currentTimeMillis() / 1000,
                    weather.offsetSeconds,
                    preferencesRepository.lastRainAlertStart(),
                )?.let { rain ->
                    val (title, body) = buildRainAlertContent(
                        rain,
                        weather.cityName,
                        weather.offsetSeconds,
                        hasDog = preferencesRepository.showWalkCard.first(),
                    )
                    val mood = if (rain.snow) PetMood.SNOW else PetMood.RAIN
                    val southern = (location.latitude ?: 0.0) < 0
                    context.postWeatherNotification(WeatherNotification(NotificationKind.RAIN, title, body, mood, southern))
                    preferencesRepository.setLastRainAlertStart(rain.epochSeconds)
                }
            }
        },
    )

    override suspend fun doWork() = logic.doWork()
}
