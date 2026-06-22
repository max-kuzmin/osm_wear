package com.osm.wear.services

import com.osm.wear.models.MapRegion

/**
 * Catalogue of downloadable Mapsforge map regions.
 * Download URLs point to https://download.mapsforge.org/maps/v5/
 * which serves free OpenStreetMap-derived .map files.
 */
import com.osm.wear.services.IMapRegionCatalogService
import javax.inject.Inject

class MapRegionCatalogService @Inject constructor() : IMapRegionCatalogService {

    companion object {
        private const val BASE = "https://download.mapsforge.org/maps/v5"
    }

    private fun r(id: String, name: String, continent: String, path: String, sizeMb: Int) =
        MapRegion(
            id          = id,
            name        = name,
            continent   = continent,
            downloadUrl = "$BASE/$path",
            fileSizeMb  = sizeMb,
            fileName    = path.substringAfterLast('/')
        )

    override val all: List<MapRegion> = listOf(

        // ── Africa ────────────────────────────────────────────────────────────
        r("africa/egypt",           "Egypt",           "Africa",        "africa/egypt.map",                    173),
        r("africa/kenya",           "Kenya",           "Africa",        "africa/kenya.map",                    316),
        r("africa/morocco",         "Morocco",         "Africa",        "africa/morocco.map",                  212),
        r("africa/nigeria",         "Nigeria",         "Africa",        "africa/nigeria.map",                  729),
        r("africa/south-africa-and-lesotho", "South Africa", "Africa",  "africa/south-africa-and-lesotho.map", 516),

        // ── Asia ──────────────────────────────────────────────────────────────
        r("asia/china/beijing",     "China (Beijing)", "Asia",          "asia/china/beijing.map",               33),
        r("asia/china/shanghai",    "China (Shanghai)","Asia",          "asia/china/shanghai.map",              27),
        r("asia/china/guangdong",   "China (Guangdong)","Asia",         "asia/china/guangdong.map",            155),
        r("asia/china/sichuan",     "China (Sichuan)", "Asia",          "asia/china/sichuan.map",              121),
        r("asia/india",             "India",           "Asia",          "asia/india.map",                     1500),
        r("asia/indonesia",         "Indonesia",       "Asia",          "asia/indonesia.map",                 1700),
        r("asia/japan",             "Japan",           "Asia",          "asia/japan.map",                     1600),
        r("asia/south-korea",       "South Korea",     "Asia",          "asia/south-korea.map",                247),
        r("asia/thailand",          "Thailand",        "Asia",          "asia/thailand.map",                   287),
        r("asia/vietnam",           "Vietnam",         "Asia",          "asia/vietnam.map",                    266),

        // ── Oceania ───────────────────────────────────────────────────────────
        r("australia-oceania/australia",  "Australia",   "Oceania",     "australia-oceania/australia.map",    1600),
        r("australia-oceania/new-zealand-1", "New Zealand (North)", "Oceania", "australia-oceania/new-zealand-1.map", 373),
        r("australia-oceania/new-zealand-2", "New Zealand (South)", "Oceania", "australia-oceania/new-zealand-2.map", 356),

        // ── Europe ────────────────────────────────────────────────────────────
        r("europe/austria",         "Austria",         "Europe",        "europe/austria.map",                  522),
        r("europe/belgium",         "Belgium",         "Europe",        "europe/belgium.map",                  486),
        r("europe/croatia",         "Croatia",         "Europe",        "europe/croatia.map",                  164),
        r("europe/czech-republic",  "Czech Republic",  "Europe",        "europe/czech-republic.map",           573),
        r("europe/denmark",         "Denmark",         "Europe",        "europe/denmark.map",                  317),
        r("europe/finland",         "Finland",         "Europe",        "europe/finland.map",                  651),
        r("europe/france",          "France",          "Europe",        "europe/france.map",                  3200),
        r("europe/germany",         "Germany",         "Europe",        "europe/germany.map",                  3000),
        r("europe/united-kingdom/england", "England",  "Europe",        "europe/united-kingdom/england.map",   1100),
        r("europe/united-kingdom/scotland","Scotland", "Europe",        "europe/united-kingdom/scotland.map",   269),
        r("europe/united-kingdom/wales",   "Wales",    "Europe",        "europe/united-kingdom/wales.map",      105),
        r("europe/greece",          "Greece",          "Europe",        "europe/greece.map",                   251),
        r("europe/hungary",         "Hungary",         "Europe",        "europe/hungary.map",                  247),
        r("europe/ireland-and-northern-ireland", "Ireland", "Europe",   "europe/ireland-and-northern-ireland.map", 312),
        r("europe/italy",           "Italy",           "Europe",        "europe/italy.map",                   1500),
        r("europe/netherlands",     "Netherlands",     "Europe",        "europe/netherlands.map",              890),
        r("europe/norway",          "Norway",          "Europe",        "europe/norway.map",                  1700),
        r("europe/poland",          "Poland",          "Europe",        "europe/poland.map",                  1500),
        r("europe/portugal",        "Portugal",        "Europe",        "europe/portugal.map",                 343),
        r("europe/romania",         "Romania",         "Europe",        "europe/romania.map",                  276),
        r("europe/spain",           "Spain",           "Europe",        "europe/spain.map",                   1100),
        r("europe/sweden",          "Sweden",          "Europe",        "europe/sweden.map",                   776),
        r("europe/switzerland",     "Switzerland",     "Europe",        "europe/switzerland.map",              312),
        r("europe/turkey",          "Turkey",          "Europe",        "europe/turkey.map",                   569),
        r("europe/ukraine",         "Ukraine",         "Europe",        "europe/ukraine.map",                  815),

        // ── North America ─────────────────────────────────────────────────────
        r("north-america/canada/ontario", "Canada (Ontario)", "North America", "north-america/canada/ontario.map", 697),
        r("north-america/canada/quebec", "Canada (Quebec)", "North America", "north-america/canada/quebec.map",   972),
        r("north-america/canada/british-columbia", "Canada (BC)", "North America", "north-america/canada/british-columbia.map", 753),
        r("north-america/canada/alberta", "Canada (Alberta)", "North America", "north-america/canada/alberta.map", 302),
        r("north-america/mexico",   "Mexico",          "North America", "north-america/mexico.map",            655),
        r("north-america/us-midwest","USA Midwest",    "North America", "north-america/us-midwest.map",       1600),
        r("north-america/us-northeast","USA Northeast","North America", "north-america/us-northeast.map",     1100),
        r("north-america/us-south", "USA South",       "North America", "north-america/us-south.map",         2800),
        r("north-america/us-west",  "USA West",        "North America", "north-america/us-west.map",          2100),

        // ── Russia ────────────────────────────────────────────────────────────
        r("russia/northwestern-fed-district", "Russia (Northwest)", "Russia", "russia/northwestern-fed-district.map", 1300),
        r("russia/central-fed-district", "Russia (Central)", "Russia",      "russia/central-fed-district.map",      752),
        r("russia/siberian-fed-district", "Russia (Siberian)", "Russia",    "russia/siberian-fed-district.map",    1600),
        r("russia/volga-fed-district", "Russia (Volga)",   "Russia",        "russia/volga-fed-district.map",        771),
        r("russia/ural-fed-district", "Russia (Ural)",     "Russia",        "russia/ural-fed-district.map",         609),

        // ── South America ─────────────────────────────────────────────────────
        r("south-america/argentina","Argentina",       "South America", "south-america/argentina.map",         526),
        r("south-america/brazil",   "Brazil",          "South America", "south-america/brazil.map",           2000),
        r("south-america/chile",    "Chile",           "South America", "south-america/chile.map",             661),
        r("south-america/colombia", "Colombia",        "South America", "south-america/colombia.map",          314),
        r("south-america/peru",     "Peru",            "South America", "south-america/peru.map",              268)
    )

    /** Unique continent names in display order. */
    override val continents: List<String>
        get() = all.map { it.continent }.distinct().sorted()

    /** Find a region by its id. */
    override fun findById(id: String): MapRegion? = all.find { it.id == id }
}

