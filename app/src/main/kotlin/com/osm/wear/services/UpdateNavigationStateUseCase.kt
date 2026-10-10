package com.osm.wear.services

import android.util.Log
import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationState
import com.osm.wear.models.RouteStep
import com.osm.wear.models.UserLocation
import com.osm.wear.repositories.IAlertsRepository
import javax.inject.Inject
import kotlin.math.*

class UpdateNavigationStateUseCase @Inject constructor(
    private val alertsRepository: IAlertsRepository
) {
    companion object {
        private const val TAG = "UpdateNavStateUseCase"
        const val OFF_TRACK_THRESHOLD_M = 35.0
        const val ON_TRACK_SNAP_THRESHOLD_M = 30.0
        const val JUNCTION_PROXIMITY_M = 20.0
        const val IMMEDIATE_ALARM_RADIUS_M = 30.0f
        const val DESTINATION_ARRIVAL_RADIUS_M = 25.0f

        data class ActiveNavigationTarget(
            val stepIndex: Int,
            val maneuver: Maneuver,
            val distanceM: Float,
            val isTurn: Boolean,
            val isArrive: Boolean,
            val roadName: String?,
            val visualAngle: Float
        )

        fun resolveActiveTarget(
            steps: List<RouteStep>,
            stepIdx: Int,
            distToActiveManeuver: Float
        ): ActiveNavigationTarget {
            if (steps.isEmpty()) {
                return ActiveNavigationTarget(
                    stepIndex = 0,
                    maneuver = Maneuver(ManeuverType.ARRIVE, GeoPoint(0.0, 0.0)),
                    distanceM = distToActiveManeuver,
                    isTurn = false,
                    isArrive = true,
                    roadName = null,
                    visualAngle = 0f
                )
            }

            val validStepIdx = stepIdx.coerceIn(0, steps.size - 1)
            val currentStep = steps[validStepIdx]

            // 1. If the current step's terminal maneuver is a turn or destination arrival,
            // that is directly the active upcoming target!
            if (currentStep.maneuver.isTurn || currentStep.maneuver.type == ManeuverType.ARRIVE) {
                return ActiveNavigationTarget(
                    stepIndex = currentStep.index,
                    maneuver = currentStep.maneuver,
                    distanceM = maxOf(0f, distToActiveManeuver),
                    isTurn = currentStep.maneuver.isTurn,
                    isArrive = currentStep.maneuver.type == ManeuverType.ARRIVE,
                    roadName = currentStep.maneuver.roadName ?: currentStep.roadName,
                    visualAngle = if (currentStep.maneuver.isTurn) currentStep.maneuver.visualAngle else 0f
                )
            }

            // 2. If current step ends at CONTINUE/DEPART, search forward for the next turn or arrival
            val upcomingTargetStep = steps.drop(validStepIdx).firstOrNull { it.maneuver.isTurn || it.maneuver.type == ManeuverType.ARRIVE }
                ?: steps.last()

            var accumulatedDist = distToActiveManeuver
            for (k in (validStepIdx + 1)..upcomingTargetStep.index) {
                accumulatedDist += steps[k].distanceM
            }

            return ActiveNavigationTarget(
                stepIndex = upcomingTargetStep.index,
                maneuver = upcomingTargetStep.maneuver,
                distanceM = maxOf(0f, accumulatedDist),
                isTurn = upcomingTargetStep.maneuver.isTurn,
                isArrive = upcomingTargetStep.maneuver.type == ManeuverType.ARRIVE,
                roadName = upcomingTargetStep.maneuver.roadName ?: upcomingTargetStep.roadName,
                visualAngle = if (upcomingTargetStep.maneuver.isTurn) upcomingTargetStep.maneuver.visualAngle else 0f
            )
        }
    }

    private var alertTargetIndex: Int = -1
    private var warnedRightAfterPrevious: Boolean = false
    private var warned1km: Boolean = false
    private var warned300m: Boolean = false
    private var warnedDuringTurn: Boolean = false

    internal fun resetAlertState() {
        alertTargetIndex = -1
        warnedRightAfterPrevious = false
        warned1km = false
        warned300m = false
        warnedDuringTurn = false
    }

    internal fun setAlertFlagsForTest(
        rightAfter: Boolean = false,
        k1: Boolean = false,
        m300: Boolean = false,
        during: Boolean = false
    ) {
        warnedRightAfterPrevious = rightAfter
        warned1km = k1
        warned300m = m300
        warnedDuringTurn = during
    }

    operator fun invoke(state: NavigationState, location: UserLocation): NavigationState {
        if (!state.isActive || !state.hasSteps) return state
        return updateStepBasedNavigation(state, location)
    }

    private fun updateStepBasedNavigation(state: NavigationState, location: UserLocation): NavigationState {
        val userPt = GeoPoint(location.latitude, location.longitude)
        val steps = state.steps
        var stepIdx = state.currentStepIndex.coerceIn(0, steps.size - 1)
        val currentStep = steps[stepIdx]

        val proj = LocalProjection(userPt)

        val projCurrent = projectToPolyline(userPt, currentStep.polyline, proj)
        val distToCurrentStep = projCurrent.distanceToSegmentM
        val remainingOnCurrentStep = pathDistanceToEnd(currentStep.polyline, projCurrent)
        val distToManeuver = haversineM(userPt, currentStep.maneuver.location).toFloat()

        var advancedStep = false

        // 1. Check advancement and forward cut-ahead
        var bestStepIdx = stepIdx
        var bestDist = if (distToCurrentStep <= ON_TRACK_SNAP_THRESHOLD_M) distToCurrentStep else Double.MAX_VALUE

        // Check immediate next step
        if (stepIdx < steps.size - 1) {
            val nextStep = steps[stepIdx + 1]
            val projNext = projectToPolyline(userPt, nextStep.polyline, proj)
            val nearJunction = remainingOnCurrentStep <= JUNCTION_PROXIMITY_M || distToManeuver <= JUNCTION_PROXIMITY_M

            val enteredNextStep = projNext.distanceToSegmentM <= ON_TRACK_SNAP_THRESHOLD_M && (
                (nearJunction && projNext.fraction > 0.0 && projNext.distanceToSegmentM <= 20.0) ||
                (distToManeuver <= 3.0f && remainingOnCurrentStep <= 3.0f) ||
                (projNext.fraction > 0.0 && projNext.distanceToSegmentM < distToCurrentStep - 10.0)
            )

            if (enteredNextStep) {
                bestStepIdx = stepIdx + 1
                bestDist = projNext.distanceToSegmentM
            }
        }

        // Check forward cut-ahead if off track or next step matched
        if (distToCurrentStep > OFF_TRACK_THRESHOLD_M || bestStepIdx > stepIdx) {
            for (k in (bestStepIdx + 1) until steps.size) {
                val projK = projectToPolyline(userPt, steps[k].polyline, proj)
                if (projK.distanceToSegmentM <= ON_TRACK_SNAP_THRESHOLD_M && projK.distanceToSegmentM < bestDist - 5.0) {
                    bestDist = projK.distanceToSegmentM
                    bestStepIdx = k
                }
            }
        }

        if (bestStepIdx != stepIdx) {
            stepIdx = bestStepIdx
            advancedStep = true
        }

        val activeStep = steps[stepIdx]
        val activeManeuver = activeStep.maneuver
        val projActive = if (advancedStep) projectToPolyline(userPt, activeStep.polyline, proj) else projCurrent
        val distToActiveManeuver = pathDistanceToEnd(activeStep.polyline, projActive)

        // 3. Off-track calculation
        val isOffTrack = projActive.distanceToSegmentM > OFF_TRACK_THRESHOLD_M

        // 4. Total remaining distance
        var totalRemaining = distToActiveManeuver
        for (k in (stepIdx + 1) until steps.size) {
            totalRemaining += steps[k].distanceM
        }

        // 5. Active navigation target resolution
        val target = resolveActiveTarget(steps, stepIdx, distToActiveManeuver)

        // 6. Destination arrival on final step
        if (target.isArrive && target.distanceM <= DESTINATION_ARRIVAL_RADIUS_M) {
            fireAlarm("Destination reached")
            Log.d(TAG, "Destination reached")
            return state.copy(
                isActive = false,
                isNavigating = false,
                currentStepIndex = stepIdx,
                distanceToNextTurnM = 0f,
                bearingToNextTurn = 0f,
                totalRemainingM = 0f,
                isOffTrack = false,
                hasNextTurn = false,
                currentRoadName = activeStep.roadName ?: activeManeuver.roadName
            )
        }

        // 7. Voice announcements and immediate alarms
        if (target.stepIndex != alertTargetIndex) {
            alertTargetIndex = target.stepIndex
            warnedRightAfterPrevious = false
            warned1km = false
            warned300m = false
            warnedDuringTurn = (state.lastAlertedStepIndex == target.stepIndex)
        }

        val streetSuffix = target.roadName?.let { " onto $it" } ?: ""

        if (target.isTurn) {
            val direction = getTurnDirection(target.maneuver)
            val turnPhrase = if (target.maneuver.type == ManeuverType.U_TURN) {
                "Make a U-turn$streetSuffix"
            } else {
                "Turn $direction$streetSuffix"
            }

            if (target.distanceM <= IMMEDIATE_ALARM_RADIUS_M) {
                if (!warnedDuringTurn && state.lastAlertedStepIndex != target.stepIndex) {
                    fireAlarm(turnPhrase)
                    warnedDuringTurn = true
                    Log.d(TAG, "Alarm fired for step ${target.stepIndex}: $turnPhrase")
                }
                warnedRightAfterPrevious = true
                warned300m = true
                warned1km = true
            } else {
                if (!warnedRightAfterPrevious) {
                    if (target.distanceM > 50f) {
                        val distStr = formatDistanceForSpeech(target.distanceM)
                        val inPhrase = if (target.maneuver.type == ManeuverType.U_TURN) {
                            "In $distStr, make a U-turn$streetSuffix"
                        } else {
                            "In $distStr, turn $direction$streetSuffix"
                        }
                        alertsRepository.announce(inPhrase)
                    }
                    warnedRightAfterPrevious = true
                    if (target.distanceM <= 1050f) warned1km = true
                    if (target.distanceM <= 350f) warned300m = true
                }

                if (!warned1km && target.distanceM <= 1000f) {
                    if (target.distanceM > 300f) {
                        val distStr = formatDistanceForSpeech(target.distanceM)
                        val inPhrase = if (target.maneuver.type == ManeuverType.U_TURN) {
                            "In $distStr, make a U-turn$streetSuffix"
                        } else {
                            "In $distStr, turn $direction$streetSuffix"
                        }
                        alertsRepository.announce(inPhrase)
                    }
                    warned1km = true
                }

                if (!warned300m && target.distanceM <= 300f) {
                    if (target.distanceM > 50f) {
                        val distStr = formatDistanceForSpeech(target.distanceM)
                        val inPhrase = if (target.maneuver.type == ManeuverType.U_TURN) {
                            "In $distStr, make a U-turn$streetSuffix"
                        } else {
                            "In $distStr, turn $direction$streetSuffix"
                        }
                        alertsRepository.announce(inPhrase)
                    }
                    warned300m = true
                }
            }
        } else if (target.isArrive) {
            if (!warnedRightAfterPrevious && target.distanceM > 50f) {
                val distStr = formatDistanceForSpeech(target.distanceM)
                alertsRepository.announce("In $distStr, arrive at destination")
                warnedRightAfterPrevious = true
            }
        }

        val lastAlerted = if (warnedDuringTurn) target.stepIndex else state.lastAlertedStepIndex

        return state.copy(
            currentStepIndex = stepIdx,
            distanceToNextTurnM = target.distanceM,
            bearingToNextTurn = target.visualAngle,
            totalRemainingM = totalRemaining,
            isOffTrack = isOffTrack,
            hasNextTurn = target.isTurn,
            currentRoadName = activeStep.roadName ?: activeManeuver.roadName,
            lastAlertedStepIndex = lastAlerted,
            lastNextTurnIndex = target.stepIndex
        )
    }

    private fun getTurnDirection(maneuver: Maneuver): String {
        return when (maneuver.type) {
            ManeuverType.TURN_LEFT -> "left"
            ManeuverType.TURN_RIGHT -> "right"
            ManeuverType.SLIGHT_LEFT -> "slight left"
            ManeuverType.SLIGHT_RIGHT -> "slight right"
            ManeuverType.SHARP_LEFT -> "sharp left"
            ManeuverType.SHARP_RIGHT -> "sharp right"
            ManeuverType.U_TURN -> "make a U-turn"
            else -> if (maneuver.relativeAngle < 0f) "left" else "right"
        }
    }

    private data class PolylineProjection(
        val projectedPoint: GeoPoint,
        val segmentIndex: Int,
        val fraction: Double,
        val distanceToSegmentM: Double
    )

    private fun projectToPolyline(
        userPt: GeoPoint,
        polyline: List<GeoPoint>,
        proj: LocalProjection
    ): PolylineProjection {
        if (polyline.isEmpty()) {
            return PolylineProjection(userPt, 0, 0.0, Double.MAX_VALUE)
        }
        if (polyline.size == 1) {
            val dist = haversineM(userPt, polyline[0])
            return PolylineProjection(polyline[0], 0, 0.0, dist)
        }

        val userPtProj = proj.project(userPt)
        var bestDist = Double.MAX_VALUE
        var bestSegIdx = 0
        var bestFraction = 0.0
        var bestProjectedPt = polyline[0]

        for (i in 0 until polyline.size - 1) {
            val a = proj.project(polyline[i])
            val b = proj.project(polyline[i + 1])
            val proj2D = projectPointToSegment2D(userPtProj, a, b)
            if (proj2D.distanceToSegmentM < bestDist) {
                bestDist = proj2D.distanceToSegmentM
                bestSegIdx = i
                bestFraction = proj2D.fraction
                bestProjectedPt = proj.unproject(proj2D.projectedPoint)
            }
        }

        return PolylineProjection(bestProjectedPt, bestSegIdx, bestFraction, bestDist)
    }

    private fun pathDistanceToEnd(
        polyline: List<GeoPoint>,
        projResult: PolylineProjection
    ): Float {
        if (polyline.size < 2) return 0f
        val segIdx = projResult.segmentIndex
        if (segIdx !in 0 until polyline.size - 1) return 0f

        val segEnd = polyline[segIdx + 1]
        var totalDist = haversineM(projResult.projectedPoint, segEnd).toFloat()

        for (i in (segIdx + 1) until polyline.size - 1) {
            totalDist += haversineM(polyline[i], polyline[i + 1]).toFloat()
        }
        return maxOf(0f, totalDist)
    }

    private data class Point2D(val x: Double, val y: Double) {
        fun distanceTo(other: Point2D): Double = sqrt((x - other.x).pow(2) + (y - other.y).pow(2))
    }

    private class LocalProjection(val origin: GeoPoint) {
        private val latRad = Math.toRadians(origin.lat)
        private val metersPerLat = 111132.9
        private val metersPerLon = 111132.9 * cos(latRad)

        fun project(p: GeoPoint): Point2D = Point2D((p.lon - origin.lon) * metersPerLon, (p.lat - origin.lat) * metersPerLat)
        fun unproject(pt: Point2D): GeoPoint = GeoPoint(origin.lat + pt.y / metersPerLat, origin.lon + pt.x / metersPerLon)
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

    internal fun formatDistanceForSpeech(distanceM: Float): String {
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

    private fun fireAlarm(message: String) {
        alertsRepository.vibrate(longArrayOf(0, 200, 100, 200, 100, 400))
        alertsRepository.announce(message)
    }

    private fun haversineM(a: GeoPoint, b: GeoPoint): Double = a.distanceTo(b)
}
