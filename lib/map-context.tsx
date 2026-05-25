/**
 * Map context provider for managing global map state
 * Handles current location, selected region, loaded GPX files, etc.
 */

import React, { createContext, useContext, useReducer, useCallback, useEffect } from 'react';
import * as Location from 'expo-location';
import { GPXData } from './gpx-parser';

export interface MapState {
  // Current location
  currentLocation: {
    latitude: number;
    longitude: number;
    accuracy?: number | null;
    timestamp?: number | null;
  } | null;
  locationError: string | null;
  locationPermission: 'granted' | 'denied' | 'undetermined';

  // Map view
  mapRegion: {
    latitude: number;
    longitude: number;
    latitudeDelta: number;
    longitudeDelta: number;
  };
  zoomLevel: number;

  // Offline tiles
  selectedRegion: string | null;
  downloadingRegion: string | null;
  downloadProgress: number;

  // GPX files
  loadedGPX: GPXData | null;
  gpxFileName: string | null;
  gpxError: string | null;

  // UI state
  isLoading: boolean;
  showMenu: boolean;
}

export type MapAction =
  | { type: 'SET_LOCATION'; payload: MapState['currentLocation'] }
  | { type: 'SET_LOCATION_ERROR'; payload: string }
  | { type: 'SET_LOCATION_PERMISSION'; payload: MapState['locationPermission'] }
  | { type: 'SET_MAP_REGION'; payload: MapState['mapRegion'] }
  | { type: 'SET_ZOOM_LEVEL'; payload: number }
  | { type: 'SET_SELECTED_REGION'; payload: string | null }
  | { type: 'SET_DOWNLOADING_REGION'; payload: string | null }
  | { type: 'SET_DOWNLOAD_PROGRESS'; payload: number }
  | { type: 'SET_LOADED_GPX'; payload: { data: GPXData; fileName: string } }
  | { type: 'CLEAR_GPX' }
  | { type: 'SET_GPX_ERROR'; payload: string }
  | { type: 'SET_LOADING'; payload: boolean }
  | { type: 'SET_SHOW_MENU'; payload: boolean };

const initialState: MapState = {
  currentLocation: null,
  locationError: null,
  locationPermission: 'undetermined',
  mapRegion: {
    latitude: 51.5074,
    longitude: -0.1278,
    latitudeDelta: 0.05,
    longitudeDelta: 0.05,
  },
  zoomLevel: 13,
  selectedRegion: null,
  downloadingRegion: null,
  downloadProgress: 0,
  loadedGPX: null,
  gpxFileName: null,
  gpxError: null,
  isLoading: false,
  showMenu: false,
};

function mapReducer(state: MapState, action: MapAction): MapState {
  switch (action.type) {
    case 'SET_LOCATION':
      return { ...state, currentLocation: action.payload, locationError: null };
    case 'SET_LOCATION_ERROR':
      return { ...state, locationError: action.payload };
    case 'SET_LOCATION_PERMISSION':
      return { ...state, locationPermission: action.payload };
    case 'SET_MAP_REGION':
      return { ...state, mapRegion: action.payload };
    case 'SET_ZOOM_LEVEL':
      return { ...state, zoomLevel: action.payload };
    case 'SET_SELECTED_REGION':
      return { ...state, selectedRegion: action.payload };
    case 'SET_DOWNLOADING_REGION':
      return { ...state, downloadingRegion: action.payload };
    case 'SET_DOWNLOAD_PROGRESS':
      return { ...state, downloadProgress: action.payload };
    case 'SET_LOADED_GPX':
      return {
        ...state,
        loadedGPX: action.payload.data,
        gpxFileName: action.payload.fileName,
        gpxError: null,
      };
    case 'CLEAR_GPX':
      return { ...state, loadedGPX: null, gpxFileName: null, gpxError: null };
    case 'SET_GPX_ERROR':
      return { ...state, gpxError: action.payload };
    case 'SET_LOADING':
      return { ...state, isLoading: action.payload };
    case 'SET_SHOW_MENU':
      return { ...state, showMenu: action.payload };
    default:
      return state;
  }
}

interface MapContextType {
  state: MapState;
  dispatch: React.Dispatch<MapAction>;
  requestLocationPermission: () => Promise<void>;
  startLocationTracking: () => Promise<void>;
  stopLocationTracking: () => void;
}

const MapContext = createContext<MapContextType | undefined>(undefined);

export function MapProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(mapReducer, initialState);
  const locationSubscriptionRef = React.useRef<Location.LocationSubscription | null>(null);

  const requestLocationPermission = useCallback(async () => {
    try {
      const { status } = await Location.requestForegroundPermissionsAsync();
      dispatch({ type: 'SET_LOCATION_PERMISSION', payload: status as any });

      if (status === 'granted') {
        // Get initial location
        const location = await Location.getCurrentPositionAsync({});
        dispatch({
          type: 'SET_LOCATION',
          payload: {
            latitude: location.coords.latitude,
            longitude: location.coords.longitude,
            accuracy: location.coords.accuracy,
            timestamp: location.timestamp,
          },
        });
      } else {
        dispatch({ type: 'SET_LOCATION_ERROR', payload: 'Location permission denied' });
      }
    } catch (error) {
      dispatch({ type: 'SET_LOCATION_ERROR', payload: String(error) });
    }
  }, []);

  const startLocationTracking = useCallback(async () => {
    try {
      // Check if permission is already granted
      const { status } = await Location.getForegroundPermissionsAsync();
      if (status !== 'granted') {
        await requestLocationPermission();
        return;
      }

      // Subscribe to location updates
      locationSubscriptionRef.current = await Location.watchPositionAsync(
        {
          accuracy: Location.Accuracy.High,
          timeInterval: 5000, // Update every 5 seconds
          distanceInterval: 10, // Or when moved 10 meters
        },
        (location) => {
          dispatch({
            type: 'SET_LOCATION',
            payload: {
              latitude: location.coords.latitude,
              longitude: location.coords.longitude,
              accuracy: location.coords.accuracy,
              timestamp: location.timestamp,
            },
          });
        }
      );
    } catch (error) {
      dispatch({ type: 'SET_LOCATION_ERROR', payload: String(error) });
    }
  }, [requestLocationPermission]);

  const stopLocationTracking = useCallback(() => {
    if (locationSubscriptionRef.current) {
      locationSubscriptionRef.current.remove();
      locationSubscriptionRef.current = null;
    }
  }, []);

  // Cleanup on unmount
  useEffect(() => {
    return () => {
      stopLocationTracking();
    };
  }, [stopLocationTracking]);

  const value: MapContextType = {
    state,
    dispatch,
    requestLocationPermission,
    startLocationTracking,
    stopLocationTracking,
  };

  return <MapContext.Provider value={value}>{children}</MapContext.Provider>;
}

export function useMap() {
  const context = useContext(MapContext);
  if (!context) {
    throw new Error('useMap must be used within a MapProvider');
  }
  return context;
}
