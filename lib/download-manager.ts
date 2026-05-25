/**
 * Download Manager for Protomaps Regions
 * 
 * Handles downloading, storing, and managing offline map tiles
 * Supports resume, progress tracking, and error handling
 */

import * as FileSystem from 'expo-file-system/legacy';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { getRegionById, type MapRegion } from './protomaps-regions';

export interface DownloadProgress {
  regionId: string;
  bytesDownloaded: number;
  totalBytes: number;
  progress: number; // 0-1
  status: 'downloading' | 'completed' | 'failed' | 'paused';
  error?: string;
}

export interface StoredRegion {
  id: string;
  name: string;
  filePath: string;
  fileSize: number;
  downloadedAt: number;
  maxZoom: number;
}

const TILES_CACHE_DIR = `${FileSystem.cacheDirectory}tiles/`;
const DOWNLOADS_STORAGE_KEY = 'osm_downloaded_regions';
const DOWNLOAD_PROGRESS_KEY = 'osm_download_progress';

/**
 * Initialize cache directory
 */
export async function initializeCacheDirectory(): Promise<void> {
  try {
    const dirInfo = await FileSystem.getInfoAsync(TILES_CACHE_DIR);
    if (!dirInfo.exists) {
      await FileSystem.makeDirectoryAsync(TILES_CACHE_DIR, { intermediates: true });
    }
  } catch (error) {
    console.error('Failed to initialize cache directory:', error);
  }
}

/**
 * Get path where a region's PMTiles file should be stored
 */
export function getRegionFilePath(regionId: string): string {
  return `${TILES_CACHE_DIR}${regionId}.pmtiles`;
}

/**
 * Get list of downloaded regions
 */
export async function getDownloadedRegions(): Promise<StoredRegion[]> {
  try {
    const data = await AsyncStorage.getItem(DOWNLOADS_STORAGE_KEY);
    return data ? JSON.parse(data) : [];
  } catch (error) {
    console.error('Failed to get downloaded regions:', error);
    return [];
  }
}

/**
 * Save downloaded region metadata
 */
async function saveDownloadedRegion(region: StoredRegion): Promise<void> {
  try {
    const regions = await getDownloadedRegions();
    const index = regions.findIndex(r => r.id === region.id);
    if (index >= 0) {
      regions[index] = region;
    } else {
      regions.push(region);
    }
    await AsyncStorage.setItem(DOWNLOADS_STORAGE_KEY, JSON.stringify(regions));
  } catch (error) {
    console.error('Failed to save downloaded region:', error);
  }
}

/**
 * Remove downloaded region
 */
export async function removeDownloadedRegion(regionId: string): Promise<void> {
  try {
    const filePath = getRegionFilePath(regionId);
    const fileInfo = await FileSystem.getInfoAsync(filePath);
    if (fileInfo.exists) {
      await FileSystem.deleteAsync(filePath);
    }

    const regions = await getDownloadedRegions();
    const filtered = regions.filter(r => r.id !== regionId);
    await AsyncStorage.setItem(DOWNLOADS_STORAGE_KEY, JSON.stringify(filtered));
  } catch (error) {
    console.error('Failed to remove downloaded region:', error);
  }
}

/**
 * Check if a region is already downloaded
 */
export async function isRegionDownloaded(regionId: string): Promise<boolean> {
  try {
    const filePath = getRegionFilePath(regionId);
    const fileInfo = await FileSystem.getInfoAsync(filePath);
    return fileInfo.exists;
  } catch (error) {
    console.error('Failed to check if region is downloaded:', error);
    return false;
  }
}

/**
 * Get cache directory size in bytes
 */
export async function getCacheDirSize(): Promise<number> {
  try {
    const files = await FileSystem.readDirectoryAsync(TILES_CACHE_DIR);
    let totalSize = 0;

    for (const file of files) {
      const filePath = `${TILES_CACHE_DIR}${file}`;
      const fileInfo = await FileSystem.getInfoAsync(filePath);
      if (fileInfo.exists && (fileInfo as any).size) {
        totalSize += (fileInfo as any).size;
      }
    }

    return totalSize;
  } catch (error) {
    console.error('Failed to get cache directory size:', error);
    return 0;
  }
}

/**
 * Format bytes to human-readable size
 */
export function formatBytes(bytes: number): string {
  if (bytes === 0) return '0 B';
  const k = 1024;
  const sizes = ['B', 'KB', 'MB', 'GB'];
  const i = Math.floor(Math.log(bytes) / Math.log(k));
  return Math.round((bytes / Math.pow(k, i)) * 100) / 100 + ' ' + sizes[i];
}

/**
 * Download a region's PMTiles file
 * 
 * @param regionId - ID of the region to download
 * @param onProgress - Callback for progress updates
 * @returns Promise that resolves when download is complete
 */
export async function downloadRegion(
  regionId: string,
  onProgress?: (progress: DownloadProgress) => void
): Promise<void> {
  const region = getRegionById(regionId);
  if (!region) {
    throw new Error(`Region not found: ${regionId}`);
  }

  const filePath = getRegionFilePath(regionId);

  try {
    // Initialize cache directory
    await initializeCacheDirectory();

    // Check if already downloaded
    const isDownloaded = await isRegionDownloaded(regionId);
    if (isDownloaded) {
      onProgress?.({
        regionId,
        bytesDownloaded: region.estimatedSize * 1024 * 1024,
        totalBytes: region.estimatedSize * 1024 * 1024,
        progress: 1,
        status: 'completed',
      });
      return;
    }

    // Create download resumable
    const downloadResumable = FileSystem.createDownloadResumable(
      region.pmtilesUrl,
      filePath,
      {},
      (progress) => {
        const { totalBytesWritten, totalBytesExpectedToWrite } = progress;
        const progressPercent = totalBytesWritten / totalBytesExpectedToWrite;

        onProgress?.({
          regionId,
          bytesDownloaded: totalBytesWritten,
          totalBytes: totalBytesExpectedToWrite,
          progress: progressPercent,
          status: 'downloading',
        });
      }
    );

    // Start download
    const result = await downloadResumable.downloadAsync();

    if (result && result.status === 200) {
      // Get actual file size
      const fileInfo = await FileSystem.getInfoAsync(filePath);
      const fileSize = (fileInfo as any).size || (region as any).estimatedSize * 1024 * 1024;

      // Save metadata
      await saveDownloadedRegion({
        id: regionId,
        name: region.name,
        filePath,
        fileSize,
        downloadedAt: Date.now(),
        maxZoom: region.maxZoom,
      });

      onProgress?.({
        regionId,
        bytesDownloaded: fileSize,
        totalBytes: fileSize,
        progress: 1,
        status: 'completed',
      });
    } else {
      throw new Error(`Download failed with status: ${result?.status}`);
    }
  } catch (error) {
    const errorMessage = error instanceof Error ? error.message : 'Unknown error';

    onProgress?.({
      regionId,
      bytesDownloaded: 0,
      totalBytes: region.estimatedSize * 1024 * 1024,
      progress: 0,
      status: 'failed',
      error: errorMessage,
    });

    throw error;
  }
}

/**
 * Clear all cached tiles
 */
export async function clearAllCache(): Promise<void> {
  try {
    const dirInfo = await FileSystem.getInfoAsync(TILES_CACHE_DIR);
    if (dirInfo.exists) {
      await FileSystem.deleteAsync(TILES_CACHE_DIR);
    }
    await AsyncStorage.removeItem(DOWNLOADS_STORAGE_KEY);
  } catch (error) {
    console.error('Failed to clear cache:', error);
  }
}

/**
 * Get download progress for a region
 */
export async function getDownloadProgress(regionId: string): Promise<DownloadProgress | null> {
  try {
    const data = await AsyncStorage.getItem(`${DOWNLOAD_PROGRESS_KEY}_${regionId}`);
    return data ? JSON.parse(data) : null;
  } catch (error) {
    console.error('Failed to get download progress:', error);
    return null;
  }
}

/**
 * Save download progress
 */
async function saveDownloadProgress(progress: DownloadProgress): Promise<void> {
  try {
    await AsyncStorage.setItem(
      `${DOWNLOAD_PROGRESS_KEY}_${progress.regionId}`,
      JSON.stringify(progress)
    );
  } catch (error) {
    console.error('Failed to save download progress:', error);
  }
}

/**
 * Get storage statistics
 */
export async function getStorageStats(): Promise<{
  totalCacheSize: number;
  downloadedRegions: number;
  regions: StoredRegion[];
}> {
  const regions = await getDownloadedRegions();
  const totalCacheSize = await getCacheDirSize();

  return {
    totalCacheSize,
    downloadedRegions: regions.length,
    regions,
  };
}
