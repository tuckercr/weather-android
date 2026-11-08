package com.fangjet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fangjet.weather.R
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.model.LocationFetchState
import com.fangjet.weather.model.LocationPermissionViewModel
import com.fangjet.weather.ui.theme.CatsDogsTheme
import timber.log.Timber

@Composable
fun OnboardingLocationScreen(
    onLocationResolved: (SavedLocation) -> Unit,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: LocationPermissionViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        Timber.d("screen shown")
    }

    LaunchedEffect(state) {
        Timber.d("state changed: $state")
        when (val current = state) {
            is LocationFetchState.Located -> {
                val location = current.location
                Timber.d("location resolved: lat=${location.latitude} lon=${location.longitude}")
                onLocationResolved(location)
            }
            // A "no" is an answer: move on to entering a city rather than asking again. This also
            // covers a tap after Android stops showing the dialog, which reports denied at once.
            LocationFetchState.PermissionDenied -> onSkip()
            else -> Unit
        }
    }

    val requestPermission = rememberLocationPermissionRequest(viewModel)

    OnboardingLayout(
        modifier = modifier,
        actions = {
            if (state != LocationFetchState.Locating) {
                Button(
                    onClick = { if (viewModel.useMyLocation()) requestPermission() },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.location_allow_button))
                }
                OutlinedButton(
                    onClick = {
                        Timber.d("user tapped Skip")
                        onSkip()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.location_skip_button))
                }
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (state == LocationFetchState.Locating) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.location_onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (state) {
                LocationFetchState.Failed -> stringResource(R.string.location_fetch_failed)
                else -> stringResource(R.string.location_onboarding_body)
            },
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingLocationScreenPreview() {
    CatsDogsTheme {
        OnboardingLocationScreen(onLocationResolved = {}, onSkip = {})
    }
}
