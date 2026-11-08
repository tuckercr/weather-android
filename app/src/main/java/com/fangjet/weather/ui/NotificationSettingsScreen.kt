package com.fangjet.weather.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fangjet.weather.R
import com.fangjet.weather.model.FollowMyLocationRequest
import com.fangjet.weather.model.SettingsViewModel
import com.fangjet.weather.worker.NotificationKind

/**
 * A switch for each notification, under Settings. Walk time only shows with a dog ("I have a dog"
 * in Settings), any kind is hidden while Remote Config has it off, and hidden switches keep their
 * values. Follow my location shows when there's a My Location.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsRoute(onNavigateBack: () -> Unit) {
    val viewModel = hiltViewModel<SettingsViewModel>()
    val hasDog by viewModel.showWalkCard.collectAsStateWithLifecycle()
    val notifyMorningBriefing by viewModel.notifyMorningBriefing.collectAsStateWithLifecycle()
    val notifyWalkTime by viewModel.notifyWalkTime.collectAsStateWithLifecycle()
    val notifyRain by viewModel.notifyRain.collectAsStateWithLifecycle()
    val hasMyLocation by viewModel.hasMyLocation.collectAsStateWithLifecycle()
    val permissions by viewModel.permissionState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    OnResume(viewModel::refreshPermissions)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_notifications)) },
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
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()),
        ) {
            if (!permissions.notifications) {
                SettingsLinkRow(
                    label = stringResource(R.string.settings_notifications_disabled),
                    onClick = context::openAppDetailsSettings,
                )
                return@Column
            }
            // Each shows unless Remote Config has switched it off for everyone; Walk time also needs a dog.
            if (viewModel.remotelyEnabled(NotificationKind.MORNING_BRIEFING)) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_notify_briefing),
                    summary = stringResource(
                        if (hasDog) R.string.settings_notify_briefing_summary_dog else R.string.settings_notify_briefing_summary,
                    ),
                    checked = notifyMorningBriefing,
                    onCheckedChange = viewModel::setNotifyMorningBriefing,
                )
            }
            if (hasDog && viewModel.remotelyEnabled(NotificationKind.WALK_TIME)) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_notify_walk),
                    summary = stringResource(R.string.settings_notify_walk_summary),
                    checked = notifyWalkTime,
                    onCheckedChange = viewModel::setNotifyWalkTime,
                )
            }
            if (viewModel.remotelyEnabled(NotificationKind.RAIN)) {
                SettingsSwitchRow(
                    label = stringResource(R.string.settings_notify_rain),
                    summary = stringResource(R.string.settings_notify_rain_summary),
                    checked = notifyRain,
                    onCheckedChange = viewModel::setNotifyRain,
                )
            }
            if (hasMyLocation) {
                FollowMyLocationRow(viewModel)
            }
        }
    }
}

/**
 * Follow my location. It shows on only while it's set and "Allow all the time" is granted. Turning
 * it on without that explains first, as Play requires, then asks: Android 11 and later open the app's location
 * page in system settings rather than a dialog. After two refusals Android stops opening that page,
 * so the app's settings open instead. Same as iOS's `FollowMyLocationToggle`.
 */
@Composable
private fun FollowMyLocationRow(viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val on by viewModel.followsMyLocation.collectAsStateWithLifecycle()
    val explaining by viewModel.explainingFollowMyLocation.collectAsStateWithLifecycle()

    val backgroundRequest = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
        viewModel::backgroundLocationAnswered,
    )
    // Android only offers "Allow all the time" once "While using the app" is granted.
    val foregroundRequest = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result.values.any { it }) backgroundRequest.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    SettingsSwitchRow(
        label = stringResource(R.string.settings_follow_location),
        summary = stringResource(R.string.settings_follow_location_summary),
        checked = on,
        onCheckedChange = viewModel::setFollowMyLocation,
    )
    if (explaining) {
        AlertDialog(
            onDismissRequest = viewModel::dismissFollowMyLocationExplanation,
            title = { Text(stringResource(R.string.follow_location_title)) },
            text = {
                Text(stringResource(R.string.follow_location_body) + "\n\n" + stringResource(R.string.follow_location_android_hint))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        when (viewModel.confirmFollowMyLocation()) {
                            FollowMyLocationRequest.NONE -> Unit
                            FollowMyLocationRequest.FOREGROUND_LOCATION -> foregroundRequest.launch(
                                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
                            )
                            FollowMyLocationRequest.APP_SETTINGS -> context.openAppDetailsSettings()
                            FollowMyLocationRequest.BACKGROUND_LOCATION -> backgroundRequest.launch(
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                            )
                        }
                    },
                ) { Text(stringResource(R.string.follow_location_continue)) }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissFollowMyLocationExplanation) {
                    Text(stringResource(R.string.follow_location_not_now))
                }
            },
        )
    }
}
