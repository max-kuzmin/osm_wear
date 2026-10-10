package com.osm.wear.view_models

import com.osm.wear.models.RouteFile

data class TracksUiState(
    val routes: List<RouteFile> = emptyList(),
    val activeRoute: RouteFile? = null,
    val isSaving: Boolean = false,
    val isImporting: Boolean = false
)
