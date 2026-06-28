package com.osm.wear.view_models

import com.osm.wear.models.Bookmark
import com.osm.wear.models.GpxPoint

sealed class MarkersIntent {
    data class SelectBookmark(val bookmark: Bookmark) : MarkersIntent()
    data class SaveBookmark(val point: GpxPoint) : MarkersIntent()
    data class DeleteBookmark(val bookmark: Bookmark) : MarkersIntent()
    data class buildRouteTo(val target: GpxPoint) : MarkersIntent()
}
