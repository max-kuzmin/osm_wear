package com.osm.wear.services

import com.osm.wear.models.GeoPoint
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class BearingMathTest {

    private fun shortestPathDiff(currentBearing: Float, targetBearing: Float): Float {
        return (((targetBearing - currentBearing + 180f) % 360f + 360f) % 360f - 180f)
    }

    @Test
    fun testGeoPoint_bearingTo_cardinalDirections() {
        val origin = GeoPoint(0.0, 0.0)

        // North: lat increases -> 0°
        assertEquals(0.0f, origin.bearingTo(GeoPoint(1.0, 0.0)), 0.01f)

        // East: lon increases -> 90°
        assertEquals(90.0f, origin.bearingTo(GeoPoint(0.0, 1.0)), 0.01f)

        // South: lat decreases -> 180°
        assertEquals(180.0f, origin.bearingTo(GeoPoint(-1.0, 0.0)), 0.01f)

        // West: lon decreases -> 270°
        assertEquals(270.0f, origin.bearingTo(GeoPoint(0.0, -1.0)), 0.01f)
    }

    @Test
    fun testGeoPoint_bearingTo_intercardinalDirections() {
        val origin = GeoPoint(0.0, 0.0)

        // Northeast: 45°
        assertEquals(45.0f, origin.bearingTo(GeoPoint(1.0, 1.0)), 0.5f)

        // Southeast: 135°
        assertEquals(135.0f, origin.bearingTo(GeoPoint(-1.0, 1.0)), 0.5f)

        // Southwest: 225°
        assertEquals(225.0f, origin.bearingTo(GeoPoint(-1.0, -1.0)), 0.5f)

        // Northwest: 315°
        assertEquals(315.0f, origin.bearingTo(GeoPoint(1.0, -1.0)), 0.5f)
    }

    @Test
    fun testGeoPoint_bearingTo_boundaryWrapNearNorth() {
        val origin = GeoPoint(0.0, 0.0)

        // Slightly East of North
        val justEast = origin.bearingTo(GeoPoint(1.0, 0.0001))
        assertTrue(justEast > 0.0f && justEast < 0.1f)

        // Slightly West of North
        val justWest = origin.bearingTo(GeoPoint(1.0, -0.0001))
        assertTrue(justWest > 359.9f && justWest < 360.0f)
    }

    @Test
    fun testGeoPoint_distanceTo_haversineAccuracy() {
        // 1 degree latitude at equator ≈ 111,195 meters
        val p1 = GeoPoint(0.0, 0.0)
        val p2 = GeoPoint(1.0, 0.0)

        val dist = p1.distanceTo(p2)
        assertEquals(111195.0, dist, 200.0)
    }

    @Test
    fun testHeadingUp_mapRotationAndLocationArrowAlignment() {
        for (b in 0..359) {
            val currentBearing = b.toFloat()
            val mapRotation = -currentBearing
            val arrowBearing = currentBearing
            val netScreenAngle = ((arrowBearing + mapRotation) % 360f + 360f) % 360f
            assertEquals(
                "In HEADING_UP, location arrow net orientation must point straight up (0°) for bearing $b",
                0.0f,
                netScreenAngle,
                0.001f
            )
        }
    }

    @Test
    fun testShortestPathBearingInterpolation() {
        assertEquals(20.0f, shortestPathDiff(350f, 10f), 0.001f)
        assertEquals(-20.0f, shortestPathDiff(10f, 350f), 0.001f)
        assertEquals(180.0f, abs(shortestPathDiff(0f, 180f)), 0.001f)
        assertEquals(20.0f, shortestPathDiff(170f, 190f), 0.001f)
    }
}
