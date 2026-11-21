package com.fangjet.weather.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.model.CityListViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LocationsScreen(
    viewModel: CityListViewModel,
    onAddLocationClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val locations by viewModel.locations.collectAsState()
    val activeIndex by viewModel.activeIndex.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(text = "Manage Locations") }
            )
        },
        modifier = modifier,
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (locations.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = "No locations added yet.")
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    itemsIndexed(locations) { index, location ->
                        ListItem(
                            headlineContent = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = location.label,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (index == activeIndex) {
                                        Icon(
                                            imageVector = Icons.Default.Notifications,
                                            contentDescription = "Notification city",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.padding(start = 6.dp).size(16.dp),
                                        )
                                    }
                                }
                            },
                            trailingContent = {
                                if (locations.size > 1) {
                                    IconButton(onClick = { viewModel.removeLocation(index) }) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Delete Location"
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = if (index == activeIndex) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                                } else {
                                    Color.Unspecified
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Divider()
                    }
                }
            }

            OutlinedButton(
                onClick = onAddLocationClick,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 16.dp)
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(text = "Add City")
            }
        }

} // This is the closing brace on line 119 that ends the function
}

object LocationsRoute {
    const val route = "locations"
}

