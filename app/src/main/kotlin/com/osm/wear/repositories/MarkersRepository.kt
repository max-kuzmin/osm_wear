package com.osm.wear.repositories

import android.content.SharedPreferences
import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

class MarkersRepository(
    private val prefs: SharedPreferences
) : IMarkersRepository {

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

    override fun getCurrentMarker(): GpxPoint? {
        val hasMarker = prefs.getBoolean("has_current_marker", false)
        return if (hasMarker) {
            val tLat = prefs.getFloat("current_marker_lat", 0f).toDouble()
            val tLon = prefs.getFloat("current_marker_lon", 0f).toDouble()
            GpxPoint(tLat, tLon)
        } else {
            null
        }
    }

    override fun setCurrentMarker(point: GpxPoint?) {
        val editor = prefs.edit()
        if (point != null) {
            editor.putBoolean("has_current_marker", true)
                .putFloat("current_marker_lat", point.lat.toFloat())
                .putFloat("current_marker_lon", point.lon.toFloat())
        } else {
            editor.putBoolean("has_current_marker", false)
        }
        editor.apply()
    }
}
