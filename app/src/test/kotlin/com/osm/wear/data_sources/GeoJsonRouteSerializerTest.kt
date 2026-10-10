package com.osm.wear.data_sources

import com.osm.wear.data_sources.geojson.GeoJsonRouteParser
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteStep
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests targeting [GeoJsonRouteParser.serialize] to verify RFC 7946 compliance,
 * strict coordinate ordering, 3D elevation formatting, role assignments, maneuver property
 * encoding, JSON string escaping, null property handling, and lossless round-trip equality.
 */
class GeoJsonRouteSerializerTest {

    private fun createTestRoute(
        id: String = "route_100",
        name: String = "Test Scenic Route",
        includeSteps: Boolean = true
    ): NavigationRoute {
        val p1 = GeoPoint(37.7749, -122.4194, 15.0)
        val p2 = GeoPoint(37.7755, -122.4180, 20.0)
        val p3 = GeoPoint(37.7760, -122.4170, 25.0)

        val steps = if (!includeSteps) emptyList() else listOf(
            RouteStep(
                index = 0,
                polyline = listOf(p1, p2),
                distanceM = 150f,
                durationS = 60f,
                roadName = "Pine Street",
                maneuver = Maneuver(
                    type = ManeuverType.TURN_LEFT,
                    location = p2,
                    bearingBefore = 90f,
                    bearingAfter = 0f,
                    relativeAngle = -90f,
                    roadName = "Oak Avenue",
                    instruction = "Turn left onto Oak Avenue"
                )
            ),
            RouteStep(
                index = 1,
                polyline = listOf(p2, p3),
                distanceM = 200f,
                durationS = 80f,
                roadName = "Oak Avenue",
                maneuver = Maneuver(
                    type = ManeuverType.ARRIVE,
                    location = p3,
                    bearingBefore = 0f,
                    bearingAfter = 0f,
                    relativeAngle = 0f,
                    roadName = "Oak Avenue",
                    instruction = "Arrive at destination"
                )
            )
        )

        return NavigationRoute(
            id = id,
            name = name,
            steps = steps,
            totalDistanceM = 350f,
            totalDurationS = 140f
        )
    }

    @Test
    fun testSerialize_rfc7946Compliance_validStructure() {
        val route = createTestRoute()
        val json = GeoJsonRouteParser.serialize(route)

        // RFC 7946: Top-level object must be a FeatureCollection
        assertTrue("Must start with FeatureCollection type", json.startsWith("{\"type\":\"FeatureCollection\","))
        assertTrue("Must declare properties", json.contains("\"properties\":{"))
        assertTrue("Must contain id", json.contains("\"id\":\"route_100\""))
        assertTrue("Must contain name", json.contains("\"name\":\"Test Scenic Route\""))
        assertTrue("Must contain totalDistanceM", json.contains("\"totalDistanceM\":350.0"))
        assertTrue("Must contain totalDurationS", json.contains("\"totalDurationS\":140.0"))
        assertTrue("Must specify creator osm_wear", json.contains("\"creator\":\"osm_wear\""))
        assertTrue("Must specify version 1.0", json.contains("\"version\":\"1.0\""))
        assertTrue("Must declare features array", json.contains("\"features\":["))
        assertTrue("Must close FeatureCollection", json.endsWith("]}"))

        // Parsable by standard parser
        val parsed = GeoJsonRouteParser.parse(json)
        assertNotNull("Serialized string must parse cleanly", parsed)
        assertEquals("route_100", parsed!!.id)
        assertEquals("Test Scenic Route", parsed.name)
    }

    @Test
    fun testSerialize_coordinateOrder_strictlyLonBeforeLat() {
        // Longitude: -122.4194, Latitude: 37.7749
        val p1 = GeoPoint(lat = 37.7749, lon = -122.4194)
        val p2 = GeoPoint(lat = 37.7755, lon = -122.4180)

        val step = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 100f,
            durationS = 40f,
            roadName = "Main St",
            maneuver = Maneuver(
                type = ManeuverType.DEPART,
                location = p1,
                bearingBefore = 0f,
                bearingAfter = 45f,
                relativeAngle = 0f,
                instruction = "Depart"
            )
        )

        val route = NavigationRoute(
            id = "coords_test",
            name = "Coords Test",
            steps = listOf(step),
            totalDistanceM = 100f,
            totalDurationS = 40f
        )

        val json = GeoJsonRouteParser.serialize(route)

        // RFC 7946 Section 3.1.1: Coordinates must be ordered [longitude, latitude]
        assertTrue("Must serialize [lon, lat] for coordinates", json.contains("[-122.4194,37.7749]"))
        assertTrue("Must serialize [lon, lat] for maneuver location", json.contains("\"location\":[-122.4194,37.7749]"))
        assertFalse("Must NEVER invert coordinates to [lat, lon]", json.contains("[37.7749,-122.4194]"))
    }

    @Test
    fun testSerialize_3dCoordinates_includesElevationOnlyWhenNonZero() {
        val pZeroEle = GeoPoint(lat = 51.5000, lon = -0.1200, ele = 0.0)
        val pPositiveEle = GeoPoint(lat = 51.5010, lon = -0.1210, ele = 150.5)
        val pNegativeEle = GeoPoint(lat = 51.5020, lon = -0.1220, ele = -42.0)

        val step = RouteStep(
            index = 0,
            polyline = listOf(pZeroEle, pPositiveEle, pNegativeEle),
            distanceM = 300f,
            durationS = 120f,
            roadName = "Hill Path",
            maneuver = Maneuver(
                type = ManeuverType.CONTINUE,
                location = pNegativeEle,
                instruction = "Continue"
            )
        )

        val route = NavigationRoute(
            id = "ele_test",
            name = "Elevation Test",
            steps = listOf(step),
            totalDistanceM = 300f,
            totalDurationS = 120f
        )

        val json = GeoJsonRouteParser.serialize(route)

        // Zero elevation: omitted (2D [lon, lat])
        assertTrue("Zero elevation must output 2D coordinate [lon, lat]", json.contains("[-0.12,51.5]"))
        assertFalse("Zero elevation must not append 0.0", json.contains("[-0.12,51.5,0.0]"))

        // Positive elevation: included
        assertTrue("Positive elevation must include third coordinate", json.contains("[-0.121,51.501,150.5]"))

        // Negative elevation: included
        assertTrue("Negative elevation must include third coordinate", json.contains("[-0.122,51.502,-42.0]"))
        assertTrue("Maneuver location with negative elevation must include third coordinate", json.contains("\"location\":[-0.122,51.502,-42.0]"))
    }

    @Test
    fun testSerialize_featureRoles_routeAndSteps() {
        val route = createTestRoute()
        val json = GeoJsonRouteParser.serialize(route)

        // Feature 0: Overall route
        assertTrue("Feature 0 must have role 'route'", json.contains("\"properties\":{\"role\":\"route\""))

        // Features 1..N: Step features with sequential index
        assertTrue("Step 0 must have role 'step' and index 0", json.contains("\"role\":\"step\",\"index\":0"))
        assertTrue("Step 1 must have role 'step' and index 1", json.contains("\"role\":\"step\",\"index\":1"))
    }

    @Test
    fun testSerialize_maneuverProperties_completeFieldEncoding() {
        val p1 = GeoPoint(48.8584, 2.2945)
        val p2 = GeoPoint(48.8600, 2.2960)

        val maneuver = Maneuver(
            type = ManeuverType.TURN_RIGHT,
            location = p2,
            bearingBefore = 45f,
            bearingAfter = 135f,
            relativeAngle = 90f,
            roadName = "Avenue Montaigne",
            instruction = "Turn right onto Avenue Montaigne"
        )

        val step = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 250f,
            durationS = 100f,
            roadName = "Rue de la Paix",
            maneuver = maneuver
        )

        val route = NavigationRoute(
            id = "maneuver_test",
            name = "Maneuver Route",
            steps = listOf(step),
            totalDistanceM = 250f,
            totalDurationS = 100f
        )

        val json = GeoJsonRouteParser.serialize(route)

        assertTrue("Must serialize maneuver type", json.contains("\"type\":\"TURN_RIGHT\""))
        assertTrue("Must serialize maneuver location", json.contains("\"location\":[2.296,48.86]"))
        assertTrue("Must serialize bearingBefore", json.contains("\"bearingBefore\":45.0"))
        assertTrue("Must serialize bearingAfter", json.contains("\"bearingAfter\":135.0"))
        assertTrue("Must serialize relativeAngle", json.contains("\"relativeAngle\":90.0"))
        assertTrue("Must serialize maneuver roadName", json.contains("\"roadName\":\"Avenue Montaigne\""))
        assertTrue("Must serialize instruction", json.contains("\"instruction\":\"Turn right onto Avenue Montaigne\""))
    }

    @Test
    fun testSerialize_stringEscaping_quotesNewlinesTabsAndBackslashes() {
        val trickyName = "Tour with \"double quotes\" and \\backslashes\\ and newline\nin name"
        val trickyInstruction = "Instruction with\ttabs, \\\\escapes\\\\, and \"quotes\""

        val p1 = GeoPoint(10.0, 20.0)
        val p2 = GeoPoint(10.1, 20.1)

        val step = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 100f,
            durationS = 40f,
            roadName = "Road \"Special\"",
            maneuver = Maneuver(
                type = ManeuverType.ARRIVE,
                location = p2,
                roadName = "Road \"Special\"",
                instruction = trickyInstruction
            )
        )

        val route = NavigationRoute(
            id = "escaped_test",
            name = trickyName,
            steps = listOf(step),
            totalDistanceM = 100f,
            totalDurationS = 40f
        )

        val json = GeoJsonRouteParser.serialize(route)

        // Raw JSON must have properly escaped characters
        assertTrue("Must escape double quotes in JSON", json.contains("\\\"double quotes\\\""))
        assertTrue("Must escape backslashes in JSON", json.contains("\\\\backslashes\\\\"))
        assertTrue("Must escape newlines as \\n", json.contains("\\n"))
        assertTrue("Must escape tabs as \\t", json.contains("\\t"))

        // Must parse back lossless
        val parsed = GeoJsonRouteParser.parse(json)
        assertNotNull("Serialized string with escaped characters must parse cleanly", parsed)
        assertEquals(trickyName, parsed!!.name)
        assertEquals("Road \"Special\"", parsed.steps[0].roadName)
        assertEquals(trickyInstruction, parsed.steps[0].maneuver.instruction)
    }

    @Test
    fun testSerialize_routeWithNoSteps_producesValidFeatureCollection() {
        val p1 = GeoPoint(37.7749, -122.4194)
        val p2 = GeoPoint(37.7755, -122.4180)

        // Fallback or empty-steps route with only fullPolyline
        val routeWithoutSteps = NavigationRoute(
            id = "no_steps_route",
            name = "Empty Steps Route",
            steps = emptyList(),
            totalDistanceM = 150f,
            totalDurationS = 60f
        )

        val json = GeoJsonRouteParser.serialize(routeWithoutSteps)

        assertTrue(json.startsWith("{\"type\":\"FeatureCollection\","))
        assertTrue(json.contains("\"role\":\"route\""))
        assertFalse("Must not contain step features", json.contains("\"role\":\"step\""))
        assertTrue(json.endsWith("]}"))

        // Parsing should synthesize destination step for routes without explicit steps
        val parsed = GeoJsonRouteParser.parse(json)
        // With empty coordinates, parser returns route or null gracefully
        // If route has no points in fullPolyline, parser returns null safely
    }

    @Test
    fun testSerialize_nullRoadNames_omitsRoadNameKeyCleanly() {
        val p1 = GeoPoint(37.7749, -122.4194)
        val p2 = GeoPoint(37.7755, -122.4180)

        val stepWithNullRoad = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 100f,
            durationS = 40f,
            roadName = null,
            maneuver = Maneuver(
                type = ManeuverType.DEPART,
                location = p1,
                roadName = null,
                instruction = "Depart"
            )
        )

        val route = NavigationRoute(
            id = "null_road_test",
            name = "Null Road Route",
            steps = listOf(stepWithNullRoad),
            totalDistanceM = 100f,
            totalDurationS = 40f
        )

        val json = GeoJsonRouteParser.serialize(route)

        // Null roadName must be completely omitted, never serialized as "roadName":"null" or "roadName":null
        assertFalse("Must not serialize \"roadName\":null", json.contains("\"roadName\":null"))
        assertFalse("Must not serialize \"roadName\":\"null\"", json.contains("\"roadName\":\"null\""))
    }

    @Test
    fun testSerialize_losslessRoundTrip_matchesOriginalRoutePrecisely() {
        val originalRoute = createTestRoute(id = "roundtrip_route_999", name = "Full Roundtrip Validation")

        val serializedJson = GeoJsonRouteParser.serialize(originalRoute)
        val roundTripRoute = GeoJsonRouteParser.parse(serializedJson)

        assertNotNull("Round-trip route must not be null", roundTripRoute)
        assertEquals(originalRoute.id, roundTripRoute!!.id)
        assertEquals(originalRoute.name, roundTripRoute.name)
        assertEquals(originalRoute.totalDistanceM, roundTripRoute.totalDistanceM, 0.01f)
        assertEquals(originalRoute.totalDurationS, roundTripRoute.totalDurationS, 0.01f)
        assertEquals(originalRoute.steps.size, roundTripRoute.steps.size)

        for (i in originalRoute.steps.indices) {
            val sOrig = originalRoute.steps[i]
            val sParsed = roundTripRoute.steps[i]

            assertEquals(sOrig.index, sParsed.index)
            assertEquals(sOrig.distanceM, sParsed.distanceM, 0.01f)
            assertEquals(sOrig.durationS, sParsed.durationS, 0.01f)
            assertEquals(sOrig.roadName, sParsed.roadName)
            assertEquals(sOrig.polyline.size, sParsed.polyline.size)

            for (j in sOrig.polyline.indices) {
                assertEquals(sOrig.polyline[j].lat, sParsed.polyline[j].lat, 0.000001)
                assertEquals(sOrig.polyline[j].lon, sParsed.polyline[j].lon, 0.000001)
                assertEquals(sOrig.polyline[j].ele, sParsed.polyline[j].ele, 0.001)
            }

            val mOrig = sOrig.maneuver
            val mParsed = sParsed.maneuver

            assertEquals(mOrig.type, mParsed.type)
            assertEquals(mOrig.location.lat, mParsed.location.lat, 0.000001)
            assertEquals(mOrig.location.lon, mParsed.location.lon, 0.000001)
            assertEquals(mOrig.bearingBefore, mParsed.bearingBefore, 0.01f)
            assertEquals(mOrig.bearingAfter, mParsed.bearingAfter, 0.01f)
            assertEquals(mOrig.relativeAngle, mParsed.relativeAngle, 0.01f)
            assertEquals(mOrig.roadName, mParsed.roadName)
            assertEquals(mOrig.instruction, mParsed.instruction)
        }
    }
}
