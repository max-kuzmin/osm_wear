package com.osm.wear.models.enums

enum class NavigationMode(val label: String, val profile: String) {
    WALKING("Walking", "foot"),
    CYCLING("Cycling", "bicycle"),
    DRIVING("Driving", "car"),
    GPX_ONLY("GPX points only", "foot")
}

// ─── GPS ─────────────────────────────────────────────────────────────────────

