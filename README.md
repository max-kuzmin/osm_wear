# Wear OSM Map

A Wear OS map application for Samsung Galaxy Watch 7 featuring offline OpenStreetMap support, GPX file visualization, and GPS tracking.

## Features

- **Offline Maps:** Download and cache OpenStreetMap tiles for offline use in different regions
- **Interactive Map:** Pan and zoom controls optimized for smartwatch displays
- **GPX Track Support:** Import, view, and manage GPS tracks with distance calculations
- **GPS Tracking:** Real-time location display with accuracy indicators
- **Dark Mode:** Theme switching for comfortable viewing in any lighting condition
- **Wear OS Optimized:** Designed specifically for Samsung Galaxy Watch 7 with circular display support

## Project Structure

```
wear-osm-map/
├── app/                          # Expo Router app structure
│   ├── (tabs)/                   # Tab-based navigation
│   │   ├── index.tsx             # Home screen (map view)
│   │   ├── regions.tsx           # Map regions management
│   │   ├── gpx.tsx               # GPX files management
│   │   └── settings.tsx          # App settings
│   ├── _layout.tsx               # Root layout with providers
│   └── oauth/                    # OAuth callback
├── lib/                          # Core utilities and state
│   ├── map-utils.ts              # OSM tile management
│   ├── gpx-parser.ts             # GPX file parsing
│   ├── map-context.tsx           # Global map state
│   └── theme-provider.tsx        # Theme management
├── components/                   # Reusable components
│   ├── map-canvas.tsx            # Map rendering
│   ├── screen-container.tsx      # SafeArea wrapper
│   └── ui/                       # UI components
├── design.md                     # UI/UX design document
├── todo.md                       # Project task tracking
└── app.config.ts                 # Expo configuration
```

## Tech Stack

- **Framework:** React Native with Expo SDK 54
- **Language:** TypeScript
- **Styling:** NativeWind (Tailwind CSS)
- **State Management:** React Context + useReducer
- **Map Rendering:** Custom canvas-based OSM tile renderer
- **Location:** expo-location for GPS tracking
- **File Management:** expo-file-system for offline storage
- **File Picker:** expo-document-picker for GPX import

## Getting Started

### Prerequisites

- Node.js 18+ and pnpm
- Expo CLI
- Android SDK (for building APK)
- Java Development Kit (JDK 11+)

### Installation

```bash
# Install dependencies
pnpm install

# Start development server
pnpm dev

# Build for Android
eas build --platform android

# Or build APK locally
pnpm build
```

### Development

```bash
# Start Metro bundler
pnpm dev:metro

# In another terminal, start the API server
pnpm dev:server
```

## Usage

### Home Screen (Map View)

- **Pan:** Drag to move the map
- **Zoom:** Use +/- buttons or rotary dial (on watch)
- **Current Location:** Tap the location button to recenter on your position
- **Menu:** Tap the menu button to toggle info display

### Map Regions

1. Navigate to the "Regions" tab
2. Select a region to download offline maps
3. Monitor download progress
4. Tap "Select" to activate a region
5. Use "Delete" to remove cached regions

### GPX Files

1. Navigate to the "GPX Files" tab
2. Tap "+ Import GPX File" to select a file from your device
3. Tap "Load" to display the track on the map
4. View track statistics (distance, points, tracks)
5. Tap "Delete" to remove files

### Settings

- **GPS Tracking:** Enable/disable real-time location updates
- **Dark Mode:** Toggle between light and dark themes
- **Storage:** View cache size and GPX file count
- **Clear Cache:** Remove all cached map tiles

## Building for Wear OS

### Build APK with EAS

```bash
# Build for Android (includes Wear OS support)
eas build --platform android --profile preview

# Build for production
eas build --platform android --profile production
```

### Local Build

```bash
# Generate native code
npx expo prebuild --clean

# Build APK
cd android
./gradlew assembleRelease
```

## Architecture

### Map Engine

The map engine uses a tile-based rendering system:

1. **Tile Coordinates:** Geographic coordinates are converted to tile coordinates using Web Mercator projection
2. **Caching:** Tiles are cached locally in the app's cache directory
3. **Rendering:** Tiles are rendered on a canvas with support for overlays (GPX tracks, location indicator)

### State Management

Global map state is managed through React Context:

- **Location:** Current GPS position and accuracy
- **Map Region:** Current viewport (latitude, longitude, zoom level)
- **Offline Regions:** Downloaded map regions and selection
- **GPX Data:** Loaded GPX tracks and file metadata

### File Storage

- **Tiles:** Cached in `FileSystem.cacheDirectory/osm-tiles/`
- **GPX Files:** Stored in `FileSystem.documentDirectory/gpx-files/`
- **Settings:** Persisted via AsyncStorage

## Performance Considerations

- **Tile Caching:** Tiles are cached to reduce network requests
- **GPS Polling:** Location updates are throttled to 5-second intervals
- **Memory Management:** Large GPX files are processed incrementally
- **Battery:** GPS is only active when explicitly enabled

## Future Enhancements

- [ ] Full OSM tile server integration with background downloads
- [ ] Offline routing and navigation
- [ ] Elevation profiles for GPX tracks
- [ ] Waypoint markers and annotations
- [ ] Multi-track comparison
- [ ] Compass integration
- [ ] Voice control support
- [ ] Circular display optimization for ambient mode

## Contributing

Contributions are welcome! Please follow these guidelines:

1. Create a feature branch (`git checkout -b feature/amazing-feature`)
2. Commit your changes (`git commit -m 'Add amazing feature'`)
3. Push to the branch (`git push origin feature/amazing-feature`)
4. Open a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues, questions, or suggestions, please open an issue on GitHub.

---

**Built with ❤️ for outdoor enthusiasts and adventurers**
