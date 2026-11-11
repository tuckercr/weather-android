package com.fangjet.weather.data

import com.fangjet.weather.data.remote.OpenWeatherApi
import com.fangjet.weather.data.remote.dto.AirQualityHourlyDto
import com.fangjet.weather.data.remote.dto.AirQualityHourlyResponse
import com.fangjet.weather.domain.AirQuality
import com.fangjet.weather.domain.AirQuality.Pollutant
import com.fangjet.weather.domain.AirQuality.Scale
import com.fangjet.weather.domain.AirQualityHours
import com.fangjet.weather.domain.WeatherUnits
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.ZoneOffset

/** Air quality asks only for the city's scale, and is fetched at most every 3 hours. Same cases as iOS's. */
class AirQualityRequestTest {
    private val json = Json { ignoreUnknownKeys = true }

    // An hour far ahead, so the forecast keeps it whatever the clock says.
    private val hour = 4_102_444_800L
    private val now = hour - 600

    private fun forecastBody(timeZone: String?) =
        """{"utc_offset_seconds":0,${timeZone?.let { "\"timezone\":\"$it\"," }.orEmpty()}
        "hourly":{"time":[$hour],"temperature_2m":[21.0],"weather_code":[0],"is_day":[1]}}"""

    private val europeanAirBody =
        """{"timezone":"Europe/London","hourly":{"time":[$hour],"european_aqi":[50],"european_aqi_ozone":[50],
        "pm2_5":[5.0],"pm10":[8.0]}}"""

    /** A client answering every request with [body] (or [failure]), recording each URL. */
    private class FakeHttp(
        private val body: String,
        private val failure: IOException? = null,
    ) {
        val urls = mutableListOf<HttpUrl>()
        val client: OkHttpClient = OkHttpClient
            .Builder()
            .addInterceptor { chain ->
                urls += chain.request().url
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
    }

    private fun repository(
        forecast: FakeHttp,
        air: FakeHttp,
    ) = WeatherRepository(
        mockk<OpenWeatherApi>(),
        OpenMeteoForecastSource(forecast.client, json),
        "test-key",
        ZoneOffset.UTC,
        Json,
        forecastSourcePolicy = { true },
        airQuality = AirQualitySource(air.client, json),
    )

    private fun hourlyFields(url: HttpUrl) = url.queryParameter("hourly").orEmpty().split(",")

    private fun cached(
        fetchedAt: Long = now - 60 * 60,
        scale: Scale = Scale.EUROPEAN,
        lastHour: Long = now + 30 * 60 * 60,
    ) = AirQualityHours(
        fetchedAt,
        scale,
        mapOf(
            // The hour in progress, which the tile shows, then the rest.
            hour - 60 * 60 to AirQuality(11, scale, null),
            hour to AirQuality(12, scale, Pollutant.OZONE),
            lastHour to AirQuality(13, scale, null),
        ),
    )

    @Test
    fun `each scale asks for its own index and sub-indices, and the particle masses`() {
        assertEquals(
            listOf(
                "us_aqi",
                "us_aqi_pm2_5",
                "us_aqi_pm10",
                "us_aqi_ozone",
                "us_aqi_nitrogen_dioxide",
                "us_aqi_sulphur_dioxide",
                "us_aqi_carbon_monoxide",
                "pm2_5",
                "pm10",
            ),
            AirQualityHourlyDto.fields(Scale.US),
        )
        assertEquals(
            listOf(
                "european_aqi",
                "european_aqi_pm2_5",
                "european_aqi_pm10",
                "european_aqi_ozone",
                "european_aqi_nitrogen_dioxide",
                "european_aqi_sulphur_dioxide",
                "pm2_5",
                "pm10",
            ),
            AirQualityHourlyDto.fields(Scale.EUROPEAN),
        )
        // Open-Meteo counts a request over 10 variables as more than one call.
        assertTrue(AirQualityHourlyDto.fields(Scale.US).size <= 10)
        assertTrue(AirQualityHourlyDto.fields(Scale.EUROPEAN).size <= 10)
        assertEquals(15, AirQualityHourlyDto.fields(null).toSet().size)
    }

    @Test
    fun `a European city's air quality asks only for the European scale`() =
        runBlocking {
            val air = FakeHttp(europeanAirBody)
            val repository = repository(FakeHttp(forecastBody("Europe/London")), air)

            val result = repository.fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1) { now }.getOrThrow()

            assertEquals(AirQualityHourlyDto.fields(Scale.EUROPEAN), hourlyFields(air.urls.single()))
            assertEquals(
                AirQualityHours(now, Scale.EUROPEAN, mapOf(hour to AirQuality(50, Scale.EUROPEAN, Pollutant.OZONE))),
                result.airQuality,
            )
            assertEquals(
                50,
                result.days
                    .single()
                    .hourlySlots
                    .single()
                    .airQuality
                    ?.index,
            )
        }

    @Test
    fun `a city elsewhere asks only for the US scale`() =
        runBlocking {
            val air = FakeHttp("""{"timezone":"America/New_York","hourly":{"time":[$hour],"us_aqi":[40]}}""")
            val repository = repository(FakeHttp(forecastBody("America/New_York")), air)

            val result = repository
                .fetchForecastWithAirQuality(
                    WeatherUnits.METRIC,
                    latitude = 40.7,
                    longitude = -74.0,
                ) { now }
                .getOrThrow()

            assertEquals(AirQualityHourlyDto.fields(Scale.US), hourlyFields(air.urls.single()))
            assertEquals(Scale.US, result.airQuality?.scale)
        }

    @Test
    fun `without a time zone both scales are asked for, and the response's zone picks one`() =
        runBlocking {
            val air = FakeHttp(europeanAirBody)
            val repository = repository(FakeHttp(forecastBody(null)), air)

            val result = repository.fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1) { now }.getOrThrow()

            assertEquals(AirQualityHourlyDto.fields(null), hourlyFields(air.urls.single()))
            assertEquals(Scale.EUROPEAN, result.airQuality?.scale)
        }

    @Test
    fun `failed air quality leaves the forecast standing`() =
        runBlocking {
            val repository = repository(FakeHttp(forecastBody("Europe/London")), FakeHttp("", IOException("down")))

            val result = repository.fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1) { now }.getOrThrow()

            assertNull(result.airQuality)
            assertNull(
                result.days
                    .single()
                    .hourlySlots
                    .single()
                    .airQuality,
            )
        }

    @Test
    fun `a failed forecast doesn't fetch air quality`() =
        runBlocking {
            val air = FakeHttp(europeanAirBody)
            val repository = repository(FakeHttp("", IOException("down")), air)

            val result = repository.fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1) { now }

            assertTrue(result.isFailure)
            assertEquals(0, air.urls.size)
        }

    @Test
    fun `air quality fetched under 3 hours ago is reused`() =
        runBlocking {
            val air = FakeHttp(europeanAirBody)
            val repository = repository(FakeHttp(forecastBody("Europe/London")), air)
            val cached = cached()

            val result = repository
                .fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1, cachedAirQuality = cached) { now }
                .getOrThrow()

            assertEquals(0, air.urls.size)
            assertSame(cached, result.airQuality)
            assertEquals(
                12,
                result.days
                    .single()
                    .hourlySlots
                    .single()
                    .airQuality
                    ?.index,
            )
        }

    @Test
    fun `old air quality, another scale's or too short is fetched again`() =
        runBlocking {
            listOf(
                cached(fetchedAt = now - AirQualityHours.MAX_AGE_SECONDS),
                cached(scale = Scale.US),
                cached(lastHour = now + 20 * 60 * 60),
            ).forEach { cached ->
                val air = FakeHttp(europeanAirBody)
                val repository = repository(FakeHttp(forecastBody("Europe/London")), air)

                val result = repository
                    .fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1, cachedAirQuality = cached) { now }
                    .getOrThrow()

                assertEquals(1, air.urls.size)
                assertEquals(AirQualityHourlyDto.fields(Scale.EUROPEAN), hourlyFields(air.urls.last()))
                assertEquals(Scale.EUROPEAN, result.airQuality?.scale)
                assertEquals(now, result.airQuality?.fetchedAtEpochSeconds)
            }
        }

    @Test
    fun `reuse needs under 3 hours, the same scale and the next 24 hours`() {
        val fresh = cached()
        assertTrue(fresh.reusable(Scale.EUROPEAN, now))
        assertTrue(cached(fetchedAt = now - AirQualityHours.MAX_AGE_SECONDS + 1).reusable(Scale.EUROPEAN, now))
        assertFalse(cached(fetchedAt = now - AirQualityHours.MAX_AGE_SECONDS).reusable(Scale.EUROPEAN, now))
        assertFalse(fresh.reusable(Scale.US, now))
        // The last hour must end at least 24 hours from now.
        assertTrue(cached(lastHour = now + 23 * 60 * 60).reusable(Scale.EUROPEAN, now))
        assertFalse(cached(lastHour = now + 23 * 60 * 60 - 1).reusable(Scale.EUROPEAN, now))
        // Fetched "in the future" (the clock went back): fetch again.
        assertFalse(cached(fetchedAt = now + 60).reusable(Scale.EUROPEAN, now))
        assertFalse(AirQualityHours(now, Scale.EUROPEAN, emptyMap()).reusable(Scale.EUROPEAN, now))
        // The first hour must have started, so the tile has the hour in progress.
        val fromNextHour = cached().let { it.copy(hours = it.hours.filterKeys { time -> time > now }) }
        assertFalse(fromNextHour.reusable(Scale.EUROPEAN, now))
    }

    @Test
    fun `with a cached scale, air quality is asked for alongside the forecast on that scale`() =
        runBlocking {
            val air = FakeHttp(europeanAirBody)
            val repository = repository(FakeHttp(forecastBody("Europe/London")), air)
            val stale = cached(fetchedAt = now - AirQualityHours.MAX_AGE_SECONDS)

            val result = repository
                .fetchForecastWithAirQuality(WeatherUnits.METRIC, latitude = 51.5, longitude = -0.1, cachedAirQuality = stale) { now }
                .getOrThrow()

            assertEquals(AirQualityHourlyDto.fields(Scale.EUROPEAN), hourlyFields(air.urls.single()))
            assertEquals(now, result.airQuality?.fetchedAtEpochSeconds)
        }

    @Test
    fun `parses a response with only one scale's fields`() {
        val response = json.decodeFromString(AirQualityHourlyResponse.serializer(), europeanAirBody)

        assertEquals(mapOf(hour to AirQuality(50, Scale.EUROPEAN, Pollutant.OZONE)), AirQualitySource.toHourly(response, Scale.EUROPEAN))
        assertEquals(emptyMap<Long, AirQuality>(), AirQualitySource.toHourly(response, Scale.US))
    }

    @Test
    fun `cached air quality survives a round trip`() {
        val hours = cached()

        assertEquals(hours, json.decodeFromString(AirQualityHours.serializer(), json.encodeToString(AirQualityHours.serializer(), hours)))
    }
}
