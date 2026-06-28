package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.IPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchAddressViewModel @Inject constructor(
    private val geocodingRepository: IGeocodingRepository,
    private val markersRepository: IMarkersRepository,
    private val preferencesRepository: IPreferencesRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.SearchAddresses -> searchAddresses(intent.query)
            is SearchIntent.ClearSearchResults -> clearSearchResults()
            is SearchIntent.SelectAddress -> selectAddress(intent.bookmark)
        }
    }

    private fun searchAddresses(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            try {
                val results = geocodingRepository.searchAddress(query)
                _uiState.update { it.copy(searchResults = results, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    private fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList()) }
    }

    private fun selectAddress(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        markersRepository.setCurrentMarker(pt)
        preferencesRepository.setMapCenter(bookmark.lat, bookmark.lon)
        preferencesRepository.setMapFollowLocation(false)
    }
}
