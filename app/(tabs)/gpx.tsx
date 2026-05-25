/**
 * GPX Files Screen - Import and manage GPX tracks
 */

import React, { useState } from 'react';
import { ScreenContainer } from '@/components/screen-container';
import { GPXManager } from '@/components/gpx-manager';
import { useMapContext } from '@/lib/map-context';
import { type GPXTrack } from '@/lib/gpx-parser';

export default function GPXScreen() {
  const { updateMapState } = useMapContext();
  const [selectedFileId, setSelectedFileId] = useState<string>();

  const handleTracksSelected = (tracks: GPXTrack[]) => {
    // Update map context with selected tracks
    updateMapState({ gpxTracks: tracks });
  };

  return (
    <ScreenContainer className="flex-1 bg-background p-0">
      <GPXManager 
        className="flex-1" 
        onTracksSelected={handleTracksSelected}
        selectedFileId={selectedFileId}
      />
    </ScreenContainer>
  );
}
