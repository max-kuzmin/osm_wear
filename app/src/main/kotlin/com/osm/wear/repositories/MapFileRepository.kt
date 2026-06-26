package com.osm.wear.repositories

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple singleton holding a reference to the active Mapsforge .map file.
 * Also provides GPX coverage validation against the map's bounding box.
 */
@Singleton
class MapFileRepository @Inject constructor() : IMapFileRepository {

    companion object {
        private const val TAG = "MapFileRepository"
    }

    @Volatile
    private var activeFile: File? = null

    override fun getActiveMapFile(): File? = activeFile

    override fun setActiveMapFile(file: File?) {
        activeFile = file
    }
}
