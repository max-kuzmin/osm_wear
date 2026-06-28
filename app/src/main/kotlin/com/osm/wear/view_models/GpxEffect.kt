package com.osm.wear.view_models

sealed class GpxEffect {
    data class ShowToast(val message: String) : GpxEffect()
}
