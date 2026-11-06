package com.fangjet.weather.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import com.fangjet.weather.domain.CitySuggestion
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.ui.theme.CatsDogsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class AddLocationScreenTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<ComponentActivity>()

    private val malmo = CitySuggestion("Malmö, Skåne County, SE", 55.61, 13.0, population = 362_133)
    private val malmoNebraska = CitySuggestion("Malmo, Nebraska, US", 41.27, -96.72, population = 118)
    private val nashik = CitySuggestion("Nashik, Maharashtra, IN", 19.99, 73.79, population = 1_486_053)
    private val nas = CitySuggestion("Nås, Dalarna County, SE", 60.45, 14.48, population = 205)
    private val london = CitySuggestion("London, England, GB", 51.51, -0.13)

    private var added: CitySuggestion? = null
    private var wentTo: Int? = null

    private fun setScreen(
        query: String,
        suggestions: List<CitySuggestion>,
        saved: List<SavedLocation> = emptyList(),
    ) {
        composeRule.setContent {
            CatsDogsTheme {
                AddLocationScreen(
                    query = query,
                    suggestions = suggestions,
                    searching = false,
                    searchFailed = false,
                    savedLocations = saved,
                    onQueryChange = {},
                    onAdd = { added = it },
                    onAddByName = {},
                    onGoTo = { wentTo = it },
                    onClose = {},
                )
            }
        }
    }

    @Test
    fun theButtonNamesWhatItAddsAndTheSearchKeyDoesTheSame() {
        setScreen("Malmo", listOf(malmo, malmoNebraska))

        composeRule.onNodeWithText("Add Malmö, SE").assertIsDisplayed()
        composeRule.onNode(hasSetTextAction()).performImeAction()
        assertEquals(malmo, added)
    }

    @Test
    fun tappingARowAddsThatRow() {
        setScreen("Malmo", listOf(malmo, malmoNebraska))

        composeRule.onNodeWithText("Nebraska, US").performClick()
        assertEquals(malmoNebraska, added)
    }

    @Test
    fun aMatchFarSmallerThanTheRowsAboveAsksForAPick() {
        setScreen("Nas", listOf(nashik, nas))

        composeRule.onNodeWithText("Pick a town or city from the list").assertIsDisplayed()
        composeRule.onNodeWithText("Add Nås, SE").assertDoesNotExist()
    }

    @Test
    fun aSavedPlaceIsMarkedAndGoneTo() {
        setScreen(
            "London",
            listOf(london),
            saved = listOf(SavedLocation("Tokyo, JP", 35.68, 139.69), SavedLocation("London, England, GB", 51.51, -0.13)),
        )

        composeRule.onNodeWithText("Saved").assertIsDisplayed()
        composeRule.onNodeWithText("Go to London, GB").performClick()
        assertEquals(1, wentTo)
        assertEquals(null, added)
    }

    @Test
    fun theScreenClosesWithItsX() {
        var closed = false
        composeRule.setContent {
            CatsDogsTheme {
                AddLocationScreen(
                    query = "",
                    suggestions = emptyList(),
                    searching = false,
                    searchFailed = false,
                    savedLocations = emptyList(),
                    onQueryChange = {},
                    onAdd = {},
                    onAddByName = {},
                    onGoTo = {},
                    onClose = { closed = true },
                )
            }
        }

        composeRule.onNodeWithContentDescription("Close").performClick()
        assertEquals(true, closed)
    }
}
