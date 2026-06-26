package com.osm.wear.services

import com.osm.wear.models.GpxPoint
import com.osm.wear.models.NavigationMode
import com.osm.wear.models.NavigationWaypoint

interface ITrackToMapMatcherService {
    fun matchTrackToMap(
        trackPoints: List<GpxPoint>,
        mode: NavigationMode
    ): List<NavigationWaypoint>

    fun clearCache()
}
