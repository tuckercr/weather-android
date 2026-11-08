package com.fangjet.weather.model

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

    private fun remoteConfig(off: Set<NotificationKind> = emptySet()): RemoteConfigRepository {
        val remote = mockk<RemoteConfigRepository>(relaxed = true)
        every { remote.notificationsEnabled(any()) } answers { firstArg<NotificationKind>() !in off }
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
