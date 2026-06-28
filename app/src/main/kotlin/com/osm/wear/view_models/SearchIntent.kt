package com.osm.wear.view_models

import com.osm.wear.models.Bookmark

sealed class SearchIntent {
    data class SearchAddresses(val query: String) : SearchIntent()
    data object ClearSearchResults : SearchIntent()
    data class SelectAddress(val bookmark: Bookmark) : SearchIntent()
}
