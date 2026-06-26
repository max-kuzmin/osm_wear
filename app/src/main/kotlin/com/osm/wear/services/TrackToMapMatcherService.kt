package com.osm.wear.services

import android.content.Context
import android.util.Log
import com.osm.wear.models.GpxPoint
import com.osm.wear.models.NavigationWaypoint
import com.osm.wear.models.enums.NavigationMode
import com.osm.wear.models.track_to_map.MatchedTurn
import com.osm.wear.models.track_to_map.RoadEdge
import com.osm.wear.models.track_to_map.RoadNode
import com.osm.wear.models.track_to_map.RoadSegment
import com.osm.wear.repositories.IMapFileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.Tile
import org.mapsforge.map.reader.MapFile
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.*

@Singleton
class TrackToMapMatcherService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mapFileRepository: IMapFileRepository
) : ITrackToMapMatcherService {

    companion object {
        private const val TAG = "TrackToMapMatcherService"
        private const val BUFFER_M = 150.0
        private const val TILE_SIZE = 256
        private const val ZOOM_LEVEL: Byte = 14
        private const val SEARCH_RADIUS_M = 50.0
        private const val CELL_SIZE_DEG = 0.001
        private const val MIN_TURN_ANGLE_DEG = 25.0
        private const val MERGE_TOLERANCE_M = 5.0
    }

    private val cacheDir: File
        get() = File(context.filesDir, "road_cache").also { it.mkdirs() }

    override fun clearCache() {
        cacheDir.listFiles()?.forEach { it.delete() }
        Log.d(TAG, "Road cache cleared")
    }

    override fun matchTrackToMap(
        trackPoints: List<GpxPoint>,
        mode: NavigationMode
    ): List<NavigationWaypoint> {
        if (trackPoints.size < 2) return emptyList()

        val roadSegments = extractRoads(trackPoints, mode)
        if (roadSegments.isEmpty()) return emptyList()

        // Match the track points against extracted road segments
        val matcher = MatchedGraphInstance(roadSegments)
        return matcher.matchTrack(trackPoints)
    }

    // ── Road Network Extraction ──────────────────────────────────────────────

    private fun extractRoads(
        trackPoints: List<GpxPoint>,
        mode: NavigationMode
    ): List<RoadSegment> {
        val mapFilePath = mapFileRepository.getActiveMapFile()
        if (mapFilePath == null || !mapFilePath.exists()) return emptyList()

        val bbox = computeBufferedBBox(trackPoints, BUFFER_M)
        val cacheKey = buildCacheKey(mapFilePath, bbox, mode)
        val cached = loadFromCache(cacheKey)
        if (cached != null) {
            Log.d(TAG, "Loaded ${cached.size} road segments from cache")
            return cached
        }

        val startTime = System.currentTimeMillis()
        val segments = extractFromMapFile(mapFilePath, bbox, mode)
        val elapsed = System.currentTimeMillis() - startTime
        Log.d(TAG, "Extracted ${segments.size} road segments in ${elapsed}ms")

        saveToCache(cacheKey, segments)
        return segments
    }

    private fun allowedHighwayTypes(mode: NavigationMode): Set<String> {
        val common = setOf(
            "motorway", "motorway_link",
            "trunk", "trunk_link",
            "primary", "primary_link",
            "secondary", "secondary_link",
            "tertiary", "tertiary_link",
            "unclassified", "residential",
            "living_street", "service", "road"
        )
        return when (mode) {
            NavigationMode.DRIVING -> common
            NavigationMode.CYCLING -> common + setOf("cycleway", "track", "path")
            NavigationMode.WALKING -> common + setOf("cycleway", "track", "path", "footway", "pedestrian", "steps", "bridleway")
        }
    }

    private fun extractFromMapFile(
        mapFilePath: File,
        bbox: BoundingBox,
        mode: NavigationMode
    ): List<RoadSegment> {
        val allowed = allowedHighwayTypes(mode)
        val mapFile: MapFile
        try {
            mapFile = MapFile(mapFilePath)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to open map file: ${mapFilePath.name}", e)
            return emptyList()
        }

        try {
            val tiles = getTilesForBBox(bbox, ZOOM_LEVEL)
            val deduped = LinkedHashMap<Long, RoadSegment>()

            for (tile in tiles) {
                try {
                    val result = mapFile.readMapData(tile) ?: continue
                    for (way in result.ways) {
                        var highwayType: String? = null
                        var roadName: String? = null
                        var oneway = false

                        for (tag in way.tags) {
                            when (tag.key) {
                                "highway" -> highwayType = tag.value
                                "name" -> roadName = tag.value
                                "oneway" -> oneway = tag.value == "yes" || tag.value == "1"
                            }
                        }

                        if (highwayType == null || highwayType !in allowed) continue

                        val latLongs = way.latLongs ?: continue
                        for (block in latLongs) {
                            if (block == null || block.size < 2) continue
                            val points = block.map { ll -> GpxPoint(ll.latitude, ll.longitude) }
                            val key = computeWayHash(points)
                            if (key !in deduped) {
                                deduped[key] = RoadSegment(
                                    id = key,
                                    points = points,
                                    highwayType = highwayType,
                                    name = roadName,
                                    oneway = oneway
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Error reading tile $tile", e)
                }
            }
            return deduped.values.toList()
        } finally {
            try { mapFile.close() } catch (_: Exception) {}
        }
    }

    private fun getTilesForBBox(bbox: BoundingBox, zoom: Byte): List<Tile> {
        val n = (1 shl zoom.toInt())
        val minTileX = lonToTileX(bbox.minLongitude, zoom)
        val maxTileX = lonToTileX(bbox.maxLongitude, zoom)
        val minTileY = latToTileY(bbox.maxLatitude, zoom)
        val maxTileY = latToTileY(bbox.minLatitude, zoom)

        val tiles = mutableListOf<Tile>()
        for (x in minTileX..maxTileX) {
            for (y in minTileY..maxTileY) {
                if (x in 0 until n && y in 0 until n) {
                    tiles.add(Tile(x, y, zoom, TILE_SIZE))
                }
            }
        }
        return tiles
    }

    private fun lonToTileX(lon: Double, zoom: Byte): Int =
        ((lon + 180.0) / 360.0 * (1 shl zoom.toInt())).toInt()

    private fun latToTileY(lat: Double, zoom: Byte): Int {
        val latRad = Math.toRadians(lat)
        val n = (1 shl zoom.toInt()).toDouble()
        return ((1.0 - ln(tan(latRad) + 1.0 / cos(latRad)) / PI) / 2.0 * n).toInt()
    }

    private fun computeBufferedBBox(points: List<GpxPoint>, bufferM: Double): BoundingBox {
        var minLat = Double.MAX_VALUE
        var maxLat = -Double.MAX_VALUE
        var minLon = Double.MAX_VALUE
        var maxLon = -Double.MAX_VALUE
        for (p in points) {
            if (p.lat < minLat) minLat = p.lat
            if (p.lat > maxLat) maxLat = p.lat
            if (p.lon < minLon) minLon = p.lon
            if (p.lon > maxLon) maxLon = p.lon
        }
        val latBuffer = bufferM / 111132.9
        val lonBuffer = bufferM / (111132.9 * cos(Math.toRadians((minLat + maxLat) / 2.0)))
        return BoundingBox(
            minLat - latBuffer, minLon - lonBuffer,
            maxLat + latBuffer, maxLon + lonBuffer
        )
    }

    private fun computeWayHash(points: List<GpxPoint>): Long {
        var hash = 17L
        val first = points.first()
        val last = points.last()
        hash = hash * 31 + java.lang.Double.doubleToLongBits(first.lat)
        hash = hash * 31 + java.lang.Double.doubleToLongBits(first.lon)
        hash = hash * 31 + java.lang.Double.doubleToLongBits(last.lat)
        hash = hash * 31 + java.lang.Double.doubleToLongBits(last.lon)
        hash = hash * 31 + points.size
        return hash
    }

    private fun buildCacheKey(mapFile: File, bbox: BoundingBox, mode: NavigationMode): String {
        val mapHash = mapFile.name.hashCode()
        val bboxHash = (bbox.minLatitude * 1000).toInt() * 1000000 +
                (bbox.minLongitude * 1000).toInt() * 1000 +
                (bbox.maxLatitude * 1000).toInt() * 100 +
                (bbox.maxLongitude * 1000).toInt()
        return "roads_${mapHash}_${bboxHash}_${mode.name}"
    }

    private fun loadFromCache(key: String): List<RoadSegment>? {
        val file = File(cacheDir, "$key.bin")
        if (!file.exists()) return null
        return try {
            val lines = file.readLines()
            val segments = mutableListOf<RoadSegment>()
            var i = 0
            while (i < lines.size) {
                val header = lines[i].split("\t")
                if (header.size < 5) { i++; continue }
                val id = header[0].toLong()
                val highwayType = header[1]
                val name = if (header[2] == "null") null else header[2]
                val oneway = header[3] == "1"
                val pointCount = header[4].toInt()
                i++
                val points = mutableListOf<GpxPoint>()
                for (j in 0 until pointCount) {
                    if (i >= lines.size) break
                    val parts = lines[i].split(",")
                    if (parts.size >= 2) {
                        points.add(GpxPoint(parts[0].toDouble(), parts[1].toDouble()))
                    }
                    i++
                }
                segments.add(RoadSegment(id, points, highwayType, name, oneway))
            }
            segments
        } catch (e: Exception) {
            Log.w(TAG, "Failed to load road cache: $key", e)
            file.delete()
            null
        }
    }

    private fun saveToCache(key: String, segments: List<RoadSegment>) {
        val file = File(cacheDir, "$key.bin")
        try {
            file.bufferedWriter().use { writer ->
                for (seg in segments) {
                    writer.write("${seg.id}\t${seg.highwayType}\t${seg.name ?: "null"}\t${if (seg.oneway) "1" else "0"}\t${seg.points.size}")
                    writer.newLine()
                    for (p in seg.points) {
                        writer.write("${p.lat},${p.lon}")
                        writer.newLine()
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to save road cache: $key", e)
        }
    }

    // ── Transient MatchedGraphInstance for SNAPPING and TURN DETECTION ───────

    private inner class MatchedGraphInstance(roadSegments: List<RoadSegment>) {
        private val nodes: List<RoadNode>
        private val edges: List<RoadEdge>
        private val edgeIndex = HashMap<Long, MutableList<Int>>()
        private val edgeById: Map<Int, RoadEdge>
        private val nodeById: Map<Int, RoadNode>

        init {
            val (allNodes, allEdges) = buildGraph(roadSegments)
            nodes = allNodes
            edges = allEdges
            edgeById = edges.associateBy { it.id }
            nodeById = nodes.associateBy { it.id }

            for (edge in edges) {
                val cells = mutableSetOf<Long>()
                for (pt in edge.points) {
                    cells.add(cellKey(pt))
                }
                for (key in cells) {
                    edgeIndex.getOrPut(key) { mutableListOf() }.add(edge.id)
                }
            }
        }

        fun matchTrack(trackPoints: List<GpxPoint>): List<NavigationWaypoint> {
            if (trackPoints.size < 2 || edges.isEmpty()) return emptyList()

            val matchedEdges = snapTrackToEdges(trackPoints)
            if (matchedEdges.isEmpty()) return emptyList()

            val edgeSequence = buildEdgeSequence(matchedEdges)
            val turns = detectTurnsAtIntersections(edgeSequence, trackPoints)
            return buildWaypoints(trackPoints, turns)
        }

        private fun snapTrackToEdges(trackPoints: List<GpxPoint>): List<SnapResult?> {
            var lastEdgeId = -1
            return trackPoints.map { pt ->
                val snap = findNearestEdge(pt, lastEdgeId)
                if (snap != null) {
                    lastEdgeId = snap.edgeId
                }
                snap
            }
        }

        private fun findNearestEdge(point: GpxPoint, preferEdgeId: Int): SnapResult? {
            val cx = (point.lon / CELL_SIZE_DEG).toLong()
            val cy = (point.lat / CELL_SIZE_DEG).toLong()
            var bestSnap: SnapResult? = null

            for (dx in -1L..1L) {
                for (dy in -1L..1L) {
                    val key = (cx + dx) * 10_000_000L + (cy + dy)
                    val edgeIds = edgeIndex[key] ?: continue
                    for (edgeId in edgeIds) {
                        val edge = edgeById[edgeId] ?: continue
                        val snap = projectToEdge(point, edge)
                        if (snap != null && snap.distanceM < SEARCH_RADIUS_M) {
                            if (bestSnap == null || snap.distanceM < bestSnap.distanceM) {
                                bestSnap = snap
                            }
                        }
                    }
                }
            }

            if (preferEdgeId >= 0 && bestSnap != null && bestSnap.edgeId != preferEdgeId) {
                val preferEdge = edgeById[preferEdgeId]
                if (preferEdge != null) {
                    val preferSnap = projectToEdge(point, preferEdge)
                    if (preferSnap != null && preferSnap.distanceM < SEARCH_RADIUS_M &&
                        preferSnap.distanceM < bestSnap.distanceM * 1.5) {
                        return preferSnap
                    }
                }
            }
            return bestSnap
        }

        private fun projectToEdge(point: GpxPoint, edge: RoadEdge): SnapResult? {
            var bestDist = Double.MAX_VALUE
            var bestProj = point
            var bestCumFraction = 0.0
            val totalLength = edge.lengthM
            var cumLen = 0.0

            for (i in 0 until edge.points.size - 1) {
                val a = edge.points[i]
                val b = edge.points[i + 1]
                val segLen = fastDistanceM(a, b)
                val proj = projectPointToSegment(point, a, b)
                if (proj.second < bestDist) {
                    bestDist = proj.second
                    bestProj = proj.first
                    bestCumFraction = if (totalLength > 0) (cumLen + segLen * proj.third) / totalLength else 0.0
                }
                cumLen += segLen
            }

            if (bestDist == Double.MAX_VALUE) return null
            return SnapResult(edge.id, bestProj, bestDist, bestCumFraction)
        }

        private fun buildEdgeSequence(snaps: List<SnapResult?>): List<Int> {
            val sequence = mutableListOf<Int>()
            for (snap in snaps) {
                if (snap == null) continue
                if (sequence.isEmpty() || sequence.last() != snap.edgeId) {
                    sequence.add(snap.edgeId)
                }
            }
            return sequence
        }

        private fun detectTurnsAtIntersections(edgeSequence: List<Int>, trackPoints: List<GpxPoint>): List<MatchedTurn> {
            if (edgeSequence.size < 2) return emptyList()
            val turns = mutableListOf<MatchedTurn>()
            var cumulativeDistM = 0.0

            for (i in 0 until edgeSequence.size - 1) {
                val prevEdge = edgeById[edgeSequence[i]] ?: continue
                val nextEdge = edgeById[edgeSequence[i + 1]] ?: continue
                cumulativeDistM += prevEdge.lengthM

                val sharedNodeId = findSharedNode(prevEdge, nextEdge) ?: continue
                val sharedNode = nodeById[sharedNodeId] ?: continue
                if (sharedNode.degree < 3) continue

                val bearingIn = computeApproachBearing(prevEdge, sharedNodeId)
                val bearingOut = computeDepartureBearing(nextEdge, sharedNodeId)
                val bearingChange = normalizeBearing(bearingOut - bearingIn)
                val angleDiff = if (abs(bearingChange) > 180f) 360f - abs(bearingChange) else abs(bearingChange)

                if (angleDiff < MIN_TURN_ANGLE_DEG) continue

                turns.add(
                    MatchedTurn(
                        point = sharedNode.point,
                        bearingChange = bearingChange,
                        roadNameBefore = prevEdge.roadName,
                        roadNameAfter = nextEdge.roadName,
                        distanceFromStartM = cumulativeDistM
                    )
                )
            }
            return turns
        }

        private fun findSharedNode(a: RoadEdge, b: RoadEdge): Int? {
            val aNodes = setOf(a.fromNodeId, a.toNodeId)
            val bNodes = setOf(b.fromNodeId, b.toNodeId)
            return aNodes.intersect(bNodes).firstOrNull()
        }

        private fun computeApproachBearing(edge: RoadEdge, nodeId: Int): Float {
            val pts = if (edge.toNodeId == nodeId) edge.points else edge.points.reversed()
            return computeBearingFromEnd(pts, 30.0)
        }

        private fun computeDepartureBearing(edge: RoadEdge, nodeId: Int): Float {
            val pts = if (edge.fromNodeId == nodeId) edge.points else edge.points.reversed()
            return computeBearingFromStart(pts, 30.0)
        }

        private fun computeBearingFromEnd(pts: List<GpxPoint>, distM: Double): Float {
            if (pts.size < 2) return 0f
            var remaining = distM
            var idx = pts.size - 1
            while (idx > 0 && remaining > 0) {
                remaining -= fastDistanceM(pts[idx - 1], pts[idx])
                idx--
            }
            return bearingDeg(pts[idx], pts.last())
        }

        private fun computeBearingFromStart(pts: List<GpxPoint>, distM: Double): Float {
            if (pts.size < 2) return 0f
            var remaining = distM
            var idx = 0
            while (idx < pts.size - 1 && remaining > 0) {
                remaining -= fastDistanceM(pts[idx], pts[idx + 1])
                idx++
            }
            return bearingDeg(pts.first(), pts[idx])
        }

        private fun buildWaypoints(trackPoints: List<GpxPoint>, turns: List<MatchedTurn>): List<NavigationWaypoint> {
            val waypoints = mutableListOf<NavigationWaypoint>()
            val turnIndices = mutableMapOf<Int, MatchedTurn>()
            for (turn in turns) {
                var bestIdx = 0
                var bestDist = Double.MAX_VALUE
                for (i in trackPoints.indices) {
                    val d = fastDistanceM(trackPoints[i], turn.point)
                    if (d < bestDist) {
                        bestDist = d
                        bestIdx = i
                    }
                }
                if (bestDist < SEARCH_RADIUS_M) {
                    turnIndices[bestIdx] = turn
                }
            }

            for (i in trackPoints.indices) {
                val pt = trackPoints[i]
                val distToNext = if (i < trackPoints.size - 1) fastDistanceM(pt, trackPoints[i + 1]).toFloat() else 0f
                val bearingToNext = if (i < trackPoints.size - 1) bearingDeg(pt, trackPoints[i + 1]) else 0f
                val turn = turnIndices[i]
                val isTurn = turn != null

                waypoints.add(
                    NavigationWaypoint(
                        index = i,
                        rawIndex = i,
                        point = pt,
                        bearingToNext = bearingToNext,
                        distanceToNextM = distToNext,
                        isTurn = isTurn,
                        turnBearingChange = turn?.bearingChange ?: 0f,
                        roadName = turn?.roadNameAfter,
                        isIntersection = isTurn
                    )
                )
            }
            return waypoints
        }

        private fun buildGraph(segments: List<RoadSegment>): Pair<List<RoadNode>, List<RoadEdge>> {
            if (segments.isEmpty()) return Pair(emptyList(), emptyList())
            val nodeMap = NodeMap(MERGE_TOLERANCE_M)
            for (seg in segments) {
                if (seg.points.size < 2) continue
                nodeMap.getOrCreate(seg.points.first())
                nodeMap.getOrCreate(seg.points.last())
            }

            for (seg in segments) {
                if (seg.points.size < 2) continue
                for (i in 1 until seg.points.size - 1) {
                    val existing = nodeMap.findNear(seg.points[i])
                    if (existing != null) {
                        nodeMap.getOrCreate(seg.points[i])
                    }
                }
            }

            val edgesList = mutableListOf<RoadEdge>()
            var edgeId = 0

            for (seg in segments) {
                if (seg.points.size < 2) continue
                var currentStart = 0
                val fromNodeId = nodeMap.getOrCreate(seg.points.first())

                for (i in 1 until seg.points.size) {
                    val nodeId = nodeMap.findNear(seg.points[i])
                    val isEnd = i == seg.points.size - 1

                    if (nodeId != null || isEnd) {
                        val toId = nodeMap.getOrCreate(seg.points[i])
                        val edgePoints = seg.points.subList(currentStart, i + 1)
                        val length = computeLength(edgePoints)

                        if (length > 0.5 && edgePoints.size >= 2) {
                            val edge = RoadEdge(
                                id = edgeId++,
                                fromNodeId = if (currentStart == 0) fromNodeId else nodeMap.getOrCreate(
                                    seg.points[currentStart]
                                ),
                                toNodeId = toId,
                                points = edgePoints.toList(),
                                lengthM = length,
                                roadName = seg.name,
                                highwayType = seg.highwayType
                            )
                            edgesList.add(edge)
                        }
                        currentStart = i
                    }
                }
            }

            val allNodes = nodeMap.allNodes()
            val nodeByIdMap = allNodes.associateBy { it.id }
            for (edge in edgesList) {
                nodeByIdMap[edge.fromNodeId]?.edgeIds?.add(edge.id)
                nodeByIdMap[edge.toNodeId]?.edgeIds?.add(edge.id)
            }
            return Pair(allNodes, edgesList)
        }

        private fun computeLength(points: List<GpxPoint>): Double {
            var total = 0.0
            for (i in 1 until points.size) {
                total += haversineM(points[i - 1], points[i])
            }
            return total
        }

        private fun haversineM(a: GpxPoint, b: GpxPoint): Double {
            val r = 6_371_000.0
            val dLat = Math.toRadians(b.lat - a.lat)
            val dLon = Math.toRadians(b.lon - a.lon)
            val h = sin(dLat / 2).pow(2) +
                    cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).pow(2)
            return r * 2 * atan2(sqrt(h.coerceIn(0.0, 1.0)), sqrt(1.0 - h.coerceIn(0.0, 1.0)))
        }

        private fun projectPointToSegment(
            p: GpxPoint, a: GpxPoint, b: GpxPoint
        ): Triple<GpxPoint, Double, Double> {
            val latMid = Math.toRadians((a.lat + b.lat) / 2.0)
            val mPerLon = 111132.9 * cos(latMid)
            val mPerLat = 111132.9
            val ax = (a.lon - a.lon) * mPerLon
            val ay = (a.lat - a.lat) * mPerLat
            val bx = (b.lon - a.lon) * mPerLon
            val by = (b.lat - a.lat) * mPerLat
            val px = (p.lon - a.lon) * mPerLon
            val py = (p.lat - a.lat) * mPerLat
            val dx = bx
            val dy = by
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-4) 0.0 else ((px * dx + py * dy) / len2).coerceIn(0.0, 1.0)
            val projX = t * dx
            val projY = t * dy
            val dist = sqrt((px - projX).pow(2) + (py - projY).pow(2))
            val projLon = a.lon + projX / mPerLon
            val projLat = a.lat + projY / mPerLat
            return Triple(GpxPoint(projLat, projLon), dist, t)
        }

        private fun fastDistanceM(a: GpxPoint, b: GpxPoint): Double {
            val latMid = Math.toRadians((a.lat + b.lat) / 2.0)
            val dx = Math.toRadians(b.lon - a.lon) * cos(latMid) * 6_371_000.0
            val dy = Math.toRadians(b.lat - a.lat) * 6_371_000.0
            return sqrt(dx * dx + dy * dy)
        }

        private fun bearingDeg(from: GpxPoint, to: GpxPoint): Float {
            val latMid = Math.toRadians((from.lat + to.lat) / 2.0)
            val dx = (to.lon - from.lon) * cos(latMid)
            val dy = to.lat - from.lat
            val rad = atan2(dx, dy)
            return ((Math.toDegrees(rad) + 360.0) % 360.0).toFloat()
        }

        private fun normalizeBearing(b: Float): Float {
            var result = b % 360f
            if (result > 180f) result -= 360f
            if (result < -180f) result += 360f
            return ((result + 360f) % 360f)
        }
    }

    private class NodeMap(private val toleranceM: Double) {
        private var nextId = 0
        private val nodes = mutableListOf<RoadNode>()
        private val cellSizeDeg = toleranceM / 111132.9 * 2.0
        private val grid = HashMap<Long, MutableList<RoadNode>>()

        fun getOrCreate(point: GpxPoint): Int {
            val existing = findNear(point)
            if (existing != null) return existing

            val node = RoadNode(id = nextId++, point = point)
            nodes.add(node)
            val key = cellKey(point)
            grid.getOrPut(key) { mutableListOf() }.add(node)
            return node.id
        }

        fun findNear(point: GpxPoint): Int? {
            val cx = (point.lon / cellSizeDeg).toLong()
            val cy = (point.lat / cellSizeDeg).toLong()
            for (dx in -1L..1L) {
                for (dy in -1L..1L) {
                    val key = (cx + dx) * 1_000_000_000L + (cy + dy)
                    val cell = grid[key] ?: continue
                    for (node in cell) {
                        if (fastDistanceM(point, node.point) < toleranceM) {
                            return node.id
                        }
                    }
                }
            }
            return null
        }

        fun allNodes(): List<RoadNode> = nodes.toList()

        private fun cellKey(p: GpxPoint): Long {
            val cx = (p.lon / cellSizeDeg).toLong()
            val cy = (p.lat / cellSizeDeg).toLong()
            return cx * 1_000_000_000L + cy
        }

        private fun fastDistanceM(a: GpxPoint, b: GpxPoint): Double {
            val latMid = Math.toRadians((a.lat + b.lat) / 2.0)
            val dx = Math.toRadians(b.lon - a.lon) * cos(latMid) * 6_371_000.0
            val dy = Math.toRadians(b.lat - a.lat) * 6_371_000.0
            return sqrt(dx * dx + dy * dy)
        }
    }

    private data class SnapResult(
        val edgeId: Int,
        val projectedPoint: GpxPoint,
        val distanceM: Double,
        val fractionAlongEdge: Double
    )

    private fun cellKey(p: GpxPoint): Long {
        val cx = (p.lon / CELL_SIZE_DEG).toLong()
        val cy = (p.lat / CELL_SIZE_DEG).toLong()
        return cx * 10_000_000L + cy
    }
}
