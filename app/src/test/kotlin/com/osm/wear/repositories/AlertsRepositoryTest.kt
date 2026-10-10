package com.osm.wear.repositories

import com.osm.wear.data_sources.IDeviceAlertsDataSource
import com.osm.wear.data_sources.ILocalPreferencesDataSource
import com.osm.wear.models.enums.NavigationAlertMode
import io.mockk.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit tests for [AlertsRepository] verifying:
 * 1. Mode dispatching and audio/speech routing for VOICE, SOUND, VIBRATION, and SILENT modes.
 * 2. Haptic vibration dispatching across allowed modes and complete suppression in SILENT mode.
 * 3. Alert mode state updates, preferences persistence, and invalid preference fallback.
 * 4. Proper delegation of lifecycle release calls to the device alerts data source.
 */
class AlertsRepositoryTest {

    private lateinit var deviceAlerts: IDeviceAlertsDataSource
    private lateinit var prefs: ILocalPreferencesDataSource
    private lateinit var repository: AlertsRepository

    @Before
    fun setUp() {
        deviceAlerts = mockk(relaxed = true)
        prefs = mockk(relaxed = true)
        every { prefs.getString("navigation_alert_mode", any()) } returns NavigationAlertMode.VOICE.name
        repository = AlertsRepository(deviceAlerts, prefs)
    }

    @After
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun testAnnounce_voiceMode_callsDeviceAnnounce() {
        repository.setAlertMode(NavigationAlertMode.VOICE)

        repository.announce("Turn left onto Market Street")

        verify(exactly = 1) { deviceAlerts.announce("Turn left onto Market Street") }
        verify(exactly = 0) { deviceAlerts.playNotificationSound() }
    }

    @Test
    fun testAnnounce_soundMode_callsPlayNotificationSound() {
        repository.setAlertMode(NavigationAlertMode.SOUND)

        repository.announce("Turn right onto Mission Street")

        verify(exactly = 1) { deviceAlerts.playNotificationSound() }
        verify(exactly = 0) { deviceAlerts.announce(any()) }
    }

    @Test
    fun testAnnounce_vibrationMode_suppressesSpeechAndSound() {
        repository.setAlertMode(NavigationAlertMode.VIBRATION)

        repository.announce("Approaching junction")

        verify(exactly = 0) { deviceAlerts.announce(any()) }
        verify(exactly = 0) { deviceAlerts.playNotificationSound() }
    }

    @Test
    fun testAnnounce_silentMode_suppressesSpeechAndSound() {
        repository.setAlertMode(NavigationAlertMode.SILENT)

        repository.announce("Destination reached")

        verify(exactly = 0) { deviceAlerts.announce(any()) }
        verify(exactly = 0) { deviceAlerts.playNotificationSound() }
    }

    @Test
    fun testVibrate_voiceSoundVibrationModes_callsDeviceVibrate() {
        val pattern = longArrayOf(0, 200, 100, 400)

        // VOICE mode
        repository.setAlertMode(NavigationAlertMode.VOICE)
        repository.vibrate(pattern)
        verify(exactly = 1) { deviceAlerts.vibrate(pattern) }

        // SOUND mode
        clearMocks(deviceAlerts, answers = false)
        repository.setAlertMode(NavigationAlertMode.SOUND)
        repository.vibrate(pattern)
        verify(exactly = 1) { deviceAlerts.vibrate(pattern) }

        // VIBRATION mode
        clearMocks(deviceAlerts, answers = false)
        repository.setAlertMode(NavigationAlertMode.VIBRATION)
        repository.vibrate(pattern)
        verify(exactly = 1) { deviceAlerts.vibrate(pattern) }
    }

    @Test
    fun testVibrate_silentMode_suppressesVibration() {
        val pattern = longArrayOf(0, 200, 100, 200, 100, 400)
        repository.setAlertMode(NavigationAlertMode.SILENT)

        repository.vibrate(pattern)

        verify(exactly = 0) { deviceAlerts.vibrate(any()) }
    }

    @Test
    fun testSetAlertMode_updatesStateFlowAndPreferences() {
        // Initial state
        assertEquals(NavigationAlertMode.VOICE, repository.alertMode.value)

        // Change to SOUND
        repository.setAlertMode(NavigationAlertMode.SOUND)
        assertEquals(NavigationAlertMode.SOUND, repository.alertMode.value)
        verify { prefs.putString("navigation_alert_mode", "SOUND") }

        // Change to VIBRATION
        repository.setAlertMode(NavigationAlertMode.VIBRATION)
        assertEquals(NavigationAlertMode.VIBRATION, repository.alertMode.value)
        verify { prefs.putString("navigation_alert_mode", "VIBRATION") }

        // Change to SILENT
        repository.setAlertMode(NavigationAlertMode.SILENT)
        assertEquals(NavigationAlertMode.SILENT, repository.alertMode.value)
        verify { prefs.putString("navigation_alert_mode", "SILENT") }

        // Invalid preference fallback test
        every { prefs.getString("navigation_alert_mode", any()) } returns "CORRUPT_VALUE"
        val fallbackRepo = AlertsRepository(deviceAlerts, prefs)
        assertEquals("Corrupted alert mode preference must fall back to VOICE", NavigationAlertMode.VOICE, fallbackRepo.alertMode.value)
    }

    @Test
    fun testRelease_forwardsToDeviceDataSource() {
        repository.release()

        verify(exactly = 1) { deviceAlerts.release() }
    }
}
