package com.osm.wear.repositories

import java.io.File

/**
 * Provides access to the currently active Mapsforge .map file.
 * Shared across ViewModels so NavigationService can read road data
 * from the same map file used for rendering.
 */
interface IMapFileRepository {
    /** Returns the currently active .map file, or null if none is loaded. */
    fun getActiveMapFile(): File?

    /** Sets the active .map file. */
    fun setActiveMapFile(file: File?)
}
