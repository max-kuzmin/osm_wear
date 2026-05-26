package com.osm.wear.data.map

import com.osm.wear.domain.model.MapRegion

/**
 * Catalog of downloadable map regions from the official Mapsforge download server.
 * Base URL: https://download.mapsforge.org/maps/v5/
 *
 * Maps are in .map format (Mapsforge v5), compatible with mapsforge-map-android.
 */
object MapRegionCatalog {

    private const val BASE_URL = "https://download.mapsforge.org/maps/v5"

    val regions: List<MapRegion> = listOf(

        // ── Europe ────────────────────────────────────────────────────────────
        MapRegion(
            id = "europe/germany",
            name = "Germany",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/germany.map",
            fileSizeBytes = 700_000_000L
        ),
        MapRegion(
            id = "europe/france",
            name = "France",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/france.map",
            fileSizeBytes = 600_000_000L
        ),
        MapRegion(
            id = "europe/great-britain",
            name = "Great Britain",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/great-britain.map",
            fileSizeBytes = 500_000_000L
        ),
        MapRegion(
            id = "europe/italy",
            name = "Italy",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/italy.map",
            fileSizeBytes = 450_000_000L
        ),
        MapRegion(
            id = "europe/spain",
            name = "Spain",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/spain.map",
            fileSizeBytes = 400_000_000L
        ),
        MapRegion(
            id = "europe/netherlands",
            name = "Netherlands",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/netherlands.map",
            fileSizeBytes = 120_000_000L
        ),
        MapRegion(
            id = "europe/poland",
            name = "Poland",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/poland.map",
            fileSizeBytes = 280_000_000L
        ),
        MapRegion(
            id = "europe/austria",
            name = "Austria",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/austria.map",
            fileSizeBytes = 150_000_000L
        ),
        MapRegion(
            id = "europe/switzerland",
            name = "Switzerland",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/switzerland.map",
            fileSizeBytes = 130_000_000L
        ),
        MapRegion(
            id = "europe/sweden",
            name = "Sweden",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/sweden.map",
            fileSizeBytes = 300_000_000L
        ),
        MapRegion(
            id = "europe/norway",
            name = "Norway",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/norway.map",
            fileSizeBytes = 250_000_000L
        ),
        MapRegion(
            id = "europe/ukraine",
            name = "Ukraine",
            continent = "Europe",
            downloadUrl = "$BASE_URL/europe/ukraine.map",
            fileSizeBytes = 320_000_000L
        ),

        // ── Asia ──────────────────────────────────────────────────────────────
        MapRegion(
            id = "asia/japan",
            name = "Japan",
            continent = "Asia",
            downloadUrl = "$BASE_URL/asia/japan.map",
            fileSizeBytes = 700_000_000L
        ),
        MapRegion(
            id = "asia/china",
            name = "China",
            continent = "Asia",
            downloadUrl = "$BASE_URL/asia/china.map",
            fileSizeBytes = 1_200_000_000L
        ),
        MapRegion(
            id = "asia/india",
            name = "India",
            continent = "Asia",
            downloadUrl = "$BASE_URL/asia/india.map",
            fileSizeBytes = 800_000_000L
        ),
        MapRegion(
            id = "asia/south-korea",
            name = "South Korea",
            continent = "Asia",
            downloadUrl = "$BASE_URL/asia/south-korea.map",
            fileSizeBytes = 180_000_000L
        ),
        MapRegion(
            id = "asia/thailand",
            name = "Thailand",
            continent = "Asia",
            downloadUrl = "$BASE_URL/asia/thailand.map",
            fileSizeBytes = 200_000_000L
        ),
        MapRegion(
            id = "asia/indonesia",
            name = "Indonesia",
            continent = "Asia",
            downloadUrl = "$BASE_URL/asia/indonesia.map",
            fileSizeBytes = 400_000_000L
        ),

        // ── North America ─────────────────────────────────────────────────────
        MapRegion(
            id = "north-america/us-northeast",
            name = "USA Northeast",
            continent = "North America",
            downloadUrl = "$BASE_URL/north-america/us-northeast.map",
            fileSizeBytes = 350_000_000L
        ),
        MapRegion(
            id = "north-america/us-south",
            name = "USA South",
            continent = "North America",
            downloadUrl = "$BASE_URL/north-america/us-south.map",
            fileSizeBytes = 400_000_000L
        ),
        MapRegion(
            id = "north-america/us-midwest",
            name = "USA Midwest",
            continent = "North America",
            downloadUrl = "$BASE_URL/north-america/us-midwest.map",
            fileSizeBytes = 350_000_000L
        ),
        MapRegion(
            id = "north-america/us-west",
            name = "USA West",
            continent = "North America",
            downloadUrl = "$BASE_URL/north-america/us-west.map",
            fileSizeBytes = 450_000_000L
        ),
        MapRegion(
            id = "north-america/canada",
            name = "Canada",
            continent = "North America",
            downloadUrl = "$BASE_URL/north-america/canada.map",
            fileSizeBytes = 600_000_000L
        ),
        MapRegion(
            id = "north-america/mexico",
            name = "Mexico",
            continent = "North America",
            downloadUrl = "$BASE_URL/north-america/mexico.map",
            fileSizeBytes = 300_000_000L
        ),

        // ── South America ─────────────────────────────────────────────────────
        MapRegion(
            id = "south-america/brazil",
            name = "Brazil",
            continent = "South America",
            downloadUrl = "$BASE_URL/south-america/brazil.map",
            fileSizeBytes = 700_000_000L
        ),
        MapRegion(
            id = "south-america/argentina",
            name = "Argentina",
            continent = "South America",
            downloadUrl = "$BASE_URL/south-america/argentina.map",
            fileSizeBytes = 350_000_000L
        ),

        // ── Africa ────────────────────────────────────────────────────────────
        MapRegion(
            id = "africa/south-africa",
            name = "South Africa",
            continent = "Africa",
            downloadUrl = "$BASE_URL/africa/south-africa.map",
            fileSizeBytes = 250_000_000L
        ),
        MapRegion(
            id = "africa/egypt",
            name = "Egypt",
            continent = "Africa",
            downloadUrl = "$BASE_URL/africa/egypt.map",
            fileSizeBytes = 150_000_000L
        ),

        // ── Australia & Oceania ───────────────────────────────────────────────
        MapRegion(
            id = "australia-oceania/australia",
            name = "Australia",
            continent = "Oceania",
            downloadUrl = "$BASE_URL/australia-oceania/australia.map",
            fileSizeBytes = 500_000_000L
        ),
        MapRegion(
            id = "australia-oceania/new-zealand",
            name = "New Zealand",
            continent = "Oceania",
            downloadUrl = "$BASE_URL/australia-oceania/new-zealand.map",
            fileSizeBytes = 100_000_000L
        ),

        // ── Russia ────────────────────────────────────────────────────────────
        MapRegion(
            id = "russia/russia-european",
            name = "Russia (European)",
            continent = "Russia",
            downloadUrl = "$BASE_URL/russia/russia-european.map",
            fileSizeBytes = 600_000_000L
        ),
        MapRegion(
            id = "russia/russia-asian",
            name = "Russia (Asian)",
            continent = "Russia",
            downloadUrl = "$BASE_URL/russia/russia-asian.map",
            fileSizeBytes = 500_000_000L
        )
    )

    /** Returns all unique continent names in order. */
    val continents: List<String> get() = regions.map { it.continent }.distinct()

    /** Returns regions filtered by continent. */
    fun byContinent(continent: String): List<MapRegion> =
        regions.filter { it.continent == continent }

    /** Finds a region by its ID. */
    fun findById(id: String): MapRegion? = regions.find { it.id == id }

    /** Formats file size to a human-readable string. */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> "%.1f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000L     -> "%.0f MB".format(bytes / 1_000_000.0)
        else                    -> "%.0f KB".format(bytes / 1_000.0)
    }
}
