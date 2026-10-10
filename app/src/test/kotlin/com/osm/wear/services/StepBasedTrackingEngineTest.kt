package com.osm.wear.services

import com.osm.wear.models.GeoPoint
import com.osm.wear.models.Maneuver
import com.osm.wear.models.ManeuverType
import com.osm.wear.models.NavigationState
import com.osm.wear.models.RouteStep
import com.osm.wear.models.UserLocation
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StepBasedTrackingEngineTest {

    private lateinit var fakeAlerts: FakeAlertsRepository
    private lateinit var useCase: UpdateNavigationStateUseCase

    @Before
    fun setUp() {
        fakeAlerts = FakeAlertsRepository()
        useCase = UpdateNavigationStateUseCase(fakeAlerts)
    }

    private fun createTwoStepRoute(): NavigationState {
        // Step 0: Heading North for 200m along lon 2.2940 from lat 48.8500 to 48.8518, turning RIGHT onto Market St
        val p0 = GeoPoint(48.8500, 2.2940)
        val p1 = GeoPoint(48.8509, 2.2940)
        val p2 = GeoPoint(48.8518, 2.2940) // Junction point (~200m from p0)

        val maneuver0 = Maneuver(
            type = ManeuverType.TURN_RIGHT,
            location = p2,
            bearingBefore = 0f,
            bearingAfter = 90f,
            relativeAngle = 90f,
            roadName = "Market Street",
            instruction = "Turn right onto Market Street"
        )
        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1, p2),
            distanceM = 200f,
            roadName = "Main Street",
            maneuver = maneuver0
        )

        // Step 1: Heading East for 300m along lat 48.8518 to lon 2.2981, ARRIVE at destination
        val p3 = GeoPoint(48.8518, 2.2960)
        val p4 = GeoPoint(48.8518, 2.2981) // Destination (~300m from p2)

        val maneuver1 = Maneuver(
            type = ManeuverType.ARRIVE,
            location = p4,
            bearingBefore = 90f,
            bearingAfter = 90f,
            relativeAngle = 0f,
            instruction = "Arrive at destination"
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(p2, p3, p4),
            distanceM = 300f,
            roadName = "Market Street",
            maneuver = maneuver1
        )

        return NavigationState(
            steps = listOf(step0, step1),
            currentStepIndex = 0,
            distanceToNextTurnM = 200f,
            bearingToNextTurn = 90f,
            totalRemainingM = 500f,
            isActive = true,
            hasNextTurn = true
        )
    }

    @Test
    fun testStepTracking_monotonicDistanceCountdownWithoutJumps() {
        val initialState = createTwoStepRoute()

        // User moves Northward along Step 0
        val locAtStart = UserLocation(48.8500, 2.2940, accuracy = 2f)
        val state1 = useCase(initialState, locAtStart)
        assertEquals(0, state1.currentStepIndex)
        assertEquals(200f, state1.distanceToNextTurnM, 15f)
        assertEquals(90f, state1.bearingToNextTurn, 0.01f)
        assertTrue(state1.hasNextTurn)

        // Halfway along Step 0 (~100m to turn)
        val locHalfway = UserLocation(48.8509, 2.2940, accuracy = 2f)
        val state2 = useCase(state1, locHalfway)
        assertEquals(0, state2.currentStepIndex)
        assertEquals(100f, state2.distanceToNextTurnM, 15f)
        assertTrue("Distance must decrease monotonically without jumping to total route length", state2.distanceToNextTurnM < state1.distanceToNextTurnM)
        assertTrue(state2.hasNextTurn)

        // Approaching turn (~20m to turn)
        val locNearTurn = UserLocation(48.85162, 2.2940, accuracy = 2f)
        val state3 = useCase(state2, locNearTurn)
        assertEquals(0, state3.currentStepIndex)
        assertEquals(20f, state3.distanceToNextTurnM, 10f)
        assertTrue(state3.distanceToNextTurnM < 30f)
    }

    @Test
    fun testStepTracking_transitionToNextStepAtJunction() {
        val initialState = createTwoStepRoute()

        // User advances and turns onto Step 1 (past junction at 48.8518, 2.2940, now at 48.8518, 2.2945)
        val locPastJunction = UserLocation(48.8518, 2.2945, accuracy = 2f)
        val updatedState = useCase(initialState, locPastJunction)

        assertEquals("Tracking must advance to Step 1 upon completing turn", 1, updatedState.currentStepIndex)
        assertEquals("Step 1 maneuver is ARRIVE so hasNextTurn must be false", false, updatedState.hasNextTurn)
        assertEquals("Market Street", updatedState.currentRoadName)
        assertTrue("Remaining distance must reflect distance along Step 1 to destination", updatedState.totalRemainingM < 300f)
    }

    @Test
    fun testStepTracking_voiceAlertsSynchronizedWithOverlay() {
        val initialState = createTwoStepRoute()

        // 1. Move to 100m from turn -> Preliminary announcement fired
        fakeAlerts.clear()
        val loc100m = UserLocation(48.8509, 2.2940, accuracy = 2f)
        val stateAt100m = useCase(initialState, loc100m)

        assertTrue(fakeAlerts.announcements.isNotEmpty())
        val announcement = fakeAlerts.announcements.first()
        assertTrue("Voice announcement must specify direction: $announcement", announcement.contains("turn right", ignoreCase = true))
        assertTrue("Voice announcement must specify street name: $announcement", announcement.contains("Market Street", ignoreCase = true))
        assertFalse("Must never say 'In now': $announcement", announcement.contains("In now", ignoreCase = true))

        // 2. Move to <= 30m -> Immediate turn alarm fires with vibration
        fakeAlerts.clear()
        val loc20m = UserLocation(48.85162, 2.2940, accuracy = 2f)
        useCase(stateAt100m, loc20m)

        assertTrue("Vibration must trigger at immediate turn alarm", fakeAlerts.vibrations.isNotEmpty())
        val immediateAlarm = fakeAlerts.announcements.firstOrNull()
        assertNotNull("Immediate alarm must fire at <= 30m", immediateAlarm)
        assertTrue(immediateAlarm!!.startsWith("Turn right", ignoreCase = true))
        assertFalse("Immediate alarm must not have 'In X meters': $immediateAlarm", immediateAlarm.startsWith("In ", ignoreCase = true))
    }

    @Test
    fun testStepTracking_arrivalAtDestinationOnFinalStep() {
        val initialState = createTwoStepRoute()

        // User is on Step 1, near destination (~15m away)
        val step1State = initialState.copy(currentStepIndex = 1, hasNextTurn = false)
        fakeAlerts.clear()

        val locNearDest = UserLocation(48.8518, 2.29795, accuracy = 2f)
        val stateArrived = useCase(step1State, locNearDest)

        assertFalse("Navigation must deactivate upon arrival", stateArrived.isActive)
        assertEquals(0f, stateArrived.totalRemainingM, 0.01f)
        assertEquals(0f, stateArrived.distanceToNextTurnM, 0.01f)
        assertTrue("Destination reached announcement must fire", fakeAlerts.announcements.any { it.contains("Destination reached", ignoreCase = true) })
    }

    @Test
    fun testStepTracking_offTrackRecoveryWithoutSkippedTurnAlarm() {
        val initialState = createTwoStepRoute()

        // User is 100m off-track laterally
        val locOffTrack = UserLocation(48.8509, 2.2960, accuracy = 2f)
        val stateOffTrack = useCase(initialState, locOffTrack)

        assertTrue(stateOffTrack.isOffTrack)
        // Ensure no false "Route segment skipped" alarm is triggered
        assertFalse(fakeAlerts.announcements.any { it.contains("Route segment skipped", ignoreCase = true) })
    }

    @Test
    fun testStepTracking_overlayDistanceStrictlyMatchesNextTurnNotification() {
        // Multi-segment corridor: Step 0 (100m straight) -> Step 1 (300m to TURN_LEFT) -> Step 2 (200m ARRIVE)
        val p0 = GeoPoint(51.5000, -0.1200)
        val p1 = GeoPoint(51.5009, -0.1200) // End of step 0 (100m)
        val p2 = GeoPoint(51.5036, -0.1200) // End of step 1 (300m), junction for TURN_LEFT
        val p3 = GeoPoint(51.5036, -0.1229) // End of step 2 (200m), destination

        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1),
            distanceM = 100f,
            roadName = "Main Street",
            maneuver = Maneuver(type = ManeuverType.CONTINUE, location = p1, roadName = "Main Street")
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(p1, p2),
            distanceM = 300f,
            roadName = "Main Street",
            maneuver = Maneuver(
                type = ManeuverType.TURN_LEFT,
                location = p2,
                relativeAngle = -90f,
                roadName = "Church Road",
                instruction = "In 300 meters, turn left onto Church Road"
            )
        )
        val step2 = RouteStep(
            index = 2,
            polyline = listOf(p2, p3),
            distanceM = 200f,
            roadName = "Church Road",
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = p3, instruction = "Arrive at destination")
        )

        val state = NavigationState(
            steps = listOf(step0, step1, step2),
            currentStepIndex = 0,
            distanceToNextTurnM = 400f,
            bearingToNextTurn = -90f,
            totalRemainingM = 600f,
            isActive = true,
            hasNextTurn = true
        )

        fakeAlerts.clear()

        // 1. User at p0 (start of Step 0):
        // Overlay must show distance to upcoming TURN_LEFT (100m on step 0 + 300m on step 1 = 400m)
        // Icon must point LEFT (-90°)
        val state1 = useCase(state, UserLocation(p0.lat, p0.lon, accuracy = 2f))
        assertEquals(400f, state1.distanceToNextTurnM, 15f)
        assertEquals(-90f, state1.bearingToNextTurn, 0.01f)
        assertTrue(state1.hasNextTurn)

        // Notification must announce 400m turn left, matching overlay
        assertTrue(fakeAlerts.announcements.isNotEmpty())
        val firstAlert = fakeAlerts.announcements.first()
        assertTrue("Notification must announce left turn: $firstAlert", firstAlert.contains("turn left", ignoreCase = true))
        assertTrue("Notification must announce 400 meters matching overlay: $firstAlert", firstAlert.contains("400 meters", ignoreCase = true))

        // 2. User enters Step 1, at 300m from turn:
        fakeAlerts.clear()
        val state2 = useCase(state1, UserLocation(p1.lat, p1.lon, accuracy = 2f))
        assertEquals(1, state2.currentStepIndex)
        assertEquals(300f, state2.distanceToNextTurnM, 15f)
        assertEquals(-90f, state2.bearingToNextTurn, 0.01f)
        assertTrue(state2.hasNextTurn)

        // 3. User approaches junction at 20m:
        fakeAlerts.clear()
        val pNearJunction = GeoPoint(51.50342, -0.1200)
        val state3 = useCase(state2, UserLocation(pNearJunction.lat, pNearJunction.lon, accuracy = 2f))
        assertTrue("Distance to next turn must be <= 30m: ${state3.distanceToNextTurnM}", state3.distanceToNextTurnM <= 30f)
        assertEquals(-90f, state3.bearingToNextTurn, 0.01f)
        // Immediate alarm fires: "Turn left onto Church Road"
        assertTrue(fakeAlerts.announcements.isNotEmpty())
        assertEquals("Turn left onto Church Road", fakeAlerts.announcements.first())
    }

    @Test
    fun testStepTracking_immediateTurnAlarmExact6BeatVibrationWaveform() {
        val initialState = createTwoStepRoute()
        fakeAlerts.clear()

        // Approaching junction point (48.8518, 2.2940) at ~20m (< 30m threshold)
        val locNearTurn = UserLocation(48.85162, 2.2940, accuracy = 2f)
        val state = useCase(initialState, locNearTurn)

        assertTrue("Immediate alarm must fire when <= 30m to turn", state.distanceToNextTurnM <= 30f)
        assertTrue("Vibration waveform must be recorded", fakeAlerts.vibrations.isNotEmpty())
        val expectedWaveform = longArrayOf(0, 200, 100, 200, 100, 400)
        assertArrayEquals("Must trigger the exact 6-beat vibration waveform array", expectedWaveform, fakeAlerts.vibrations.last())
        assertEquals("Turn right onto Market Street", fakeAlerts.announcements.last())
    }

    @Test
    fun testStepTracking_uTurnManeuverAnnouncementPhrasing() {
        val p0 = GeoPoint(48.8500, 2.2940)
        val p1 = GeoPoint(48.8527, 2.2940) // ~300m North
        val p2 = GeoPoint(48.8500, 2.2940) // Heading South after U-turn

        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1),
            distanceM = 300f,
            roadName = "Grand Avenue",
            maneuver = Maneuver(
                type = ManeuverType.U_TURN,
                location = p1,
                bearingBefore = 0f,
                bearingAfter = 180f,
                relativeAngle = 180f,
                roadName = "Grand Avenue South",
                instruction = "In 300 meters, make a U-turn onto Grand Avenue South"
            )
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(p1, p2),
            distanceM = 300f,
            roadName = "Grand Avenue South",
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = p2, instruction = "Arrive at destination")
        )

        val state = NavigationState(
            steps = listOf(step0, step1),
            currentStepIndex = 0,
            distanceToNextTurnM = 300f,
            bearingToNextTurn = 180f,
            totalRemainingM = 600f,
            isActive = true,
            hasNextTurn = true
        )

        fakeAlerts.clear()

        // 1. At 300m away, preliminary warning phrasing
        val loc300m = UserLocation(48.8500, 2.2940, accuracy = 2f)
        val state300m = useCase(state, loc300m)

        assertTrue(fakeAlerts.announcements.isNotEmpty())
        val preliminaryAlert = fakeAlerts.announcements.first()
        assertEquals("In 300 meters, make a U-turn onto Grand Avenue South", preliminaryAlert)

        // 2. Approaching U-turn within <= 30m (at 48.85252, ~20m from p1 at 48.8527)
        fakeAlerts.clear()
        val locNearTurn = UserLocation(48.85252, 2.2940, accuracy = 2f)
        useCase(state300m, locNearTurn)

        assertTrue(fakeAlerts.announcements.isNotEmpty())
        val immediateAlert = fakeAlerts.announcements.first()
        assertEquals("Make a U-turn onto Grand Avenue South", immediateAlert)
    }

    @Test
    fun testStepTracking_multiStepCorridorCutAheadTransitionWithoutFalseAlarms() {
        val p0 = GeoPoint(51.5000, -0.1200)
        val p1 = GeoPoint(51.5009, -0.1200) // End of step 0 (100m)
        val p2 = GeoPoint(51.5018, -0.1200) // End of step 1 (100m)
        val p3 = GeoPoint(51.5018, -0.1229) // End of step 2 (200m TURN_LEFT)
        val p4 = GeoPoint(51.5018, -0.1245) // Destination

        val step0 = RouteStep(
            index = 0,
            polyline = listOf(p0, p1),
            distanceM = 100f,
            roadName = "Corridor A",
            maneuver = Maneuver(type = ManeuverType.CONTINUE, location = p1, roadName = "Corridor A")
        )
        val step1 = RouteStep(
            index = 1,
            polyline = listOf(p1, p2),
            distanceM = 100f,
            roadName = "Corridor A",
            maneuver = Maneuver(type = ManeuverType.CONTINUE, location = p2, roadName = "Corridor A")
        )
        val step2 = RouteStep(
            index = 2,
            polyline = listOf(p2, p3),
            distanceM = 200f,
            roadName = "Turn Street",
            maneuver = Maneuver(type = ManeuverType.TURN_LEFT, location = p3, roadName = "Church Road")
        )
        val step3 = RouteStep(
            index = 3,
            polyline = listOf(p3, p4),
            distanceM = 110f,
            roadName = "Church Road",
            maneuver = Maneuver(type = ManeuverType.ARRIVE, location = p4)
        )

        val state = NavigationState(
            steps = listOf(step0, step1, step2, step3),
            currentStepIndex = 0,
            distanceToNextTurnM = 400f,
            totalRemainingM = 510f,
            isActive = true,
            hasNextTurn = true
        )

        fakeAlerts.clear()

        // User cuts ahead: currently on Step 2 (at 51.5018, -0.1210, well away from Step 0)
        val locCutAhead = UserLocation(51.5018, -0.1210, accuracy = 2f)
        val updatedState = useCase(state, locCutAhead)

        assertEquals("Tracking must smoothly cut-ahead and snap to Step 2", 2, updatedState.currentStepIndex)
        assertFalse("Must not be off-track on snap", updatedState.isOffTrack)
        assertFalse("Must suppress false 'Route segment skipped' alerts", fakeAlerts.announcements.any { it.contains("Route segment skipped", ignoreCase = true) })
    }

    @Test
    fun testStepTracking_speechDistanceNeverProducesInNowAcrossBoundaryValues() {
        val testDistances = floatArrayOf(
            0.0f, 0.1f, 1.0f, 5.0f, 15.0f, 24.9f, 25.0f, 30.0f, 49.0f, 50.0f,
            74.0f, 75.0f, 99.0f, 100.0f, 250.0f, 299.0f, 300.0f, 301.0f,
            949.0f, 950.0f, 999.0f, 1000.0f, 1049.0f, 1050.0f, 1100.0f,
            2450.0f, 5000.0f, 9999.0f, 10000.0f
        )

        for (d in testDistances) {
            val formatted = useCase.formatDistanceForSpeech(d)
            assertFalse("Distance formatting for ${d}m must never contain 'now': $formatted", formatted.contains("now", ignoreCase = true))
            assertFalse("Distance formatting for ${d}m must never start with 'In now': $formatted", formatted.startsWith("In now", ignoreCase = true))
            assertTrue("Distance formatting for ${d}m must end in valid unit: $formatted", formatted.endsWith("meters") || formatted.endsWith("kilometer") || formatted.endsWith("kilometers"))
        }
    }
}

