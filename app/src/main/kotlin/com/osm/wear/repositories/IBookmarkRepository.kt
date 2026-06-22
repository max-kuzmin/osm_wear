package com.osm.wear.repositories

import com.osm.wear.models.Bookmark
import kotlinx.coroutines.flow.StateFlow

interface IBookmarkRepository {
    val bookmarks: StateFlow<List<Bookmark>>
    fun addBookmark(bookmark: Bookmark)
    fun removeBookmark(bookmark: Bookmark)
}
