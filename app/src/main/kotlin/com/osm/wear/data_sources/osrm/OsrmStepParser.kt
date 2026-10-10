package com.osm.wear.data_sources.osrm

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.RouteStep
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import kotlin.math.abs
import kotlin.math.round

object OsrmStepParser {

    data class RawOsrmStep(
        val polyline: List<GeoPoint>,
        val distanceM: Float,
        val durationS: Float,
        val roadName: String?,
        val maneuverType: String,
        val maneuverModifier: String,
        val maneuverLocation: GeoPoint,
        val bearingBefore: Float,
        val bearingAfter: Float
    )

    fun parseResponse(jsonString: String, routeName: String = "OSRM Route"): NavigationRoute? {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty() || !trimmed.startsWith("{")) return null
        val root = try {
            JSONObject(trimmed)
        } catch (_: Exception) {
            return null
        }

        if (root.optString("code") != "Ok") return null
        val routes = root.optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null

        val primaryRoute = routes.optJSONObject(0) ?: return null
        val totalDistanceM = primaryRoute.optDouble("distance", 0.0).toFloat()
        val totalDurationS = primaryRoute.optDouble("duration", 0.0).toFloat()

        val legs = primaryRoute.optJSONArray("legs") ?: return null
        if (legs.length() == 0) return null
        val primaryLeg = legs.optJSONObject(0) ?: return null

        val stepsList = primaryLeg.optJSONArray("steps") ?: return null
        val rawSteps = mutableListOf<RawOsrmStep>()

        for (i in 0 until stepsList.length()) {
            val stepObj = stepsList.optJSONObject(i) ?: continue
            val distance = stepObj.optDouble("distance", 0.0).toFloat()
            val duration = stepObj.optDouble("duration", 0.0).toFloat()
            val roadName = stepObj.optString("name").trim().ifEmpty { null }

            val geomObj = stepObj.optJSONObject("geometry")
            val coordsList = geomObj?.optJSONArray("coordinates")
            val pts = mutableListOf<GeoPoint>()
            if (coordsList != null) {
                if (coordsList.length() >= 2 && coordsList.opt(0) is Number && coordsList.opt(1) is Number) {
                    val lon = coordsList.optDouble(0, Double.NaN)
                    val lat = coordsList.optDouble(1, Double.NaN)
                    if (isValidCoordinate(lat, lon)) {
                        pts.add(GeoPoint(lat = lat, lon = lon))
                    }
                } else {
                    for (c in 0 until coordsList.length()) {
                        val coord = coordsList.optJSONArray(c) ?: continue
                        if (coord.length() >= 2) {
                            val lon = coord.optDouble(0, Double.NaN)
                            val lat = coord.optDouble(1, Double.NaN)
                            if (isValidCoordinate(lat, lon)) {
                                pts.add(GeoPoint(lat = lat, lon = lon))
                            }
                        }
                    }
                }
            }

            val maneuverObj = stepObj.optJSONObject("maneuver")
            val mType = maneuverObj?.optString("type") ?: ""
            val mModifier = maneuverObj?.optString("modifier") ?: ""
            val bBefore = maneuverObj?.optDouble("bearing_before", 0.0)?.toFloat() ?: 0f
            val bAfter = maneuverObj?.optDouble("bearing_after", 0.0)?.toFloat() ?: 0f

            val locList = maneuverObj?.optJSONArray("location")
            val locFromList = if (locList != null && locList.length() >= 2) {
                val lon = locList.optDouble(0, Double.NaN)
                val lat = locList.optDouble(1, Double.NaN)
                if (isValidCoordinate(lat, lon)) {
                    GeoPoint(lat = lat, lon = lon)
                } else null
            } else null

            val mLocation = locFromList
                ?: pts.firstOrNull()
                ?: rawSteps.lastOrNull()?.polyline?.lastOrNull()
                ?: GeoPoint(0.0, 0.0)

            if (pts.isEmpty()) {
                if (mLocation.lat != 0.0 || mLocation.lon != 0.0) {
                    pts.add(mLocation)
                } else if (rawSteps.isNotEmpty() && rawSteps.last().polyline.isNotEmpty()) {
                    pts.add(rawSteps.last().polyline.last())
                }
            }

            rawSteps.add(
                RawOsrmStep(
                    polyline = pts,
                    distanceM = distance,
                    durationS = duration,
                    roadName = roadName,
                    maneuverType = mType,
                    maneuverModifier = mModifier,
                    maneuverLocation = mLocation,
                    bearingBefore = bBefore,
                    bearingAfter = bAfter
                )
            )
        }

        if (rawSteps.isEmpty()) return null
        if (rawSteps.all { it.polyline.isEmpty() }) return null

        val routeSteps = buildTerminatingSteps(rawSteps)

        return NavigationRoute(
            id = UUID.randomUUID().toString(),
            name = routeName,
            steps = routeSteps,
            totalDistanceM = totalDistanceM,
            totalDurationS = totalDurationS
        )
    }

    private fun isValidCoordinate(lat: Double, lon: Double): Boolean {
        return !lat.isNaN() && !lat.isInfinite() &&
                !lon.isNaN() && !lon.isInfinite() &&
                lat in -90.0..90.0 && lon in -180.0..180.0
    }

    private fun buildTerminatingSteps(rawSteps: List<RawOsrmStep>): List<RouteStep> {
        val result = mutableListOf<RouteStep>()
        val accumulatedPolyline = mutableListOf<GeoPoint>()
        var accumulatedDistance = 0f
        var accumulatedDuration = 0f
        var accumulatedRoadName: String? = null

        for (i in 0 until rawSteps.size) {
            val raw = rawSteps[i]
            val isLast = (i == rawSteps.size - 1)

            // If a zero-length arrival step exists at the end, it was already associated with the preceding step
            if (isLast && raw.distanceM == 0f && raw.maneuverType.lowercase() == "arrive" && result.isNotEmpty()) {
                continue
            }

            val upcomingManeuverRaw = if (!isLast) rawSteps[i + 1] else raw

            var bBefore = upcomingManeuverRaw.bearingBefore
            var bAfter = upcomingManeuverRaw.bearingAfter

            if (bBefore == 0f && bAfter == 0f && !isLast) {
                if (raw.polyline.size >= 2) {
                    val p1 = raw.polyline[raw.polyline.size - 2]
                    val p2 = raw.polyline.last()
                    bBefore = p1.bearingTo(p2)
                }
                if (upcomingManeuverRaw.polyline.size >= 2) {
                    val p1 = upcomingManeuverRaw.polyline[0]
                    val p2 = upcomingManeuverRaw.polyline[1]
                    bAfter = p1.bearingTo(p2)
                }
            }

            val relativeAngle = calculateRelativeAngle(
                bBefore,
                bAfter
            )

            val maneuverType = if (isLast) {
                ManeuverType.ARRIVE
            } else {
                parseManeuverType(
                    upcomingManeuverRaw.maneuverType,
                    upcomingManeuverRaw.maneuverModifier,
                    relativeAngle
                )
            }

            val targetRoadName = if (isLast) raw.roadName else upcomingManeuverRaw.roadName

            val shouldTerminate = maneuverType.isTurn || maneuverType == ManeuverType.ARRIVE || isLast

            if (!shouldTerminate) {
                // Non-turn continuation (e.g. road name change, continuing straight through crossing):
                // Accumulate into running buffer so the user isn't interrupted by pseudo-steps
                appendPointsWithoutDuplicate(accumulatedPolyline, raw.polyline)
                accumulatedDistance += raw.distanceM
                accumulatedDuration += raw.durationS
                if (accumulatedRoadName == null) {
                    accumulatedRoadName = raw.roadName
                }
                continue
            }

            val stepPolyline = mutableListOf<GeoPoint>()
            appendPointsWithoutDuplicate(stepPolyline, accumulatedPolyline)
            appendPointsWithoutDuplicate(stepPolyline, raw.polyline)

            val stepDistance = accumulatedDistance + raw.distanceM
            val stepDuration = accumulatedDuration + raw.durationS
            val stepRoadName = accumulatedRoadName ?: raw.roadName

            accumulatedPolyline.clear()
            accumulatedDistance = 0f
            accumulatedDuration = 0f
            accumulatedRoadName = null

            val instruction = formatStepInstruction(stepDistance, maneuverType, targetRoadName)

            val junctionLocation = if (isLast) {
                stepPolyline.lastOrNull() ?: raw.polyline.lastOrNull() ?: raw.maneuverLocation
            } else {
                if (upcomingManeuverRaw.maneuverLocation.lat != 0.0 || upcomingManeuverRaw.maneuverLocation.lon != 0.0) {
                    upcomingManeuverRaw.maneuverLocation
                } else {
                    raw.polyline.lastOrNull()
                        ?: upcomingManeuverRaw.polyline.firstOrNull()
                        ?: GeoPoint(0.0, 0.0)
                }
            }

            val maneuver = Maneuver(
                type = maneuverType,
                location = junctionLocation,
                bearingBefore = bBefore,
                bearingAfter = bAfter,
                relativeAngle = relativeAngle,
                roadName = targetRoadName,
                instruction = instruction
            )

            result.add(
                RouteStep(
                    index = result.size,
                    polyline = stepPolyline,
                    distanceM = stepDistance,
                    durationS = stepDuration,
                    roadName = stepRoadName,
                    maneuver = maneuver
                )
            )
        }

        return result
    }

    private fun appendPointsWithoutDuplicate(dest: MutableList<GeoPoint>, src: List<GeoPoint>) {
        for (pt in src) {
            if (dest.isEmpty() || dest.last() != pt) {
                dest.add(pt)
            }
        }
    }

    fun parseManeuverType(
        rawType: String?,
        rawModifier: String?,
        relativeAngle: Float = 0f
    ): ManeuverType {
        val type = rawType?.lowercase()?.trim() ?: ""
        val modifier = rawModifier?.lowercase()?.trim()?.replace("_", " ") ?: ""

        if (type == "depart") return ManeuverType.DEPART
        if (type == "arrive") return ManeuverType.ARRIVE

        when {
            modifier.contains("uturn") || modifier.contains("u turn") -> return ManeuverType.U_TURN
            modifier.contains("sharp left") -> return ManeuverType.SHARP_LEFT
            modifier.contains("sharp right") -> return ManeuverType.SHARP_RIGHT
            modifier.contains("slight left") -> return ManeuverType.SLIGHT_LEFT
            modifier.contains("slight right") -> return ManeuverType.SLIGHT_RIGHT
            modifier == "left" -> return ManeuverType.TURN_LEFT
            modifier == "right" -> return ManeuverType.TURN_RIGHT
            modifier == "straight" -> return ManeuverType.CONTINUE
        }

        return when (type) {
            "turn", "fork", "merge", "end of road", "roundabout", "rotary", "roundabout turn" -> {
                mapAngleToManeuverType(relativeAngle)
            }
            "continue", "new name", "use lane", "notification" -> ManeuverType.CONTINUE
            "" -> mapAngleToManeuverType(relativeAngle)
            else -> mapAngleToManeuverType(relativeAngle)
        }
    }

    fun mapAngleToManeuverType(relativeAngle: Float): ManeuverType {
        val absAngle = abs(relativeAngle)
        val isLeft = relativeAngle < 0f
        return when {
            absAngle >= 150f -> ManeuverType.U_TURN
            absAngle >= 105f -> if (isLeft) ManeuverType.SHARP_LEFT else ManeuverType.SHARP_RIGHT
            absAngle >= 45f -> if (isLeft) ManeuverType.TURN_LEFT else ManeuverType.TURN_RIGHT
            absAngle >= 15f -> if (isLeft) ManeuverType.SLIGHT_LEFT else ManeuverType.SLIGHT_RIGHT
            else -> ManeuverType.CONTINUE
        }
    }

    fun calculateAzimuth(a: GeoPoint, b: GeoPoint): Float = a.bearingTo(b)

    fun calculateRelativeAngle(bearingBefore: Float, bearingAfter: Float): Float {
        return (((bearingAfter - bearingBefore + 180f) % 360f + 360f) % 360f - 180f)
    }

    fun formatActionPhrase(type: ManeuverType, roadName: String?): String {
        val roadSuffix = if (!roadName.isNullOrBlank()) " onto $roadName" else ""
        return when (type) {
            ManeuverType.DEPART -> if (!roadName.isNullOrBlank()) "Depart onto $roadName" else "Depart"
            ManeuverType.ARRIVE -> if (!roadName.isNullOrBlank()) "Arrive at destination on $roadName" else "Arrive at destination"
            ManeuverType.TURN_LEFT -> "Turn left$roadSuffix"
            ManeuverType.TURN_RIGHT -> "Turn right$roadSuffix"
            ManeuverType.SLIGHT_LEFT -> "Bear slight left$roadSuffix"
            ManeuverType.SLIGHT_RIGHT -> "Bear slight right$roadSuffix"
            ManeuverType.SHARP_LEFT -> "Make a sharp left$roadSuffix"
            ManeuverType.SHARP_RIGHT -> "Make a sharp right$roadSuffix"
            ManeuverType.U_TURN -> "Make a U-turn$roadSuffix"
            ManeuverType.CONTINUE -> if (!roadName.isNullOrBlank()) "Continue onto $roadName" else "Continue straight"
        }
    }

    fun formatStepInstruction(distanceM: Float, type: ManeuverType, roadName: String?): String {
        if (distanceM <= 30f) {
            return formatActionPhrase(type, roadName)
        }
        val distSpeech = formatDistanceForSpeech(distanceM)
        val action = formatActionPhrase(type, roadName).replaceFirstChar { it.lowercase() }
        return "In $distSpeech, $action"
    }

    fun formatDistanceForSpeech(distanceM: Float): String {
        val nonNegativeDist = maxOf(0f, distanceM)
        return if (nonNegativeDist >= 1000f) {
            if (abs(nonNegativeDist - 1000f) < 50f) {
                "1 kilometer"
            } else {
                val roundedKm = Math.round(nonNegativeDist / 100.0) / 10.0
                val formattedKm = String.format(java.util.Locale.US, "%.1f", roundedKm)
                "$formattedKm kilometers"
            }
        } else {
            val roundedMeters = maxOf(50, (round(nonNegativeDist / 50f) * 50).toInt())
            if (roundedMeters >= 1000) {
                "1 kilometer"
            } else {
                "$roundedMeters meters"
            }
        }
    }
}
