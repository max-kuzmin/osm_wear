package com.osm.wear.services

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IGeocodingRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MarkerServiceImpl @Inject constructor(
    private val markersRepository: IMarkersRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val geocodingRepository: IGeocodingRepository
) : IMarkerService {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private val _currentMarker = MutableStateFlow<GpxPoint?>(null)
    override val currentMarker: StateFlow<GpxPoint?> = _currentMarker.asStateFlow()

    override val bookmarks: StateFlow<List<Bookmark>> = markersRepository.bookmarks

    init {
        _currentMarker.value = markersRepository.getCurrentMarker()
    }

    override fun setCurrentMarker(point: GpxPoint?) {
        _currentMarker.value = point
        markersRepository.setCurrentMarker(point)
    }

    override fun selectBookmark(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        setCurrentMarker(pt)
        preferencesRepository.setMapCenter(bookmark.lat, bookmark.lon)
        preferencesRepository.setMapFollowLocation(false)
    }

    override fun addBookmark(bookmark: Bookmark) {
        markersRepository.addBookmark(bookmark)
    }

    override fun removeBookmark(bookmark: Bookmark) {
        markersRepository.removeBookmark(bookmark)
    }

    override fun saveBookmarkFromMap(pt: GpxPoint) {
        scope.launch {
            var name: String? = null
            var address: String? = null
            try {
                val res = geocodingRepository.reverseGeocode(pt.lat, pt.lon)
                if (res != null) {
                    name = res.name
                    address = res.address
                }
            } catch (e: Exception) {
                // Ignore
            }
            val finalName = name ?: "Point (%.4f, %.4f)".format(pt.lat, pt.lon)
            markersRepository.addBookmark(
                Bookmark(
                    name = finalName,
                    address = address,
                    lat = pt.lat,
                    lon = pt.lon
                )
            )
        }
    }
}
