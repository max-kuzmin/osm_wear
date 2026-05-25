/**
 * Map Regions Screen - Download and manage offline map regions
 */

import React from 'react';
import { ScreenContainer } from '@/components/screen-container';
import { RegionDownloader } from '@/components/region-downloader';

export default function RegionsScreen() {
  return (
    <ScreenContainer className="flex-1 bg-background p-0">
      <RegionDownloader className="flex-1" />
    </ScreenContainer>
  );
}
