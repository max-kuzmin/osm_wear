/**
 * Home Screen - Main map view for Wear OS app
 * Displays OSM map with current location, GPX overlay, and controls
 */

import React, { useEffect, useState } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, Dimensions } from 'react-native';
import { ScreenContainer } from '@/components/screen-container';
import { useMap } from '@/lib/map-context';
import { MapCanvas } from '@/components/map-canvas';
import { useColors } from '@/hooks/use-colors';

const SCREEN_WIDTH = Dimensions.get('window').width;
const SCREEN_HEIGHT = Dimensions.get('window').height;
const IS_WATCH = SCREEN_WIDTH < 300; // Detect if running on watch

export default function HomeScreen() {
  const { state, dispatch, startLocationTracking, requestLocationPermission } = useMap();
  const colors = useColors();
  const [showControls, setShowControls] = useState(true);

  // Request location permission on mount
  useEffect(() => {
    requestLocationPermission();
  }, [requestLocationPermission]);

  // Start location tracking when permission is granted
  useEffect(() => {
    if (state.locationPermission === 'granted') {
      startLocationTracking();
    }
  }, [state.locationPermission, startLocationTracking]);

  const handleZoomIn = () => {
    const newZoom = Math.min(state.zoomLevel + 1, 18);
    dispatch({ type: 'SET_ZOOM_LEVEL', payload: newZoom });
  };

  const handleZoomOut = () => {
    const newZoom = Math.max(state.zoomLevel - 1, 0);
    dispatch({ type: 'SET_ZOOM_LEVEL', payload: newZoom });
  };

  const handleRecenterLocation = () => {
    if (state.currentLocation) {
      dispatch({
        type: 'SET_MAP_REGION',
        payload: {
          latitude: state.currentLocation.latitude,
          longitude: state.currentLocation.longitude,
          latitudeDelta: 0.05,
          longitudeDelta: 0.05,
        },
      });
    }
  };

  const handleToggleMenu = () => {
    dispatch({ type: 'SET_SHOW_MENU', payload: !state.showMenu });
  };

  return (
    <ScreenContainer className="flex-1 bg-background" edges={['top', 'left', 'right', 'bottom']}>
      {/* Map Canvas */}
      <View style={styles.mapContainer}>
        <MapCanvas gpxData={state.loadedGPX || undefined} />

        {/* Zoom Controls - Right side */}
        {!IS_WATCH && (
          <View style={[styles.zoomControls, { backgroundColor: colors.surface }]}>
            <TouchableOpacity
              style={[styles.zoomButton, { backgroundColor: colors.primary }]}
              onPress={handleZoomIn}
              activeOpacity={0.7}
            >
              <Text style={[styles.zoomButtonText, { color: colors.background }]}>+</Text>
            </TouchableOpacity>
            <TouchableOpacity
              style={[styles.zoomButton, { backgroundColor: colors.primary }]}
              onPress={handleZoomOut}
              activeOpacity={0.7}
            >
              <Text style={[styles.zoomButtonText, { color: colors.background }]}>−</Text>
            </TouchableOpacity>
          </View>
        )}

        {/* Current Location Button - Bottom right */}
        {state.currentLocation && (
          <TouchableOpacity
            style={[styles.locationButton, { backgroundColor: colors.primary }]}
            onPress={handleRecenterLocation}
            activeOpacity={0.7}
          >
            <Text style={[styles.locationButtonText, { color: colors.background }]}>📍</Text>
          </TouchableOpacity>
        )}

        {/* Menu Button - Top right */}
        <TouchableOpacity
          style={[styles.menuButton, { backgroundColor: colors.surface }]}
          onPress={handleToggleMenu}
          activeOpacity={0.7}
        >
          <Text style={[styles.menuButtonText, { color: colors.foreground }]}>☰</Text>
        </TouchableOpacity>

        {/* Map Info Overlay - Top left */}
        {showControls && (
          <View style={[styles.infoOverlay, { backgroundColor: colors.surface }]}>
            <Text style={[styles.infoText, { color: colors.foreground }]}>
              Zoom: {Math.round(state.zoomLevel)}
            </Text>
            {state.currentLocation && (
              <>
                <Text style={[styles.infoText, { color: colors.muted }]}>
                  Lat: {state.currentLocation.latitude.toFixed(4)}
                </Text>
                <Text style={[styles.infoText, { color: colors.muted }]}>
                  Lon: {state.currentLocation.longitude.toFixed(4)}
                </Text>
                {state.currentLocation.accuracy && (
                  <Text style={[styles.infoText, { color: colors.muted }]}>
                    Acc: {Math.round(state.currentLocation.accuracy)}m
                  </Text>
                )}
              </>
            )}
            {state.gpxFileName && (
              <Text style={[styles.infoText, { color: colors.success }]}>
                Track: {state.gpxFileName}
              </Text>
            )}
            {state.selectedRegion && (
              <Text style={[styles.infoText, { color: colors.success }]}>
                Region: {state.selectedRegion}
              </Text>
            )}
          </View>
        )}

        {/* Error Messages */}
        {state.locationError && (
          <View style={[styles.errorOverlay, { backgroundColor: colors.error }]}>
            <Text style={[styles.errorText, { color: colors.background }]}>
              {state.locationError}
            </Text>
          </View>
        )}

        {state.gpxError && (
          <View style={[styles.errorOverlay, { backgroundColor: colors.error }]}>
            <Text style={[styles.errorText, { color: colors.background }]}>
              {state.gpxError}
            </Text>
          </View>
        )}
      </View>

      {/* Bottom Info Bar */}
      {!IS_WATCH && (
        <View style={[styles.bottomBar, { backgroundColor: colors.surface, borderTopColor: colors.border }]}>
          <Text style={[styles.bottomBarText, { color: colors.foreground }]}>
            {state.loadedGPX ? `📍 ${state.gpxFileName}` : 'No track loaded'}
          </Text>
        </View>
      )}
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  mapContainer: {
    flex: 1,
    position: 'relative',
  },
  zoomControls: {
    position: 'absolute',
    right: 16,
    top: 16,
    borderRadius: 8,
    overflow: 'hidden',
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3,
  },
  zoomButton: {
    width: 44,
    height: 44,
    justifyContent: 'center',
    alignItems: 'center',
    borderBottomWidth: 1,
    borderBottomColor: 'rgba(0,0,0,0.1)',
  },
  zoomButtonText: {
    fontSize: 20,
    fontWeight: 'bold',
  },
  locationButton: {
    position: 'absolute',
    bottom: 16,
    right: 16,
    width: 48,
    height: 48,
    borderRadius: 24,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 5,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 2 },
    shadowOpacity: 0.25,
    shadowRadius: 3,
  },
  locationButtonText: {
    fontSize: 24,
  },
  menuButton: {
    position: 'absolute',
    top: 16,
    right: 16,
    width: 44,
    height: 44,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  menuButtonText: {
    fontSize: 20,
  },
  infoOverlay: {
    position: 'absolute',
    top: 16,
    left: 16,
    borderRadius: 8,
    padding: 12,
    maxWidth: 150,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  infoText: {
    fontSize: 12,
    marginVertical: 2,
    fontFamily: 'monospace',
  },
  errorOverlay: {
    position: 'absolute',
    bottom: 16,
    left: 16,
    right: 16,
    borderRadius: 8,
    padding: 12,
    elevation: 3,
    shadowColor: '#000',
    shadowOffset: { width: 0, height: 1 },
    shadowOpacity: 0.2,
    shadowRadius: 2,
  },
  errorText: {
    fontSize: 12,
    fontWeight: '500',
  },
  bottomBar: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderTopWidth: 1,
  },
  bottomBarText: {
    fontSize: 14,
    fontWeight: '500',
  },
});
