package com.osm.wear.repositories

import android.net.Uri
import com.osm.wear.data_sources.ILocalFileDataSource
import com.osm.wear.data_sources.ILocalPreferencesDataSource
import com.osm.wear.data_sources.geojson.GeoJsonRouteParser
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteStep
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.Files

@OptIn(ExperimentalCoroutinesApi::class)
class RouteRepositoryTest {

    private lateinit var tempTracksDir: File
    private lateinit var fakeFileDataSource: FakeLocalFileDataSource
    private lateinit var fakePrefs: FakeLocalPreferencesDataSource
    private val testDispatcher = UnconfinedTestDispatcher()

    private class FakeLocalFileDataSource(private val tracksDir: File) : ILocalFileDataSource {
        var uriStreamMap = mutableMapOf<String, InputStream>()

        override fun getTracksDirectory(): File = tracksDir
        override fun getRegionDirectory(): File = tracksDir

        override fun openInputStream(uri: Uri): InputStream? {
            val key = uri.toString()
            return uriStreamMap[key] ?: uri.path?.let { p ->
                val f = File(p)
                if (f.exists()) f.inputStream() else null
            }
        }

        override fun openOutputStream(file: File): OutputStream = FileOutputStream(file)
        override fun getFileNameFromUri(uri: Uri): String? = uri.lastPathSegment
    }

    private class FakeLocalPreferencesDataSource : ILocalPreferencesDataSource {
        private val storage = mutableMapOf<String, Any?>()

        override fun getString(key: String, defValue: String?): String? = storage[key] as? String ?: defValue
        override fun putString(key: String, value: String?) {
            if (value != null) storage[key] = value else storage.remove(key)
        }
        override fun getInt(key: String, defValue: Int): Int = storage[key] as? Int ?: defValue
        override fun putInt(key: String, value: Int) { storage[key] = value }
        override fun getFloat(key: String, defValue: Float): Float = storage[key] as? Float ?: defValue
        override fun putFloat(key: String, value: Float) { storage[key] = value }
        override fun getBoolean(key: String, defValue: Boolean): Boolean = storage[key] as? Boolean ?: defValue
        override fun putBoolean(key: String, value: Boolean) { storage[key] = value }
        override fun remove(key: String) { storage.remove(key) }
    }

    @Before
    fun setUp() {
        tempTracksDir = Files.createTempDirectory("osm_wear_tracks_test").toFile()
        fakeFileDataSource = FakeLocalFileDataSource(tempTracksDir)
        fakePrefs = FakeLocalPreferencesDataSource()
    }

    @After
    fun tearDown() {
        tempTracksDir.deleteRecursively()
    }

    private fun createSampleRoute(
        id: String = "test_route_1",
        name: String = "Scenic Park Path"
    ): NavigationRoute {
        val p1 = GeoPoint(37.7749, -122.4194)
        val p2 = GeoPoint(37.7750, -122.4180)
        val p3 = GeoPoint(37.7760, -122.4170)

        val step1 = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 150f,
            durationS = 60f,
            roadName = "Park Entry",
            maneuver = Maneuver(
                type = ManeuverType.TURN_LEFT,
                location = p2,
                bearingBefore = 90f,
                bearingAfter = 45f,
                relativeAngle = -45f,
                roadName = "Meadow Lane",
                instruction = "Turn left onto Meadow Lane"
            )
        )

        val step2 = RouteStep(
            index = 1,
            polyline = listOf(p2, p3),
            distanceM = 200f,
            durationS = 90f,
            roadName = "Meadow Lane",
            maneuver = Maneuver(
                type = ManeuverType.ARRIVE,
                location = p3,
                bearingBefore = 45f,
                bearingAfter = 45f,
                relativeAngle = 0f,
                roadName = "Meadow Lane",
                instruction = "Arrive at destination"
            )
        )

        return NavigationRoute(
            id = id,
            name = name,
            steps = listOf(step1, step2),
            totalDistanceM = 350f,
            totalDurationS = 150f
        )
    }

    @Test
    fun testLoadStoredRoutes_discoversGeoJsonAndJsonFiles() = runTest(testDispatcher) {
        val r1 = createSampleRoute("r1", "Route One")
        val r2 = createSampleRoute("r2", "Route Two")
        val r3 = createSampleRoute("r3", "Route Three")

        File(tempTracksDir, "route_1.geojson").writeText(GeoJsonRouteParser.serialize(r1))
        File(tempTracksDir, "route_2.json").writeText(GeoJsonRouteParser.serialize(r2))
        File(tempTracksDir, "route_3.GEOJSON").writeText(GeoJsonRouteParser.serialize(r3))
        // Non-geojson file should be ignored
        File(tempTracksDir, "unrelated.txt").writeText("ignored text")

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)

        assertEquals(3, repository.routes.value.size)
        val names = repository.routes.value.map { it.name }.toSet()
        assertTrue(names.contains("Route One"))
        assertTrue(names.contains("Route Two"))
        assertTrue(names.contains("Route Three"))
    }

    @Test
    fun testLoadStoredRoutes_skipsCorruptedFilesSafely() = runTest(testDispatcher) {
        val r1 = createSampleRoute("r1", "Valid Route")
        File(tempTracksDir, "valid.geojson").writeText(GeoJsonRouteParser.serialize(r1))
        File(tempTracksDir, "corrupted.geojson").writeText("{ broken json ...")

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)

        assertEquals(1, repository.routes.value.size)
        assertEquals("Valid Route", repository.routes.value.first().name)
    }

    @Test
    fun testLoadStoredRoutes_restoresActiveRouteFromPrefs() = runTest(testDispatcher) {
        val r1 = createSampleRoute("r1", "Active Route")
        val r2 = createSampleRoute("r2", "Inactive Route")

        File(tempTracksDir, "active.geojson").writeText(GeoJsonRouteParser.serialize(r1))
        File(tempTracksDir, "inactive.geojson").writeText(GeoJsonRouteParser.serialize(r2))

        fakePrefs.putString(RouteRepository.PREF_ACTIVE_ROUTE_ID, "active.geojson")

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)

        val active = repository.activeRoute.value
        assertNotNull("Active route must be restored", active)
        assertEquals("active.geojson", active?.id)
        assertTrue(active!!.isActive)

        val inactive = repository.routes.value.find { it.id == "inactive.geojson" }
        assertNotNull(inactive)
        assertFalse(inactive!!.isActive)
    }

    @Test
    fun testSaveRoute_writesSingleGeoJsonFile_zeroSidecars() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val route = createSampleRoute("new_route", "Harbor Path")

        val result = repository.saveRoute("Harbor Path", route)
        assertTrue(result.isSuccess)

        val savedFile = result.getOrThrow()
        assertEquals("Harbor Path", savedFile.name)
        assertTrue(File(savedFile.filePath).exists())

        // Verify strictly 1 file created, ending in .geojson, and NO sidecar files (.route.json or .gpx)
        val filesOnDisk = tempTracksDir.listFiles() ?: emptyArray()
        assertEquals(1, filesOnDisk.size)
        assertTrue(filesOnDisk[0].name.endsWith(".geojson"))
        assertFalse("Must not create any companion .route.json sidecar file", filesOnDisk.any { it.name.endsWith(".route.json") })
        assertFalse("Must not create any .gpx file", filesOnDisk.any { it.name.endsWith(".gpx") })

        assertEquals(1, repository.routes.value.size)
        assertEquals(savedFile.id, repository.routes.value[0].id)
    }

    @Test
    fun testSaveRoute_preservesAllStepsAndManeuvers() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val route = createSampleRoute()

        val result = repository.saveRoute("Preserved Route", route)
        assertTrue(result.isSuccess)
        val routeFile = result.getOrThrow()

        // Read directly from disk and parse
        val content = File(routeFile.filePath).readText()
        val parsed = GeoJsonRouteParser.parse(content)
        assertNotNull(parsed)
        assertEquals(2, parsed!!.steps.size)
        assertEquals(ManeuverType.TURN_LEFT, parsed.steps[0].maneuver.type)
        assertEquals("Meadow Lane", parsed.steps[0].maneuver.roadName)
        assertEquals(ManeuverType.ARRIVE, parsed.steps[1].maneuver.type)
    }

    @Test
    fun testDeleteRoute_removesFileAndClearsActiveRoute() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val route = createSampleRoute()
        val saved = repository.saveRoute("Delete Me", route).getOrThrow()

        repository.setActive(saved.id)
        assertEquals(saved.id, repository.activeRoute.value?.id)

        repository.deleteRoute(saved.id)

        assertFalse("File must be deleted from disk", File(saved.filePath).exists())
        assertEquals(0, repository.routes.value.size)
        assertNull("Active route must be reset to null", repository.activeRoute.value)
        assertNull("Active preference must be cleared", fakePrefs.getString(RouteRepository.PREF_ACTIVE_ROUTE_ID, null))
    }

    @Test
    fun testSetActive_updatesPreferencesAndStateFlows() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val r1 = repository.saveRoute("Route A", createSampleRoute()).getOrThrow()
        val r2 = repository.saveRoute("Route B", createSampleRoute()).getOrThrow()

        // Activate Route A
        repository.setActive(r1.id)
        assertEquals(r1.id, fakePrefs.getString(RouteRepository.PREF_ACTIVE_ROUTE_ID, null))
        assertEquals(r1.id, repository.activeRoute.value?.id)
        assertTrue(repository.routes.value.find { it.id == r1.id }!!.isActive)
        assertFalse(repository.routes.value.find { it.id == r2.id }!!.isActive)

        // Switch to Route B
        repository.setActive(r2.id)
        assertEquals(r2.id, fakePrefs.getString(RouteRepository.PREF_ACTIVE_ROUTE_ID, null))
        assertEquals(r2.id, repository.activeRoute.value?.id)
        assertFalse(repository.routes.value.find { it.id == r1.id }!!.isActive)
        assertTrue(repository.routes.value.find { it.id == r2.id }!!.isActive)

        // Deactivate
        repository.setActive("")
        assertNull(fakePrefs.getString(RouteRepository.PREF_ACTIVE_ROUTE_ID, null))
        assertNull(repository.activeRoute.value)
    }

    @Test
    fun testImportFromFile_copiesExternalFileToTracksDir() = runTest(testDispatcher) {
        val externalDir = Files.createTempDirectory("external_import").toFile()
        try {
            val externalFile = File(externalDir, "external_tour.geojson")
            val sample = createSampleRoute("ext_id", "External Tour")
            externalFile.writeText(GeoJsonRouteParser.serialize(sample))

            val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
            val result = repository.importFromFile(externalFile)

            assertTrue(result.isSuccess)
            val imported = result.getOrThrow()
            assertEquals("External Tour", imported.name)
            assertTrue("File must be copied into tracks directory", File(imported.filePath).parentFile.canonicalPath == tempTracksDir.canonicalPath)
            assertEquals(1, repository.routes.value.size)
        } finally {
            externalDir.deleteRecursively()
        }
    }

    @Test
    fun testImportFromUri_handlesStreamsAndFailuresProperly() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)

        // 1. Success case: URI with valid GeoJSON stream
        val sample = createSampleRoute("uri_id", "URI Imported Route")
        val sampleJson = GeoJsonRouteParser.serialize(sample)
        val validUri = mockk<Uri>()
        every { validUri.toString() } returns "content://media/external/tracks/123"
        every { validUri.lastPathSegment } returns "imported_track.geojson"
        fakeFileDataSource.uriStreamMap["content://media/external/tracks/123"] = sampleJson.byteInputStream()

        val successResult = repository.importFromUri(validUri)
        assertTrue(successResult.isSuccess)
        assertEquals("URI Imported Route", successResult.getOrThrow().name)

        // 2. Failure case: Non-existent or unreadable URI stream
        val unreadableUri = mockk<Uri>()
        every { unreadableUri.toString() } returns "content://media/external/tracks/999"
        every { unreadableUri.lastPathSegment } returns "missing.geojson"

        val failureResult = repository.importFromUri(unreadableUri)
        assertTrue("Unreadable URI stream must return failure", failureResult.isFailure)
    }

    @Test
    fun testImportFromUri_invalidJson_returnsFailure() = runTest(testDispatcher) {
        val badUri = mockk<Uri>()
        every { badUri.toString() } returns "content://media/external/tracks/bad_json"
        every { badUri.lastPathSegment } returns "bad.geojson"
        fakeFileDataSource.uriStreamMap["content://media/external/tracks/bad_json"] = "{ invalid json content".byteInputStream()

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val result = repository.importFromUri(badUri)

        assertTrue("Importing invalid JSON must return failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun testImportFromUri_insufficientCoordinates_returnsFailure() = runTest(testDispatcher) {
        val singlePtJson = """{"type":"FeatureCollection","properties":{"name":"Single"},"features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[-122.4,37.7]]},"properties":{"role":"step","index":0,"maneuver":{"type":"ARRIVE","location":[-122.4,37.7],"instruction":"Arrive"}}}]}"""
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/external/tracks/single_pt"
        every { uri.lastPathSegment } returns "single_pt.geojson"
        fakeFileDataSource.uriStreamMap["content://media/external/tracks/single_pt"] = singlePtJson.byteInputStream()

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val result = repository.importFromUri(uri)

        assertTrue("Route with fewer than 2 coordinates must fail", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("fewer than 2 coordinates") == true)
    }

    @Test
    fun testImportFromUri_extensionlessUriFilename_persistsWithGeoJsonExtension() = runTest(testDispatcher) {
        val sample = createSampleRoute("extless", "Outdoor Track")
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/external/tracks/extless"
        every { uri.lastPathSegment } returns "my_outdoor_track"
        fakeFileDataSource.uriStreamMap["content://media/external/tracks/extless"] = GeoJsonRouteParser.serialize(sample).byteInputStream()

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val result = repository.importFromUri(uri)

        assertTrue(result.isSuccess)
        val imported = result.getOrThrow()
        assertTrue("Imported ID must have .geojson extension", imported.id.endsWith(".geojson"))
        assertTrue("Imported file on disk must have .geojson extension", File(imported.filePath).name.endsWith(".geojson"))
    }

    @Test
    fun testImportFromUri_duplicateUriImports_generateUniqueFilenames() = runTest(testDispatcher) {
        val sample = createSampleRoute("dup", "Duplicate Track")
        val uri = mockk<Uri>()
        every { uri.toString() } returns "content://media/external/tracks/dup"
        every { uri.lastPathSegment } returns "track.geojson"
        fakeFileDataSource.uriStreamMap["content://media/external/tracks/dup"] = GeoJsonRouteParser.serialize(sample).byteInputStream()

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val r1 = repository.importFromUri(uri)
        assertTrue(r1.isSuccess)

        // Provide a fresh stream for second import of the same URI
        fakeFileDataSource.uriStreamMap["content://media/external/tracks/dup"] = GeoJsonRouteParser.serialize(sample).byteInputStream()
        val r2 = repository.importFromUri(uri)
        assertTrue(r2.isSuccess)

        assertNotEquals("Duplicate imports must generate unique IDs/filenames", r1.getOrThrow().id, r2.getOrThrow().id)
        assertEquals(2, repository.routes.value.size)
    }

    @Test
    fun testImportFromFile_nonExistentFile_returnsFailure() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val nonExistent = File(tempTracksDir, "does_not_exist.geojson")

        val result = repository.importFromFile(nonExistent)
        assertTrue("Importing non-existent file must return failure", result.isFailure)
        assertTrue(result.exceptionOrNull() is java.io.IOException)
    }

    @Test
    fun testImportFromFile_insufficientCoordinates_returnsFailure() = runTest(testDispatcher) {
        val singlePtJson = """{"type":"FeatureCollection","properties":{"name":"Single"},"features":[{"type":"Feature","geometry":{"type":"LineString","coordinates":[[-122.4,37.7]]},"properties":{"role":"step","index":0,"maneuver":{"type":"ARRIVE","location":[-122.4,37.7],"instruction":"Arrive"}}}]}"""
        val f = File(tempTracksDir, "single_pt_file.geojson")
        f.writeText(singlePtJson)

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val result = repository.importFromFile(f)

        assertTrue("File with fewer than 2 coordinates must fail", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("fewer than 2 coordinates") == true)
    }

    @Test
    fun testSaveRoute_sanitizesRouteNameToSafeFilename() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val sample = createSampleRoute("r_sanitize", "Special / Route : 1 @ #")

        val result = repository.saveRoute("Special / Route : 1 @ #", sample)
        assertTrue(result.isSuccess)

        val saved = result.getOrThrow()
        val fileName = File(saved.filePath).name
        assertFalse("Filename must not contain forward slashes", fileName.contains("/"))
        assertFalse("Filename must not contain colons", fileName.contains(":"))
        assertFalse("Filename must not contain at symbols", fileName.contains("@"))
        assertFalse("Filename must not contain hash symbols", fileName.contains("#"))
        assertTrue("Filename should sanitize invalid characters to underscores", fileName.startsWith("Special___Route___1____"))
    }

    @Test
    fun testSaveRoute_recreatesTracksDirectoryIfMissing() = runTest(testDispatcher) {
        tempTracksDir.deleteRecursively()
        assertFalse(tempTracksDir.exists())

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val sample = createSampleRoute()

        val result = repository.saveRoute("Recreated Dir Route", sample)
        assertTrue(result.isSuccess)
        assertTrue("Tracks directory must be recreated automatically", tempTracksDir.exists())
        assertTrue("Saved file must exist inside recreated tracks directory", File(result.getOrThrow().filePath).exists())
    }

    @Test
    fun testDeleteRoute_blankId_isSafeNoOp() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val saved = repository.saveRoute("Keep Me", createSampleRoute()).getOrThrow()
        assertEquals(1, repository.routes.value.size)

        repository.deleteRoute("")
        repository.deleteRoute("   ")

        assertEquals("Blank ID deletion must be a safe no-op", 1, repository.routes.value.size)
        assertTrue(File(saved.filePath).exists())
    }

    @Test
    fun testDeleteRoute_exactMatchPreventsSuffixCollision() = runTest(testDispatcher) {
        val r1 = createSampleRoute("r1", "Fast Route")
        val r2 = createSampleRoute("r2", "Standard Route")
        val f1 = File(tempTracksDir, "fast_route.geojson")
        val f2 = File(tempTracksDir, "route.geojson")
        f1.writeText(GeoJsonRouteParser.serialize(r1))
        f2.writeText(GeoJsonRouteParser.serialize(r2))

        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        assertEquals(2, repository.routes.value.size)

        repository.deleteRoute("route.geojson")

        assertTrue("fast_route.geojson must still exist", f1.exists())
        assertFalse("route.geojson must be deleted", f2.exists())
        assertEquals(1, repository.routes.value.size)
        assertEquals("fast_route.geojson", repository.routes.value[0].id)
    }

    @Test
    fun testSetActive_unmatchedId_clearsActiveRoute() = runTest(testDispatcher) {
        val repository = RouteRepository(fakeFileDataSource, fakePrefs, testDispatcher)
        val saved = repository.saveRoute("Route A", createSampleRoute()).getOrThrow()
        repository.setActive(saved.id)
        assertEquals(saved.id, repository.activeRoute.value?.id)

        repository.setActive("unmatched_id_999.geojson")
        assertNull("Active route must be cleared when unmatched ID is set", repository.activeRoute.value)
        assertNull("Active preference must be removed", fakePrefs.getString(RouteRepository.PREF_ACTIVE_ROUTE_ID, null))
        assertFalse(repository.routes.value.first().isActive)
    }
}

