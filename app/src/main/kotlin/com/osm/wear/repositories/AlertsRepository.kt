package com.osm.wear.repositories

import com.osm.wear.data_sources.IDeviceAlertsDataSource
import com.osm.wear.data_sources.ILocalPreferencesDataSource
import com.osm.wear.models.enums.NavigationAlertMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AlertsRepository @Inject constructor(
    private val deviceAlertsDataSource: IDeviceAlertsDataSource,
    private val prefs: ILocalPreferencesDataSource
) : IAlertsRepository {

    private val _alertMode = MutableStateFlow(NavigationAlertMode.VOICE)
    override val alertMode: StateFlow<NavigationAlertMode> = _alertMode.asStateFlow()

    init {
        _alertMode.value = getAlertMode()
    }

    override fun getAlertMode(): NavigationAlertMode {
        val name = prefs.getString("navigation_alert_mode", NavigationAlertMode.VOICE.name)
        return try {
            NavigationAlertMode.valueOf(name!!)
        } catch (e: Exception) {
            NavigationAlertMode.VOICE
        }
    }

    override fun setAlertMode(mode: NavigationAlertMode) {
        _alertMode.value = mode
        prefs.putString("navigation_alert_mode", mode.name)
    }

    override fun announce(message: String) {
        val mode = _alertMode.value
        if (mode == NavigationAlertMode.SILENT) return

        if (mode == NavigationAlertMode.VOICE) {
            deviceAlertsDataSource.announce(message)
        } else if (mode == NavigationAlertMode.SOUND) {
            deviceAlertsDataSource.playNotificationSound()
        }
    }

    override fun vibrate(pattern: LongArray) {
        val mode = _alertMode.value
        if (mode == NavigationAlertMode.SILENT) return

        if (mode == NavigationAlertMode.VOICE || mode == NavigationAlertMode.SOUND || mode == NavigationAlertMode.VIBRATION) {
            deviceAlertsDataSource.vibrate(pattern)
        }
    }

    override fun release() {
        deviceAlertsDataSource.release()
    }
}
