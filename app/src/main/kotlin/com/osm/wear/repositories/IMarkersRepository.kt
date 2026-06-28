package com.osm.wear.repositories

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import kotlinx.coroutines.flow.StateFlow

interface IMarkersRepository {
    val bookmarks: StateFlow<List<Bookmark>>
    val currentMarker: StateFlow<GpxPoint?>
    fun addBookmark(bookmark: Bookmark)
    fun removeBookmark(bookmark: Bookmark)
    fun getCurrentMarker(): GpxPoint?
    fun setCurrentMarker(point: GpxPoint?)
}
