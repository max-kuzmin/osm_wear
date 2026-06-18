package com.osm.wear.data.navigation

import android.content.Context
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import com.osm.wear.domain.model.GpxFile
import com.osm.wear.domain.model.GpxPoint
import com.osm.wear.domain.model.NavigationState
import com.osm.wear.domain.model.NavigationWaypoint
import com.osm.wear.domain.model.UserLocation
import kotlin.math.*

/**
 * Computes turn-by-turn navigation from a [GpxFile] and the user's [UserLocation].
 *
 * A waypoint is classified as a "turn" when the bearing change between the incoming
 * and outgoing segments exceeds [TURN_THRESHOLD_DEG].
 *
 * Alarms (vibration + beep) fire when the user is within [ALARM_RADIUS_M] of a turn
 * and that turn has not yet been alerted.
 */
class NavigationEngine(context: Context) {

    companion object {
        private const val TAG               = "NavigationEngine"
        private const val TURN_THRESHOLD_DEG = 25.0  // degrees of bearing change = "turn"
        private const val ALARM_RADIUS_M     = 30.0  // metres – fire alarm when within this distance
        private const val OFF_TRACK_M        = 80.0  // metres – warn when this far from track
    }

    // ── Alarm hardware ────────────────────────────────────────────────────────

    @Suppress("DEPRECATION")
    private val vibrator: Vibrator? = if (android.os.Build.VERSION.SDK_INT >= 31) {
        (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)
            ?.defaultVibrator
    } else {
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    private val toneGen: ToneGenerator? = try {
        ToneGenerator(AudioManager.STREAM_ALARM, 90)
    } catch (e: Exception) {
        Log.w(TAG, "ToneGenerator unavailable", e)
        null
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Builds a list of [NavigationWaypoint]s from a [GpxFile].
     * Call once when navigation starts; pass the result into [NavigationState].
     */
    fun buildWaypoints(gpxFile: GpxFile): List<NavigationWaypoint> {
        val pts = gpxFile.trackPoints
        if (pts.size < 2) return emptyList()

        return pts.mapIndexed { i, point ->
            val bearingIn  = if (i > 0) bearingDeg(pts[i - 1], point) else 0f
            val bearingOut = if (i < pts.size - 1) bearingDeg(point, pts[i + 1]) else bearingIn
            val distToNext = if (i < pts.size - 1)
                haversineM(point, pts[i + 1]).toFloat() else 0f

            val isTurn = i > 0 && i < pts.size - 1 &&
                    angleDiff(bearingIn, bearingOut) >= TURN_THRESHOLD_DEG

            NavigationWaypoint(
                index           = i,
                point           = point,
                bearingToNext   = bearingOut,
                distanceToNextM = distToNext,
                isTurn          = isTurn
            )
        }
    }

    data class SegmentProjection(
        val projectedPoint: GpxPoint,
        val distanceToSegmentM: Double,
        val fraction: Double
    )

    private fun projectPointToSegment(p: GpxPoint, a: GpxPoint, b: GpxPoint): SegmentProjection {
        val ab = haversineM(a, b)
        if (ab < 1.0) {
            return SegmentProjection(a, haversineM(p, a), 0.0)
        }
        val ap = haversineM(a, p)
        if (ap < 0.1) {
            return SegmentProjection(a, ap, 0.0)
        }
        val bp = haversineM(b, p)
        val cosA = (ab * ab + ap * ap - bp * bp) / (2 * ab * ap)
        val t = (ap * cosA).coerceIn(0.0, ab) / ab
        val projLat = a.lat + t * (b.lat - a.lat)
        val projLon = a.lon + t * (b.lon - a.lon)
        val projPt = GpxPoint(projLat, projLon)
        return SegmentProjection(projPt, haversineM(p, projPt), t)
    }

    /**
     * Updates [state] based on the user's new [location].
     * Fires alarms when a turn waypoint is reached.
     */
    fun update(state: NavigationState, location: UserLocation): NavigationState {
        if (!state.isActive || state.waypoints.isEmpty()) return state

        val userPt = GpxPoint(location.latitude, location.longitude)

        // 1. Find the closest segment on the track.
        // We use a localized window to avoid incorrect snaps on self-crossing routes,
        // but if we are far off track or just starting, we search the entire track.
        val currentSegIdx = state.currentWaypointIndex
        val searchRadius = 10
        val startIdx = maxOf(0, currentSegIdx - 2)
        val endIdx = minOf(state.waypoints.size - 2, currentSegIdx + searchRadius)

        var bestProjection: SegmentProjection? = null
        var bestSegIdx = -1

        // Check local window first
        for (i in startIdx..endIdx) {
            val a = state.waypoints[i].point
            val b = state.waypoints[i + 1].point
            val proj = projectPointToSegment(userPt, a, b)
            if (bestProjection == null || proj.distanceToSegmentM < bestProjection.distanceToSegmentM) {
                bestProjection = proj
                bestSegIdx = i
            }
        }

        // If off track locally, scan the entire track to find the closest segment.
        val localDist = bestProjection?.distanceToSegmentM ?: Double.MAX_VALUE
        if (localDist > 50.0) {
            var globalProjection: SegmentProjection? = null
            var globalSegIdx = -1
            for (i in 0 until state.waypoints.size - 1) {
                val a = state.waypoints[i].point
                val b = state.waypoints[i + 1].point
                val proj = projectPointToSegment(userPt, a, b)
                if (globalProjection == null || proj.distanceToSegmentM < globalProjection.distanceToSegmentM) {
                    globalProjection = proj
                    globalSegIdx = i
                } else if (abs(proj.distanceToSegmentM - globalProjection.distanceToSegmentM) < 5.0) {
                    // Tie-breaker: prefer the segment with the smaller index to prevent jumping ahead on loop routes
                    if (i < globalSegIdx) {
                        globalProjection = proj
                        globalSegIdx = i
                    }
                }
            }
            val globalDist = globalProjection?.distanceToSegmentM ?: Double.MAX_VALUE
            if (globalProjection != null && globalDist < localDist) {
                bestProjection = globalProjection
                bestSegIdx = globalSegIdx
            }
        }

        if (bestProjection == null || bestSegIdx == -1) {
            return state // Fallback
        }

        val projectedPt = bestProjection.projectedPoint
        val distToTrack = bestProjection.distanceToSegmentM
        val isOffTrack = distToTrack > OFF_TRACK_M

        // Next waypoint index that we are approaching
        val nextWpIdx = minOf(bestSegIdx + 1, state.waypoints.size - 1)

        // Find the next TURN waypoint at or after nextWpIdx. Fallback to destination if no turn is left.
        val nextTurnWp = state.waypoints
            .drop(nextWpIdx)
            .firstOrNull { it.isTurn } ?: state.waypoints.last()

        // Calculate distance to the next turn/destination along the track
        var distToNextTurn = 0f
        // Distance from projected point to the next waypoint along the segment
        distToNextTurn = haversineM(projectedPt, state.waypoints[nextWpIdx].point).toFloat()
        // Plus sum of segment distances from nextWpIdx to the turn/destination waypoint
        for (k in nextWpIdx until nextTurnWp.index) {
            distToNextTurn += state.waypoints[k].distanceToNextM
        }

        // Calculate the turn direction relative to the track at the upcoming turn/destination
        var relativeTurnBearing = 0f
        if (nextTurnWp.isTurn) {
            val T = nextTurnWp.index
            val bIn = if (T > 0) state.waypoints[T - 1].bearingToNext else state.waypoints[T].bearingToNext
            val bOut = state.waypoints[T].bearingToNext
            relativeTurnBearing = (bOut - bIn + 360f) % 360f
        }

        // Calculate total remaining distance along the track to the end
        var totalRemaining = haversineM(projectedPt, state.waypoints[nextWpIdx].point).toFloat()
        for (k in nextWpIdx until state.waypoints.size - 1) {
            totalRemaining += state.waypoints[k].distanceToNextM
        }

        // Alarm when approaching a turn (based on distance along track)
        var lastAlerted = state.lastAlertedWaypointIndex
        if (nextTurnWp.isTurn &&
            nextTurnWp.index != lastAlerted &&
            distToNextTurn <= ALARM_RADIUS_M
        ) {
            fireAlarm()
            lastAlerted = nextTurnWp.index
            Log.d(TAG, "Alarm fired at waypoint ${nextTurnWp.index}")
        }

        // Destination reached check (remaining track distance < ALARM_RADIUS_M)
        if (totalRemaining < ALARM_RADIUS_M && nextWpIdx >= state.waypoints.size - 2) {
            fireAlarm()
            Log.d(TAG, "Destination reached")
            return state.copy(isActive = false)
        }

        return state.copy(
            currentWaypointIndex     = bestSegIdx,
            distanceToNextTurnM      = distToNextTurn,
            bearingToNextTurn        = relativeTurnBearing,
            totalRemainingM          = totalRemaining,
            isOffTrack               = isOffTrack,
            lastAlertedWaypointIndex = lastAlerted
        )
    }

    fun release() { toneGen?.release() }

    // ── Private helpers ───────────────────────────────────────────────────────

    private fun findNearestWaypointIndex(
        waypoints: List<NavigationWaypoint>,
        user: GpxPoint,
        fromIndex: Int
    ): Int {
        var bestIdx  = fromIndex
        var bestDist = Double.MAX_VALUE
        val end = minOf(fromIndex + 50, waypoints.size)
        for (i in fromIndex until end) {
            val d = haversineM(user, waypoints[i].point)
            if (d < bestDist) { bestDist = d; bestIdx = i }
        }
        return bestIdx
    }

    private fun distToNearestSegment(track: List<GpxPoint>, user: GpxPoint): Double {
        var min = Double.MAX_VALUE
        for (i in 0 until track.size - 1) {
            val d = pointToSegmentDist(user, track[i], track[i + 1])
            if (d < min) min = d
        }
        return min
    }

    private fun pointToSegmentDist(p: GpxPoint, a: GpxPoint, b: GpxPoint): Double {
        val ab = haversineM(a, b)
        if (ab < 1.0) return haversineM(p, a)
        val ap = haversineM(a, p)
        val bp = haversineM(b, p)
        val cosA = (ab * ab + ap * ap - bp * bp) / (2 * ab * ap)
        val t = (ap * cosA).coerceIn(0.0, ab) / ab
        val projLat = a.lat + t * (b.lat - a.lat)
        val projLon = a.lon + t * (b.lon - a.lon)
        return haversineM(p, GpxPoint(projLat, projLon))
    }

    private fun fireAlarm() {
        try {
            vibrator?.vibrate(
                VibrationEffect.createWaveform(
                    longArrayOf(0, 200, 100, 200, 100, 400),
                    intArrayOf(0, 255, 0, 255, 0, 255),
                    -1
                )
            )
        } catch (e: Exception) { Log.w(TAG, "Vibration failed", e) }
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP2, 600)
        } catch (e: Exception) { Log.w(TAG, "Beep failed", e) }
    }

    // ── Math ──────────────────────────────────────────────────────────────────

    private fun haversineM(a: GpxPoint, b: GpxPoint): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        return r * 2 * atan2(sqrt(h), sqrt(1 - h))
    }

    private fun bearingDeg(from: GpxPoint, to: GpxPoint): Float {
        val dLon = Math.toRadians(to.lon - from.lon)
        val lat1 = Math.toRadians(from.lat)
        val lat2 = Math.toRadians(to.lat)
        val y    = sin(dLon) * cos(lat2)
        val x    = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(dLon)
        return ((Math.toDegrees(atan2(y, x)) + 360) % 360).toFloat()
    }

    private fun angleDiff(a: Float, b: Float): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
