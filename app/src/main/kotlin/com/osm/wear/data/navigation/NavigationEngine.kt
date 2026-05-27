package com.osm.wear.data.navigation

import com.osm.wear.data.recording.TrackRecorder.Companion.haversineM
import com.osm.wear.domain.model.*
import kotlin.math.*

/**
 * Pure-function navigation engine.
 *
 * Responsibilities:
 *  1. Pre-process a [GpxTrack] into a list of [NavigationWaypoint]s (turns).
 *  2. Given the current [UserLocation], compute the updated [NavigationState].
 *
 * Turn detection uses a bearing-change threshold:
 *  < 15°  → STRAIGHT
 *  15–45° → slight turn (treated as STRAIGHT for simplicity)
 *  45–90° → TURN_LEFT / TURN_RIGHT
 *  > 90°  → SHARP_LEFT / SHARP_RIGHT
 *  > 150° → U_TURN
 */
object NavigationEngine {

    /** Minimum bearing change (degrees) to classify a point as a turn waypoint. */
    private const val TURN_THRESHOLD_DEG = 30.0

    /** Distance ahead (metres) at which the alarm fires before a turn. */
    const val ALARM_DISTANCE_M = 30.0

    /** Snap-to-track radius (metres) — beyond this the user is "off track". */
    const val OFF_TRACK_RADIUS_M = 50.0

    // ── Build waypoints ───────────────────────────────────────────────────────

    /**
     * Converts a [GpxTrack] into a list of [NavigationWaypoint]s.
     * Only points with a significant bearing change are included as turns,
     * plus explicit START and ARRIVE waypoints.
     */
    fun buildWaypoints(track: GpxTrack): List<NavigationWaypoint> {
        val pts = track.points
        if (pts.size < 2) return emptyList()

        val waypoints = mutableListOf<NavigationWaypoint>()
        var cumDist = 0.0

        // Compute per-segment bearings and distances
        val bearings = DoubleArray(pts.size - 1) { i ->
            bearing(pts[i].latitude, pts[i].longitude, pts[i + 1].latitude, pts[i + 1].longitude)
        }
        val segDists = DoubleArray(pts.size - 1) { i ->
            haversineM(pts[i].latitude, pts[i].longitude, pts[i + 1].latitude, pts[i + 1].longitude)
        }

        // START
        waypoints.add(
            NavigationWaypoint(
                index = 0,
                point = pts.first(),
                bearingIn = bearings[0],
                bearingOut = bearings[0],
                turnDirection = TurnDirection.START,
                distanceFromStart = 0.0,
                distanceToNext = segDists[0]
            )
        )

        // Intermediate turns
        for (i in 1 until pts.size - 1) {
            cumDist += segDists[i - 1]
            val bIn  = bearings[i - 1]
            val bOut = bearings[i]
            val delta = bearingDelta(bIn, bOut)

            if (abs(delta) >= TURN_THRESHOLD_DEG) {
                waypoints.add(
                    NavigationWaypoint(
                        index = i,
                        point = pts[i],
                        bearingIn = bIn,
                        bearingOut = bOut,
                        turnDirection = classifyTurn(delta),
                        distanceFromStart = cumDist,
                        distanceToNext = segDists[i]
                    )
                )
            }
        }

        // ARRIVE
        cumDist += segDists.takeLast(1).sum()
        waypoints.add(
            NavigationWaypoint(
                index = pts.size - 1,
                point = pts.last(),
                bearingIn = bearings.last(),
                bearingOut = bearings.last(),
                turnDirection = TurnDirection.ARRIVE,
                distanceFromStart = cumDist,
                distanceToNext = 0.0
            )
        )

        return waypoints
    }

    // ── Update navigation state ───────────────────────────────────────────────

    /**
     * Recomputes [NavigationState] given the current [UserLocation].
     *
     * Advances [nextWaypointIndex] when the user passes a waypoint.
     */
    fun update(current: NavigationState, location: UserLocation): NavigationState {
        if (current.isFinished) return current

        val waypoints = current.waypoints
        var nextIdx = current.nextWaypointIndex

        // Advance past waypoints the user has already passed
        while (nextIdx < waypoints.size) {
            val wp = waypoints[nextIdx]
            val distToWp = haversineM(
                location.latitude, location.longitude,
                wp.point.latitude, wp.point.longitude
            )
            if (distToWp < ALARM_DISTANCE_M * 0.5) {
                nextIdx++
            } else break
        }

        if (nextIdx >= waypoints.size) {
            return current.copy(
                nextWaypointIndex = waypoints.size - 1,
                distanceToNextM = 0.0,
                distanceRemainingM = 0.0,
                isFinished = true
            )
        }

        val nextWp = waypoints[nextIdx]
        val distToNext = haversineM(
            location.latitude, location.longitude,
            nextWp.point.latitude, nextWp.point.longitude
        )

        // Remaining distance = dist to next wp + cumulative dist from next wp to end
        val lastWp = waypoints.last()
        val distRemaining = distToNext +
                (lastWp.distanceFromStart - nextWp.distanceFromStart)

        // Off-track: distance from user to nearest segment
        val offTrack = distanceToTrack(location, current.track)

        return current.copy(
            nextWaypointIndex = nextIdx,
            distanceToNextM = distToNext,
            distanceRemainingM = distRemaining,
            offTrackM = offTrack
        )
    }

    // ── Geometry helpers ──────────────────────────────────────────────────────

    /** Bearing from (lat1,lon1) to (lat2,lon2) in degrees [0, 360). */
    fun bearing(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = sin(dLon) * cos(lat2R)
        val x = cos(lat1R) * sin(lat2R) - sin(lat1R) * cos(lat2R) * cos(dLon)
        return (Math.toDegrees(atan2(y, x)) + 360) % 360
    }

    /**
     * Signed bearing delta in (-180, 180].
     * Positive = right turn, negative = left turn.
     */
    private fun bearingDelta(from: Double, to: Double): Double {
        var d = to - from
        while (d > 180) d -= 360
        while (d < -180) d += 360
        return d
    }

    private fun classifyTurn(delta: Double): TurnDirection = when {
        delta > 150  -> TurnDirection.U_TURN
        delta > 90   -> TurnDirection.SHARP_RIGHT
        delta > 30   -> TurnDirection.TURN_RIGHT
        delta < -150 -> TurnDirection.U_TURN
        delta < -90  -> TurnDirection.SHARP_LEFT
        delta < -30  -> TurnDirection.TURN_LEFT
        else         -> TurnDirection.STRAIGHT
    }

    /** Minimum distance (metres) from [location] to any segment of [track]. */
    private fun distanceToTrack(location: UserLocation, track: GpxTrack): Double {
        val pts = track.points
        if (pts.size < 2) return Double.MAX_VALUE
        var minDist = Double.MAX_VALUE
        for (i in 0 until pts.size - 1) {
            val d = pointToSegmentDistance(
                location.latitude, location.longitude,
                pts[i].latitude, pts[i].longitude,
                pts[i + 1].latitude, pts[i + 1].longitude
            )
            if (d < minDist) minDist = d
        }
        return minDist
    }

    private fun pointToSegmentDistance(
        px: Double, py: Double,
        ax: Double, ay: Double,
        bx: Double, by: Double
    ): Double {
        val abx = bx - ax; val aby = by - ay
        val apx = px - ax; val apy = py - ay
        val t = ((apx * abx + apy * aby) / (abx * abx + aby * aby)).coerceIn(0.0, 1.0)
        val cx = ax + t * abx; val cy = ay + t * aby
        return haversineM(px, py, cx, cy)
    }
}
