package com.fangjet.weather.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fangjet.weather.BuildConfig
import com.fangjet.weather.R
import com.fangjet.weather.domain.UnitOverride
import com.fangjet.weather.model.SettingsViewModel
import com.fangjet.weather.worker.canPostNotifications
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsRoute(
    onNavigateBack: () -> Unit,
    onOpenNotifications: () -> Unit = {},
    onOpenLocations: () -> Unit = {},
) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val unitOverride by viewModel.unitOverride.collectAsStateWithLifecycle()
    val shareUsageStatistics by viewModel.shareUsageStatistics.collectAsStateWithLifecycle()
    val showWalkCard by viewModel.showWalkCard.collectAsStateWithLifecycle()
    val notificationsOnCount by viewModel.notificationsOnCount.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val cacheClearedMessage = stringResource(R.string.settings_cache_cleared)

    val locationGranted = rememberGrantedOnResume {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val backgroundLocationGranted = rememberGrantedOnResume {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_BACKGROUND_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
    }
    val notificationGranted = rememberGrantedOnResume { context.canPostNotifications() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.screen_settings)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.cd_navigate_back),
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            SettingsSectionHeader(stringResource(R.string.settings_section_my_pets))
            // One switch for everything dog: the walk card, Paw check and the walk notifications.
            SettingsSwitchRow(
                label = stringResource(R.string.settings_show_walk_card),
                summary = stringResource(R.string.settings_show_walk_card_summary),
                checked = showWalkCard,
                onCheckedChange = viewModel::setShowWalkCard,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_locations))

            SettingsLinkRow(
                label = stringResource(R.string.settings_manage_locations),
                onClick = onOpenLocations,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsNavRow(
                label = stringResource(R.string.settings_notifications),
                value = if (notificationGranted && notificationsOnCount > 0) {
                    stringResource(R.string.settings_notifications_on_count, notificationsOnCount)
                } else {
                    stringResource(R.string.settings_notifications_off)
                },
                onClick = onOpenNotifications,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_location))

            if (locationGranted) {
                Text(
                    // Android's own words for the two kinds of access.
                    text = stringResource(
                        if (backgroundLocationGranted) {
                            R.string.settings_location_granted_always
                        } else {
                            R.string.settings_location_granted
                        },
                    ),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                )
            } else {
                SettingsLinkRow(
                    label = stringResource(R.string.settings_location_required),
                    onClick = context::openAppDetailsSettings,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_units))

            Column(modifier = Modifier.selectableGroup()) {
                UnitOverride.entries.forEach { option ->
                    UnitOptionRow(
                        label = when (option) {
                            UnitOverride.SYSTEM -> stringResource(R.string.settings_unit_system)
                            UnitOverride.METRIC -> stringResource(R.string.settings_unit_metric)
                            UnitOverride.IMPERIAL -> stringResource(R.string.settings_unit_imperial)
                        },
                        selected = unitOverride == option,
                        onClick = { viewModel.setUnitOverride(option) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_cache))

            SettingsLinkRow(
                label = stringResource(R.string.settings_clear_cache),
                onClick = {
                    viewModel.clearCache()
                    scope.launch { snackbarHostState.showSnackbar(cacheClearedMessage) }
                },
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_privacy))

            SettingsSwitchRow(
                label = stringResource(R.string.settings_share_usage_statistics),
                summary = stringResource(R.string.settings_share_usage_statistics_summary),
                checked = shareUsageStatistics,
                onCheckedChange = viewModel::setShareUsageStatistics,
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SettingsSectionHeader(stringResource(R.string.settings_section_about))

            val openWeatherUrl = stringResource(R.string.settings_openweather_url)
            SettingsLinkRow(
                label = stringResource(R.string.settings_attribution),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, openWeatherUrl.toUri()),
                    )
                },
            )

            // Open-Meteo's free API is CC BY 4.0, which requires this credit.
            val openMeteoUrl = stringResource(R.string.settings_openmeteo_url)
            SettingsLinkRow(
                label = stringResource(R.string.settings_attribution_openmeteo),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, openMeteoUrl.toUri()),
                    )
                },
            )

            val privacyUrl = stringResource(R.string.settings_privacy_policy_url)
            SettingsLinkRow(
                label = stringResource(R.string.settings_privacy_policy),
                onClick = {
                    context.startActivity(
                        Intent(Intent.ACTION_VIEW, privacyUrl.toUri()),
                    )
                },
            )

            // "Version 1.0", plus "· Debug build" in debug builds, so a screenshot says what
            // was tested. Debug's versionNameSuffix is left off: the line says it instead.
            Text(
                text = stringResource(
                    if (BuildConfig.DEBUG) R.string.settings_version_debug else R.string.settings_version,
                    BuildConfig.VERSION_NAME.removeSuffix("-debug"),
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/** Re-checked each time the screen comes back to the foreground (it may have changed in App Settings). */
@Composable
internal fun rememberGrantedOnResume(check: () -> Boolean): Boolean {
    var granted by remember { mutableStateOf(check()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) granted = check()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return granted
}

internal fun Context.openAppDetailsSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = "package:$packageName".toUri()
        },
    )
}

@Composable
internal fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun UnitOptionRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    // The whole row is one radio item, so TalkBack reads the label with the state. The button
    // itself takes no clicks, or it would be a second, unlabelled focus stop.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onClick, role = Role.RadioButton)
            // Same size and positions as before, when the button carried its own 48dp touch area.
            .heightIn(min = 56.dp)
            .padding(start = 22.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge)
    }
}

/** A row that opens another screen: its label, a short value ("2 on") and a chevron. */
@Composable
private fun SettingsNavRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 56.dp)
            .padding(start = 16.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
internal fun SettingsLinkRow(
    label: String,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .heightIn(min = 48.dp)
            .wrapContentHeight(Alignment.CenterVertically)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    )
}

@Composable
internal fun SettingsSwitchRow(
    label: String,
    summary: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = label, style = MaterialTheme.typography.bodyLarge)
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // The whole row is the toggle target; the Switch itself is display-only.
        Switch(checked = checked, onCheckedChange = null, modifier = Modifier.padding(start = 16.dp))
    }
}
