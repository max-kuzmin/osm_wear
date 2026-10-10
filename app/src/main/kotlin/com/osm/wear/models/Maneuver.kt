package com.osm.wear.models

import kotlin.math.*

/**
 * Standard maneuver types for discrete step-based navigation.
 * Corresponds to OSRM maneuver actions and turn classifications.
 */
enum class ManeuverType {
    DEPART,
    ARRIVE,
    CONTINUE,
    TURN_LEFT,
    TURN_RIGHT,
    SLIGHT_LEFT,
    SLIGHT_RIGHT,
    SHARP_LEFT,
    SHARP_RIGHT,
    U_TURN;

    val isTurn: Boolean
        get() = this != DEPART && this != ARRIVE && this != CONTINUE

    val isLeft: Boolean
        get() = this == TURN_LEFT || this == SLIGHT_LEFT || this == SHARP_LEFT

    val isRight: Boolean
        get() = this == TURN_RIGHT || this == SLIGHT_RIGHT || this == SHARP_RIGHT

    val isArriveOrDepart: Boolean
        get() = this == DEPART || this == ARRIVE

    companion object {
        /**
         * Resolves domain ManeuverType from OSRM maneuver type and modifier strings.
         * Falls back to angular classification if modifier is ambiguous or null.
         */
        fun fromOsrm(type: String?, modifier: String?, relativeAngle: Float = 0f): ManeuverType {
            val normalizedType = type?.lowercase()?.trim() ?: ""
            val normalizedMod = modifier?.lowercase()?.trim()

            if (normalizedType == "depart") return DEPART
            if (normalizedType == "arrive") return ARRIVE

            return when (normalizedMod) {
                "uturn" -> U_TURN
                "sharp left" -> SHARP_LEFT
                "left" -> TURN_LEFT
                "slight left" -> SLIGHT_LEFT
                "straight" -> CONTINUE
                "slight right" -> SLIGHT_RIGHT
                "right" -> TURN_RIGHT
                "sharp right" -> SHARP_RIGHT
                else -> {
                    when {
                        relativeAngle <= -160f || relativeAngle >= 160f -> U_TURN
                        relativeAngle in -160f..-115f -> SHARP_LEFT
                        relativeAngle in -115f..-60f -> TURN_LEFT
                        relativeAngle in -60f..-20f -> SLIGHT_LEFT
                        relativeAngle in -20f..20f -> CONTINUE
                        relativeAngle in 20f..60f -> SLIGHT_RIGHT
                        relativeAngle in 60f..115f -> TURN_RIGHT
                        relativeAngle in 115f..160f -> SHARP_RIGHT
                        else -> CONTINUE
                    }
                }
            }
        }
    }
}

/**
 * An explicit decision or turn point at a road junction or route milestone.
 */
data class Maneuver(
    val type: ManeuverType,
    val location: GeoPoint,
    val bearingBefore: Float = 0f,
    val bearingAfter: Float = 0f,
    val relativeAngle: Float = 0f,
    val roadName: String? = null,
    val instruction: String = ""
) {
    val isTurn: Boolean
        get() = type.isTurn

    val visualAngle: Float
        get() = when (type) {
            ManeuverType.TURN_LEFT -> if (relativeAngle < -15f) relativeAngle else -90f
            ManeuverType.TURN_RIGHT -> if (relativeAngle > 15f) relativeAngle else 90f
            ManeuverType.SLIGHT_LEFT -> if (relativeAngle < -5f) relativeAngle else -45f
            ManeuverType.SLIGHT_RIGHT -> if (relativeAngle > 5f) relativeAngle else 45f
            ManeuverType.SHARP_LEFT -> if (relativeAngle < -90f) relativeAngle else -135f
            ManeuverType.SHARP_RIGHT -> if (relativeAngle > 90f) relativeAngle else 135f
            ManeuverType.U_TURN -> 180f
            else -> relativeAngle
        }
}

/**
 * A discrete navigation step representing travel along a polyline
 * terminating at an explicit maneuver junction point.
 */
data class RouteStep(
    val index: Int,
    val polyline: List<GeoPoint>,
    val distanceM: Float,
    val durationS: Float = 0f,
    val roadName: String? = null,
    val maneuver: Maneuver
) {
    val startPoint: GeoPoint?
        get() = polyline.firstOrNull()

    val endPoint: GeoPoint?
        get() = polyline.lastOrNull()
}

/**
 * Complete navigation route composed of ordered discrete steps.
 */
data class NavigationRoute(
    val id: String,
    val name: String,
    val steps: List<RouteStep>,
    val totalDistanceM: Float,
    val totalDurationS: Float = 0f
) {
    val fullPolyline: List<GeoPoint>
        get() {
            val result = mutableListOf<GeoPoint>()
            for (step in steps) {
                for (point in step.polyline) {
                    if (result.lastOrNull() != point) {
                        result.add(point)
                    }
                }
            }
            return result
        }
}
