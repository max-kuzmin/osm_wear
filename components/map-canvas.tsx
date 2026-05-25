/**
 * MapCanvas component for rendering OpenStreetMap tiles
 * Handles tile rendering, panning, zooming, and overlays
 */

import React, { useEffect, useRef, useState } from 'react';
import { View, StyleSheet, Dimensions, PanResponder, GestureResponderEvent, PanResponderGestureState } from 'react-native';
import { Canvas, Image as SkiaImage, useImage } from '@shopify/react-native-skia';
import { useMap } from '@/lib/map-context';
import { geoToTile, getTileCachePath, getTileUrl } from '@/lib/map-utils';
import { GPXData } from '@/lib/gpx-parser';

interface MapCanvasProps {
  gpxData?: GPXData;
}

const TILE_SIZE = 256;
const MIN_ZOOM = 0;
const MAX_ZOOM = 18;

export function MapCanvas({ gpxData }: MapCanvasProps) {
  const { state, dispatch } = useMap();
  const [tiles, setTiles] = useState<Array<{ x: number; y: number; z: number; uri: string }>>([]);
  const [panOffset, setPanOffset] = useState({ x: 0, y: 0 });
  const canvasRef = useRef(null);

  const screenWidth = Dimensions.get('window').width;
  const screenHeight = Dimensions.get('window').height;

  // Calculate which tiles to render based on current map region
  useEffect(() => {
    const { latitude, longitude } = state.mapRegion;
    const zoom = Math.floor(state.zoomLevel);

    // Get center tile
    const centerTile = geoToTile(latitude, longitude, zoom);

    // Calculate visible tiles (simplified - just get center and neighbors)
    const visibleTiles = [];
    const tileRange = 2; // Show 2x2 grid of tiles

    for (let dx = -tileRange; dx <= tileRange; dx++) {
      for (let dy = -tileRange; dy <= tileRange; dy++) {
        visibleTiles.push({
          x: centerTile.x + dx,
          y: centerTile.y + dy,
          z: zoom,
          uri: getTileUrl(centerTile.x + dx, centerTile.y + dy, zoom),
        });
      }
    }

    setTiles(visibleTiles);
  }, [state.mapRegion, state.zoomLevel]);

  // Pan responder for map dragging
  const panResponder = useRef(
    PanResponder.create({
      onStartShouldSetPanResponder: () => true,
      onMoveShouldSetPanResponder: () => true,
      onPanResponderMove: (evt: GestureResponderEvent, gestureState: PanResponderGestureState) => {
        setPanOffset({
          x: gestureState.dx,
          y: gestureState.dy,
        });
      },
      onPanResponderRelease: (evt: GestureResponderEvent, gestureState: PanResponderGestureState) => {
        // Update map region based on pan
        const { latitude, longitude, latitudeDelta, longitudeDelta } = state.mapRegion;
        const newLatitude = latitude - (gestureState.dy / screenHeight) * latitudeDelta;
        const newLongitude = longitude - (gestureState.dx / screenWidth) * longitudeDelta;

        dispatch({
          type: 'SET_MAP_REGION',
          payload: {
            latitude: newLatitude,
            longitude: newLongitude,
            latitudeDelta,
            longitudeDelta,
          },
        });

        setPanOffset({ x: 0, y: 0 });
      },
    })
  ).current;

  return (
    <View style={styles.container} {...panResponder.panHandlers}>
      <Canvas style={styles.canvas} ref={canvasRef}>
        {/* Render background */}
        <View style={[styles.tileGrid, { transform: [{ translateX: panOffset.x }, { translateY: panOffset.y }] }]}>
          {tiles.map((tile, idx) => (
            <MapTile key={`${tile.x}-${tile.y}-${tile.z}`} tile={tile} />
          ))}
        </View>

        {/* Render current location indicator */}
        {state.currentLocation && (
          <View style={[styles.locationIndicator, { left: screenWidth / 2 - 8, top: screenHeight / 2 - 8 }]} />
        )}

        {/* Render GPX track overlay */}
        {gpxData && <GPXOverlay gpxData={gpxData} mapRegion={state.mapRegion} screenWidth={screenWidth} screenHeight={screenHeight} />}
      </Canvas>
    </View>
  );
}

interface MapTileProps {
  tile: { x: number; y: number; z: number; uri: string };
}

function MapTile({ tile }: MapTileProps) {
  const tileImage = useImage(tile.uri);

  if (!tileImage) {
    return <View style={[styles.tile, { backgroundColor: '#f0f0f0' }]} />;
  }

  return (
    <View style={styles.tile}>
      <SkiaImage image={tileImage} x={0} y={0} width={TILE_SIZE} height={TILE_SIZE} />
    </View>
  );
}

interface GPXOverlayProps {
  gpxData: GPXData;
  mapRegion: any;
  screenWidth: number;
  screenHeight: number;
}

function GPXOverlay({ gpxData, mapRegion, screenWidth, screenHeight }: GPXOverlayProps) {
  // Render GPX tracks as polylines
  return (
    <View style={styles.gpxOverlay}>
      {gpxData.tracks.map((track, trackIdx) =>
        track.segments.map((segment, segIdx) => (
          <View key={`track-${trackIdx}-seg-${segIdx}`} style={styles.trackSegment}>
            {/* Render polyline for segment */}
            {segment.length > 1 &&
              segment.map((point, idx) => {
                if (idx === 0) return null;
                const prevPoint = segment[idx - 1];

                // Convert geo coords to screen coords (simplified)
                const x1 = ((prevPoint.longitude - mapRegion.longitude) / mapRegion.longitudeDelta) * screenWidth + screenWidth / 2;
                const y1 = ((mapRegion.latitude - prevPoint.latitude) / mapRegion.latitudeDelta) * screenHeight + screenHeight / 2;
                const x2 = ((point.longitude - mapRegion.longitude) / mapRegion.longitudeDelta) * screenWidth + screenWidth / 2;
                const y2 = ((mapRegion.latitude - point.latitude) / mapRegion.latitudeDelta) * screenHeight + screenHeight / 2;

                return (
                  <View
                    key={`point-${idx}`}
                    style={[
                      styles.trackLine,
                      {
                        left: Math.min(x1, x2),
                        top: Math.min(y1, y2),
                        width: Math.abs(x2 - x1),
                        height: Math.abs(y2 - y1),
                      },
                    ]}
                  />
                );
              })}
          </View>
        ))
      )}
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#f5f5f5',
  },
  canvas: {
    flex: 1,
  },
  tileGrid: {
    flex: 1,
    flexDirection: 'row',
    flexWrap: 'wrap',
  },
  tile: {
    width: TILE_SIZE,
    height: TILE_SIZE,
    backgroundColor: '#f0f0f0',
  },
  locationIndicator: {
    width: 16,
    height: 16,
    borderRadius: 8,
    backgroundColor: '#0A7EA4',
    borderWidth: 2,
    borderColor: '#ffffff',
    position: 'absolute',
  },
  gpxOverlay: {
    ...StyleSheet.absoluteFillObject,
  },
  trackSegment: {
    ...StyleSheet.absoluteFillObject,
  },
  trackLine: {
    position: 'absolute',
    backgroundColor: '#22C55E',
    height: 2,
  },
});
