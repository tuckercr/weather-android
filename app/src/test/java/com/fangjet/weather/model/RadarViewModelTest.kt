package com.fangjet.weather.model

import com.fangjet.weather.domain.RadarFrame
import com.fangjet.weather.domain.RadarTimeline
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class RadarViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val morning = timeline("/v2/radar/0750")
    private val noon = timeline("/v2/radar/1250")

    /** A fake RainViewer: answers each fetch from [results] in turn, counting calls. */
    private class FakeSource(
        results: List<Result<RadarTimeline>>,
    ) {
        private val queue = ArrayDeque(results)
        var calls = 0
        var gate: CompletableDeferred<Unit>? = null

        suspend fun fetch(): Result<RadarTimeline> {
            calls++
            gate?.await()
            return queue.removeFirst()
        }
    }

    private var now = 0L

    private fun viewModel(
        source: FakeSource,
        radarEnabled: Boolean = true,
    ) = RadarViewModel(
        getTimeline = source::fetch,
        frameIntervalMs = 850,
        radarEnabled = radarEnabled,
        nowMillis = { now },
    )

    private fun settle() = mainDispatcherRule.testDispatcher.scheduler.advanceUntilIdle()

    @Test
    fun `a fresh timeline is not refetched`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()

            now += RadarViewModel.STALE_AFTER_MS - 1
            viewModel.load()
            settle()

            assertEquals(1, source.calls)
            assertEquals(LoadingState.Success(morning), viewModel.timeline.value)
        }

    @Test
    fun `a stale timeline is refetched and replaced`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning), Result.success(noon)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()

            now += RadarViewModel.STALE_AFTER_MS
            viewModel.load()
            settle()

            assertEquals(2, source.calls)
            assertEquals(LoadingState.Success(noon), viewModel.timeline.value)
        }

    @Test
    fun `the old timeline stays visible while a refresh is in flight`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning), Result.success(noon)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()

            source.gate = CompletableDeferred()
            now += RadarViewModel.STALE_AFTER_MS
            viewModel.load()
            settle()
            assertEquals(LoadingState.Success(morning), viewModel.timeline.value)

            // A second load while the refresh is in flight is a no-op.
            viewModel.load()
            settle()
            assertEquals(2, source.calls)

            source.gate?.complete(Unit)
            settle()
            assertEquals(LoadingState.Success(noon), viewModel.timeline.value)
        }

    @Test
    fun `a failed refresh keeps the old timeline and retries on the next load`() =
        runTest {
            val source = FakeSource(
                listOf(
                    Result.success(morning),
                    Result.failure(IOException("offline")),
                    Result.success(noon),
                ),
            )
            val viewModel = viewModel(source)
            viewModel.load()
            settle()

            now += RadarViewModel.STALE_AFTER_MS
            viewModel.load()
            settle()
            assertEquals(LoadingState.Success(morning), viewModel.timeline.value)

            // Still stale, so the next resume / tick tries again.
            viewModel.load()
            settle()
            assertEquals(3, source.calls)
            assertEquals(LoadingState.Success(noon), viewModel.timeline.value)
        }

    // --- visibility ---

    @Test
    fun `the recheck leaves a stale radar alone while it is out of view`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning), Result.success(noon)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()
            viewModel.setVisible(false)

            now += RadarViewModel.STALE_AFTER_MS
            viewModel.recheck()
            settle()

            assertEquals(1, source.calls)
            assertEquals(LoadingState.Success(morning), viewModel.timeline.value)
        }

    @Test
    fun `the recheck refreshes a stale radar in view`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning), Result.success(noon)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()
            viewModel.setVisible(true)
            settle()
            // Fresh frames aren't fetched again on coming into view.
            assertEquals(1, source.calls)

            now += RadarViewModel.STALE_AFTER_MS
            viewModel.recheck()
            settle()

            assertEquals(2, source.calls)
            assertEquals(LoadingState.Success(noon), viewModel.timeline.value)
        }

    @Test
    fun `scrolling a stale radar into view refreshes it straight away`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning), Result.success(noon)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()
            viewModel.setVisible(false)

            now += RadarViewModel.STALE_AFTER_MS
            viewModel.setVisible(true)
            settle()

            assertEquals(2, source.calls)
            assertEquals(LoadingState.Success(noon), viewModel.timeline.value)
        }

    @Test
    fun `the recheck retries a failed load only while in view`() =
        runTest {
            val source = FakeSource(listOf(Result.failure(IOException("offline")), Result.success(morning)))
            val viewModel = viewModel(source)
            viewModel.load()
            settle()
            assertEquals(LoadingState.Error("radar_unavailable"), viewModel.timeline.value)

            viewModel.recheck()
            settle()
            assertEquals(1, source.calls)

            viewModel.setVisible(true)
            settle()
            assertEquals(2, source.calls)
            assertEquals(LoadingState.Success(morning), viewModel.timeline.value)
        }

    @Test
    fun `a failed first load still shows the error, and retry loads again`() =
        runTest {
            val source = FakeSource(listOf(Result.failure(IOException("offline")), Result.success(noon)))
            val viewModel = viewModel(source)

            viewModel.load()
            assertEquals(LoadingState.Loading, viewModel.timeline.value)
            settle()
            assertEquals(LoadingState.Error("radar_unavailable"), viewModel.timeline.value)

            viewModel.retry()
            settle()
            assertEquals(LoadingState.Success(noon), viewModel.timeline.value)
        }

    @Test
    fun `staleness is measured from when the timeline was requested`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning), Result.success(noon)))
            source.gate = CompletableDeferred()
            val viewModel = viewModel(source)
            viewModel.load()
            now += 30_000 // the fetch takes 30 s
            source.gate?.complete(Unit)
            settle()

            // The screen re-checks exactly STALE_AFTER_MS after calling load(): it must refetch.
            now = RadarViewModel.STALE_AFTER_MS
            source.gate = null
            viewModel.load()
            settle()

            assertEquals(2, source.calls)
        }

    // --- radar_enabled ---

    @Test
    fun `radar_enabled off disables the radar and never fetches the timeline`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning)))
            val viewModel = viewModel(source, radarEnabled = false)

            viewModel.load()
            settle()

            assertEquals(false, viewModel.radarEnabled)
            assertEquals(LoadingState.Idle, viewModel.timeline.value)
            assertEquals(0, source.calls)
        }

    @Test
    fun `radar_enabled off makes retry and later loads no-ops too`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning)))
            val viewModel = viewModel(source, radarEnabled = false)

            viewModel.retry()
            now += RadarViewModel.STALE_AFTER_MS
            viewModel.load()
            settle()

            assertEquals(LoadingState.Idle, viewModel.timeline.value)
            assertEquals(0, source.calls)
        }

    @Test
    fun `radar_enabled on loads the timeline`() =
        runTest {
            val source = FakeSource(listOf(Result.success(morning)))
            val viewModel = viewModel(source, radarEnabled = true)

            viewModel.load()
            settle()

            assertEquals(true, viewModel.radarEnabled)
            assertEquals(LoadingState.Success(morning), viewModel.timeline.value)
            assertEquals(1, source.calls)
        }

    private fun timeline(nowPath: String) =
        RadarTimeline(
            host = "https://tilecache.rainviewer.com",
            frames = listOf(
                RadarFrame(timeEpochSeconds = 1, path = "$nowPath-past", isForecast = false),
                RadarFrame(timeEpochSeconds = 2, path = nowPath, isForecast = false),
            ),
        )

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
