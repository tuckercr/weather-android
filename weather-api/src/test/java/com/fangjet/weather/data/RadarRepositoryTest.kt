package com.fangjet.weather.data

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class RadarRepositoryTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `parses host and merges past then nowcast in chronological order`() {
        val body =
            """
            {
              "host": "https://tilecache.rainviewer.com",
              "radar": {
                "past": [
                  { "time": 100, "path": "/v2/radar/100" },
                  { "time": 200, "path": "/v2/radar/200" }
                ],
                "nowcast": [
                  { "time": 300, "path": "/v2/radar/nowcast_300" }
                ]
              }
            }
            """.trimIndent()

        val timeline = RadarRepository.parseTimeline(json, body)

        assertEquals("https://tilecache.rainviewer.com", timeline.host)
        assertEquals(listOf(100L, 200L, 300L), timeline.frames.map { it.timeEpochSeconds })
        // Past frames are observed, the nowcast frame is a forecast.
        assertFalse(timeline.frames[0].isForecast)
        assertFalse(timeline.frames[1].isForecast)
        assertTrue(timeline.frames[2].isForecast)
    }

    @Test
    fun `builds a rainviewer tile url from host, path, and tile coordinates`() {
        val body =
            """
            {
              "host": "https://tilecache.rainviewer.com",
              "radar": { "past": [ { "time": 100, "path": "/v2/radar/100" } ], "nowcast": [] }
            }
            """.trimIndent()

        val timeline = RadarRepository.parseTimeline(json, body)
        // Exercise the real zoom the app requests (RadarTimeline.MAX_ZOOM = 7); x/y are a valid
        // tile at that zoom.
        val zoom = com.fangjet.weather.domain.RadarTimeline.MAX_ZOOM
        val url = timeline.tileUrl(timeline.frames.first(), z = zoom, x = 34, y = 50)

        assertEquals(
            "https://tilecache.rainviewer.com/v2/radar/100/256/$zoom/34/50/4/1_1.png",
            url,
        )
    }

    @Test
    fun `builds a rainviewer coverage tile url from host and tile coordinates`() {
        val timeline = com.fangjet.weather.domain
            .RadarTimeline(host = "https://tilecache.rainviewer.com", frames = emptyList())
        val zoom = com.fangjet.weather.domain.RadarTimeline.MAX_ZOOM

        assertEquals(
            "https://tilecache.rainviewer.com/v2/coverage/0/256/$zoom/34/50/0/0_0.png",
            timeline.coverageTileUrl(z = zoom, x = 34, y = 50),
        )
    }

    @Test
    fun `throws when there are no frames`() {
        val body = """{ "host": "https://tilecache.rainviewer.com", "radar": { "past": [], "nowcast": [] } }"""
        assertThrows(IllegalStateException::class.java) {
            RadarRepository.parseTimeline(json, body)
        }
    }
}
