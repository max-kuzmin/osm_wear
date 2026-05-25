/**
 * Map Regions Screen - Download and manage offline map regions
 */

import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, FlatList, ActivityIndicator, Alert } from 'react-native';
import { ScreenContainer } from '@/components/screen-container';
import { useMap } from '@/lib/map-context';
import { PREDEFINED_REGIONS, getTileCacheDir } from '@/lib/map-utils';
import * as FileSystem from 'expo-file-system/legacy';
import { useColors } from '@/hooks/use-colors';

interface RegionItem {
  name: string;
  bounds: any;
  minZoom: number;
  maxZoom: number;
  tileCount: number;
  sizeBytes: number;
  isDownloaded?: boolean;
}

export default function RegionsScreen() {
  const { state, dispatch } = useMap();
  const colors = useColors();
  const [regions, setRegions] = useState<RegionItem[]>(PREDEFINED_REGIONS);
  const [downloadingRegion, setDownloadingRegion] = useState<string | null>(null);
  const [downloadProgress, setDownloadProgress] = useState(0);

  // Check which regions are already downloaded
  useEffect(() => {
    checkDownloadedRegions();
  }, []);

  const checkDownloadedRegions = async () => {
    try {
      const cacheDir = await getTileCacheDir();
      const files = await FileSystem.readDirectoryAsync(cacheDir);

      const updatedRegions = regions.map((region) => ({
        ...region,
        isDownloaded: files.includes(region.name.replace(/\s+/g, '-').toLowerCase()),
      }));

      setRegions(updatedRegions);
    } catch (error) {
      console.error('Error checking downloaded regions:', error);
    }
  };

  const handleDownloadRegion = async (region: RegionItem) => {
    if (region.isDownloaded) {
      // Select already downloaded region
      dispatch({ type: 'SET_SELECTED_REGION', payload: region.name });
      Alert.alert('Region Selected', `${region.name} is now active`);
      return;
    }

    // Start download
    setDownloadingRegion(region.name);
    dispatch({ type: 'SET_DOWNLOADING_REGION', payload: region.name });
    setDownloadProgress(0);

    try {
      // Simulate download progress (in a real app, this would download actual tiles)
      for (let i = 0; i <= 100; i += 10) {
        await new Promise((resolve) => setTimeout(resolve, 500));
        setDownloadProgress(i);
        dispatch({ type: 'SET_DOWNLOAD_PROGRESS', payload: i });
      }

      // Mark as downloaded
      const regionDir = await getTileCacheDir();
      const regionPath = `${regionDir}${region.name.replace(/\s+/g, '-').toLowerCase()}`;
      await FileSystem.makeDirectoryAsync(regionPath, { intermediates: true });

      // Update state
      const updatedRegions = regions.map((r) =>
        r.name === region.name ? { ...r, isDownloaded: true } : r
      );
      setRegions(updatedRegions);
      dispatch({ type: 'SET_SELECTED_REGION', payload: region.name });

      Alert.alert('Download Complete', `${region.name} has been downloaded`);
    } catch (error) {
      console.error('Error downloading region:', error);
      Alert.alert('Download Failed', `Failed to download ${region.name}`);
    } finally {
      setDownloadingRegion(null);
      dispatch({ type: 'SET_DOWNLOADING_REGION', payload: null });
      setDownloadProgress(0);
    }
  };

  const handleDeleteRegion = async (region: RegionItem) => {
    Alert.alert('Delete Region', `Are you sure you want to delete ${region.name}?`, [
      { text: 'Cancel', onPress: () => {} },
      {
        text: 'Delete',
        onPress: async () => {
          try {
            const cacheDir = await getTileCacheDir();
            const regionPath = `${cacheDir}${region.name.replace(/\s+/g, '-').toLowerCase()}`;
            await FileSystem.deleteAsync(regionPath, { idempotent: true });

            const updatedRegions = regions.map((r) =>
              r.name === region.name ? { ...r, isDownloaded: false } : r
            );
            setRegions(updatedRegions);

            if (state.selectedRegion === region.name) {
              dispatch({ type: 'SET_SELECTED_REGION', payload: null });
            }

            Alert.alert('Deleted', `${region.name} has been deleted`);
          } catch (error) {
            console.error('Error deleting region:', error);
            Alert.alert('Error', 'Failed to delete region');
          }
        },
      },
    ]);
  };

  const renderRegionItem = ({ item }: { item: RegionItem }) => (
    <View style={[styles.regionCard, { backgroundColor: colors.surface, borderColor: colors.border }]}>
      <View style={styles.regionHeader}>
        <Text style={[styles.regionName, { color: colors.foreground }]}>{item.name}</Text>
        {item.isDownloaded && (
          <Text style={[styles.downloadedBadge, { color: colors.success }]}>✓</Text>
        )}
      </View>

      <Text style={[styles.regionInfo, { color: colors.muted }]}>
        Bounds: {item.bounds.north.toFixed(1)}° N, {Math.abs(item.bounds.south).toFixed(1)}° S
      </Text>
      <Text style={[styles.regionInfo, { color: colors.muted }]}>
        {Math.abs(item.bounds.east - item.bounds.west).toFixed(1)}° × {Math.abs(item.bounds.north - item.bounds.south).toFixed(1)}°
      </Text>

      <View style={styles.regionActions}>
        <TouchableOpacity
          style={[
            styles.actionButton,
            {
              backgroundColor:
                downloadingRegion === item.name ? colors.muted : colors.primary,
            },
          ]}
          onPress={() => handleDownloadRegion(item)}
          disabled={downloadingRegion === item.name}
        >
          {downloadingRegion === item.name ? (
            <ActivityIndicator color={colors.background} size="small" />
          ) : (
            <Text style={[styles.actionButtonText, { color: colors.background }]}>
              {item.isDownloaded ? 'Select' : 'Download'}
            </Text>
          )}
        </TouchableOpacity>

        {item.isDownloaded && (
          <TouchableOpacity
            style={[styles.actionButton, { backgroundColor: colors.error }]}
            onPress={() => handleDeleteRegion(item)}
          >
            <Text style={[styles.actionButtonText, { color: colors.background }]}>Delete</Text>
          </TouchableOpacity>
        )}
      </View>

      {downloadingRegion === item.name && (
        <View style={styles.progressContainer}>
          <View
            style={[
              styles.progressBar,
              {
                width: `${downloadProgress}%`,
                backgroundColor: colors.primary,
              },
            ]}
          />
          <Text style={[styles.progressText, { color: colors.muted }]}>
            {downloadProgress}%
          </Text>
        </View>
      )}
    </View>
  );

  return (
    <ScreenContainer className="flex-1 bg-background">
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.foreground }]}>Map Regions</Text>
        <Text style={[styles.subtitle, { color: colors.muted }]}>
          Download offline maps for different regions
        </Text>
      </View>

      {state.selectedRegion && (
        <View style={[styles.activeRegion, { backgroundColor: colors.primary }]}>
          <Text style={[styles.activeRegionText, { color: colors.background }]}>
            Active: {state.selectedRegion}
          </Text>
        </View>
      )}

      <FlatList
        data={regions}
        renderItem={renderRegionItem}
        keyExtractor={(item) => item.name}
        contentContainerStyle={styles.listContent}
        scrollEnabled={true}
      />
    </ScreenContainer>
  );
}

const styles = StyleSheet.create({
  header: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: '#E5E7EB',
  },
  title: {
    fontSize: 18,
    fontWeight: 'bold',
    marginBottom: 4,
  },
  subtitle: {
    fontSize: 12,
  },
  activeRegion: {
    marginHorizontal: 16,
    marginVertical: 12,
    paddingHorizontal: 12,
    paddingVertical: 8,
    borderRadius: 8,
  },
  activeRegionText: {
    fontSize: 12,
    fontWeight: '500',
  },
  listContent: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
  },
  regionCard: {
    borderRadius: 8,
    padding: 12,
    borderWidth: 1,
  },
  regionHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  regionName: {
    fontSize: 14,
    fontWeight: '600',
    flex: 1,
  },
  downloadedBadge: {
    fontSize: 16,
    fontWeight: 'bold',
  },
  regionInfo: {
    fontSize: 11,
    marginVertical: 2,
  },
  regionActions: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  actionButton: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  actionButtonText: {
    fontSize: 12,
    fontWeight: '600',
  },
  progressContainer: {
    marginTop: 10,
    height: 20,
    backgroundColor: 'rgba(0,0,0,0.1)',
    borderRadius: 4,
    overflow: 'hidden',
    justifyContent: 'center',
  },
  progressBar: {
    height: '100%',
  },
  progressText: {
    position: 'absolute',
    fontSize: 10,
    fontWeight: '600',
    alignSelf: 'center',
  },
});
