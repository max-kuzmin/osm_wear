package com.osm.wear.data_sources

import com.osm.wear.models.MapRegion
import java.io.File
import kotlinx.coroutines.flow.Flow

interface IRemoteRegionDataSource {
    suspend fun downloadRegion(
        region: MapRegion,
        tempFile: File,
        onProgress: (Int, Float) -> Unit
    )
    fun cancelDownload()
}
