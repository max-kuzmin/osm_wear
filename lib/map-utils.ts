/**
 * Map utilities for OpenStreetMap tile management
 * Handles tile downloading, caching, and coordinate calculations
 */

import * as FileSystem from 'expo-file-system/legacy';

export interface MapRegion {
  name: string;
  bounds: {
    north: number;
    south: number;
    east: number;
    west: number;
  };
  minZoom: number;
  maxZoom: number;
  tileCount: number;
  sizeBytes: number;
}

export interface TileCoord {
  x: number;
  y: number;
  z: number;
}

export interface GeoCoord {
  latitude: number;
  longitude: number;
}

/**
 * OSM tile server URL
 */
export const OSM_TILE_URL = 'https://tile.openstreetmap.org';

/**
 * Get the cache directory for OSM tiles
 */
export async function getTileCacheDir(): Promise<string> {
  const cacheDir = FileSystem.cacheDirectory + 'osm-tiles/';
  const info = await FileSystem.getInfoAsync(cacheDir);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(cacheDir, { intermediates: true });
  }
  return cacheDir;
}

/**
 * Get the directory for GPX files
 */
export async function getGPXDir(): Promise<string> {
  const gpxDir = FileSystem.documentDirectory + 'gpx-files/';
  const info = await FileSystem.getInfoAsync(gpxDir);
  if (!info.exists) {
    await FileSystem.makeDirectoryAsync(gpxDir, { intermediates: true });
  }
  return gpxDir;
}

/**
 * Convert geographic coordinates to tile coordinates at a given zoom level
 */
export function geoToTile(lat: number, lon: number, zoom: number): TileCoord {
  const n = Math.pow(2, zoom);
  const x = Math.floor(((lon + 180) / 360) * n);
  const y = Math.floor(
    ((1 - Math.log(Math.tan((lat * Math.PI) / 180) + 1 / Math.cos((lat * Math.PI) / 180)) / Math.PI) / 2) * n
  );
  return { x, y, z: zoom };
}

/**
 * Convert tile coordinates to geographic coordinates (northwest corner)
 */
export function tileToGeo(x: number, y: number, zoom: number): GeoCoord {
  const n = Math.pow(2, zoom);
  const lon = (x / n) * 360 - 180;
  const lat = Math.atan(Math.sinh(Math.PI * (1 - (2 * y) / n))) * (180 / Math.PI);
  return { latitude: lat, longitude: lon };
}

/**
 * Get the tile URL for a given coordinate
 */
export function getTileUrl(x: number, y: number, z: number): string {
  return `${OSM_TILE_URL}/${z}/${x}/${y}.png`;
}

/**
 * Get the local cache path for a tile
 */
export async function getTileCachePath(x: number, y: number, z: number): Promise<string> {
  const cacheDir = await getTileCacheDir();
  return `${cacheDir}${z}/${x}/${y}.png`;
}

/**
 * Download and cache a single tile
 */
export async function downloadTile(x: number, y: number, z: number): Promise<string> {
  const tilePath = await getTileCachePath(x, y, z);
  const info = await FileSystem.getInfoAsync(tilePath);

  if (info.exists) {
    return tilePath;
  }

  const url = getTileUrl(x, y, z);
  const tileDir = `${await getTileCacheDir()}${z}/${x}/`;
  const dirInfo = await FileSystem.getInfoAsync(tileDir);
  if (!dirInfo.exists) {
    await FileSystem.makeDirectoryAsync(tileDir, { intermediates: true });
  }

  try {
    await FileSystem.downloadAsync(url, tilePath);
    return tilePath;
  } catch (error) {
    console.error(`Failed to download tile ${z}/${x}/${y}:`, error);
    throw error;
  }
}

/**
 * Get tiles for a given region and zoom level
 */
export function getTilesForRegion(
  north: number,
  south: number,
  east: number,
  west: number,
  zoom: number
): TileCoord[] {
  const tiles: TileCoord[] = [];
  const nwTile = geoToTile(north, west, zoom);
  const seTile = geoToTile(south, east, zoom);

  for (let x = nwTile.x; x <= seTile.x; x++) {
    for (let y = nwTile.y; y <= seTile.y; y++) {
      tiles.push({ x, y, z: zoom });
    }
  }

  return tiles;
}

/**
 * Calculate the distance between two geographic points (Haversine formula)
 * Returns distance in kilometers
 */
export function calculateDistance(lat1: number, lon1: number, lat2: number, lon2: number): number {
  const R = 6371; // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}

/**
 * Predefined map regions for download
 */
export const PREDEFINED_REGIONS: MapRegion[] = [
  {
    name: 'Europe - Central',
    bounds: {
      north: 55,
      south: 43,
      east: 25,
      west: -5,
    },
    minZoom: 0,
    maxZoom: 16,
    tileCount: 0,
    sizeBytes: 0,
  },
  {
    name: 'USA - West Coast',
    bounds: {
      north: 49,
      south: 32,
      east: -114,
      west: -125,
    },
    minZoom: 0,
    maxZoom: 16,
    tileCount: 0,
    sizeBytes: 0,
  },
  {
    name: 'Asia - Southeast',
    bounds: {
      north: 20,
      south: -10,
      east: 140,
      west: 95,
    },
    minZoom: 0,
    maxZoom: 16,
    tileCount: 0,
    sizeBytes: 0,
  },
];
