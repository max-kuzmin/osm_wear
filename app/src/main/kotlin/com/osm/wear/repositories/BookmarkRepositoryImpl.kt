package com.osm.wear.repositories

import android.content.SharedPreferences
import com.osm.wear.models.Bookmark
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class BookmarkRepositoryImpl(
    private val prefs: SharedPreferences
) : IBookmarkRepository {

    private val _bookmarks = MutableStateFlow<List<Bookmark>>(emptyList())
    override val bookmarks: StateFlow<List<Bookmark>> = _bookmarks.asStateFlow()

    init {
        loadBookmarks()
    }

    private fun loadBookmarks() {
        val jsonStr = prefs.getString("bookmarks_list", "[]") ?: "[]"
        try {
            val arr = JSONArray(jsonStr)
            val list = mutableListOf<Bookmark>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Bookmark(
                        name = obj.getString("name"),
                        lat = obj.getDouble("lat"),
                        lon = obj.getDouble("lon"),
                        address = obj.optString("address", "").takeIf { it.isNotEmpty() },
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis())
                    )
                )
            }
            _bookmarks.value = list
        } catch (e: Exception) {
            _bookmarks.value = emptyList()
        }
    }

    private fun saveBookmarks(list: List<Bookmark>) {
        try {
            val arr = JSONArray()
            for (b in list) {
                val obj = JSONObject().apply {
                    put("name", b.name)
                    put("lat", b.lat)
                    put("lon", b.lon)
                    if (b.address != null) {
                        put("address", b.address)
                    }
                    put("timestamp", b.timestamp)
                }
                arr.put(obj)
            }
            prefs.edit().putString("bookmarks_list", arr.toString()).apply()
        } catch (e: Exception) {
            // Ignore error silently
        }
    }

    override fun addBookmark(bookmark: Bookmark) {
        val current = _bookmarks.value.toMutableList()
        // Remove duplicates by coordinates & name
        current.removeAll { it.lat == bookmark.lat && it.lon == bookmark.lon && it.name == bookmark.name }
        current.add(0, bookmark)
        _bookmarks.value = current
        saveBookmarks(current)
    }

    override fun removeBookmark(bookmark: Bookmark) {
        val current = _bookmarks.value.toMutableList()
        current.remove(bookmark)
        _bookmarks.value = current
        saveBookmarks(current)
    }
}
