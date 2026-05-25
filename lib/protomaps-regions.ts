/**
 * Protomaps Regions Configuration
 * 
 * This module defines downloadable map regions using Protomaps PMTiles format.
 * PMTiles is a single-file archive format for tiled data, perfect for offline mobile apps.
 * 
 * Data Source: https://maps.protomaps.com/builds/
 * License: ODbL (requires attribution to OpenStreetMap)
 * Format: PMTiles (vector tiles)
 */

export interface MapRegion {
  id: string;
  name: string;
  description: string;
  bounds: {
    north: number;
    south: number;
    east: number;
    west: number;
  };
  /** Protomaps download URL - these are examples, check maps.protomaps.com/builds for current URLs */
  pmtilesUrl: string;
  /** Estimated file size in MB */
  estimatedSize: number;
  /** Maximum zoom level included in the PMTiles file */
  maxZoom: number;
  /** Approximate download time in minutes on 5G/WiFi */
  estimatedDownloadTime: number;
}

/**
 * Predefined downloadable regions using Protomaps PMTiles
 * 
 * Note: URLs should be updated regularly from https://maps.protomaps.com/builds/
 * These are example URLs - use the latest builds from Protomaps
 */
export const PROTOMAPS_REGIONS: MapRegion[] = [
  {
    id: 'north-america',
    name: 'North America',
    description: 'USA, Canada, Mexico - Perfect for outdoor activities',
    bounds: {
      north: 72,
      south: 15,
      east: -52,
      west: -170,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/north-america.pmtiles',
    estimatedSize: 2500,
    maxZoom: 14,
    estimatedDownloadTime: 15,
  },
  {
    id: 'south-america',
    name: 'South America',
    description: 'All South American countries',
    bounds: {
      north: 13,
      south: -56,
      east: -34,
      west: -82,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/south-america.pmtiles',
    estimatedSize: 1200,
    maxZoom: 14,
    estimatedDownloadTime: 8,
  },
  {
    id: 'europe',
    name: 'Europe',
    description: 'All European countries including Russia (European part)',
    bounds: {
      north: 71,
      south: 35,
      east: 40,
      west: -10,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/europe.pmtiles',
    estimatedSize: 1800,
    maxZoom: 14,
    estimatedDownloadTime: 12,
  },
  {
    id: 'africa',
    name: 'Africa',
    description: 'All African countries',
    bounds: {
      north: 37,
      south: -35,
      east: 55,
      west: -18,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/africa.pmtiles',
    estimatedSize: 1600,
    maxZoom: 14,
    estimatedDownloadTime: 10,
  },
  {
    id: 'asia',
    name: 'Asia',
    description: 'Asia including Middle East and Central Asia',
    bounds: {
      north: 77,
      south: -10,
      east: 150,
      west: 26,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/asia.pmtiles',
    estimatedSize: 2200,
    maxZoom: 14,
    estimatedDownloadTime: 14,
  },
  {
    id: 'oceania',
    name: 'Oceania',
    description: 'Australia, New Zealand, Pacific Islands',
    bounds: {
      north: 0,
      south: -47,
      east: 180,
      west: 113,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/oceania.pmtiles',
    estimatedSize: 800,
    maxZoom: 14,
    estimatedDownloadTime: 5,
  },
];

/**
 * Smaller regional extracts for limited storage (smartwatch)
 * These are more manageable sizes for Wear OS devices
 */
export const PROTOMAPS_SUBREGIONS: MapRegion[] = [
  // Europe Sub-regions
  {
    id: 'western-europe',
    name: 'Western Europe',
    description: 'UK, France, Germany, Benelux, Spain, Portugal',
    bounds: {
      north: 56,
      south: 36,
      east: 15,
      west: -10,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/western-europe.pmtiles',
    estimatedSize: 600,
    maxZoom: 14,
    estimatedDownloadTime: 4,
  },
  {
    id: 'central-europe',
    name: 'Central Europe',
    description: 'Poland, Czech Republic, Slovakia, Hungary, Austria',
    bounds: {
      north: 55,
      south: 45,
      east: 27,
      west: 12,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/central-europe.pmtiles',
    estimatedSize: 400,
    maxZoom: 14,
    estimatedDownloadTime: 3,
  },
  {
    id: 'southern-europe',
    name: 'Southern Europe',
    description: 'Italy, Greece, Croatia, Turkey',
    bounds: {
      north: 47,
      south: 35,
      east: 45,
      west: 12,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/southern-europe.pmtiles',
    estimatedSize: 500,
    maxZoom: 14,
    estimatedDownloadTime: 3,
  },
  {
    id: 'scandinavian',
    name: 'Scandinavia',
    description: 'Sweden, Norway, Finland, Denmark',
    bounds: {
      north: 71,
      south: 54,
      east: 32,
      west: 4,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/scandinavian.pmtiles',
    estimatedSize: 350,
    maxZoom: 14,
    estimatedDownloadTime: 2,
  },

  // North America Sub-regions
  {
    id: 'usa-east',
    name: 'USA - Eastern',
    description: 'Eastern United States',
    bounds: {
      north: 49,
      south: 25,
      east: -67,
      west: -100,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/usa-east.pmtiles',
    estimatedSize: 800,
    maxZoom: 14,
    estimatedDownloadTime: 5,
  },
  {
    id: 'usa-west',
    name: 'USA - Western',
    description: 'Western United States',
    bounds: {
      north: 49,
      south: 25,
      east: -100,
      west: -125,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/usa-west.pmtiles',
    estimatedSize: 700,
    maxZoom: 14,
    estimatedDownloadTime: 5,
  },
  {
    id: 'canada',
    name: 'Canada',
    description: 'All of Canada',
    bounds: {
      north: 72,
      south: 42,
      east: -52,
      west: -141,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/canada.pmtiles',
    estimatedSize: 600,
    maxZoom: 14,
    estimatedDownloadTime: 4,
  },

  // Asia Sub-regions
  {
    id: 'southeast-asia',
    name: 'Southeast Asia',
    description: 'Thailand, Vietnam, Cambodia, Laos, Myanmar, Malaysia, Indonesia',
    bounds: {
      north: 20,
      south: -10,
      east: 141,
      west: 92,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/southeast-asia.pmtiles',
    estimatedSize: 500,
    maxZoom: 14,
    estimatedDownloadTime: 3,
  },
  {
    id: 'east-asia',
    name: 'East Asia',
    description: 'China, Japan, Korea, Taiwan',
    bounds: {
      north: 54,
      south: 18,
      east: 145,
      west: 73,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/east-asia.pmtiles',
    estimatedSize: 700,
    maxZoom: 14,
    estimatedDownloadTime: 5,
  },
  {
    id: 'south-asia',
    name: 'South Asia',
    description: 'India, Pakistan, Bangladesh, Nepal, Sri Lanka',
    bounds: {
      north: 37,
      south: 6,
      east: 97,
      west: 68,
    },
    pmtilesUrl: 'https://maps.protomaps.com/builds/v4/south-asia.pmtiles',
    estimatedSize: 600,
    maxZoom: 14,
    estimatedDownloadTime: 4,
  },
];

/**
 * Get all available regions (continents + subregions)
 */
export function getAllRegions(): MapRegion[] {
  return [...PROTOMAPS_REGIONS, ...PROTOMAPS_SUBREGIONS];
}

/**
 * Get region by ID
 */
export function getRegionById(id: string): MapRegion | undefined {
  return getAllRegions().find(region => region.id === id);
}

/**
 * Get regions by continent
 */
export function getRegionsByContinent(continent: string): MapRegion[] {
  const continentMap: Record<string, string[]> = {
    'north-america': ['north-america', 'usa-east', 'usa-west', 'canada'],
    'south-america': ['south-america'],
    'europe': ['europe', 'western-europe', 'central-europe', 'southern-europe', 'scandinavian'],
    'africa': ['africa'],
    'asia': ['asia', 'southeast-asia', 'east-asia', 'south-asia'],
    'oceania': ['oceania'],
  };

  const regionIds = continentMap[continent] || [];
  return getAllRegions().filter(r => regionIds.includes(r.id));
}

/**
 * Calculate total download size for multiple regions
 */
export function calculateTotalSize(regionIds: string[]): number {
  return regionIds.reduce((total, id) => {
    const region = getRegionById(id);
    return total + (region?.estimatedSize || 0);
  }, 0);
}

/**
 * Get regions within a bounding box
 */
export function getRegionsInBounds(
  north: number,
  south: number,
  east: number,
  west: number
): MapRegion[] {
  return getAllRegions().filter(region => {
    // Check if region bounds overlap with requested bounds
    return !(
      region.bounds.south > north ||
      region.bounds.north < south ||
      region.bounds.west > east ||
      region.bounds.east < west
    );
  });
}

/**
 * Attribution text required for OSM/Protomaps usage
 */
export const ATTRIBUTION_TEXT =
  '© OpenStreetMap contributors | Maps by Protomaps';

/**
 * License information
 */
export const LICENSE_INFO = {
  name: 'Open Data Commons Open Database License (ODbL)',
  url: 'https://opendatacommons.org/licenses/odbl/',
  attribution: 'Map data © OpenStreetMap contributors',
  tiles: 'Tiles © Protomaps',
};
