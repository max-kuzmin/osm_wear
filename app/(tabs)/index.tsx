/**
 * Home Screen - Main map view for Wear OS app
 * Displays OSM map with current location, GPX overlay, and controls
 */

import React, { useEffect, useState } from 'react';
import { View, Text } from 'react-native';
import { ScreenContainer } from '@/components/screen-container';
import { useMapContext } from '@/lib/map-context';
import { MapLibreMap } from '@/components/maplibre-map';
import { useColors } from '@/hooks/use-colors';
import { type GPXTrack } from '@/lib/gpx-parser';

export default function HomeScreen() {
  const colors = useColors();
  const { currentLocation, requestLocationPermission, startLocationTracking } = useMapContext();
  const [gpxTracks, setGpxTracks] = useState<GPXTrack[]>([]);

  // Request location permission on mount
  useEffect(() => {
    requestLocationPermission();
  }, [requestLocationPermission]);

  // Start location tracking when permission is granted
  useEffect(() => {
    startLocationTracking();
  }, [startLocationTracking]);

  return (
    <ScreenContainer className="flex-1 bg-background p-0" edges={['top', 'left', 'right', 'bottom']}>
      {/* Map */}
      <MapLibreMap
        gpxTracks={gpxTracks}
        isDarkMode={false}
        simplified={true}
        showAttribution={true}
      />

      {/* Status Bar - Top Left */}
      <View className="absolute top-4 left-4 bg-background/90 px-3 py-2 rounded border border-border">
        <Text className="text-xs font-semibold text-foreground">Map View</Text>
        {currentLocation && (
          <Text className="text-xs text-muted mt-1">
            {currentLocation.latitude.toFixed(4)}, {currentLocation.longitude.toFixed(4)}
          </Text>
        )}
      </View>

      {/* Info - Top Right */}
      <View className="absolute top-4 right-4 bg-background/90 px-3 py-2 rounded border border-border">
        <Text className="text-xs text-muted">
          {gpxTracks.length > 0 ? `${gpxTracks.length} track(s)` : 'No tracks'}
        </Text>
      </View>
    </ScreenContainer>
  );
}
