package com.osm.wear.view_models

sealed class TracksEffect {
    data class ShowToast(val message: String) : TracksEffect()
}
