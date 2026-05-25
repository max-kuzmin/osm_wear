/**
 * GPX Files Screen - Import and manage GPX tracks
 */

import React, { useState, useEffect } from 'react';
import { View, Text, TouchableOpacity, StyleSheet, FlatList, Alert, ActivityIndicator } from 'react-native';
import { ScreenContainer } from '@/components/screen-container';
import { useMap } from '@/lib/map-context';
import { getGPXDir } from '@/lib/map-utils';
import { parseGPX, calculateGPXDistance, getGPXBounds } from '@/lib/gpx-parser';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import { useColors } from '@/hooks/use-colors';

interface GPXFile {
  name: string;
  uri: string;
  size: number;
  modificationTime?: number;
}

export default function GPXScreen() {
  const { state, dispatch } = useMap();
  const colors = useColors();
  const [gpxFiles, setGPXFiles] = useState<GPXFile[]>([]);
  const [loading, setLoading] = useState(false);
  const [importing, setImporting] = useState(false);

  // Load GPX files on mount
  useEffect(() => {
    loadGPXFiles();
  }, []);

  const loadGPXFiles = async () => {
    try {
      setLoading(true);
      const gpxDir = await getGPXDir();
      const files = await FileSystem.readDirectoryAsync(gpxDir);

      const gpxFilesList: GPXFile[] = [];
      for (const file of files) {
        if (file.endsWith('.gpx')) {
          const fileUri = `${gpxDir}${file}`;
          const info = await FileSystem.getInfoAsync(fileUri);
          gpxFilesList.push({
            name: file,
            uri: fileUri,
            size: (info as any).size || 0,
            modificationTime: (info as any).modificationTime,
          });
        }
      }

      setGPXFiles(gpxFilesList);
    } catch (error) {
      console.error('Error loading GPX files:', error);
    } finally {
      setLoading(false);
    }
  };

  const handleImportGPX = async () => {
    try {
      setImporting(true);
      const result = await DocumentPicker.getDocumentAsync({
        type: 'application/gpx+xml',
        copyToCacheDirectory: true,
      });

      if (!result.canceled && result.assets[0]) {
        const asset = result.assets[0];
        const gpxDir = await getGPXDir();
        const destinationUri = `${gpxDir}${asset.name}`;

        // Copy file to app directory
        await FileSystem.copyAsync({
          from: asset.uri,
          to: destinationUri,
        });

        // Reload files
        await loadGPXFiles();
        Alert.alert('Success', `${asset.name} has been imported`);
      }
    } catch (error) {
      console.error('Error importing GPX:', error);
      Alert.alert('Error', 'Failed to import GPX file');
    } finally {
      setImporting(false);
    }
  };

  const handleLoadGPX = async (gpxFile: GPXFile) => {
    try {
      setLoading(true);
      const content = await FileSystem.readAsStringAsync(gpxFile.uri);
      const gpxData = parseGPX(content);

      dispatch({
        type: 'SET_LOADED_GPX',
        payload: {
          data: gpxData,
          fileName: gpxFile.name,
        },
      });

      Alert.alert('Loaded', `${gpxFile.name} is now displayed on the map`);
    } catch (error) {
      console.error('Error loading GPX:', error);
      dispatch({ type: 'SET_GPX_ERROR', payload: 'Failed to load GPX file' });
      Alert.alert('Error', 'Failed to load GPX file');
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteGPX = async (gpxFile: GPXFile) => {
    Alert.alert('Delete GPX', `Are you sure you want to delete ${gpxFile.name}?`, [
      { text: 'Cancel', onPress: () => {} },
      {
        text: 'Delete',
        onPress: async () => {
          try {
            await FileSystem.deleteAsync(gpxFile.uri);
            await loadGPXFiles();

            if (state.gpxFileName === gpxFile.name) {
              dispatch({ type: 'CLEAR_GPX' });
            }

            Alert.alert('Deleted', `${gpxFile.name} has been deleted`);
          } catch (error) {
            console.error('Error deleting GPX:', error);
            Alert.alert('Error', 'Failed to delete GPX file');
          }
        },
      },
    ]);
  };

  const getGPXStats = async (gpxFile: GPXFile) => {
    try {
      const content = await FileSystem.readAsStringAsync(gpxFile.uri);
      const gpxData = parseGPX(content);

      let totalDistance = 0;
      let totalPoints = 0;

      gpxData.tracks.forEach((track) => {
        track.segments.forEach((segment) => {
          totalDistance += calculateGPXDistance(segment);
          totalPoints += segment.length;
        });
      });

      gpxData.routes.forEach((route) => {
        totalDistance += calculateGPXDistance(route.points);
        totalPoints += route.points.length;
      });

      return {
        distance: totalDistance.toFixed(2),
        points: totalPoints,
        tracks: gpxData.tracks.length,
        routes: gpxData.routes.length,
      };
    } catch (error) {
      return null;
    }
  };

  const renderGPXItem = ({ item }: { item: GPXFile }) => {
    const isActive = state.gpxFileName === item.name;

    return (
      <GPXFileCard
        file={item}
        isActive={isActive}
        onLoad={() => handleLoadGPX(item)}
        onDelete={() => handleDeleteGPX(item)}
        colors={colors}
      />
    );
  };

  return (
    <ScreenContainer className="flex-1 bg-background">
      <View style={styles.header}>
        <Text style={[styles.title, { color: colors.foreground }]}>GPX Files</Text>
        <Text style={[styles.subtitle, { color: colors.muted }]}>
          Import and manage GPS tracks
        </Text>
      </View>

      <TouchableOpacity
        style={[styles.importButton, { backgroundColor: colors.primary }]}
        onPress={handleImportGPX}
        disabled={importing}
      >
        {importing ? (
          <ActivityIndicator color={colors.background} size="small" />
        ) : (
          <Text style={[styles.importButtonText, { color: colors.background }]}>
            + Import GPX File
          </Text>
        )}
      </TouchableOpacity>

      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={colors.primary} />
        </View>
      ) : gpxFiles.length === 0 ? (
        <View style={styles.emptyContainer}>
          <Text style={[styles.emptyText, { color: colors.muted }]}>
            No GPX files imported yet
          </Text>
          <Text style={[styles.emptySubtext, { color: colors.muted }]}>
            Tap "Import GPX File" to get started
          </Text>
        </View>
      ) : (
        <FlatList
          data={gpxFiles}
          renderItem={renderGPXItem}
          keyExtractor={(item) => item.uri}
          contentContainerStyle={styles.listContent}
          scrollEnabled={true}
        />
      )}
    </ScreenContainer>
  );
}

interface GPXFileCardProps {
  file: GPXFile;
  isActive: boolean;
  onLoad: () => void;
  onDelete: () => void;
  colors: any;
}

function GPXFileCard({ file, isActive, onLoad, onDelete, colors }: GPXFileCardProps) {
  const [stats, setStats] = useState<any>(null);
  const [loadingStats, setLoadingStats] = useState(false);

  useEffect(() => {
    loadStats();
  }, []);

  const loadStats = async () => {
    setLoadingStats(true);
    const content = await FileSystem.readAsStringAsync(file.uri);
    const gpxData = parseGPX(content);

    let totalDistance = 0;
    let totalPoints = 0;

    gpxData.tracks.forEach((track) => {
      track.segments.forEach((segment) => {
        totalDistance += calculateGPXDistance(segment);
        totalPoints += segment.length;
      });
    });

    gpxData.routes.forEach((route) => {
      totalDistance += calculateGPXDistance(route.points);
      totalPoints += route.points.length;
    });

    setStats({
      distance: totalDistance.toFixed(2),
      points: totalPoints,
      tracks: gpxData.tracks.length,
      routes: gpxData.routes.length,
    });
    setLoadingStats(false);
  };

  return (
    <View style={[styles.gpxCard, { backgroundColor: colors.surface, borderColor: isActive ? colors.primary : colors.border }]}>
      <View style={styles.gpxHeader}>
        <Text style={[styles.gpxName, { color: colors.foreground }]} numberOfLines={1}>
          {isActive && '✓ '}
          {file.name}
        </Text>
        {isActive && (
          <Text style={[styles.activeBadge, { color: colors.success }]}>Active</Text>
        )}
      </View>

      {loadingStats ? (
        <ActivityIndicator size="small" color={colors.primary} />
      ) : stats ? (
        <>
          <Text style={[styles.gpxStat, { color: colors.muted }]}>
            Distance: {stats.distance} km
          </Text>
          <Text style={[styles.gpxStat, { color: colors.muted }]}>
            Points: {stats.points}
          </Text>
          <Text style={[styles.gpxStat, { color: colors.muted }]}>
            Tracks: {stats.tracks} | Routes: {stats.routes}
          </Text>
        </>
      ) : null}

      <View style={styles.gpxActions}>
        <TouchableOpacity
          style={[styles.gpxButton, { backgroundColor: colors.primary }]}
          onPress={onLoad}
        >
          <Text style={[styles.gpxButtonText, { color: colors.background }]}>
            {isActive ? 'Loaded' : 'Load'}
          </Text>
        </TouchableOpacity>

        <TouchableOpacity
          style={[styles.gpxButton, { backgroundColor: colors.error }]}
          onPress={onDelete}
        >
          <Text style={[styles.gpxButtonText, { color: colors.background }]}>Delete</Text>
        </TouchableOpacity>
      </View>
    </View>
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
  importButton: {
    marginHorizontal: 16,
    marginVertical: 12,
    paddingVertical: 12,
    paddingHorizontal: 16,
    borderRadius: 8,
    justifyContent: 'center',
    alignItems: 'center',
  },
  importButtonText: {
    fontSize: 14,
    fontWeight: '600',
  },
  loadingContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
  },
  emptyContainer: {
    flex: 1,
    justifyContent: 'center',
    alignItems: 'center',
    paddingHorizontal: 32,
  },
  emptyText: {
    fontSize: 16,
    fontWeight: '600',
    marginBottom: 8,
    textAlign: 'center',
  },
  emptySubtext: {
    fontSize: 12,
    textAlign: 'center',
  },
  listContent: {
    paddingHorizontal: 16,
    paddingVertical: 12,
    gap: 12,
  },
  gpxCard: {
    borderRadius: 8,
    padding: 12,
    borderWidth: 2,
  },
  gpxHeader: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: 8,
  },
  gpxName: {
    fontSize: 14,
    fontWeight: '600',
    flex: 1,
  },
  activeBadge: {
    fontSize: 10,
    fontWeight: 'bold',
    paddingHorizontal: 6,
    paddingVertical: 2,
  },
  gpxStat: {
    fontSize: 11,
    marginVertical: 2,
  },
  gpxActions: {
    flexDirection: 'row',
    gap: 8,
    marginTop: 10,
  },
  gpxButton: {
    flex: 1,
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 6,
    justifyContent: 'center',
    alignItems: 'center',
  },
  gpxButtonText: {
    fontSize: 12,
    fontWeight: '600',
  },
});
