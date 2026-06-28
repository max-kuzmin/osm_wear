package com.osm.wear.repositories

import com.osm.wear.models.MapRegion

interface IRegionCatalogRepository {
    val all: List<MapRegion>
}
