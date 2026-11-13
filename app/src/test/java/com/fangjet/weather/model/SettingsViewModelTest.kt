package com.fangjet.weather.model

import com.fangjet.weather.analytics.AnalyticsPermission
import com.fangjet.weather.analytics.PermissionAskedFrom
import com.fangjet.weather.data.AppPermissions
import com.fangjet.weather.data.PreferencesRepository
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.worker.NotificationKind
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `unitOverride reflects value from repository`() =
        runTest {
            val viewModel = viewModel(unitOverride = UnitOverride.IMPERIAL)
            val collected = mutableListOf<UnitOverride>()
            val job = backgroundScope.launch { viewModel.unitOverride.collect { collected += it } }

            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(UnitOverride.IMPERIAL, collected.last())
            job.cancel()
        }

    @Test
    fun `setUnitOverride delegates to repository`() =
        runTest {
            val repo = mockk<PreferencesRepository>(relaxed = true)
            every { repo.unitOverride } returns flowOf(UnitOverride.SYSTEM)
            every { repo.shareUsageStatistics } returns flowOf(true)
            every { repo.showWalkCard } returns flowOf(true)
            val viewModel = SettingsViewModel(repo, remoteConfig(), FakePermissions())

            viewModel.setUnitOverride(UnitOverride.METRIC)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repo.setUnitOverride(UnitOverride.METRIC) }
        }

    @Test
    fun `clearCache delegates to repository`() =
        runTest {
            val repo = mockk<PreferencesRepository>(relaxed = true)
            every { repo.unitOverride } returns flowOf(UnitOverride.SYSTEM)
            every { repo.shareUsageStatistics } returns flowOf(true)
            every { repo.showWalkCard } returns flowOf(true)
            val viewModel = SettingsViewModel(repo, remoteConfig(), FakePermissions())

            viewModel.clearCache()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repo.clearCache() }
        }

    @Test
    fun `shareUsageStatistics reflects value from repository`() =
        runTest {
            val viewModel = viewModel(shareUsageStatistics = false)
            val collected = mutableListOf<Boolean>()
            val job = backgroundScope.launch { viewModel.shareUsageStatistics.collect { collected += it } }

            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(false, collected.last())
            job.cancel()
        }

    @Test
    fun `setShareUsageStatistics delegates to repository`() =
        runTest {
            val repo = mockk<PreferencesRepository>(relaxed = true)
            every { repo.unitOverride } returns flowOf(UnitOverride.SYSTEM)
            every { repo.shareUsageStatistics } returns flowOf(true)
            every { repo.showWalkCard } returns flowOf(true)
            val viewModel = SettingsViewModel(repo, remoteConfig(), FakePermissions())

            viewModel.setShareUsageStatistics(false)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repo.setShareUsageStatistics(false) }
        }

    @Test
    fun `showWalkCard reflects value from repository`() =
        runTest {
            val viewModel = viewModel(showWalkCard = false)
            val collected = mutableListOf<Boolean>()
            val job = backgroundScope.launch { viewModel.showWalkCard.collect { collected += it } }

            mainDispatcherRule.testDispatcher.scheduler.runCurrent()

            assertEquals(false, collected.last())
            job.cancel()
        }

    @Test
    fun `setShowWalkCard delegates to repository`() =
        runTest {
            val repo = mockk<PreferencesRepository>(relaxed = true)
            every { repo.unitOverride } returns flowOf(UnitOverride.SYSTEM)
            every { repo.shareUsageStatistics } returns flowOf(true)
            every { repo.showWalkCard } returns flowOf(true)
            val viewModel = SettingsViewModel(repo, remoteConfig(), FakePermissions())

            viewModel.setShowWalkCard(false)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

            coVerify { repo.setShowWalkCard(false) }
        }

    @Test
    fun `kinds switched off remotely are left out of the count and hidden`() =
        runTest {
            val viewModel = viewModel(notifyRain = true, remoteOff = setOf(NotificationKind.MORNING_BRIEFING, NotificationKind.RAIN))
            val collected = mutableListOf<Int>()
            val job = backgroundScope.launch { viewModel.notificationsOnCount.collect { collected += it } }
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            job.cancel()

            assertEquals(1, collected.last())
            assertFalse(viewModel.remotelyEnabled(NotificationKind.MORNING_BRIEFING))
            assertTrue(viewModel.remotelyEnabled(NotificationKind.WALK_TIME))
            assertFalse(viewModel.remotelyEnabled(NotificationKind.RAIN))
        }

    @Test
    fun `a Remote Config activation while Settings is open updates the switches and the count`() =
        runTest {
            // A settable Remote Config: Rain is switched off, then on again by a fetch.
            val off = mutableSetOf(NotificationKind.RAIN)
            val activations = MutableStateFlow(0)
            val repo = mockk<PreferencesRepository>(relaxed = true)
            every { repo.showWalkCard } returns flowOf(true)
            every { repo.notifyMorningBriefing } returns flowOf(true)
            every { repo.notifyWalkTime } returns flowOf(true)
            every { repo.notifyRain } returns flowOf(true)
            val remote = mockk<RemoteConfigRepository>(relaxed = true)
            every { remote.notificationsEnabled(any()) } answers { firstArg<NotificationKind>() !in off }
            every { remote.activationCount } returns activations
            val viewModel = SettingsViewModel(repo, remote, FakePermissions())
            val collected = mutableListOf<Int>()
            val job = backgroundScope.launch { viewModel.notificationsOnCount.collect { collected += it } }
            val kinds = backgroundScope.launch { viewModel.remotelyEnabledKinds.collect {} }
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            assertEquals(2, collected.last())
            assertFalse(NotificationKind.RAIN in viewModel.remotelyEnabledKinds.value)

            off.clear()
            activations.value = 1
            mainDispatcherRule.testDispatcher.scheduler.runCurrent()
            job.cancel()
            kinds.cancel()

            assertEquals(3, collected.last())
            assertTrue(NotificationKind.RAIN in viewModel.remotelyEnabledKinds.value)
        }

    @Test
    fun `the notification count leaves out Walk time without a dog`() =
        runTest {
            fun count(showWalkCard: Boolean): Int {
                val viewModel = viewModel(showWalkCard = showWalkCard, notifyWalkTime = true, notifyRain = false)
                val collected = mutableListOf<Int>()
                val job = backgroundScope.launch { viewModel.notificationsOnCount.collect { collected += it } }
                mainDispatcherRule.testDispatcher.scheduler.runCurrent()
                job.cancel()
                return collected.last()
            }

            assertEquals(2, count(showWalkCard = true))
            assertEquals(1, count(showWalkCard = false))
        }

    private fun viewModel(
        unitOverride: UnitOverride = UnitOverride.SYSTEM,
        shareUsageStatistics: Boolean = true,
        showWalkCard: Boolean = true,
        notifyMorningBriefing: Boolean = true,
        notifyWalkTime: Boolean = true,
        notifyRain: Boolean = false,
        remoteOff: Set<NotificationKind> = emptySet(),
        permissions: AppPermissions = FakePermissions(),
        followMyLocation: Boolean = false,
    ): SettingsViewModel {
        val repo = mockk<PreferencesRepository>(relaxed = true)
        every { repo.unitOverride } returns flowOf(unitOverride)
        every { repo.shareUsageStatistics } returns flowOf(shareUsageStatistics)
        every { repo.showWalkCard } returns flowOf(showWalkCard)
        every { repo.notifyMorningBriefing } returns flowOf(notifyMorningBriefing)
        every { repo.notifyWalkTime } returns flowOf(notifyWalkTime)
        every { repo.notifyRain } returns flowOf(notifyRain)
        every { repo.followMyLocation } returns flowOf(followMyLocation)
        return SettingsViewModel(repo, remoteConfig(remoteOff), permissions)
    }

    // --- Permissions and Follow my location ---

    @Test
    fun `permissions are re-read when asked, as on every resume`() {
        val permissions = FakePermissions(notifications = false)
        val viewModel = viewModel(permissions = permissions)
        assertFalse(viewModel.permissionState.value.notifications)

        permissions.notifications = true
        viewModel.refreshPermissions()

        assertTrue(viewModel.permissionState.value.notifications)
    }

    @Test
    fun `follow my location explains first, then asks for what's missing`() =
        runTest {
            val permissions = FakePermissions(location = false, backgroundLocation = false)
            val repo = mockk<PreferencesRepository>(relaxed = true)
            val viewModel = SettingsViewModel(repo, remoteConfig(), permissions)

            viewModel.setFollowMyLocation(true)
            assertTrue(viewModel.explainingFollowMyLocation.value)
            assertEquals(FollowMyLocationRequest.FOREGROUND_LOCATION, viewModel.confirmFollowMyLocation())
            assertFalse(viewModel.explainingFollowMyLocation.value)
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            coVerify { repo.setFollowMyLocation(true) }

            permissions.location = true
            assertEquals(FollowMyLocationRequest.BACKGROUND_LOCATION, viewModel.confirmFollowMyLocation())
            // Android stops opening its page after two refusals.
            viewModel.backgroundLocationAnswered(false)
            viewModel.backgroundLocationAnswered(false)
            assertEquals(FollowMyLocationRequest.APP_SETTINGS, viewModel.confirmFollowMyLocation())
        }

    @Test
    fun `follow my location shows on only with all-the-time access, and needs no explaining then`() =
        runTest {
            val permissions = FakePermissions(backgroundLocation = true)
            val viewModel = viewModel(permissions = permissions, followMyLocation = true)
            val job = launch { viewModel.followsMyLocation.collect {} }
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertTrue(viewModel.followsMyLocation.value)

            viewModel.setFollowMyLocation(true)
            assertFalse(viewModel.explainingFollowMyLocation.value)

            permissions.backgroundLocation = false
            viewModel.refreshPermissions()
            mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()
            assertFalse(viewModel.followsMyLocation.value)
            job.cancel()
        }

    // --- permission_changed ---

    private data class PermissionChange(
        val permission: AnalyticsPermission,
        val granted: Boolean,
        val from: PermissionAskedFrom,
    )

    private fun loggingViewModel(
        permissions: AppPermissions,
        changes: MutableList<PermissionChange>,
    ) = SettingsViewModel(
        mockk(relaxed = true),
        remoteConfig(),
        permissions,
        onPermissionChanged = { permission, granted, from -> changes += PermissionChange(permission, granted, from) },
    )

    @Test
    fun `follow my location logs each answer it asked for`() {
        val permissions = FakePermissions(location = false, backgroundLocation = false)
        val changes = mutableListOf<PermissionChange>()
        val viewModel = loggingViewModel(permissions, changes)

        viewModel.setFollowMyLocation(true)
        assertEquals(FollowMyLocationRequest.FOREGROUND_LOCATION, viewModel.confirmFollowMyLocation())
        permissions.location = true
        viewModel.foregroundLocationAnswered(true)
        viewModel.backgroundLocationAnswered(false)

        assertEquals(
            listOf(
                PermissionChange(AnalyticsPermission.LOCATION, true, PermissionAskedFrom.FOLLOW_MY_LOCATION),
                PermissionChange(AnalyticsPermission.LOCATION_ALWAYS, false, PermissionAskedFrom.FOLLOW_MY_LOCATION),
            ),
            changes,
        )
    }

    @Test
    fun `follow my location's trip to the app's settings is answered on the return`() {
        val permissions = FakePermissions(backgroundLocation = false)
        val changes = mutableListOf<PermissionChange>()
        val viewModel = loggingViewModel(permissions, changes)
        viewModel.backgroundLocationAnswered(false)
        viewModel.backgroundLocationAnswered(false)
        changes.clear()

        assertEquals(FollowMyLocationRequest.APP_SETTINGS, viewModel.confirmFollowMyLocation())
        permissions.backgroundLocation = true
        viewModel.refreshPermissions()
        // Only the return answers it, not every later resume.
        viewModel.refreshPermissions()

        assertEquals(listOf(PermissionChange(AnalyticsPermission.LOCATION_ALWAYS, true, PermissionAskedFrom.FOLLOW_MY_LOCATION)), changes)
    }

    @Test
    fun `nothing is logged when follow my location has nothing to ask`() {
        val changes = mutableListOf<PermissionChange>()
        val viewModel = loggingViewModel(FakePermissions(backgroundLocation = true), changes)

        viewModel.setFollowMyLocation(true)
        assertEquals(FollowMyLocationRequest.NONE, viewModel.confirmFollowMyLocation())
        viewModel.refreshPermissions()

        assertEquals(emptyList<PermissionChange>(), changes)
    }

    @Test
    fun `the settings rows' trips to the app's settings are answered on the return`() {
        val permissions = FakePermissions(location = false, notifications = false)
        val changes = mutableListOf<PermissionChange>()
        val viewModel = loggingViewModel(permissions, changes)

        viewModel.openingAppSettingsFor(AnalyticsPermission.NOTIFICATIONS)
        permissions.notifications = true
        viewModel.refreshPermissions()
        viewModel.openingAppSettingsFor(AnalyticsPermission.LOCATION)
        viewModel.refreshPermissions()
        // A resume with no trip logs nothing.
        viewModel.refreshPermissions()

        assertEquals(
            listOf(
                PermissionChange(AnalyticsPermission.NOTIFICATIONS, true, PermissionAskedFrom.SETTINGS),
                PermissionChange(AnalyticsPermission.LOCATION, false, PermissionAskedFrom.SETTINGS),
            ),
            changes,
        )
    }

    private class FakePermissions(
        var location: Boolean = true,
        var backgroundLocation: Boolean = false,
        var notifications: Boolean = true,
    ) : AppPermissions {
        private var refusals = 0

        override fun location() = location

        override fun backgroundLocation() = backgroundLocation

        override fun notifications() = notifications

        override fun backgroundLocationRefusals() = refusals

        override fun noteBackgroundLocationAnswer(granted: Boolean) {
            refusals = if (granted) 0 else refusals + 1
        }
    }

    private fun remoteConfig(
        off: Set<NotificationKind> = emptySet(),
        activations: Flow<Int> = flowOf(0),
    ): RemoteConfigRepository {
        val remote = mockk<RemoteConfigRepository>(relaxed = true)
        every { remote.notificationsEnabled(any()) } answers { firstArg<NotificationKind>() !in off }
        every { remote.activationCount } returns activations
        return remote
    }

    class MainDispatcherRule(
        val testDispatcher: TestDispatcher = StandardTestDispatcher(),
    ) : TestWatcher() {
        override fun starting(description: Description) {
            Dispatchers.setMain(testDispatcher)
        }

        override fun finished(description: Description) {
            Dispatchers.resetMain()
        }
    }
}
