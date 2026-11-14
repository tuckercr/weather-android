package com.fangjet.weather.data

import com.fangjet.weather.data.remote.OpenWeatherApi
import com.fangjet.weather.data.remote.dto.CurrentWeatherResponse
import com.fangjet.weather.data.remote.dto.ForecastResponse
import com.fangjet.weather.data.remote.dto.MainDto
import com.fangjet.weather.data.remote.dto.WeatherDescDto
import com.fangjet.weather.data.remote.dto.WindDto
import com.fangjet.weather.domain.WeatherUnits
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException
import java.time.ZoneOffset

/**
 * Every failed request to a weather service is reported (analytics' `weather_error`), with its
 * provider and what was asked for, including failures a fallback covers. Same cases as iOS's.
 */
class WeatherFailureReportingTest {
    private val json = Json { ignoreUnknownKeys = true }

    private data class Failure(
        val provider: String,
        val request: String,
        val errorKey: String,
    )

    private val failures = mutableListOf<Failure>()
    private val reporter = WeatherFailureReporter { provider, request, errorKey ->
        failures += Failure(provider.key, request.key, errorKey)
    }

    /** A client answering every request with [body], or failing with [failure] or [code]. */
    private fun client(
        body: String = "{}",
        failure: IOException? = null,
        code: Int = 200,
    ): OkHttpClient =
        OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                if (failure != null) throw failure
                okhttp3.Response
                    .Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(code)
                    .message("OK")
                    .body(body.toResponseBody("application/json".toMediaType()))
                    .build()
            }.build()

    private val hour = 4_102_444_800L
    private val forecastBody =
        """{"utc_offset_seconds":0,"timezone":"Europe/London",
        "hourly":{"time":[$hour],"temperature_2m":[21.0],"weather_code":[0],"is_day":[1]}}"""

    private fun openWeather(
        current: Throwable? = null,
        forecast: Throwable? = null,
    ): OpenWeatherApi {
        val api = mockk<OpenWeatherApi>()
        coEvery { api.currentWeather(any(), any(), any(), any(), any()) } answers {
            current?.let { throw it }
            CurrentWeatherResponse(
                name = "OpenWeather City",
                weather = listOf(WeatherDescDto(main = "Clear", description = "clear sky", icon = "01d")),
                main = MainDto(temp = 20.0, feelsLike = 19.0, humidity = 40),
                wind = WindDto(speed = 1.0),
            )
        }
        coEvery { api.forecast(any(), any(), any(), any(), any()) } answers {
            forecast?.let { throw it }
            ForecastResponse(list = emptyList(), city = null)
        }
        return api
    }

    private fun repository(
        api: OpenWeatherApi = openWeather(),
        openMeteo: OkHttpClient = client(OPEN_METEO_CURRENT_BODY),
        air: OkHttpClient? = null,
        openMeteoEnabled: Boolean = true,
    ) = WeatherRepository(
        api,
        OpenMeteoForecastSource(openMeteo, json),
        "test-key",
        ZoneOffset.UTC,
        Json,
        forecastSourcePolicy = { openMeteoEnabled },
        airQuality = air?.let { AirQualitySource(it, json) },
        failures = reporter,
    )

    @Test
    fun `Open-Meteo's reading failing is reported even when OpenWeather's covers it`() =
        runBlocking {
            val repository = repository(openMeteo = client(failure = IOException("down")))

            val weather = repository.fetchCurrentWeather(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0)

            assertTrue(weather.isSuccess)
            assertEquals(listOf(Failure("open_meteo", "current", "offline")), failures)
        }

    @Test
    fun `Open-Meteo sending no reading isn't a failed request`() =
        runBlocking {
            val repository = repository(openMeteo = client("""{"current":{"time":1793986200}}"""))

            repository.fetchCurrentWeather(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0).getOrThrow()

            assertEquals(emptyList<Failure>(), failures)
        }

    @Test
    fun `a name-only city with an empty name never reaches a service, so isn't reported`() =
        runBlocking {
            val repository = repository()

            assertTrue(repository.fetchCurrentWeather(WeatherUnits.METRIC, cityQuery = " ").isFailure)
            assertTrue(repository.fetchForecast(WeatherUnits.METRIC, cityQuery = " ").isFailure)

            assertEquals(emptyList<Failure>(), failures)
        }

    @Test
    fun `both readings failing reports both`() =
        runBlocking {
            val notFound = HttpException(Response.error<Any>(404, """{"cod":"404","message":"city not found"}""".toResponseBody()))
            val repository = repository(
                api = openWeather(current = notFound),
                openMeteo = client(code = 503),
            )

            val weather = repository.fetchCurrentWeather(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0)

            assertTrue(weather.isFailure)
            assertEquals(
                listOf(Failure("open_meteo", "current", "server_error"), Failure("openweather", "current", "city_not_found")),
                failures,
            )
        }

    @Test
    fun `a name-only city's reading and forecast failing are OpenWeather's`() =
        runBlocking {
            val repository = repository(api = openWeather(current = IOException("down"), forecast = IOException("down")))

            repository.fetchCurrentWeather(WeatherUnits.METRIC, cityQuery = "Austin")
            repository.fetchForecast(WeatherUnits.METRIC, cityQuery = "Austin")

            assertEquals(listOf(Failure("openweather", "current", "offline"), Failure("openweather", "forecast", "offline")), failures)
        }

    @Test
    fun `Open-Meteo's forecast failing is reported`() =
        runBlocking {
            val repository = repository(openMeteo = client(failure = IOException("down")))

            val forecast = repository.fetchForecast(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0)

            assertTrue(forecast.isFailure)
            assertEquals(listOf(Failure("open_meteo", "forecast", "offline")), failures)
        }

    @Test
    fun `OpenWeather's forecast failing with Open-Meteo off is reported`() =
        runBlocking {
            val repository = repository(api = openWeather(forecast = IOException("down")), openMeteoEnabled = false)

            repository.fetchForecast(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0)

            assertEquals(listOf(Failure("openweather", "forecast", "offline")), failures)
        }

    @Test
    fun `air quality failing quietly is still reported, and the forecast stands`() =
        runBlocking {
            val repository = repository(openMeteo = client(forecastBody), air = client(code = 500))

            val forecast = repository.fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1)

            assertTrue(forecast.isSuccess)
            assertEquals(listOf(Failure("open_meteo", "air_quality", "server_error")), failures)
        }

    @Test
    fun `nothing is reported when every request works`() =
        runBlocking {
            val repository = repository()

            repository.fetchCurrentWeather(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0).getOrThrow()
            repository.fetchCurrentWeather(WeatherUnits.METRIC, cityQuery = "Austin").getOrThrow()

            assertEquals(emptyList<Failure>(), failures)
        }

    @Test
    fun `a failing reporter never breaks a fetch`() =
        runBlocking {
            val repository = WeatherRepository(
                openWeather(),
                OpenMeteoForecastSource(client(failure = IOException("down")), json),
                "test-key",
                ZoneOffset.UTC,
                Json,
                failures = { _, _, _ -> throw IllegalStateException("analytics down") },
            )

            val weather = repository.fetchCurrentWeather(WeatherUnits.METRIC, latitude = 1.0, longitude = 2.0)

            assertTrue(weather.isSuccess)
        }

    @Test
    fun `error keys are the screen's categories`() {
        assertEquals("offline", weatherErrorKey(IOException("offline")))
        assertEquals("city_not_found", weatherErrorKey(IllegalStateException("city_not_found")))
        assertEquals("generic", weatherErrorKey(IllegalStateException("invalid_payload")))
        assertEquals("generic", weatherErrorKey(IllegalStateException()))
    }
}
