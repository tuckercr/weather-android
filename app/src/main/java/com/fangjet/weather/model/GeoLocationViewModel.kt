package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fangjet.weather.data.GeocodingRepository
import com.fangjet.weather.domain.CitySuggestion
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class GeoLocationViewModel @Inject constructor(
    private val geocodingRepository: GeocodingRepository,
) : ViewModel() {

    private val _cityInput = MutableStateFlow("")
    val cityInput: StateFlow<String> = _cityInput.asStateFlow()

    private val _citySuggestions = MutableStateFlow<List<CitySuggestion>>(emptyList())
    val citySuggestions: StateFlow<List<CitySuggestion>> = _citySuggestions.asStateFlow()

    private val _citySuggestLoading = MutableStateFlow(false)
    val citySuggestLoading: StateFlow<Boolean> = _citySuggestLoading.asStateFlow()

    /**
     * The last search failed (offline, say), as opposed to finding nothing, so Add location can
     * save the typed name instead. Cleared when the text changes.
     */
    private val _searchFailed = MutableStateFlow(false)
    val searchFailed: StateFlow<Boolean> = _searchFailed.asStateFlow()

    private var suggestJob: Job? = null

    fun onCityInputChange(value: String) {
        _cityInput.value = value
        _searchFailed.value = false
        suggestJob?.cancel()
        val trimmed = value.trim()
        if (trimmed.length < MIN_SEARCH_LENGTH) {
            _citySuggestions.value = emptyList()
            _citySuggestLoading.value = false
            return
        }
        suggestJob = viewModelScope.launch {
            _citySuggestLoading.value = true
            delay(280)
            if (_cityInput.value.trim() != trimmed) {
                _citySuggestLoading.value = false
                return@launch
            }
            val result = geocodingRepository.searchCities(trimmed)
            if (_cityInput.value.trim() != trimmed) {
                _citySuggestLoading.value = false
                return@launch
            }
            _citySuggestLoading.value = false
            _citySuggestions.value = result.getOrElse { emptyList() }
            _searchFailed.value = result.isFailure
        }
    }
}
