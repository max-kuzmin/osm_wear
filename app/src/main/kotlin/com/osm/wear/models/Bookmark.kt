package com.osm.wear.models

data class Bookmark(
    val name: String,
    val lat: Double,
    val lon: Double,
    val address: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
