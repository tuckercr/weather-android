package com.fangjet.weather.model

import androidx.lifecycle.ViewModel
import com.fangjet.weather.data.CityRepository
import com.fangjet.weather.domain.SavedLocation
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

/**
 * Manage Locations and Add location: the saved cities, and adding, removing, reordering and picking
 * them. The cities themselves are kept by [CityRepository], which the weather screen reads too.
 */
@HiltViewModel
class CityListViewModel
    @Inject
    constructor(
        private val cities: CityRepository,
    ) : ViewModel() {
        val locations: StateFlow<List<SavedLocation>> = cities.locations

        fun addLocation(location: SavedLocation) = cities.addLocation(location)

        fun removeLocation(index: Int) = cities.removeLocation(index)

        fun setActiveIndex(index: Int) = cities.setActiveIndex(index)

        fun reorderLocations(
            fromIndex: Int,
            toIndex: Int,
        ) = cities.reorderLocations(fromIndex, toIndex)
    }
