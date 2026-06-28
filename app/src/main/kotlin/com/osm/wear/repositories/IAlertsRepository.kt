package com.osm.wear.repositories

import com.osm.wear.models.enums.NavigationAlertMode
import kotlinx.coroutines.flow.StateFlow

interface IAlertsRepository {
    val alertMode: StateFlow<NavigationAlertMode>
    fun getAlertMode(): NavigationAlertMode
    fun setAlertMode(mode: NavigationAlertMode)
    fun announce(message: String)
    fun vibrate(pattern: LongArray)
    fun release()
}
