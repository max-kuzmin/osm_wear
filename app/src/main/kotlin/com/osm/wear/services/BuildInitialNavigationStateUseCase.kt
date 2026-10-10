package com.osm.wear.services

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationRoute
import com.osm.wear.models.NavigationState
import com.osm.wear.models.RouteFile
import com.osm.wear.models.RouteStep
import com.osm.wear.repositories.IPreferencesRepository
import com.osm.wear.repositories.IRegionRepository
import javax.inject.Inject

class BuildInitialNavigationStateUseCase @Inject constructor(
    private val preferencesRepository: IPreferencesRepository,
    private val regionRepository: IRegionRepository
) {
    operator fun invoke(routeFile: RouteFile): Result<NavigationState> {
        val mapFile = regionRepository.getActiveMapFile()

        if (mapFile == null || !mapFile.exists()) {
            return Result.failure(Exception("Download a map for this region first"))
        }

        val steps = buildSteps(routeFile)
        if (steps.isEmpty()) {
            return Result.failure(Exception("Failed to build navigation state"))
        }

        val totalRemaining = when {
            routeFile.route != null && routeFile.route.totalDistanceM > 0f -> routeFile.route.totalDistanceM
            steps.isNotEmpty() && steps.any { it.distanceM > 0f } -> steps.sumOf { it.distanceM.toDouble() }.toFloat()
            routeFile.totalDistanceKm > 0.0 -> (routeFile.totalDistanceKm * 1000.0).toFloat()
            else -> 0f
        }

        val activeTarget = UpdateNavigationStateUseCase.resolveActiveTarget(
            steps = steps,
            stepIdx = 0,
            distToActiveManeuver = steps.first().distanceM
        )

        val hasNextTurn = activeTarget.isTurn
        val distanceToNextTurn = if (activeTarget.isTurn) activeTarget.distanceM else totalRemaining
        val bearingToNextTurn = if (activeTarget.isTurn) activeTarget.visualAngle else 0f
        val firstTurnIndex = if (activeTarget.isTurn) activeTarget.stepIndex else -1

        val navRoute = routeFile.route ?: NavigationRoute(
            id = routeFile.id,
            name = routeFile.name,
            steps = steps,
            totalDistanceM = totalRemaining
        )

        val currentRoad = steps.first().roadName
            ?: steps.first().maneuver.roadName
            ?: routeFile.name.takeIf { it.isNotBlank() && it != "path_finder" }

        val state = NavigationState(
            steps = steps,
            currentStepIndex = 0,
            distanceToNextTurnM = distanceToNextTurn,
            bearingToNextTurn = bearingToNextTurn,
            totalRemainingM = totalRemaining,
            currentRoadName = currentRoad,
            hasNextTurn = hasNextTurn,
            isActive = true,
            isNavigating = true,
            route = navRoute,
            isOffTrack = false,
            lastAlertedStepIndex = -1,
            lastNextTurnIndex = firstTurnIndex
        )
        return Result.success(state)
    }

    operator fun invoke(navRoute: NavigationRoute): Result<NavigationState> {
        val routeFile = RouteFile(
            id = navRoute.id,
            name = navRoute.name,
            filePath = "",
            points = navRoute.fullPolyline,
            totalDistanceKm = (navRoute.totalDistanceM / 1000f).toDouble(),
            isActive = true,
            route = navRoute
        )
        return invoke(routeFile)
    }

    private fun buildSteps(routeFile: RouteFile): List<RouteStep> {
        if (routeFile.route != null && routeFile.route.steps.isNotEmpty()) {
            return routeFile.route.steps
        }

        val pts = routeFile.points
        if (pts.size < 2) return emptyList()

        val totalDistM = (routeFile.totalDistanceKm * 1000.0).toFloat()
        val destination = pts.last()
        val fallbackManeuver = Maneuver(
            type = ManeuverType.ARRIVE,
            location = destination,
            roadName = routeFile.name,
            instruction = "Arrive at destination"
        )
        val fallbackStep = RouteStep(
            index = 0,
            polyline = pts,
            distanceM = totalDistM,
            durationS = 0f,
            roadName = routeFile.name,
            maneuver = fallbackManeuver
        )
        return listOf(fallbackStep)
    }
}
