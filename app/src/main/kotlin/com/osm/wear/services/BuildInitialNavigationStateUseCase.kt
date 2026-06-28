package com.osm.wear.services

import android.util.Log
import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.NavigationState
import com.osm.wear.models.NavigationWaypoint
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IRegionRepository
import java.io.File
import javax.inject.Inject
import kotlin.math.*

class BuildInitialNavigationStateUseCase @Inject constructor(
    private val matchTrackToMapUseCase: MatchTrackToMapUseCase,
    private val preferencesRepository: IPreferencesRepository,
    private val regionRepository: IRegionRepository,
    private val checkGpxCoverageUseCase: CheckGpxCoverageUseCase
) {
    private val TAG = "BuildInitialNavState"
    private val TURN_THRESHOLD_DEG = 35.0

    operator fun invoke(gpx: GpxFile): Result<NavigationState> {
        val mapFile = regionRepository.getActiveMapFile()
        val navMode = preferencesRepository.getNavigationMode()

        if (navMode != NavigationMode.GPX_ONLY && (mapFile == null || !mapFile.exists())) {
            return Result.failure(Exception("Download a map for this region first"))
        }

        val isCovered = checkGpxCoverageUseCase(gpx)
        if (navMode != NavigationMode.GPX_ONLY && !isCovered) {
            return Result.failure(Exception("GPX track is outside the downloaded map area"))
        }

        val waypoints = buildWaypoints(gpx, mapFile, navMode)
        if (waypoints.isEmpty()) {
            return Result.failure(
                Exception(
                    if (navMode == NavigationMode.GPX_ONLY) {
                        "Failed to build navigation state"
                    } else {
                        "Failed to map route to roads"
                    }
                )
            )
        }

        val state = NavigationState(
            isActive = true,
            gpxFile = gpx,
            waypoints = waypoints,
            currentWaypointIndex = 0,
            distanceToNextTurnM = waypoints.first().distanceToNextM,
            bearingToNextTurn = waypoints.first().bearingToNext,
            totalRemainingM = waypoints.sumOf { it.distanceToNextM.toDouble() }.toFloat(),
            isOffTrack = false,
            lastAlertedWaypointIndex = -1
        )
        return Result.success(state)
    }

    private data class Point2D(val x: Double, val y: Double) {
        fun distanceTo(other: Point2D): Double = sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
    }

    private class LocalProjection(val origin: GpxPoint) {
        private val latRad = Math.toRadians(origin.lat)
        private val metersPerLat = 111132.9
        private val metersPerLon = 111132.9 * cos(latRad)

        fun project(p: GpxPoint): Point2D = Point2D((p.lon - origin.lon) * metersPerLon, (p.lat - origin.lat) * metersPerLat)
    }

    private data class IndexedPoint(val originalIndex: Int, val point: GpxPoint, val projected: Point2D)

    private fun perpendicularDistance2D(p: Point2D, a: Point2D, b: Point2D): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-4) return p.distanceTo(a)
        val numerator = abs((p.x - a.x) * dy - (p.y - a.y) * dx)
        return numerator / sqrt(len2)
    }

    private fun simplifyRdp(points: List<GpxPoint>, epsilon: Double): List<IndexedPoint> {
        if (points.size < 3) {
            val origin = points.firstOrNull() ?: GpxPoint(0.0, 0.0)
            val proj = LocalProjection(origin)
            return points.mapIndexed { idx, pt -> IndexedPoint(idx, pt, proj.project(pt)) }
        }

        val origin = points.first()
        val proj = LocalProjection(origin)
        val projectedPoints = points.mapIndexed { idx, pt -> IndexedPoint(idx, pt, proj.project(pt)) }

        val keep = BooleanArray(points.size)
        keep[0] = true
        keep[points.size - 1] = true

        simplifyRdpStep(projectedPoints, 0, points.size - 1, epsilon, keep)

        val result = mutableListOf<IndexedPoint>()
        for (i in points.indices) {
            if (keep[i]) {
                result.add(projectedPoints[i])
            }
        }
        return result
    }

    private fun simplifyRdpStep(
        pts: List<IndexedPoint>,
        start: Int,
        end: Int,
        epsilon: Double,
        keep: BooleanArray
    ) {
        if (end <= start + 1) return

        var maxDist = 0.0
        var index = -1
        val a = pts[start].projected
        val b = pts[end].projected

        for (i in start + 1 until end) {
            val p = pts[i].projected
            val dist = perpendicularDistance2D(p, a, b)
            if (dist > maxDist) {
                maxDist = dist
                index = i
            }
        }

        if (maxDist > epsilon) {
            keep[index] = true
            simplifyRdpStep(pts, start, index, epsilon, keep)
            simplifyRdpStep(pts, index, end, epsilon, keep)
        }
    }

    private fun findPointAlongTrackBackward(
        pts: List<IndexedPoint>,
        cumDist: DoubleArray,
        startIndex: Int,
        distance: Double
    ): Point2D {
        val targetDist = cumDist[startIndex] - distance
        if (targetDist <= 0.0) return pts.first().projected
        var j = startIndex
        while (j > 0 && cumDist[j] > targetDist) {
            j--
        }
        val dSegment = cumDist[j + 1] - cumDist[j]
        if (dSegment < 1e-4) return pts[j].projected
        val ratio = (targetDist - cumDist[j]) / dSegment
        val a = pts[j].projected
        val b = pts[j + 1].projected
        return Point2D(a.x + ratio * (b.x - a.x), a.y + ratio * (b.y - a.y))
    }

    private fun findPointAlongTrackForward(
        pts: List<IndexedPoint>,
        cumDist: DoubleArray,
        startIndex: Int,
        distance: Double
    ): Point2D {
        val targetDist = cumDist[startIndex] + distance
        val maxDist = cumDist.last()
        if (targetDist >= maxDist) return pts.last().projected
        var j = startIndex
        while (j < pts.size - 1 && cumDist[j] < targetDist) {
            j++
        }
        val dSegment = cumDist[j] - cumDist[j - 1]
        if (dSegment < 1e-4) return pts[j].projected
        val ratio = (targetDist - cumDist[j - 1]) / dSegment
        val a = pts[j - 1].projected
        val b = pts[j].projected
        return Point2D(a.x + ratio * (b.x - a.x), a.y + ratio * (b.y - a.y))
    }

    private fun bearingDeg2D(from: Point2D, to: Point2D): Float {
        val dx = to.x - from.x
        val dy = to.y - from.y
        val rad = atan2(dx, dy)
        return ((Math.toDegrees(rad) + 360.0) % 360.0).toFloat()
    }

    private fun buildWaypoints(gpxFile: GpxFile, mapFile: File?, navigationMode: NavigationMode): List<NavigationWaypoint> {
        val pts = gpxFile.trackPoints
        if (pts.size < 2) return emptyList()

        if (navigationMode != NavigationMode.GPX_ONLY && mapFile != null && mapFile.exists()) {
            try {
                val roadWaypoints = matchTrackToMapUseCase(pts, navigationMode)
                if (roadWaypoints.isNotEmpty()) {
                    Log.d(TAG, "Using road-aware waypoints (${roadWaypoints.count { it.isTurn }} turns)")
                    return roadWaypoints
                }
                Log.w(TAG, "Road matching produced no results")
            } catch (e: Exception) {
                Log.e(TAG, "Road matching failed", e)
            }
            Log.w(TAG, "Road mapping failed or returned no results; aborting start")
            return emptyList()
        }

        val simplifiedPts = simplifyRdp(pts, 3.0)
        val finalPts = if (simplifiedPts.size < 2) {
            val origin = pts.first()
            val proj = LocalProjection(origin)
            pts.mapIndexed { idx, pt -> IndexedPoint(idx, pt, proj.project(pt)) }
        } else {
            simplifiedPts
        }

        val n = finalPts.size
        val cumDist = DoubleArray(n)
        cumDist[0] = 0.0
        for (i in 1 until n) {
            cumDist[i] = cumDist[i - 1] + finalPts[i - 1].projected.distanceTo(finalPts[i].projected)
        }

        val distShort = 15.0
        val distLong = 40.0
        val turnAngles = DoubleArray(n)
        val isTurnCandidate = BooleanArray(n)
        val turnBearingChanges = FloatArray(n)

        for (i in 1 until n - 1) {
            val pt = finalPts[i].projected
            val ptBackShort = findPointAlongTrackBackward(finalPts, cumDist, i, distShort)
            val ptAheadShort = findPointAlongTrackForward(finalPts, cumDist, i, distShort)
            val bInShort = bearingDeg2D(ptBackShort, pt)
            val bOutShort = bearingDeg2D(pt, ptAheadShort)
            val angleShort = angleDiff(bInShort, bOutShort)

            val ptBackLong = findPointAlongTrackBackward(finalPts, cumDist, i, distLong)
            val ptAheadLong = findPointAlongTrackForward(finalPts, cumDist, i, distLong)
            val bInLong = bearingDeg2D(ptBackLong, pt)
            val bOutLong = bearingDeg2D(pt, ptAheadLong)
            val angleLong = angleDiff(bInLong, bOutLong)

            val meetsThreshold = angleLong >= TURN_THRESHOLD_DEG
            val isConcentrated = (angleLong > 0.0 && (angleShort / angleLong) >= 0.70) || (angleShort >= 40.0)

            if (meetsThreshold && isConcentrated) {
                isTurnCandidate[i] = true
                turnAngles[i] = angleLong
                turnBearingChanges[i] = ((bOutLong - bInLong + 360f) % 360f)
            }
        }

        val isTurn = BooleanArray(n)
        val nmsDistance = 20.0
        for (i in 1 until n - 1) {
            if (!isTurnCandidate[i]) continue

            val currentAngle = turnAngles[i]
            var isMax = true

            var j = i - 1
            while (j > 0 && cumDist[i] - cumDist[j] < nmsDistance) {
                if (isTurnCandidate[j] && turnAngles[j] > currentAngle) {
                    isMax = false
                    break
                }
                j--
            }

            if (!isMax) continue

            j = i + 1
            while (j < n - 1 && cumDist[j] - cumDist[i] < nmsDistance) {
                if (isTurnCandidate[j] && turnAngles[j] >= currentAngle) {
                    isMax = false
                    break
                }
                j++
            }

            if (isMax) {
                isTurn[i] = true
            }
        }

        val waypoints = mutableListOf<NavigationWaypoint>()
        for (i in 0 until n) {
            val indexedPt = finalPts[i]
            val bearingIn  = if (i > 0) bearingDeg2D(finalPts[i - 1].projected, indexedPt.projected) else 0f
            val bearingOut = if (i < n - 1) bearingDeg2D(indexedPt.projected, finalPts[i + 1].projected) else bearingIn
            val distToNext = if (i < n - 1) indexedPt.projected.distanceTo(finalPts[i + 1].projected).toFloat() else 0f

            waypoints.add(
                NavigationWaypoint(
                    index             = i,
                    rawIndex          = indexedPt.originalIndex,
                    point             = indexedPt.point,
                    bearingToNext     = bearingOut,
                    distanceToNextM   = distToNext,
                    isTurn            = isTurn[i],
                    turnBearingChange = if (isTurn[i]) turnBearingChanges[i] else 0f
                )
            )
        }
        return waypoints
    }

    private fun angleDiff(a: Float, b: Float): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}
