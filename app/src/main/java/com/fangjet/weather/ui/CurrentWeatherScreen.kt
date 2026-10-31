package com.fangjet.weather.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fangjet.weather.R
import com.fangjet.weather.domain.CitySuggestion
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetWeatherThresholds
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.TodayTileSwitches
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.model.CityListViewModel
import com.fangjet.weather.model.GeoLocationViewModel
import com.fangjet.weather.model.LoadingState
import com.fangjet.weather.model.LocationFetchState
import com.fangjet.weather.model.LocationPermissionViewModel
import com.fangjet.weather.model.RadarViewModel
import com.fangjet.weather.model.SettingsViewModel
import com.fangjet.weather.model.UpdatePromptViewModel
import com.fangjet.weather.model.WeatherForecastViewModel
import com.fangjet.weather.ui.pets.PetMood
import com.fangjet.weather.ui.pets.PetScene
import com.fangjet.weather.ui.pets.WalkCard
import com.fangjet.weather.ui.pets.inkColor
import com.fangjet.weather.ui.pets.isPrecipitating
import com.fangjet.weather.ui.pets.petCaption
import com.fangjet.weather.ui.pets.petMoodFor
import com.fangjet.weather.ui.pets.skyColor
import com.fangjet.weather.ui.pets.walkAdviceFor
import com.fangjet.weather.ui.theme.CatsDogsTheme
import com.fangjet.weather.ui.theme.StaleBannerBg
import com.fangjet.weather.ui.theme.StaleBannerText
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.util.ReadingDay
import com.fangjet.weather.util.UpdatePrompt
import com.fangjet.weather.util.forecastAsCurrent
import com.fangjet.weather.util.fromCurrentSlot
import com.fangjet.weather.util.isTooOldToShowAsNow
import com.fangjet.weather.util.nextRain
import com.fangjet.weather.util.readingDay
import com.fangjet.weather.util.usesMilesForDistance
import kotlinx.coroutines.delay
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes

/** How often an open screen re-reads the clock and quietly refreshes the weather. */
private val AUTO_REFRESH_INTERVAL = 30.minutes

/** How often old weather is re-checked against the cutoff while a refresh keeps failing. */
private val STALE_RECHECK_INTERVAL = 5.minutes

/**
 * "My Location" for screens that can add it. Returns the add-city sheet's "Add My Location" action,
 * or null when it's already in the list or the system won't ask for location any more. Without
 * access it asks first, so "Only this time" / "Ask every time" users can still add it. Access is
 * re-read on every resume, so changing it in system settings shows or hides the action on return.
 *
 * With [autoAdd] (the main screen) it also:
 * - adds it unprompted once access turns up after onboarding went without it: granted later in
 *   Settings, say. Only when there are no cities yet, or access has just changed to granted, so
 *   someone who removed My Location on purpose doesn't get it back on every resume. That check
 *   waits for [locationsLoaded]: before the saved cities have loaded, the empty list would read
 *   as "no cities yet".
 * - re-reads the device's position on every resume while My Location is saved and access is on,
 *   passing it to [onRefreshed], so My Location follows the device rather than staying where it
 *   was added.
 */
@Composable
internal fun rememberAddMyLocation(
    locations: List<SavedLocation>,
    onLocated: (SavedLocation) -> Unit,
    autoAdd: Boolean = false,
    locationsLoaded: Boolean = true,
    onRefreshed: (SavedLocation) -> Unit = {},
    // Whether a saved My Location is due a move check (at most every 15 minutes).
    claimRefresh: () -> Boolean = { true },
    viewModel: LocationPermissionViewModel = hiltViewModel(),
): (() -> Unit)? {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val hasCurrentLocation = locations.any { it.isCurrentLocation }
    var granted by remember { mutableStateOf(viewModel.hasLocationPermission()) }
    var wasGranted by rememberSaveable { mutableStateOf(granted) }
    // Whether the fetch under way is to add My Location, rather than to move a saved one.
    var fetchingToAdd by rememberSaveable { mutableStateOf(false) }
    var resumeCount by remember { mutableIntStateOf(0) }
    val currentHasCurrentLocation by rememberUpdatedState(hasCurrentLocation)
    val currentHasAnyLocation by rememberUpdatedState(locations.isNotEmpty())
    val requestPermission = rememberLocationPermissionRequest(viewModel)

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME) return@LifecycleEventObserver
            granted = viewModel.hasLocationPermission()
            resumeCount++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (autoAdd) {
        LaunchedEffect(resumeCount, locationsLoaded) {
            if (resumeCount == 0 || !locationsLoaded) return@LaunchedEffect
            val newlyGranted = granted && !wasGranted
            wasGranted = granted
            when {
                !granted -> Unit
                currentHasCurrentLocation -> {
                    if (claimRefresh()) {
                        fetchingToAdd = false
                        viewModel.fetchLocation()
                    }
                }
                newlyGranted || !currentHasAnyLocation -> {
                    fetchingToAdd = true
                    viewModel.fetchLocation()
                }
            }
        }
    }

    val context = LocalContext.current
    LaunchedEffect(state) {
        // The sheet has closed by now, so a fix that fails to add My Location says so here.
        if (state == LocationFetchState.Failed && fetchingToAdd) {
            fetchingToAdd = false
            Toast.makeText(context, R.string.add_my_location_failed, Toast.LENGTH_LONG).show()
        }
        val located = state as? LocationFetchState.Located ?: return@LaunchedEffect
        // Handled once: a Located still held when this screen comes back (from another screen,
        // or after rotation) must not re-add a My Location the user has since removed.
        viewModel.consumeLocated()
        when {
            currentHasCurrentLocation -> onRefreshed(located.location)
            // A position read only to move My Location, arriving after it was removed, adds nothing.
            fetchingToAdd -> onLocated(located.location)
        }
        fetchingToAdd = false
    }

    // Re-read on recomposition, so an answer in the system dialog shows or hides it at once.
    val canAsk = !granted && viewModel.canRequestPermission()
    return when {
        hasCurrentLocation -> null
        granted -> {
            {
                fetchingToAdd = true
                viewModel.fetchLocation()
            }
        }
        canAsk -> {
            {
                fetchingToAdd = true
                requestPermission()
            }
        }
        else -> null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentWeatherRoute(onOpenSettings: () -> Unit = {}) {
    val activity = LocalActivity.current as ComponentActivity
    val cityListViewModel = hiltViewModel<CityListViewModel>(viewModelStoreOwner = activity)
    val weatherForecastViewModel =
        hiltViewModel<WeatherForecastViewModel>(viewModelStoreOwner = activity)
    val geoViewModel = hiltViewModel<GeoLocationViewModel>(viewModelStoreOwner = activity)
    // Same owner RadarCard uses, so this is the instance that would load the timeline.
    val radarEnabled = hiltViewModel<RadarViewModel>().radarEnabled
    val settingsViewModel = hiltViewModel<SettingsViewModel>(viewModelStoreOwner = activity)
    // The activity's instance, the one that decides whether the app is blocked for an update.
    val updatePromptViewModel = hiltViewModel<UpdatePromptViewModel>(viewModelStoreOwner = activity)
    val updatePrompt by updatePromptViewModel.prompt.collectAsStateWithLifecycle()
    val unitOverride by settingsViewModel.unitOverride.collectAsStateWithLifecycle()
    // The user's Settings choice; Remote Config walk_card_enabled can also hide it for everyone.
    val showWalkCard by settingsViewModel.showWalkCard.collectAsStateWithLifecycle()
    val distanceInMiles = LocalContext.current.usesMilesForDistance(unitOverride)

    val locations by cityListViewModel.locations.collectAsStateWithLifecycle()
    val activeIndex by cityListViewModel.activeIndex.collectAsStateWithLifecycle()
    val activeLocation by cityListViewModel.activeLocation.collectAsStateWithLifecycle()
    val weatherState by weatherForecastViewModel.currentWeather.collectAsStateWithLifecycle()
    val forecastState by weatherForecastViewModel.forecast.collectAsStateWithLifecycle()
    val isRefreshing by weatherForecastViewModel.isRefreshing.collectAsStateWithLifecycle()
    val staleErrorKey by weatherForecastViewModel.staleErrorKey.collectAsStateWithLifecycle()
    val cityInput by geoViewModel.cityInput.collectAsStateWithLifecycle()
    val suggestions by geoViewModel.citySuggestions.collectAsStateWithLifecycle()
    val suggestLoading by geoViewModel.citySuggestLoading.collectAsStateWithLifecycle()
    val selectedSuggestion by geoViewModel.selectedSuggestion.collectAsStateWithLifecycle()

    // Full refresh whenever the active location changes
    LaunchedEffect(activeLocation) {
        activeLocation?.let {
            weatherForecastViewModel.refreshCurrent(it)
            weatherForecastViewModel.refreshForecast(it)
        }
    }

    // Silent background refresh on every foreground resume (picks up new units or stale data)
    val currentActiveLocation by rememberUpdatedState(activeLocation)
    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                currentActiveLocation?.let {
                    weatherForecastViewModel.backgroundRefreshCurrent(it)
                    weatherForecastViewModel.backgroundRefreshForecast(it)
                }
                cityListViewModel.fillInMissingCoordinates()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // And the same quiet refresh every half hour while the screen stays open (left on a stand
    // overnight, say), which also moves the hourly strip and captions on.
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL)
                currentActiveLocation?.let {
                    weatherForecastViewModel.backgroundRefreshCurrent(it)
                    weatherForecastViewModel.backgroundRefreshForecast(it)
                }
            }
        }
    }

    val locationsLoaded by cityListViewModel.locationsLoaded.collectAsStateWithLifecycle()
    val addMyLocation = rememberAddMyLocation(
        locations = locations,
        onLocated = cityListViewModel::addLocation,
        autoAdd = true,
        locationsLoaded = locationsLoaded,
        onRefreshed = cityListViewModel::updateCurrentLocation,
        claimRefresh = { cityListViewModel.claimMyLocationCheck() },
    )

    var showAddSheet by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalDistanceInMiles provides distanceInMiles) {
        CurrentWeatherScreen(
            locations = locations,
            activeIndex = activeIndex,
            weatherState = weatherState,
            forecastState = forecastState,
            isRefreshing = isRefreshing,
            staleErrorKey = staleErrorKey,
            radarEnabled = radarEnabled,
            walkCardEnabled = weatherForecastViewModel.walkCardEnabled && showWalkCard,
            dogWalking = showWalkCard,
            airQualityEnabled = weatherForecastViewModel.airQualityEnabled,
            todayTiles = weatherForecastViewModel.todayTiles,
            petWeatherThresholds = weatherForecastViewModel.petWeatherThresholds,
            onTabSelected = cityListViewModel::setActiveIndex,
            onOpenSettings = {
                weatherForecastViewModel.logSettingsOpened()
                onOpenSettings()
            },
            onAddCityClick = { showAddSheet = true },
            topBanner = {
                if (updatePrompt == UpdatePrompt.RECOMMENDED) {
                    UpdateBanner(
                        onDismiss = updatePromptViewModel::dismissBanner,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
            },
            onRefresh = {
                activeLocation?.let {
                    weatherForecastViewModel.refreshCurrent(it)
                    weatherForecastViewModel.refreshForecast(it)
                }
            },
        ) {
            activeLocation?.let {
                weatherForecastViewModel.refreshCurrent(it)
                weatherForecastViewModel.refreshForecast(it)
            }
        }
    }

    if (showAddSheet) {
        val sheetState = rememberModalBottomSheetState()
        ModalBottomSheet(
            onDismissRequest = {
                geoViewModel.reset()
                showAddSheet = false
            },
            sheetState = sheetState,
        ) {
            AddCitySheetContent(
                savedLocations = locations,
                cityInput = cityInput,
                suggestions = suggestions,
                suggestLoading = suggestLoading,
                selectedSuggestion = selectedSuggestion,
                onCityInputChange = geoViewModel::onCityInputChange,
                onSuggestionChosen = geoViewModel::onCitySuggestionChosen,
                onAddCity = {
                    geoViewModel.locationToAdd()?.let(cityListViewModel::addLocation)
                    geoViewModel.reset()
                    showAddSheet = false
                },
                onRemoveSaved = cityListViewModel::removeLocation,
                onAddMyLocation = addMyLocation?.let { add ->
                    {
                        add()
                        geoViewModel.reset()
                        showAddSheet = false
                    }
                },
                onDismiss = {
                    geoViewModel.reset()
                    showAddSheet = false
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CurrentWeatherScreen(
    modifier: Modifier = Modifier,
    locations: List<SavedLocation>,
    activeIndex: Int,
    weatherState: LoadingState<CurrentWeather>,
    forecastState: LoadingState<List<DayForecast>> = LoadingState.Idle,
    isRefreshing: Boolean = false,
    // Set when the last refresh failed and the weather shown is cached; see StaleWeatherBanner.
    staleErrorKey: String? = null,
    radarEnabled: Boolean = true,
    walkCardEnabled: Boolean = true,
    // Settings' Dog walking switch: off hides the Paw check tile (the walk card follows walkCardEnabled).
    dogWalking: Boolean = true,
    // Remote Config air_quality_enabled: off hides the Air quality tile.
    airQualityEnabled: Boolean = true,
    // Remote Config tile_*_enabled: which Today tiles show.
    todayTiles: TodayTileSwitches = TodayTileSwitches(),
    petWeatherThresholds: PetWeatherThresholds = PetWeatherThresholds.DEFAULT,
    onTabSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    onAddCityClick: () -> Unit,
    onRefresh: () -> Unit = {},
    // Under the city tabs: the update banner, when a newer version is recommended.
    topBanner: @Composable () -> Unit = {},
    onRetry: () -> Unit,
) {
    // Each pull also re-reads the clock, so the hourly strip and captions move on even when the
    // weather itself hasn't changed.
    var pullCount by remember { mutableIntStateOf(0) }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Text(
                            text = if (locations.isEmpty()) {
                                stringResource(R.string.app_name)
                            } else {
                                locations.getOrNull(activeIndex)?.label
                                    ?: stringResource(R.string.app_name)
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    actions = {
                        IconButton(onClick = onAddCityClick) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.cd_add_city),
                            )
                        }
                        IconButton(onClick = onOpenSettings) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = stringResource(R.string.cd_open_settings),
                            )
                        }
                    },
                )
                if (locations.size >= 2) {
                    PrimaryScrollableTabRow(
                        selectedTabIndex = activeIndex,
                        edgePadding = 16.dp,
                    ) {
                        locations.forEachIndexed { index, location ->
                            Tab(
                                selected = index == activeIndex,
                                onClick = { onTabSelected(index) },
                            ) {
                                Row(
                                    modifier = Modifier.padding(
                                        vertical = 12.dp,
                                        horizontal = 4.dp,
                                    ),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    if (location.isCurrentLocation) {
                                        Icon(
                                            imageVector = Icons.Default.LocationOn,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp),
                                        )
                                    }
                                    Text(
                                        text = location.label,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            }
                        }
                    }
                }
                topBanner()
            }
        },
    ) { padding ->
        if (locations.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyLocationsState(onAddCityClick = onAddCityClick)
            }
        } else {
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = {
                    pullCount++
                    onRefresh()
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(modifier = Modifier.height(8.dp))
                    when (weatherState) {
                        LoadingState.Idle, LoadingState.Loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        is LoadingState.Error -> WeatherErrorState(
                            errorKey = weatherState.errorKey,
                            canRetry = weatherState.canRetry,
                            onRetry = onRetry,
                        )

                        is LoadingState.Success -> {
                            val cached = weatherState.data
                            // Re-read on each pull, and every few minutes while the data is old, so a
                            // reading that passes the cutoff gives way to the forecast for this hour.
                            val nowEpoch by produceState(Instant.now().epochSecond, pullCount, staleErrorKey) {
                                while (true) {
                                    value = Instant.now().epochSecond
                                    if (staleErrorKey == null) break
                                    delay(STALE_RECHECK_INTERVAL)
                                }
                            }
                            // Past the cutoff, the old reading isn't shown as "now": the cached
                            // forecast's current hour stands in, or the error if it doesn't cover now.
                            val tooOld = staleErrorKey != null && cached.isTooOldToShowAsNow(nowEpoch)
                            val forecastNow = if (tooOld) {
                                (forecastState as? LoadingState.Success)?.data?.let { forecastAsCurrent(cached, it, nowEpoch) }
                            } else {
                                null
                            }
                            if (tooOld && forecastNow == null) {
                                WeatherErrorState(
                                    errorKey = staleErrorKey.orEmpty(),
                                    canRetry = true,
                                    onRetry = onRetry,
                                    lastUpdated = readingTimeText(cached, nowEpoch),
                                )
                            } else {
                                if (staleErrorKey != null) {
                                    StaleWeatherBanner(
                                        weather = cached,
                                        errorKey = staleErrorKey,
                                        showingForecast = forecastNow != null,
                                        nowEpochSeconds = nowEpoch,
                                    )
                                }
                                val location = locations.getOrNull(activeIndex)
                                // The moon (hero scene and night icons) is lit from the other side down south.
                                CompositionLocalProvider(LocalSouthernHemisphere provides ((location?.latitude ?: 0.0) < 0)) {
                                    CurrentWeatherContent(
                                        weather = forecastNow ?: cached,
                                        showingForecast = forecastNow != null,
                                        forecastState = forecastState,
                                        location = location,
                                        radarEnabled = radarEnabled,
                                        walkCardEnabled = walkCardEnabled,
                                        dogWalking = dogWalking,
                                        airQualityEnabled = airQualityEnabled,
                                        todayTiles = todayTiles,
                                        petWeatherThresholds = petWeatherThresholds,
                                        refreshCount = pullCount,
                                        onForecastRetry = onRetry,
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(24.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyLocationsState(
    onAddCityClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(
            imageVector = Icons.Default.Cloud,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
        )
        Text(
            text = stringResource(R.string.empty_cities_title),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.empty_cities_body),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onAddCityClick) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.size(8.dp))
            Text(stringResource(R.string.action_add_city))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCitySheetContent(
    savedLocations: List<SavedLocation>,
    cityInput: String,
    suggestions: List<CitySuggestion>,
    suggestLoading: Boolean,
    selectedSuggestion: CitySuggestion?,
    onCityInputChange: (String) -> Unit,
    onSuggestionChosen: (CitySuggestion) -> Unit,
    onAddCity: () -> Unit,
    onRemoveSaved: (Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    // Shown when My Location isn't saved and location access is on.
    onAddMyLocation: (() -> Unit)? = null,
) {
    val keyboardController = LocalSoftwareKeyboardController.current

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.add_city_sheet_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (savedLocations.isNotEmpty()) {
            Text(
                text = stringResource(R.string.saved_cities_section),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            savedLocations.forEachIndexed { index, location ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (location.isCurrentLocation) {
                        Icon(
                            Icons.Default.LocationOn,
                            contentDescription = null,
                            modifier = Modifier
                                .size(16.dp)
                                .padding(end = 0.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(modifier = Modifier.size(4.dp))
                    }
                    Text(
                        text = location.label,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    IconButton(onClick = { onRemoveSaved(index) }) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = stringResource(R.string.cd_remove_city),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            HorizontalDivider()
        }

        if (onAddMyLocation != null) {
            OutlinedButton(onClick = onAddMyLocation, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.add_my_location))
            }
        }

        Text(
            text = stringResource(R.string.search_city_section),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Column(modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = cityInput,
                onValueChange = onCityInputChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.field_city_label)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
            )
            if (suggestLoading) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            if (suggestions.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 220.dp),
                    ) {
                        suggestions.forEach { suggestion ->
                            TextButton(
                                onClick = {
                                    keyboardController?.hide()
                                    onSuggestionChosen(suggestion)
                                },
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text(
                                    text = suggestion.label,
                                    modifier = Modifier.fillMaxWidth(),
                                    textAlign = TextAlign.Start,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    }
                }
            }
        }
        Button(
            onClick = {
                keyboardController?.hide()
                onAddCity()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = cityInput.isNotBlank() || selectedSuggestion != null,
        ) {
            Text(stringResource(R.string.action_add_city))
        }
        OutlinedButton(
            onClick = onDismiss,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(R.string.action_cancel))
        }
    }
}

@Composable
private fun CurrentWeatherContent(
    weather: CurrentWeather,
    forecastState: LoadingState<List<DayForecast>>,
    modifier: Modifier = Modifier,
    // True when [weather] is the cached forecast's current hour standing in for an old reading:
    // there's no cloud cover (or visibility) to show.
    showingForecast: Boolean = false,
    location: SavedLocation? = null,
    radarEnabled: Boolean = true,
    walkCardEnabled: Boolean = true,
    // Settings' Dog walking switch: off hides the Paw check tile (the walk card follows walkCardEnabled).
    dogWalking: Boolean = true,
    airQualityEnabled: Boolean = true,
    todayTiles: TodayTileSwitches = TodayTileSwitches(),
    petWeatherThresholds: PetWeatherThresholds = PetWeatherThresholds.DEFAULT,
    refreshCount: Int = 0,
    onForecastRetry: () -> Unit = {},
) {
    val forecastDays = (forecastState as? LoadingState.Success)?.data.orEmpty()
    val todayLabel = remember(weather.offsetSeconds) {
        // "Today" in the city's local date, so it matches the city-local forecast day labels.
        java.time.OffsetDateTime
            .now(java.time.ZoneOffset.ofTotalSeconds(weather.offsetSeconds))
            .format(
                java.time.format.DateTimeFormatter
                    .ofPattern("EEE, MMM d"),
            )
    }
    val todayForecast = forecastDays.firstOrNull()?.takeIf { it.dateLabel == todayLabel }
    val upcomingDays = if (todayForecast != null) forecastDays.drop(1) else forecastDays
    var selectedDay by remember { mutableStateOf<DayForecast?>(null) }
    val feelsLikeC = weather.units.toCelsius(weather.feelsLike)
    val mood = petMoodFor(weather.conditionMain, weather.iconCode, feelsLikeC, petWeatherThresholds)
    val ink = mood.inkColor
    // Slots from the one in progress onwards. "Now" is re-read whenever the forecast changes,
    // whenever the screen resumes and every half hour while it stays open, so a cached forecast,
    // a failed refresh or an app left open for hours never shows (or advises on) past hours.
    var resumeCount by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        resumeCount++
        onPauseOrDispose { }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(AUTO_REFRESH_INTERVAL)
                resumeCount++
            }
        }
    }
    val nowEpochSeconds = remember(forecastDays, resumeCount, refreshCount) {
        java.time.Instant
            .now()
            .epochSecond
    }
    val currentSlots = remember(forecastDays, nowEpochSeconds) {
        forecastDays.flatMap { it.hourlySlots }.fromCurrentSlot(nowEpochSeconds)
    }
    val walkAdvice =
        remember(currentSlots, feelsLikeC, weather.conditionMain, weather.offsetSeconds, nowEpochSeconds, petWeatherThresholds) {
            walkAdviceFor(
                feelsLikeC,
                currentSlots,
                precipitatingNow = isPrecipitating(weather.conditionMain),
                thresholds = petWeatherThresholds,
                utcOffsetSeconds = weather.offsetSeconds,
                nowEpochSeconds = nowEpochSeconds,
            )
        }

    // Landscape phones and tablets put the hero beside today's details instead of stacking
    // everything in one stretched column.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val wide = maxWidth >= WIDE_LAYOUT_MIN_WIDTH
        val heroSection: @Composable () -> Unit = {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (todayForecast != null) {
                            Modifier.clickable { selectedDay = todayForecast }
                        } else {
                            Modifier
                        },
                    ),
                colors = CardDefaults.cardColors(containerColor = mood.skyColor),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Kept a bit smaller than the card so the scene doesn't dominate, especially on wide screens.
                    PetScene(
                        mood = mood,
                        modifier = Modifier
                            .fillMaxWidth(PET_SCENE_WIDTH_FRACTION)
                            .widthIn(max = PET_SCENE_MAX_WIDTH),
                    )
                    Text(
                        text = weather.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink,
                    )
                    if (location != null) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocationOn,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = ink,
                            )
                            Text(
                                text = weather.cityName,
                                style = MaterialTheme.typography.titleLarge,
                                color = ink,
                            )
                        }
                    }
                    Text(
                        text = formatTemperature(weather.temperature, weather.units),
                        style = MaterialTheme.typography.displayMedium,
                        // Baloo 2 reserves a lot of empty space above its digits; pull the number up
                        // into it so it sits close under the city name.
                        modifier = Modifier.layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val cut = TEMP_TOP_TRIM.roundToPx()
                            layout(placeable.width, placeable.height - cut) { placeable.place(0, -cut) }
                        },
                        fontWeight = FontWeight.Bold,
                        color = ink,
                    )
                    val (high, low) = heroHighLow(weather, todayForecast)
                    val spokenRange = stringResource(
                        R.string.label_temp_range_spoken,
                        high.roundToInt(),
                        low.roundToInt(),
                    )
                    Text(
                        text = stringResource(
                            R.string.label_temp_range,
                            high.roundToInt(),
                            low.roundToInt(),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = ink,
                        // "H 64°" would be read as the letter H.
                        modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(spokenRange) },
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = petCaption(mood, weather.offsetSeconds),
                        style = MaterialTheme.typography.titleMedium,
                        color = ink,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        // "Rain starting around 3:15 PM", only when rain or snow is due in the next 2 hours.
        val nextRain = remember(forecastDays, nowEpochSeconds) {
            nextRain(forecastDays.firstOrNull()?.quarterHours.orEmpty(), nowEpochSeconds)
        }
        val nextRainSection: @Composable () -> Unit = {
            nextRain?.let { NextRainLine(it, weather.offsetSeconds) }
        }
        val walkSection: @Composable () -> Unit = {
            // walk_card_enabled: Remote Config can hide the card.
            if (walkCardEnabled) walkAdvice?.let { WalkCard(advice = it) }
        }
        val detailsSection: @Composable () -> Unit = {
            SectionHeader(R.string.section_today)
            // UV is always 0 after dark, so only show it in the daytime (OWM night icons end in "n").
            val isNight = weather.iconCode.endsWith("n")
            TodayTiles(
                weather = weather,
                showingForecast = showingForecast,
                // Paw check goes with the walk card: Settings' Dog walking switch hides both.
                pavementNow = currentSlots.firstOrNull()?.pavementTemperature?.takeIf { dogWalking },
                groundWet = currentSlots.firstOrNull()?.groundWet == true || isPrecipitating(weather.conditionMain),
                uvIndex = currentSlots.firstOrNull()?.uvIndex?.takeUnless { isNight },
                uvPeak = todayForecast?.hourlySlots?.filter { it.uvIndex != null }?.maxByOrNull { it.uvIndex ?: 0.0 },
                // The forecast's hour in progress, so it shows offline too.
                airQuality = currentSlots.firstOrNull()?.airQuality?.takeIf { airQualityEnabled },
                hasDog = dogWalking,
                nowEpochSeconds = nowEpochSeconds,
                thresholds = petWeatherThresholds,
                switches = todayTiles,
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        heroSection()
                        nextRainSection()
                        walkSection()
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        detailsSection()
                    }
                }
            } else {
                heroSection()
                nextRainSection()
                walkSection()
            }
            // Hourly strip (next 24h), starting at the slot in progress — see currentSlots above.
            val hourly = remember(currentSlots) { currentSlots.take(HOURLY_STRIP_COUNT) }
            if (hourly.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.section_hourly),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                HourlyStrip(slots = hourly)
            }

            if (!wide) detailsSection()
            // Weather radar (shared by the phone and wide layouts). Off via Remote Config
            // radar_enabled: not composing RadarCard means no RainViewer or tile requests.
            if (radarEnabled) {
                SectionHeader(R.string.section_radar)
                RadarCard(location = location, utcOffsetSeconds = weather.offsetSeconds)
            }

            // Upcoming days section (excludes today)
            if (upcomingDays.isNotEmpty() ||
                forecastState is LoadingState.Loading ||
                forecastState is LoadingState.Error
            ) {
                Text(
                    text = stringResource(R.string.section_upcoming),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                when {
                    // Wide screens show the days in two columns rather than stretched rows.
                    upcomingDays.isNotEmpty() -> upcomingDays.chunked(if (wide) 2 else 1).forEach { row ->
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            row.forEach { day ->
                                UpcomingDayRow(day = day, onClick = { selectedDay = day }, modifier = Modifier.weight(1f).fillMaxHeight())
                            }
                            if (row.size < 2 && wide) Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    forecastState is LoadingState.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    forecastState is LoadingState.Error -> Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = weatherErrorMessage(forecastState.errorKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (forecastState.canRetry) {
                            TextButton(onClick = onForecastRetry) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }
                }
            }
        }
    }

    selectedDay?.let { day ->
        DayDetailBottomSheet(day = day) { selectedDay = null }
    }
}

private const val HOURLY_STRIP_COUNT = 24
private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
private const val PET_SCENE_WIDTH_FRACTION = 0.8f
private val PET_SCENE_MAX_WIDTH = 280.dp

@Composable
private fun HourlyStrip(
    slots: List<HourlySlot>,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(slots) { slot -> HourlyCell(slot = slot) }
        }
    }
}

@Composable
private fun HourlyCell(
    slot: HourlySlot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = slot.timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        WeatherIcon(
            iconCode = slot.iconCode,
            contentDescription = slot.description,
            sizeDp = 32,
            epochSeconds = slot.epochSeconds,
        )
        Text(
            text = "${slot.temperature.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
        PrecipChance(percent = slot.precipitationChance)
    }
}

/** One line under the top card: when rain or snow starts or stops in the next 2 hours. */
@Composable
private fun NextRainLine(
    nextRain: NextRain,
    utcOffsetSeconds: Int,
) {
    val text = when (nextRain) {
        is NextRain.Starting -> stringResource(
            if (nextRain.snow) R.string.next_snow_starting else R.string.next_rain_starting,
            formatEpochTime(nextRain.epochSeconds, utcOffsetSeconds),
        )
        is NextRain.Stopping -> stringResource(
            if (nextRain.snow) R.string.next_snow_stopping else R.string.next_rain_stopping,
            formatEpochTime(nextRain.epochSeconds, utcOffsetSeconds),
        )
        is NextRain.Continuing -> stringResource(if (nextRain.snow) R.string.next_snow_continuing else R.string.next_rain_continuing)
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // The app's own shower and snow icons, as in the hourly row; the text says it all.
            WeatherIcon(
                iconCode = if (nextRain.snow) "13d" else "09d",
                contentDescription = "",
                sizeDp = 28,
                modifier = Modifier.clearAndSetSemantics {},
            )
            Text(text = text, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

/**
 * Small "raindrop N%" chip, showing [displayedRainChance]; renders nothing under 10% to avoid
 * clutter.
 */
@Composable
internal fun PrecipChance(
    percent: Int,
    modifier: Modifier = Modifier,
) {
    val shown = displayedRainChance(percent) ?: return
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Icon(
            imageVector = Icons.Default.WaterDrop,
            contentDescription = stringResource(R.string.cd_precip_chance),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(12.dp),
        )
        Text(
            text = stringResource(R.string.format_percent, shown),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

/**
 * A chance of rain as shown: nothing under 10%, where it's noise (most dry hours forecast 1%), and
 * otherwise to the nearest 10%, like Apple and Google Weather, since the forecast isn't more precise.
 */
internal fun displayedRainChance(percent: Int): Int? = if (percent < 10) null else (percent + 5) / 10 * 10

/** The WHO exposure category for a UV index, e.g. "High" for 6. */
@androidx.annotation.StringRes
internal fun uvLevelRes(uv: Double): Int {
    val value = uv.roundToInt()
    return when {
        value <= 2 -> R.string.uv_low
        value <= 5 -> R.string.uv_moderate
        value <= 7 -> R.string.uv_high
        value <= 10 -> R.string.uv_very_high
        else -> R.string.uv_extreme
    }
}

/**
 * Shown in place of the weather when a fetch fails and there's nothing cached to fall back to: the
 * pets (with the router knocked over when offline), what went wrong, and a way to try again.
 */
@Composable
private fun WeatherErrorState(
    errorKey: String,
    canRetry: Boolean,
    onRetry: () -> Unit,
    // When the weather on file is too old to show: "yesterday at 10:10 AM".
    lastUpdated: String? = null,
) {
    val offline = errorKey == "offline"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 32.dp, start = 8.dp, end = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        PetScene(
            mood = PetMood.CLOUDY,
            knockedOverRouter = offline,
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(24.dp))
                .background(PetMood.CLOUDY.skyColor)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        )
        Text(
            text = stringResource(if (offline) R.string.error_title_offline else R.string.error_title_generic),
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(top = 8.dp)
                .semantics { heading() },
        )
        Text(
            text = weatherErrorMessage(errorKey),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (lastUpdated != null) {
            Text(
                text = stringResource(R.string.error_last_updated, lastUpdated),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (canRetry) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            ) {
                Text(stringResource(R.string.action_try_again))
            }
        }
    }
}

/**
 * Above cached weather when the last refresh failed: when the data is from, and why it's old.
 * Pull to refresh tries again, and the banner goes once a refresh succeeds.
 */
@Composable
private fun StaleWeatherBanner(
    weather: CurrentWeather,
    errorKey: String,
    // The reading is past the cutoff and the screen shows the cached forecast for this hour.
    showingForecast: Boolean,
    nowEpochSeconds: Long,
) {
    val lead = when (val readingTime = readingTimeText(weather, nowEpochSeconds)) {
        null -> stringResource(R.string.stale_weather_saved)
        else -> stringResource(if (showingForecast) R.string.stale_forecast_from else R.string.stale_weather_from, readingTime)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(StaleBannerBg)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = if (errorKey == "offline") Icons.Outlined.WifiOff else Icons.Outlined.History,
            contentDescription = null,
            tint = StaleBannerText,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = listOfNotNull(
                lead,
                weatherErrorMessage(errorKey),
                // The banner has no button, so the offline message says how to retry; the other
                // messages already say when to try again.
                stringResource(R.string.error_pull_to_retry).takeIf { errorKey == "offline" },
            ).joinToString(" "),
            style = MaterialTheme.typography.bodyMedium,
            color = StaleBannerText,
        )
    }
}

/** When [weather] was observed: "10:10 AM" today, "yesterday at 10:10 AM", or "Sat at 10:10 AM". */
@Composable
private fun readingTimeText(
    weather: CurrentWeather,
    nowEpochSeconds: Long,
): String? {
    val observedAt = weather.observedAtEpochSeconds ?: return null
    val time = formatEpochTime(observedAt, weather.offsetSeconds)
    return when (val day = readingDay(observedAt, weather.offsetSeconds, nowEpochSeconds)) {
        ReadingDay.Today -> time
        ReadingDay.Yesterday -> stringResource(R.string.reading_time_yesterday, time)
        is ReadingDay.Earlier -> stringResource(R.string.reading_time_day, day.dayName, time)
    }
}

@Composable
internal fun weatherErrorMessage(errorKey: String): String =
    when (errorKey) {
        "offline" -> stringResource(R.string.error_offline)
        "city_not_found" -> stringResource(R.string.error_city_not_found)
        "missing_api_key", "bad_api_key" -> stringResource(R.string.error_bad_api_key)
        "rate_limited" -> stringResource(R.string.error_rate_limited)
        "server_error" -> stringResource(R.string.error_server)
        else -> stringResource(R.string.error_generic)
    }

/** Whole degrees: forecasts aren't accurate to a tenth, and other weather apps don't show one. */
@Composable
internal fun formatTemperature(
    value: Double,
    units: WeatherUnits,
): String =
    when (units) {
        WeatherUnits.METRIC -> stringResource(R.string.format_temperature_c, value.roundToInt())
        WeatherUnits.IMPERIAL -> stringResource(R.string.format_temperature_f, value.roundToInt())
    }

/**
 * Whole mph, and whole m/s from 10 up; below that a whole m/s is too coarse (3 m/s is anything
 * from a light to a gentle breeze), so it keeps one decimal.
 */
@Composable
internal fun formatWind(
    speed: Double,
    units: WeatherUnits,
): String =
    when {
        isCalmWind(speed, units) && units == WeatherUnits.METRIC -> stringResource(R.string.format_wind_calm_ms)
        isCalmWind(speed, units) -> stringResource(R.string.format_wind_calm_mph)
        units == WeatherUnits.METRIC && speed < WHOLE_MS_FROM -> stringResource(R.string.format_wind_ms, speed)
        units == WeatherUnits.METRIC -> stringResource(R.string.format_wind_ms_whole, speed.roundToInt())
        else -> stringResource(R.string.format_wind_mph, speed.roundToInt())
    }

/** At and above this many m/s (what rounds to 10.0), wind speeds show without a decimal. */
private const val WHOLE_MS_FROM = 9.95

/** A speed that would show as 0 (whole mph, or m/s to one decimal): written as a plain 0, with no direction. */
internal fun isCalmWind(
    speed: Double,
    units: WeatherUnits,
): Boolean = speed < (if (units == WeatherUnits.IMPERIAL) 0.5 else 0.05)

internal fun formatEpochTime(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
): String {
    val localTime = java.time.Instant
        .ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        .toLocalTime()
    return localTime.format(
        java.time.format.DateTimeFormatter
            .ofPattern("h:mm a"),
    )
}

/** The compass point a wind comes from, 0 (N) to 7 (NW) clockwise; indexes `R.array.compass_points_spoken`. */
internal fun windDirectionIndex(deg: Int): Int = ((deg + 22) / 45) % 8

@Composable
private fun UpcomingDayRow(
    day: DayForecast,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 4.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            WeatherIcon(iconCode = day.iconCode, contentDescription = day.description, sizeDp = 36)
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = day.dateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = day.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrecipChance(percent = day.precipitationChance)
            }
            Text(
                text = formatTemperature(day.tempMax, day.units),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = formatTemperature(day.tempMin, day.units),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
    }
}

// --- Previews ---

@Preview(showBackground = true)
@Composable
private fun CurrentWeatherEmptyPreview() {
    CatsDogsTheme {
        CurrentWeatherScreen(
            locations = emptyList(),
            activeIndex = 0,
            weatherState = LoadingState.Idle,
            onTabSelected = {},
            onAddCityClick = {},
        ) {}
    }
}

private val previewWeather = CurrentWeather(
    cityName = "London",
    conditionMain = "Clouds",
    description = "Broken clouds",
    iconCode = "04d",
    temperature = 15.0,
    feelsLike = 14.2,
    tempMin = 12.0,
    tempMax = 17.5,
    humidityPercent = 72,
    pressureHpa = 1012,
    windSpeed = 4.1,
    windDeg = 225,
    visibilityMeters = 9000,
    cloudPercent = 75,
    units = WeatherUnits.METRIC,
)

@Preview(showBackground = true)
@Composable
private fun CurrentWeatherSuccessPreview() {
    CatsDogsTheme {
        CurrentWeatherScreen(
            locations = listOf(
                SavedLocation("My Location", 51.5, -0.1, isCurrentLocation = true),
                SavedLocation("London, GB", 51.5074, -0.1278),
                SavedLocation("New York, US", 40.7128, -74.0060),
            ),
            activeIndex = 0,
            weatherState = LoadingState.Success(previewWeather),
            onTabSelected = {},
            onAddCityClick = {},
        ) {}
    }
}

@Preview(showBackground = true)
@Composable
private fun AddCitySheetPreview() {
    CatsDogsTheme {
        AddCitySheetContent(
            savedLocations = listOf(
                SavedLocation("My Location", 51.5, -0.1, isCurrentLocation = true),
                SavedLocation("London, GB", 51.5074, -0.1278),
            ),
            cityInput = "New Y",
            suggestions = listOf(
                CitySuggestion("New York, US", 40.71, -74.0),
                CitySuggestion("New Haven, US", 41.30, -72.92),
            ),
            suggestLoading = false,
            selectedSuggestion = null,
            onCityInputChange = {},
            onSuggestionChosen = {},
            onAddCity = {},
            onRemoveSaved = {},
            onDismiss = {},
        )
    }
}

@Composable
private fun SectionHeader(
    @androidx.annotation.StringRes text: Int,
) {
    Text(
        text = stringResource(text),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp),
    )
}

private val TEMP_TOP_TRIM = 14.dp

/**
 * The hero's "H … · L …": today's forecast high and low, widened to take in the temperature now.
 * OpenWeather's current `temp_max`/`temp_min` are the spread across nearby stations at this moment,
 * not the day's range, so they're only the fallback until today's forecast is in.
 */
internal fun heroHighLow(
    weather: CurrentWeather,
    today: DayForecast?,
): Pair<Double, Double> =
    if (today == null) {
        weather.tempMax to weather.tempMin
    } else {
        maxOf(today.tempMax, weather.temperature) to minOf(today.tempMin, weather.temperature)
    }
