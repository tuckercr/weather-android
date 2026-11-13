package com.fangjet.weather.analytics

import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.data.WeatherProvider
import com.fangjet.weather.data.WeatherRequestKind
import com.fangjet.weather.worker.NotificationKind
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsRepositoryTest {

    private val backend = mockk<AnalyticsBackend>(relaxed = true)

    // --- gates ---

    @Test
    fun `kill switch on and user opted in sends the event`() {
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = true)

        analytics.logSettingsOpened()

        verify(exactly = 1) { backend.logEvent(AnalyticsRepository.EVENT_SETTINGS_OPENED, emptyMap()) }
    }

    @Test
    fun `kill switch off drops every event and user property`() {
        val analytics = analytics(killSwitchOn = false, shareUsageStatistics = true)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
        verify(exactly = 0) { backend.setUserProperty(any(), any()) }
    }

    @Test
    fun `user opted out drops every event and user property`() {
        val analytics = analytics(killSwitchOn = true, shareUsageStatistics = false)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
        verify(exactly = 0) { backend.setUserProperty(any(), any()) }
    }

    @Test
    fun `kill switch off and user opted out drops every event`() {
        val analytics = analytics(killSwitchOn = false, shareUsageStatistics = false)

        logEveryEvent(analytics)

        verify(exactly = 0) { backend.logEvent(any(), any()) }
    }

    @Test
    fun `an event logged before the user's preference has loaded waits for it, then is sent`() =
        runTest {
            val analytics = analytics(killSwitchOn = true, shareUsageStatistics = null, awaited = true)

            analytics.logNotificationOpened(NotificationKind.RAIN)
            runCurrent()

            verify(exactly = 1) {
                backend.logEvent(AnalyticsRepository.EVENT_NOTIFICATION_OPENED, mapOf(AnalyticsRepository.PARAM_KIND to "rain"))
            }
        }

    @Test
    fun `an event logged before the user's preference has loaded is dropped when it says no, or never comes`() =
        runTest {
            for (awaited in listOf(false, null)) {
                val analytics = analytics(killSwitchOn = true, shareUsageStatistics = null, awaited = awaited)

                logEveryEvent(analytics)
                runCurrent()
            }

            verify(exactly = 0) { backend.logEvent(any(), any()) }
            verify(exactly = 0) { backend.setUserProperty(any(), any()) }
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

        verify { backend.logEvent("onboarding_step", mapOf("step" to "dog")) }
        verify {
            backend.logEvent(
                "onboarding_complete",
                mapOf("has_dog" to "yes", "notifications" to "denied", "location" to "skipped"),
            )
        }
        verify { backend.logEvent("city_added", mapOf("source" to "typed_match", "provider" to "openweather")) }
        verify { backend.logEvent("settings_opened", emptyMap()) }
        verify {
            backend.logEvent(
                "permission_changed",
                mapOf("permission" to "location_always", "result" to "granted", "from" to "follow_my_location"),
            )
        }
        verify { backend.logEvent("notification_opened", mapOf("kind" to "briefing")) }
        verify {
            backend.logEvent(
                "weather_error",
                mapOf("error_key" to "offline", "provider" to "open_meteo", "request" to "air_quality"),
            )
        }
        verify { backend.setUserProperty("has_dog", "no") }
    }

    @Test
    fun `every fixed value is the same as iOS's`() {
        assertEquals(listOf("welcome", "dog", "notifications", "location"), OnboardingStepShown.entries.map { it.key })
        assertEquals(listOf("granted", "denied", "skipped"), OnboardingPermissionResult.entries.map { it.key })
        assertEquals(listOf("notifications", "location", "location_always"), AnalyticsPermission.entries.map { it.key })
        assertEquals(listOf("settings", "add_my_location", "follow_my_location"), PermissionAskedFrom.entries.map { it.key })
        assertEquals(listOf("briefing", "walk", "rain"), NotificationKind.entries.map { it.openedKey })
        assertEquals(listOf("suggestion", "typed_match", "typed_name", "my_location"), CityAddedSource.entries.map { it.key })
        assertEquals(listOf("open_meteo", "openweather"), CitySearchProvider.entries.map { it.key })
        assertEquals(listOf("open_meteo", "openweather"), WeatherProvider.entries.map { it.key })
        assertEquals(listOf("current", "forecast", "air_quality"), WeatherRequestKind.entries.map { it.key })
    }

    @Test
    fun `a notification's tap extra names its kind, and nothing else does`() {
        NotificationKind.entries.forEach { assertEquals(it, notificationKindForOpenedKey(it.openedKey)) }
        assertNull(notificationKindForOpenedKey(null))
        assertNull(notificationKindForOpenedKey("something else"))
    }

    @Test
    fun `onboarding's permission results`() {
        // Held at the end counts as granted, whatever was answered.
        assertEquals(OnboardingPermissionResult.GRANTED, OnboardingPermissionResult.of(asked = true, held = true))
        assertEquals(OnboardingPermissionResult.GRANTED, OnboardingPermissionResult.of(asked = false, held = true))
        assertEquals(OnboardingPermissionResult.GRANTED, OnboardingPermissionResult.of(asked = null, held = true))
        assertEquals(OnboardingPermissionResult.DENIED, OnboardingPermissionResult.of(asked = true, held = false))
        assertEquals(OnboardingPermissionResult.SKIPPED, OnboardingPermissionResult.of(asked = false, held = false))
        // Unknown after a restart mid-onboarding.
        assertEquals(OnboardingPermissionResult.SKIPPED, OnboardingPermissionResult.of(asked = null, held = false))
    }

    // --- never crash ---

    @Test
    fun `a failing backend never crashes the caller`() {
        every { backend.logEvent(any(), any()) } throws IllegalStateException("Firebase not initialised")
        every { backend.setUserProperty(any(), any()) } throws IllegalStateException("Firebase not initialised")
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
        verify(exactly = 0) { backend.setUserProperty(any(), any()) }
    }

    private fun logEveryEvent(analytics: AnalyticsRepository) {
        analytics.logOnboardingStep(OnboardingStepShown.DOG)
        analytics.logOnboardingComplete(
            hasDog = true,
            notifications = OnboardingPermissionResult.DENIED,
            location = OnboardingPermissionResult.SKIPPED,
        )
        analytics.logCityAdded(CityAdded(CityAddedSource.TYPED_MATCH, CitySearchProvider.OPENWEATHER))
        analytics.logSettingsOpened()
        analytics.logPermissionChanged(AnalyticsPermission.LOCATION_ALWAYS, granted = true, from = PermissionAskedFrom.FOLLOW_MY_LOCATION)
        analytics.logNotificationOpened(NotificationKind.MORNING_BRIEFING)
        analytics.logWeatherError("offline", WeatherProvider.OPEN_METEO, WeatherRequestKind.AIR_QUALITY)
        analytics.setUserProperties(mapOf("has_dog" to "no"))
    }

    private fun analytics(
        killSwitchOn: Boolean,
        shareUsageStatistics: Boolean?,
    ): AnalyticsRepository {
        val remoteConfig = mockk<RemoteConfigRepository>()
        every { remoteConfig.analyticsEnabled() } returns killSwitchOn
        return AnalyticsRepository(backend, remoteConfig, collection(shareUsageStatistics))
    }

    /** With the preference unread at first: [awaited] is what it turns out to be (null: never read). */
    private fun TestScope.analytics(
        killSwitchOn: Boolean,
        shareUsageStatistics: Boolean?,
        awaited: Boolean?,
    ): AnalyticsRepository {
        val remoteConfig = mockk<RemoteConfigRepository>()
        every { remoteConfig.analyticsEnabled() } returns killSwitchOn
        val collection = collection(shareUsageStatistics)
        coEvery { collection.awaitShareUsageStatistics() } returns awaited
        return AnalyticsRepository(backend, remoteConfig, collection, backgroundScope)
    }

    private fun collection(shareUsageStatistics: Boolean?): AnalyticsCollectionController {
        val collection = mockk<AnalyticsCollectionController>()
        every { collection.shareUsageStatistics } returns shareUsageStatistics
        return collection
    }
}
