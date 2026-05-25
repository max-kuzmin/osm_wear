/**
 * MapLibre GL Map Component
 * 
 * Renders PMTiles offline maps with zoom, pan, and location tracking
 */

import React, { useEffect, useRef, useState, useCallback } from 'react';
import { View, ActivityIndicator, Text, Pressable, Alert } from 'react-native';
import * as MapLibreGL from '@maplibre/maplibre-react-native';
const MapView = (MapLibreGL as any).MapView;
import { useMapContext } from '@/lib/map-context';
import { useColors } from '@/hooks/use-colors';
import { cn } from '@/lib/utils';
import {
  createPMTilesSourceUrl,
  createPMTilesStyle,
  formatCoordinates,
  SIMPLIFIED_PROTOMAPS_LAYERS,
  DARK_MODE_PROTOMAPS_LAYERS,
} from '@/lib/maplibre-integration';
import { isRegionDownloaded } from '@/lib/download-manager';
import { parseGPX, type GPXTrack } from '@/lib/gpx-parser';

interface MapLibreMapProps {
  className?: string;
  showAttribution?: boolean;
  isDarkMode?: boolean;
  simplified?: boolean;
  gpxTracks?: GPXTrack[];
}

export function MapLibreMap({
  className,
  showAttribution = true,
  isDarkMode = false,
  simplified = true,
  gpxTracks = [],
}: MapLibreMapProps) {
  const colors = useColors();
  const { mapState, currentLocation, updateMapState } = useMapContext();
  const [isLoading, setIsLoading] = useState(true);
  const [sourceUrl, setSourceUrl] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [zoom, setZoom] = useState(mapState.zoomLevel);
  const mapRef = useRef<any>(null);

  // Initialize PMTiles source URL
  useEffect(() => {
    const initializeSource = async () => {
      try {
        setIsLoading(true);
        setError(null);

        if (!mapState.selectedRegionId) {
          setError('No region selected. Please download a map region first.');
          setIsLoading(false);
          return;
        }

        // Check if region is downloaded
        const isDownloaded = await isRegionDownloaded(mapState.selectedRegionId);
        if (!isDownloaded) {
          setError('Region not downloaded. Please download the region first.');
          setIsLoading(false);
          return;
        }

        // Create PMTiles source URL
        const url = await createPMTilesSourceUrl(mapState.selectedRegionId);
        if (!url) {
          setError('Failed to create source URL');
          setIsLoading(false);
          return;
        }

        setSourceUrl(url);
        setIsLoading(false);
      } catch (err) {
        const errorMessage = err instanceof Error ? err.message : 'Unknown error';
        setError(errorMessage);
        setIsLoading(false);
      }
    };

    initializeSource();
  }, [mapState.selectedRegionId]);

  // Handle zoom in
  const handleZoomIn = useCallback(async () => {
    if (mapRef.current) {
      const currentZoom = await mapRef.current.getZoom();
      await mapRef.current.zoomTo(Math.min(currentZoom + 1, 18), 200);
    }
  }, []);

  // Handle zoom out
  const handleZoomOut = useCallback(async () => {
    if (mapRef.current) {
      const currentZoom = await mapRef.current.getZoom();
      await mapRef.current.zoomTo(Math.max(currentZoom - 1, 0), 200);
    }
  }, []);

  // Handle recenter on location
  const handleRecenterLocation = useCallback(async () => {
    if (mapRef.current && currentLocation) {
      await mapRef.current.setCenter([currentLocation.longitude, currentLocation.latitude]);
      await mapRef.current.zoomTo(14, 300);
    }
  }, [currentLocation]);

  // Handle map region change
  const handleRegionDidChange = useCallback(async () => {
    if (mapRef.current) {
      try {
        const center = await mapRef.current.getCenter();
        const currentZoom = await mapRef.current.getZoom();
        
        updateMapState({
          latitude: center[1],
          longitude: center[0],
          zoomLevel: currentZoom,
        });
        
        setZoom(currentZoom);
      } catch (err) {
        console.error('Failed to get map state:', err);
      }
    }
  }, [updateMapState]);

  if (isLoading) {
    return (
      <View className={cn('flex-1 items-center justify-center bg-background', className)}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text className="mt-4 text-muted">Loading map...</Text>
      </View>
    );
  }

  if (error) {
    return (
      <View className={cn('flex-1 items-center justify-center bg-background p-4', className)}>
        <Text className="text-error font-semibold mb-2">Error Loading Map</Text>
        <Text className="text-muted text-center text-sm">{error}</Text>
      </View>
    );
  }

  if (!sourceUrl) {
    return (
      <View className={cn('flex-1 items-center justify-center bg-background', className)}>
        <Text className="text-muted">No map source available</Text>
      </View>
    );
  }

  const layers = simplified
    ? SIMPLIFIED_PROTOMAPS_LAYERS
    : isDarkMode
      ? DARK_MODE_PROTOMAPS_LAYERS
      : undefined;

  return (
    <View className={cn('flex-1', className)}>
      {React.useMemo(() => {
        const MapViewComponent = (MapLibreGL as any).MapView;
        const PointAnnotationComponent = (MapLibreGL as any).PointAnnotation;
        return (
      <MapViewComponent
        ref={mapRef}
        style={{ flex: 1 }}
        styleURL={sourceUrl}
        zoomLevel={zoom}
        centerCoordinate={[mapState.longitude, mapState.latitude]}
        onDidFinishRenderingMap={() => setIsLoading(false)}
        onRegionDidChange={handleRegionDidChange}
      >
        {/* User Location Marker */}
        {currentLocation && (
          <PointAnnotationComponent
            id="user-location"
            coordinate={[currentLocation.longitude, currentLocation.latitude]}
          >
            <View className="w-4 h-4 bg-blue-500 rounded-full border-2 border-white shadow-lg" />
          </PointAnnotationComponent>
        )}

        {/* GPX Tracks */}
        {gpxTracks.map((track, trackIndex) => (
          <View key={`track-${trackIndex}`}>
            {track.segments.map((segment, segmentIndex) => {
              const coordinates = segment.map((point) => [point.longitude, point.latitude]);

              if (coordinates.length < 2) return null;

              const ShapeSource = (MapLibreGL as any).ShapeSource;
              const LineLayer = (MapLibreGL as any).LineLayer;
              return (
                <ShapeSource
                  key={`segment-${trackIndex}-${segmentIndex}`}
                  id={`gpx-segment-${trackIndex}-${segmentIndex}`}
                  shape={{
                    type: 'Feature',
                    geometry: {
                      type: 'LineString',
                      coordinates,
                    },
                  }}
                >
                  <LineLayer
                    id={`gpx-line-${trackIndex}-${segmentIndex}`}
                    style={{
                      lineColor: '#ff0000',
                      lineWidth: 3,
                      lineCap: 'round',
                      lineJoin: 'round',
                    }}
                  />
                </ShapeSource>
              );
            })}
          </View>
        ))}
      </MapViewComponent>
        );
      }, [])}

      {/* Map Controls */}
      <View className="absolute bottom-4 right-4 gap-2">
        {/* Zoom In Button */}
        <Pressable
          onPress={handleZoomIn}
          className="bg-primary rounded-full w-10 h-10 items-center justify-center shadow-lg"
          style={({ pressed }) => [pressed && { opacity: 0.8 }]}
        >
          <Text className="text-background font-bold text-lg">+</Text>
        </Pressable>

        {/* Zoom Out Button */}
        <Pressable
          onPress={handleZoomOut}
          className="bg-primary rounded-full w-10 h-10 items-center justify-center shadow-lg"
          style={({ pressed }) => [pressed && { opacity: 0.8 }]}
        >
          <Text className="text-background font-bold text-lg">−</Text>
        </Pressable>

        {/* Recenter on Location Button */}
        {currentLocation && (
          <Pressable
            onPress={handleRecenterLocation}
            className="bg-primary rounded-full w-10 h-10 items-center justify-center shadow-lg"
            style={({ pressed }) => [pressed && { opacity: 0.8 }]}
          >
            <Text className="text-background font-bold text-lg">📍</Text>
          </Pressable>
        )}
      </View>

      {/* Attribution */}
      {showAttribution && (
        <View className="absolute bottom-4 left-4 bg-background/90 px-2 py-1 rounded border border-border">
          <Text className="text-xs text-muted">© OpenStreetMap contributors</Text>
          <Text className="text-xs text-muted">Tiles by Protomaps</Text>
        </View>
      )}

      {/* Location Info */}
      {currentLocation && (
        <View className="absolute top-4 left-4 bg-background/90 px-3 py-2 rounded border border-border">
          <Text className="text-xs font-semibold text-foreground">
            {formatCoordinates(currentLocation.latitude, currentLocation.longitude)}
          </Text>
          <Text className="text-xs text-muted">
            Accuracy: ±{Math.round(currentLocation.accuracy || 0)}m
          </Text>
        </View>
      )}

      {/* Zoom Level Info */}
      <View className="absolute top-4 right-4 bg-background/90 px-3 py-2 rounded border border-border">
        <Text className="text-xs font-semibold text-foreground">
          Zoom: {zoom.toFixed(1)}
        </Text>
      </View>
    </View>
  );
}
