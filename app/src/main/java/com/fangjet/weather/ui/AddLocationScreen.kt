package com.fangjet.weather.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fangjet.weather.R
import com.fangjet.weather.domain.CitySuggestion
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.model.AddLocationAction
import com.fangjet.weather.model.GeoLocationViewModel
import com.fangjet.weather.model.LocationFetchState
import com.fangjet.weather.model.LocationPermissionViewModel
import com.fangjet.weather.model.MIN_SEARCH_LENGTH
import com.fangjet.weather.model.addLocationAction
import com.fangjet.weather.model.matchedPrefixLength
import com.fangjet.weather.model.savedIndexOf
import com.fangjet.weather.ui.theme.CatsDogsTheme

/**
 * Add location, a screen of its own, opened from Manage Locations' Add location button and the
 * empty weather screen. It was a bottom sheet with the saved list above the search, which put the
 * results under the keyboard. Same layout and rules as iOS's `Views/AddLocationView.swift`.
 *
 * [onAdd] gets the location to save and the name to confirm it with ("Malmö added").
 */
@Composable
fun AddLocationRoute(
    locations: List<SavedLocation>,
    onAdd: (location: SavedLocation, name: String) -> Unit,
    onGoTo: (index: Int) -> Unit,
    onClose: () -> Unit,
    geoViewModel: GeoLocationViewModel = hiltViewModel(),
    locationViewModel: LocationPermissionViewModel = hiltViewModel(),
) {
    val query by geoViewModel.cityInput.collectAsStateWithLifecycle()
    val suggestions by geoViewModel.citySuggestions.collectAsStateWithLifecycle()
    val searching by geoViewModel.citySuggestLoading.collectAsStateWithLifecycle()
    val searchFailed by geoViewModel.searchFailed.collectAsStateWithLifecycle()
    val locationState by locationViewModel.state.collectAsStateWithLifecycle()
    // Every way out puts the keyboard away first, or it stays up over the screen underneath.
    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current

    fun leaving() {
        focusManager.clearFocus()
        keyboardController?.hide()
    }
    // The screen stays open until the fix arrives, then adds it and closes: closing first would
    // drop the fix with the screen.
    val addMyLocation = rememberAddMyLocation(
        locations = locations,
        onLocated = {
            leaving()
            onAdd(it, it.label)
        },
        viewModel = locationViewModel,
    )

    AddLocationScreen(
        query = query,
        suggestions = suggestions,
        searching = searching,
        searchFailed = searchFailed,
        savedLocations = locations,
        onQueryChange = geoViewModel::onCityInputChange,
        onAdd = { suggestion ->
            leaving()
            onAdd(
                SavedLocation(label = suggestion.label, latitude = suggestion.weatherLat, longitude = suggestion.weatherLon),
                suggestion.name,
            )
        },
        onAddByName = { name ->
            leaving()
            onAdd(SavedLocation(label = name, latitude = null, longitude = null), name)
        },
        onGoTo = { index ->
            leaving()
            onGoTo(index)
        },
        onClose = {
            leaving()
            onClose()
        },
        onAddMyLocation = addMyLocation,
        locatingMyLocation = locationState == LocationFetchState.Locating,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddLocationScreen(
    query: String,
    suggestions: List<CitySuggestion>,
    searching: Boolean,
    searchFailed: Boolean,
    savedLocations: List<SavedLocation>,
    onQueryChange: (String) -> Unit,
    onAdd: (CitySuggestion) -> Unit,
    onAddByName: (String) -> Unit,
    onGoTo: (index: Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    // Shown while My Location isn't saved and location access is on or can be asked for.
    onAddMyLocation: (() -> Unit)? = null,
    locatingMyLocation: Boolean = false,
) {
    val action = addLocationAction(query, suggestions, searching, searchFailed, savedLocations)
    val runAction: () -> Unit = {
        when (action) {
            is AddLocationAction.Add -> onAdd(action.suggestion)
            is AddLocationAction.GoTo -> onGoTo(action.index)
            is AddLocationAction.AddByName -> onAddByName(action.name)
            else -> Unit
        }
    }
    // The row the button and the keyboard's Search key would take, highlighted in the list.
    val target = when (action) {
        is AddLocationAction.Add -> action.suggestion
        is AddLocationAction.GoTo -> action.suggestion
        else -> null
    }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.add_location_title)) },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_close))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .consumeWindowInsets(padding)
                .imePadding(),
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .focusRequester(focusRequester),
                placeholder = { Text(stringResource(R.string.add_location_search_hint)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotEmpty()) {
                    {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cd_clear_search))
                        }
                    }
                } else {
                    null
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = false,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { runAction() }),
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp)
                    .height(4.dp),
            ) {
                if (searching) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            when {
                query.trim().length < MIN_SEARCH_LENGTH -> {
                    if (onAddMyLocation != null) {
                        MyLocationRow(onClick = onAddMyLocation, locating = locatingMyLocation)
                    }
                    Spacer(modifier = Modifier.weight(1f))
                }

                suggestions.isNotEmpty() -> LazyColumn(modifier = Modifier.weight(1f)) {
                    items(suggestions) { suggestion ->
                        val savedIndex = savedLocations.savedIndexOf(suggestion)
                        SuggestionRow(
                            suggestion = suggestion,
                            query = query,
                            saved = savedIndex >= 0,
                            isTarget = suggestion == target,
                            onClick = { if (savedIndex >= 0) onGoTo(savedIndex) else onAdd(suggestion) },
                        )
                    }
                }

                action == AddLocationAction.NotFound -> {
                    Text(
                        text = stringResource(R.string.error_city_not_found),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    )
                    Spacer(modifier = Modifier.weight(1f))
                }

                else -> Spacer(modifier = Modifier.weight(1f))
            }
            ActionBar(action = action, onClick = runAction)
        }
    }
}

@Composable
private fun MyLocationRow(
    onClick: () -> Unit,
    locating: Boolean,
) {
    ListItem(
        headlineContent = {
            Text(
                text = stringResource(R.string.add_my_location),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Medium,
            )
        },
        leadingContent = {
            if (locating) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(Icons.Default.LocationOn, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = !locating, onClick = onClick),
    )
}

@Composable
private fun SuggestionRow(
    suggestion: CitySuggestion,
    query: String,
    saved: Boolean,
    isTarget: Boolean,
    onClick: () -> Unit,
) {
    // The letters typed so far in bold, as the list narrows.
    val matched = matchedPrefixLength(suggestion.name, query)
    val name = buildAnnotatedString {
        withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(suggestion.name.take(matched)) }
        append(suggestion.name.drop(matched))
    }
    ListItem(
        headlineContent = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = suggestion.detail.takeIf { it.isNotEmpty() }?.let { detail ->
            { Text(detail, maxLines = 1, overflow = TextOverflow.Ellipsis) }
        },
        trailingContent = when {
            saved -> {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.location_saved),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                }
            }

            isTarget -> {
                {
                    // What the keyboard's Search key would take; the button says it in words.
                    Icon(
                        Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            else -> null
        },
        colors = ListItemDefaults.colors(
            containerColor = if (isTarget) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent,
        ),
        modifier = Modifier.clickable(onClick = onClick),
    )
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}

/** The button above the keyboard, which names what it does, or why there's none. */
@Composable
private fun ActionBar(
    action: AddLocationAction,
    onClick: () -> Unit,
) {
    val buttonModifier = Modifier
        .fillMaxWidth()
        .padding(horizontal = 16.dp, vertical = 8.dp)
    when (action) {
        is AddLocationAction.Add -> Button(onClick = onClick, modifier = buttonModifier) {
            ButtonLabel(stringResource(R.string.format_add_location, action.suggestion.shortLabel))
        }

        is AddLocationAction.AddByName -> Button(onClick = onClick, modifier = buttonModifier) {
            ButtonLabel(stringResource(R.string.format_add_typed_location, action.name))
        }

        is AddLocationAction.GoTo -> OutlinedButton(onClick = onClick, modifier = buttonModifier) {
            ButtonLabel(stringResource(R.string.format_go_to_location, action.suggestion.shortLabel))
        }

        AddLocationAction.PickFromList -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.add_pick_from_list),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(start = 6.dp),
            )
        }

        AddLocationAction.None, AddLocationAction.NotFound -> Unit
    }
}

@Composable
private fun ButtonLabel(text: String) {
    Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Preview(showBackground = true)
@Composable
private fun AddLocationScreenPreview() {
    CatsDogsTheme {
        AddLocationScreen(
            query = "Malmo",
            suggestions = listOf(
                CitySuggestion("Malmö, Skåne County, SE", 55.61, 13.0, population = 362_133),
                CitySuggestion("Malmo, Nebraska, US", 41.27, -96.72, population = 118),
                CitySuggestion("Malmö, Kalmar, SE", 57.25, 16.47),
            ),
            searching = false,
            searchFailed = false,
            savedLocations = listOf(SavedLocation("London, England, GB", 51.51, -0.13)),
            onQueryChange = {},
            onAdd = {},
            onAddByName = {},
            onGoTo = {},
            onClose = {},
        )
    }
}
