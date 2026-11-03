package com.fangjet.weather.ui

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fangjet.weather.R
import com.fangjet.weather.ui.theme.CatsDogsTheme
import timber.log.Timber

@Composable
fun OnboardingNotificationScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
    // The answer to the dog question just before: with a dog, the walk-time nudge is mentioned too.
    hasDog: Boolean = true,
    // Remote Config: a kind switched off for everyone isn't mentioned.
    walkTimeEnabled: Boolean = true,
    rainEnabled: Boolean = true,
) {
    LaunchedEffect(Unit) {
        Timber.d("screen shown")
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        Timber.d("permission result: granted=$granted")
        onDone()
    }

    OnboardingLayout(
        modifier = modifier,
        actions = {
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        Timber.d("launching permission dialog")
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        Timber.d("pre-Tiramisu device, skipping permission request")
                        onDone()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.notif_allow_button))
            }
            OutlinedButton(
                onClick = {
                    Timber.d("user tapped Not now")
                    onDone()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.notif_not_now_button))
            }
        },
    ) {
        Box(
            modifier = Modifier
                .size(112.dp)
                .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.Notifications,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            text = stringResource(R.string.notif_onboarding_title),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(
                if (hasDog && walkTimeEnabled) R.string.notif_onboarding_body_dog else R.string.notif_onboarding_body,
            ) + if (rainEnabled) "\n\n" + stringResource(R.string.notif_onboarding_rain_hint) else "",
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingNotificationScreenPreview() {
    CatsDogsTheme {
        OnboardingNotificationScreen(onDone = {})
    }
}
