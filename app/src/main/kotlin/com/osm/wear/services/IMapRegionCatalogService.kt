package com.osm.wear.services

import com.osm.wear.models.MapRegion

interface IMapRegionCatalogService {
    val all: List<MapRegion>
    val continents: List<String>
    fun findById(id: String): MapRegion?
}

