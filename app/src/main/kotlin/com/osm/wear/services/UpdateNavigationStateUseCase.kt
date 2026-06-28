package com.osm.wear.services

import android.util.Log
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.NavigationState
import com.osm.wear.models.SegmentProjection
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IAlertsRepository
import javax.inject.Inject
import kotlin.math.*

class UpdateNavigationStateUseCase @Inject constructor(
    private val alertsRepository: IAlertsRepository
) {
    private val TAG = "UpdateNavStateUseCase"
    private val OFF_TRACK_M = 80.0
    private val ALARM_RADIUS_M = 30.0

    operator fun invoke(state: NavigationState, location: UserLocation): NavigationState {
        if (!state.isActive || state.waypoints.isEmpty()) return state

        val userPt = GpxPoint(location.latitude, location.longitude)

        val currentSegIdx = state.currentWaypointIndex
        val searchRadius = 10
        val startIdx = maxOf(0, currentSegIdx - 2)
        val endIdx = minOf(state.waypoints.size - 2, currentSegIdx + searchRadius)

        var bestProjection: SegmentProjection? = null
        var bestSegIdx = -1

        val origin = state.waypoints.first().point
        val proj = LocalProjection(origin)
        val userPtProj = proj.project(userPt)

        // Check local window first
        for (i in startIdx..endIdx) {
            val a = proj.project(state.waypoints[i].point)
            val b = proj.project(state.waypoints[i + 1].point)
            val proj2D = projectPointToSegment2D(userPtProj, a, b)
            val projPt = proj.unproject(proj2D.projectedPoint)
            val projObj = SegmentProjection(projPt, proj2D.distanceToSegmentM, proj2D.fraction)

            if (bestProjection == null || projObj.distanceToSegmentM < bestProjection.distanceToSegmentM) {
                bestProjection = projObj
                bestSegIdx = i
            }
        }

        // If off track locally, scan the entire track
        val localDist = bestProjection?.distanceToSegmentM ?: Double.MAX_VALUE
        if (localDist > 50.0) {
            var globalProjection: SegmentProjection? = null
            var globalSegIdx = -1

            val maxGoBack = 10
            val maxGoForward = state.waypoints.size / 2

            val globalStartIdx = maxOf(0, currentSegIdx - maxGoBack)
            val globalEndIdx = minOf(state.waypoints.size - 2, currentSegIdx + maxGoForward)

            for (i in globalStartIdx..globalEndIdx) {
                val a = proj.project(state.waypoints[i].point)
                val b = proj.project(state.waypoints[i + 1].point)
                val proj2D = projectPointToSegment2D(userPtProj, a, b)
                val projPt = proj.unproject(proj2D.projectedPoint)
                val projObj = SegmentProjection(projPt, proj2D.distanceToSegmentM, proj2D.fraction)

                if (globalProjection == null || projObj.distanceToSegmentM < globalProjection.distanceToSegmentM) {
                    globalProjection = projObj
                    globalSegIdx = i
                } else if (abs(projObj.distanceToSegmentM - globalProjection.distanceToSegmentM) < 5.0) {
                    if (i < globalSegIdx) {
                        globalProjection = projObj
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
            return state
        }

        val projectedPt = bestProjection.projectedPoint
        val distToTrack = bestProjection.distanceToSegmentM
        val isOffTrack = distToTrack > OFF_TRACK_M

        var lastAlerted = state.lastAlertedWaypointIndex
        lastAlerted = fireSkippedTurnAlarms(bestSegIdx, state, lastAlerted)

        val nextWpIdx = minOf(bestSegIdx + 1, state.waypoints.size - 1)
        val nextTurnWp = state.waypoints
            .drop(nextWpIdx)
            .firstOrNull { it.isTurn } ?: state.waypoints.last()

        var distToNextTurn = haversineM(projectedPt, state.waypoints[nextWpIdx].point).toFloat()
        for (k in nextWpIdx until nextTurnWp.index) {
            distToNextTurn += state.waypoints[k].distanceToNextM
        }

        var relativeTurnBearing = 0f
        if (nextTurnWp.isTurn) {
            relativeTurnBearing = nextTurnWp.turnBearingChange
        }

        var totalRemaining = haversineM(projectedPt, state.waypoints[nextWpIdx].point).toFloat()
        for (k in nextWpIdx until state.waypoints.size - 1) {
            totalRemaining += state.waypoints[k].distanceToNextM
        }

        val isNewTurnTarget = nextTurnWp.index != state.lastNextTurnIndex
        
        var warnedRightAfter = if (isNewTurnTarget) false else state.warnedRightAfterPrevious
        var warned1k = if (isNewTurnTarget) false else state.warned1km
        var warned300 = if (isNewTurnTarget) false else state.warned300m
        var warnedDuring = if (isNewTurnTarget) false else state.warnedDuringTurn

        if (nextTurnWp.isTurn) {
            val direction = if (relativeTurnBearing > 180f) "left" else "right"
            val streetSuffix = nextTurnWp.roadName?.let { " onto $it" } ?: ""
            
            if (!warnedRightAfter) {
                val distStr = formatDistanceForSpeech(distToNextTurn)
                alertsRepository.announce("In $distStr, turn $direction$streetSuffix")
                warnedRightAfter = true
                
                if (distToNextTurn <= 1000f) warned1k = true
                if (distToNextTurn <= 300f) warned300 = true
                if (distToNextTurn <= ALARM_RADIUS_M) {
                    warnedDuring = true
                    lastAlerted = nextTurnWp.index
                }
            }
            
            if (!warned1k && distToNextTurn <= 1000f) {
                alertsRepository.announce("In 1 kilometer, turn $direction$streetSuffix")
                warned1k = true
                if (distToNextTurn <= 300f) warned300 = true
                if (distToNextTurn <= ALARM_RADIUS_M) {
                    warnedDuring = true
                    lastAlerted = nextTurnWp.index
                }
            }
            
            if (!warned300 && distToNextTurn <= 300f) {
                alertsRepository.announce("In 300 meters, turn $direction$streetSuffix")
                warned300 = true
                if (distToNextTurn <= ALARM_RADIUS_M) {
                    warnedDuring = true
                    lastAlerted = nextTurnWp.index
                }
            }
            
            if (!warnedDuring && distToNextTurn <= ALARM_RADIUS_M) {
                fireAlarm("Turn $direction$streetSuffix")
                warnedDuring = true
                lastAlerted = nextTurnWp.index
                Log.d(TAG, "Alarm fired at waypoint ${nextTurnWp.index}")
            }
        }

        if (totalRemaining < ALARM_RADIUS_M && nextWpIdx >= state.waypoints.size - 2) {
            fireAlarm("Destination reached")
            Log.d(TAG, "Destination reached")
            return state.copy(isActive = false)
        }

        return state.copy(
            currentWaypointIndex     = bestSegIdx,
            distanceToNextTurnM      = distToNextTurn,
            bearingToNextTurn        = relativeTurnBearing,
            totalRemainingM          = totalRemaining,
            isOffTrack               = isOffTrack,
            lastAlertedWaypointIndex = lastAlerted,
            lastNextTurnIndex        = nextTurnWp.index,
            warnedRightAfterPrevious = warnedRightAfter,
            warned1km                = warned1k,
            warned300m               = warned300,
            warnedDuringTurn         = warnedDuring
        )
    }

    private data class Point2D(val x: Double, val y: Double) {
        fun distanceTo(other: Point2D): Double = sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
    }

    private class LocalProjection(val origin: GpxPoint) {
        private val latRad = Math.toRadians(origin.lat)
        private val metersPerLat = 111132.9
        private val metersPerLon = 111132.9 * cos(latRad)

        fun project(p: GpxPoint): Point2D = Point2D((p.lon - origin.lon) * metersPerLon, (p.lat - origin.lat) * metersPerLat)
        fun unproject(pt: Point2D): GpxPoint = GpxPoint(origin.lat + pt.y / metersPerLat, origin.lon + pt.x / metersPerLon)
    }

    private data class SegmentProjection2D(
        val projectedPoint: Point2D,
        val distanceToSegmentM: Double,
        val fraction: Double
    )

    private fun projectPointToSegment2D(p: Point2D, a: Point2D, b: Point2D): SegmentProjection2D {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-4) return SegmentProjection2D(a, p.distanceTo(a), 0.0)
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2
        val clampedT = t.coerceIn(0.0, 1.0)
        val projPt = Point2D(a.x + clampedT * dx, a.y + clampedT * dy)
        return SegmentProjection2D(projPt, p.distanceTo(projPt), clampedT)
    }

    private fun formatDistanceForSpeech(distanceM: Float): String {
        return if (distanceM >= 1000f) {
            val km = distanceM / 1000f
            if (abs(km - 1.0f) < 0.05f) {
                "1 kilometer"
            } else {
                val formattedKm = String.format(java.util.Locale.US, "%.1f", km)
                "$formattedKm kilometers"
            }
        } else {
            val roundedMeters = (round(distanceM / 50f) * 50).toInt()
            if (roundedMeters <= 0) "now" else "$roundedMeters meters"
        }
    }

    private fun fireAlarm(message: String) {
        alertsRepository.vibrate(longArrayOf(0, 200, 100, 200, 100, 400))
        alertsRepository.announce(message)
    }

    private fun fireSkippedTurnAlarms(
        bestSegIdx: Int,
        state: NavigationState,
        lastAlerted: Int
    ): Int {
        val prevIdx = state.currentWaypointIndex
        if (bestSegIdx <= prevIdx) return lastAlerted

        var updatedLastAlerted = lastAlerted
        var skippedTurn = false

        for (i in (prevIdx + 1)..minOf(bestSegIdx, state.waypoints.size - 1)) {
            val wp = state.waypoints.getOrNull(i) ?: continue
            if (wp.isTurn && wp.index != updatedLastAlerted) {
                skippedTurn = true
                updatedLastAlerted = wp.index
                Log.d(TAG, "Retroactive alarm for skipped turn at wp ${wp.index}")
            }
        }
        
        if (skippedTurn) {
            fireAlarm("Route segment skipped")
        }
        
        return updatedLastAlerted
    }

    private fun haversineM(a: GpxPoint, b: GpxPoint): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * atan2(sqrt(clampedH), sqrt(1.0 - clampedH))
    }
}
