package com.osm.wear.view_models

sealed class MarkersEffect {
    data object ShowMap : MarkersEffect()
    data class ShowToast(val message: String) : MarkersEffect()
}
