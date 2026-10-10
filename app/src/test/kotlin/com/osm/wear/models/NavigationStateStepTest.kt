package com.osm.wear.models

import org.junit.Assert.*
import org.junit.Test

class NavigationStateStepTest {

    @Test
    fun testNavigationState_defaults() {
        val state = NavigationState()

        assertTrue(state.steps.isEmpty())
        assertEquals(0, state.currentStepIndex)
        assertEquals(0f, state.distanceToNextTurnM, 0.001f)
        assertEquals(0f, state.bearingToNextTurn, 0.001f)
        assertEquals(0f, state.totalRemainingM, 0.001f)
        assertEquals(0f, state.currentSpeedKmh, 0.001f)
        assertFalse(state.isOffTrack)
        assertFalse(state.hasNextTurn)
        assertFalse(state.isActive)
        assertFalse(state.isNavigating)
        assertNull(state.currentRoadName)
        assertEquals(-1, state.lastAlertedStepIndex)
        assertEquals(-1, state.lastNextTurnIndex)
        assertNull(state.route)

        assertNull(state.activeStep)
        assertNull(state.nextStep)
        assertNull(state.activeManeuver)
        assertNull(state.nextManeuver)
        assertFalse(state.hasSteps)
    }

    @Test
    fun testNavigationState_stepResolution() {
        val p1 = GeoPoint(48.8580, 2.2940)
        val p2 = GeoPoint(48.8590, 2.2940)
        val p3 = GeoPoint(48.8590, 2.2960)

        val m1 = Maneuver(ManeuverType.TURN_RIGHT, p2, relativeAngle = 90f)
        val m2 = Maneuver(ManeuverType.ARRIVE, p3)

        val step1 = RouteStep(0, listOf(p1, p2), 100f, maneuver = m1)
        val step2 = RouteStep(1, listOf(p2, p3), 150f, maneuver = m2)

        val state = NavigationState(
            steps = listOf(step1, step2),
            currentStepIndex = 0,
            distanceToNextTurnM = 100f,
            bearingToNextTurn = 90f,
            totalRemainingM = 250f,
            hasNextTurn = true,
            isActive = true,
            isNavigating = true
        )

        assertTrue(state.hasSteps)
        assertTrue(state.isActive)
        assertTrue(state.isNavigating)
        assertEquals(step1, state.activeStep)
        assertEquals(step2, state.nextStep)
        assertEquals(m1, state.activeManeuver)
        assertEquals(m2, state.nextManeuver)

        // Advance to step 1
        val advanced = state.copy(currentStepIndex = 1, hasNextTurn = false)
        assertEquals(step2, advanced.activeStep)
        assertNull(advanced.nextStep)
        assertEquals(m2, advanced.activeManeuver)
        assertNull(advanced.nextManeuver)

        // Out of bounds index
        val outOfBounds = state.copy(currentStepIndex = 5)
        assertNull(outOfBounds.activeStep)
        assertNull(outOfBounds.nextStep)
        assertNull(outOfBounds.activeManeuver)
    }

    @Test
    fun testNavigationState_pureStepBasedProperties() {
        val p1 = GeoPoint(48.0, 2.0)
        val p2 = GeoPoint(48.1, 2.0)
        val m1 = Maneuver(ManeuverType.TURN_RIGHT, p2, relativeAngle = 90f, roadName = "Rue de Lyon")
        val step = RouteStep(0, listOf(p1, p2), 50f, roadName = "Rue de Lyon", maneuver = m1)

        val navRoute = NavigationRoute(
            id = "test_route",
            name = "Test Route",
            steps = listOf(step),
            totalDistanceM = 50f
        )

        val state = NavigationState(
            steps = listOf(step),
            currentStepIndex = 0,
            distanceToNextTurnM = 50f,
            bearingToNextTurn = 90f,
            totalRemainingM = 50f,
            currentSpeedKmh = 12.5f,
            isOffTrack = false,
            hasNextTurn = true,
            isActive = true,
            isNavigating = true,
            currentRoadName = "Rue de Lyon",
            lastAlertedStepIndex = -1,
            lastNextTurnIndex = 0,
            route = navRoute
        )

        assertTrue(state.isActive)
        assertTrue(state.isNavigating)
        assertEquals(1, state.steps.size)
        assertEquals(50f, state.distanceToNextTurnM, 0.01f)
        assertEquals(90f, state.bearingToNextTurn, 0.01f)
        assertEquals(50f, state.totalRemainingM, 0.01f)
        assertEquals(12.5f, state.currentSpeedKmh, 0.01f)
        assertEquals("Rue de Lyon", state.currentRoadName)
        assertEquals(0, state.lastNextTurnIndex)
        assertEquals(-1, state.lastAlertedStepIndex)
        assertEquals(navRoute, state.route)

        val deactivated = state.copy(isActive = false, isNavigating = false)
        assertFalse(deactivated.isActive)
        assertFalse(deactivated.isNavigating)
    }
}
