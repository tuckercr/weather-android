package com.fangjet.weather

import com.fangjet.weather.worker.NotificationKind
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationChannelsTest {
    @Test
    fun `the Walk time channel is only made with a dog`() {
        assertEquals(
            listOf(NotificationKind.MORNING_BRIEFING, NotificationKind.RAIN),
            WeatherApplication.channelsNeeded(hasDog = false, rainAlerts = true),
        )
        assertEquals(NotificationKind.entries.toList(), WeatherApplication.channelsNeeded(hasDog = true, rainAlerts = true))
    }

    @Test
    fun `the Rain alerts channel is only made while they're on`() {
        assertEquals(listOf(NotificationKind.MORNING_BRIEFING), WeatherApplication.channelsNeeded(hasDog = false, rainAlerts = false))
        assertEquals(
            listOf(NotificationKind.MORNING_BRIEFING, NotificationKind.WALK_TIME),
            WeatherApplication.channelsNeeded(hasDog = true, rainAlerts = false),
        )
    }
}
