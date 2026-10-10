package com.osm.wear.data_sources

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.enums.NavigationMode
import kotlinx.coroutines.test.runTest
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import org.mapsforge.core.model.BoundingBox
import java.io.IOException

/**
 * Pure JVM hermetic unit tests for [RemoteGeocodingDataSource] verifying:
 * 1. OSRM routing request URL template, coordinate ordering (lon before lat), and mode profiles.
 * 2. OSRM response parsing into NavigationRoute and full polyline.
 * 3. HTTP error codes (400, 404, 500, 503) and network IOExceptions returning null / emptyList.
 * 4. Nominatim reverse geocode and search request URL formatting, headers, viewbox bounding box,
 *    and response parsing with fallbacks.
 * 5. Fast exit on blank search queries without making network calls.
 */
class RemoteGeocodingDataSourceTest {

    private var capturedRequest: Request? = null
    private var requestCount: Int = 0

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun createMockClient(responseHandler: (Request) -> Response): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor { chain ->
                requestCount++
                capturedRequest = chain.request()
                responseHandler(chain.request())
            }
            .build()
    }

    private fun createJsonResponse(request: Request, code: Int, bodyString: String): Response {
        val mediaType = "application/json; charset=utf-8".toMediaType()
        val body = bodyString.toResponseBody(mediaType)
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code in 200..299) "OK" else "Error")
            .body(body)
            .build()
    }

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
                      [2.2940, 48.8500],
                      [2.2940, 48.8518]
                    ]
                  },
                  "maneuver": {
                    "type": "depart",
                    "location": [2.2940, 48.8500],
                    "bearing_before": 0.0,
                    "bearing_after": 0.0
                  }
                },
                {
                  "distance": 250.0,
                  "duration": 20.0,
                  "name": "Market Street",
                  "geometry": {
                    "type": "LineString",
                    "coordinates": [
                      [2.2940, 48.8518],
                      [2.2981, 48.8518]
                    ]
                  },
                  "maneuver": {
                    "type": "turn",
                    "modifier": "right",
                    "location": [2.2940, 48.8518],
                    "bearing_before": 0.0,
                    "bearing_after": 90.0
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
    fun testFetchNavigationRoute_success_queriesCorrectOsrmUrlAndCoordinates() = runTest {
        val client = createMockClient { req ->
            createJsonResponse(req, 200, sampleOsrmJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val route = dataSource.fetchNavigationRoute(
            startLat = 48.8500,
            startLon = 2.2940,
            endLat = 48.8518,
            endLon = 2.2981,
            mode = NavigationMode.WALKING
        )

        assertNotNull("NavigationRoute must not be null on 200 OK", route)
        assertEquals("Destination Route", route!!.name)
        assertEquals(600f, route.totalDistanceM, 0.01f)
        assertEquals(2, route.steps.size)

        // Verify request URL structure: lon before lat
        assertNotNull(capturedRequest)
        val url = capturedRequest!!.url.toString()
        assertTrue("URL must point to project-osrm router", url.startsWith("https://router.project-osrm.org/route/v1/foot/"))
        assertTrue("URL must specify coordinates as lon,lat;lon,lat", url.contains("/foot/2.294,48.85;2.2981,48.8518"))
        assertTrue("URL must request steps", url.contains("steps=true"))
        assertTrue("URL must request overview=full", url.contains("overview=full"))
        assertTrue("URL must request geometries=geojson", url.contains("geometries=geojson"))
    }

    @Test
    fun testFetchNavigationRoute_navigationModes_profileMapping() = runTest {
        var lastUrl = ""
        val client = createMockClient { req ->
            lastUrl = req.url.toString()
            createJsonResponse(req, 200, sampleOsrmJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)

        // Walking -> "foot"
        dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.WALKING)
        assertTrue("WALKING must map to 'foot' profile", lastUrl.contains("/v1/foot/"))

        // Cycling -> "bike"
        dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.CYCLING)
        assertTrue("CYCLING must map to 'bike' profile", lastUrl.contains("/v1/bike/"))

        // Driving -> "driving"
        dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.DRIVING)
        assertTrue("DRIVING must map to 'driving' profile", lastUrl.contains("/v1/driving/"))
    }

    @Test
    fun testFetchNavigationRoute_httpErrors_returnNullSafely() = runTest {
        val errorCodes = listOf(400, 404, 500, 503)
        for (code in errorCodes) {
            val client = createMockClient { req ->
                createJsonResponse(req, code, "{\"message\":\"Error $code\"}")
            }
            val dataSource = RemoteGeocodingDataSource(client)

            val result = dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.WALKING)
            assertNull("HTTP $code must return null safely without throwing", result)
        }
    }

    @Test
    fun testFetchNavigationRoute_networkException_returnsNullSafely() = runTest {
        val client = createMockClient { _ ->
            throw IOException("Connection timed out to router.project-osrm.org")
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val result = dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.WALKING)
        assertNull("Network IOException must return null safely without throwing", result)
    }

    @Test
    fun testFetchNavigationRoute_malformedOrEmptyJson_returnsNullSafely() = runTest {
        val corruptions = listOf("", "   ", "{ corrupted json ...", "[]", "{\"code\":\"Ok\"}")
        for (badJson in corruptions) {
            val client = createMockClient { req ->
                createJsonResponse(req, 200, badJson)
            }
            val dataSource = RemoteGeocodingDataSource(client)

            val result = dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.WALKING)
            assertNull("Malformed JSON response must return null safely", result)
        }
    }

    @Test
    fun testFetchNavigationRoute_osrmNoRouteCode_returnsNull() = runTest {
        val noRouteJson = "{\"code\":\"NoRoute\",\"message\":\"Impossible route between points\"}"
        val client = createMockClient { req ->
            createJsonResponse(req, 200, noRouteJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val result = dataSource.fetchNavigationRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.WALKING)
        assertNull("OSRM NoRoute code must return null", result)
    }

    @Test
    fun testFetchRoute_success_returnsFullPolyline() = runTest {
        val client = createMockClient { req ->
            createJsonResponse(req, 200, sampleOsrmJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val points = dataSource.fetchRoute(48.8500, 2.2940, 48.8518, 2.2981, NavigationMode.WALKING)

        assertNotNull(points)
        assertTrue("Polyline must contain coordinates on success", points.isNotEmpty())
        assertEquals(3, points.size) // 2 in step 0, 2 in step 1 deduplicated = 3 points
        assertEquals(48.8500, points.first().lat, 0.0001)
        assertEquals(2.2981, points.last().lon, 0.0001)
    }

    @Test
    fun testFetchRoute_failure_returnsEmptyList() = runTest {
        val client = createMockClient { req ->
            createJsonResponse(req, 500, "Internal Server Error")
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val points = dataSource.fetchRoute(48.85, 2.29, 48.86, 2.30, NavigationMode.WALKING)
        assertNotNull(points)
        assertTrue("Must return emptyList on failure", points.isEmpty())
    }

    @Test
    fun testReverseGeocode_success_parsesNominatimResponse() = runTest {
        val nominatimJson = """
        {
          "name": "Eiffel Tower",
          "display_name": "Eiffel Tower, 5, Avenue Anatole France, Gros-Caillou, Paris, Île-de-France, France"
        }
        """.trimIndent()

        val client = createMockClient { req ->
            createJsonResponse(req, 200, nominatimJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)
        io.mockk.mockkConstructor(org.json.JSONObject::class)
        io.mockk.every { anyConstructed<org.json.JSONObject>().optString("name", any()) } returns "Eiffel Tower"
        io.mockk.every { anyConstructed<org.json.JSONObject>().optString("display_name", any()) } returns "Eiffel Tower, 5, Avenue Anatole France, Gros-Caillou, Paris, Île-de-France, France"

        val result = dataSource.reverseGeocode(48.8584, 2.2945)
        io.mockk.unmockkConstructor(org.json.JSONObject::class)

        assertNotNull(result)
        assertEquals("Eiffel Tower", result!!.name)
        assertTrue("Address must parse middle parts", result.address.contains("Avenue Anatole France"))
        assertEquals(48.8584, result.lat, 0.00001)
        assertEquals(2.2945, result.lon, 0.00001)

        // Verify request details
        assertNotNull(capturedRequest)
        val url = capturedRequest!!.url.toString()
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/reverse?lat=48.8584&lon=2.2945"))
        assertTrue(url.contains("format=json"))
        assertTrue(url.contains("accept-language=en"))
        val userAgent = capturedRequest!!.header("User-Agent")
        assertNotNull("Must include User-Agent header", userAgent)
        assertTrue(userAgent!!.contains("Mozilla/5.0"))
    }

    @Test
    fun testReverseGeocode_missingName_usesFirstAddressPart() = runTest {
        val nominatimJson = """
        {
          "name": "",
          "display_name": "5 Avenue Anatole France, Gros-Caillou, Paris, France"
        }
        """.trimIndent()

        val client = createMockClient { req ->
            createJsonResponse(req, 200, nominatimJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)

        mockkConstructor(org.json.JSONObject::class)
        every { anyConstructed<org.json.JSONObject>().optString("name", any()) } returns ""
        every { anyConstructed<org.json.JSONObject>().optString("display_name", any()) } returns "5 Avenue Anatole France, Gros-Caillou, Paris, France"

        val result = dataSource.reverseGeocode(48.8584, 2.2945)

        assertNotNull(result)
        assertEquals("5 Avenue Anatole France", result!!.name)
    }

    @Test
    fun testReverseGeocode_httpErrorOrException_returnsNull() = runTest {
        // HTTP 429
        val client429 = createMockClient { req ->
            createJsonResponse(req, 429, "Too Many Requests")
        }
        val ds429 = RemoteGeocodingDataSource(client429)
        assertNull("HTTP 429 must return null", ds429.reverseGeocode(48.85, 2.29))

        // IOException
        val clientError = createMockClient { _ ->
            throw IOException("Network unreachable")
        }
        val dsError = RemoteGeocodingDataSource(clientError)
        assertNull("Network error must return null", dsError.reverseGeocode(48.85, 2.29))
    }

    @Test
    fun testSearchAddress_success_parsesResultList() = runTest {
        val searchJson = """
        [
          {
            "name": "Louvre Museum",
            "display_name": "Louvre Museum, Rue de Rivoli, Paris, Île-de-France, France",
            "lat": 48.8606,
            "lon": 2.3376
          },
          {
            "name": "Market Street",
            "display_name": "Market Street, San Francisco, California, USA",
            "lat": 37.7749,
            "lon": -122.4194
          }
        ]
        """.trimIndent()

        val client = createMockClient { req ->
            createJsonResponse(req, 200, searchJson)
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val obj0 = mockk<org.json.JSONObject>()
        every { obj0.optString("name", any()) } returns "Louvre Museum"
        every { obj0.optString("display_name", any()) } returns "Louvre Museum, Rue de Rivoli, Paris, Île-de-France, France"
        every { obj0.getDouble("lat") } returns 48.8606
        every { obj0.getDouble("lon") } returns 2.3376

        val obj1 = mockk<org.json.JSONObject>()
        every { obj1.optString("name", any()) } returns "Market Street"
        every { obj1.optString("display_name", any()) } returns "Market Street, San Francisco, California, USA"
        every { obj1.getDouble("lat") } returns 37.7749
        every { obj1.getDouble("lon") } returns -122.4194

        mockkConstructor(org.json.JSONArray::class)
        every { anyConstructed<org.json.JSONArray>().length() } returns 2
        every { anyConstructed<org.json.JSONArray>().getJSONObject(0) } returns obj0
        every { anyConstructed<org.json.JSONArray>().getJSONObject(1) } returns obj1

        val results = dataSource.searchAddress("Museums", null)

        assertEquals(2, results.size)
        assertEquals("Louvre Museum", results[0].name)
        assertEquals(48.8606, results[0].lat, 0.0001)
        assertEquals(2.3376, results[0].lon, 0.0001)
        assertEquals("Market Street", results[1].name)
        assertEquals(37.7749, results[1].lat, 0.0001)
        assertEquals(-122.4194, results[1].lon, 0.0001)

        assertNotNull(capturedRequest)
        val url = capturedRequest!!.url.toString()
        assertTrue(url.startsWith("https://nominatim.openstreetmap.org/search?q=Museums&format=json"))
    }

    @Test
    fun testSearchAddress_withBoundingBox_appendsViewboxParam() = runTest {
        val client = createMockClient { req ->
            createJsonResponse(req, 200, "[]")
        }
        val dataSource = RemoteGeocodingDataSource(client)

        // BoundingBox(minLat, minLon, maxLat, maxLon)
        val bbox = BoundingBox(37.70, -122.50, 37.85, -122.35)
        dataSource.searchAddress("Market Street", bbox)

        assertNotNull(capturedRequest)
        val url = capturedRequest!!.url.toString()
        // viewbox = "${bbox.minLongitude},${bbox.maxLatitude},${bbox.maxLongitude},${bbox.minLatitude}&bounded=1"
        assertTrue("URL must append viewbox parameter", url.contains("&viewbox=-122.5,37.85,-122.35,37.7&bounded=1"))
    }

    @Test
    fun testSearchAddress_blankQuery_returnsEmptyListWithoutNetworkCall() = runTest {
        requestCount = 0
        val client = createMockClient { req ->
            createJsonResponse(req, 200, "[]")
        }
        val dataSource = RemoteGeocodingDataSource(client)

        val resEmpty = dataSource.searchAddress("", null)
        val resBlank = dataSource.searchAddress("   ", null)

        assertTrue(resEmpty.isEmpty())
        assertTrue(resBlank.isEmpty())
        assertEquals("Must NOT make any network calls for blank queries", 0, requestCount)
    }

    @Test
    fun testSearchAddress_httpErrorOrMalformed_returnsEmptyList() = runTest {
        // HTTP 500
        val client500 = createMockClient { req ->
            createJsonResponse(req, 500, "Server Error")
        }
        val ds500 = RemoteGeocodingDataSource(client500)
        assertTrue(ds500.searchAddress("Paris", null).isEmpty())

        // Corrupted JSON
        val clientCorrupt = createMockClient { req ->
            createJsonResponse(req, 200, "{ not an array }")
        }
        val dsCorrupt = RemoteGeocodingDataSource(clientCorrupt)
        assertTrue(dsCorrupt.searchAddress("Paris", null).isEmpty())

        // Network exception
        val clientNetError = createMockClient { _ ->
            throw IOException("Socket timeout")
        }
        val dsNetError = RemoteGeocodingDataSource(clientNetError)
        assertTrue(dsNetError.searchAddress("Paris", null).isEmpty())
    }
}
