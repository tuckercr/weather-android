package com.fangjet.weather.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.fangjet.weather.R
import com.fangjet.weather.ui.theme.CatsDogsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class OnboardingDogScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private fun string(id: Int) = composeRule.activity.getString(id)

    @Test
    fun asksAboutADogAndReportsTheAnswer() {
        val answers = mutableListOf<Boolean>()
        composeRule.setContent {
            CatsDogsTheme { OnboardingDogScreen(onAnswered = { answers += it }) }
        }

        composeRule.onNodeWithText(string(R.string.dog_question_title)).assertIsDisplayed()
        composeRule.onNodeWithText(string(R.string.dog_question_no)).performClick()
        composeRule.onNodeWithText(string(R.string.dog_question_yes)).performClick()

        assertEquals(listOf(false, true), answers)
    }
}
