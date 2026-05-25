/**
 * Settings Screen - App configuration and preferences\n */

import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, ScrollView, Switch, Alert } from 'react-native';
import { ScreenContainer } from '@/components/screen-container';
import { useMap } from '@/lib/map-context';
import { useColors } from '@/hooks/use-colors';
import { useColorScheme } from '@/hooks/use-color-scheme';
import { useTheme } from '@/lib/theme-provider';
import * as FileSystem from 'expo-file-system/legacy';
import { getTileCacheDir, getGPXDir } from '@/lib/map-utils';

export default function SettingsScreen() {
  const { state, startLocationTracking, stopLocationTracking } = useMap();
  const colors = useColors();
  const { colorScheme, setColorScheme } = useTheme();
  const [gpsEnabled, setGpsEnabled] = useState(true);
  const [cacheSize, setCacheSize] = useState('0 MB');
  const [gpxCount, setGpxCount] = useState(0);

  // Load cache and GPX info on mount
  useEffect(() => {
    loadStorageInfo();
  }, []);

  const loadStorageInfo = async () => {
    try {
      // Get cache size
      const cacheDir = await getTileCacheDir();
      const cacheInfo = await FileSystem.getInfoAsync(cacheDir);
      if ((cacheInfo as any).size) {
        const sizeMB = ((cacheInfo as any).size / (1024 * 1024)).toFixed(2);
        setCacheSize(`${sizeMB} MB`);
      }

      // Get GPX file count
      const gpxDir = await getGPXDir();
      const gpxFiles = await FileSystem.readDirectoryAsync(gpxDir);
      const gpxCount = gpxFiles.filter((f) => f.endsWith('.gpx')).length;
      setGpxCount(gpxCount);
    } catch (error) {
      console.error('Error loading storage info:', error);
    }
  };

  const handleToggleGPS = (value: boolean) => {
    setGpsEnabled(value);
    if (value) {
      startLocationTracking();
    } else {
      stopLocationTracking();
    }
  };

  const handleToggleDarkMode = () => {
    const newScheme = colorScheme === 'dark' ? 'light' : 'dark';
    setColorScheme(newScheme);
  };

  const handleClearCache = () => {
    Alert.alert('Clear Cache', 'Are you sure you want to delete all cached map tiles?', [
      { text: 'Cancel', onPress: () => {} },
      {
        text: 'Clear',
        onPress: async () => {
          try {
            const cacheDir = await getTileCacheDir();
            await FileSystem.deleteAsync(cacheDir, { idempotent: true });
            await FileSystem.makeDirectoryAsync(cacheDir, { intermediates: true });
            setCacheSize('0 MB');
            Alert.alert('Success', 'Cache cleared');
          } catch (error) {
            console.error('Error clearing cache:', error);
            Alert.alert('Error', 'Failed to clear cache');
          }
        },
      },
    ]);
  };

  const handleAbout = () => {
    Alert.alert(
      'About Wear OSM Map',
      'Version 1.0.0\n\nA Wear OS map application with offline OpenStreetMap support, GPX file visualization, and GPS tracking.\n\nOptimized for Samsung Galaxy Watch 7.',
      [{ text: 'OK', onPress: () => {} }]
    );
  };

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={styles.container}>
        <View style={styles.header}>
          <Text style={[styles.title, { color: colors.foreground }]}>Settings</Text>
        </View>

        {/* GPS Settings */}
        <View style={[styles.section, { borderBottomColor: colors.border }]}>
          <Text style={[styles.sectionTitle, { color: colors.foreground }]}>Location</Text>

          <View style={styles.settingRow}>
            <View style={styles.settingLabel}>
              <Text style={[styles.settingText, { color: colors.foreground }]}>GPS Tracking</Text>
              <Text style={[styles.settingSubtext, { color: colors.muted }]}>
                {gpsEnabled ? 'Enabled' : 'Disabled'}
              </Text>
            </View>
            <Switch
              value={gpsEnabled}
              onValueChange={handleToggleGPS}
              trackColor={{ false: colors.border, true: colors.primary }}
              thumbColor={gpsEnabled ? colors.primary : colors.muted}
            />
          </View>

          {state.currentLocation && (
            <View style={styles.infoBox}>
              <Text style={[styles.infoText, { color: colors.muted }]}>
                Current Location:
              </Text>
              <Text style={[styles.infoValue, { color: colors.foreground }]}>
                {state.currentLocation.latitude.toFixed(6)}, {state.currentLocation.longitude.toFixed(6)}
              </Text>
              {state.currentLocation.accuracy && (
                <Text style={[styles.infoText, { color: colors.muted }]}>
                  Accuracy: {Math.round(state.currentLocation.accuracy)}m
                </Text>
              )}
            </View>
          )}
        </View>

        {/* Display Settings */}
        <View style={[styles.section, { borderBottomColor: colors.border }]}>
          <Text style={[styles.sectionTitle, { color: colors.foreground }]}>Display</Text>

          <View style={styles.settingRow}>
            <View style={styles.settingLabel}>
              <Text style={[styles.settingText, { color: colors.foreground }]}>Dark Mode</Text>
              <Text style={[styles.settingSubtext, { color: colors.muted }]}>
                {colorScheme === 'dark' ? 'On' : 'Off'}
              </Text>
            </View>
            <Switch
              value={colorScheme === 'dark'}
              onValueChange={handleToggleDarkMode}
              trackColor={{ false: colors.border, true: colors.primary }}
              thumbColor={colorScheme === 'dark' ? colors.primary : colors.muted}
            />
          </View>
        </View>

        {/* Storage Settings */}
        <View style={[styles.section, { borderBottomColor: colors.border }]}>
          <Text style={[styles.sectionTitle, { color: colors.foreground }]}>Storage</Text>

          <View style={styles.infoBox}>
            <Text style={[styles.infoText, { color: colors.muted }]}>Map Tiles Cache:</Text>
            <Text style={[styles.infoValue, { color: colors.foreground }]}>{cacheSize}</Text>
          </View>

          <View style={styles.infoBox}>
            <Text style={[styles.infoText, { color: colors.muted }]}>GPX Files:</Text>
            <Text style={[styles.infoValue, { color: colors.foreground }]}>{gpxCount} files</Text>
          </View>

          <TouchableOpacity
            style={[styles.button, { backgroundColor: colors.error }]}
            onPress={handleClearCache}
          >
            <Text style={[styles.buttonText, { color: colors.background }]}>Clear Cache</Text>
          </TouchableOpacity>
        </View>

        {/* About */}
        <View style={[styles.section, { borderBottomColor: colors.border }]}>
          <TouchableOpacity
            style={[styles.button, { backgroundColor: colors.primary }]}
            onPress={handleAbout}
          >
            <Text style={[styles.buttonText, { color: colors.background }]}>About</Text>
          </TouchableOpacity>
        </View>

        {/* App Info */}
        <View style={styles.footer}>
          <Text style={[styles.footerText, { color: colors.muted }]}>
            Wear OSM Map v1.0.0
          </Text>
          <Text style={[styles.footerText, { color: colors.muted }]}>
            Optimized for Wear OS
          </Text>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  container: {
    paddingBottom: 24,
  },
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
  },
  section: {
    paddingHorizontal: 16,
    paddingVertical: 16,
    borderBottomWidth: 1,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    marginBottom: 12,
  },
  settingRow: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginVertical: 8,
  },
  settingLabel: {
    flex: 1,
  },
  settingText: {
    fontSize: 14,
    fontWeight: '500',
  },
  settingSubtext: {
    fontSize: 12,
    marginTop: 2,
  },
  infoBox: {
    marginVertical: 8,
    paddingHorizontal: 12,
    paddingVertical: 8,
    backgroundColor: 'rgba(0,0,0,0.05)',
    borderRadius: 6,
  },
  infoText: {
    fontSize: 11,
    marginBottom: 2,
  },
  infoValue: {
    fontSize: 12,
    fontWeight: '600',
    fontFamily: 'monospace',
  },
  button: {
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
    marginVertical: 8,
  },
  buttonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  footer: {
    paddingHorizontal: 16,
    paddingVertical: 24,
    alignItems: 'center',
  },
  footerText: {
    fontSize: 11,
    marginVertical: 2,
  },
});
