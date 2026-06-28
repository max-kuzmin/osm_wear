package com.osm.wear.repositories

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import kotlinx.coroutines.flow.StateFlow

interface IMarkersRepository {
    val bookmarks: StateFlow<List<Bookmark>>
    val tappedPoint: StateFlow<GpxPoint?>
    fun addBookmark(bookmark: Bookmark)
    fun removeBookmark(bookmark: Bookmark)
    fun setTappedPoint(point: GpxPoint?)
}
