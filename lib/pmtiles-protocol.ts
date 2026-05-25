/**
 * PMTiles Protocol Initialization
 * 
 * Sets up the pmtiles:// protocol for MapLibre GL
 * Must be called once at app startup
 */

import maplibregl from 'maplibre-gl';
import { Protocol } from 'pmtiles';

let protocol: Protocol | null = null;

/**
 * Initialize PMTiles protocol for MapLibre GL
 * Call this once in your app's root component
 */
export function initializePMTilesProtocol(): void {
  if (protocol) {
    console.log('PMTiles protocol already initialized');
    return;
  }

  try {
    protocol = new Protocol();
    maplibregl.addProtocol('pmtiles', protocol.tile);
    console.log('PMTiles protocol initialized successfully');
  } catch (error) {
    console.error('Failed to initialize PMTiles protocol:', error);
  }
}

/**
 * Remove PMTiles protocol (cleanup)
 */
export function removePMTilesProtocol(): void {
  if (protocol) {
    try {
      maplibregl.removeProtocol('pmtiles');
      protocol = null;
      console.log('PMTiles protocol removed');
    } catch (error) {
      console.error('Failed to remove PMTiles protocol:', error);
    }
  }
}

/**
 * Check if PMTiles protocol is initialized
 */
export function isPMTilesProtocolInitialized(): boolean {
  return protocol !== null;
}
