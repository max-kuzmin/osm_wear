package com.osm.wear.data_sources.geojson

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteStep
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * High-performance parser and serializer for RFC 7946 GeoJSON route files using platform org.json.
 *
 * Implements:
 * 1. RFC 7946 FeatureCollection serialization with explicit step maneuvers.
 * 2. Deserialization of native OsmWear step features.
 * 3. Deserialization of compact/OSRM step formats.
 * 4. Fallback polyline extraction for 3rd-party GeoJSON exports (Strava, GeoJSON.io).
 */
object GeoJsonRouteParser {

    // ── Public API ────────────────────────────────────────────────────────────

    fun parse(jsonString: String, defaultName: String = "Route"): NavigationRoute? {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val root = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return null
        }
        val type = root.optString("type")

        return when (type) {
            "FeatureCollection" -> parseFeatureCollection(root, defaultName)
            "Feature" -> parseSingleFeature(root, defaultName)
            "LineString", "MultiLineString" -> parseDirectGeometry(root, defaultName)
            else -> null
        }
    }

    fun serialize(route: NavigationRoute): String = buildString {
        append("{\"type\":\"FeatureCollection\",")
        append("\"properties\":{")
        append("\"id\":\"").append(escapeJson(route.id)).append("\",")
        append("\"name\":\"").append(escapeJson(route.name)).append("\",")
        append("\"totalDistanceM\":").append(route.totalDistanceM).append(",")
        append("\"totalDurationS\":").append(route.totalDurationS).append(",")
        append("\"creator\":\"osm_wear\",")
        append("\"version\":\"1.0\"")
        append("},")
        append("\"features\":[")

        // Feature 0: Overall Route LineString
        append("{\"type\":\"Feature\",")
        append("\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
        route.fullPolyline.forEachIndexed { i, pt ->
            if (i > 0) append(",")
            appendCoordinate(this, pt)
        }
        append("]},")
        append("\"properties\":{")
        append("\"role\":\"route\",")
        append("\"name\":\"").append(escapeJson(route.name)).append("\",")
        append("\"totalDistanceM\":").append(route.totalDistanceM).append(",")
        append("\"totalDurationS\":").append(route.totalDurationS)
        append("}}")

        // Features 1..N: Discrete Step Features
        route.steps.forEach { step ->
            append(",")
            append("{\"type\":\"Feature\",")
            append("\"geometry\":{\"type\":\"LineString\",\"coordinates\":[")
            step.polyline.forEachIndexed { j, pt ->
                if (j > 0) append(",")
                appendCoordinate(this, pt)
            }
            append("]},")
            append("\"properties\":{")
            append("\"role\":\"step\",")
            append("\"index\":").append(step.index).append(",")
            append("\"distanceM\":").append(step.distanceM).append(",")
            append("\"durationS\":").append(step.durationS).append(",")
            if (step.roadName != null) {
                append("\"roadName\":\"").append(escapeJson(step.roadName)).append("\",")
            }
            append("\"maneuver\":{")
            append("\"type\":\"").append(step.maneuver.type.name).append("\",")
            append("\"location\":")
            appendCoordinate(this, step.maneuver.location)
            append(",")
            append("\"bearingBefore\":").append(step.maneuver.bearingBefore).append(",")
            append("\"bearingAfter\":").append(step.maneuver.bearingAfter).append(",")
            append("\"relativeAngle\":").append(step.maneuver.relativeAngle).append(",")
            if (step.maneuver.roadName != null) {
                append("\"roadName\":\"").append(escapeJson(step.maneuver.roadName)).append("\",")
            }
            append("\"instruction\":\"").append(escapeJson(step.maneuver.instruction)).append("\"")
            append("}}}")
        }

        append("]}")
    }

    // ── Internal Parsing Logic ────────────────────────────────────────────────

    private fun parseFeatureCollection(root: JSONObject, defaultName: String): NavigationRoute? {
        val collectionProps = root.optJSONObject("properties")
        val routeId = collectionProps?.optString("id")?.ifBlank { null } ?: UUID.randomUUID().toString()
        val routeName = collectionProps?.optString("name")?.ifBlank { null } ?: defaultName
        val totalDistProp = collectionProps?.optDouble("totalDistanceM", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()
        val totalDurProp = collectionProps?.optDouble("totalDurationS", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()

        val features = root.optJSONArray("features") ?: return null
        if (features.length() == 0) return null

        // 1. Try extracting explicit step features
        val stepFeatures = mutableListOf<JSONObject>()
        var fullRouteFeature: JSONObject? = null

        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i) ?: continue
            val props = feat.optJSONObject("properties")
            val role = props?.optString("role")
            val hasManeuver = props?.has("maneuver") == true

            if (role == "step" || hasManeuver) {
                stepFeatures.add(feat)
            } else if (role == "route" || fullRouteFeature == null) {
                fullRouteFeature = feat
            }
        }

        if (stepFeatures.isNotEmpty()) {
            val steps = mutableListOf<RouteStep>()
            for ((idx, feat) in stepFeatures.withIndex()) {
                val step = parseStepFeature(feat, idx) ?: continue
                steps.add(step)
            }
            if (steps.isNotEmpty()) {
                val totalDist = totalDistProp ?: steps.sumOf { it.distanceM.toDouble() }.toFloat()
                val totalDur = totalDurProp ?: steps.sumOf { it.durationS.toDouble() }.toFloat()
                return NavigationRoute(
                    id = routeId,
                    name = routeName,
                    steps = steps,
                    totalDistanceM = totalDist,
                    totalDurationS = totalDur
                )
            }
        }

        // 2. Check if routeFeature has embedded steps array
        val embeddedSteps = fullRouteFeature?.optJSONObject("properties")?.optJSONArray("steps")
            ?: collectionProps?.optJSONArray("steps")
        if (embeddedSteps != null && embeddedSteps.length() > 0) {
            val steps = parseEmbeddedSteps(embeddedSteps)
            if (steps.isNotEmpty()) {
                val totalDist = totalDistProp ?: steps.sumOf { it.distanceM.toDouble() }.toFloat()
                val totalDur = totalDurProp ?: steps.sumOf { it.durationS.toDouble() }.toFloat()
                return NavigationRoute(
                    id = routeId,
                    name = routeName,
                    steps = steps,
                    totalDistanceM = totalDist,
                    totalDurationS = totalDur
                )
            }
        }

        // 3. Fallback: Extract polyline from all line geometries in the collection
        val allPoints = mutableListOf<GeoPoint>()
        for (i in 0 until features.length()) {
            val feat = features.optJSONObject(i) ?: continue
            val geom = feat.optJSONObject("geometry") ?: continue
            appendPointsWithoutDuplicate(allPoints, extractCoordinates(geom))
        }

        if (allPoints.size >= 2) {
            return synthesizeFallbackRoute(routeId, routeName, allPoints, totalDistProp, totalDurProp)
        }

        return null
    }

    private fun parseSingleFeature(feature: JSONObject, defaultName: String): NavigationRoute? {
        val props = feature.optJSONObject("properties")
        val routeId = props?.optString("id")?.ifBlank { null } ?: UUID.randomUUID().toString()
        val routeName = props?.optString("name")?.ifBlank { null } ?: defaultName
        val totalDistProp = props?.optDouble("totalDistanceM", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()
        val totalDurProp = props?.optDouble("totalDurationS", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()

        // Check for embedded steps
        val embeddedSteps = props?.optJSONArray("steps")
        if (embeddedSteps != null && embeddedSteps.length() > 0) {
            val steps = parseEmbeddedSteps(embeddedSteps)
            if (steps.isNotEmpty()) {
                val totalDist = totalDistProp ?: steps.sumOf { it.distanceM.toDouble() }.toFloat()
                val totalDur = totalDurProp ?: steps.sumOf { it.durationS.toDouble() }.toFloat()
                return NavigationRoute(routeId, routeName, steps, totalDist, totalDur)
            }
        }

        val geom = feature.optJSONObject("geometry") ?: return null
        val points = extractCoordinates(geom)
        if (points.size >= 2) {
            return synthesizeFallbackRoute(routeId, routeName, points, totalDistProp, totalDurProp)
        }
        return null
    }

    private fun parseDirectGeometry(geom: JSONObject, defaultName: String): NavigationRoute? {
        val points = extractCoordinates(geom)
        if (points.size >= 2) {
            val id = UUID.randomUUID().toString()
            return synthesizeFallbackRoute(id, defaultName, points, null, null)
        }
        return null
    }

    private fun parseStepFeature(feature: JSONObject, defaultIndex: Int): RouteStep? {
        val geom = feature.optJSONObject("geometry") ?: return null
        val polyline = extractCoordinates(geom)
        if (polyline.isEmpty()) return null

        val props = feature.optJSONObject("properties")
        val index = props?.optInt("index", defaultIndex) ?: defaultIndex
        val roadName = props?.optString("roadName")?.ifBlank { null }

        var distanceM = props?.optDouble("distanceM", 0.0)?.toFloat() ?: 0f
        if (distanceM <= 0f && polyline.size >= 2) {
            distanceM = calculatePolylineDistanceM(polyline).toFloat()
        }
        val durationS = props?.optDouble("durationS", 0.0)?.toFloat() ?: 0f

        val mObj = props?.optJSONObject("maneuver")
        val maneuver = parseManeuver(mObj, polyline, roadName, distanceM)

        return RouteStep(
            index = index,
            polyline = polyline,
            distanceM = distanceM,
            durationS = durationS,
            roadName = roadName,
            maneuver = maneuver
        )
    }

    private fun parseManeuver(
        mObj: JSONObject?,
        polyline: List<GeoPoint>,
        stepRoadName: String?,
        stepDistanceM: Float
    ): Maneuver {
        val typeStr = mObj?.optString("type")?.trim() ?: ""
        val modStr = mObj?.optString("modifier")?.trim()?.ifBlank { null }
        val relAngle = mObj?.optDouble("relativeAngle", 0.0)?.toFloat() ?: 0f

        val maneuverType = resolveManeuverType(typeStr, modStr, relAngle)

        val locObj = mObj?.opt("location")
        val location = parseLocationCoordinate(locObj) ?: polyline.lastOrNull() ?: GeoPoint(0.0, 0.0)

        val bBefore = mObj?.optDouble("bearingBefore", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat()
            ?: (if (polyline.size >= 2) polyline[polyline.size - 2].bearingTo(polyline.last()) else 0f)
        val bAfter = mObj?.optDouble("bearingAfter", Double.NaN)?.takeIf { !it.isNaN() }?.toFloat() ?: bBefore

        val mRoadName = mObj?.optString("roadName")?.ifBlank { null } ?: stepRoadName
        val instruction = mObj?.optString("instruction")?.ifBlank { null }
            ?: formatDefaultInstruction(stepDistanceM, maneuverType, mRoadName)

        return Maneuver(
            type = maneuverType,
            location = location,
            bearingBefore = bBefore,
            bearingAfter = bAfter,
            relativeAngle = relAngle,
            roadName = mRoadName,
            instruction = instruction
        )
    }

    private fun resolveManeuverType(typeStr: String, modifierStr: String?, relativeAngle: Float): ManeuverType {
        if (typeStr.isNotEmpty()) {
            try {
                return ManeuverType.valueOf(typeStr.uppercase())
            } catch (_: Exception) {}
        }
        return ManeuverType.fromOsrm(typeStr, modifierStr, relativeAngle)
    }

    private fun parseLocationCoordinate(locObj: Any?): GeoPoint? {
        if (locObj is JSONArray && locObj.length() >= 2) {
            val lon = locObj.optDouble(0, Double.NaN)
            val lat = locObj.optDouble(1, Double.NaN)
            val ele = if (locObj.length() >= 3) locObj.optDouble(2, 0.0) else 0.0
            if (isValidCoordinate(lat, lon)) return GeoPoint(lat, lon, ele)
        } else if (locObj is JSONObject) {
            val lat = locObj.optDouble("lat", Double.NaN)
            val lon = locObj.optDouble("lon", Double.NaN)
            val ele = locObj.optDouble("ele", 0.0)
            if (isValidCoordinate(lat, lon)) return GeoPoint(lat, lon, ele)
        }
        return null
    }

    private fun parseEmbeddedSteps(stepsArray: JSONArray): List<RouteStep> {
        val result = mutableListOf<RouteStep>()
        for (idx in 0 until stepsArray.length()) {
            val stepObj = stepsArray.optJSONObject(idx) ?: continue
            val index = stepObj.optInt("index", idx)
            val dist = (stepObj.optDouble("distanceM", Double.NaN).takeIf { !it.isNaN() }
                ?: stepObj.optDouble("distance", 0.0)).toFloat()
            val dur = (stepObj.optDouble("durationS", Double.NaN).takeIf { !it.isNaN() }
                ?: stepObj.optDouble("duration", 0.0)).toFloat()
            val road = (stepObj.optString("roadName").ifBlank { null }
                ?: stepObj.optString("name")).ifBlank { null }

            val polyline = mutableListOf<GeoPoint>()
            val geomObj = stepObj.optJSONObject("geometry")
            if (geomObj != null) {
                polyline.addAll(extractCoordinates(geomObj))
            } else {
                val polyArr = stepObj.optJSONArray("polyline")
                if (polyArr != null) {
                    for (p in 0 until polyArr.length()) {
                        val ptObj = polyArr.optJSONObject(p) ?: continue
                        val lat = ptObj.optDouble("lat", Double.NaN)
                        val lon = ptObj.optDouble("lon", Double.NaN)
                        val ele = ptObj.optDouble("ele", 0.0)
                        if (isValidCoordinate(lat, lon)) {
                            polyline.add(GeoPoint(lat, lon, ele))
                        }
                    }
                }
            }

            val mObj = stepObj.optJSONObject("maneuver")
            val maneuver = parseManeuver(mObj, polyline, road, dist)

            result.add(
                RouteStep(
                    index = index,
                    polyline = polyline,
                    distanceM = dist,
                    durationS = dur,
                    roadName = road,
                    maneuver = maneuver
                )
            )
        }
        return result
    }

    private fun extractCoordinates(geometry: JSONObject): List<GeoPoint> {
        val geomType = geometry.optString("type")
        val coords = geometry.optJSONArray("coordinates") ?: return emptyList()
        return when (geomType) {
            "LineString" -> parseCoordinateList(coords)
            "MultiLineString" -> {
                val list = mutableListOf<GeoPoint>()
                for (i in 0 until coords.length()) {
                    val subCoords = coords.optJSONArray(i) ?: continue
                    appendPointsWithoutDuplicate(list, parseCoordinateList(subCoords))
                }
                list
            }
            else -> emptyList()
        }
    }

    private fun parseCoordinateList(coords: JSONArray): List<GeoPoint> {
        val points = mutableListOf<GeoPoint>()
        for (i in 0 until coords.length()) {
            val pt = coords.optJSONArray(i) ?: continue
            if (pt.length() >= 2) {
                val lon = pt.optDouble(0, Double.NaN)
                val lat = pt.optDouble(1, Double.NaN)
                val ele = if (pt.length() >= 3) pt.optDouble(2, 0.0) else 0.0
                if (isValidCoordinate(lat, lon)) {
                    points.add(GeoPoint(lat = lat, lon = lon, ele = ele))
                }
            }
        }
        return points
    }

    private fun synthesizeFallbackRoute(
        id: String,
        name: String,
        points: List<GeoPoint>,
        overrideDistanceM: Float?,
        overrideDurationS: Float?
    ): NavigationRoute {
        val totalDistM = overrideDistanceM ?: calculatePolylineDistanceM(points).toFloat()
        // Estimate walking duration ~5 km/h (1.38889 m/s) if duration missing
        val totalDurS = overrideDurationS ?: (totalDistM / 1.38889f)

        val destination = points.last()
        val bBefore = if (points.size >= 2) points[points.size - 2].bearingTo(destination) else 0f

        val finalManeuver = Maneuver(
            type = ManeuverType.ARRIVE,
            location = destination,
            bearingBefore = bBefore,
            bearingAfter = bBefore,
            relativeAngle = 0f,
            roadName = name,
            instruction = "Arrive at destination"
        )

        val singleStep = RouteStep(
            index = 0,
            polyline = points,
            distanceM = totalDistM,
            durationS = totalDurS,
            roadName = name,
            maneuver = finalManeuver
        )

        return NavigationRoute(
            id = id,
            name = name,
            steps = listOf(singleStep),
            totalDistanceM = totalDistM,
            totalDurationS = totalDurS
        )
    }

    // ── Geographic Helpers ───────────────────────────────────────────────────

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return !lat.isNaN() && !lat.isInfinite() &&
                !lon.isNaN() && !lon.isInfinite() &&
                lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun appendPointsWithoutDuplicate(dest: MutableList<GeoPoint>, src: List<GeoPoint>) {
        for (pt in src) {
            if (dest.isEmpty() || dest.last() != pt) {
                dest.add(pt)
            }
        }
    }

    private fun calculatePolylineDistanceM(points: List<GeoPoint>): Double {
        if (points.size < 2) return 0.0
        var sum = 0.0
        for (i in 1 until points.size) {
            sum += points[i - 1].distanceTo(points[i])
        }
        return sum
    }

    private fun formatDefaultInstruction(distM: Float, type: ManeuverType, road: String?): String {
        val roadSuffix = if (!road.isNullOrBlank()) " onto $road" else ""
        return when (type) {
            ManeuverType.DEPART -> "Depart"
            ManeuverType.ARRIVE -> "Arrive at destination"
            ManeuverType.TURN_LEFT -> "Turn left$roadSuffix"
            ManeuverType.TURN_RIGHT -> "Turn right$roadSuffix"
            ManeuverType.SLIGHT_LEFT -> "Bear slight left$roadSuffix"
            ManeuverType.SLIGHT_RIGHT -> "Bear slight right$roadSuffix"
            ManeuverType.SHARP_LEFT -> "Make a sharp left$roadSuffix"
            ManeuverType.SHARP_RIGHT -> "Make a sharp right$roadSuffix"
            ManeuverType.U_TURN -> "Make a U-turn$roadSuffix"
            ManeuverType.CONTINUE -> "Continue straight$roadSuffix"
        }
    }

    private fun appendCoordinate(sb: StringBuilder, pt: GeoPoint) {
        sb.append("[")
        sb.append(pt.lon)
        sb.append(",")
        sb.append(pt.lat)
        if (pt.ele != 0.0) {
            sb.append(",")
            sb.append(pt.ele)
        }
        sb.append("]")
    }

    private fun escapeJson(str: String): String =
        str.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
}
