package com.osm.wear.models.enums

enum class Routes(val value: String) {
    MAP("map"),
    MAIN_MENU("main_menu"),
    REGIONS("regions"),
    TRACKS("tracks"),
    MARKERS("markers"),
    PREFERENCES("preferences"),
    SEARCH_ADDRESS("search_address");

    override fun toString(): String = value
}
