package com.osm.wear.presentation.screens

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationState
import com.osm.wear.models.RouteStep
import org.junit.Assert.*
import org.junit.Test

class NavigationOverlayTest {

    private fun formatDistance(distM: Float): String {
        return if (distM >= 1000f) "%.1f km".format(distM / 1000f) else "${distM.toInt()} m"
    }

    private fun resolveTargetRoad(navState: NavigationState): String? {
        return navState.activeManeuver?.roadName ?: navState.currentRoadName
    }

    @Test
    fun testNavigationOverlay_distanceFormattingUnder1000m() {
        assertEquals("0 m", formatDistance(0f))
        assertEquals("25 m", formatDistance(25.4f))
        assertEquals("50 m", formatDistance(50f))
        assertEquals("300 m", formatDistance(300.9f))
        assertEquals("999 m", formatDistance(999.4f))
    }

    @Test
    fun testNavigationOverlay_distanceFormattingKilometers() {
        assertEquals("1.0 km", formatDistance(1000f))
        assertEquals("1.2 km", formatDistance(1200f))
        assertEquals("2.5 km", formatDistance(2540f))
        assertEquals("10.0 km", formatDistance(10000f))
    }

    @Test
    fun testNavigationOverlay_roadNameResolution_prefersActiveManeuverRoadName() {
        val junctionPt = GeoPoint(48.8580, 2.2940)
        val maneuver = Maneuver(
            type = ManeuverType.TURN_RIGHT,
            location = junctionPt,
            relativeAngle = 90f,
            roadName = "Target Boulevard"
        )
        val step = RouteStep(
            index = 0,
            polyline = listOf(GeoPoint(48.8570, 2.2940), junctionPt),
            distanceM = 100f,
            roadName = "Current Street",
            maneuver = maneuver
        )

        val navState = NavigationState(
            steps = listOf(step),
            currentStepIndex = 0,
            currentRoadName = "Current Street"
        )

        val resolvedRoad = resolveTargetRoad(navState)
        assertEquals("Target Boulevard", resolvedRoad)
    }

    @Test
    fun testNavigationOverlay_roadNameResolution_fallsBackToCurrentRoadNameWhenManeuverRoadNameNull() {
        val junctionPt = GeoPoint(48.8580, 2.2940)
        val maneuver = Maneuver(
            type = ManeuverType.TURN_RIGHT,
            location = junctionPt,
            relativeAngle = 90f,
            roadName = null
        )
        val step = RouteStep(
            index = 0,
            polyline = listOf(GeoPoint(48.8570, 2.2940), junctionPt),
            distanceM = 100f,
            roadName = "Current Street",
            maneuver = maneuver
        )

        val navState = NavigationState(
            steps = listOf(step),
            currentStepIndex = 0,
            currentRoadName = "Current Street"
        )

        val resolvedRoad = resolveTargetRoad(navState)
        assertEquals("Current Street", resolvedRoad)
    }

    @Test
    fun testNavigationOverlay_roadNameResolution_nullWhenBothNull() {
        val navState = NavigationState(
            steps = emptyList(),
            currentStepIndex = 0,
            currentRoadName = null
        )

        val resolvedRoad = resolveTargetRoad(navState)
        assertNull(resolvedRoad)
    }

    @Test
    fun testNavigationOverlay_turnDirectionAngleConsistency() {
        // TURN_LEFT: bearingToNextTurn = -90f
        val leftState = NavigationState(
            hasNextTurn = true,
            bearingToNextTurn = -90f
        )
        assertTrue(leftState.hasNextTurn)
        assertEquals(-90f, leftState.bearingToNextTurn, 0.01f)

        // TURN_RIGHT: bearingToNextTurn = 90f
        val rightState = NavigationState(
            hasNextTurn = true,
            bearingToNextTurn = 90f
        )
        assertTrue(rightState.hasNextTurn)
        assertEquals(90f, rightState.bearingToNextTurn, 0.01f)

        // U_TURN: bearingToNextTurn = 180f
        val uturnState = NavigationState(
            hasNextTurn = true,
            bearingToNextTurn = 180f
        )
        assertTrue(uturnState.hasNextTurn)
        assertEquals(180f, uturnState.bearingToNextTurn, 0.01f)

        // Destination ARRIVE: hasNextTurn = false, bearingToNextTurn = 0f
        val arriveState = NavigationState(
            hasNextTurn = false,
            bearingToNextTurn = 0f
        )
        assertFalse(arriveState.hasNextTurn)
        assertEquals(0f, arriveState.bearingToNextTurn, 0.01f)
    }

    @Test
    fun testNavigationOverlay_offTrackFlagReflectsState() {
        val onTrackState = NavigationState(isOffTrack = false)
        assertFalse(onTrackState.isOffTrack)

        val offTrackState = NavigationState(isOffTrack = true)
        assertTrue(offTrackState.isOffTrack)
    }

    @Test
    fun testNavigationOverlay_fullStateBindingSynchronization() {
        val junctionPt = GeoPoint(48.8580, 2.2940)
        val maneuver = Maneuver(
            type = ManeuverType.TURN_RIGHT,
            location = junctionPt,
            relativeAngle = 90f,
            roadName = "Champs-Elysees"
        )
        val step = RouteStep(
            index = 0,
            polyline = listOf(GeoPoint(48.8570, 2.2940), junctionPt),
            distanceM = 150f,
            roadName = "Avenue Montaigne",
            maneuver = maneuver
        )

        val state = NavigationState(
            steps = listOf(step),
            currentStepIndex = 0,
            distanceToNextTurnM = 150f,
            bearingToNextTurn = 90f,
            totalRemainingM = 500f,
            currentSpeedKmh = 15f,
            isOffTrack = false,
            hasNextTurn = true,
            isActive = true,
            currentRoadName = "Avenue Montaigne",
            lastAlertedStepIndex = -1,
            lastNextTurnIndex = 0
        )

        assertEquals("150 m", formatDistance(state.distanceToNextTurnM))
        assertEquals("Champs-Elysees", resolveTargetRoad(state))
        assertEquals(90f, state.bearingToNextTurn, 0.01f)
        assertTrue(state.hasNextTurn)
        assertFalse(state.isOffTrack)
        assertTrue(state.isActive)
    }
}
