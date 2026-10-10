package com.osm.wear.services

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import com.osm.wear.models.RouteStep
import com.osm.wear.models.UserLocation
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.repositories.IGeocodingRepository
import com.osm.wear.repositories.IPreferencesRepository
import io.mockk.*
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.IOException

class BuildRouteToMarkerUseCaseTest {

    private lateinit var geocodingRepository: IGeocodingRepository
    private lateinit var preferencesRepository: IPreferencesRepository
    private lateinit var useCase: BuildRouteToMarkerUseCase

    private val defaultCenter = GeoPoint(48.8584, 2.2945)
    private val targetPoint = GeoPoint(48.8606, 2.3376) // Louvre

    @Before
    fun setUp() {
        geocodingRepository = mockk()
        preferencesRepository = mockk()
        useCase = BuildRouteToMarkerUseCase(geocodingRepository, preferencesRepository)

        every { preferencesRepository.getMapCenter() } returns defaultCenter
        every { preferencesRepository.getNavigationMode() } returns NavigationMode.WALKING
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    private fun createSampleRoute(
        id: String = "test-route-1",
        name: String = "Test Route",
        stepCount: Int = 2
    ): NavigationRoute {
        val p1 = GeoPoint(48.8584, 2.2945)
        val p2 = GeoPoint(48.8595, 2.3000)
        val p3 = GeoPoint(48.8606, 2.3376)

        val steps = mutableListOf<RouteStep>()
        if (stepCount >= 1) {
            steps.add(
                RouteStep(
                    index = 0,
                    polyline = listOf(p1, p2),
                    distanceM = 450f,
                    durationS = 320f,
                    roadName = "Avenue de Paris",
                    maneuver = Maneuver(
                        type = ManeuverType.TURN_RIGHT,
                        location = p2,
                        bearingBefore = 45f,
                        bearingAfter = 90f,
                        relativeAngle = 45f,
                        roadName = "Rue de Rivoli",
                        instruction = "In 450 meters, turn right onto Rue de Rivoli"
                    )
                )
            )
        }
        if (stepCount >= 2) {
            steps.add(
                RouteStep(
                    index = 1,
                    polyline = listOf(p2, p3),
                    distanceM = 600f,
                    durationS = 420f,
                    roadName = "Rue de Rivoli",
                    maneuver = Maneuver(
                        type = ManeuverType.ARRIVE,
                        location = p3,
                        bearingBefore = 90f,
                        bearingAfter = 90f,
                        relativeAngle = 0f,
                        roadName = "Rue de Rivoli",
                        instruction = "Arrive at destination on Rue de Rivoli"
                    )
                )
            )
        }

        return NavigationRoute(
            id = id,
            name = name,
            steps = steps,
            totalDistanceM = steps.sumOf { it.distanceM.toDouble() }.toFloat(),
            totalDurationS = steps.sumOf { it.durationS.toDouble() }.toFloat()
        )
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Success Modes
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testInvoke_successWithCurrentLocation_usesLocationCoordinates() = runBlocking {
        val sampleRoute = createSampleRoute()
        val currentLoc = UserLocation(48.8500, 2.2900, accuracy = 5f)

        coEvery {
            geocodingRepository.fetchNavigationRoute(
                startLat = 48.8500,
                startLon = 2.2900,
                endLat = targetPoint.lat,
                endLon = targetPoint.lon,
                mode = NavigationMode.WALKING
            )
        } returns sampleRoute

        val result = useCase(targetPoint, currentLoc, "Louvre Museum")

        assertTrue("Expected success with valid route", result.isSuccess)
        val route = result.getOrThrow()

        assertEquals(sampleRoute, route)
        assertEquals(2, route.steps.size)

        // Also test buildRouteFile
        val fileResult = useCase.buildRouteFile(targetPoint, currentLoc, "Louvre Museum")
        assertTrue(fileResult.isSuccess)
        val routeFile = fileResult.getOrThrow()
        assertEquals("Louvre Museum", routeFile.name)
        assertTrue(routeFile.isActive)
        assertEquals(sampleRoute, routeFile.route)
        assertEquals(3, routeFile.points.size)
        assertEquals(48.8584, routeFile.points[0].lat, 0.0001)
        assertEquals(48.8606, routeFile.points[2].lat, 0.0001)

        // Map center must be evaluated lazily; when currentLoc is valid, getMapCenter() is not called
        verify(exactly = 0) { preferencesRepository.getMapCenter() }
        coVerify(exactly = 2) {
            geocodingRepository.fetchNavigationRoute(48.8500, 2.2900, targetPoint.lat, targetPoint.lon, NavigationMode.WALKING)
        }
    }

    @Test
    fun testInvoke_lazyMapCenterEvaluation_succeedsWhenCurrentLocationIsValidEvenIfMapCenterThrows() = runBlocking {
        val sampleRoute = createSampleRoute()
        every { preferencesRepository.getMapCenter() } throws IllegalStateException("No map center set")
        val currentLoc = UserLocation(48.8500, 2.2900, accuracy = 5f)

        coEvery {
            geocodingRepository.fetchNavigationRoute(
                startLat = 48.8500,
                startLon = 2.2900,
                endLat = targetPoint.lat,
                endLon = targetPoint.lon,
                mode = NavigationMode.WALKING
            )
        } returns sampleRoute

        val result = useCase(targetPoint, currentLoc, "Test Point")
        assertTrue("Route calculation must succeed with valid GPS location even if map center preference throws", result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(sampleRoute, route)
        verify(exactly = 0) { preferencesRepository.getMapCenter() }
        coVerify(exactly = 1) {
            geocodingRepository.fetchNavigationRoute(48.8500, 2.2900, targetPoint.lat, targetPoint.lon, NavigationMode.WALKING)
        }
    }

    @Test
    fun testInvoke_successWithNullCurrentLocation_fallsBackToMapCenter() = runBlocking {
        val sampleRoute = createSampleRoute()

        coEvery {
            geocodingRepository.fetchNavigationRoute(
                startLat = defaultCenter.lat,
                startLon = defaultCenter.lon,
                endLat = targetPoint.lat,
                endLon = targetPoint.lon,
                mode = NavigationMode.WALKING
            )
        } returns sampleRoute

        val result = useCase(targetPoint, currentLoc = null, "Center Route")

        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(sampleRoute, route)

        verify(exactly = 1) { preferencesRepository.getMapCenter() }
        coVerify(exactly = 1) {
            geocodingRepository.fetchNavigationRoute(defaultCenter.lat, defaultCenter.lon, targetPoint.lat, targetPoint.lon, NavigationMode.WALKING)
        }
    }

    @Test
    fun testBuildNavigationRoute_successDirectRoute() = runBlocking {
        val sampleRoute = createSampleRoute()
        val currentLoc = UserLocation(48.8550, 2.2920, accuracy = 4f)

        coEvery {
            geocodingRepository.fetchNavigationRoute(
                startLat = 48.8550,
                startLon = 2.2920,
                endLat = targetPoint.lat,
                endLon = targetPoint.lon,
                mode = NavigationMode.WALKING
            )
        } returns sampleRoute

        val result = useCase.buildNavigationRoute(targetPoint, currentLoc, "Direct Route")

        assertTrue(result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(sampleRoute.id, route.id)
        assertEquals(2, route.steps.size)
        assertEquals("Avenue de Paris", route.steps[0].roadName)
        assertEquals(ManeuverType.TURN_RIGHT, route.steps[0].maneuver.type)
        assertEquals("Rue de Rivoli", route.steps[0].maneuver.roadName)
        verify(exactly = 0) { preferencesRepository.getMapCenter() }
    }

    @Test
    fun testBuildNavigationRoute_lazyMapCenterEvaluation_succeedsWhenCurrentLocationIsValidEvenIfMapCenterThrows() = runBlocking {
        val sampleRoute = createSampleRoute()
        every { preferencesRepository.getMapCenter() } throws IllegalStateException("No map center set")
        val currentLoc = UserLocation(48.8550, 2.2920, accuracy = 4f)

        coEvery {
            geocodingRepository.fetchNavigationRoute(
                startLat = 48.8550,
                startLon = 2.2920,
                endLat = targetPoint.lat,
                endLon = targetPoint.lon,
                mode = NavigationMode.WALKING
            )
        } returns sampleRoute

        val result = useCase.buildNavigationRoute(targetPoint, currentLoc, "Direct Route")
        assertTrue("Direct navigation route must succeed when current location is valid even if map center throws", result.isSuccess)
        val route = result.getOrThrow()
        assertEquals(sampleRoute.id, route.id)
        verify(exactly = 0) { preferencesRepository.getMapCenter() }
        coVerify(exactly = 1) {
            geocodingRepository.fetchNavigationRoute(48.8550, 2.2920, targetPoint.lat, targetPoint.lon, NavigationMode.WALKING)
        }
    }

    @Test
    fun testInvoke_respectsSelectedNavigationMode() = runBlocking {
        val sampleRoute = createSampleRoute()
        every { preferencesRepository.getNavigationMode() } returns NavigationMode.CYCLING

        coEvery {
            geocodingRepository.fetchNavigationRoute(
                startLat = defaultCenter.lat,
                startLon = defaultCenter.lon,
                endLat = targetPoint.lat,
                endLon = targetPoint.lon,
                mode = NavigationMode.CYCLING
            )
        } returns sampleRoute

        val result = useCase(targetPoint, currentLoc = null)
        assertTrue(result.isSuccess)

        coVerify(exactly = 1) {
            geocodingRepository.fetchNavigationRoute(any(), any(), any(), any(), NavigationMode.CYCLING)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Failure Modes
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testInvoke_failureWhenRepositoryReturnsNull() = runBlocking {
        coEvery {
            geocodingRepository.fetchNavigationRoute(any(), any(), any(), any(), any())
        } returns null

        val result = useCase(targetPoint, currentLoc = null)

        assertTrue("Null route must produce failure", result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Routing failed. Check your internet connection.", ex?.message)
    }

    @Test
    fun testInvoke_failureWhenRepositoryReturnsEmptySteps() = runBlocking {
        val emptyRoute = NavigationRoute(
            id = "empty",
            name = "Empty Route",
            steps = emptyList(),
            totalDistanceM = 0f,
            totalDurationS = 0f
        )

        coEvery {
            geocodingRepository.fetchNavigationRoute(any(), any(), any(), any(), any())
        } returns emptyRoute

        val result = useCase(targetPoint, currentLoc = null)

        assertTrue("Empty steps must produce failure", result.isFailure)
        val ex = result.exceptionOrNull()
        assertNotNull(ex)
        assertEquals("Routing failed. Check your internet connection.", ex?.message)
    }

    @Test
    fun testInvoke_failureWhenRepositoryThrowsIOException() = runBlocking {
        val networkError = IOException("Socket timed out: OSRM endpoint unavailable")
        coEvery {
            geocodingRepository.fetchNavigationRoute(any(), any(), any(), any(), any())
        } throws networkError

        val result = useCase(targetPoint, currentLoc = null)

        assertTrue("Network error must produce failure", result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue("Exception must be or wrap network error", ex is IOException || ex?.cause is IOException)
        assertEquals("Socket timed out: OSRM endpoint unavailable", ex?.message)
    }

    @Test
    fun testInvoke_failureWhenPreferencesThrows() = runBlocking {
        every { preferencesRepository.getMapCenter() } throws IllegalStateException("Preferences corrupt")

        val result = useCase(targetPoint, currentLoc = null)

        assertTrue(result.isFailure)
        val ex = result.exceptionOrNull()
        assertTrue(ex is IllegalStateException)
        assertEquals("Preferences corrupt", ex?.message)
    }

    @Test
    fun testBuildNavigationRoute_failureModesPropagateProperly() = runBlocking {
        coEvery {
            geocodingRepository.fetchNavigationRoute(any(), any(), any(), any(), any())
        } returns null

        val result = useCase.buildNavigationRoute(targetPoint, currentLoc = null)

        assertTrue(result.isFailure)
        assertEquals("Routing failed. Check your internet connection.", result.exceptionOrNull()?.message)
    }
}
