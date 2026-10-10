package com.osm.wear.view_models

import android.net.Uri
import com.osm.wear.models.NavigationRoute

sealed class TracksIntent {
    data object ScanFolders : TracksIntent()
    data class SetActive(val routeId: String) : TracksIntent()
    data class SaveCurrent(val name: String, val route: NavigationRoute) : TracksIntent()
    data class ImportTrack(val uri: Uri) : TracksIntent()
    data class DeleteTrack(val routeId: String) : TracksIntent()
}
