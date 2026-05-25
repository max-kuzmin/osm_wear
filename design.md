# Wear OSM Map - Design Document

## Overview

A Wear OS map application optimized for Samsung Galaxy Watch 7 (circular display, ~1.4" screen). The app provides offline OpenStreetMap navigation with GPX track visualization and GPS tracking.

---

## Screen List

1. **Home Screen** - Main map view with controls
2. **Map Regions** - Download/manage offline map regions
3. **GPX Files** - Browse and load GPX tracks
4. **Settings** - App configuration and preferences

---

## Design Constraints

- **Display**: Circular Wear OS (Samsung Galaxy Watch 7)
- **Orientation**: Always portrait (watch face orientation)
- **Interaction**: Rotary input, touch, physical buttons
- **Performance**: Minimal battery drain, efficient tile rendering
- **Offline-first**: All maps and data stored locally

---

## Screen Details

### 1. Home Screen (Map View)

**Primary Content:**
- Full-screen map canvas (OpenStreetMap tiles)
- Current location indicator (blue dot with accuracy circle)
- Zoom controls (+ / - buttons, rotary input support)
- GPX track overlay (if loaded)
- Scale/legend indicator

**Functionality:**
- Pan map by dragging
- Zoom in/out with buttons or rotary dial
- Tap current location to recenter
- Long-press to show coordinates
- Swipe to access quick menu

**Color Scheme:**
- Map background: #F5F5F5 (light), #1E1E1E (dark)
- Current location: #0A7EA4 (primary blue)
- GPX track: #22C55E (green)
- UI controls: Semi-transparent overlays

### 2. Map Regions Screen

**Primary Content:**
- List of available offline map regions (scrollable)
- Each region shows: name, size, download status
- Download progress indicator
- Available storage indicator

**Functionality:**
- Tap to download/select region
- Long-press to delete region
- Swipe to refresh available regions
- Show download progress with cancel option

**Layout:**
- Circular list optimized for rotary input
- Compact card layout (region name + status)

### 3. GPX Files Screen

**Primary Content:**
- List of GPX files stored locally
- File info: name, track points count, distance
- Active track indicator

**Functionality:**
- Tap to load/view GPX track
- Long-press to delete file
- Import new GPX file via file picker
- Show track statistics (distance, elevation if available)

**Layout:**
- Vertical scrollable list
- Compact cards with essential info

### 4. Settings Screen

**Primary Content:**
- Theme toggle (light/dark)
- GPS accuracy preference
- Map tile cache size
- About & version info

**Functionality:**
- Toggle dark mode
- Adjust GPS update frequency
- Clear cache
- View app version and credits

---

## Key User Flows

### Flow 1: First Launch & Region Download

1. User opens app → Home Screen (empty map)
2. Tap menu → Navigate to Map Regions
3. Select region (e.g., "Europe - Central")
4. Tap download → Progress indicator
5. Download completes → Region available for map
6. Return to Home Screen → Map displays tiles

### Flow 2: Load & View GPX Track

1. From Home Screen, tap menu → GPX Files
2. Tap "Import" → File picker
3. Select GPX file from device storage
4. File loads → Track displayed on map (green line)
5. Tap track → Show statistics (distance, points)
6. Return to map view with track overlay

### Flow 3: Navigate with GPS

1. Home Screen shows map with current location (blue dot)
2. Tap current location button → Map recenters on user
3. Accuracy circle shows GPS precision
4. Zoom in/out to explore
5. Pan map to see surrounding areas
6. GPS updates continuously (if enabled)

---

## Color Palette

| Element | Light Mode | Dark Mode | Usage |
|---------|-----------|----------|-------|
| Background | #FFFFFF | #151718 | Screen background |
| Map Base | #F5F5F5 | #1E1E1E | Map tile background |
| Primary | #0A7EA4 | #0A7EA4 | Current location, active controls |
| Success | #22C55E | #4ADE80 | GPX tracks, positive actions |
| Text | #11181C | #ECEDEE | Primary text |
| Text Muted | #687076 | #9BA1A6 | Secondary text, labels |
| Border | #E5E7EB | #334155 | Dividers, borders |

---

## Wear OS Specific Considerations

- **Rotary Input**: Support rotary dial for zoom and list scrolling
- **Ambient Mode**: Simplified map display with reduced detail
- **Battery**: Minimize GPS polling, use efficient tile caching
- **Circular UI**: Avoid corner-based layouts, use center-focused design
- **Touch**: Large tap targets (min 48dp), avoid small buttons
- **Always-On Display**: Support always-on map view with low refresh rate

---

## Technical Architecture

### Data Flow

```
User Input (Touch/Rotary)
    ↓
Map Controller (Pan, Zoom, GPS)
    ↓
Tile Renderer (OSM tiles from cache)
    ↓
Canvas Display
    ↓
GPX Overlay (if loaded)
```

### Storage

- **Offline Tiles**: Cached in app filesystem (SQLite or file-based)
- **GPX Files**: Stored in app documents directory
- **Settings**: AsyncStorage or secure storage
- **GPS Data**: Real-time only, not persisted

---

## Accessibility

- High contrast mode support
- Large text options
- Haptic feedback for actions
- Voice control integration (future)

---

## Future Enhancements

- Offline routing/navigation
- Elevation profiles for GPX tracks
- Waypoint markers and annotations
- Multi-track comparison
- Export/share GPX files
- Compass integration
- Altitude display
