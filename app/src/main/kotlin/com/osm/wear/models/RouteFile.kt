package com.osm.wear.models

/**
 * Represents a saved GeoJSON route file stored in internal tracks storage.
 * Native RFC 7946 GeoJSON route representation with zero sidecar dependencies.
 *
 * @param id Unique identifier, typically filename (e.g. "route_1728512345.geojson")
 * @param name User-visible track/route title
 * @param filePath Absolute filesystem path in context.filesDir/tracks/
 * @param points Complete polyline coordinates
 * @param totalDistanceKm Total track length in kilometers
 * @param isActive Whether this route is currently active for tracking/navigation
 * @param route Fully parsed step-based route with discrete maneuvers (null if parsing failed)
 */
data class RouteFile(
    val id: String,
    val name: String,
    val filePath: String,
    val points: List<GeoPoint>,
    val totalDistanceKm: Double,
    val isActive: Boolean = false,
    val route: NavigationRoute? = null
) {
    val pointCount: Int
        get() = points.size

    val hasSteps: Boolean
        get() = route != null && route.steps.isNotEmpty()
}
