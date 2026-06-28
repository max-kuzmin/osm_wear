package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.GeocodeResult
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.IPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchAddressViewModel @Inject constructor(
    private val geocodingRepository: IGeocodingRepository,
    private val markersRepository: IMarkersRepository,
    private val preferencesRepository: IPreferencesRepository
) : ViewModel() {

    private val _searchResults = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodeResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun searchAddresses(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        viewModelScope.launch {
            try {
                val results = geocodingRepository.searchAddress(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    fun saveSearchBookmark(name: String, address: String?, lat: Double, lon: Double) {
        markersRepository.addBookmark(
            Bookmark(
                name = name,
                address = address,
                lat = lat,
                lon = lon
            )
        )
    }

    fun selectAddress(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        markersRepository.setCurrentMarker(pt)
        preferencesRepository.setMapCenter(bookmark.lat, bookmark.lon)
        preferencesRepository.setMapFollowLocation(false)
    }
}
