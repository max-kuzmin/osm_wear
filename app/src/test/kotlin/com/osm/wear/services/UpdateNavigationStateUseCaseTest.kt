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
import kotlin.math.*

class UpdateNavigationStateUseCaseTest {

    private lateinit var fakeAlerts: FakeAlertsRepository
    private lateinit var useCase: UpdateNavigationStateUseCase

    @Before
    fun setUp() {
        fakeAlerts = FakeAlertsRepository()
        useCase = UpdateNavigationStateUseCase(fakeAlerts)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // R2 Voice Phrasing: formatDistanceForSpeech Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testFormatDistanceForSpeech_neverProducesNowAcrossDomain0To10000m() {
        val testDistances = mutableListOf<Float>()
        for (d in 0..100) testDistances.add(d.toFloat())
        for (d in 100..1000 step 10) testDistances.add(d.toFloat())
        for (d in 1000..10000 step 50) testDistances.add(d.toFloat())

        for (d in testDistances) {
            val formatted = useCase.formatDistanceForSpeech(d)
            assertFalse(
                "Distance $d m produced 'now' or contained 'now': '$formatted'",
                formatted.equals("now", ignoreCase = true) || formatted.contains("now", ignoreCase = true)
            )
            assertTrue(
                "Distance $d m produced invalid suffix: '$formatted'",
                formatted.endsWith("meters") || formatted.endsWith("kilometer") || formatted.endsWith("kilometers")
            )
        }
    }

    @Test
    fun testFormatDistanceForSpeech_exactBrackets() {
        assertEquals("50 meters", useCase.formatDistanceForSpeech(0f))
        assertEquals("50 meters", useCase.formatDistanceForSpeech(10f))
        assertEquals("50 meters", useCase.formatDistanceForSpeech(24.9f))
        assertEquals("50 meters", useCase.formatDistanceForSpeech(25f))
        assertEquals("50 meters", useCase.formatDistanceForSpeech(30f))
        assertEquals("50 meters", useCase.formatDistanceForSpeech(50f))
        assertEquals("50 meters", useCase.formatDistanceForSpeech(74f))
        assertEquals("100 meters", useCase.formatDistanceForSpeech(75f))
        assertEquals("300 meters", useCase.formatDistanceForSpeech(300f))

        val at990 = useCase.formatDistanceForSpeech(990f)
        assertTrue(at990 == "1 kilometer" || at990 == "1000 meters")
        assertEquals("1 kilometer", useCase.formatDistanceForSpeech(1000f))
        assertEquals("1.1 kilometers", useCase.formatDistanceForSpeech(1050f))
        assertEquals("2.5 kilometers", useCase.formatDistanceForSpeech(2500f))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Step-Based Voice Guidance & Tracking Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun testUpdateNavigationState_noInNowAnnouncementDuringNavigation() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85818, 2.2940) // ~20m North
        val p2 = GeoPoint(48.85818, 2.29468)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Rue de Paris")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        val shortDistLocs = listOf(
            UserLocation(48.8580, 2.2940, accuracy = 3f),
            UserLocation(48.8581, 2.2940, accuracy = 3f),
            UserLocation(48.85818, 2.2940, accuracy = 3f)
        )

        for (loc in shortDistLocs) {
            fakeAlerts.clear()
            useCase(state, loc)
            for (announcement in fakeAlerts.announcements) {
                assertFalse("Announcement must never contain 'In now': '$announcement'", announcement.contains("In now", ignoreCase = true))
                assertFalse("Announcement must never start with 'In now': '$announcement'", announcement.startsWith("In now", ignoreCase = true))
            }
        }
    }

    @Test
    fun testUpdateNavigationState_immediateAlarmFiresAtOrBelow30mWithVibration() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.858225, 2.2940) // ~25m North
        val p2 = GeoPoint(48.858225, 2.29468)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Grand Avenue")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)
        useCase.setAlertFlagsForTest(rightAfter = true, k1 = true, m300 = true, during = false)

        val userLoc = UserLocation(48.8580, 2.2940, accuracy = 3f)
        val updatedState = useCase(state, userLoc)

        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("Turn right onto Grand Avenue", fakeAlerts.announcements.first())

        assertEquals(1, fakeAlerts.vibrations.size)
        val expectedPattern = longArrayOf(0, 200, 100, 200, 100, 400)
        assertArrayEquals(expectedPattern, fakeAlerts.vibrations.first())

        assertEquals(0, updatedState.lastAlertedStepIndex)
    }

    @Test
    fun testUpdateNavigationState_turnAlarmNotSuppressedWhenStartingWithin30m() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85818, 2.2940) // ~20m North
        val p2 = GeoPoint(48.85818, 2.29332)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_LEFT, -90f, roadName = "Oak Lane")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        val userLoc = UserLocation(48.8580, 2.2940, accuracy = 3f)
        val updatedState = useCase(state, userLoc)

        assertEquals("Vibration must fire even when starting <= 30m", 1, fakeAlerts.vibrations.size)
        assertArrayEquals(longArrayOf(0, 200, 100, 200, 100, 400), fakeAlerts.vibrations.first())

        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("Turn left onto Oak Lane", fakeAlerts.announcements.first())
        assertEquals(0, updatedState.lastAlertedStepIndex)
    }

    @Test
    fun testUpdateNavigationState_preliminaryAnnouncementSuppressedNearTurn() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85836, 2.2940) // ~40m North
        val p2 = GeoPoint(48.85836, 2.29482)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Pine St")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        val userLocAt40m = UserLocation(48.8580, 2.2940, accuracy = 3f)
        val stateAt40m = useCase(state, userLocAt40m)

        assertTrue("Preliminary speech suppressed at 40m near turn", fakeAlerts.announcements.isEmpty())

        val userLocAt25m = UserLocation(48.858135, 2.2940, accuracy = 3f)
        val stateAt25m = useCase(stateAt40m, userLocAt25m)

        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("Turn right onto Pine St", fakeAlerts.announcements.first())
        assertEquals(1, fakeAlerts.vibrations.size)
        assertEquals(0, stateAt25m.lastAlertedStepIndex)
    }

    @Test
    fun testUpdateNavigationState_thresholdProgressionSequence() {
        val startLat = 48.8500
        val turnLat = startLat + 0.010798 // ~1200m North
        val p0 = GeoPoint(startLat, 2.2940)
        val p1 = GeoPoint(turnLat, 2.2940)
        val p2 = GeoPoint(turnLat, 2.2953)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Main St")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        var state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        // Step 1: At 1200m (Segment start)
        val loc1200 = UserLocation(startLat, 2.2940, accuracy = 3f)
        state = useCase(state, loc1200)
        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("In 1.2 kilometers, turn right onto Main St", fakeAlerts.announcements.first())

        // Step 2: At 1000m (1km threshold reached)
        fakeAlerts.clear()
        val loc1000 = UserLocation(startLat + (210.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc1000)
        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("In 1 kilometer, turn right onto Main St", fakeAlerts.announcements.first())

        // Step 3: At 500m (No milestone reached)
        fakeAlerts.clear()
        val loc500 = UserLocation(startLat + (700.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc500)
        assertTrue(fakeAlerts.announcements.isEmpty())

        // Step 4: At 300m (300m threshold reached)
        fakeAlerts.clear()
        val loc300 = UserLocation(startLat + (910.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc300)
        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("In 300 meters, turn right onto Main St", fakeAlerts.announcements.first())

        // Step 5: At 25m (Immediate turn alarm reached)
        fakeAlerts.clear()
        val loc25 = UserLocation(startLat + (1175.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc25)
        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("Turn right onto Main St", fakeAlerts.announcements.first())
        assertEquals(1, fakeAlerts.vibrations.size)

        // Step 6: At 10m (Same turn, no duplicate alarm)
        fakeAlerts.clear()
        val loc10 = UserLocation(startLat + (1190.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc10)
        assertTrue("No duplicate alarm when still in turn zone", fakeAlerts.announcements.isEmpty())
        assertTrue("No duplicate vibration", fakeAlerts.vibrations.isEmpty())
    }

    @Test
    fun testUpdateNavigationState_preventDuplicateMilestoneAnnouncementsAt320m() {
        val startLat = 48.8500
        val turnLat = startLat + (320.0 / 111132.9)
        val p0 = GeoPoint(startLat, 2.2940)
        val p1 = GeoPoint(turnLat, 2.2940)
        val p2 = GeoPoint(turnLat, 2.2947)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Broadway")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        var state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        // User enters segment at 320m
        val loc320 = UserLocation(startLat, 2.2940, accuracy = 3f)
        state = useCase(state, loc320)
        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("In 300 meters, turn right onto Broadway", fakeAlerts.announcements.first())

        // User advances to 290m (past the raw 300m threshold)
        fakeAlerts.clear()
        val loc290 = UserLocation(startLat + (30.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc290)

        assertTrue("Duplicate 300m announcement must be prevented", fakeAlerts.announcements.isEmpty())
    }

    @Test
    fun testUpdateNavigationState_preventDuplicateMilestoneAnnouncementsAt1020m() {
        val startLat = 48.8500
        val turnLat = startLat + (1020.0 / 111132.9)
        val p0 = GeoPoint(startLat, 2.2940)
        val p1 = GeoPoint(turnLat, 2.2940)
        val p2 = GeoPoint(turnLat, 2.2947)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Highway 1")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        var state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        // User enters segment at 1020m
        val loc1020 = UserLocation(startLat, 2.2940, accuracy = 3f)
        state = useCase(state, loc1020)
        assertEquals(1, fakeAlerts.announcements.size)
        assertEquals("In 1 kilometer, turn right onto Highway 1", fakeAlerts.announcements.first())

        // User advances to 980m (past the raw 1000m threshold)
        fakeAlerts.clear()
        val loc980 = UserLocation(startLat + (40.0 / 111132.9), 2.2940, accuracy = 3f)
        state = useCase(state, loc980)

        assertTrue("Duplicate 1km announcement must be prevented", fakeAlerts.announcements.isEmpty())
    }

    @Test
    fun testUpdateNavigationState_turnDirectionLeftAndRightWithStreetSuffix() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.858225, 2.2940) // ~25m
        val p2 = GeoPoint(48.858225, 2.29332)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_LEFT, -90f, roadName = null)
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val stateLeft = createStepState(listOf(step0, step1), currentStepIdx = 0)
        useCase.setAlertFlagsForTest(rightAfter = true)
        useCase(stateLeft, UserLocation(48.8580, 2.2940, accuracy = 3f))

        assertEquals("Turn left", fakeAlerts.announcements.first())
    }

    @Test
    fun testUpdateNavigationState_cutAheadProgressionSnapsForwardWithoutSkippedAlarm() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85845, 2.2940)
        val p2 = GeoPoint(48.85845, 2.29468)
        val p3 = GeoPoint(48.85845, 2.29536)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.DEPART)
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.TURN_RIGHT, 90f, roadName = "Skipped St")
        val step2 = createStep(2, listOf(p2, p3), ManeuverType.ARRIVE)

        var state = createStepState(listOf(step0, step1, step2), currentStepIdx = 0)

        // GPS updates directly to step 2, skipping step 1
        val locStep2 = UserLocation(48.85845, 2.2949, accuracy = 3f)
        state = useCase(state, locStep2)

        assertEquals("Tracking must snap forward to step 2", 2, state.currentStepIndex)
        assertFalse("Snapped state must not be off-track", state.isOffTrack)
        assertFalse("Must never alarm 'Route segment skipped'", fakeAlerts.announcements.contains("Route segment skipped"))
    }

    @Test
    fun testUpdateNavigationState_offTrackDetection() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.8625, 2.2940) // 500m North

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.ARRIVE)
        val state = createStepState(listOf(step0), currentStepIdx = 0)

        // Location 120m East of track line (OFF_TRACK_THRESHOLD_M is 35m)
        val offTrackLoc = UserLocation(48.8600, 2.2940 + 0.00164, accuracy = 3f)
        val offTrackState = useCase(state, offTrackLoc)
        assertTrue("User > 35m from route must be flagged isOffTrack", offTrackState.isOffTrack)

        // Return onto track (10m from line)
        val onTrackLoc = UserLocation(48.8600, 2.2940 + (10.0 / 73047.0), accuracy = 3f)
        val onTrackState = useCase(offTrackState, onTrackLoc)
        assertFalse("User back on track must not be isOffTrack", onTrackState.isOffTrack)
    }

    @Test
    fun testUpdateNavigationState_destinationArrival() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85818, 2.2940) // ~20m

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.ARRIVE)
        val state = createStepState(listOf(step0), currentStepIdx = 0)

        // User within 20m of final destination
        val finalLoc = UserLocation(48.85810, 2.2940, accuracy = 3f)
        val finishedState = useCase(state, finalLoc)

        assertFalse("Route must be deactivated upon reaching destination", finishedState.isActive)
        assertTrue(fakeAlerts.announcements.contains("Destination reached"))
        assertEquals(1, fakeAlerts.vibrations.size)
        assertFalse("Destination arrival hasNextTurn must be false", finishedState.hasNextTurn)
    }

    @Test
    fun testUpdateNavigationState_normalTurnProgressionDoesNotFireSkippedTurnAlarm() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85827, 2.2940) // ~30m
        val p2 = GeoPoint(48.85827, 2.2944)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "High St")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        // User moves past junction onto step 1
        val locOnStep1 = UserLocation(48.85827, 2.2941, accuracy = 3f)
        fakeAlerts.clear()
        val nextState = useCase(state, locOnStep1)

        assertFalse(
            "Advancing through a turn to the next step must not alarm 'Route segment skipped'",
            fakeAlerts.announcements.contains("Route segment skipped")
        )
        assertEquals(1, nextState.currentStepIndex)
    }

    @Test
    fun testUpdateNavigationState_hasNextTurnStateTransitions() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85836, 2.2940) // ~40m
        val p2 = GeoPoint(48.85836, 2.2872) // ~500m West

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_LEFT, -90f, roadName = "West Way")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)

        // 1. Approaching turn on step 0
        val locStep0 = UserLocation(48.85810, 2.2940, accuracy = 3f)
        val stateApproaching = useCase(state, locStep0)
        assertTrue(stateApproaching.hasNextTurn)
        assertEquals(-90f, stateApproaching.bearingToNextTurn, 0.001f)

        // 2. Past turn on step 1
        val locStep1 = UserLocation(48.85836, 2.2920, accuracy = 3f)
        val statePastTurn = useCase(stateApproaching, locStep1)
        assertFalse("After passing the last turn, hasNextTurn must be false", statePastTurn.hasNextTurn)
        assertEquals(0f, statePastTurn.bearingToNextTurn, 0.001f)
        assertEquals(statePastTurn.totalRemainingM, statePastTurn.distanceToNextTurnM, 1f)
    }

    @Test
    fun testUpdateNavigationState_overlayDistanceMatchesSpokenDistance() {
        val startLat = 48.8500
        val turnLat = startLat + (280.0 / 111132.9)
        val p0 = GeoPoint(startLat, 2.2940)
        val p1 = GeoPoint(turnLat, 2.2940)
        val p2 = GeoPoint(turnLat, 2.2953)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "Target Way")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1), currentStepIdx = 0)
        useCase.setAlertFlagsForTest(rightAfter = true)

        val locAt280 = UserLocation(startLat, 2.2940, accuracy = 3f)
        val updated = useCase(state, locAt280)

        assertEquals(1, fakeAlerts.announcements.size)
        val spoken = fakeAlerts.announcements.first()
        val expectedSpokenDist = useCase.formatDistanceForSpeech(updated.distanceToNextTurnM)
        assertTrue(
            "Spoken message must contain formatted distance to next turn '$expectedSpokenDist': '$spoken'",
            spoken.contains(expectedSpokenDist)
        )
    }

    @Test
    fun testUpdateNavigationState_afterCompletingTurnNavAdvancesImmediately() {
        val p0 = GeoPoint(48.8580, 2.2940)
        val p1 = GeoPoint(48.85827, 2.2940) // ~30m
        val p2 = GeoPoint(48.85827, 2.2944)
        val p3 = GeoPoint(48.85827, 2.2970) // ~200m
        val p4 = GeoPoint(48.85870, 2.2970)

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.TURN_RIGHT, 90f, roadName = "First Turn")
        val step1 = createStep(1, listOf(p1, p2, p3), ManeuverType.TURN_LEFT, -90f, roadName = "Second Turn")
        val step2 = createStep(2, listOf(p3, p4), ManeuverType.ARRIVE)

        // 1. Approaching turn 0: alert fires
        val stateApproaching = createStepState(listOf(step0, step1, step2), currentStepIdx = 0)
        val locAtTurn = UserLocation(48.85820, 2.2940, accuracy = 3f) // ~8m from turn
        val stateAlerted = useCase(stateApproaching, locAtTurn)
        assertEquals(0, stateAlerted.lastAlertedStepIndex)

        // 2. User completes turn onto step 1
        val locPastTurn = UserLocation(48.85827, 2.29415, accuracy = 3f)
        val stateNext = useCase(stateAlerted, locPastTurn)

        // Must advance to Second Turn (step 1)
        assertEquals("Must advance to next turn at step 1", 1, stateNext.lastNextTurnIndex)
        assertEquals("Current step must advance to 1", 1, stateNext.currentStepIndex)
        assertTrue("hasNextTurn must remain true for upcoming turn", stateNext.hasNextTurn)
        assertEquals("Bearing must target second turn (-90°)", -90f, stateNext.bearingToNextTurn, 0.001f)
    }

    @Test
    fun testUpdateNavigationState_corridorLookaheadAcrossContinueSteps() {
        val p0 = GeoPoint(51.5000, -0.1200)
        val p1 = GeoPoint(51.5009, -0.1200) // 100m straight
        val p2 = GeoPoint(51.5027, -0.1200) // 200m to turn
        val p3 = GeoPoint(51.5027, -0.1215) // destination

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.CONTINUE, 0f, roadName = "Straight St")
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.TURN_RIGHT, 90f, roadName = "Right Way")
        val step2 = createStep(2, listOf(p2, p3), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1, step2), currentStepIdx = 0)

        // Initial target looks ahead to step 1 (TURN_RIGHT), distance is 100m + 200m = 300m
        val stateAtStart = useCase(state, UserLocation(p0.lat, p0.lon, accuracy = 2f))
        assertEquals(1, stateAtStart.lastNextTurnIndex)
        assertTrue(stateAtStart.hasNextTurn)
        assertEquals(300f, stateAtStart.distanceToNextTurnM, 15f)
        assertEquals(90f, stateAtStart.bearingToNextTurn, 0.01f)

        // User walks 50m along step 0 -> distance counts down to ~250m monotonically
        val pMid = GeoPoint(51.50045, -0.1200)
        val stateMid = useCase(stateAtStart, UserLocation(pMid.lat, pMid.lon, accuracy = 2f))
        assertEquals(250f, stateMid.distanceToNextTurnM, 15f)
        assertTrue(stateMid.distanceToNextTurnM < stateAtStart.distanceToNextTurnM)
    }

    @Test
    fun testUpdateNavigationState_remainingDistanceCalculationMonotonicSum() {
        val p0 = GeoPoint(51.5000, -0.1200)
        val p1 = GeoPoint(51.5009, -0.1200) // 100m
        val p2 = GeoPoint(51.5027, -0.1200) // 200m
        val p3 = GeoPoint(51.5054, -0.1200) // 300m

        val step0 = createStep(0, listOf(p0, p1), ManeuverType.CONTINUE)
        val step1 = createStep(1, listOf(p1, p2), ManeuverType.CONTINUE)
        val step2 = createStep(2, listOf(p2, p3), ManeuverType.ARRIVE)

        val state = createStepState(listOf(step0, step1, step2), currentStepIdx = 0)

        // At start of step 0: ~600m total remaining
        val state0 = useCase(state, UserLocation(p0.lat, p0.lon, accuracy = 2f))
        assertEquals(600f, state0.totalRemainingM, 20f)

        // User advances to middle of step 1 (p1 + 100m)
        val pMidStep1 = GeoPoint(51.5018, -0.1200)
        val state1 = useCase(state0.copy(currentStepIndex = 1), UserLocation(pMidStep1.lat, pMidStep1.lon, accuracy = 2f))
        assertEquals(400f, state1.totalRemainingM, 20f)
        assertTrue(state1.totalRemainingM < state0.totalRemainingM)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Helper factories
    // ─────────────────────────────────────────────────────────────────────────────

    private fun createStep(
        index: Int,
        polyline: List<GeoPoint>,
        maneuverType: ManeuverType,
        relativeAngle: Float = 0f,
        roadName: String? = null,
        instruction: String = ""
    ): RouteStep {
        val junctionPt = polyline.last()
        var dist = 0.0
        for (i in 0 until polyline.size - 1) {
            dist += haversineDistanceM(polyline[i], polyline[i + 1])
        }
        return RouteStep(
            index = index,
            polyline = polyline,
            distanceM = dist.toFloat(),
            roadName = roadName,
            maneuver = Maneuver(
                type = maneuverType,
                location = junctionPt,
                relativeAngle = relativeAngle,
                roadName = roadName,
                instruction = instruction.ifEmpty {
                    if (maneuverType == ManeuverType.ARRIVE) "Arrive at destination" else "Turn at junction"
                }
            )
        )
    }

    private fun createStepState(
        steps: List<RouteStep>,
        currentStepIdx: Int = 0,
        lastNextTurnIdx: Int = -1,
        lastAlertedStep: Int = -1
    ): NavigationState {
        val activeTarget = UpdateNavigationStateUseCase.resolveActiveTarget(
            steps = steps,
            stepIdx = currentStepIdx,
            distToActiveManeuver = steps.getOrNull(currentStepIdx)?.distanceM ?: 0f
        )
        val totalRem = steps.drop(currentStepIdx).sumOf { it.distanceM.toDouble() }.toFloat()
        return NavigationState(
            steps = steps,
            currentStepIndex = currentStepIdx,
            distanceToNextTurnM = activeTarget.distanceM,
            bearingToNextTurn = activeTarget.visualAngle,
            totalRemainingM = totalRem,
            hasNextTurn = activeTarget.isTurn,
            isActive = true,
            isNavigating = true,
            currentRoadName = steps.getOrNull(currentStepIdx)?.roadName,
            lastNextTurnIndex = if (lastNextTurnIdx != -1) lastNextTurnIdx else (if (activeTarget.isTurn) activeTarget.stepIndex else -1),
            lastAlertedStepIndex = lastAlertedStep
        )
    }

    private fun haversineDistanceM(a: GeoPoint, b: GeoPoint): Double {
        val r = 6_371_000.0
        val dLat = Math.toRadians(b.lat - a.lat)
        val dLon = Math.toRadians(b.lon - a.lon)
        val h = sin(dLat / 2).pow(2) +
                cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
        val clampedH = h.coerceIn(0.0, 1.0)
        return r * 2 * atan2(sqrt(clampedH), sqrt(1.0 - clampedH))
    }
}
