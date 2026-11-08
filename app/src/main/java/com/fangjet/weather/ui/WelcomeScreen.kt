package com.fangjet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fangjet.weather.R
import com.fangjet.weather.domain.PetMood
import com.fangjet.weather.ui.pets.PetScene
import com.fangjet.weather.ui.pets.skyColor
import com.fangjet.weather.ui.theme.CatsDogsTheme

/** The first onboarding screen: introduces Sunny and Misty, who tell you the weather. */
@Composable
fun WelcomeScreen(
    onGetStarted: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingLayout(
        modifier = modifier,
        actions = {
            Button(
                onClick = onGetStarted,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(text = stringResource(R.string.get_started))
            }
        },
    ) {
        PetScene(
            mood = PetMood.SUNNY,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(24.dp))
                .background(PetMood.SUNNY.skyColor)
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.welcome_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Character(name = stringResource(R.string.welcome_sunny_name), body = stringResource(R.string.welcome_sunny_body))
        Spacer(modifier = Modifier.height(12.dp))
        Character(name = stringResource(R.string.welcome_misty_name), body = stringResource(R.string.welcome_misty_body))
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.welcome_together),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Character(
    name: String,
    body: String,
) {
    Column(
        modifier = Modifier.semantics(mergeDescendants = true) {},
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = name,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun WelcomeScreenPreview() {
    CatsDogsTheme {
        WelcomeScreen(onGetStarted = {})
    }
}
