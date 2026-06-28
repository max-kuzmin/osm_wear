package com.osm.wear.models.enums

enum class Routes(val value: String) {
    MAP("map"),
    MAIN_MENU("main_menu"),
    REGIONS("regions"),
    GPX_TRACKS("gpx_tracks"),
    MARKERS("markers"),
    PREFERENCES("preferences"),
    SEARCH_ADDRESS("search_address");

    override fun toString(): String = value
}
