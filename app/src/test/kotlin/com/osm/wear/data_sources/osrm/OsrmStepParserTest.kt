package com.osm.wear.data_sources.osrm

import com.osm.wear.models.ManeuverType
import org.junit.Assert.*
import org.junit.Test

class OsrmStepParserTest {

    private val sampleOsrmJson = """
    {
      "code": "Ok",
      "routes": [
        {
          "distance": 600.0,
          "duration": 45.0,
          "legs": [
            {
              "steps": [
                {
                  "distance": 350.0,
                  "duration": 25.0,
                  "name": "Main Street",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [-122.4194, 37.7749],
                      [-122.4150, 37.7749]
                    ]
                  },
                  "maneuver": {
                    "type": "depart",
                    "location": [-122.4194, 37.7749],
                    "bearing_before": 0.0,
                    "bearing_after": 90.0
                  }
                },
                {
                  "distance": 250.0,
                  "duration": 20.0,
                  "name": "Market Street",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [-122.4150, 37.7749],
                      [-122.4150, 37.7770]
                    ]
                  },
                  "maneuver": {
                    "type": "turn",
                    "modifier": "left",
                    "location": [-122.4150, 37.7749],
                    "bearing_before": 90.0,
                    "bearing_after": 0.0
                  }
                },
                {
                  "distance": 0.0,
                  "duration": 0.0,
                  "name": "",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [-122.4150, 37.7770]
                    ]
                  },
                  "maneuver": {
                    "type": "arrive",
                    "location": [-122.4150, 37.7770],
                    "bearing_before": 0.0,
                    "bearing_after": 0.0
                  }
                }
              ]
            }
          ]
        }
      ]
    }
    """.trimIndent()

    @Test
    fun testParseResponse_extractsStepsAndCoordinatesCorrectly() {
        val route = OsrmStepParser.parseResponse(sampleOsrmJson, "Test Route")
        assertNotNull(route)
        assertEquals(600f, route!!.totalDistanceM, 0.01f)
        assertEquals(2, route.steps.size)

        val step0 = route.steps[0]
        assertEquals("Main Street", step0.roadName)
        assertEquals(350f, step0.distanceM, 0.01f)
        assertEquals(ManeuverType.TURN_LEFT, step0.maneuver.type)
        assertEquals("Market Street", step0.maneuver.roadName)
        assertEquals(-122.4150, step0.maneuver.location.lon, 1e-4)
        assertEquals(37.7749, step0.maneuver.location.lat, 1e-4)
        assertEquals("In 350 meters, turn left onto Market Street", step0.maneuver.instruction)

        val step1 = route.steps[1]
        assertEquals("Market Street", step1.roadName)
        assertEquals(250f, step1.distanceM, 0.01f)
        assertEquals(ManeuverType.ARRIVE, step1.maneuver.type)
        assertEquals("In 250 meters, arrive at destination", step1.maneuver.instruction)
    }

    @Test
    fun testManeuverTypeMapping_modifiers() {
        assertEquals(ManeuverType.TURN_LEFT, OsrmStepParser.parseManeuverType("turn", "left"))
        assertEquals(ManeuverType.TURN_RIGHT, OsrmStepParser.parseManeuverType("turn", "right"))
        assertEquals(ManeuverType.SLIGHT_LEFT, OsrmStepParser.parseManeuverType("turn", "slight left"))
        assertEquals(ManeuverType.SLIGHT_RIGHT, OsrmStepParser.parseManeuverType("turn", "slight right"))
        assertEquals(ManeuverType.SHARP_LEFT, OsrmStepParser.parseManeuverType("turn", "sharp left"))
        assertEquals(ManeuverType.SHARP_RIGHT, OsrmStepParser.parseManeuverType("turn", "sharp right"))
        assertEquals(ManeuverType.U_TURN, OsrmStepParser.parseManeuverType("turn", "uturn"))
        assertEquals(ManeuverType.CONTINUE, OsrmStepParser.parseManeuverType("continue", "straight"))
        assertEquals(ManeuverType.DEPART, OsrmStepParser.parseManeuverType("depart", ""))
        assertEquals(ManeuverType.ARRIVE, OsrmStepParser.parseManeuverType("arrive", ""))
    }

    @Test
    fun testRelativeAngleCalculation() {
        assertEquals(90f, OsrmStepParser.calculateRelativeAngle(0f, 90f), 0.001f)
        assertEquals(-90f, OsrmStepParser.calculateRelativeAngle(90f, 0f), 0.001f)
        assertEquals(20f, OsrmStepParser.calculateRelativeAngle(350f, 10f), 0.001f)
        assertEquals(-20f, OsrmStepParser.calculateRelativeAngle(10f, 350f), 0.001f)
    }

    @Test
    fun testAngleFallbackClassification() {
        assertEquals(ManeuverType.CONTINUE, OsrmStepParser.mapAngleToManeuverType(0f))
        assertEquals(ManeuverType.SLIGHT_RIGHT, OsrmStepParser.mapAngleToManeuverType(30f))
        assertEquals(ManeuverType.TURN_RIGHT, OsrmStepParser.mapAngleToManeuverType(90f))
        assertEquals(ManeuverType.SHARP_RIGHT, OsrmStepParser.mapAngleToManeuverType(130f))
        assertEquals(ManeuverType.U_TURN, OsrmStepParser.mapAngleToManeuverType(170f))
        assertEquals(ManeuverType.SLIGHT_LEFT, OsrmStepParser.mapAngleToManeuverType(-30f))
        assertEquals(ManeuverType.TURN_LEFT, OsrmStepParser.mapAngleToManeuverType(-90f))
        assertEquals(ManeuverType.SHARP_LEFT, OsrmStepParser.mapAngleToManeuverType(-130f))
    }

    @Test
    fun testInvalidOrErrorJsonHandling() {
        assertNull(OsrmStepParser.parseResponse("invalid json"))
        assertNull(OsrmStepParser.parseResponse("""{"code": "NoRoute"}"""))
        assertNull(OsrmStepParser.parseResponse("""{"code": "Ok", "routes": []}"""))
    }

    @Test
    fun testSpeechDistanceFormatting() {
        assertEquals("50 meters", OsrmStepParser.formatDistanceForSpeech(20f))
        assertEquals("100 meters", OsrmStepParser.formatDistanceForSpeech(110f))
        assertEquals("1 kilometer", OsrmStepParser.formatDistanceForSpeech(990f))
        assertEquals("1.5 kilometers", OsrmStepParser.formatDistanceForSpeech(1520f))
    }

    @Test
    fun testAngleFallbackClassification_boundarySymmetry() {
        // Boundary at 15
        assertEquals(ManeuverType.CONTINUE, OsrmStepParser.mapAngleToManeuverType(14.9f))
        assertEquals(ManeuverType.CONTINUE, OsrmStepParser.mapAngleToManeuverType(-14.9f))
        assertEquals(ManeuverType.SLIGHT_RIGHT, OsrmStepParser.mapAngleToManeuverType(15f))
        assertEquals(ManeuverType.SLIGHT_LEFT, OsrmStepParser.mapAngleToManeuverType(-15f))

        // Boundary at 45
        assertEquals(ManeuverType.SLIGHT_RIGHT, OsrmStepParser.mapAngleToManeuverType(44.9f))
        assertEquals(ManeuverType.SLIGHT_LEFT, OsrmStepParser.mapAngleToManeuverType(-44.9f))
        assertEquals(ManeuverType.TURN_RIGHT, OsrmStepParser.mapAngleToManeuverType(45f))
        assertEquals(ManeuverType.TURN_LEFT, OsrmStepParser.mapAngleToManeuverType(-45f))

        // Boundary at 105
        assertEquals(ManeuverType.TURN_RIGHT, OsrmStepParser.mapAngleToManeuverType(104.9f))
        assertEquals(ManeuverType.TURN_LEFT, OsrmStepParser.mapAngleToManeuverType(-104.9f))
        assertEquals(ManeuverType.SHARP_RIGHT, OsrmStepParser.mapAngleToManeuverType(105f))
        assertEquals(ManeuverType.SHARP_LEFT, OsrmStepParser.mapAngleToManeuverType(-105f))

        // Boundary at 150
        assertEquals(ManeuverType.SHARP_RIGHT, OsrmStepParser.mapAngleToManeuverType(149.9f))
        assertEquals(ManeuverType.SHARP_LEFT, OsrmStepParser.mapAngleToManeuverType(-149.9f))
        assertEquals(ManeuverType.U_TURN, OsrmStepParser.mapAngleToManeuverType(150f))
        assertEquals(ManeuverType.U_TURN, OsrmStepParser.mapAngleToManeuverType(-150f))
    }

    @Test
    fun testParseResponse_missingManeuverDerivesBearingsFromPolyline() {
        // Step 0 goes East, Step 1 goes North (a 90-degree left turn) with no maneuver object in step 1
        val json = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 200.0,
              "duration": 20.0,
              "legs": [
                {
                  "steps": [
                    {
                      "distance": 100.0,
                      "duration": 10.0,
                      "name": "East St",
                      "geometry": {
                        "coordinates": [
                          [10.0, 50.0],
                          [10.01, 50.0]
                        ]
                      }
                    },
                    {
                      "distance": 100.0,
                      "duration": 10.0,
                      "name": "North St",
                      "geometry": {
                        "coordinates": [
                          [10.01, 50.0],
                          [10.01, 50.01]
                        ]
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val route = OsrmStepParser.parseResponse(json, "Derived Route")
        assertNotNull(route)
        assertEquals(2, route!!.steps.size)
        // Step 0 should have derived TURN_LEFT toward North St
        assertEquals(ManeuverType.TURN_LEFT, route.steps[0].maneuver.type)
        assertEquals(ManeuverType.ARRIVE, route.steps[1].maneuver.type)
    }

    @Test
    fun testParseResponse_flatCoordinatesAndStrictJsonRejection() {
        // Step with flat coordinate [lon, lat]
        val jsonFlat = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 50.0,
              "duration": 5.0,
              "legs": [
                {
                  "steps": [
                    {
                      "distance": 50.0,
                      "duration": 5.0,
                      "geometry": {
                        "coordinates": [10.0, 50.0]
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()
        val routeFlat = OsrmStepParser.parseResponse(jsonFlat)
        assertNotNull(routeFlat)
        assertEquals(1, routeFlat!!.steps.size)
        assertEquals(1, routeFlat.steps[0].polyline.size)

        // Trailing garbage after valid JSON must be rejected
        assertNull(OsrmStepParser.parseResponse("""{"code": "Ok", "routes": []} extra"""))
        // Unquoted key must be rejected
        assertNull(OsrmStepParser.parseResponse("""{code: "Ok"}"""))
        // Malformed number token must be rejected
        assertNull(OsrmStepParser.parseResponse("""{"distance": ]}"""))
    }

    @Test
    fun testParseResponse_mergesStraightContinuationsAndRoadNameChanges() {
        val json = """
        {
          "code": "Ok",
          "routes": [
            {
              "distance": 700.0,
              "duration": 60.0,
              "legs": [
                {
                  "steps": [
                    {
                      "distance": 150.0,
                      "duration": 15.0,
                      "name": "High Street",
                      "geometry": {
                        "coordinates": [
                          [-0.12, 51.50],
                          [-0.12, 51.51]
                        ]
                      },
                      "maneuver": {
                        "type": "depart",
                        "location": [-0.12, 51.50]
                      }
                    },
                    {
                      "distance": 250.0,
                      "duration": 20.0,
                      "name": "London Road",
                      "geometry": {
                        "coordinates": [
                          [-0.12, 51.51],
                          [-0.12, 51.52]
                        ]
                      },
                      "maneuver": {
                        "type": "new name",
                        "modifier": "straight",
                        "location": [-0.12, 51.51]
                      }
                    },
                    {
                      "distance": 300.0,
                      "duration": 25.0,
                      "name": "Church Road",
                      "geometry": {
                        "coordinates": [
                          [-0.12, 51.52],
                          [-0.11, 51.52]
                        ]
                      },
                      "maneuver": {
                        "type": "turn",
                        "modifier": "right",
                        "location": [-0.12, 51.52]
                      }
                    },
                    {
                      "distance": 0.0,
                      "duration": 0.0,
                      "name": "",
                      "geometry": {
                        "coordinates": [
                          [-0.11, 51.52]
                        ]
                      },
                      "maneuver": {
                        "type": "arrive",
                        "location": [-0.11, 51.52]
                      }
                    }
                  ]
                }
              ]
            }
          ]
        }
        """.trimIndent()

        val route = OsrmStepParser.parseResponse(json, "Merged Route")
        assertNotNull(route)
        assertEquals(700f, route!!.totalDistanceM, 0.01f)
        // High Street (150m) and London Road (250m) must merge into 1 step terminating at TURN_RIGHT
        assertEquals(2, route.steps.size)

        val step0 = route.steps[0]
        assertEquals("High Street", step0.roadName)
        assertEquals(400f, step0.distanceM, 0.01f)
        assertEquals(ManeuverType.TURN_RIGHT, step0.maneuver.type)
        assertEquals("Church Road", step0.maneuver.roadName)
        assertEquals("In 400 meters, turn right onto Church Road", step0.maneuver.instruction)
        // Polyline should include points from both segments without duplicates
        assertEquals(3, step0.polyline.size)

        val step1 = route.steps[1]
        assertEquals("Church Road", step1.roadName)
        assertEquals(300f, step1.distanceM, 0.01f)
        assertEquals(ManeuverType.ARRIVE, step1.maneuver.type)
        assertEquals("In 300 meters, arrive at destination", step1.maneuver.instruction)
    }
}
