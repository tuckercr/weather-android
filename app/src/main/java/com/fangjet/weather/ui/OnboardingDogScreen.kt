package com.fangjet.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

/**
 * Onboarding step after the welcome screen. The answer sets the "Best walk time" switch in
 * Settings, so a home without a dog never sees the walk card.
 */
@Composable
fun OnboardingDogScreen(
    onAnswered: (hasDog: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingLayout(
        modifier = modifier,
        actions = {
            Button(
                onClick = { onAnswered(true) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dog_question_yes))
            }
            OutlinedButton(
                onClick = { onAnswered(false) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dog_question_no))
            }
        },
    ) {
        PetScene(
            mood = PetMood.CLOUDY,
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .background(PetMood.CLOUDY.skyColor, RoundedCornerShape(24.dp))
                .padding(horizontal = 12.dp, vertical = 16.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.dog_question_title),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.dog_question_body),
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun OnboardingDogScreenPreview() {
    CatsDogsTheme {
        OnboardingDogScreen(onAnswered = {})
    }
}
