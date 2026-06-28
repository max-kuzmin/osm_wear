package com.osm.wear.repositories

import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.DownloadState
import com.osm.wear.models.MapRegion
import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface IRegionRepository {
    val activeRegionId: StateFlow<String?>
    val activeMapFile: StateFlow<File?>
    fun getActiveMapFile(): File?
    fun getActiveRegionId(): String?
    fun setActiveRegionId(id: String?)
    
    val all: List<MapRegion>
    fun getLocalFile(region: MapRegion): File
    fun getDownloadedRegions(activeId: String?): List<DownloadedRegion>
    fun deleteRegion(region: MapRegion): Boolean

    val downloadState: StateFlow<DownloadState>
    suspend fun downloadRegion(region: MapRegion)
    fun cancelDownload()
}
