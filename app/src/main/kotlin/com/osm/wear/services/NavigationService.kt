package com.osm.wear.services

import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.tts.TextToSpeech
import android.util.Log
import androidx.core.content.ContextCompat
import com.osm.wear.models.GpxFile
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.UserLocation
import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.NavigationState
import com.osm.wear.models.NavigationWaypoint
import com.osm.wear.models.SegmentProjection
import dagger.hilt.android.qualifiers.ApplicationContext
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import androidx.core.app.NotificationCompat
import com.osm.wear.presentation.MainActivity
import java.io.File
import java.util.Locale
import javax.inject.Inject
import kotlin.math.*

class NavigationService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val trackToMapMatcher: ITrackToMapMatcherService
) : INavigationService, TextToSpeech.OnInitListener {

    companion object {
        private const val TAG               = "NavigationService"
        private const val TURN_THRESHOLD_DEG = 35.0  // degrees of bearing change = "turn"
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

    private var tts: TextToSpeech? = null
    private var isTtsInitialized = false
    
    private var alertMode: NavigationAlertMode = NavigationAlertMode.VOICE

    init {
        tts = TextToSpeech(context, this)
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.ENGLISH
            isTtsInitialized = true
        } else {
            Log.e(TAG, "TTS Initialization failed")
        }
    }

    override fun startForegroundService() {
        val serviceIntent = Intent(context, com.osm.wear.services.NavigationForegroundService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }

    override fun stopForegroundService() {
        val serviceIntent = Intent(context, com.osm.wear.services.NavigationForegroundService::class.java)
        context.stopService(serviceIntent)
    }

    override fun setAlertMode(mode: NavigationAlertMode) {
        alertMode = mode
    }

    override fun announce(message: String) {
        if (alertMode != NavigationAlertMode.VOICE) return
        if (isTtsInitialized && tts != null) {
            tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
        } else {
            showDeviceNotification(message)
        }
    }

    override fun release() {
        tts?.stop()
        tts?.shutdown()
    }

    override fun buildInitialNavigationState(gpx: GpxFile, mapFile: File?, navigationMode: NavigationMode): NavigationState? {
        val waypoints = buildWaypoints(gpx, mapFile, navigationMode)
        if (waypoints.isEmpty()) return null
        return NavigationState(
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
    }

    private data class Point2D(val x: Double, val y: Double) {
        fun distanceTo(other: Point2D): Double {
            return sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
        }
    }

    private class LocalProjection(val origin: GpxPoint) {
        private val latRad = Math.toRadians(origin.lat)
        private val metersPerLat = 111132.9
        private val metersPerLon = 111132.9 * cos(latRad)

        fun project(p: GpxPoint): Point2D {
            return Point2D((p.lon - origin.lon) * metersPerLon, (p.lat - origin.lat) * metersPerLat)
        }

        fun unproject(pt: Point2D): GpxPoint {
            return GpxPoint(origin.lat + pt.y / metersPerLat, origin.lon + pt.x / metersPerLon)
        }
    }

    private data class IndexedPoint(val originalIndex: Int, val point: GpxPoint, val projected: Point2D)

    private data class SegmentProjection2D(
        val projectedPoint: Point2D,
        val distanceToSegmentM: Double,
        val fraction: Double
    )

    private fun perpendicularDistance2D(p: Point2D, a: Point2D, b: Point2D): Double {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-4) {
            return p.distanceTo(a)
        }
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

    private fun projectPointToSegment2D(p: Point2D, a: Point2D, b: Point2D): SegmentProjection2D {
        val dx = b.x - a.x
        val dy = b.y - a.y
        val len2 = dx * dx + dy * dy
        if (len2 < 1e-4) {
            return SegmentProjection2D(a, p.distanceTo(a), 0.0)
        }
        val t = ((p.x - a.x) * dx + (p.y - a.y) * dy) / len2
        val clampedT = t.coerceIn(0.0, 1.0)
        val projPt = Point2D(a.x + clampedT * dx, a.y + clampedT * dy)
        return SegmentProjection2D(projPt, p.distanceTo(projPt), clampedT)
    }

    private fun buildWaypoints(gpxFile: GpxFile, mapFile: File?, navigationMode: NavigationMode): List<NavigationWaypoint> {
        val pts = gpxFile.trackPoints
        if (pts.size < 2) return emptyList()

        // ── Road-aware path: extract road network and match track ─────────
        if (navigationMode != NavigationMode.GPX_ONLY && mapFile != null && mapFile.exists()) {
            try {
                val roadWaypoints = trackToMapMatcher.matchTrackToMap(pts, navigationMode)
                if (roadWaypoints.isNotEmpty()) {
                    Log.d(TAG, "Using road-aware waypoints (${roadWaypoints.count { it.isTurn }} turns)")
                    return roadWaypoints
                }
                Log.w(TAG, "Road matching produced no results")
            } catch (e: Exception) {
                Log.e(TAG, "Road matching failed", e)
            }

            // All road-aware modes (WALKING, CYCLING, DRIVING) MUST fail if road matching failed.
            Log.w(TAG, "Road mapping failed or returned no results; aborting start")
            return emptyList()
        } else {
            Log.d(TAG, "No map file available or GPX points only mode, using RDP geometric waypoints")
        }

        // ── Fallback: RDP-based geometric turn detection ──────────────────
        // Simplify trackpoints using RDP with epsilon = 3.0 meters
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
            
            // Short window (15m) for measuring immediate/sharp corner turn
            val ptBackShort = findPointAlongTrackBackward(finalPts, cumDist, i, distShort)
            val ptAheadShort = findPointAlongTrackForward(finalPts, cumDist, i, distShort)
            val bInShort = bearingDeg2D(ptBackShort, pt)
            val bOutShort = bearingDeg2D(pt, ptAheadShort)
            val angleShort = angleDiff(bInShort, bOutShort)

            // Long window (40m) for measuring overall heading change
            val ptBackLong = findPointAlongTrackBackward(finalPts, cumDist, i, distLong)
            val ptAheadLong = findPointAlongTrackForward(finalPts, cumDist, i, distLong)
            val bInLong = bearingDeg2D(ptBackLong, pt)
            val bOutLong = bearingDeg2D(pt, ptAheadLong)
            val angleLong = angleDiff(bInLong, bOutLong)

            // Classifier logic: turn must be significant in the long window,
            // and concentrated within the short window (ratio >= 0.70) or very sharp on its own (>= 40°).
            val meetsThreshold = angleLong >= TURN_THRESHOLD_DEG
            val isConcentrated = (angleLong > 0.0 && (angleShort / angleLong) >= 0.70) || (angleShort >= 40.0)

            if (meetsThreshold && isConcentrated) {
                isTurnCandidate[i] = true
                turnAngles[i] = angleLong
                turnBearingChanges[i] = ((bOutLong - bInLong + 360f) % 360f)
            }
        }

        val isTurn = BooleanArray(n)
        val nmsDistance = 20.0 // meters
        for (i in 1 until n - 1) {
            if (!isTurnCandidate[i]) continue

            val currentAngle = turnAngles[i]
            var isMax = true

            // Search backward in NMS window
            var j = i - 1
            while (j > 0 && cumDist[i] - cumDist[j] < nmsDistance) {
                if (isTurnCandidate[j] && turnAngles[j] > currentAngle) {
                    isMax = false
                    break
                }
                j--
            }

            if (!isMax) continue

            // Search forward in NMS window
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

    /**
     * Updates [state] based on the user's new [location].
     * Fires alarms when a turn waypoint is reached.
     */
    override fun updateNavigationState(state: NavigationState, location: UserLocation): NavigationState {
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

        // If off track locally, scan the entire track to find the closest segment.
        val localDist = bestProjection?.distanceToSegmentM ?: Double.MAX_VALUE
        if (localDist > 50.0) {
            var globalProjection: SegmentProjection? = null
            var globalSegIdx = -1

            val maxGoBack = 10 // allow slight backwards jitter
            val maxGoForward = state.waypoints.size / 2 // prevent cutting off more than half the track

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
                    // Tie-breaker: prefer the segment with the smaller index to prevent jumping ahead on loop routes
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
            return state // Fallback
        }

        val projectedPt = bestProjection.projectedPoint
        val distToTrack = bestProjection.distanceToSegmentM
        val isOffTrack = distToTrack > OFF_TRACK_M

        // Retroactively fire alarms for any turn waypoints we jumped past
        var lastAlerted = state.lastAlertedWaypointIndex
        lastAlerted = fireSkippedTurnAlarms(bestSegIdx, state, lastAlerted)

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
            relativeTurnBearing = nextTurnWp.turnBearingChange
        }

        // Calculate total remaining distance along the track to the end
        var totalRemaining = haversineM(projectedPt, state.waypoints[nextWpIdx].point).toFloat()
        for (k in nextWpIdx until state.waypoints.size - 1) {
            totalRemaining += state.waypoints[k].distanceToNextM
        }

        // Alarm/voice warning logic when approaching a turn
        val isNewTurnTarget = nextTurnWp.index != state.lastNextTurnIndex
        
        var warnedRightAfter = if (isNewTurnTarget) false else state.warnedRightAfterPrevious
        var warned1k = if (isNewTurnTarget) false else state.warned1km
        var warned300 = if (isNewTurnTarget) false else state.warned300m
        var warnedDuring = if (isNewTurnTarget) false else state.warnedDuringTurn

        if (nextTurnWp.isTurn) {
            val direction = if (relativeTurnBearing > 180f) "left" else "right"
            val streetSuffix = nextTurnWp.roadName?.let { " onto $it" } ?: ""
            
            // 1. Right after previous turn
            if (!warnedRightAfter) {
                val distStr = formatDistanceForSpeech(distToNextTurn)
                announce("In $distStr, turn $direction$streetSuffix")
                warnedRightAfter = true
                
                // Skip subsequent warnings if we started closer than their thresholds
                if (distToNextTurn <= 1000f) {
                    warned1k = true
                }
                if (distToNextTurn <= 300f) {
                    warned300 = true
                }
                if (distToNextTurn <= ALARM_RADIUS_M) {
                    warnedDuring = true
                    lastAlerted = nextTurnWp.index
                }
            }
            
            // 2. 1 km before turn
            if (!warned1k && distToNextTurn <= 1000f) {
                announce("In 1 kilometer, turn $direction$streetSuffix")
                warned1k = true
                if (distToNextTurn <= 300f) {
                    warned300 = true
                }
                if (distToNextTurn <= ALARM_RADIUS_M) {
                    warnedDuring = true
                    lastAlerted = nextTurnWp.index
                }
            }
            
            // 3. 300 meters before turn
            if (!warned300 && distToNextTurn <= 300f) {
                announce("In 300 meters, turn $direction$streetSuffix")
                warned300 = true
                if (distToNextTurn <= ALARM_RADIUS_M) {
                    warnedDuring = true
                    lastAlerted = nextTurnWp.index
                }
            }
            
            // 4. During turn (ALARM_RADIUS_M, i.e., 30m)
            if (!warnedDuring && distToNextTurn <= ALARM_RADIUS_M) {
                fireAlarm("Turn $direction$streetSuffix")
                warnedDuring = true
                lastAlerted = nextTurnWp.index
                Log.d(TAG, "Alarm fired at waypoint ${nextTurnWp.index}")
            }
        }

        // Destination reached check (remaining track distance < ALARM_RADIUS_M)
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

    private fun formatDistanceForSpeech(distanceM: Float): String {
        return if (distanceM >= 1000f) {
            val km = distanceM / 1000f
            if (abs(km - 1.0f) < 0.05f) {
                "1 kilometer"
            } else {
                val formattedKm = String.format(Locale.US, "%.1f", km)
                "$formattedKm kilometers"
            }
        } else {
            val roundedMeters = (round(distanceM / 50f) * 50).toInt()
            if (roundedMeters <= 0) {
                "now"
            } else {
                "$roundedMeters meters"
            }
        }
    }

    private fun fireAlarm(message: String) {
        if (alertMode == NavigationAlertMode.SILENT) return

        if (alertMode == NavigationAlertMode.VOICE || alertMode == NavigationAlertMode.SOUND || alertMode == NavigationAlertMode.VIBRATION) {
            try {
                vibrator?.vibrate(
                    VibrationEffect.createWaveform(
                        longArrayOf(0, 200, 100, 200, 100, 400),
                        intArrayOf(0, 255, 0, 255, 0, 255),
                        -1
                    )
                )
            } catch (e: Exception) { Log.w(TAG, "Vibration failed", e) }
        }

        if (alertMode == NavigationAlertMode.VOICE) {
            if (isTtsInitialized && tts != null) {
                tts?.speak(message, TextToSpeech.QUEUE_FLUSH, null, null)
            } else {
                playNotificationSound()
                showDeviceNotification(message)
            }
        } else if (alertMode == NavigationAlertMode.SOUND) {
            playNotificationSound()
        }
    }

    private fun showDeviceNotification(message: String) {
        try {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    "navigation_channel",
                    "Navigation",
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = "Shows ongoing navigation status"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val intent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntentFlags = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
            val pendingIntent = PendingIntent.getActivity(context, 0, intent, pendingIntentFlags)

            val notification = NotificationCompat.Builder(context, "navigation_channel")
                .setContentTitle("Navigation Alert")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_menu_directions)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setDefaults(NotificationCompat.DEFAULT_ALL)
                .setAutoCancel(true)
                .build()

            notificationManager.notify(102, notification)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to show device notification", e)
        }
    }

    private fun playNotificationSound() {
        try {
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
            val ringtone = RingtoneManager.getRingtone(context, uri)
            ringtone?.play()
        } catch (e: Exception) { Log.w(TAG, "Notification sound failed", e) }
    }

    /**
     * Retroactively fires alarms for any turn waypoints that were skipped
     * when the segment index jumped forward (due to fast movement or rare GPS updates).
     * Returns the updated lastAlertedWaypointIndex.
     */
    private fun fireSkippedTurnAlarms(
        bestSegIdx: Int,
        state: NavigationState,
        lastAlerted: Int
    ): Int {
        val prevIdx = state.currentWaypointIndex
        // Only check if we actually jumped forward
        if (bestSegIdx <= prevIdx) return lastAlerted

        var updatedLastAlerted = lastAlerted

        // Check every waypoint between the old and new segment positions
        for (i in (prevIdx + 1)..minOf(bestSegIdx, state.waypoints.size - 1)) {
            val wp = state.waypoints.getOrNull(i) ?: continue
            if (wp.isTurn && wp.index != updatedLastAlerted) {
                val direction = if (wp.turnBearingChange > 180f) "left" else "right"
                fireAlarm("Turn $direction")
                updatedLastAlerted = wp.index
                Log.d(TAG, "Retroactive alarm for skipped turn at wp ${wp.index}")
            }
        }
        return updatedLastAlerted
    }

    // ── Math ──────────────────────────────────────────────────────────────────

    private fun haversineM(a: GpxPoint, b: GpxPoint): Double {
        val r    = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h    = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * atan2(sqrt(clampedH), sqrt(1.0 - clampedH))
    }


    private fun angleDiff(a: Float, b: Float): Double {
        val diff = abs(a - b) % 360.0
        return if (diff > 180.0) 360.0 - diff else diff
    }
}

