package com.fangjet.weather.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import com.fangjet.weather.BuildConfig
import com.fangjet.weather.R
import com.fangjet.weather.ui.pets.PetMood
import com.fangjet.weather.ui.pets.PetScene
import com.fangjet.weather.ui.pets.skyColor

/** Shown instead of the whole app when this version is below the minimum supported one. */
@Composable
fun UpdateRequiredScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    OnboardingLayout(
        modifier = modifier,
        actions = {
            Button(onClick = { context.openPlayStore() }, modifier = Modifier.fillMaxWidth()) {
                Text(text = stringResource(R.string.update_button))
            }
        },
    ) {
        PetScene(
            mood = PetMood.NIGHT,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PetMood.NIGHT.skyColor)
                .padding(horizontal = 12.dp, vertical = 16.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.update_required_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.update_required_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Above the weather when a newer version is recommended. "Not now" hides it for a week. */
@Composable
fun UpdateBanner(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(start = 14.dp, end = 8.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            Icons.Default.Download,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column {
            Text(stringResource(R.string.update_banner_title), style = MaterialTheme.typography.titleSmall)
            Text(
                stringResource(R.string.update_banner_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { context.openPlayStore() }) {
                    Text(stringResource(R.string.update_button), fontWeight = FontWeight.SemiBold)
                }
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.update_not_now), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

/** The app's Play Store page: the Play app if it's there, otherwise the web page. */
private fun Context.openPlayStore() {
    // Debug builds carry a ".debug" suffix that isn't on the Play Store.
    val packageName = BuildConfig.APPLICATION_ID.removeSuffix(".debug")
    try {
        startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=$packageName".toUri()))
    } catch (_: ActivityNotFoundException) {
        startActivity(Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=$packageName".toUri()))
    }
}
