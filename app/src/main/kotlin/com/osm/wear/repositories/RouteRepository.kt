package com.osm.wear.repositories

import android.net.Uri
import android.util.Log
import com.osm.wear.data_sources.ILocalFileDataSource
import com.osm.wear.data_sources.ILocalPreferencesDataSource
import com.osm.wear.data_sources.geojson.GeoJsonRouteParser
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException

class RouteRepository(
    private val localFileDataSource: ILocalFileDataSource,
    private val prefs: ILocalPreferencesDataSource,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : IRouteRepository {

    private val tracksDir: File get() = localFileDataSource.getTracksDirectory()

    private val _routes = MutableStateFlow<List<RouteFile>>(emptyList())
    override val routes: StateFlow<List<RouteFile>> = _routes.asStateFlow()

    private val _activeRoute = MutableStateFlow<RouteFile?>(null)
    override val activeRoute: StateFlow<RouteFile?> = _activeRoute.asStateFlow()

    private val repositoryScope = CoroutineScope(ioDispatcher + SupervisorJob())

    init {
        loadStoredRoutes()
        repositoryScope.launch {
            routes.collect { list ->
                _activeRoute.value = list.find { it.isActive }
            }
        }
    }

    private fun loadStoredRoutes() {
        val activeId = prefs.getString(PREF_ACTIVE_ROUTE_ID, null)

        val files = tracksDir.listFiles()
            ?.filter { it.isFile && (it.extension.equals("geojson", ignoreCase = true) || it.extension.equals("json", ignoreCase = true)) }
            ?: emptyList()

        val loaded = files.mapNotNull { file ->
            try {
                val text = file.readText()
                val parsedRoute = GeoJsonRouteParser.parse(text, defaultName = file.nameWithoutExtension)
                if (parsedRoute != null && parsedRoute.fullPolyline.isNotEmpty()) {
                    RouteFile(
                        id = file.name,
                        name = parsedRoute.name.ifBlank { file.nameWithoutExtension },
                        filePath = file.absolutePath,
                        points = parsedRoute.fullPolyline,
                        totalDistanceKm = parsedRoute.totalDistanceM / 1000.0,
                        isActive = (!activeId.isNullOrBlank() && (file.name == activeId || file.absolutePath == activeId)),
                        route = parsedRoute
                    )
                } else {
                    Log.w(TAG, "Skipping invalid route file: ${file.name}")
                    null
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error reading route file: ${file.name}", e)
                null
            }
        }

        _routes.value = loaded
        _activeRoute.value = loaded.find { it.isActive }
        Log.d(TAG, "Loaded ${loaded.size} routes from tracks directory")
    }

    override suspend fun saveRoute(
        name: String,
        route: NavigationRoute
    ): Result<RouteFile> = withContext(ioDispatcher) {
        try {
            val safeName = name.replace(Regex("[^a-zA-Z0-9_-]"), "_").ifBlank { "route" }
            val fileName = "${safeName}_${System.currentTimeMillis()}.geojson"
            val destFile = File(tracksDir, fileName)
            destFile.parentFile?.mkdirs()

            val routeToSave = route.copy(id = fileName, name = name)
            val jsonContent = GeoJsonRouteParser.serialize(routeToSave)
            destFile.writeText(jsonContent)

            val activeId = prefs.getString(PREF_ACTIVE_ROUTE_ID, null)
            val routeFile = RouteFile(
                id = fileName,
                name = name,
                filePath = destFile.absolutePath,
                points = routeToSave.fullPolyline,
                totalDistanceKm = routeToSave.totalDistanceM / 1000.0,
                isActive = (!activeId.isNullOrBlank() && (fileName == activeId || destFile.absolutePath == activeId)),
                route = routeToSave
            )

            addOrReplace(routeFile)
            Result.success(routeFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save route: $name", e)
            Result.failure(e)
        }
    }

    override suspend fun deleteRoute(routeId: String): Unit = withContext(ioDispatcher) {
        if (routeId.isBlank()) return@withContext

        val target = _routes.value.find {
            it.id == routeId || it.filePath == routeId || File(it.filePath).name == routeId
        } ?: return@withContext

        val file = File(target.filePath)
        if (file.exists()) {
            file.delete()
        }

        val currentActiveId = prefs.getString(PREF_ACTIVE_ROUTE_ID, null)
        if (currentActiveId == target.id || _activeRoute.value?.id == target.id) {
            prefs.remove(PREF_ACTIVE_ROUTE_ID)
            _activeRoute.value = null
        }

        _routes.value = _routes.value.filter { it.id != target.id }
    }

    override fun setActive(routeId: String) {
        if (routeId.isBlank()) {
            prefs.remove(PREF_ACTIVE_ROUTE_ID)
            _routes.value = _routes.value.map { it.copy(isActive = false) }
            _activeRoute.value = null
            return
        }

        val target = _routes.value.find {
            it.id == routeId || it.filePath == routeId || File(it.filePath).name == routeId
        }

        if (target != null) {
            prefs.putString(PREF_ACTIVE_ROUTE_ID, target.id)
            _routes.value = _routes.value.map { it.copy(isActive = (it.id == target.id)) }
            _activeRoute.value = target.copy(isActive = true)
        } else {
            // Unmatched ID unsets active route per IRouteRepository specification
            prefs.remove(PREF_ACTIVE_ROUTE_ID)
            _routes.value = _routes.value.map { it.copy(isActive = false) }
            _activeRoute.value = null
        }
    }

    override suspend fun importFromUri(uri: Uri): Result<RouteFile> = withContext(ioDispatcher) {
        try {
            var rawName = localFileDataSource.getFileNameFromUri(uri)?.let { File(it).name.trim() }
            if (rawName.isNullOrBlank()) {
                rawName = "imported_${System.currentTimeMillis()}.geojson"
            } else if (!rawName.endsWith(".geojson", ignoreCase = true) && !rawName.endsWith(".json", ignoreCase = true)) {
                rawName = "$rawName.geojson"
            }

            val jsonText = localFileDataSource.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: return@withContext Result.failure(IOException("Cannot open input stream for URI: $uri"))

            val defaultName = rawName.substringBeforeLast(".").ifBlank { "imported" }
            val parsedRoute = GeoJsonRouteParser.parse(jsonText, defaultName = defaultName)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid GeoJSON route data in URI: $uri"))

            if (parsedRoute.fullPolyline.size < 2) {
                return@withContext Result.failure(IllegalArgumentException("Route polyline contains fewer than 2 coordinates"))
            }

            val destFile = File(tracksDir, rawName).let { file ->
                if (file.exists()) {
                    File(tracksDir, "${file.nameWithoutExtension}_${System.currentTimeMillis()}.${file.extension}")
                } else {
                    file
                }
            }
            destFile.parentFile?.mkdirs()

            val canonicalRoute = parsedRoute.copy(id = destFile.name)
            destFile.writeText(GeoJsonRouteParser.serialize(canonicalRoute))

            val activeId = prefs.getString(PREF_ACTIVE_ROUTE_ID, null)
            val routeFile = RouteFile(
                id = destFile.name,
                name = canonicalRoute.name,
                filePath = destFile.absolutePath,
                points = canonicalRoute.fullPolyline,
                totalDistanceKm = canonicalRoute.totalDistanceM / 1000.0,
                isActive = (!activeId.isNullOrBlank() && (destFile.name == activeId || destFile.absolutePath == activeId)),
                route = canonicalRoute
            )

            addOrReplace(routeFile)
            Result.success(routeFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import route from URI: $uri", e)
            Result.failure(e)
        }
    }

    override suspend fun importFromFile(file: File): Result<RouteFile> = withContext(ioDispatcher) {
        try {
            if (!file.exists() || !file.isFile) {
                return@withContext Result.failure(IOException("Source file does not exist or is not a file: ${file.path}"))
            }

            val jsonText = file.readText()
            val parsedRoute = GeoJsonRouteParser.parse(jsonText, defaultName = file.nameWithoutExtension)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid GeoJSON route data in file: ${file.name}"))

            if (parsedRoute.fullPolyline.size < 2) {
                return@withContext Result.failure(IllegalArgumentException("Route polyline contains fewer than 2 coordinates"))
            }

            val hasValidExtension = file.name.endsWith(".geojson", ignoreCase = true) || file.name.endsWith(".json", ignoreCase = true)
            val destName = if (hasValidExtension) file.name else "${file.name}.geojson"

            val destFile = if (file.parentFile?.canonicalPath == tracksDir.canonicalPath && hasValidExtension) {
                file
            } else {
                val candidate = File(tracksDir, destName)
                if (candidate.exists()) {
                    File(tracksDir, "${candidate.nameWithoutExtension}_${System.currentTimeMillis()}.${candidate.extension}")
                } else {
                    candidate
                }
            }
            destFile.parentFile?.mkdirs()

            val canonicalRoute = parsedRoute.copy(id = destFile.name)
            destFile.writeText(GeoJsonRouteParser.serialize(canonicalRoute))

            val activeId = prefs.getString(PREF_ACTIVE_ROUTE_ID, null)
            val routeFile = RouteFile(
                id = destFile.name,
                name = canonicalRoute.name,
                filePath = destFile.absolutePath,
                points = canonicalRoute.fullPolyline,
                totalDistanceKm = canonicalRoute.totalDistanceM / 1000.0,
                isActive = (!activeId.isNullOrBlank() && (destFile.name == activeId || destFile.absolutePath == activeId)),
                route = canonicalRoute
            )

            addOrReplace(routeFile)
            Result.success(routeFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to import route from file: ${file.path}", e)
            Result.failure(e)
        }
    }

    private fun addOrReplace(routeFile: RouteFile) {
        val current = _routes.value.toMutableList()
        val idx = current.indexOfFirst { it.filePath == routeFile.filePath || it.id == routeFile.id }
        if (idx >= 0) {
            val existing = current[idx]
            current[idx] = routeFile.copy(isActive = existing.isActive)
        } else {
            val activeId = prefs.getString(PREF_ACTIVE_ROUTE_ID, null)
            current.add(routeFile.copy(isActive = (!activeId.isNullOrBlank() && (routeFile.id == activeId || routeFile.filePath == activeId))))
        }
        _routes.value = current
        _activeRoute.value = current.find { it.isActive }
    }

    companion object {
        private const val TAG = "RouteRepository"
        const val PREF_ACTIVE_ROUTE_ID = "active_route_id"
    }
}
