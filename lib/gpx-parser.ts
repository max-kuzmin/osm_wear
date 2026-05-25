/**
 * GPX file parser for extracting track data
 * Parses XML GPX format and extracts waypoints, tracks, and routes
 */

export interface GPXWaypoint {
  latitude: number;
  longitude: number;
  elevation?: number;
  time?: string;
  name?: string;
}

export interface GPXTrack {
  name?: string;
  description?: string;
  segments: GPXWaypoint[][];
}

export interface GPXRoute {
  name?: string;
  description?: string;
  points: GPXWaypoint[];
}

export interface GPXData {
  waypoints: GPXWaypoint[];
  tracks: GPXTrack[];
  routes: GPXRoute[];
  metadata?: {
    name?: string;
    description?: string;
    author?: string;
    time?: string;
  };
}

/**
 * Parse GPX XML string and extract track data
 */
export function parseGPX(gpxString: string): GPXData {
  const data: GPXData = {
    waypoints: [],
    tracks: [],
    routes: [],
    metadata: {},
  };

  try {
    // Simple XML parsing (note: a full XML parser would be better for production)
    // This is a basic implementation that handles common GPX structures

    // Parse metadata
    const metadataMatch = gpxString.match(/<metadata>([\s\S]*?)<\/metadata>/);
    if (metadataMatch) {
      const metadata = metadataMatch[1];
      const nameMatch = metadata.match(/<name>(.*?)<\/name>/);
      const descMatch = metadata.match(/<desc>(.*?)<\/desc>/);
      const authorMatch = metadata.match(/<author>(.*?)<\/author>/);
      const timeMatch = metadata.match(/<time>(.*?)<\/time>/);

      data.metadata = {
        name: nameMatch ? decodeXML(nameMatch[1]) : undefined,
        description: descMatch ? decodeXML(descMatch[1]) : undefined,
        author: authorMatch ? decodeXML(authorMatch[1]) : undefined,
        time: timeMatch ? timeMatch[1] : undefined,
      };
    }

    // Parse waypoints
    const wpRegex = /<wpt\s+lat="([^"]+)"\s+lon="([^"]+)">([\s\S]*?)<\/wpt>/g;
    let match;
    while ((match = wpRegex.exec(gpxString)) !== null) {
      const waypoint = parseWaypoint(match[1], match[2], match[3]);
      data.waypoints.push(waypoint);
    }

    // Parse tracks
    const trkRegex = /<trk>([\s\S]*?)<\/trk>/g;
    while ((match = trkRegex.exec(gpxString)) !== null) {
      const track = parseTrack(match[1]);
      if (track) {
        data.tracks.push(track);
      }
    }

    // Parse routes
    const rtRegex = /<rte>([\s\S]*?)<\/rte>/g;
    while ((match = rtRegex.exec(gpxString)) !== null) {
      const route = parseRoute(match[1]);
      if (route) {
        data.routes.push(route);
      }
    }
  } catch (error) {
    console.error('Error parsing GPX:', error);
  }

  return data;
}

/**
 * Parse a single waypoint element
 */
function parseWaypoint(lat: string, lon: string, content: string): GPXWaypoint {
  const eleMatch = content.match(/<ele>(.*?)<\/ele>/);
  const nameMatch = content.match(/<name>(.*?)<\/name>/);
  const timeMatch = content.match(/<time>(.*?)<\/time>/);

  return {
    latitude: parseFloat(lat),
    longitude: parseFloat(lon),
    elevation: eleMatch ? parseFloat(eleMatch[1]) : undefined,
    name: nameMatch ? decodeXML(nameMatch[1]) : undefined,
    time: timeMatch ? timeMatch[1] : undefined,
  };
}

/**
 * Parse a track element
 */
function parseTrack(content: string): GPXTrack | null {
  const nameMatch = content.match(/<name>(.*?)<\/name>/);
  const descMatch = content.match(/<desc>(.*?)<\/desc>/);

  const segments: GPXWaypoint[][] = [];
  const trkSegRegex = /<trkseg>([\s\S]*?)<\/trkseg>/g;
  let match;

  while ((match = trkSegRegex.exec(content)) !== null) {
    const segment = parseTrackSegment(match[1]);
    if (segment.length > 0) {
      segments.push(segment);
    }
  }

  if (segments.length === 0) {
    return null;
  }

  return {
    name: nameMatch ? decodeXML(nameMatch[1]) : undefined,
    description: descMatch ? decodeXML(descMatch[1]) : undefined,
    segments,
  };
}

/**
 * Parse a track segment
 */
function parseTrackSegment(content: string): GPXWaypoint[] {
  const points: GPXWaypoint[] = [];
  const trkptRegex = /<trkpt\s+lat="([^"]+)"\s+lon="([^"]+)">([\s\S]*?)<\/trkpt>/g;
  let match;

  while ((match = trkptRegex.exec(content)) !== null) {
    const point = parseWaypoint(match[1], match[2], match[3]);
    points.push(point);
  }

  return points;
}

/**
 * Parse a route element
 */
function parseRoute(content: string): GPXRoute | null {
  const nameMatch = content.match(/<name>(.*?)<\/name>/);
  const descMatch = content.match(/<desc>(.*?)<\/desc>/);

  const points: GPXWaypoint[] = [];
  const rtptRegex = /<rtpt\s+lat="([^"]+)"\s+lon="([^"]+)">([\s\S]*?)<\/rtpt>/g;
  let match;

  while ((match = rtptRegex.exec(content)) !== null) {
    const point = parseWaypoint(match[1], match[2], match[3]);
    points.push(point);
  }

  if (points.length === 0) {
    return null;
  }

  return {
    name: nameMatch ? decodeXML(nameMatch[1]) : undefined,
    description: descMatch ? decodeXML(descMatch[1]) : undefined,
    points,
  };
}

/**
 * Decode XML entities
 */
function decodeXML(str: string): string {
  const entities: { [key: string]: string } = {
    '&amp;': '&',
    '&lt;': '<',
    '&gt;': '>',
    '&quot;': '"',
    '&apos;': "'",
  };

  return str.replace(/&[a-zA-Z]+;/g, (match) => entities[match] || match);
}

/**
 * Calculate total distance of a GPX track
 */
export function calculateGPXDistance(waypoints: GPXWaypoint[]): number {
  if (waypoints.length < 2) return 0;

  let distance = 0;
  for (let i = 1; i < waypoints.length; i++) {
    const lat1 = waypoints[i - 1].latitude;
    const lon1 = waypoints[i - 1].longitude;
    const lat2 = waypoints[i].latitude;
    const lon2 = waypoints[i].longitude;

    const R = 6371; // Earth's radius in km
    const dLat = ((lat2 - lat1) * Math.PI) / 180;
    const dLon = ((lon2 - lon1) * Math.PI) / 180;
    const a =
      Math.sin(dLat / 2) * Math.sin(dLat / 2) +
      Math.cos((lat1 * Math.PI) / 180) * Math.cos((lat2 * Math.PI) / 180) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
    const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
    distance += R * c;
  }

  return distance;
}

/**
 * Get bounds of GPX data
 */
export function getGPXBounds(data: GPXData): {
  north: number;
  south: number;
  east: number;
  west: number;
} | null {
  let allPoints: GPXWaypoint[] = [...data.waypoints];

  data.tracks.forEach((track) => {
    track.segments.forEach((segment) => {
      allPoints = allPoints.concat(segment);
    });
  });

  data.routes.forEach((route) => {
    allPoints = allPoints.concat(route.points);
  });

  if (allPoints.length === 0) {
    return null;
  }

  const lats = allPoints.map((p) => p.latitude);
  const lons = allPoints.map((p) => p.longitude);

  return {
    north: Math.max(...lats),
    south: Math.min(...lats),
    east: Math.max(...lons),
    west: Math.min(...lons),
  };
}
