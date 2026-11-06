package com.fangjet.weather.data

import com.fangjet.weather.data.remote.OpenWeatherApi
import com.fangjet.weather.data.remote.dto.CloudsDto
import com.fangjet.weather.data.remote.dto.CurrentWeatherResponse
import com.fangjet.weather.data.remote.dto.ForecastListItemDto
import com.fangjet.weather.data.remote.dto.ForecastResponse
import com.fangjet.weather.data.remote.dto.MainDto
import com.fangjet.weather.data.remote.dto.WeatherDescDto
import com.fangjet.weather.data.remote.dto.WindDto
import com.fangjet.weather.domain.WeatherUnits
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.ZoneOffset

class WeatherRepositoryTest {

    /** Open-Meteo source backed by a fake HTTP layer that always answers with [body]. */
    private fun openMeteo(
        body: String = """{"hourly":{}}""",
        failure: IOException? = null,
    ): OpenMeteoForecastSource {
        val client = OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                if (failure != null) throw failure
                okhttp3.Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()
        return OpenMeteoForecastSource(client, Json { ignoreUnknownKeys = true })
    }

    @Test
    fun `fetchCurrentWeather with coordinates sends coordinates and preserves location label`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), " test-key ", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.IMPERIAL,
                locationLabel = "Austin, TX, US",
                cityQuery = "Austin",
                latitude = 30.2672,
                longitude = -97.7431,
            )
            val weather = result.getOrThrow()

            assertEquals("Austin, TX, US", weather.cityName)
            assertEquals("Clear sky", weather.description)
            assertEquals(WeatherUnits.IMPERIAL, weather.units)
            assertNull(api.lastCurrentCityQuery)
            assertEquals(30.2672, api.lastCurrentLatitude ?: 0.0, 0.0001)
            assertEquals(-97.7431, api.lastCurrentLongitude ?: 0.0, 0.0001)
            assertEquals("test-key", api.lastCurrentApiKey)
            assertEquals("imperial", api.lastCurrentUnits)
        }

    @Test
    fun `fetchCurrentWeather trims city query when coordinates are absent`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "  Denver  ",
            )
            val weather = result.getOrThrow()

            assertEquals("OpenWeather City", weather.cityName)
            assertEquals("Denver", api.lastCurrentCityQuery)
            assertNull(api.lastCurrentLatitude)
            assertNull(api.lastCurrentLongitude)
            assertEquals("metric", api.lastCurrentUnits)
        }

    @Test
    fun `fetchCurrentWeather with partial coordinates falls back to city query`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "  Austin  ",
                latitude = 30.2672,
                longitude = null,
            )

            result.getOrThrow()
            assertEquals("Austin", api.lastCurrentCityQuery)
            assertNull(api.lastCurrentLatitude)
            assertNull(api.lastCurrentLongitude)
            assertEquals(1, api.currentWeatherCallCount)
        }

    @Test
    fun `fetchCurrentWeather with blank city query fails before calling api`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "   ",
            )

            val error = result.exceptionOrNull()
            assertEquals("empty_query", error?.message)
            assertEquals(0, api.currentWeatherCallCount)
        }

    @Test
    fun `fetchForecast with missing api key fails before calling api`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), "   ", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "Austin",
            )

            val error = result.exceptionOrNull()
            assertEquals("missing_api_key", error?.message)
            assertEquals(0, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast trims city query when coordinates are absent`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                forecastResponse = ForecastResponse(
                    list = listOf(
                        forecastItem(
                            epochSeconds = 1_704_067_200L,
                            temperature = 8.0,
                            main = "Clouds",
                        ),
                    ),
                    city = null,
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.IMPERIAL,
                cityQuery = "  Denver  ",
            )
            val forecast = result.getOrThrow()

            assertEquals(1, forecast.size)
            assertEquals("Denver", api.lastForecastCityQuery)
            assertNull(api.lastForecastLatitude)
            assertNull(api.lastForecastLongitude)
            assertEquals("test-key", api.lastForecastApiKey)
            assertEquals("imperial", api.lastForecastUnits)
        }

    @Test
    fun `fetchForecast with partial coordinates and blank city query fails before calling api`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "   ",
                latitude = null,
                longitude = -97.7431,
            )

            val error = result.exceptionOrNull()
            assertEquals("empty_query", error?.message)
            assertEquals(0, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast with blank city query fails before calling api`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "   ",
            )

            val error = result.exceptionOrNull()
            assertEquals("empty_query", error?.message)
            assertEquals(0, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast maps forecast slots through aggregator`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                forecastResponse = ForecastResponse(
                    list = listOf(
                        forecastItem(
                            epochSeconds = 1_704_067_200L + 3600,
                            temperature = 1.0,
                            main = "Morning",
                        ),
                        forecastItem(
                            epochSeconds = 1_704_067_200L + 12 * 3600,
                            temperature = 10.0,
                            main = "Noon",
                        ),
                    ),
                    city = null,
                ),
            )
            val openMeteoBody =
                """{"utc_offset_seconds":0,"hourly":{"time":[4102444800],"temperature_2m":[21.0],
                "weather_code":[0],"is_day":[1],"uv_index":[6.5],"shortwave_radiation":[800.0],
                "precipitation_probability":[10]}}"""
            val repository = WeatherRepository(api, openMeteo(openMeteoBody), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                latitude = 30.2672,
                longitude = -97.7431,
            )
            val forecast = result.getOrThrow()

            // Coordinates are served by Open-Meteo; OpenWeatherMap's forecast is not called.
            assertEquals(0, api.forecastCallCount)
            val slot = forecast.single().hourlySlots.single()
            assertEquals("Clear", forecast.single().conditionMain)
            assertEquals(21.0, slot.temperature, 0.0001)
            assertEquals(6.5, slot.uvIndex ?: -1.0, 0.0001)
            assertEquals(10, slot.precipitationChance)
        }

    @Test
    fun `Open-Meteo network failures map to offline`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val noNetwork = openMeteo(failure = java.net.UnknownHostException("Unable to resolve host"))
            val repository = WeatherRepository(api, noNetwork, "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(WeatherUnits.METRIC, latitude = 39.7, longitude = -105.0)

            assertEquals("offline", result.exceptionOrNull()?.message)
        }

    @Test
    fun `fetchForecast maps network failures to network message`() =
        runBlocking {
            val api = FakeOpenWeatherApi(forecastThrowable = IOException("timeout"))
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "Austin",
            )

            val error = result.exceptionOrNull()
            assertEquals("offline", error?.message)
            assertEquals(1, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast maps OpenWeather error body message`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                forecastThrowable = httpException(
                    code = 404,
                    body = """{"cod":"404","message":"city not found"}""",
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "Missing City",
            )

            val error = result.exceptionOrNull()
            assertEquals("city_not_found", error?.message)
            assertEquals(1, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast falls back to http code when OpenWeather error message is blank`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                forecastThrowable = httpException(
                    code = 401,
                    body = """{"cod":"401","message":"   "}""",
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "Austin",
            )

            val error = result.exceptionOrNull()
            assertEquals("http_401", error?.message)
            assertEquals(1, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast maps missing weather entry to invalid payload`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                forecastResponse = ForecastResponse(
                    list = listOf(
                        forecastItem(
                            epochSeconds = 1_704_067_200L,
                            temperature = 21.0,
                            main = "Broken",
                        ).copy(weather = emptyList()),
                    ),
                    city = null,
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchForecast(
                units = WeatherUnits.METRIC,
                cityQuery = "Broken City",
            )

            val error = result.exceptionOrNull()
            assertEquals("invalid_payload", error?.message)
            assertEquals(1, api.forecastCallCount)
        }

    @Test
    fun `fetchCurrentWeather maps network failures to network message`() =
        runBlocking {
            val api = FakeOpenWeatherApi(currentThrowable = IOException("timeout"))
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "Austin",
            )

            val error = result.exceptionOrNull()
            assertEquals("offline", error?.message)
        }

    @Test
    fun `fetchCurrentWeather maps OpenWeather error body message`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                currentThrowable = httpException(
                    code = 404,
                    body = """{"cod":"404","message":"city not found"}""",
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "Missing City",
            )

            val error = result.exceptionOrNull()
            assertEquals("city_not_found", error?.message)
        }

    @Test
    fun `fetchCurrentWeather falls back to http code when OpenWeather error body is malformed`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                currentThrowable = httpException(
                    code = 500,
                    body = """{"cod":"500","message":""",
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "Austin",
            )

            val error = result.exceptionOrNull()
            assertEquals("http_500", error?.message)
            assertEquals(1, api.currentWeatherCallCount)
        }

    @Test
    fun `fetchCurrentWeather maps missing weather entry to invalid payload`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                currentResponse = CurrentWeatherResponse(
                    name = "Broken City",
                    weather = emptyList(),
                    main = MainDto(
                        temp = 21.0,
                        feelsLike = 20.0,
                        humidity = 55,
                    ),
                    wind = WindDto(speed = 4.2),
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val result = repository.fetchCurrentWeather(
                units = WeatherUnits.METRIC,
                cityQuery = "Broken City",
            )

            val error = result.exceptionOrNull()
            assertEquals("invalid_payload", error?.message)
        }

    @Test
    fun `fetchCurrentWeather maps all extended fields correctly`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                currentResponse = CurrentWeatherResponse(
                    name = "Portland",
                    weather = listOf(
                        WeatherDescDto(
                            main = "Rain",
                            description = "light rain",
                            icon = "10d",
                        ),
                    ),
                    main = MainDto(
                        temp = 14.0,
                        feelsLike = 12.5,
                        tempMin = 10.0,
                        tempMax = 17.0,
                        humidity = 88,
                        pressure = 1005,
                    ),
                    wind = WindDto(speed = 3.6, deg = 200, gust = 6.1),
                    visibility = 8000,
                    clouds = CloudsDto(all = 90),
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val weather = repository
                .fetchCurrentWeather(
                    units = WeatherUnits.METRIC,
                    cityQuery = "Portland",
                ).getOrThrow()

            assertEquals(88, weather.humidityPercent)
            assertEquals(1005, weather.pressureHpa)
            assertEquals(3.6, weather.windSpeed, 0.001)
            assertEquals(200, weather.windDeg)
            assertEquals(6.1, weather.windGust ?: 0.0, 0.001)
            assertEquals(8000, weather.visibilityMeters)
            assertEquals(90, weather.cloudPercent)
            assertEquals(10.0, weather.tempMin, 0.001)
            assertEquals(17.0, weather.tempMax, 0.001)
            assertEquals("Light rain", weather.description)
        }

    @Test
    fun `fetchCurrentWeather maps null visibility and clouds to safe defaults`() =
        runBlocking {
            val api = FakeOpenWeatherApi(
                currentResponse = CurrentWeatherResponse(
                    name = "Fog City",
                    weather = listOf(
                        WeatherDescDto(
                            main = "Fog",
                            description = "dense fog",
                            icon = "50d",
                        ),
                    ),
                    main = MainDto(temp = 10.0, feelsLike = 9.0, humidity = 95),
                    wind = WindDto(speed = 0.5),
                    visibility = null,
                    clouds = null,
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val weather = repository
                .fetchCurrentWeather(
                    units = WeatherUnits.METRIC,
                    cityQuery = "Fog City",
                ).getOrThrow()

            assertEquals(null, weather.visibilityMeters)
            assertEquals(0, weather.cloudPercent)
        }

    @Test
    fun `fetchForecast hourly slots carry wind, humidity, and pressure`() =
        runBlocking {
            val epochSeconds = 1_704_067_200L + 12 * 3600 // 2024-01-01T12:00:00Z
            val api = FakeOpenWeatherApi(
                forecastResponse = ForecastResponse(
                    list = listOf(
                        ForecastListItemDto(
                            dt = epochSeconds,
                            main = MainDto(
                                temp = 18.0,
                                feelsLike = 17.0,
                                humidity = 72,
                                pressure = 1012,
                            ),
                            weather = listOf(
                                WeatherDescDto(
                                    main = "Clouds",
                                    description = "broken clouds",
                                    icon = "04d",
                                ),
                            ),
                            wind = WindDto(speed = 4.5, deg = 135),
                        ),
                    ),
                    city = null,
                ),
            )
            val repository = WeatherRepository(api, openMeteo(), "test-key", ZoneOffset.UTC, Json)

            val day = repository
                .fetchForecast(
                    units = WeatherUnits.METRIC,
                    cityQuery = "Portland",
                ).getOrThrow()
                .single()

            val slot = day.hourlySlots.single()
            assertEquals(4.5, slot.windSpeed, 0.001)
            assertEquals(135, slot.windDeg)
            assertEquals(72, slot.humidity)
            assertEquals(1012, slot.pressure)
            assertEquals("04d", slot.iconCode)
            assertEquals("Broken clouds", slot.description)
        }

    // --- the current reading from Open-Meteo, OpenWeather as the backup ---

    /** Records what it's asked and answers [answer]. */
    private class RecordingPlaceNames(
        private val answer: String?,
    ) : PlaceNaming {
        val requests = mutableListOf<Pair<Double, Double>>()

        override suspend fun name(
            latitude: Double,
            longitude: Double,
        ): String? {
            requests += latitude to longitude
            return answer
        }
    }

    private fun currentRepository(
        api: FakeOpenWeatherApi = FakeOpenWeatherApi(),
        openMeteo: OpenMeteoForecastSource = openMeteo(OPEN_METEO_CURRENT_BODY),
        placeNames: PlaceNaming = PlaceNaming.None,
        apiKey: String = "test-key",
        openMeteoEnabled: Boolean = true,
    ) = WeatherRepository(
        api,
        openMeteo,
        apiKey,
        ZoneOffset.UTC,
        Json,
        forecastSourcePolicy = { openMeteoEnabled },
        placeNames = placeNames,
    )

    @Test
    fun `fetchCurrentWeather with coordinates uses Open-Meteo's reading`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val placeNames = RecordingPlaceNames("Knoxville")
            val repository = currentRepository(api = api, placeNames = placeNames)

            val weather = repository
                .fetchCurrentWeather(WeatherUnits.IMPERIAL, "Austin, TX, US", latitude = 30.2672, longitude = -97.7431)
                .getOrThrow()

            assertEquals("Austin, TX, US", weather.cityName)
            assertEquals(63.3, weather.temperature, 0.001)
            assertEquals(63.8, weather.feelsLike, 0.001)
            assertEquals(0, api.currentWeatherCallCount)
            assertEquals("A saved city keeps its own label", 0, placeNames.requests.size)
        }

    @Test
    fun `fetchCurrentWeather names My Location with the phone`() =
        runBlocking {
            val placeNames = RecordingPlaceNames("Knoxville")
            val repository = currentRepository(placeNames = placeNames)

            val weather = repository.fetchCurrentWeather(WeatherUnits.IMPERIAL, latitude = 35.9168, longitude = -83.9597).getOrThrow()

            assertEquals("Knoxville", weather.cityName)
            assertEquals(listOf(35.9168 to -83.9597), placeNames.requests)
        }

    @Test
    fun `fetchCurrentWeather calls My Location by its label when the phone can't name it`() =
        runBlocking {
            val repository = currentRepository(placeNames = RecordingPlaceNames(null))

            val weather = repository.fetchCurrentWeather(WeatherUnits.IMPERIAL, latitude = 35.9168, longitude = -83.9597).getOrThrow()

            assertEquals("My Location", weather.cityName)
        }

    @Test
    fun `fetchCurrentWeather falls back to OpenWeather when Open-Meteo fails, without a lookup`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val placeNames = RecordingPlaceNames("Knoxville")
            val repository = currentRepository(api = api, openMeteo = openMeteo(failure = IOException("down")), placeNames = placeNames)

            val weather = repository.fetchCurrentWeather(WeatherUnits.IMPERIAL, latitude = 35.9168, longitude = -83.9597).getOrThrow()

            assertEquals("OpenWeather City", weather.cityName)
            assertEquals(1, api.currentWeatherCallCount)
            assertEquals("No lookup is spent on a failed reading", 0, placeNames.requests.size)
        }

    @Test
    fun `fetchCurrentWeather falls back to OpenWeather without a temperature`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = currentRepository(api = api, openMeteo = openMeteo("""{"current":{"time":1793986200}}"""))

            repository.fetchCurrentWeather(WeatherUnits.IMPERIAL, "Knoxville", latitude = 1.0, longitude = 2.0).getOrThrow()

            assertEquals(1, api.currentWeatherCallCount)
        }

    @Test
    fun `fetchCurrentWeather uses OpenWeather when open_meteo_enabled is off`() =
        runBlocking {
            val api = FakeOpenWeatherApi()
            val repository = currentRepository(api = api, openMeteoEnabled = false)

            val weather = repository.fetchCurrentWeather(WeatherUnits.IMPERIAL, latitude = 1.0, longitude = 2.0).getOrThrow()

            assertEquals("OpenWeather City", weather.cityName)
            assertEquals(1, api.currentWeatherCallCount)
        }

    @Test
    fun `Open-Meteo's reading needs no OpenWeather key`() =
        runBlocking {
            val repository = currentRepository(apiKey = " ", placeNames = RecordingPlaceNames("Knoxville"))

            val weather = repository.fetchCurrentWeather(WeatherUnits.IMPERIAL, latitude = 1.0, longitude = 2.0).getOrThrow()

            assertEquals(63.3, weather.temperature, 0.001)
        }

    // --- open_meteo_enabled (ForecastSourcePolicy) ---

    private val openMeteoBody =
        """{"utc_offset_seconds":0,"hourly":{"time":[4102444800],"temperature_2m":[21.0],
        "weather_code":[0],"is_day":[1],"uv_index":[6.5],"shortwave_radiation":[800.0],
        "precipitation_probability":[10]}}"""

    private fun owmForecast() =
        ForecastResponse(
            list = listOf(forecastItem(epochSeconds = 1_704_067_200L, temperature = 8.0, main = "Clouds")),
            city = null,
        )

    @Test
    fun `fetchForecast with coordinates uses Open-Meteo when open_meteo_enabled is on`() =
        runBlocking {
            val api = FakeOpenWeatherApi(forecastResponse = owmForecast())
            val repository = WeatherRepository(
                api,
                openMeteo(openMeteoBody),
                "test-key",
                ZoneOffset.UTC,
                Json,
                forecastSourcePolicy = { true },
            )

            val forecast = repository.fetchForecast(WeatherUnits.METRIC, latitude = 30.27, longitude = -97.74).getOrThrow()

            assertEquals(0, api.forecastCallCount)
            assertEquals("Clear", forecast.single().conditionMain)
        }

    @Test
    fun `fetchForecast with coordinates uses OpenWeatherMap by lat-lon when open_meteo_enabled is off`() =
        runBlocking {
            val api = FakeOpenWeatherApi(forecastResponse = owmForecast())
            // Open-Meteo would fail if it were called, so success proves it was skipped.
            val unreachableOpenMeteo = openMeteo(failure = IOException("Open-Meteo must not be called"))
            val repository = WeatherRepository(
                api,
                unreachableOpenMeteo,
                " test-key ",
                ZoneOffset.UTC,
                Json,
                forecastSourcePolicy = { false },
            )

            val forecast = repository
                .fetchForecast(WeatherUnits.IMPERIAL, cityQuery = "Austin", latitude = 30.27, longitude = -97.74)
                .getOrThrow()

            assertEquals(1, api.forecastCallCount)
            assertNull(api.lastForecastCityQuery)
            assertEquals(30.27, api.lastForecastLatitude ?: 0.0, 0.0001)
            assertEquals(-97.74, api.lastForecastLongitude ?: 0.0, 0.0001)
            assertEquals("test-key", api.lastForecastApiKey)
            assertEquals("imperial", api.lastForecastUnits)
            assertEquals("Clouds", forecast.single().conditionMain)
        }

    @Test
    fun `fetchForecast with open_meteo_enabled off and no api key fails before calling api`() =
        runBlocking {
            val api = FakeOpenWeatherApi(forecastResponse = owmForecast())
            val repository = WeatherRepository(
                api,
                openMeteo(openMeteoBody),
                "  ",
                ZoneOffset.UTC,
                Json,
                forecastSourcePolicy = { false },
            )

            val result = repository.fetchForecast(WeatherUnits.METRIC, latitude = 30.27, longitude = -97.74)

            assertEquals("missing_api_key", result.exceptionOrNull()?.message)
            assertEquals(0, api.forecastCallCount)
        }

    @Test
    fun `fetchForecast without coordinates uses the city query whatever open_meteo_enabled says`() =
        runBlocking {
            val api = FakeOpenWeatherApi(forecastResponse = owmForecast())
            val repository = WeatherRepository(
                api,
                openMeteo(openMeteoBody),
                "test-key",
                ZoneOffset.UTC,
                Json,
                forecastSourcePolicy = { true },
            )

            repository.fetchForecast(WeatherUnits.METRIC, cityQuery = "Denver").getOrThrow()

            assertEquals("Denver", api.lastForecastCityQuery)
            assertNull(api.lastForecastLatitude)
        }

    @Test
    fun `a failing forecast source policy falls back to Open-Meteo`() =
        runBlocking {
            val api = FakeOpenWeatherApi(forecastResponse = owmForecast())
            val repository = WeatherRepository(
                api,
                openMeteo(openMeteoBody),
                "test-key",
                ZoneOffset.UTC,
                Json,
                forecastSourcePolicy = { throw IllegalStateException("Remote Config unavailable") },
            )

            val forecast = repository.fetchForecast(WeatherUnits.METRIC, latitude = 30.27, longitude = -97.74).getOrThrow()

            assertEquals(0, api.forecastCallCount)
            assertEquals("Clear", forecast.single().conditionMain)
        }

    private class FakeOpenWeatherApi(
        private val currentResponse: CurrentWeatherResponse = currentWeatherResponse(),
        private val forecastResponse: ForecastResponse = ForecastResponse(
            list = emptyList(),
            city = null,
        ),
        private val currentThrowable: Throwable? = null,
        private val forecastThrowable: Throwable? = null,
    ) : OpenWeatherApi {
        var currentWeatherCallCount = 0
        var forecastCallCount = 0
        var lastCurrentCityQuery: String? = null
        var lastCurrentLatitude: Double? = null
        var lastCurrentLongitude: Double? = null
        var lastCurrentApiKey: String? = null
        var lastCurrentUnits: String? = null
        var lastForecastCityQuery: String? = null
        var lastForecastLatitude: Double? = null
        var lastForecastLongitude: Double? = null
        var lastForecastApiKey: String? = null
        var lastForecastUnits: String? = null

        override suspend fun currentWeather(
            cityQuery: String?,
            latitude: Double?,
            longitude: Double?,
            apiKey: String,
            units: String,
        ): CurrentWeatherResponse {
            currentWeatherCallCount += 1
            lastCurrentCityQuery = cityQuery
            lastCurrentLatitude = latitude
            lastCurrentLongitude = longitude
            lastCurrentApiKey = apiKey
            lastCurrentUnits = units
            currentThrowable?.let { throw it }
            return currentResponse
        }

        override suspend fun forecast(
            cityQuery: String?,
            latitude: Double?,
            longitude: Double?,
            apiKey: String,
            units: String,
        ): ForecastResponse {
            forecastCallCount += 1
            lastForecastCityQuery = cityQuery
            lastForecastLatitude = latitude
            lastForecastLongitude = longitude
            lastForecastApiKey = apiKey
            lastForecastUnits = units
            forecastThrowable?.let { throw it }
            return forecastResponse
        }
    }

    private companion object {
        fun currentWeatherResponse() =
            CurrentWeatherResponse(
                name = "OpenWeather City",
                weather = listOf(
                    WeatherDescDto(
                        main = "Clear",
                        description = "clear sky",
                        icon = "01d",
                    ),
                ),
                main = MainDto(
                    temp = 72.5,
                    feelsLike = 70.0,
                    humidity = 42,
                ),
                wind = WindDto(speed = 5.5),
            )

        fun forecastItem(
            epochSeconds: Long,
            temperature: Double,
            main: String,
        ) = ForecastListItemDto(
            dt = epochSeconds,
            main = MainDto(
                temp = temperature,
                feelsLike = temperature - 1.0,
                humidity = 50,
            ),
            weather = listOf(
                WeatherDescDto(
                    main = main,
                    description = "${main.lowercase()} description",
                    icon = "01d",
                ),
            ),
            wind = WindDto(speed = 1.0),
        )

        fun httpException(
            code: Int,
            body: String,
        ) = HttpException(
            Response.error<CurrentWeatherResponse>(
                code,
                body.toResponseBody(),
            ),
        )
    }
}

/** Open-Meteo's current reading at 12:30 in Knoxville on 11-06: 63.3° feeling like 63.8°, clear. */
internal const val OPEN_METEO_CURRENT_BODY =
    """{"utc_offset_seconds":-18000,"current":{"time":1793986200,"interval":900,"temperature_2m":63.3,
    "apparent_temperature":63.8,"relative_humidity_2m":62,"weather_code":0,"cloud_cover":5,
    "pressure_msl":1014.8,"wind_speed_10m":1.1,"wind_direction_10m":267,"wind_gusts_10m":9.8,"visibility":29300.0,"is_day":1},
    "daily":{"time":[1793941200],"sunrise":[1793966580],"sunset":[1794004500],
    "temperature_2m_max":[65.6],"temperature_2m_min":[48.0]}}"""
