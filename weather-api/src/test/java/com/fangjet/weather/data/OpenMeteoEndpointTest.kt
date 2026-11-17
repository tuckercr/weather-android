package com.fangjet.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The commercial plan's key moves requests to the customer hosts. Same cases as iOS's. */
class OpenMeteoEndpointTest {
    @Test
    fun aKeyGoesToTheCustomerHostWithTheKey() {
        val url = OpenMeteoEndpoint.builder(OpenMeteoEndpoint.FORECAST, " key123 ").build()
        assertEquals("customer-api.open-meteo.com", url.host)
        assertEquals("/v1/forecast", url.encodedPath)
        assertEquals("key123", url.queryParameter("apikey"))

        val air = OpenMeteoEndpoint.builder(OpenMeteoEndpoint.AIR_QUALITY, "key123").build()
        assertEquals("customer-air-quality-api.open-meteo.com", air.host)
        assertEquals("key123", air.queryParameter("apikey"))
    }

    @Test
    fun noKeyGoesToTheFreeHost() {
        listOf("", "   ").forEach { key ->
            val url = OpenMeteoEndpoint.builder(OpenMeteoEndpoint.FORECAST, key).build()
            assertEquals("api.open-meteo.com", url.host)
            assertNull(url.queryParameter("apikey"))
        }
    }
}
