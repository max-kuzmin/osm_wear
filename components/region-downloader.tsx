/**
 * Region Downloader Component
 * 
 * Displays available regions and manages downloads
 * Shows progress, storage usage, and region management
 */

import React, { useEffect, useState, useCallback } from 'react';
import {
  View,
  Text,
  ScrollView,
  Pressable,
  ActivityIndicator,
  FlatList,
  Alert,
} from 'react-native';
import { getAllRegions, getRegionById, type MapRegion } from '@/lib/protomaps-regions';
import {
  downloadRegion,
  getDownloadedRegions,
  removeDownloadedRegion,
  getStorageStats,
  formatBytes,
  type DownloadProgress,
  type StoredRegion,
} from '@/lib/download-manager';
import { useMapContext } from '@/lib/map-context';
import { useColors } from '@/hooks/use-colors';
import { cn } from '@/lib/utils';

interface RegionDownloaderProps {
  className?: string;
  onRegionSelected?: (regionId: string) => void;
}

export function RegionDownloader({ className, onRegionSelected }: RegionDownloaderProps) {
  const colors = useColors();
  const { mapState, updateMapState } = useMapContext();
  const [regions, setRegions] = useState<MapRegion[]>([]);
  const [downloadedRegions, setDownloadedRegions] = useState<StoredRegion[]>([]);
  const [downloadProgress, setDownloadProgress] = useState<Record<string, DownloadProgress>>({});
  const [storageStats, setStorageStats] = useState({ totalCacheSize: 0, downloadedRegions: 0 });
  const [isLoading, setIsLoading] = useState(true);
  const [activeDownloads, setActiveDownloads] = useState<Set<string>>(new Set());

  // Load regions and storage info
  useEffect(() => {
    const loadData = async () => {
      try {
        setIsLoading(true);
        const allRegions = getAllRegions();
        const downloaded = await getDownloadedRegions();
        const stats = await getStorageStats();

        setRegions(allRegions);
        setDownloadedRegions(downloaded);
        setStorageStats(stats);
      } catch (error) {
        console.error('Failed to load regions:', error);
      } finally {
        setIsLoading(false);
      }
    };

    loadData();
  }, []);

  // Handle region download
  const handleDownloadRegion = useCallback(
    async (regionId: string) => {
      try {
        setActiveDownloads((prev) => new Set([...prev, regionId]));

        await downloadRegion(regionId, (progress) => {
          setDownloadProgress((prev) => ({
            ...prev,
            [regionId]: progress,
          }));
        });

        // Refresh downloaded regions
        const downloaded = await getDownloadedRegions();
        setDownloadedRegions(downloaded);

        // Refresh storage stats
        const stats = await getStorageStats();
        setStorageStats(stats);

        Alert.alert('Success', `${getRegionById(regionId)?.name} downloaded successfully`);
      } catch (error) {
        const errorMessage = error instanceof Error ? error.message : 'Unknown error';
        Alert.alert('Download Failed', errorMessage);
      } finally {
        setActiveDownloads((prev) => {
          const next = new Set(prev);
          next.delete(regionId);
          return next;
        });
      }
    },
    []
  );

  // Handle region deletion
  const handleDeleteRegion = useCallback(
    async (regionId: string) => {
      Alert.alert(
        'Delete Region',
        'Are you sure you want to delete this offline map?',
        [
          { text: 'Cancel', onPress: () => {} },
          {
            text: 'Delete',
            onPress: async () => {
              try {
                await removeDownloadedRegion(regionId);

                // Refresh downloaded regions
                const downloaded = await getDownloadedRegions();
                setDownloadedRegions(downloaded);

                // Refresh storage stats
                const stats = await getStorageStats();
                setStorageStats(stats);

                Alert.alert('Success', 'Region deleted');
              } catch (error) {
                Alert.alert('Error', 'Failed to delete region');
              }
            },
          },
        ]
      );
    },
    []
  );

  // Handle region selection
  const handleSelectRegion = useCallback(
    (regionId: string) => {
      updateMapState({ selectedRegionId: regionId });
      onRegionSelected?.(regionId);
    },
    [updateMapState, onRegionSelected]
  );

  if (isLoading) {
    return (
      <View className={cn('flex-1 items-center justify-center', className)}>
        <ActivityIndicator size="large" color={colors.primary} />
        <Text className="mt-4 text-muted">Loading regions...</Text>
      </View>
    );
  }

  return (
    <ScrollView className={cn('flex-1 bg-background', className)}>
      {/* Storage Stats */}
      <View className="bg-surface m-4 p-4 rounded-lg border border-border">
        <Text className="text-foreground font-semibold mb-2">Storage Usage</Text>
        <Text className="text-muted text-sm">
          Downloaded: {storageStats.downloadedRegions} region(s)
        </Text>
        <Text className="text-muted text-sm">
          Used: {formatBytes(storageStats.totalCacheSize)}
        </Text>
      </View>

      {/* Downloaded Regions */}
      {downloadedRegions.length > 0 && (
        <View className="mx-4 mb-4">
          <Text className="text-foreground font-semibold mb-3">Downloaded Maps</Text>
          {downloadedRegions.map((region) => (
            <View
              key={region.id}
              className="bg-surface p-3 rounded-lg border border-border mb-2 flex-row items-center justify-between"
            >
              <View className="flex-1">
                <Text className="text-foreground font-semibold">{region.name}</Text>
                <Text className="text-muted text-xs">
                  {formatBytes(region.fileSize)} • Zoom: {region.maxZoom}
                </Text>
              </View>

              <View className="flex-row gap-2">
                {mapState.selectedRegionId === region.id && (
                  <View className="bg-success px-2 py-1 rounded">
                    <Text className="text-background text-xs font-semibold">Active</Text>
                  </View>
                )}

                {mapState.selectedRegionId !== region.id && (
                  <Pressable
                    onPress={() => handleSelectRegion(region.id)}
                    className="bg-primary px-3 py-1 rounded"
                    style={({ pressed }) => [pressed && { opacity: 0.8 }]}
                  >
                    <Text className="text-background text-xs font-semibold">Select</Text>
                  </Pressable>
                )}

                <Pressable
                  onPress={() => handleDeleteRegion(region.id)}
                  className="bg-error px-3 py-1 rounded"
                  style={({ pressed }) => [pressed && { opacity: 0.8 }]}
                >
                  <Text className="text-background text-xs font-semibold">Delete</Text>
                </Pressable>
              </View>
            </View>
          ))}
        </View>
      )}

      {/* Available Regions */}
      <View className="mx-4 mb-4">
        <Text className="text-foreground font-semibold mb-3">Available Regions</Text>

        {regions.map((region) => {
          const isDownloaded = downloadedRegions.some((r) => r.id === region.id);
          const progress = downloadProgress[region.id];
          const isDownloading = activeDownloads.has(region.id);

          return (
            <View
              key={region.id}
              className="bg-surface p-3 rounded-lg border border-border mb-2"
            >
              <View className="flex-row items-start justify-between mb-2">
                <View className="flex-1">
                  <Text className="text-foreground font-semibold">{region.name}</Text>
                  <Text className="text-muted text-xs">{region.description}</Text>
                </View>
              </View>

              <View className="mb-2">
                <View className="flex-row justify-between mb-1">
                  <Text className="text-muted text-xs">
                    {region.estimatedSize}MB • ~{region.estimatedDownloadTime}min
                  </Text>
                  <Text className="text-muted text-xs">Zoom: {region.maxZoom}</Text>
                </View>
              </View>

              {/* Progress Bar */}
              {isDownloading && progress && (
                <View className="mb-2">
                  <View className="h-2 bg-border rounded-full overflow-hidden">
                    <View
                      className="h-full bg-primary"
                      style={{ width: `${progress.progress * 100}%` }}
                    />
                  </View>
                  <Text className="text-muted text-xs mt-1">
                    {(progress.progress * 100).toFixed(0)}% •{' '}
                    {formatBytes(progress.bytesDownloaded)} / {formatBytes(progress.totalBytes)}
                  </Text>
                </View>
              )}

              {/* Action Buttons */}
              <View className="flex-row gap-2">
                {!isDownloaded ? (
                  <Pressable
                    onPress={() => handleDownloadRegion(region.id)}
                    disabled={isDownloading}
                    className={cn(
                      'flex-1 py-2 rounded items-center justify-center',
                      isDownloading ? 'bg-border' : 'bg-primary'
                    )}
                    style={({ pressed }) => [pressed && !isDownloading && { opacity: 0.8 }]}
                  >
                    {isDownloading ? (
                      <ActivityIndicator size="small" color={colors.background} />
                    ) : (
                      <Text className="text-background font-semibold">Download</Text>
                    )}
                  </Pressable>
                ) : (
                  <View className="flex-1 py-2 rounded items-center justify-center bg-success">
                    <Text className="text-background font-semibold">Downloaded</Text>
                  </View>
                )}
              </View>
            </View>
          );
        })}
      </View>

      {/* Info Section */}
      <View className="mx-4 mb-8 p-3 bg-surface rounded-lg border border-border">
        <Text className="text-foreground font-semibold mb-2">About Offline Maps</Text>
        <Text className="text-muted text-xs leading-relaxed">
          Download map regions to use offline. Each region includes detailed map data for the
          selected area. Larger regions take longer to download but provide more coverage.
        </Text>
        <Text className="text-muted text-xs mt-2 leading-relaxed">
          Data: © OpenStreetMap contributors • Tiles: Protomaps
        </Text>
      </View>
    </ScrollView>
  );
}

/**
 * Region List Item Component
 */
interface RegionListItemProps {
  region: MapRegion;
  isDownloaded: boolean;
  isDownloading: boolean;
  progress?: DownloadProgress;
  onDownload: (regionId: string) => void;
  onDelete: (regionId: string) => void;
  onSelect: (regionId: string) => void;
  isSelected: boolean;
}

export function RegionListItem({
  region,
  isDownloaded,
  isDownloading,
  progress,
  onDownload,
  onDelete,
  onSelect,
  isSelected,
}: RegionListItemProps) {
  const colors = useColors();

  return (
    <View className="bg-surface p-3 rounded-lg border border-border mb-2">
      <View className="flex-row items-start justify-between mb-2">
        <View className="flex-1">
          <Text className="text-foreground font-semibold">{region.name}</Text>
          <Text className="text-muted text-xs">{region.description}</Text>
        </View>
      </View>

      {isDownloading && progress && (
        <View className="mb-2">
          <View className="h-2 bg-border rounded-full overflow-hidden">
            <View
              className="h-full bg-primary"
              style={{ width: `${progress.progress * 100}%` }}
            />
          </View>
          <Text className="text-muted text-xs mt-1">
            {(progress.progress * 100).toFixed(0)}%
          </Text>
        </View>
      )}

      <View className="flex-row gap-2">
        {!isDownloaded ? (
          <Pressable
            onPress={() => onDownload(region.id)}
            disabled={isDownloading}
            className={cn(
              'flex-1 py-2 rounded items-center',
              isDownloading ? 'bg-border' : 'bg-primary'
            )}
          >
            {isDownloading ? (
              <ActivityIndicator size="small" color={colors.background} />
            ) : (
              <Text className="text-background font-semibold">Download</Text>
            )}
          </Pressable>
        ) : (
          <>
            {isSelected ? (
              <View className="flex-1 py-2 rounded items-center bg-success">
                <Text className="text-background font-semibold">Active</Text>
              </View>
            ) : (
              <Pressable
                onPress={() => onSelect(region.id)}
                className="flex-1 py-2 rounded items-center bg-primary"
              >
                <Text className="text-background font-semibold">Select</Text>
              </Pressable>
            )}

            <Pressable
              onPress={() => onDelete(region.id)}
              className="py-2 px-3 rounded items-center bg-error"
            >
              <Text className="text-background font-semibold">Delete</Text>
            </Pressable>
          </>
        )}
      </View>
    </View>
  );
}
