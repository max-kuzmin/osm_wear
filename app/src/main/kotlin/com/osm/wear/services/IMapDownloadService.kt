package com.osm.wear.services

import com.osm.wear.models.DownloadState
import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.MapRegion
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface IMapDownloadService {
    val downloadState: StateFlow<DownloadState>
    fun getLocalFile(region: MapRegion): File
    fun getDownloadedRegions(catalog: List<MapRegion>, activeId: String?): List<DownloadedRegion>
    suspend fun downloadRegion(region: MapRegion)
    fun cancelDownload()
    suspend fun deleteRegion(region: MapRegion)
}
