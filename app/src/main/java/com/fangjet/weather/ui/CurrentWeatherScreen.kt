package com.fangjet.weather.ui

import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.fangjet.weather.R
import com.fangjet.weather.domain.CurrentWeather
import com.fangjet.weather.domain.DayForecast
import com.fangjet.weather.domain.HourlySlot
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.WeatherUnits
import com.fangjet.weather.model.LoadingState
import com.fangjet.weather.model.LocationPermissionViewModel
import com.fangjet.weather.model.UpdatePromptViewModel
import com.fangjet.weather.model.WeatherForecastViewModel
import com.fangjet.weather.model.WeatherScreen
import com.fangjet.weather.model.WeatherScreenConfig
import com.fangjet.weather.model.WeatherScreenState
import com.fangjet.weather.model.WeekSection
import com.fangjet.weather.model.weatherScreen
import com.fangjet.weather.ui.pets.PetScene
import com.fangjet.weather.ui.pets.WalkCard
import com.fangjet.weather.ui.pets.inkColor
import com.fangjet.weather.ui.pets.resolve
import com.fangjet.weather.ui.pets.skyColor
import com.fangjet.weather.ui.theme.CatsDogsTheme
import com.fangjet.weather.ui.theme.StaleBannerBg
import com.fangjet.weather.ui.theme.StaleBannerText
import com.fangjet.weather.ui.theme.WalkOkayBg
import com.fangjet.weather.ui.theme.WalkOkayText
import com.fangjet.weather.util.HourlyStripItem
import com.fangjet.weather.util.NextRain
import com.fangjet.weather.util.ReadingDay
import com.fangjet.weather.util.SunEvent
import com.fangjet.weather.util.UpdatePrompt
import com.fangjet.weather.util.displayedRainChance
import com.fangjet.weather.util.formatEpochTime
import com.fangjet.weather.util.hourlyStrip
import com.fangjet.weather.util.isCalmWind
import com.fangjet.weather.util.nextRain
import com.fangjet.weather.util.rainOutlook
import com.fangjet.weather.util.readingDay
import com.fangjet.weather.util.usesMilesForDistance
import java.time.Instant
import kotlin.math.roundToInt

/**
 * "My Location" for screens that can add it. Returns the Add location screen's "Add My Location"
 * action, or null when it's already in the list. Without access it asks first, so "Only this time" /
 * "Ask every time" users can still add it; when Android won't ask any more, it opens the app's
 * settings page and adds it on return if allowed there (see [rememberLocationPermissionRequest]).
 * The weather screen adds and moves My Location by itself: see [WeatherForecastViewModel].
 */
@Composable
internal fun rememberAddMyLocation(
    locations: List<SavedLocation>,
    onLocated: (SavedLocation) -> Unit,
    viewModel: LocationPermissionViewModel = hiltViewModel(),
): (() -> Unit)? {
    val requestPermission = rememberLocationPermissionRequest(viewModel)
    val currentLocations by rememberUpdatedState(locations)
    val currentOnLocated by rememberUpdatedState(onLocated)
    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        // Not when My Location turned up meanwhile, from the weather screen say.
        viewModel.myLocationAdded.collect { if (currentLocations.none { it.isCurrentLocation }) currentOnLocated(it) }
    }
    LaunchedEffect(viewModel) {
        viewModel.addMyLocationFailed.collect { Toast.makeText(context, R.string.add_my_location_failed, Toast.LENGTH_LONG).show() }
    }
    if (locations.any { it.isCurrentLocation }) return null
    return { if (viewModel.addMyLocation()) requestPermission() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CurrentWeatherRoute(
    onOpenSettings: () -> Unit = {},
    onAddLocation: () -> Unit = {},
) {
    val activity = LocalActivity.current as ComponentActivity
    // What the screen shows and does when: refreshes and My Location. It only gets told what happened.
    val viewModel = hiltViewModel<WeatherForecastViewModel>(viewModelStoreOwner = activity)
    // The activity's instance, the one that decides whether the app is blocked for an update.
    val updatePromptViewModel = hiltViewModel<UpdatePromptViewModel>(viewModelStoreOwner = activity)
    val updatePrompt by updatePromptViewModel.prompt.collectAsStateWithLifecycle()
    val unitOverride by viewModel.unitOverride.collectAsStateWithLifecycle()
    val distanceInMiles = LocalContext.current.usesMilesForDistance(unitOverride)

    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val activeIndex by viewModel.activeIndex.collectAsStateWithLifecycle()
    val screen by viewModel.screen.collectAsStateWithLifecycle()
    val staleErrorKey by viewModel.staleErrorKey.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val temperatureUnitsShown by viewModel.temperatureUnitsShown.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.becameActive()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.refreshWhileActive() }
    }
    LaunchedEffect(lifecycleOwner, staleErrorKey) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) { viewModel.recheckWhileStale() }
    }

    val context = LocalContext.current
    LaunchedEffect(viewModel) {
        viewModel.addMyLocationFailed.collect {
            Toast.makeText(context, R.string.add_my_location_failed, Toast.LENGTH_LONG).show()
        }
    }

    CompositionLocalProvider(
        LocalDistanceInMiles provides distanceInMiles,
        LocalTemperatureUnitsShown provides temperatureUnitsShown,
    ) {
        CurrentWeatherScreen(
            locations = locations,
            activeIndex = activeIndex,
            screen = screen,
            isRefreshing = isRefreshing,
            onTabSelected = viewModel::selectCity,
            onOpenSettings = {
                viewModel.logSettingsOpened()
                onOpenSettings()
            },
            onAddCityClick = onAddLocation,
            topBanner = {
                if (updatePrompt == UpdatePrompt.RECOMMENDED) {
                    UpdateBanner(
                        onDismiss = updatePromptViewModel::dismissBanner,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                    )
                }
            },
            onRefresh = viewModel::pulledToRefresh,
            onRetry = viewModel::retry,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CurrentWeatherScreen(
    modifier: Modifier = Modifier,
    locations: List<SavedLocation>,
    activeIndex: Int,
    // What to show for the active city, worked out by the weather view model.
    screen: WeatherScreen,
    isRefreshing: Boolean = false,
    onTabSelected: (Int) -> Unit,
    onOpenSettings: () -> Unit = {},
    onAddCityClick: () -> Unit,
    onRefresh: () -> Unit = {},
    // Under the city tabs: the update banner, when a newer version is recommended.
    topBanner: @Composable () -> Unit = {},
    onRetry: () -> Unit,
) {
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
                        // Locations are added from Manage Locations in Settings, or the empty
                        // screen's button when there are none.
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
                onRefresh = onRefresh,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp)
                        .verticalScroll(rememberScrollState())
                        // The same gap below the tabs (or the title bar) as between the cards.
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    when (screen) {
                        WeatherScreen.Loading -> Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 48.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator()
                        }

                        is WeatherScreen.Error -> WeatherErrorState(
                            errorKey = screen.errorKey,
                            canRetry = screen.canRetry,
                            onRetry = onRetry,
                            lastUpdated = screen.oldReading?.let { readingTimeText(it, screen.nowEpochSeconds) },
                        )

                        is WeatherScreen.Weather -> {
                            val state = screen.state
                            state.stale?.let {
                                StaleWeatherBanner(
                                    weather = it.reading,
                                    errorKey = it.errorKey,
                                    showingForecast = state.showingForecast,
                                    nowEpochSeconds = state.nowEpochSeconds,
                                )
                            }
                            val location = locations.getOrNull(activeIndex)
                            // The moon (hero scene and night icons) is lit from the other side down south.
                            CompositionLocalProvider(LocalSouthernHemisphere provides ((location?.latitude ?: 0.0) < 0)) {
                                CurrentWeatherContent(
                                    state = state,
                                    location = location,
                                    onForecastRetry = onRetry,
                                )
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

@Composable
private fun CurrentWeatherContent(
    // Everything shown, worked out by the view model at its clock.
    state: WeatherScreenState,
    modifier: Modifier = Modifier,
    location: SavedLocation? = null,
    onForecastRetry: () -> Unit = {},
) {
    val weather = state.weather
    val todayForecast = state.today
    val seasonalTheme = state.seasonalTheme
    val mood = state.mood
    val night = state.night
    val heroInk = seasonalTheme?.heroInk(night) ?: mood.inkColor
    var selectedDay by remember { mutableStateOf<DayForecast?>(null) }
    val heroCaption = state.heroCaption.resolve()

    // Landscape phones and tablets put the hero beside today's details instead of stacking
    // everything in one stretched column.
    CompositionLocalProvider(LocalSeasonalTheme provides seasonalTheme) {
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
                    colors = CardDefaults.cardColors(containerColor = seasonalTheme?.heroBackground(night) ?: mood.skyColor),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        // Kept a bit smaller than the card so the scene doesn't dominate, especially on wide screens.
                        PetScene(
                            mood = mood,
                            moonPhase = moonPhaseAt(state.nowEpochSeconds),
                            modifier = Modifier
                                .fillMaxWidth(PET_SCENE_WIDTH_FRACTION)
                                .widthIn(max = PET_SCENE_MAX_WIDTH),
                            decorations = seasonalTheme?.decorations.orEmpty(),
                        )
                        // Medium weight on the small lines so they hold up on the brighter hero colours.
                        Text(
                            text = weather.description,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = heroInk,
                        )
                        if (location != null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = weather.cityName,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = heroInk,
                            )
                        }
                        Text(
                            text = formatTemperature(weather.temperature, weather.units),
                            style = MaterialTheme.typography.displayLarge,
                            // Baloo 2 reserves a lot of empty space above its digits; pull the number up
                            // into it so it sits close under the city name.
                            modifier = Modifier.layout { measurable, constraints ->
                                val placeable = measurable.measure(constraints)
                                val cut = TEMP_TOP_TRIM.roundToPx()
                                layout(placeable.width, placeable.height - cut) { placeable.place(0, -cut) }
                            },
                            fontWeight = FontWeight.Bold,
                            color = heroInk,
                        )
                        val spokenRange = stringResource(R.string.label_temp_range_spoken, state.high, state.low)
                        Text(
                            text = stringResource(R.string.label_temp_range, state.high, state.low),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = heroInk,
                            // "H 64°" would be read as the letter H.
                            modifier = Modifier.clearAndSetSemantics { text = AnnotatedString(spokenRange) },
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = heroCaption,
                            style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, lineHeight = 26.sp),
                            color = heroInk,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            val nextRainSection: @Composable () -> Unit = {
                // "Rain starting around 3:15 PM", only when rain or snow is due in the next 2 hours.
                // Not when the rain outlook shows: it says the same as its headline.
                val nextRain = state.nextRain
                if (state.rainOutlook == null && nextRain != null) {
                    NextRainLine(nextRain, nextRainText(nextRain, weather.offsetSeconds))
                }
            }
            val walkSection: @Composable () -> Unit = {
                state.walkCard?.let { WalkCard(content = it) }
            }
            val detailsSection: @Composable () -> Unit = {
                TodayTiles(state.todayTiles)
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
                // The next 24 hours from the one in progress. No section headers: each card says what it is.
                if (state.hourlyStrip.isNotEmpty()) {
                    HourlyStrip(items = state.hourlyStrip, utcOffsetSeconds = weather.offsetSeconds)
                }
                // Straight after the hourly strip, before the tiles (or the radar, when the tiles sit
                // beside the hero), in both layouts.
                state.rainOutlook?.let {
                    RainOutlookCard(outlook = it, units = weather.units, utcOffsetSeconds = weather.offsetSeconds)
                }

                if (!wide) detailsSection()
                // Weather radar (shared by the phone and wide layouts). Off via Remote Config
                // radar_enabled: not composing RadarCard means no RainViewer or tile requests.
                if (state.showsRadar) {
                    RadarCard(location = location, utcOffsetSeconds = weather.offsetSeconds)
                }

                // The rest of the week, after today.
                when (val week = state.week) {
                    // Wide screens show the days in two columns rather than stretched rows.
                    is WeekSection.Days -> week.days.chunked(if (wide) 2 else 1).forEach { row ->
                        Row(
                            modifier = Modifier.height(IntrinsicSize.Min),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                        ) {
                            row.forEach { day ->
                                UpcomingDayRow(
                                    day = day,
                                    onClick = { selectedDay = day },
                                    modifier = Modifier.weight(1f).fillMaxHeight(),
                                )
                            }
                            if (row.size < 2 && wide) Spacer(modifier = Modifier.weight(1f).fillMaxHeight())
                        }
                    }

                    WeekSection.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }

                    is WeekSection.Error -> Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = weatherErrorMessage(week.errorKey),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        if (week.canRetry) {
                            TextButton(onClick = onForecastRetry) {
                                Text(stringResource(R.string.action_retry))
                            }
                        }
                    }

                    null -> Unit
                }
            }
        }
    }

    selectedDay?.let { day ->
        DayDetailBottomSheet(day = day) { selectedDay = null }
    }
}

private val WIDE_LAYOUT_MIN_WIDTH = 600.dp
private const val PET_SCENE_WIDTH_FRACTION = 0.8f
private val PET_SCENE_MAX_WIDTH = 280.dp

@Composable
private fun HourlyStrip(
    items: List<HourlyStripItem>,
    utcOffsetSeconds: Int,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = seasonalCardColor()),
    ) {
        LazyRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            items(items) { item ->
                when (item) {
                    is HourlyStripItem.Hour -> HourlyCell(slot = item.slot)
                    is HourlyStripItem.Sun -> SunMarker(event = item.event, utcOffsetSeconds = utcOffsetSeconds)
                }
            }
        }
    }
}

/**
 * A sunrise or sunset between two hours of the strip: half a column, with the time ("6:50"), the
 * sun on the horizon in the hours' icon space and a thin line beside the temperatures. It's never
 * taller than the hours beside it, so the strip doesn't grow for it.
 */
@Composable
private fun SunMarker(
    event: SunEvent,
    utcOffsetSeconds: Int,
    modifier: Modifier = Modifier,
) {
    val dark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val spoken = stringResource(
        if (event.sunrise) R.string.hourly_sunrise else R.string.hourly_sunset,
        formatEpochTime(event.epochSeconds, utcOffsetSeconds),
    )
    Column(
        modifier = modifier
            .padding(horizontal = 2.dp)
            .clearAndSetSemantics { contentDescription = spoken },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = formatShortEpochTime(event.epochSeconds, utcOffsetSeconds),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            // The walk card's amber, the dark shade on the light card and the pale one on the dark.
            color = if (dark) WalkOkayBg else WalkOkayText,
        )
        HourIconSpace {
            Image(
                painter = painterResource(if (event.sunrise) R.drawable.wx_sunrise else R.drawable.wx_sunset),
                contentDescription = null,
                modifier = Modifier.size(28.dp),
            )
        }
        Box(
            modifier = Modifier
                .width(1.5.dp)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(1.dp)),
        ) {
            // An unseen temperature gives the line its height whatever the text size; it overflows
            // the line's width without widening it.
            Text(
                text = "0°",
                modifier = Modifier
                    .wrapContentWidth(unbounded = true)
                    .alpha(0f),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/** An hour's icon on its own, and the smaller one with a rain chance under it. Same as iOS's `HourIconMetrics`. */
private val HOUR_ICON_SIZE = 32.dp
private val HOUR_ICON_BESIDE_CHANCE = 24.dp
private val HOUR_CHANCE_SPACING = 2.dp

/**
 * The icon's space in an hour of the strip, the same height in every hour, so the card never
 * changes height with the rain and never ends in a gap. An hour with a rain chance shows a smaller
 * icon with the chance under it, inside the space; an hour without one, the full-size icon in the
 * middle. The sunrise and sunset markers use it for the sun too, so their lines meet the
 * temperatures. Same sizes as iOS's `HourIconSpace`.
 */
@Composable
private fun HourIconSpace(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.heightIn(min = HOUR_ICON_SIZE),
        contentAlignment = Alignment.Center,
    ) {
        // An unseen smaller icon and chance, 1dp wide, give the space its height at any font scale,
        // whether or not any hour has rain.
        Column(
            modifier = Modifier
                .width(1.dp)
                .wrapContentWidth(unbounded = true)
                .alpha(0f)
                .clearAndSetSemantics {},
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(HOUR_CHANCE_SPACING),
        ) {
            Spacer(Modifier.size(HOUR_ICON_BESIDE_CHANCE))
            PrecipChance(percent = 50)
        }
        content()
    }
}

@Composable
private fun HourlyCell(
    slot: HourlySlot,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .padding(horizontal = 8.dp)
            .semantics { isTraversalGroup = true },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = slot.timeLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        HourIconSpace {
            if (displayedRainChance(slot.precipitationChance) != null) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(HOUR_CHANCE_SPACING),
                ) {
                    HourIcon(slot, HOUR_ICON_BESIDE_CHANCE)
                    // Read last, after the temperature, as before it moved up beside the icon.
                    PrecipChance(
                        percent = slot.precipitationChance,
                        modifier = Modifier.semantics { traversalIndex = 1f },
                    )
                }
            } else {
                HourIcon(slot, HOUR_ICON_SIZE)
            }
        }
        Text(
            text = "${slot.temperature.roundToInt()}°",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun HourIcon(
    slot: HourlySlot,
    size: Dp,
) {
    WeatherIcon(
        iconCode = slot.iconCode,
        contentDescription = slot.description,
        sizeDp = size.value.roundToInt(),
        epochSeconds = slot.epochSeconds,
    )
}

/** "Rain starting around 3:15 PM": the next-rain line's words, also the rain outlook's headline. */
@Composable
internal fun nextRainText(
    nextRain: NextRain,
    utcOffsetSeconds: Int,
): String =
    when (nextRain) {
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

/** One line under the top card: when rain or snow starts or stops in the next 2 hours. */
@Composable
private fun NextRainLine(
    nextRain: NextRain,
    text: String,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = seasonalCardColor()),
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

/**
 * Whole degrees: forecasts aren't accurate to a tenth, and other weather apps don't show one.
 * "89 °F", or "89°" when Remote Config `temperature_units_shown` leaves the unit out.
 */
@Composable
internal fun formatTemperature(
    value: Double,
    units: WeatherUnits,
    unitShown: Boolean = LocalTemperatureUnitsShown.current,
): String {
    val text = temperatureText(value, units, unitShown)
    return stringResource(text.resId, text.degrees)
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

/** "6:50" in the city's time, without AM or PM: the hours either side of a sun marker say which. */
internal fun formatShortEpochTime(
    epochSeconds: Long,
    utcOffsetSeconds: Int,
): String =
    java.time.Instant
        .ofEpochSecond(epochSeconds)
        .atZone(java.time.ZoneOffset.ofTotalSeconds(utcOffsetSeconds))
        .toLocalTime()
        .format(
            java.time.format.DateTimeFormatter
                .ofPattern("h:mm"),
        )

/** Wide enough for "H 85° · L 76°" in bodyLarge, at any font size. */
private val TEMPERATURE_RANGE_WIDTH = 112.sp

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
            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Text(
                    text = day.dateLabel,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                // 2 sp up from bodySmall.
                Text(
                    text = day.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PrecipChance(percent = day.precipitationChance)
            }
            WeatherIcon(iconCode = day.iconCode, contentDescription = day.description, sizeDp = 36)
            // The high and low as the hero has them, "H 85° · L 76°", with the high stronger, in a
            // column wide enough for that, so the icons line up down the list.
            val high = day.tempMax.roundToInt()
            val low = day.tempMin.roundToInt()
            val spokenRange = stringResource(R.string.label_temp_range_spoken, high, low)
            val highText = stringResource(R.string.label_temp_high, high)
            val lowText = stringResource(R.string.label_temp_low, low)
            val lowColor = MaterialTheme.colorScheme.onSurfaceVariant
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(highText) }
                    withStyle(SpanStyle(color = lowColor)) { append(" · $lowText") }
                },
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.End,
                // "H 85°" would be read as the letter H.
                modifier = Modifier
                    .widthIn(min = with(LocalDensity.current) { TEMPERATURE_RANGE_WIDTH.toDp() })
                    .clearAndSetSemantics { text = AnnotatedString(spokenRange) },
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
            screen = WeatherScreen.Loading,
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
            screen = weatherScreen(
                current = LoadingState.Success(previewWeather),
                forecast = LoadingState.Idle,
                staleErrorKey = null,
                nowEpochSeconds = Instant.now().epochSecond,
                hasDog = true,
                config = WeatherScreenConfig(),
            ),
            onTabSelected = {},
            onAddCityClick = {},
        ) {}
    }
}

/** 9 AM to 8 PM UTC with a sunset at 5:49 PM, each hour's rain chance from [chance]. */
private fun previewHourlyStrip(chance: (Int) -> Int): List<HourlyStripItem> {
    val dayStart = 1_700_000_000L - 1_700_000_000L % 86_400
    val slots = (9..20).map { hour ->
        HourlySlot(
            timeLabel = "${if (hour % 12 == 0) 12 else hour % 12} ${if (hour < 12) "AM" else "PM"}",
            iconCode = if (chance(hour) > 0) "10d" else "02d",
            description = "Cloudy",
            temperature = 18.0,
            feelsLike = 18.0,
            windSpeed = 2.0,
            windDeg = 0,
            humidity = 60,
            pressure = 1010,
            units = WeatherUnits.METRIC,
            epochSeconds = dayStart + hour * 3600,
            precipitationChance = chance(hour),
        )
    }
    return hourlyStrip(slots, listOf(SunEvent(sunrise = false, epochSeconds = dayStart + 17 * 3600 + 49 * 60)), dayStart + 9 * 3600)
}

/** The hourly card is the same height with no rain, rain every hour and rain only off screen. */
@Preview(showBackground = true, widthDp = 390)
@Preview(showBackground = true, widthDp = 390, fontScale = 2f)
@Composable
private fun HourlyStripPreview() {
    CatsDogsTheme {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            HourlyStrip(items = previewHourlyStrip { 0 }, utcOffsetSeconds = 0)
            HourlyStrip(items = previewHourlyStrip { 30 + it }, utcOffsetSeconds = 0)
            HourlyStrip(items = previewHourlyStrip { if (it >= 19) 80 else 0 }, utcOffsetSeconds = 0)
            HourlyStrip(items = previewHourlyStrip { if (it == 18) 60 else 0 }.drop(6), utcOffsetSeconds = 0)
        }
    }
}

private val TEMP_TOP_TRIM = 18.dp
