package com.osm.wear.services

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import kotlinx.coroutines.flow.StateFlow

interface IMarkerService {
    val currentMarker: StateFlow<GpxPoint?>
    val bookmarks: StateFlow<List<Bookmark>>
    
    fun setCurrentMarker(point: GpxPoint?)
    fun selectBookmark(bookmark: Bookmark)
    fun addBookmark(bookmark: Bookmark)
    fun removeBookmark(bookmark: Bookmark)
    fun saveBookmarkFromMap(pt: GpxPoint)
}
