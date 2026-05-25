/**
 * GPX File Manager Component
 * 
 * Manages GPX file import, display, and track selection
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  Pressable,
  ActivityIndicator,
  Alert,
  FlatList,
} from 'react-native';
import * as DocumentPicker from 'expo-document-picker';
import * as FileSystem from 'expo-file-system/legacy';
import { parseGPX, type GPXTrack, calculateGPXDistance } from '@/lib/gpx-parser';
import { useColors } from '@/hooks/use-colors';
import { cn } from '@/lib/utils';
import AsyncStorage from '@react-native-async-storage/async-storage';

const GPX_FILES_STORAGE_KEY = 'osm_gpx_files';
const GPX_FILES_DIR = `${FileSystem.documentDirectory}gpx/`;

interface StoredGPXFile {
  id: string;
  name: string;
  filePath: string;
  fileSize: number;
  importedAt: number;
  trackCount: number;
  totalDistance: number;
}

interface GPXManagerProps {
  className?: string;
  onTracksSelected?: (tracks: GPXTrack[]) => void;
  selectedFileId?: string;
}

export function GPXManager({ className, onTracksSelected, selectedFileId }: GPXManagerProps) {
  const colors = useColors();
  const [gpxFiles, setGpxFiles] = useState<StoredGPXFile[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isImporting, setIsImporting] = useState(false);
  const [expandedFileId, setExpandedFileId] = useState<string | null>(null);

  // Initialize GPX directory
  useEffect(() => {
    const initializeGPXDir = async () => {
      try {
        const dirInfo = await FileSystem.getInfoAsync(GPX_FILES_DIR);
        if (!dirInfo.exists) {
          await FileSystem.makeDirectoryAsync(GPX_FILES_DIR, { intermediates: true });
        }
        await loadGPXFiles();
      } catch (error) {
        console.error('Failed to initialize GPX directory:', error);
      }
    };

    initializeGPXDir();
  }, []);

  // Load GPX files from storage
  const loadGPXFiles = useCallback(async () => {
    try {
      setIsLoading(true);
      const data = await AsyncStorage.getItem(GPX_FILES_STORAGE_KEY);
      const files = data ? JSON.parse(data) : [];
      setGpxFiles(files);
    } catch (error) {
      console.error('Failed to load GPX files:', error);
    } finally {
      setIsLoading(false);
    }
  }, []);

  // Save GPX files to storage
  const saveGPXFiles = useCallback(async (files: StoredGPXFile[]) => {
    try {
      await AsyncStorage.setItem(GPX_FILES_STORAGE_KEY, JSON.stringify(files));
      setGpxFiles(files);
    } catch (error) {
      console.error('Failed to save GPX files:', error);
    }
  }, []);

  // Import GPX file
  const handleImportGPX = useCallback(async () => {
    try {
      setIsImporting(true);

      const result = await DocumentPicker.getDocumentAsync({
        type: 'text/xml',
        copyToCacheDirectory: false,
      });

      if (result.canceled) {
        setIsImporting(false);
        return;
      }

      const selectedFile = result.assets[0];
      if (!selectedFile.uri) {
        Alert.alert('Error', 'Failed to get file URI');
        setIsImporting(false);
        return;
      }

      // Read file content
      const content = await FileSystem.readAsStringAsync(selectedFile.uri);

      // Parse GPX
      const gpxData = parseGPX(content);

      if (!gpxData.tracks || gpxData.tracks.length === 0) {
        Alert.alert('Error', 'No tracks found in GPX file');
        setIsImporting(false);
        return;
      }

      // Calculate total distance
      let totalDistance = 0;
      gpxData.tracks.forEach((track) => {
        track.segments.forEach((segment) => {
          totalDistance += calculateGPXDistance(segment);
        });
      });

      // Copy file to GPX directory
      const fileName = selectedFile.name || `track-${Date.now()}.gpx`;
      const newFilePath = `${GPX_FILES_DIR}${fileName}`;

      await FileSystem.copyAsync({
        from: selectedFile.uri,
        to: newFilePath,
      });

      // Get file size
      const fileInfo = await FileSystem.getInfoAsync(newFilePath);
      const fileSize = (fileInfo as any).size || 0;

      // Create stored file entry
      const newFile: StoredGPXFile = {
        id: `gpx-${Date.now()}`,
        name: fileName,
        filePath: newFilePath,
        fileSize,
        importedAt: Date.now(),
        trackCount: gpxData.tracks.length,
        totalDistance,
      };

      // Add to files list
      const updatedFiles = [...gpxFiles, newFile];
      await saveGPXFiles(updatedFiles);

      Alert.alert('Success', `Imported ${fileName} with ${gpxData.tracks.length} track(s)`);
    } catch (error) {
      const errorMessage = error instanceof Error ? error.message : 'Unknown error';
      Alert.alert('Import Failed', errorMessage);
    } finally {
      setIsImporting(false);
    }
  }, [gpxFiles, saveGPXFiles]);

  // Delete GPX file
  const handleDeleteGPX = useCallback(
    async (fileId: string) => {
      Alert.alert('Delete GPX File', 'Are you sure you want to delete this file?', [
        { text: 'Cancel', onPress: () => {} },
        {
          text: 'Delete',
          onPress: async () => {
            try {
              const file = gpxFiles.find((f) => f.id === fileId);
              if (file) {
                // Delete physical file
                await FileSystem.deleteAsync(file.filePath);

                // Remove from list
                const updatedFiles = gpxFiles.filter((f) => f.id !== fileId);
                await saveGPXFiles(updatedFiles);

                Alert.alert('Success', 'File deleted');
              }
            } catch (error) {
              Alert.alert('Error', 'Failed to delete file');
            }
          },
        },
      ]);
    },
    [gpxFiles, saveGPXFiles]
  );

  // Load and display GPX file
  const handleSelectGPX = useCallback(
    async (fileId: string) => {
      try {
        const file = gpxFiles.find((f) => f.id === fileId);
        if (!file) return;

        setIsLoading(true);

        // Read file content
        const content = await FileSystem.readAsStringAsync(file.filePath);

        // Parse GPX
        const gpxData = parseGPX(content);

        // Assign colors to tracks
        const colors = ['#ff0000', '#00ff00', '#0000ff', '#ffff00', '#ff00ff'];
        const tracksWithColors = gpxData.tracks.map((track, index) => ({
          ...track,
          color: colors[index % colors.length],
        }));

        // Notify parent component
        onTracksSelected?.(tracksWithColors);

        setIsLoading(false);
      } catch (error) {
        Alert.alert('Error', 'Failed to load GPX file');
        setIsLoading(false);
      }
    },
    [gpxFiles, onTracksSelected]
  );

  // Format bytes
  const formatBytes = (bytes: number): string => {
    if (bytes === 0) return '0 B';
    const k = 1024;
    const sizes = ['B', 'KB', 'MB'];
    const i = Math.floor(Math.log(bytes) / Math.log(k));
    return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
  };

  // Format distance
  const formatDistance = (km: number): string => {
    if (km < 1) {
      return `${Math.round(km * 1000)}m`;
    }
    return `${km.toFixed(2)}km`;
  };

  if (isLoading) {
    return (
      <View className={cn('flex-1 items-center justify-center', className)}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text className="mt-4 text-muted">Loading GPX files...</Text>
      </View>
    );
  }

  return (
    <ScrollView className={cn('flex-1 bg-background', className)}>
      {/* Import Button */}
      <View className="m-4">
        <Pressable
          onPress={handleImportGPX}
          disabled={isImporting}
          className={cn(
            'py-3 rounded-lg items-center justify-center',
            isImporting ? 'bg-border' : 'bg-primary'
          )}
          style={({ pressed }) => [pressed && !isImporting && { opacity: 0.8 }]}
        >
          {isImporting ? (
            <ActivityIndicator size="small" color={colors.background} />
          ) : (
            <Text className="text-background font-semibold">Import GPX File</Text>
          )}
        </Pressable>
      </View>

      {/* GPX Files List */}
      {gpxFiles.length > 0 ? (
        <View className="mx-4 mb-8">
          <Text className="text-foreground font-semibold mb-3">Imported Tracks</Text>

          {gpxFiles.map((file) => {
            const isExpanded = expandedFileId === file.id;
            const isSelected = selectedFileId === file.id;

            return (
              <View
                key={file.id}
                className="bg-surface rounded-lg border border-border mb-2 overflow-hidden"
              >
                {/* File Header */}
                <Pressable
                  onPress={() => setExpandedFileId(isExpanded ? null : file.id)}
                  className="p-3 flex-row items-center justify-between"
                  style={({ pressed }) => [pressed && { opacity: 0.7 }]}
                >
                  <View className="flex-1">
                    <Text className="text-foreground font-semibold">{file.name}</Text>
                    <Text className="text-muted text-xs">
                      {file.trackCount} track(s) • {formatDistance(file.totalDistance)}
                    </Text>
                  </View>

                  <Text className="text-muted text-sm">{isExpanded ? '▼' : '▶'}</Text>
                </Pressable>

                {/* File Details */}
                {isExpanded && (
                  <View className="bg-background/50 px-3 py-2 border-t border-border">
                    <Text className="text-muted text-xs mb-2">
                      Size: {formatBytes(file.fileSize)} • Imported:{' '}
                      {new Date(file.importedAt).toLocaleDateString()}
                    </Text>

                    {/* Action Buttons */}
                    <View className="flex-row gap-2 mt-2">
                      {isSelected ? (
                        <View className="flex-1 py-2 rounded items-center bg-success">
                          <Text className="text-background text-xs font-semibold">Active</Text>
                        </View>
                      ) : (
                        <Pressable
                          onPress={() => handleSelectGPX(file.id)}
                          className="flex-1 py-2 rounded items-center bg-primary"
                          style={({ pressed }) => [pressed && { opacity: 0.8 }]}
                        >
                          <Text className="text-background text-xs font-semibold">Show</Text>
                        </Pressable>
                      )}

                      <Pressable
                        onPress={() => handleDeleteGPX(file.id)}
                        className="py-2 px-3 rounded items-center bg-error"
                        style={({ pressed }) => [pressed && { opacity: 0.8 }]}
                      >
                        <Text className="text-background text-xs font-semibold">Delete</Text>
                      </Pressable>
                    </View>
                  </View>
                )}
              </View>
            );
          })}
        </View>
      ) : (
        <View className="mx-4 p-4 bg-surface rounded-lg border border-border items-center">
          <Text className="text-muted text-center">No GPX files imported yet</Text>
          <Text className="text-muted text-xs text-center mt-2">
            Import GPX files to display tracks on the map
          </Text>
        </View>
      )}
    </ScrollView>
  );
}
