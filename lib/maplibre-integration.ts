/**
 * MapLibre GL Integration for PMTiles
 * 
 * This module provides utilities for rendering PMTiles offline maps
 * using MapLibre GL in React Native
 */

import { getRegionById, ATTRIBUTION_TEXT, type MapRegion } from './protomaps-regions';
import { getRegionFilePath, isRegionDownloaded } from './download-manager';

/**
 * MapLibre GL source configuration for PMTiles
 */
export interface PMTilesSource {
  type: 'vector' | 'raster' | 'raster-dem';
  url: string;
  minzoom?: number;
  maxzoom?: number;
}

/**
 * Layer configuration for MapLibre GL
 */
export interface MapLayer {
  id: string;
  type: 'line' | 'fill' | 'symbol' | 'circle' | 'raster' | 'background';
  source: string;
  'source-layer'?: string;
  paint?: Record<string, any>;
  layout?: Record<string, any>;
}

/**
 * Create a PMTiles source URL for local file
 * 
 * @param regionId - Region ID
 * @returns PMTiles URL for local file or null if not downloaded
 */
export async function createPMTilesSourceUrl(regionId: string): Promise<string | null> {
  const isDownloaded = await isRegionDownloaded(regionId);
  if (!isDownloaded) {
    return null;
  }

  const filePath = getRegionFilePath(regionId);
  // Use file:// protocol for local files
  return `pmtiles://file://${filePath}`;
}

/**
 * Create a MapLibre GL source configuration for PMTiles
 */
export function createPMTilesSource(url: string, region?: MapRegion): PMTilesSource {
  return {
    type: 'vector',
    url,
    minzoom: 0,
    maxzoom: region?.maxZoom || 14,
  };
}

/**
 * Default vector tile layers for Protomaps basemap
 * 
 * These layers render the standard Protomaps vector tile schema
 */
export const DEFAULT_PROTOMAPS_LAYERS: MapLayer[] = [
  // Water
  {
    id: 'water',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'water',
    paint: {
      'fill-color': '#a0c8f0',
    },
  },
  // Land
  {
    id: 'land',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'land',
    paint: {
      'fill-color': '#f3f3f3',
    },
  },
  // Landuse
  {
    id: 'landuse',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'landuse',
    paint: {
      'fill-color': '#e8e8e8',
      'fill-opacity': 0.5,
    },
  },
  // Roads
  {
    id: 'roads-minor',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'roads',
    layout: {
      'line-join': 'round',
      'line-cap': 'round',
    },
    paint: {
      'line-color': '#ffffff',
      'line-width': 1,
    },
  },
  {
    id: 'roads-major',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'roads',
    layout: {
      'line-join': 'round',
      'line-cap': 'round',
    },
    paint: {
      'line-color': '#ffcc00',
      'line-width': 2,
    },
  },
  // Boundaries
  {
    id: 'boundaries',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'boundaries',
    paint: {
      'line-color': '#cccccc',
      'line-width': 1,
      'line-dasharray': [4, 2],
    },
  },
  // POI labels
  {
    id: 'poi-labels',
    type: 'symbol',
    source: 'pmtiles',
    'source-layer': 'poi',
    layout: {
      'text-field': ['get', 'name'],
      'text-size': 10,
      'text-offset': [0, 1.5],
      'text-anchor': 'top',
    },
    paint: {
      'text-color': '#333333',
      'text-halo-color': '#ffffff',
      'text-halo-width': 1,
    },
  },
  // Place labels
  {
    id: 'place-labels',
    type: 'symbol',
    source: 'pmtiles',
    'source-layer': 'places',
    layout: {
      'text-field': ['get', 'name'],
      'text-size': 12,
      'text-anchor': 'center',
    },
    paint: {
      'text-color': '#000000',
      'text-halo-color': '#ffffff',
      'text-halo-width': 1,
    },
  },
];

/**
 * Simplified layers for low-bandwidth/low-power rendering
 * Suitable for smartwatch displays
 */
export const SIMPLIFIED_PROTOMAPS_LAYERS: MapLayer[] = [
  // Water
  {
    id: 'water',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'water',
    paint: {
      'fill-color': '#a0c8f0',
    },
  },
  // Land
  {
    id: 'land',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'land',
    paint: {
      'fill-color': '#f3f3f3',
    },
  },
  // Roads
  {
    id: 'roads',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'roads',
    layout: {
      'line-join': 'round',
      'line-cap': 'round',
    },
    paint: {
      'line-color': '#ffffff',
      'line-width': 1.5,
    },
  },
  // Place labels (minimal)
  {
    id: 'place-labels',
    type: 'symbol',
    source: 'pmtiles',
    'source-layer': 'places',
    layout: {
      'text-field': ['get', 'name'],
      'text-size': 10,
      'text-anchor': 'center',
    },
    paint: {
      'text-color': '#000000',
      'text-halo-color': '#ffffff',
      'text-halo-width': 1,
    },
  },
];

/**
 * Dark mode layers for Protomaps
 */
export const DARK_MODE_PROTOMAPS_LAYERS: MapLayer[] = [
  // Water
  {
    id: 'water',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'water',
    paint: {
      'fill-color': '#1a1a2e',
    },
  },
  // Land
  {
    id: 'land',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'land',
    paint: {
      'fill-color': '#16213e',
    },
  },
  // Landuse
  {
    id: 'landuse',
    type: 'fill',
    source: 'pmtiles',
    'source-layer': 'landuse',
    paint: {
      'fill-color': '#0f3460',
      'fill-opacity': 0.5,
    },
  },
  // Roads
  {
    id: 'roads-minor',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'roads',
    layout: {
      'line-join': 'round',
      'line-cap': 'round',
    },
    paint: {
      'line-color': '#cccccc',
      'line-width': 1,
    },
  },
  {
    id: 'roads-major',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'roads',
    layout: {
      'line-join': 'round',
      'line-cap': 'round',
    },
    paint: {
      'line-color': '#ffcc00',
      'line-width': 2,
    },
  },
  // Boundaries
  {
    id: 'boundaries',
    type: 'line',
    source: 'pmtiles',
    'source-layer': 'boundaries',
    paint: {
      'line-color': '#444444',
      'line-width': 1,
      'line-dasharray': [4, 2],
    },
  },
  // Place labels
  {
    id: 'place-labels',
    type: 'symbol',
    source: 'pmtiles',
    'source-layer': 'places',
    layout: {
      'text-field': ['get', 'name'],
      'text-size': 12,
      'text-anchor': 'center',
    },
    paint: {
      'text-color': '#ffffff',
      'text-halo-color': '#000000',
      'text-halo-width': 1,
    },
  },
];

/**
 * Get appropriate layers based on theme
 */
export function getLayersForTheme(isDarkMode: boolean, simplified: boolean = false): MapLayer[] {
  if (isDarkMode) {
    return DARK_MODE_PROTOMAPS_LAYERS;
  }
  if (simplified) {
    return SIMPLIFIED_PROTOMAPS_LAYERS;
  }
  return DEFAULT_PROTOMAPS_LAYERS;
}

/**
 * Map style for Protomaps with PMTiles
 */
export interface MapStyle {
  version: 8;
  name: string;
  metadata?: Record<string, any>;
  sources: Record<string, any>;
  layers: MapLayer[];
  glyphs?: string;
  sprite?: string;
}

/**
 * Create a complete MapLibre GL style for PMTiles
 */
export function createPMTilesStyle(
  sourceUrl: string,
  isDarkMode: boolean = false,
  simplified: boolean = false
): MapStyle {
  return {
    version: 8,
    name: 'Protomaps',
    sources: {
      pmtiles: createPMTilesSource(sourceUrl),
    },
    layers: getLayersForTheme(isDarkMode, simplified),
  };
}

/**
 * Map view configuration
 */
export interface MapViewConfig {
  centerCoordinate: [number, number]; // [longitude, latitude]
  zoomLevel: number;
  minZoomLevel: number;
  maxZoomLevel: number;
  rotateEnabled: boolean;
  scrollEnabled: boolean;
  zoomEnabled: boolean;
  pitchEnabled: boolean;
}

/**
 * Default map view config for Wear OS
 */
export const DEFAULT_MAP_VIEW_CONFIG: MapViewConfig = {
  centerCoordinate: [0, 0], // Will be set to user location
  zoomLevel: 12,
  minZoomLevel: 0,
  maxZoomLevel: 14,
  rotateEnabled: false,
  scrollEnabled: true,
  zoomEnabled: true,
  pitchEnabled: false,
};

/**
 * Get current location bounds
 */
export function getLocationBounds(
  latitude: number,
  longitude: number,
  radiusKm: number = 10
): {
  north: number;
  south: number;
  east: number;
  west: number;
} {
  // Rough approximation: 1 degree ≈ 111 km
  const latDelta = radiusKm / 111;
  const lonDelta = radiusKm / (111 * Math.cos((latitude * Math.PI) / 180));

  return {
    north: latitude + latDelta,
    south: latitude - latDelta,
    east: longitude + lonDelta,
    west: longitude - lonDelta,
  };
}

/**
 * Format coordinates for display
 */
export function formatCoordinates(latitude: number, longitude: number): string {
  const latDir = latitude >= 0 ? 'N' : 'S';
  const lonDir = longitude >= 0 ? 'E' : 'W';
  const latAbs = Math.abs(latitude).toFixed(4);
  const lonAbs = Math.abs(longitude).toFixed(4);
  return `${latAbs}° ${latDir}, ${lonAbs}° ${lonDir}`;
}

/**
 * Calculate distance between two points in kilometers
 */
export function calculateDistance(
  lat1: number,
  lon1: number,
  lat2: number,
  lon2: number
): number {
  const R = 6371; // Earth's radius in km
  const dLat = ((lat2 - lat1) * Math.PI) / 180;
  const dLon = ((lon2 - lon1) * Math.PI) / 180;
  const a =
    Math.sin(dLat / 2) * Math.sin(dLat / 2) +
    Math.cos((lat1 * Math.PI) / 180) *
      Math.cos((lat2 * Math.PI) / 180) *
      Math.sin(dLon / 2) *
      Math.sin(dLon / 2);
  const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
  return R * c;
}
