package com.osm.wear.models

data class NavigationState(
    val steps: List<RouteStep> = emptyList(),
    val currentStepIndex: Int = 0,
    val distanceToNextTurnM: Float = 0f,
    val bearingToNextTurn: Float = 0f,
    val totalRemainingM: Float = 0f,
    val currentSpeedKmh: Float = 0f,
    val isOffTrack: Boolean = false,
    val hasNextTurn: Boolean = false,
    val isActive: Boolean = false,
    val isNavigating: Boolean = isActive,
    val currentRoadName: String? = null,
    val lastAlertedStepIndex: Int = -1,
    val lastNextTurnIndex: Int = -1,
    val route: NavigationRoute? = null
) {
    val activeStep: RouteStep?
        get() = steps.getOrNull(currentStepIndex)

    val nextStep: RouteStep?
        get() = steps.getOrNull(currentStepIndex + 1)

    val activeManeuver: Maneuver?
        get() = activeStep?.maneuver

    val nextManeuver: Maneuver?
        get() = nextStep?.maneuver

    val hasSteps: Boolean
        get() = steps.isNotEmpty()
}
