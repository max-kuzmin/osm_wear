package com.osm.wear.services

import com.osm.wear.models.DownloadedRegion
import com.osm.wear.models.DownloadState
import com.osm.wear.models.MapRegion
import com.osm.wear.repositories.IRegionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File

class FakeRegionRepository(var activeFile: File? = null) : IRegionRepository {
    override val activeRegionId: StateFlow<String?> = MutableStateFlow(null)
    override val activeMapFile: StateFlow<File?> = MutableStateFlow(activeFile)
    override fun getActiveMapFile(): File? = activeFile
    override fun getActiveRegionId(): String? = null
    override fun setActiveRegionId(id: String?) {}

    override val all: List<MapRegion> = emptyList()
    override fun getLocalFile(region: MapRegion): File = File("")
    override fun getDownloadedRegions(activeId: String?): List<DownloadedRegion> = emptyList()
    override fun deleteRegion(region: MapRegion): Boolean = false

    override val downloadState: StateFlow<DownloadState> = MutableStateFlow(DownloadState.Idle)
    override suspend fun downloadRegion(region: MapRegion) {}
    override fun cancelDownload() {}
}
