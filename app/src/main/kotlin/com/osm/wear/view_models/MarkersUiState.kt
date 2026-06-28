package com.osm.wear.view_models

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.UserLocation

data class MarkersUiState(
    val currentLocation: UserLocation? = null,
    val currentMarker: GpxPoint? = null,
    val bookmarks: List<Bookmark> = emptyList(),
    val bookmarkDistances: List<Pair<Bookmark, Double?>> = emptyList()
)
