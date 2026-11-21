package com.fangjet.weather.analytics

import com.fangjet.weather.data.RemoteConfigRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.Test

class AnalyticsRepositoryTest {

    private val backend = mockk<AnalyticsBackend>(relaxed = true)

    // --- gates ---

    @Test
    fun `kill switch on and user opted in sends the event`() {
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = true)

        analytics.logCityAdded(source = "test_source", provider = "test_provider")

        verify(exactly = 1) { backend.logEvent(name = AnalyticsRepository.EVENT_CITY_ADDED, params = mapOf("source" to "test_source", "provider" to "test_provider")) }
    }

    @Test
    fun `kill switch off drops every event`() {
        val analytics = analytics(killSwitchOn = false, shareUsageStatistics = true)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
    }

    @Test
    fun `user opted out drops every event`() {
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = false)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
    }

    @Test
    fun `kill switch off and user opted out drops every event`() {
        val analytics = analytics(killSwitchOn = false, shareUsageStatistics = false)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
    }

    @Test
    fun `events are dropped until the user's preference has loaded`() {
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = null)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
    }

    @Test
    fun `kill switch is re-read on every event`() {
        var killSwitchOn = true
        val remoteConfig = mockk<RemoteConfigRepository>()
        every { remoteConfig.analyticsEnabled() } answers { killSwitchOn }
        val analytics = AnalyticsRepository(backend, remoteConfig, collection(shareUsageStatistics = true))

        analytics.logSettingsOpened()
        killSwitchOn = false
        analytics.logSettingsOpened()

        verify(exactly = 1) { backend.logEvent(AnalyticsRepository.EVENT_SETTINGS_OPENED, any()) }
    }

    // --- events ---

    @Test
    fun `each event is sent with its name and parameters`() {
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = true)

        logEveryEvent(analytics)

        verify { backend.logEvent(AnalyticsRepository.EVENT_ONBOARDING_COMPLETE, emptyMap()) }
        verify { backend.logEvent(AnalyticsRepository.EVENT_CITY_ADDED, emptyMap()) }
        verify { backend.logEvent(AnalyticsRepository.EVENT_SETTINGS_OPENED, emptyMap()) }
        verify {
            backend.logEvent(
                AnalyticsRepository.EVENT_WEATHER_ERROR,
                mapOf(AnalyticsRepository.PARAM_ERROR_KEY to "offline"),
            )
        }
    }

    // --- never crash ---

    @Test
    fun `a failing backend never crashes the caller`() {
        every { backend.logEvent(any(), any()) } throws IllegalStateException("Firebase not initialised")
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = true)

        logEveryEvent(analytics)
    }

    @Test
    fun `a failing Remote Config read never crashes the caller and sends nothing`() {
        val remoteConfig = mockk<RemoteConfigRepository>()
        every { remoteConfig.analyticsEnabled() } throws IllegalStateException("boom")
        val analytics = AnalyticsRepository(backend, remoteConfig, collection(shareUsageStatistics = true))

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
    }

    private fun logEveryEvent(analytics: AnalyticsRepository) {
        analytics.logOnboardingComplete()
        /**analytics.logCityAdded()*/   analytics.logCityAdded(source = "test_source", provider = "test_provider")
        analytics.logSettingsOpened()
        analytics.logWeatherError("offline")
    }

    private fun analytics(
        killSwitchOn: Boolean,
        shareUsageStatistics: Boolean?,
    ): AnalyticsRepository {
        val remoteConfig = mockk<RemoteConfigRepository>()
        every { remoteConfig.analyticsEnabled() } returns killSwitchOn
        return AnalyticsRepository(backend, remoteConfig, collection(shareUsageStatistics))
    }

    private fun collection(shareUsageStatistics: Boolean?): AnalyticsCollectionController {
        val collection = mockk<AnalyticsCollectionController>()
        every { collection.shareUsageStatistics } returns shareUsageStatistics
        return collection
    }
}
