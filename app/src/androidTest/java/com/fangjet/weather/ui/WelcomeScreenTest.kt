package com.fangjet.weather.ui

import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.Density
import com.fangjet.weather.R
import com.fangjet.weather.ui.theme.CatsDogsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class WelcomeScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun showsWelcomeCopyAndGetStartedButton() {
        composeRule.setContent {
            CatsDogsTheme { WelcomeScreen(onGetStarted = {}) }
        }

        composeRule.onNodeWithText(string(R.string.welcome_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.welcome_sunny_body)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.welcome_misty_body)).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.get_started)).assertIsDisplayed()
    }

    @Test
    fun getStartedStaysOnScreenAtTheLargestFontSize() {
        composeRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale = 2f)) {
                CatsDogsTheme { WelcomeScreen(onGetStarted = {}) }
            }
        }

        // The text runs past the bottom of the screen; the button stays pinned without scrolling.
        composeRule.onNodeWithText(string(R.string.get_started)).assertIsDisplayed()
    }

    @Test
    fun tappingGetStartedInvokesCallback() {
        var started = 0
        composeRule.setContent {
            CatsDogsTheme { WelcomeScreen(onGetStarted = { started++ }) }
        }

        composeRule.onNodeWithText(string(R.string.get_started)).performClick()

        assertEquals(1, started)
    }
}
