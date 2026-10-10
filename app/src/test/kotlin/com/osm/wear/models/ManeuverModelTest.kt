package com.osm.wear.models

import org.junit.Assert.*
import org.junit.Test

class ManeuverModelTest {

    @Test
    fun testManeuverType_turnProperties() {
        // Turns
        assertTrue(ManeuverType.TURN_LEFT.isTurn)
        assertTrue(ManeuverType.TURN_RIGHT.isTurn)
        assertTrue(ManeuverType.SLIGHT_LEFT.isTurn)
        assertTrue(ManeuverType.SLIGHT_RIGHT.isTurn)
        assertTrue(ManeuverType.SHARP_LEFT.isTurn)
        assertTrue(ManeuverType.SHARP_RIGHT.isTurn)
        assertTrue(ManeuverType.U_TURN.isTurn)

        // Non-turns
        assertFalse(ManeuverType.DEPART.isTurn)
        assertFalse(ManeuverType.ARRIVE.isTurn)
        assertFalse(ManeuverType.CONTINUE.isTurn)

        // Direction classification
        assertTrue(ManeuverType.TURN_LEFT.isLeft)
        assertTrue(ManeuverType.SLIGHT_LEFT.isLeft)
        assertTrue(ManeuverType.SHARP_LEFT.isLeft)
        assertFalse(ManeuverType.TURN_RIGHT.isLeft)

        assertTrue(ManeuverType.TURN_RIGHT.isRight)
        assertTrue(ManeuverType.SLIGHT_RIGHT.isRight)
        assertTrue(ManeuverType.SHARP_RIGHT.isRight)
        assertFalse(ManeuverType.TURN_LEFT.isRight)

        assertTrue(ManeuverType.DEPART.isArriveOrDepart)
        assertTrue(ManeuverType.ARRIVE.isArriveOrDepart)
        assertFalse(ManeuverType.CONTINUE.isArriveOrDepart)
    }

    @Test
    fun testManeuverType_fromOsrmMapping() {
        assertEquals(ManeuverType.DEPART, ManeuverType.fromOsrm("depart", null))
        assertEquals(ManeuverType.ARRIVE, ManeuverType.fromOsrm("arrive", null))
        assertEquals(ManeuverType.U_TURN, ManeuverType.fromOsrm("turn", "uturn"))
        assertEquals(ManeuverType.SHARP_LEFT, ManeuverType.fromOsrm("turn", "sharp left"))
        assertEquals(ManeuverType.TURN_LEFT, ManeuverType.fromOsrm("turn", "left"))
        assertEquals(ManeuverType.SLIGHT_LEFT, ManeuverType.fromOsrm("turn", "slight left"))
        assertEquals(ManeuverType.CONTINUE, ManeuverType.fromOsrm("turn", "straight"))
        assertEquals(ManeuverType.SLIGHT_RIGHT, ManeuverType.fromOsrm("turn", "slight right"))
        assertEquals(ManeuverType.TURN_RIGHT, ManeuverType.fromOsrm("turn", "right"))
        assertEquals(ManeuverType.SHARP_RIGHT, ManeuverType.fromOsrm("turn", "sharp right"))

        // Angular fallbacks when modifier is null
        assertEquals(ManeuverType.TURN_LEFT, ManeuverType.fromOsrm("turn", null, -90f))
        assertEquals(ManeuverType.TURN_RIGHT, ManeuverType.fromOsrm("turn", null, 90f))
        assertEquals(ManeuverType.SLIGHT_LEFT, ManeuverType.fromOsrm("turn", null, -45f))
        assertEquals(ManeuverType.SLIGHT_RIGHT, ManeuverType.fromOsrm("turn", null, 45f))
        assertEquals(ManeuverType.SHARP_LEFT, ManeuverType.fromOsrm("turn", null, -135f))
        assertEquals(ManeuverType.SHARP_RIGHT, ManeuverType.fromOsrm("turn", null, 135f))
        assertEquals(ManeuverType.U_TURN, ManeuverType.fromOsrm("turn", null, 175f))
        assertEquals(ManeuverType.CONTINUE, ManeuverType.fromOsrm("turn", null, 5f))
    }

    @Test
    fun testRouteStep_properties() {
        val p1 = GeoPoint(48.8580, 2.2940)
        val p2 = GeoPoint(48.8590, 2.2940)
        val maneuver = Maneuver(
            type = ManeuverType.TURN_RIGHT,
            location = p2,
            bearingBefore = 0f,
            bearingAfter = 90f,
            relativeAngle = 90f,
            roadName = "Rue de Paris",
            instruction = "Turn right onto Rue de Paris"
        )
        val step = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 111f,
            durationS = 15f,
            roadName = "Avenue Gustave Eiffel",
            maneuver = maneuver
        )

        assertEquals(0, step.index)
        assertEquals(p1, step.startPoint)
        assertEquals(p2, step.endPoint)
        assertEquals(111f, step.distanceM, 0.01f)
        assertEquals(15f, step.durationS, 0.01f)
        assertEquals("Avenue Gustave Eiffel", step.roadName)
        assertEquals(maneuver, step.maneuver)
        assertTrue(step.maneuver.isTurn)
    }

    @Test
    fun testNavigationRoute_fullPolylineDeduplication() {
        val p1 = GeoPoint(48.8580, 2.2940)
        val p2 = GeoPoint(48.8590, 2.2940)
        val p3 = GeoPoint(48.8590, 2.2960)

        val step1 = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 100f,
            maneuver = Maneuver(ManeuverType.TURN_RIGHT, p2)
        )
        val step2 = RouteStep(
            index = 1,
            polyline = listOf(p2, p3), // p2 shared at junction
            distanceM = 150f,
            maneuver = Maneuver(ManeuverType.ARRIVE, p3)
        )

        val route = NavigationRoute(
            id = "test_route",
            name = "Test Route",
            steps = listOf(step1, step2),
            totalDistanceM = 250f,
            totalDurationS = 30f
        )

        assertEquals("test_route", route.id)
        assertEquals(2, route.steps.size)
        assertEquals(250f, route.totalDistanceM, 0.01f)
        // Deduplication removes duplicate junction point p2
        assertEquals(listOf(p1, p2, p3), route.fullPolyline)
    }
}

