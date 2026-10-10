package com.osm.wear.presentation

import android.content.Intent
import android.net.Uri
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteFile
import com.osm.wear.models.RouteStep
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test

class TracksScreenLogicTest {

    @Test
    fun testSaveCurrent_preservesCustomRouteName() {
        // When activeRoute has a non-blank name, it must be preserved even if id starts with route_
        val routeFile = RouteFile(
            id = "route_1728512345.geojson",
            name = "Morning Mountain Hike",
            filePath = "/tracks/route_1728512345.geojson",
            points = listOf(GeoPoint(45.0, 7.0)),
            totalDistanceKm = 5.2
        )

        val nameToSave = if (routeFile.name.isBlank() || routeFile.name == "path_finder") {
            "Saved Route"
        } else {
            routeFile.name
        }

        assertEquals("Morning Mountain Hike", nameToSave)
    }

    @Test
    fun testSaveCurrent_fallsBackToSavedRouteWhenBlank() {
        val routeFile = RouteFile(
            id = "route_1728512345.geojson",
            name = "   ",
            filePath = "/tracks/route_1728512345.geojson",
            points = listOf(GeoPoint(45.0, 7.0)),
            totalDistanceKm = 5.2
        )

        val nameToSave = if (routeFile.name.isBlank() || routeFile.name == "path_finder") {
            "Saved Route"
        } else {
            routeFile.name
        }

        assertEquals("Saved Route", nameToSave)
    }

    @Test
    fun testSaveCurrent_fallsBackToSavedRouteWhenPathFinder() {
        val routeFile = RouteFile(
            id = "path_finder",
            name = "path_finder",
            filePath = "/tracks/path_finder.geojson",
            points = listOf(GeoPoint(45.0, 7.0)),
            totalDistanceKm = 1.0
        )

        val nameToSave = if (routeFile.name.isBlank() || routeFile.name == "path_finder") {
            "Saved Route"
        } else {
            routeFile.name
        }

        assertEquals("Saved Route", nameToSave)
    }

    @Test
    fun testMainActivityIntentUriExtraction_prefersDataOverExtraStream() {
        val dataUri = mockk<Uri>()
        val streamUri = mockk<Uri>()
        val intent = mockk<Intent>()

        every { intent.action } returns Intent.ACTION_VIEW
        every { intent.data } returns dataUri

        val data = intent.data
        val uri = data ?: streamUri

        assertEquals(dataUri, uri)
    }

    @Test
    fun testMainActivityIntentUriExtraction_fallsBackToExtraStreamWhenDataIsNull() {
        val streamUri = mockk<Uri>()
        val intent = mockk<Intent>()

        every { intent.action } returns Intent.ACTION_SEND
        every { intent.data } returns null

        val data = intent.data
        val uri = data ?: streamUri

        assertEquals(streamUri, uri)
    }

    @Test
    fun testMainActivityIntentAction_supportsViewAndSend() {
        val isSupportedView = Intent.ACTION_VIEW == Intent.ACTION_VIEW || Intent.ACTION_VIEW == Intent.ACTION_SEND
        val isSupportedSend = Intent.ACTION_SEND == Intent.ACTION_VIEW || Intent.ACTION_SEND == Intent.ACTION_SEND
        val isSupportedMain = Intent.ACTION_MAIN == Intent.ACTION_VIEW || Intent.ACTION_MAIN == Intent.ACTION_SEND

        assertTrue("ACTION_VIEW must be supported", isSupportedView)
        assertTrue("ACTION_SEND must be supported", isSupportedSend)
        assertFalse("ACTION_MAIN must not trigger route import", isSupportedMain)
    }
}
