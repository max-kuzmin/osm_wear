package com.osm.wear.services

import com.osm.wear.models.GpxFile
import com.osm.wear.repositories.IRegionRepository
import org.mapsforge.map.reader.MapFile
import javax.inject.Inject

class CheckGpxCoverageUseCase @Inject constructor(
    private val regionRepository: IRegionRepository
) {
    operator fun invoke(gpx: GpxFile): Boolean {
        val file = regionRepository.getActiveMapFile() ?: return false
        if (!file.exists()) return false

        return try {
            val mapFile = MapFile(file)
            try {
                val mapBBox = mapFile.boundingBox() ?: return false
                val trackPoints = gpx.trackPoints
                if (trackPoints.isEmpty()) return false

                var sumLat = 0.0
                var sumLon = 0.0
                for (pt in trackPoints) {
                    sumLat += pt.lat
                    sumLon += pt.lon
                }
                val centroidLat = sumLat / trackPoints.size
                val centroidLon = sumLon / trackPoints.size

                centroidLat >= mapBBox.minLatitude &&
                        centroidLat <= mapBBox.maxLatitude &&
                        centroidLon >= mapBBox.minLongitude &&
                        centroidLon <= mapBBox.maxLongitude
            } finally {
                mapFile.close()
            }
        } catch (e: Exception) {
            android.util.Log.e("CheckGpxCoverage", "Failed to check GPX coverage", e)
            false
        }
    }
}
