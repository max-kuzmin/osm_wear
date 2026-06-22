package com.osm.wear.models

data class UserLocation(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val timestamp: Long = System.currentTimeMillis()
)

