package com.osm.wear.data_sources

interface IDeviceAlertsDataSource {
    fun announce(message: String)
    fun vibrate(pattern: LongArray)
    fun playNotificationSound()
    fun showDeviceNotification(message: String)
    fun release()
}
