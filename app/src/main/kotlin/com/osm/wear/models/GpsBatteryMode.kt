package com.osm.wear.models

enum class GpsBatteryMode(
    val intervalMs: Long,
    val minDisplacementM: Float,
    val label: String
) {
    HIGH_ACCURACY(1_000L, 0f, "High Accuracy"),
    BALANCED(5_000L, 5f, "Balanced"),
    LOW_POWER(30_000L, 100f, "Low Power")
}

