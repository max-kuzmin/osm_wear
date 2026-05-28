package com.osm.wear.data.map

import com.osm.wear.domain.model.MapRegion

/**
 * Catalogue of downloadable Mapsforge map regions.
 * Download URLs point to https://download.mapsforge.org/maps/v5/
 * which serves free OpenStreetMap-derived .map files.
 */
object MapRegionCatalog {

    private const val BASE = "https://download.mapsforge.org/maps/v5"

    private fun r(id: String, name: String, continent: String, path: String, sizeMb: Int) =
        MapRegion(
            id          = id,
            name        = name,
            continent   = continent,
            downloadUrl = "$BASE/$path",
            fileSizeMb  = sizeMb,
            fileName    = path.substringAfterLast('/')
        )

    val all: List<MapRegion> = listOf(

        // ── Africa ────────────────────────────────────────────────────────────
        r("africa/egypt",           "Egypt",           "Africa",        "africa/egypt.map",                    120),
        r("africa/kenya",           "Kenya",           "Africa",        "africa/kenya.map",                     80),
        r("africa/morocco",         "Morocco",         "Africa",        "africa/morocco.map",                   90),
        r("africa/nigeria",         "Nigeria",         "Africa",        "africa/nigeria.map",                   95),
        r("africa/south-africa",    "South Africa",    "Africa",        "africa/south-africa.map",             200),

        // ── Asia ──────────────────────────────────────────────────────────────
        r("asia/china",             "China",           "Asia",          "asia/china.map",                      850),
        r("asia/india",             "India",           "Asia",          "asia/india.map",                      480),
        r("asia/indonesia",         "Indonesia",       "Asia",          "asia/indonesia.map",                  310),
        r("asia/japan",             "Japan",           "Asia",          "asia/japan.map",                      560),
        r("asia/south-korea",       "South Korea",     "Asia",          "asia/south-korea.map",                130),
        r("asia/thailand",          "Thailand",        "Asia",          "asia/thailand.map",                   120),
        r("asia/vietnam",           "Vietnam",         "Asia",          "asia/vietnam.map",                     90),

        // ── Oceania ───────────────────────────────────────────────────────────
        r("australia-oceania/australia",  "Australia",   "Oceania",     "australia-oceania/australia.map",     420),
        r("australia-oceania/new-zealand","New Zealand", "Oceania",     "australia-oceania/new-zealand.map",    80),

        // ── Europe ────────────────────────────────────────────────────────────
        r("europe/austria",         "Austria",         "Europe",        "europe/austria.map",                  130),
        r("europe/belgium",         "Belgium",         "Europe",        "europe/belgium.map",                   75),
        r("europe/croatia",         "Croatia",         "Europe",        "europe/croatia.map",                   55),
        r("europe/czech-republic",  "Czech Republic",  "Europe",        "europe/czech-republic.map",           120),
        r("europe/denmark",         "Denmark",         "Europe",        "europe/denmark.map",                   65),
        r("europe/finland",         "Finland",         "Europe",        "europe/finland.map",                  130),
        r("europe/france",          "France",          "Europe",        "europe/france.map",                   560),
        r("europe/germany",         "Germany",         "Europe",        "europe/germany.map",                  560),
        r("europe/great-britain",   "Great Britain",   "Europe",        "europe/great-britain.map",            380),
        r("europe/greece",          "Greece",          "Europe",        "europe/greece.map",                   100),
        r("europe/hungary",         "Hungary",         "Europe",        "europe/hungary.map",                   80),
        r("europe/ireland",         "Ireland",         "Europe",        "europe/ireland.map",                   55),
        r("europe/italy",           "Italy",           "Europe",        "europe/italy.map",                    430),
        r("europe/netherlands",     "Netherlands",     "Europe",        "europe/netherlands.map",              120),
        r("europe/norway",          "Norway",          "Europe",        "europe/norway.map",                   160),
        r("europe/poland",          "Poland",          "Europe",        "europe/poland.map",                   280),
        r("europe/portugal",        "Portugal",        "Europe",        "europe/portugal.map",                  80),
        r("europe/romania",         "Romania",         "Europe",        "europe/romania.map",                  130),
        r("europe/spain",           "Spain",           "Europe",        "europe/spain.map",                    380),
        r("europe/sweden",          "Sweden",          "Europe",        "europe/sweden.map",                   220),
        r("europe/switzerland",     "Switzerland",     "Europe",        "europe/switzerland.map",              110),
        r("europe/turkey",          "Turkey",          "Europe",        "europe/turkey.map",                   240),
        r("europe/ukraine",         "Ukraine",         "Europe",        "europe/ukraine.map",                  230),

        // ── North America ─────────────────────────────────────────────────────
        r("north-america/canada",   "Canada",          "North America", "north-america/canada.map",            650),
        r("north-america/mexico",   "Mexico",          "North America", "north-america/mexico.map",            280),
        r("north-america/us-midwest","USA Midwest",    "North America", "north-america/us-midwest.map",        280),
        r("north-america/us-northeast","USA Northeast","North America", "north-america/us-northeast.map",      240),
        r("north-america/us-pacific","USA Pacific",    "North America", "north-america/us-pacific.map",        310),
        r("north-america/us-south", "USA South",       "North America", "north-america/us-south.map",          350),
        r("north-america/us-west",  "USA West",        "North America", "north-america/us-west.map",           280),

        // ── Russia ────────────────────────────────────────────────────────────
        r("russia/russia-european", "Russia (European)","Russia",       "russia/russia-european.map",          600),
        r("russia/russia-asian",    "Russia (Asian)",   "Russia",       "russia/russia-asian.map",             500),

        // ── South America ─────────────────────────────────────────────────────
        r("south-america/argentina","Argentina",       "South America", "south-america/argentina.map",         230),
        r("south-america/brazil",   "Brazil",          "South America", "south-america/brazil.map",            680),
        r("south-america/chile",    "Chile",           "South America", "south-america/chile.map",              90),
        r("south-america/colombia", "Colombia",        "South America", "south-america/colombia.map",           90),
        r("south-america/peru",     "Peru",            "South America", "south-america/peru.map",               90)
    )

    /** Unique continent names in display order. */
    val continents: List<String> get() = all.map { it.continent }.distinct()

    /** Find a region by its id. */
    fun findById(id: String): MapRegion? = all.find { it.id == id }
}
