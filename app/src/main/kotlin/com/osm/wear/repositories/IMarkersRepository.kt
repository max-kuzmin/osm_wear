package com.osm.wear.repositories

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GeoPoint
import kotlinx.coroutines.flow.StateFlow

interface IMarkersRepository {
    val bookmarks: StateFlow<List<Bookmark>>
    val currentMarker: StateFlow<GeoPoint?>
    fun addBookmark(bookmark: Bookmark)
    fun removeBookmark(bookmark: Bookmark)
    fun getCurrentMarker(): GeoPoint?
    fun setCurrentMarker(point: GeoPoint?)
}
