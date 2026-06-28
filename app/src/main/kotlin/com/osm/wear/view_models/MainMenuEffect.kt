package com.osm.wear.view_models

sealed class MainMenuEffect {
    data class ShowToast(val message: String) : MainMenuEffect()
    data object ShowMap : MainMenuEffect()
}
