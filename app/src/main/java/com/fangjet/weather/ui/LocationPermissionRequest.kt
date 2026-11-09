package com.fangjet.weather.ui

import android.Manifest
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.fangjet.weather.model.LocationPermissionViewModel
import timber.log.Timber

private val LOCATION_PERMISSIONS = arrayOf(
    Manifest.permission.ACCESS_FINE_LOCATION,
    Manifest.permission.ACCESS_COARSE_LOCATION,
)

/**
 * Makes the system location request and hands its answer to [viewModel], which fetches the location
 * on a grant and remembers a refusal. When Android refuses without showing the dialog, it opens the
 * app's settings page, where the user can allow it, and tells [viewModel] when they're back.
 */
@Composable
internal fun rememberLocationPermissionRequest(viewModel: LocationPermissionViewModel): () -> Unit {
    val activity = LocalActivity.current
    val context = LocalContext.current
    var rationaleBefore by remember { mutableStateOf(false) }

    fun rationale() =
        activity != null &&
            LOCATION_PERMISSIONS.any { ActivityCompat.shouldShowRequestPermissionRationale(activity, it) }

    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions.values.any { it }
        Timber.d("permission result: granted=$granted")
        viewModel.onPermissionResult(granted, rationaleBefore, rationaleAfter = rationale())
    }
    LaunchedEffect(viewModel) {
        viewModel.appSettingsRequested.collect {
            Timber.d("refused without a dialog: opening the app's settings")
            context.openAppDetailsSettings()
            viewModel.appSettingsOpened()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> viewModel.refreshAccess()
                Lifecycle.Event.ON_STOP -> viewModel.leftApp()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return {
        rationaleBefore = rationale()
        viewModel.permissionRequestStarted()
        launcher.launch(LOCATION_PERMISSIONS)
    }
}
