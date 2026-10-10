package com.osm.wear.view_models

import android.net.Uri
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import com.osm.wear.models.RouteStep
import com.osm.wear.repositories.IRouteRepository
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TracksViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var routeRepository: IRouteRepository
    private val routesFlow = MutableStateFlow<List<RouteFile>>(emptyList())
    private val activeRouteFlow = MutableStateFlow<RouteFile?>(null)

    private lateinit var viewModel: TracksViewModel

    private val sampleRouteFile = RouteFile(
        id = "test_1.geojson",
        name = "Test Route 1",
        filePath = "/tracks/test_1.geojson",
        points = listOf(GeoPoint(48.85, 2.35), GeoPoint(48.86, 2.36)),
        totalDistanceKm = 1.5,
        isActive = false
    )

    private lateinit var scanTracksFoldersUseCase: com.osm.wear.services.ScanTracksFoldersUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        routeRepository = mockk(relaxed = true)
        scanTracksFoldersUseCase = mockk(relaxed = true)

        every { routeRepository.routes } returns routesFlow
        every { routeRepository.activeRoute } returns activeRouteFlow

        viewModel = TracksViewModel(routeRepository, scanTracksFoldersUseCase)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        clearAllMocks()
    }

    @Test
    fun testInitialState_loadsRoutesAndActiveRouteFromRepository() = runTest {
        val activeRoute = sampleRouteFile.copy(isActive = true)
        routesFlow.value = listOf(sampleRouteFile, activeRoute)
        activeRouteFlow.value = activeRoute

        val state = viewModel.uiState.value
        assertEquals(2, state.routes.size)
        assertEquals(activeRoute, state.activeRoute)
        assertFalse(state.isSaving)
        assertFalse(state.isImporting)
    }

    @Test
    fun testOnSetActiveIntent_delegatesToRepository() = runTest {
        viewModel.onIntent(TracksIntent.SetActive("target_route.geojson"))
        verify(exactly = 1) { routeRepository.setActive("target_route.geojson") }
    }

    @Test
    fun testOnImportTrackIntent_success() = runTest {
        val mockUri = mockk<Uri>()
        val importedRoute = sampleRouteFile.copy(id = "imported.geojson", name = "Imported Track")
        coEvery { routeRepository.importFromUri(mockUri) } returns Result.success(importedRoute)

        val effects = mutableListOf<TracksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(TracksIntent.ImportTrack(mockUri))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { routeRepository.importFromUri(mockUri) }
        verify(exactly = 1) { routeRepository.setActive("imported.geojson") }
        assertFalse(viewModel.uiState.value.isImporting)
        assertTrue(effects.any { it is TracksEffect.ShowToast && it.message.contains("Imported Track") })
    }

    @Test
    fun testOnImportTrackIntent_failure() = runTest {
        val mockUri = mockk<Uri>()
        coEvery { routeRepository.importFromUri(mockUri) } returns Result.failure(Exception("Malformed GeoJSON"))

        val effects = mutableListOf<TracksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(TracksIntent.ImportTrack(mockUri))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { routeRepository.importFromUri(mockUri) }
        verify(exactly = 0) { routeRepository.setActive(any()) }
        assertFalse(viewModel.uiState.value.isImporting)
        assertTrue(effects.any { it is TracksEffect.ShowToast && it.message.contains("Malformed GeoJSON") })
    }

    @Test
    fun testOnSaveCurrentIntent_success() = runTest {
        val navRoute = NavigationRoute(
            id = "nav_1",
            name = "My Saved Route",
            steps = listOf(
                RouteStep(0, listOf(GeoPoint(48.85, 2.35)), 100f, maneuver = Maneuver(ManeuverType.ARRIVE, GeoPoint(48.85, 2.35)))
            ),
            totalDistanceM = 100f
        )
        val savedFile = sampleRouteFile.copy(id = "saved.geojson", name = "My Saved Route")
        coEvery { routeRepository.saveRoute("My Saved Route", navRoute) } returns Result.success(savedFile)

        val effects = mutableListOf<TracksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(TracksIntent.SaveCurrent("My Saved Route", navRoute))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { routeRepository.saveRoute("My Saved Route", navRoute) }
        verify(exactly = 1) { routeRepository.setActive("saved.geojson") }
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(effects.any { it is TracksEffect.ShowToast && it.message.contains("saved successfully") })
    }

    @Test
    fun testOnSaveCurrentIntent_failure() = runTest {
        val navRoute = NavigationRoute(
            id = "nav_1",
            name = "Error Route",
            steps = emptyList(),
            totalDistanceM = 0f
        )
        coEvery { routeRepository.saveRoute("Error Route", navRoute) } returns Result.failure(Exception("Disk full"))

        val effects = mutableListOf<TracksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(TracksIntent.SaveCurrent("Error Route", navRoute))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { routeRepository.saveRoute("Error Route", navRoute) }
        verify(exactly = 0) { routeRepository.setActive(any()) }
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(effects.any { it is TracksEffect.ShowToast && it.message.contains("Disk full") })
    }

    @Test
    fun testOnDeleteTrackIntent() = runTest {
        coEvery { routeRepository.deleteRoute("to_delete.geojson") } returns Unit

        val effects = mutableListOf<TracksEffect>()
        backgroundScope.launch(UnconfinedTestDispatcher(testScheduler)) {
            viewModel.effect.collect { effects.add(it) }
        }

        viewModel.onIntent(TracksIntent.DeleteTrack("to_delete.geojson"))
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { routeRepository.deleteRoute("to_delete.geojson") }
        assertTrue(effects.any { it is TracksEffect.ShowToast && it.message.contains("Track deleted") })
    }

    @Test
    fun testOnScanFoldersIntent() = runTest {
        coEvery { scanTracksFoldersUseCase() } returns Unit

        viewModel.onIntent(TracksIntent.ScanFolders)
        testScheduler.advanceUntilIdle()

        coVerify(exactly = 1) { scanTracksFoldersUseCase() }
    }
}
