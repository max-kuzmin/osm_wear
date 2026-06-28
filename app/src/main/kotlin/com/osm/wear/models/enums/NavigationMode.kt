package com.osm.wear.models.enums

enum class NavigationMode(val label: String, val profile: String) {
    WALKING("Walking", "foot"),
    CYCLING("Cycling", "bike"),
    DRIVING("Driving", "driving"),
    GPX_ONLY("GPX points only", "foot")
}

// ─── GPS ─────────────────────────────────────────────────────────────────────

