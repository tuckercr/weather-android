package com.fangjet.weather.model

import com.fangjet.weather.domain.CitySuggestion
import com.fangjet.weather.domain.SavedLocation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AddLocationRulesTest {

    // Live Open-Meteo rows (11-05), in the order a phone outside the US sees them.
    private val malmo = CitySuggestion("Malmö, Skåne County, SE", 55.61, 13.0, population = 362_133)
    private val malmoNebraska = CitySuggestion("Malmo, Nebraska, US", 41.27, -96.72, population = 118)
    private val nashik = CitySuggestion("Nashik, Maharashtra, IN", 19.99, 73.79, population = 1_486_053)
    private val nashville = CitySuggestion("Nashville, Tennessee, US", 36.17, -86.78, population = 689_447)
    private val nas = CitySuggestion("Nås, Dalarna County, SE", 60.45, 14.48, population = 205)
    private val bathinda = CitySuggestion("Bathinda, Punjab, IN", 30.21, 74.95, population = 285_788)
    private val bath = CitySuggestion("Bath, England, GB", 51.38, -2.36, population = 101_557)
    private val bathMaine = CitySuggestion("Bath, Maine, US", 43.91, -69.82, population = 8_305)
    private val london = CitySuggestion("London, England, GB", 51.51, -0.13)
    private val londonOntario = CitySuggestion("London, Ontario, CA", 42.98, -81.25)

    private fun action(
        typed: String,
        suggestions: List<CitySuggestion>,
        saved: List<SavedLocation> = emptyList(),
        searching: Boolean = false,
        searchFailed: Boolean = false,
    ) = addLocationAction(typed, suggestions, searching, searchFailed, saved)

    @Test
    fun `typing without accents adds the place that has them`() {
        assertEquals(AddLocationAction.Add(malmo), action("Malmo", listOf(malmo, malmoNebraska)))
        assertEquals(AddLocationAction.Add(malmo), action("malmö ", listOf(malmo, malmoNebraska)))
    }

    @Test
    fun `a match far smaller than a row above it asks for a pick`() {
        // Nashik is over 7,000 times Nås, so "Nas" doesn't quietly add Sweden.
        assertEquals(AddLocationAction.PickFromList, action("Nas", listOf(nashik, nashville, nas)))
        // Bathinda is only 2.8 times Bath; Bath, Maine is 34 times smaller.
        assertEquals(AddLocationAction.Add(bath), action("Bath", listOf(bathinda, bath)))
        assertEquals(AddLocationAction.PickFromList, action("Bath", listOf(bathinda, bathMaine, bath)))
    }

    @Test
    fun `the size check follows the guard's rules for missing populations and 0`() {
        val noPopulation = CitySuggestion("Nash, Wales, GB", 51.6, -3.0)
        val village = CitySuggestion("Nashville, Georgia, US", 31.2, -83.25, population = 4_900)
        // A row with no population can't pass one that has one...
        assertNull(typedMatch("Nash", listOf(nashik, noPopulation)))
        // ...and a row with no population blocks nothing.
        assertEquals(village, typedMatch("Nashville", listOf(london, village)))
        // 0 turns the check off.
        assertEquals(nas, typedMatch("Nas", listOf(nashik, nas), sizeRatio = 0))
    }

    @Test
    fun `no row named what was typed asks for a pick, and text after a comma is ignored`() {
        assertEquals(AddLocationAction.PickFromList, action("Nash", listOf(nashik, nashville)))
        assertEquals(AddLocationAction.Add(london), action("London, Ontario", listOf(london, londonOntario)))
    }

    @Test
    fun `a saved place is gone to, not added again`() {
        val saved = listOf(
            SavedLocation("My Location", 51.5, -0.12, isCurrentLocation = true),
            SavedLocation("Tokyo, JP", 35.68, 139.69),
            SavedLocation("London, England, GB", 51.51, -0.13),
        )
        assertEquals(AddLocationAction.GoTo(london, 2), action("London", listOf(london), saved))
    }

    @Test
    fun `saved means the same label or within 2 km, but never My Location`() {
        val myLocation = SavedLocation("My Location", 55.61, 13.0, isCurrentLocation = true)
        val spelledOtherwise = SavedLocation("Malmo, Skane, SE", 55.605, 13.003)
        val nameOnly = SavedLocation("Malmö, Skåne County, SE", null, null)
        val farAway = SavedLocation("Malmö, Kalmar, SE", 57.25, 16.47)

        assertEquals(-1, listOf(myLocation).savedIndexOf(malmo))
        assertEquals(0, listOf(spelledOtherwise).savedIndexOf(malmo))
        assertEquals(0, listOf(nameOnly).savedIndexOf(malmo))
        assertEquals(-1, listOf(farAway).savedIndexOf(malmo))
    }

    @Test
    fun `no list says why`() {
        assertEquals(AddLocationAction.None, action("M", emptyList()))
        assertEquals(AddLocationAction.None, action("Malmo", emptyList(), searching = true))
        assertEquals(AddLocationAction.AddByName("Malmo"), action(" Malmo ", emptyList(), searchFailed = true))
        assertEquals(AddLocationAction.NotFound, action("Nashvile", emptyList()))
    }

    @Test
    fun `a list still on screen while the next search runs keeps its button`() {
        assertEquals(AddLocationAction.Add(malmo), action("Malmo", listOf(malmo), searching = true))
    }

    @Test
    fun `the typed letters are found in the name for bold, accents ignored`() {
        assertEquals(3, matchedPrefixLength("Nashik", "Nas"))
        assertEquals(3, matchedPrefixLength("Nåshult", "nas"))
        assertEquals(5, matchedPrefixLength("Malmö", "Malmo"))
        assertEquals(5, matchedPrefixLength("New York", "New Y"))
        assertEquals(0, matchedPrefixLength("Ahvāz", "Nas"))
        assertEquals(0, matchedPrefixLength("Nas", "Nashville"))
    }
}
