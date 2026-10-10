package com.osm.wear.repositories

import android.net.Uri
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import kotlinx.coroutines.flow.StateFlow
import java.io.File

/**
 * Repository responsible for persistent storage, retrieval, import, and lifecycle
 * management of native GeoJSON routes in the app's internal storage (`tracks/` directory).
 *
 * All routes are stored strictly as self-contained RFC 7946 GeoJSON files without
 * companion or sidecar files.
 */
interface IRouteRepository {

    /**
     * Read-only reactive stream of all saved route files in `tracks/`.
     * Automatically updated whenever routes are saved, imported, renamed, or deleted.
     */
    val routes: StateFlow<List<RouteFile>>

    /**
     * Read-only reactive stream of the currently active route file, or `null` if none selected.
     */
    val activeRoute: StateFlow<RouteFile?>

    /**
     * Imports a GeoJSON route from an external content URI (e.g. system file picker).
     * Validates GeoJSON structure, saves a canonical copy into `tracks/`, and updates [routes].
     */
    suspend fun importFromUri(uri: Uri): Result<RouteFile>

    /**
     * Imports a GeoJSON route from a local [File].
     * Copies or writes the file into `tracks/` and updates [routes].
     */
    suspend fun importFromFile(file: File): Result<RouteFile>

    /**
     * Saves a [NavigationRoute] directly into `tracks/` as a self-contained `.geojson` file.
     * Encodes all steps, maneuvers, and road names directly into GeoJSON feature properties.
     */
    suspend fun saveRoute(name: String, route: NavigationRoute): Result<RouteFile>

    /**
     * Deletes a route file from disk and removes it from [routes].
     * If the deleted route was active, clears active route preference and resets [activeRoute] to null.
     */
    suspend fun deleteRoute(routeId: String)

    /**
     * Sets the active route by ID and persists the selection in preferences.
     * Passing an empty string or unmatched ID unsets the active route.
     */
    fun setActive(routeId: String)
}
