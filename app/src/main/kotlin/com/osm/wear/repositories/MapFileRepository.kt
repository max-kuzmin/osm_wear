package com.osm.wear.repositories

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Simple singleton holding a reference to the active Mapsforge .map file.
 * Also provides GPX coverage validation against the map's bounding box.
 */
@Singleton
class MapFileRepository @Inject constructor() : IMapFileRepository {

    private val _activeMapFile = MutableStateFlow<File?>(null)
    override val activeMapFile: StateFlow<File?> = _activeMapFile.asStateFlow()

    override fun getActiveMapFile(): File? = _activeMapFile.value

    override fun setActiveMapFile(file: File?) {
        _activeMapFile.value = file
    }
}
