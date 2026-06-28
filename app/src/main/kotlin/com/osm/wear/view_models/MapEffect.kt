package com.osm.wear.view_models

import org.mapsforge.core.model.LatLong

sealed class MapEffect {
    data class CenterMap(val latLong: LatLong) : MapEffect()
    data class ShowToast(val message: String) : MapEffect()
}
