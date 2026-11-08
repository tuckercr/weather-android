package com.fangjet.weather.model

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.data.RadarRepository
import com.fangjet.weather.data.RadarTile
import com.fangjet.weather.data.RadarTileLoader
import com.fangjet.weather.data.RemoteConfigRepository
import com.fangjet.weather.domain.RadarFrame
import com.fangjet.weather.domain.RadarTimeline
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import kotlin.math.abs

/**
 * Holds the RainViewer radar timeline. The frame list is global (not per-city), so it is fetched
 * once and shared, then refreshed whenever it has gone stale ([STALE_AFTER_MS]): RainViewer
 * publishes a new frame every 10 minutes and only serves the last couple of hours, so an old
 * timeline would label hours-old frames "Now" and point at tiles that no longer exist.
 *
 * [load] is a no-op when Remote Config `radar_enabled` is off, while a load is in flight, or
 * while the timeline is fresh. A refresh keeps the
 * current timeline visible until the new one arrives, and a failed refresh keeps it (the next
 * [load] retries); only a failed first load shows the error.
 *
 * While the screen stays open, [recheck] only refreshes while the radar is in view ([setVisible]),
 * so a radar scrolled out of sight doesn't keep fetching frames nobody watches.
 *
 * It also holds the images for the city on screen ([show]): the map behind the radar ([baseMap])
 * and each frame's overlay ([overlays]), loaded through [RadarTileLoader]. Same as iOS's
 * `RadarViewModel`.
 */
@HiltViewModel
class RadarViewModel internal constructor(
    private val getTimeline: suspend () -> Result<RadarTimeline>,
    /** Delay between animation frames (ms), from Remote Config. */
    val frameIntervalMs: Long,
    /**
     * Remote Config `radar_enabled`. When false the radar section is hidden and nothing is
     * fetched from RainViewer or the OpenStreetMap tile servers.
     */
    val radarEnabled: Boolean = true,
    // Monotonic clock in milliseconds; injectable for tests.
    private val nowMillis: () -> Long = { System.nanoTime() / 1_000_000 },
    private val loadBaseMap: suspend (RadarTile) -> Bitmap? = { null },
    private val loadOverlay: suspend (RadarTile, RadarTimeline, RadarFrame) -> Bitmap? = { _, _, _ -> null },
) : ViewModel() {

    @Inject
    constructor(
        radarRepository: RadarRepository,
        remoteConfigRepository: RemoteConfigRepository,
        tileLoader: RadarTileLoader,
    ) : this(
        getTimeline = radarRepository::getTimeline,
        frameIntervalMs = remoteConfigRepository.radarFrameIntervalMs(),
        radarEnabled = remoteConfigRepository.radarEnabled(),
        loadBaseMap = tileLoader::baseMap,
        loadOverlay = { tile, timeline, frame -> tileLoader.overlay(frame, timeline, tile) },
    )

    private val _timeline = MutableStateFlow<LoadingState<RadarTimeline>>(LoadingState.Idle)
    val timeline: StateFlow<LoadingState<RadarTimeline>> = _timeline.asStateFlow()

    private var loadInFlight = false

    /** Whether any of the radar is inside the visible part of the screen. */
    private var visible = false

    /** When the request behind the current timeline was made; null until one has loaded. */
    private var loadedAtMillis: Long? = null

    private fun isStale(): Boolean = loadedAtMillis?.let { nowMillis() - it >= STALE_AFTER_MS } ?: true

    private val tile = MutableStateFlow<RadarTile?>(null)

    private val _baseMap = MutableStateFlow<Bitmap?>(null)

    /** The OpenStreetMap tiles around the city on screen; null until they load. */
    val baseMap: StateFlow<Bitmap?> = _baseMap.asStateFlow()

    private val _overlays = MutableStateFlow<Map<String, Bitmap>>(emptyMap())

    /** Each frame's stitched overlay by frame path, kept so replays are smooth. */
    val overlays: StateFlow<Map<String, Bitmap>> = _overlays.asStateFlow()

    init {
        // Each new set of frames starts on "now", playing.
        viewModelScope.launch {
            timeline
                .map { (it as? LoadingState.Success)?.data?.frames }
                .filterNotNull()
                .distinctUntilChanged()
                .collect { frames -> _playback.value = RadarPlayback(frameIndex = nowIndex(frames)) }
        }
        // A new city's map, loaded once per tile.
        viewModelScope.launch {
            tile.filterNotNull().collectLatest {
                _baseMap.value = null
                _baseMap.value = loadBaseMap(it)
            }
        }
        // Every frame's overlay, prefetched once per tile and timeline (a change cancels the old
        // prefetch), frames nearest "now" first, a few at a time. A new tile starts from scratch; a
        // refreshed timeline keeps the overlays it shares with the old one and drops the rest.
        viewModelScope.launch {
            var overlaysTile: RadarTile? = null
            combine(tile.filterNotNull(), timeline) { tile, state -> tile to (state as? LoadingState.Success)?.data }
                .collectLatest { (tile, timeline) ->
                    if (tile != overlaysTile) {
                        _overlays.value = emptyMap()
                        overlaysTile = tile
                    }
                    timeline ?: return@collectLatest
                    val live = timeline.frames.mapTo(HashSet()) { it.path }
                    _overlays.update { cached -> cached.filterKeys { it in live } }
                    prefetchOverlays(tile, timeline)
                }
        }
    }

    /** The card's city, by its map tile: loads its map and overlays. */
    fun show(tile: RadarTile) {
        this.tile.value = tile
    }

    /** The card's city, by its coordinates. */
    fun show(
        latitude: Double,
        longitude: Double,
    ) = show(RadarTile.from(latitude, longitude))

    /** The map tile the card shows; null until [show]. */
    val shownTile: StateFlow<RadarTile?> = tile.asStateFlow()

    private val _playback = MutableStateFlow(RadarPlayback())

    /** The frame on screen and whether it's playing. Starts on "now", playing, for each timeline. */
    val playback: StateFlow<RadarPlayback> = _playback.asStateFlow()

    /** The frame on screen, its time and what it is; null before there are frames. */
    val shownFrame: StateFlow<ShownRadarFrame?> =
        combine(timeline, _playback) { state, playback ->
            val frames = (state as? LoadingState.Success)?.data?.frames.orEmpty()
            frames.getOrNull(playback.frameIndex)?.let { frame ->
                ShownRadarFrame(frame, isNow = playback.frameIndex == nowIndex(frames))
            }
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val frames: List<RadarFrame> get() = (timeline.value as? LoadingState.Success)?.data?.frames.orEmpty()

    /** Steps through the frames while playing, until cancelled. */
    suspend fun play() {
        while (true) {
            // Asleep while paused or before there are frames.
            _playback.first { it.playing }
            timeline.first { (it as? LoadingState.Success)?.data?.frames?.isNotEmpty() == true }
            delay(frameIntervalMs)
            val count = frames.size
            if (_playback.value.playing && count > 0) {
                _playback.update { it.copy(frameIndex = (it.frameIndex + 1) % count) }
            }
        }
    }

    /** The card came back on screen: playback starts again on "now", as it did before the card left. */
    fun restartPlayback() {
        _playback.value = RadarPlayback(frameIndex = nowIndex(frames))
    }

    fun togglePlaying() = _playback.update { it.copy(playing = !it.playing) }

    /** The slider: shows that frame, and pauses. */
    fun scrub(index: Int) {
        _playback.value = RadarPlayback(frameIndex = index.coerceIn(0, (frames.size - 1).coerceAtLeast(0)), playing = false)
    }

    /**
     * While the screen is resumed: fetches the frames if they're missing or stale, then keeps them
     * current, only while the card is in view. Until cancelled.
     */
    suspend fun keepCurrent() {
        load()
        while (true) {
            delay(RECHECK_INTERVAL_MS)
            recheck()
        }
    }

    private suspend fun prefetchOverlays(
        tile: RadarTile,
        timeline: RadarTimeline,
    ) = coroutineScope {
        val permits = Semaphore(PARALLEL_FRAME_LOADS)
        val now = timeline.frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)
        timeline.frames.indices
            .sortedBy { abs(it - now) }
            .map { timeline.frames[it] }
            .filterNot { _overlays.value.containsKey(it.path) }
            .forEach { frame ->
                launch {
                    permits.withPermit {
                        loadOverlay(tile, timeline, frame)?.let { image -> _overlays.update { it + (frame.path to image) } }
                    }
                }
            }
    }

    fun load() {
        if (!radarEnabled) return
        if (loadInFlight) return
        val current = _timeline.value
        if (current is LoadingState.Success && !isStale()) return
        val isRefresh = current is LoadingState.Success
        // A refresh keeps the old timeline on screen; only a first load shows the spinner.
        if (!isRefresh) _timeline.value = LoadingState.Loading
        loadInFlight = true
        // Age is measured from the request, so a re-check 10 minutes later always finds it stale.
        val requestedAt = nowMillis()
        viewModelScope.launch {
            getTimeline().fold(
                onSuccess = {
                    _timeline.value = LoadingState.Success(it)
                    loadedAtMillis = requestedAt
                },
                onFailure = {
                    // A failed refresh keeps the old timeline; it is retried on the next load().
                    if (!isRefresh) _timeline.value = LoadingState.Error("radar_unavailable")
                },
            )
            loadInFlight = false
        }
    }

    /**
     * Called as the radar scrolls into and out of view. Coming into view refreshes stale frames
     * straight away rather than waiting for the next [recheck].
     */
    fun setVisible(visible: Boolean) {
        val cameIntoView = visible && !this.visible
        this.visible = visible
        if (cameIntoView) load()
    }

    /**
     * The timed re-check while the screen is open: refreshes stale frames, or retries a failed
     * load, only while the radar is in view.
     */
    fun recheck() {
        if (visible) load()
    }

    fun retry() {
        if (_timeline.value is LoadingState.Error) _timeline.value = LoadingState.Idle
        load()
    }

    companion object {
        /** The most recent observed frame: "now". */
        fun nowIndex(frames: List<RadarFrame>): Int = frames.indexOfLast { !it.isForecast }.coerceAtLeast(0)

        /** RainViewer adds a frame every 10 minutes, so a timeline older than this is out of date. */
        const val STALE_AFTER_MS = 10 * 60 * 1000L

        /** How often the radar card checks for staleness while the screen is open. */
        const val RECHECK_INTERVAL_MS = 60 * 1000L

        /** Radar frames stitched at once; each one fetches 9 tiles. */
        private const val PARALLEL_FRAME_LOADS = 4
    }
}

/** The radar frame on screen and whether it's playing through them. */
data class RadarPlayback(
    val frameIndex: Int = 0,
    val playing: Boolean = true,
)

/** The radar frame on screen: [isNow] for the latest observed one, otherwise a past or forecast frame. */
data class ShownRadarFrame(
    val frame: RadarFrame,
    val isNow: Boolean,
)
