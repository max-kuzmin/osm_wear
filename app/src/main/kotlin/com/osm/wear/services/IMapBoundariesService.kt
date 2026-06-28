package com.osm.wear.services

import com.osm.wear.models.GpxFile

interface IMapBoundariesService {
    fun isGpxCoveredByMap(gpx: GpxFile): Boolean
}
