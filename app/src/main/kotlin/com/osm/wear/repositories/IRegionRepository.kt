package com.osm.wear.repositories

import kotlinx.coroutines.flow.StateFlow
import java.io.File

interface IRegionRepository {
    val activeRegionId: StateFlow<String?>
    val activeMapFile: StateFlow<File?>
    fun getActiveMapFile(): File?
    fun getActiveRegionId(): String?
    fun setActiveRegionId(id: String?)
}
