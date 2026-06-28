package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IMarkersRepository
import com.osm.wear.repositories.IPreferencesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.osm.wear.repositories.ICursorRepository
import com.osm.wear.repositories.IRegionRepository
import org.mapsforge.map.reader.MapFile
import kotlin.math.*

@HiltViewModel
class SearchAddressViewModel @Inject constructor(
    private val geocodingRepository: IGeocodingRepository,
    private val markersRepository: IMarkersRepository,
    private val preferencesRepository: IPreferencesRepository,
    private val cursorRepository: ICursorRepository,
    private val regionRepository: IRegionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SearchUiState())
    val uiState: StateFlow<SearchUiState> = _uiState.asStateFlow()

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.SearchAddresses -> searchAddresses(intent.query)
            is SearchIntent.ClearSearchResults -> clearSearchResults()
            is SearchIntent.SelectAddress -> selectAddress(intent.bookmark)
        }
    }

    private fun searchAddresses(query: String) {
        if (query.isBlank()) {
            _uiState.update { it.copy(searchResults = emptyList()) }
            return
        }
        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            try {
                val bbox = getOverallBoundingBox()
                val results = geocodingRepository.searchAddress(query, bbox)
                val loc = cursorRepository.getLastKnownLocation()
                
                val sorted = if (loc != null) {
                    results.sortedBy { res ->
                        fastDistanceMeters(loc.latitude, loc.longitude, res.lat, res.lon)
                    }
                } else {
                    results
                }
                
                _uiState.update { it.copy(searchResults = sorted, isSearching = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(searchResults = emptyList(), isSearching = false) }
            }
        }
    }

    private fun getOverallBoundingBox(): org.mapsforge.core.model.BoundingBox? {
        val downloaded = regionRepository.getDownloadedRegions(null)
        if (downloaded.isEmpty()) return null
        
        var minLat = Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        var hasValidBbox = false
        
        for (region in downloaded) {
            try {
                val mapFile = MapFile(java.io.File(region.filePath))
                val bbox = mapFile.boundingBox()
                mapFile.close()
                if (bbox != null) {
                    if (bbox.minLatitude < minLat) minLat = bbox.minLatitude
                    if (bbox.minLongitude < minLon) minLon = bbox.minLongitude
                    if (bbox.maxLatitude > maxLat) maxLat = bbox.maxLatitude
                    if (bbox.maxLongitude > maxLon) maxLon = bbox.maxLongitude
                    hasValidBbox = true
                }
            } catch (e: Exception) {
                // Ignore errors reading individual files
            }
        }
        if (!hasValidBbox) return null
        return org.mapsforge.core.model.BoundingBox(minLat, minLon, maxLat, maxLon)
    }

    private fun fastDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val latMid = Math.toRadians((lat1 + lat2) / 2.0)
        val dx = Math.toRadians(lon2 - lon1) * cos(latMid) * 6_371_000.0
        val dy = Math.toRadians(lat2 - lat1) * 6_371_000.0
        return sqrt(dx * dx + dy * dy)
    }

    private fun clearSearchResults() {
        _uiState.update { it.copy(searchResults = emptyList()) }
    }

    private fun selectAddress(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        markersRepository.setCurrentMarker(pt)
        preferencesRepository.setMapCenter(bookmark.lat, bookmark.lon)
        preferencesRepository.setMapFollowLocation(false)
    }
}
