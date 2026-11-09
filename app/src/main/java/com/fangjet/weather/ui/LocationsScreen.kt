package com.fangjet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.fangjet.weather.R
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.notificationIndex
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Manage Locations: reorder and remove, and the Add location button, which opens the full-screen
 * [AddLocationRoute]. [locationAdded] is the name of a location just added there, confirmed here
 * once ("Malmö added") and then cleared with [onLocationAddedShown].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsRoute(
    locations: List<SavedLocation>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    onNavigateBack: () -> Unit,
    onAddLocation: () -> Unit,
    locationAdded: String? = null,
    onLocationAddedShown: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    // Outlives the effect below, which restarts when the name is cleared.
    val scope = rememberCoroutineScope()
    val resources = LocalResources.current
    LaunchedEffect(locationAdded) {
        val name = locationAdded ?: return@LaunchedEffect
        onLocationAddedShown()
        scope.launch { snackbarHostState.showSnackbar(resources.getString(R.string.format_location_added, name)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_manage_locations)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        DraggableLocationList(
            locations = locations,
            onReorder = onReorder,
            onRemove = onRemove,
            onAddLocation = onAddLocation,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        )
    }
}

@Composable
internal fun DraggableLocationList(
    locations: List<SavedLocation>,
    onReorder: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (index: Int) -> Unit,
    modifier: Modifier = Modifier,
    onAddLocation: (() -> Unit)? = null,
) {
    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var itemHeightPx by remember { mutableIntStateOf(1) }

    // Resolved here: the semantics block below isn't composable.
    val moveUpLabel = stringResource(R.string.a11y_move_up)
    val moveDownLabel = stringResource(R.string.a11y_move_down)

    val notificationIndex = locations.notificationIndex()

    val targetIndex: Int? = draggingIndex?.let { from ->
        val steps = (dragOffsetY / itemHeightPx).roundToInt()
        (from + steps).coerceIn(0, locations.size - 1)
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
    ) {
        locations.forEachIndexed { index, location ->
            val isDragging = draggingIndex == index

            val visualOffsetY = when {
                isDragging -> dragOffsetY
                draggingIndex != null && targetIndex != null -> {
                    val from = draggingIndex!!
                    val to = targetIndex
                    when {
                        from < to && index in (from + 1)..to -> -itemHeightPx.toFloat()
                        from > to && index in to until from -> itemHeightPx.toFloat()
                        else -> 0f
                    }
                }
                else -> 0f
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { coords ->
                        if (itemHeightPx == 1 || coords.size.height > 0) {
                            itemHeightPx = coords.size.height
                        }
                    }.zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer { translationY = visualOffsetY }
                    .background(
                        when {
                            isDragging -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        },
                    ),
            ) {
                ListItem(
                    headlineContent = {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            // The name and the bell take all the room the delete button leaves, so
                            // a long name wraps at the button, not halfway across (two weighted
                            // children would split the row equally).
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = location.label,
                                    modifier = Modifier.weight(1f, fill = false),
                                )
                                if (index == notificationIndex) {
                                    Icon(
                                        Icons.Default.Notifications,
                                        contentDescription = stringResource(R.string.cd_notification_city),
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(start = 6.dp).size(16.dp),
                                    )
                                }
                            }
                            // The last city stays, so the app always has one to show: no button
                            // for it, rather than one that does nothing (as on iOS).
                            if (locations.size > 1) {
                                IconButton(onClick = { onRemove(index) }) {
                                    Icon(
                                        Icons.Default.Delete,
                                        contentDescription = stringResource(R.string.cd_delete_location, location.label),
                                    )
                                }
                            }
                        }
                    },
                    leadingContent = {
                        Icon(
                            Icons.Default.DragHandle,
                            // The row's Move up / Move down actions do this for accessibility services.
                            contentDescription = null,
                            modifier = Modifier.pointerInput(index) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggingIndex = index
                                        dragOffsetY = 0f
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount.y
                                    },
                                    onDragEnd = {
                                        val from = draggingIndex
                                        if (from != null) {
                                            val steps = (dragOffsetY / itemHeightPx).roundToInt()
                                            val to = (from + steps).coerceIn(0, locations.size - 1)
                                            if (from != to) onReorder(from, to)
                                        }
                                        draggingIndex = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggingIndex = null
                                        dragOffsetY = 0f
                                    },
                                )
                            },
                        )
                    },
                    // ListItem merges its children into one accessibility node, so the actions go
                    // here, on the node that also carries the city name. Reordering is drag-only on
                    // screen, so TalkBack and Switch Access get Move up / Move down actions instead.
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics {
                            customActions = listOfNotNull(
                                if (index > 0) {
                                    CustomAccessibilityAction(moveUpLabel) {
                                        onReorder(index, index - 1)
                                        true
                                    }
                                } else {
                                    null
                                },
                                if (index < locations.size - 1) {
                                    CustomAccessibilityAction(moveDownLabel) {
                                        onReorder(index, index + 1)
                                        true
                                    }
                                } else {
                                    null
                                },
                            )
                        }.padding(4.dp),
                    colors = androidx.compose.material3.ListItemDefaults.colors(
                        containerColor = androidx.compose.ui.graphics.Color.Transparent,
                    ),
                )
            }
        }
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.locations_notification_footnote),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 6.dp),
            )
        }
        if (onAddLocation != null) {
            OutlinedButton(
                onClick = onAddLocation,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.action_add_city))
            }
        }
    }
}
