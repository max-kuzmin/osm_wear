package com.osm.wear.view_models

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IBookmarkRepository
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.GeocodeResult
import com.osm.wear.repositories.ISettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MarkerViewModel @Inject constructor(
    private val settingsRepository: ISettingsRepository,
    private val geocodingRepository: IGeocodingRepository,
    private val bookmarkRepository: IBookmarkRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(MarkerUiState())
    val uiState: StateFlow<MarkerUiState> = _uiState.asStateFlow()

    /** Current GPS location, forwarded from MapViewModel via Navigation. */
    private val _currentLocation = MutableStateFlow<UserLocation?>(null)
    val currentLocation: StateFlow<UserLocation?> = _currentLocation.asStateFlow()

    val bookmarks: StateFlow<List<Bookmark>> = bookmarkRepository.bookmarks

    private val _searchResults = MutableStateFlow<List<GeocodeResult>>(emptyList())
    val searchResults: StateFlow<List<GeocodeResult>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    init {
        loadTappedPoint()
    }

    private fun loadTappedPoint() {
        val tappedPoint = settingsRepository.getTappedPoint()

        val matchingBookmark = if (tappedPoint != null) {
            bookmarkRepository.bookmarks.value.find {
                it.lat == tappedPoint.lat && it.lon == tappedPoint.lon
            }
        } else null

        _uiState.update {
            it.copy(
                tappedPoint = tappedPoint,
                tappedPointName = matchingBookmark?.name,
                tappedPointAddress = matchingBookmark?.address
            )
        }

        if (tappedPoint != null && matchingBookmark == null) {
            resolveAddressForPoint(tappedPoint)
        }
    }

    /** Called from Navigation when GPS location updates arrive. */
    fun updateCurrentLocation(loc: UserLocation) {
        _currentLocation.value = loc
    }

    fun onMapTapped(lat: Double, lon: Double) {
        val pt = GpxPoint(lat, lon)
        _uiState.update {
            it.copy(
                tappedPoint = pt,
                tappedPointName = null,
                tappedPointAddress = null,
                isResolvingAddress = true
            )
        }
        settingsRepository.setTappedPoint(pt)
        resolveAddressForPoint(pt)
    }

    fun clearTappedPoint() {
        _uiState.update { it.copy(tappedPoint = null) }
        settingsRepository.setTappedPoint(null)
    }

    fun selectBookmark(bookmark: Bookmark) {
        val pt = GpxPoint(bookmark.lat, bookmark.lon)
        _uiState.update {
            it.copy(
                tappedPoint = pt,
                tappedPointName = bookmark.name,
                tappedPointAddress = null,
                isResolvingAddress = true
            )
        }
        settingsRepository.setTappedPoint(pt)
        resolveAddressForPoint(pt, overrideName = bookmark.name)
    }

    fun selectAddress(name: String, address: String?, pt: GpxPoint) {
        _uiState.update {
            it.copy(
                tappedPoint = pt,
                tappedPointName = name,
                tappedPointAddress = address,
                isResolvingAddress = false
            )
        }
        settingsRepository.setTappedPoint(pt)
    }

    fun saveBookmarkFromMap(pt: GpxPoint, resolvedName: String?, resolvedAddress: String? = null) {
        viewModelScope.launch {
            var name = resolvedName
            var address = resolvedAddress

            if (name == null || address == null) {
                try {
                    val res = geocodingRepository.reverseGeocode(pt.lat, pt.lon)
                    if (res != null) {
                        if (name == null) name = res.name
                        if (address == null) address = res.address
                    }
                } catch (e: Exception) {
                    // Ignore
                }
            }

            val finalName = name ?: "Point (%.4f, %.4f)".format(pt.lat, pt.lon)

            bookmarkRepository.addBookmark(
                Bookmark(
                    name = finalName,
                    address = address,
                    lat = pt.lat,
                    lon = pt.lon
                )
            )

            _uiState.update {
                it.copy(
                    tappedPointName = finalName,
                    tappedPointAddress = address
                )
            }
        }
    }

    fun saveSearchBookmark(name: String, address: String?, lat: Double, lon: Double) {
        bookmarkRepository.addBookmark(
            Bookmark(
                name = name,
                address = address,
                lat = lat,
                lon = lon
            )
        )
    }

    fun deleteBookmark(bookmark: Bookmark) {
        bookmarkRepository.removeBookmark(bookmark)
    }

    fun searchAddresses(query: String) {
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        _isSearching.value = true
        viewModelScope.launch {
            try {
                val results = geocodingRepository.searchAddress(query)
                _searchResults.value = results
            } catch (e: Exception) {
                _searchResults.value = emptyList()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun clearSearchResults() {
        _searchResults.value = emptyList()
    }

    private fun resolveAddressForPoint(pt: GpxPoint, overrideName: String? = null) {
        viewModelScope.launch {
            try {
                _uiState.update { it.copy(isResolvingAddress = true) }
                val result = geocodingRepository.reverseGeocode(pt.lat, pt.lon)
                if (result != null) {
                    _uiState.update {
                        it.copy(
                            tappedPointName = overrideName ?: result.name,
                            tappedPointAddress = result.address,
                            isResolvingAddress = false
                        )
                    }
                } else {
                    _uiState.update { it.copy(isResolvingAddress = false) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isResolvingAddress = false) }
            }
        }
    }
}
