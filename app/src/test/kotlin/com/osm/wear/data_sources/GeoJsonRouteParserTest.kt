package com.osm.wear.data_sources

import com.osm.wear.data_sources.geojson.GeoJsonRouteParser
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteStep
import org.junit.Assert.*
import org.junit.Test

class GeoJsonRouteParserTest {

    @Test
    fun testLosslessRoundTrip_preservesAllManeuversAndCoordinates() {
        val p1 = GeoPoint(37.7749, -122.4194, 15.0)
        val p2 = GeoPoint(37.7749, -122.4150, 14.5)
        val p3 = GeoPoint(37.7790, -122.4150, 12.0)
        val p4 = GeoPoint(37.7810, -122.4130, 10.0)

        val step1 = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 450.0f,
            durationS = 180.0f,
            roadName = "Market Street",
            maneuver = Maneuver(
                type = ManeuverType.TURN_LEFT,
                location = p2,
                bearingBefore = 90.0f,
                bearingAfter = 0.0f,
                relativeAngle = -90.0f,
                roadName = "4th Street",
                instruction = "In 450 meters, turn left onto 4th Street"
            )
        )

        val step2 = RouteStep(
            index = 1,
            polyline = listOf(p2, p3),
            distanceM = 800.0f,
            durationS = 300.0f,
            roadName = "4th Street",
            maneuver = Maneuver(
                type = ManeuverType.SLIGHT_RIGHT,
                location = p3,
                bearingBefore = 0.0f,
                bearingAfter = 45.0f,
                relativeAngle = 45.0f,
                roadName = "Mission Way",
                instruction = "Bear slight right onto Mission Way"
            )
        )

        val step3 = RouteStep(
            index = 2,
            polyline = listOf(p3, p4),
            distanceM = 300.0f,
            durationS = 120.0f,
            roadName = "Mission Way",
            maneuver = Maneuver(
                type = ManeuverType.ARRIVE,
                location = p4,
                bearingBefore = 45.0f,
                bearingAfter = 45.0f,
                relativeAngle = 0.0f,
                roadName = "Mission Way",
                instruction = "Arrive at destination"
            )
        )

        val originalRoute = NavigationRoute(
            id = "route_1728512345",
            name = "Market to Mission",
            steps = listOf(step1, step2, step3),
            totalDistanceM = 1550.0f,
            totalDurationS = 600.0f
        )

        val json = GeoJsonRouteParser.serialize(originalRoute)
        assertTrue("JSON should contain FeatureCollection", json.contains("\"FeatureCollection\""))
        assertTrue("JSON should contain route role", json.contains("\"role\":\"route\""))
        assertTrue("JSON should contain step role", json.contains("\"role\":\"step\""))
        assertTrue("JSON should contain TURN_LEFT", json.contains("TURN_LEFT"))
        assertTrue("JSON should contain SLIGHT_RIGHT", json.contains("SLIGHT_RIGHT"))
        assertTrue("JSON should contain ARRIVE", json.contains("ARRIVE"))

        val parsedRoute = GeoJsonRouteParser.parse(json)
        assertNotNull("Parsed route should not be null", parsedRoute)
        assertEquals("route_1728512345", parsedRoute!!.id)
        assertEquals("Market to Mission", parsedRoute.name)
        assertEquals(1550.0f, parsedRoute.totalDistanceM, 0.01f)
        assertEquals(600.0f, parsedRoute.totalDurationS, 0.01f)
        assertEquals(3, parsedRoute.steps.size)

        // Step 0 verification
        val rStep0 = parsedRoute.steps[0]
        assertEquals(0, rStep0.index)
        assertEquals(450.0f, rStep0.distanceM, 0.01f)
        assertEquals(180.0f, rStep0.durationS, 0.01f)
        assertEquals("Market Street", rStep0.roadName)
        assertEquals(ManeuverType.TURN_LEFT, rStep0.maneuver.type)
        assertEquals(-90.0f, rStep0.maneuver.relativeAngle, 0.01f)
        assertEquals(90.0f, rStep0.maneuver.bearingBefore, 0.01f)
        assertEquals(0.0f, rStep0.maneuver.bearingAfter, 0.01f)
        assertEquals("4th Street", rStep0.maneuver.roadName)
        assertEquals("In 450 meters, turn left onto 4th Street", rStep0.maneuver.instruction)
        assertEquals(p2.lat, rStep0.maneuver.location.lat, 0.0001)
        assertEquals(p2.lon, rStep0.maneuver.location.lon, 0.0001)
        assertEquals(p2.ele, rStep0.maneuver.location.ele, 0.0001)

        // Step 1 verification
        val rStep1 = parsedRoute.steps[1]
        assertEquals(1, rStep1.index)
        assertEquals(ManeuverType.SLIGHT_RIGHT, rStep1.maneuver.type)
        assertEquals(45.0f, rStep1.maneuver.relativeAngle, 0.01f)
        assertEquals("Mission Way", rStep1.maneuver.roadName)

        // Step 2 verification
        val rStep2 = parsedRoute.steps[2]
        assertEquals(2, rStep2.index)
        assertEquals(ManeuverType.ARRIVE, rStep2.maneuver.type)
        assertEquals("Arrive at destination", rStep2.maneuver.instruction)

        // Full polyline verification
        val poly = parsedRoute.fullPolyline
        assertEquals(4, poly.size)
        assertEquals(p1.lat, poly[0].lat, 0.0001)
        assertEquals(p4.lat, poly[3].lat, 0.0001)
    }

    @Test
    fun testParse_embeddedStepsFormat() {
        val json = """
        {
          "type": "Feature",
          "properties": {
            "id": "osrm_route_101",
            "name": "Downtown Loop",
            "totalDistanceM": 900.0,
            "totalDurationS": 400.0,
            "steps": [
              {
                "index": 0,
                "distanceM": 400.0,
                "durationS": 150.0,
                "roadName": "Main St",
                "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [10.0, 50.0],
                    [10.1, 50.0]
                  ]
                },
                "maneuver": {
                  "type": "TURN_RIGHT",
                  "location": [10.1, 50.0],
                  "bearingBefore": 90.0,
                  "bearingAfter": 180.0,
                  "relativeAngle": 90.0,
                  "instruction": "Turn right"
                }
              },
              {
                "index": 1,
                "distanceM": 500.0,
                "durationS": 250.0,
                "roadName": "Broadway",
                "geometry": {
                  "type": "LineString",
                  "coordinates": [
                    [10.1, 50.0],
                    [10.1, 49.9]
                  ]
                },
                "maneuver": {
                  "type": "ARRIVE",
                  "location": [10.1, 49.9],
                  "instruction": "Arrive"
                }
              }
            ]
          }
        }
        """.trimIndent()

        val route = GeoJsonRouteParser.parse(json)
        assertNotNull(route)
        assertEquals("osrm_route_101", route!!.id)
        assertEquals("Downtown Loop", route.name)
        assertEquals(2, route.steps.size)
        assertEquals(ManeuverType.TURN_RIGHT, route.steps[0].maneuver.type)
        assertEquals(ManeuverType.ARRIVE, route.steps[1].maneuver.type)
        assertEquals(900.0f, route.totalDistanceM, 0.1f)
    }

    @Test
    fun testFallback_thirdPartyLineString_synthesizesDestinationArrivalStep() {
        // Standard Strava / GeoJSON.io export without steps
        val json = """
        {
          "type": "FeatureCollection",
          "features": [
            {
              "type": "Feature",
              "geometry": {
                "type": "LineString",
                "coordinates": [
                  [-122.4194, 37.7749],
                  [-122.4150, 37.7749],
                  [-122.4150, 37.7790]
                ]
              },
              "properties": {
                "name": "Strava Run"
              }
            }
          ]
        }
        """.trimIndent()

        val route = GeoJsonRouteParser.parse(json, defaultName = "Strava Fallback")
        assertNotNull(route)
        assertEquals("Strava Fallback", route!!.name)
        assertEquals(1, route.steps.size)

        val fallbackStep = route.steps[0]
        assertEquals(3, fallbackStep.polyline.size)
        assertEquals(ManeuverType.ARRIVE, fallbackStep.maneuver.type)
        assertEquals("Arrive at destination", fallbackStep.maneuver.instruction)
        assertEquals(37.7790, fallbackStep.maneuver.location.lat, 0.0001)
        assertEquals(-122.4150, fallbackStep.maneuver.location.lon, 0.0001)

        // Distance should be calculated from haversine sum (> 0)
        assertTrue("Distance must be positive", route.totalDistanceM > 500f)
    }

    @Test
    fun testFallback_multiLineString_concatenatesSegmentsWithoutDuplicates() {
        val json = """
        {
          "type": "MultiLineString",
          "coordinates": [
            [
              [2.2945, 48.8584],
              [2.3000, 48.8584]
            ],
            [
              [2.3000, 48.8584],
              [2.3050, 48.8590]
            ]
          ]
        }
        """.trimIndent()

        val route = GeoJsonRouteParser.parse(json, defaultName = "MultiLine Track")
        assertNotNull(route)
        assertEquals("MultiLine Track", route!!.name)
        assertEquals(1, route.steps.size)
        // Midpoint [2.3000, 48.8584] should be deduplicated
        assertEquals(3, route.fullPolyline.size)
    }

    @Test
    fun testParse_singleFeatureDirectGeometry() {
        val json = """
        {
          "type": "Feature",
          "geometry": {
            "type": "LineString",
            "coordinates": [
              [13.4050, 52.5200],
              [13.4080, 52.5220]
            ]
          },
          "properties": {
            "name": "Berlin Walk"
          }
        }
        """.trimIndent()

        val route = GeoJsonRouteParser.parse(json)
        assertNotNull(route)
        assertEquals("Berlin Walk", route!!.name)
        assertEquals(2, route.fullPolyline.size)
        assertEquals(52.5200, route.fullPolyline[0].lat, 0.0001)
    }

    @Test
    fun testParse_malformedAndCorruptedJson_returnsNullSafely() {
        assertNull(GeoJsonRouteParser.parse(""))
        assertNull(GeoJsonRouteParser.parse("   "))
        assertNull(GeoJsonRouteParser.parse("not json at all"))
        assertNull(GeoJsonRouteParser.parse("{\"type\": \"FeatureCollection\"")) // Truncated
        assertNull(GeoJsonRouteParser.parse("{\"type\": \"UnknownType\"}"))
        assertNull(GeoJsonRouteParser.parse("{\"type\": \"FeatureCollection\", \"features\": []}")) // Empty features
    }

    @Test
    fun testParse_invalidCoordinates_returnsNullSafely() {
        // Out-of-bounds latitude (999.0)
        val invalidCoords = """
        {
          "type": "LineString",
          "coordinates": [
            [10.0, 999.0],
            [10.1, 50.0]
          ]
        }
        """.trimIndent()
        // Only 1 valid coordinate remains -> total valid < 2 -> should return null
        assertNull(GeoJsonRouteParser.parse(invalidCoords))
    }

    @Test
    fun testLosslessRoundTrip_handlesEscapedCharactersInStrings() {
        val p1 = GeoPoint(10.0, 20.0)
        val p2 = GeoPoint(10.1, 20.1)

        val step = RouteStep(
            index = 0,
            polyline = listOf(p1, p2),
            distanceM = 100f,
            roadName = "O'Connor \"Express\" Way\\Path",
            maneuver = Maneuver(
                type = ManeuverType.TURN_RIGHT,
                location = p2,
                roadName = "O'Connor \"Express\" Way\\Path",
                instruction = "Turn right onto \"Express\" Way\nCaution!"
            )
        )

        val route = NavigationRoute(
            id = "escaped_test",
            name = "Special \"Characters\" Route",
            steps = listOf(step),
            totalDistanceM = 100f,
            totalDurationS = 50f
        )

        val json = GeoJsonRouteParser.serialize(route)
        val parsed = GeoJsonRouteParser.parse(json)
        assertNotNull(parsed)
        assertEquals("Special \"Characters\" Route", parsed!!.name)
        assertEquals("O'Connor \"Express\" Way\\Path", parsed.steps[0].roadName)
        assertEquals("Turn right onto \"Express\" Way\nCaution!", parsed.steps[0].maneuver.instruction)
    }
}
