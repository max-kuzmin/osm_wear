package com.osm.wear.view_models

import com.osm.wear.repositories.GeocodeResult

data class SearchUiState(
    val searchResults: List<GeocodeResult> = emptyList(),
    val isSearching: Boolean = false
)
