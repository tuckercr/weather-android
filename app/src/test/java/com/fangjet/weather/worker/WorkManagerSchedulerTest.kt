package com.fangjet.weather.worker

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequest
import androidx.work.PeriodicWorkRequest
import androidx.work.WorkManager
import com.fangjet.weather.data.RemoteConfigRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit

class WorkManagerSchedulerTest {

    private val context = mockk<Context>(relaxed = true)
    private val workManager = mockk<WorkManager>(relaxed = true)
    private val remoteConfig = mockk<RemoteConfigRepository>()

    private val periodic = mutableMapOf<String, Pair<ExistingPeriodicWorkPolicy, PeriodicWorkRequest>>()
    private val oneTime = mutableMapOf<String, Pair<ExistingWorkPolicy, OneTimeWorkRequest>>()

    @Before
    fun setUp() {
        mockkObject(WorkManager.Companion)
        every { WorkManager.getInstance(any()) } returns workManager
        every { workManager.enqueueUniquePeriodicWork(any(), any(), any()) } answers {
            periodic[firstArg()] = secondArg<ExistingPeriodicWorkPolicy>() to thirdArg()
            mockk(relaxed = true)
        }
        every { workManager.enqueueUniqueWork(any<String>(), any(), any<OneTimeWorkRequest>()) } answers {
            oneTime[firstArg()] = secondArg<ExistingWorkPolicy>() to thirdArg()
            mockk(relaxed = true)
        }
        every { remoteConfig.refreshIntervalMinutes() } returns 30L
    }

    @After
    fun tearDown() = unmockkAll()

    private fun scheduler() = WorkManagerScheduler(context, remoteConfig)

    // --- background refresh ---

    @Test
    fun `background refresh runs at the Remote Config interval on any network`() {
        every { remoteConfig.refreshIntervalMinutes() } returns 45L

        scheduler().schedule()

        val (policy, request) = periodic.getValue("weather_background_update")
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policy)
        assertEquals(TimeUnit.MINUTES.toMillis(45), request.workSpec.intervalDuration)
        assertEquals(WeatherUpdateWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
    }

    @Test
    fun `an interval below the WorkManager minimum is raised to 15 minutes`() {
        every { remoteConfig.refreshIntervalMinutes() } returns 1L

        scheduler().schedule()

        val (_, request) = periodic.getValue("weather_background_update")
        assertEquals(TimeUnit.MINUTES.toMillis(15), request.workSpec.intervalDuration)
    }

    // --- morning run ---

    @Test
    fun `the morning run is daily, keeps an existing schedule, and starts within a day`() {
        scheduler().schedule()

        val (policy, request) = periodic.getValue("notification_briefing")
        assertEquals(ExistingPeriodicWorkPolicy.KEEP, policy)
        assertEquals(TimeUnit.DAYS.toMillis(1), request.workSpec.intervalDuration)
        assertEquals(WeatherNotificationWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        assertTrue(request.workSpec.initialDelay in 1..TimeUnit.DAYS.toMillis(1))
        assertEquals(setOf("weather_background_update", "notification_briefing"), periodic.keys)
    }

    @Test
    fun `the old 9, 13 and 19 o'clock briefings and the drifting 8-30 run are cancelled`() {
        scheduler().schedule()

        listOf(9, 13, 19).forEach { hour ->
            verify { workManager.cancelUniqueWork("weather_notification_${hour}h") }
        }
        verify { workManager.cancelUniqueWork("notification_morning") }
    }

    @Test
    fun `after a run, the next one is put back on the next 8-30 without stopping this one`() {
        val before = System.currentTimeMillis()

        scheduler().keepBriefingAtHalfPastEight()

        val (policy, request) = periodic.getValue("notification_briefing")
        assertEquals(ExistingPeriodicWorkPolicy.UPDATE, policy)
        assertEquals(WeatherNotificationWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals(TimeUnit.DAYS.toMillis(1), request.workSpec.intervalDuration)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        val next = request.workSpec.nextScheduleTimeOverride
        assertTrue("next $next", next in before + 1..System.currentTimeMillis() + TimeUnit.DAYS.toMillis(1))
        val local = java.time.Instant
            .ofEpochMilli(next)
            .atZone(ZoneId.systemDefault())
        // On the dot: never 8:29:59.999.
        assertEquals(listOf(8, 30, 0, 0), listOf(local.hour, local.minute, local.second, local.nano))
    }

    // --- walk time ---

    @Test
    fun `a walk time is a one-off that replaces any already planned`() {
        val inTwoHours = System.currentTimeMillis() / 1000 + TimeUnit.HOURS.toSeconds(2)

        scheduler().scheduleWalkTime(inTwoHours)

        val (policy, request) = oneTime.getValue("notification_walk_time")
        assertEquals(ExistingWorkPolicy.REPLACE, policy)
        assertEquals(WalkTimeWorker::class.java.name, request.workSpec.workerClassName)
        assertEquals(NetworkType.CONNECTED, request.workSpec.constraints.requiredNetworkType)
        val delay = request.workSpec.initialDelay
        assertTrue("delay $delay", delay in TimeUnit.MINUTES.toMillis(119)..TimeUnit.HOURS.toMillis(2))
    }

    @Test
    fun `a walk time already past runs at once`() {
        scheduler().scheduleWalkTime(System.currentTimeMillis() / 1000 - 600)

        assertEquals(
            0L,
            oneTime
                .getValue("notification_walk_time")
                .second.workSpec.initialDelay,
        )
    }

    // --- next occurrence ---

    private val zone = ZoneId.of("America/New_York")

    private fun at(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int = 0,
    ) = ZonedDateTime.of(year, month, day, hour, minute, 0, 0, zone)

    @Test
    fun `a time later today is reached today`() {
        assertEquals(
            TimeUnit.MINUTES.toMillis(60),
            WorkManagerScheduler.millisUntilNext(8, 30, at(2026, 6, 10, 7, 30)),
        )
    }

    @Test
    fun `the next time is the moment itself, whatever the seconds now`() {
        val now = ZonedDateTime.of(2026, 6, 10, 7, 30, 59, 999_999_999, zone)
        assertEquals(
            at(2026, 6, 10, 8, 30).toInstant().toEpochMilli(),
            WorkManagerScheduler.nextAt(8, 30, now),
        )
        assertEquals(
            at(2026, 6, 11, 8, 30).toInstant().toEpochMilli(),
            WorkManagerScheduler.nextAt(8, 30, ZonedDateTime.of(2026, 6, 10, 8, 30, 0, 1, zone)),
        )
    }

    @Test
    fun `a time already passed today is tomorrow`() {
        assertEquals(
            TimeUnit.HOURS.toMillis(23),
            WorkManagerScheduler.millisUntilNext(8, 30, at(2026, 6, 10, 9, 30)),
        )
    }

    @Test
    fun `exactly on the time waits a full day`() {
        assertEquals(
            TimeUnit.DAYS.toMillis(1),
            WorkManagerScheduler.millisUntilNext(8, 30, at(2026, 6, 10, 8, 30)),
        )
    }

    @Test
    fun `across the spring-forward night it is one hour shorter`() {
        // 2026-03-08 02:00 EST jumps to 03:00 EDT: 20:30 on the 7th to 08:30 on the 8th is 11 hours.
        assertEquals(
            TimeUnit.HOURS.toMillis(11),
            WorkManagerScheduler.millisUntilNext(8, 30, at(2026, 3, 7, 20, 30)),
        )
    }

    @Test
    fun `across the fall-back night it is one hour longer`() {
        // 2026-11-01 02:00 EDT falls back to 01:00 EST: 20:30 on Oct 31 to 08:30 Nov 1 is 13 hours.
        assertEquals(
            TimeUnit.HOURS.toMillis(13),
            WorkManagerScheduler.millisUntilNext(8, 30, at(2026, 10, 31, 20, 30)),
        )
    }
}
