package com.osm.wear.services

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import com.osm.wear.models.RouteStep
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

class BuildInitialNavigationStateUseCaseTest {

    private lateinit var regionRepository: FakeRegionRepository
    private lateinit var useCase: BuildInitialNavigationStateUseCase

    private lateinit var testMapFile: File

    @Before
    fun setUp() {
        testMapFile = File.createTempFile("test_map", ".map")
        regionRepository = FakeRegionRepository(testMapFile)

        useCase = BuildInitialNavigationStateUseCase(
            regionRepository = regionRepository
        )
    }

    @After
    fun tearDown() {
        testMapFile.delete()
        clearAllMocks()
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Initial Navigation State Calculations
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testBuildInitialNavigationState_distanceToNextTurnIsCumulativeToFirstTurn() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.8585, 2.29415)
        val p2 = GeoPoint(48.8590, 2.2940)
        val p3 = GeoPoint(48.8595, 2.2940)
        val p4 = GeoPoint(48.8595, 2.2950)

        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1),
            distanceM = 55.6f,
            maneuver = Maneuver(type = ManeuverType.CONTINUE, location = p1, relativeAngle = 0f)
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(p1, p2, p3),
            distanceM = 111.2f,
            maneuver = Maneuver(type = ManeuverType.TURN_RIGHT, location = p3, relativeAngle = 90f)
        )
        val step2 = RouteStep(
            index = 2,
            polyline = listOf(p3, p4),
            distanceM = 73.4f,
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = p4, relativeAngle = 0f)
        )
        val navRoute = NavigationRoute(
            id = "route_1",
            name = "Test Route",
            steps = listOf(step0, step1, step2),
            totalDistanceM = 240.2f
        )
        val routeFile = RouteFile(
            id = "route_1",
            name = "Test Route",
            filePath = "",
            points = listOf(p0, p1, p2, p3, p4),
            totalDistanceKm = 0.24,
            isActive = true,
            route = navRoute
        )

        val result = useCase(routeFile)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertTrue(state.hasNextTurn)
        assertEquals(166.8f, state.distanceToNextTurnM, 1.0f)
        assertEquals(90f, state.bearingToNextTurn, 1.0f)
    }

    @Test
    fun testBuildInitialNavigationState_bearingToNextTurnIsRelativeTurnBearingNotAzimuth() {
        val p0 = GeoPoint(48.8580, 2.2960)
        val p1 = GeoPoint(48.8580, 2.2940)
        val p2 = GeoPoint(48.8580, 2.2920)
        val p3 = GeoPoint(48.8600, 2.2920)

        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1, p2),
            distanceM = 300f,
            maneuver = Maneuver(type = ManeuverType.TURN_RIGHT, location = p2, relativeAngle = 90f)
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(p2, p3),
            distanceM = 200f,
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = p3, relativeAngle = 0f)
        )
        val navRoute = NavigationRoute(
            id = "westbound",
            name = "West Route",
            steps = listOf(step0, step1),
            totalDistanceM = 500f
        )
        val routeFile = RouteFile(
            id = "westbound",
            name = "West Route",
            filePath = "",
            points = listOf(p0, p1, p2, p3),
            totalDistanceKm = 0.5,
            isActive = true,
            route = navRoute
        )

        val result = useCase(routeFile)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        // bearingToNextTurn must be relative turn angle (90°), NOT compass azimuth (270°)
        assertNotEquals(270.0f, state.bearingToNextTurn, 5.0f)
        assertEquals(90.0f, state.bearingToNextTurn, 1.0f)
    }

    @Test
    fun testBuildInitialNavigationState_straightRouteWithNoTurns() {
        val points = listOf(
            GeoPoint(48.8500, 2.2940),
            GeoPoint(48.8510, 2.2940),
            GeoPoint(48.8520, 2.2940),
            GeoPoint(48.8530, 2.2940)
        )
        val step0 = RouteStep(
            index = 0,
            polyline = points,
            distanceM = 400f,
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = points.last())
        )
        val navRoute = NavigationRoute(
            id = "straight",
            name = "Straight Route",
            steps = listOf(step0),
            totalDistanceM = 400f
        )
        val routeFile = RouteFile(
            id = "straight",
            name = "Straight Route",
            filePath = "",
            points = points,
            totalDistanceKm = 0.4,
            isActive = true,
            route = navRoute
        )

        val result = useCase(routeFile)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        // No turns on route
        assertFalse(state.hasNextTurn)
        assertEquals(state.totalRemainingM, state.distanceToNextTurnM, 1.0f)
        assertEquals(0.0f, state.bearingToNextTurn, 0.001f)
        assertEquals(-1, state.lastNextTurnIndex)
    }

    @Test
    fun testBuildInitialNavigationState_lastNextTurnIndexInitialized() {
        val points = listOf(
            GeoPoint(48.8500, 2.2940),
            GeoPoint(48.8510, 2.2940),
            GeoPoint(48.8510, 2.2960)
        )
        val step0 = RouteStep(
            index = 0,
            polyline = listOf(points[0], points[1]),
            distanceM = 100f,
            maneuver = Maneuver(type = ManeuverType.TURN_RIGHT, location = points[1], relativeAngle = 90f)
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(points[1], points[2]),
            distanceM = 100f,
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = points[2])
        )
        val navRoute = NavigationRoute(
            id = "turn",
            name = "Turn Route",
            steps = listOf(step0, step1),
            totalDistanceM = 200f
        )
        val routeFile = RouteFile(
            id = "turn",
            name = "Turn Route",
            filePath = "",
            points = points,
            totalDistanceKm = 0.2,
            isActive = true,
            route = navRoute
        )

        val result = useCase(routeFile)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertEquals(0, state.lastNextTurnIndex)
    }

    @Test
    fun testBuildInitialNavigationState_lastNextTurnIndexWithIntermediateDepartStep() {
        val points = listOf(
            GeoPoint(48.8500, 2.2940),
            GeoPoint(48.8510, 2.2940),
            GeoPoint(48.8510, 2.2960)
        )
        val step0 = RouteStep(
            index = 0,
            polyline = listOf(points[0], points[1]),
            distanceM = 100f,
            maneuver = Maneuver(type = ManeuverType.DEPART, location = points[1])
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(points[1], points[2]),
            distanceM = 100f,
            maneuver = Maneuver(type = ManeuverType.TURN_RIGHT, location = points[2], relativeAngle = 90f)
        )
        val navRoute = NavigationRoute(
            id = "turn_next",
            name = "Turn Next Route",
            steps = listOf(step0, step1),
            totalDistanceM = 200f
        )
        val routeFile = RouteFile(
            id = "turn_next",
            name = "Turn Next Route",
            filePath = "",
            points = points,
            totalDistanceKm = 0.2,
            isActive = true,
            route = navRoute
        )

        val result = useCase(routeFile)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertEquals(1, state.lastNextTurnIndex)
        assertEquals(200f, state.distanceToNextTurnM, 0.1f)
    }

    @Test
    fun testBuildInitialNavigationState_emptyStepsReturnsFailure() {
        val navRoute = NavigationRoute(
            id = "empty",
            name = "Empty Route",
            steps = emptyList(),
            totalDistanceM = 0f
        )
        val result = useCase(navRoute)
        assertTrue("Empty steps must return failure", result.isFailure)
        assertEquals("Failed to build navigation state", result.exceptionOrNull()?.message)
    }

    @Test
    fun testBuildInitialNavigationState_singleStepWithTurnManeuver() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.8590, 2.2940)
        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1),
            distanceM = 110f,
            roadName = "Rue de Paris",
            maneuver = Maneuver(type = ManeuverType.TURN_LEFT, location = p1, relativeAngle = -90f)
        )
        val navRoute = NavigationRoute(
            id = "single_turn",
            name = "Single Turn Route",
            steps = listOf(step0),
            totalDistanceM = 110f
        )

        val result = useCase(navRoute)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertTrue(state.hasNextTurn)
        assertEquals(110f, state.distanceToNextTurnM, 0.01f)
        assertEquals(-90f, state.bearingToNextTurn, 0.01f)
        assertEquals("Rue de Paris", state.currentRoadName)
        assertEquals(0, state.lastNextTurnIndex)
    }

    @Test
    fun testBuildInitialNavigationState_multipleStraightStepsWithoutTurns() {
        val p0 = GeoPoint(48.850, 2.290)
        val p1 = GeoPoint(48.851, 2.290)
        val p2 = GeoPoint(48.852, 2.290)
        val p3 = GeoPoint(48.853, 2.290)

        val step0 = RouteStep(0, listOf(p0, p1), 100f, maneuver = Maneuver(ManeuverType.DEPART, p1))
        val step1 = RouteStep(1, listOf(p1, p2), 150f, maneuver = Maneuver(ManeuverType.CONTINUE, p2))
        val step2 = RouteStep(2, listOf(p2, p3), 200f, maneuver = Maneuver(ManeuverType.ARRIVE, p3))

        val navRoute = NavigationRoute("straight_multi", "Straight Multi", listOf(step0, step1, step2), 450f)
        val result = useCase(navRoute)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertFalse(state.hasNextTurn)
        assertEquals(450f, state.totalRemainingM, 0.01f)
        assertEquals(450f, state.distanceToNextTurnM, 0.01f)
        assertEquals(0f, state.bearingToNextTurn, 0.01f)
        assertEquals(-1, state.lastNextTurnIndex)
    }

    @Test
    fun testBuildInitialNavigationState_fieldCompleteness() {
        val p0 = GeoPoint(48.850, 2.290)
        val p1 = GeoPoint(48.851, 2.290)
        val step0 = RouteStep(0, listOf(p0, p1), 100f, roadName = "Start St", maneuver = Maneuver(ManeuverType.TURN_RIGHT, p1, relativeAngle = 90f))
        val navRoute = NavigationRoute("complete", "Complete Route", listOf(step0), 100f)

        val result = useCase(navRoute)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertEquals(0, state.currentStepIndex)
        assertEquals(0f, state.currentSpeedKmh, 0.01f)
        assertFalse(state.isOffTrack)
        assertTrue(state.isActive)
        assertTrue(state.isNavigating)
        assertEquals(-1, state.lastAlertedStepIndex)
        assertEquals(0, state.lastNextTurnIndex)
        assertEquals("Start St", state.currentRoadName)
        assertEquals(step0, state.activeStep)
        assertNull(state.nextStep)
        assertEquals(step0.maneuver, state.activeManeuver)
        assertTrue(state.hasSteps)
        assertEquals(navRoute, state.route)
    }

    @Test
    fun testBuildInitialNavigationState_routeFileWithoutRoute_createsFallbackArriveStep() {
        val points = listOf(GeoPoint(48.850, 2.290), GeoPoint(48.851, 2.290), GeoPoint(48.852, 2.290))
        val routeFile = RouteFile(
            id = "fallback",
            name = "Fallback Track",
            filePath = "",
            points = points,
            totalDistanceKm = 0.3,
            isActive = true,
            route = null
        )

        val result = useCase(routeFile)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()

        assertEquals(1, state.steps.size)
        assertEquals(ManeuverType.ARRIVE, state.steps[0].maneuver.type)
        assertEquals("Fallback Track", state.currentRoadName)
        assertEquals(300f, state.totalRemainingM, 1.0f)
        assertFalse(state.hasNextTurn)
    }

    @Test
    fun testBuildInitialNavigationState_missingMapFileReturnsFailure() {
        val points = listOf(GeoPoint(48.8500, 2.2940), GeoPoint(48.8510, 2.2940))
        val routeFile = RouteFile("nomap", "No Map", "", points, 0.1, true)

        regionRepository.activeFile = null

        val result = useCase(routeFile)
        assertTrue("Missing map file must fail", result.isFailure)
        assertEquals("Download a map for this region first", result.exceptionOrNull()?.message)
    }

    @Test
    fun testBuildInitialNavigationState_insufficientTrackPointsReturnsFailure() {
        val routeFile = RouteFile("single", "Single Point", "", listOf(GeoPoint(48.8500, 2.2940)), 0.0, true)

        val result = useCase(routeFile)
        assertTrue("Single track point must fail", result.isFailure)
    }

    @Test
    fun testBuildInitialNavigationState_fromNavigationRouteDirectly() {
        val points = listOf(GeoPoint(48.8500, 2.2940), GeoPoint(48.8510, 2.2940))
        val step = RouteStep(0, points, 100f, maneuver = Maneuver(ManeuverType.ARRIVE, points.last()))
        val navRoute = NavigationRoute("direct", "Direct Route", listOf(step), 100f)

        val result = useCase(navRoute)
        assertTrue(result.isSuccess)
        val state = result.getOrThrow()
        assertEquals(100f, state.totalRemainingM, 1.0f)
        assertTrue(state.isActive)
    }
}
