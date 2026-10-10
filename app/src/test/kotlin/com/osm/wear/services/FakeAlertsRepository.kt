package com.osm.wear.services

import com.osm.wear.models.enums.NavigationAlertMode
import com.osm.wear.repositories.IAlertsRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Lightweight, in-memory implementation of IAlertsRepository for fast, deterministic unit testing.
 * Records all speech announcements and vibration patterns for direct verification.
 */
class FakeAlertsRepository(
    initialMode: NavigationAlertMode = NavigationAlertMode.VOICE
) : IAlertsRepository {

    private val _alertMode = MutableStateFlow(initialMode)
    override val alertMode: StateFlow<NavigationAlertMode> = _alertMode.asStateFlow()

    val announcements = mutableListOf<String>()
    val vibrations = mutableListOf<LongArray>()
    var releaseCalled = false

    override fun getAlertMode(): NavigationAlertMode = _alertMode.value

    override fun setAlertMode(mode: NavigationAlertMode) {
        _alertMode.value = mode
    }

    override fun announce(message: String) {
        announcements.add(message)
    }

    override fun vibrate(pattern: LongArray) {
        vibrations.add(pattern)
    }

    override fun release() {
        releaseCalled = true
    }

    fun clear() {
        announcements.clear()
        vibrations.clear()
        releaseCalled = false
    }
}
