package com.fangjet.weather.model

import com.fangjet.weather.domain.CitySuggestion
import com.fangjet.weather.domain.SavedLocation
import com.fangjet.weather.domain.distanceMeters
import java.text.Normalizer
import java.util.Locale

/**
 * What the Add location screen's button does, from what's typed and what the search found. The
 * rules are SEARCH.md's "Add with nothing picked" and "Already saved". Same rules as iOS's
 * `Utilities/AddLocationRules.swift`.
 */
sealed interface AddLocationAction {
    /** Nothing typed yet, or the first search still running: no button. */
    data object None : AddLocationAction

    /** Adds [suggestion], the row that matches what was typed: "Add Malmö, SE". */
    data class Add(
        val suggestion: CitySuggestion,
    ) : AddLocationAction

    /** The matching row is already saved, at [index]: "Go to London, GB". */
    data class GoTo(
        val suggestion: CitySuggestion,
        val index: Int,
    ) : AddLocationAction

    /** No list because the search failed (offline, say): saves the typed name, looked up later. */
    data class AddByName(
        val name: String,
    ) : AddLocationAction

    /** A list with no row Add can take: "Pick a town or city from the list". */
    data object PickFromList : AddLocationAction

    /** The search found nothing. */
    data object NotFound : AddLocationAction
}

/** The shortest text that's searched, as in [GeoLocationViewModel]. */
const val MIN_SEARCH_LENGTH = 2

/**
 * Add with nothing tapped can't take a row past one this many times its population. SEARCH.md's
 * `city_search_size_ratio` default; it becomes that Remote Config key with the new search.
 */
const val ADD_SIZE_RATIO = 20

/** Two saved places closer than this are the same place (SEARCH.md, "Already saved"). */
const val SAME_PLACE_METERS = 2_000.0

fun addLocationAction(
    typed: String,
    suggestions: List<CitySuggestion>,
    searching: Boolean,
    searchFailed: Boolean,
    saved: List<SavedLocation>,
    sizeRatio: Int = ADD_SIZE_RATIO,
): AddLocationAction {
    val query = typed.trim()
    if (query.length < MIN_SEARCH_LENGTH) return AddLocationAction.None
    if (suggestions.isEmpty()) {
        return when {
            searching -> AddLocationAction.None
            searchFailed -> AddLocationAction.AddByName(query)
            else -> AddLocationAction.NotFound
        }
    }
    val match = typedMatch(query, suggestions, sizeRatio) ?: return AddLocationAction.PickFromList
    val index = saved.savedIndexOf(match)
    return if (index >= 0) AddLocationAction.GoTo(match, index) else AddLocationAction.Add(match)
}

/**
 * The first row whose name is what was typed, ignoring case, accents and anything after a comma,
 * unless a row above it has at least [sizeRatio] times its population: "Nas" never adds Nås,
 * Sweden (205 people) from under Nashik. As with the size guard, a row with no population can't
 * be taken past one that has one, a row with no population blocks nothing, and 0 turns it off.
 */
fun typedMatch(
    typed: String,
    suggestions: List<CitySuggestion>,
    sizeRatio: Int = ADD_SIZE_RATIO,
): CitySuggestion? {
    val name = fold(typed.substringBefore(',').trim())
    if (name.isEmpty()) return null
    val index = suggestions.indexOfFirst { fold(it.name.trim()) == name }
    if (index < 0) return null
    val match = suggestions[index]
    return match.takeIf { suggestions.take(index).all { above -> canPass(match, above, sizeRatio) } }
}

private fun canPass(
    row: CitySuggestion,
    above: CitySuggestion,
    sizeRatio: Int,
): Boolean {
    if (sizeRatio <= 0) return true
    val abovePopulation = above.population ?: return true
    val population = row.population ?: return false
    return abovePopulation < sizeRatio.toLong() * population
}

/**
 * Where [suggestion] already is in the saved list, or -1: the same label, or within
 * [SAME_PLACE_METERS] (labels miss the same place spelt two ways). My Location is left out: it
 * moves, and saving the town you're in as its own location is reasonable.
 */
fun List<SavedLocation>.savedIndexOf(suggestion: CitySuggestion): Int =
    indexOfFirst { saved ->
        if (saved.isCurrentLocation) return@indexOfFirst false
        if (saved.label == suggestion.label) return@indexOfFirst true
        val lat = saved.latitude ?: return@indexOfFirst false
        val lon = saved.longitude ?: return@indexOfFirst false
        distanceMeters(lat, lon, suggestion.weatherLat, suggestion.weatherLon) < SAME_PLACE_METERS
    }

/**
 * How many characters at the start of [name] the typed text covers, accents ignored ("Nas" in
 * "Nåshult" is 3), for showing them in bold; 0 when the name doesn't start with it.
 */
fun matchedPrefixLength(
    name: String,
    typed: String,
): Int {
    val query = fold(typed.substringBefore(',').trim())
    if (query.isEmpty()) return 0
    var folded = ""
    name.forEachIndexed { index, char ->
        folded += fold(char.toString())
        if (!query.startsWith(folded) && !folded.startsWith(query)) return 0
        if (folded.length >= query.length) return if (folded.startsWith(query)) index + 1 else 0
    }
    return 0
}

/** Lower case without accents, so "Malmo" finds Malmö and nobody has to type them. */
fun fold(text: String): String =
    Normalizer
        .normalize(text, Normalizer.Form.NFD)
        .replace(COMBINING_MARKS, "")
        .lowercase(Locale.ROOT)

private val COMBINING_MARKS = Regex("\\p{M}+")
