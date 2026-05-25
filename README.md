# Wear OSM Map - Offline Maps for Wear OS

A production-ready Wear OS application for Samsung Galaxy Watch 7 that enables offline OpenStreetMap navigation, GPX track visualization, and GPS tracking. Built with React Native, Expo, and MapLibre GL.

## Features

- **Offline Map Downloads**: Download OpenStreetMap regions from Protomaps (6 continents + 13 subregions)
- **MapLibre GL Rendering**: Vector tile rendering with smooth zoom and pan controls
- **GPX Track Support**: Import, parse, and visualize GPX files with distance calculations
- **GPS Location Tracking**: Real-time location display with blue dot marker and recenter button
- **Wear OS Optimized**: Tab-based navigation, dark mode, and compact layouts for smartwatch displays
- **No API Keys Required**: All tile sources are free and open-source (noncommercial use)

## Architecture

```
wear-osm-map/
├── app/                          # Expo Router screens
│   ├── (tabs)/
│   │   ├── index.tsx            # Home screen (map view)
│   │   ├── regions.tsx          # Region downloader
│   │   ├── gpx.tsx              # GPX file manager
│   │   └── settings.tsx         # App settings
│   └── _layout.tsx              # Root layout with providers
├── lib/                          # Core functionality
│   ├── map-context.tsx          # Global map state management
│   ├── maplibre-integration.ts  # MapLibre utilities
│   ├── protomaps-regions.ts     # Region definitions
│   ├── download-manager.ts      # Tile download management
│   ├── gpx-parser.ts            # GPX file parsing
│   ├── pmtiles-protocol.ts      # PMTiles protocol setup
│   └── map-utils.ts             # Coordinate utilities
├── components/
│   ├── maplibre-map.tsx         # Map canvas component
│   ├── gpx-manager.tsx          # GPX file manager UI
│   ├── region-downloader.tsx    # Region download UI
│   └── screen-container.tsx     # SafeArea wrapper
└── package.json
```

## Tech Stack

- **React Native 0.81** with Expo SDK 54
- **TypeScript 5.9** for type safety
- **MapLibre GL** for vector tile rendering
- **Expo Router 6** for navigation
- **NativeWind 4** (Tailwind CSS) for styling
- **AsyncStorage** for local persistence
- **expo-location** for GPS tracking
- **expo-document-picker** for GPX file import

## Getting Started

### Prerequisites

- Node.js 18+ and pnpm
- Android SDK (for local APK builds) or Expo Go app on Wear OS device
- Git

### Local Development

1. **Clone the repository**:
   ```bash
   git clone https://github.com/max-kuzmin/osm_wear.git
   cd osm_wear
   ```

2. **Install dependencies**:
   ```bash
   pnpm install
   ```

3. **Start the development server**:
   ```bash
   pnpm dev
   ```

   This starts both the Metro bundler (port 8081) and the backend API server (port 3000).

4. **Test in Expo Go**:
   - Install Expo Go on your Wear OS device
   - Scan the QR code displayed in the terminal
   - App will load and hot-reload on code changes

5. **Build for local testing**:
   ```bash
   # Android
   pnpm android
   
   # iOS (macOS only)
   pnpm ios
   
   # Web (for development/testing)
   pnpm dev:metro
   ```

### Building Locally (APK)

#### Prerequisites for Local Builds

- Android SDK (API 24+)
- Java Development Kit (JDK 11+)
- Gradle

#### Build Debug APK

```bash
# Install Expo CLI globally (if not already installed)
npm install -g expo-cli

# Build debug APK
eas build --platform android --local

# Or use Gradle directly
cd android
./gradlew assembleDebug
cd ..
```

The APK will be generated in `android/app/build/outputs/apk/debug/app-debug.apk`.

#### Build Release APK

```bash
# Build release APK (requires signing configuration)
eas build --platform android --local --release

# Or with Gradle
cd android
./gradlew assembleRelease
cd ..
```

#### Install on Device

```bash
# Connect your Wear OS device via USB
adb install path/to/app-debug.apk

# Or use Expo
expo install:android
```

## Building with GitHub Actions

### Setup

1. **Create GitHub Secrets**:
   - Go to repository Settings → Secrets and variables → Actions
   - Add the following secrets:

   | Secret | Description |
   |--------|-------------|
   | `EXPO_TOKEN` | Get from `eas login` then `eas secrets:create` |
   | `ANDROID_KEYSTORE_BASE64` | Base64-encoded keystore file (optional, for release builds) |
   | `ANDROID_KEYSTORE_PASSWORD` | Keystore password (optional) |
   | `ANDROID_KEY_ALIAS` | Key alias (optional) |
   | `ANDROID_KEY_PASSWORD` | Key password (optional) |

2. **GitHub Actions Workflows**:

   The repository includes two workflow options:

   **Option A: Local Gradle Builds** (`.github/workflows/build-apk.yml`)
   - Builds APK directly using Android Gradle
   - Fastest for testing
   - No external dependencies
   - Triggers on push to `main` or `develop`

   **Option B: EAS Builds** (`.github/workflows/eas-build.yml`)
   - Uses Expo's managed build service
   - Better for production
   - Handles signing automatically
   - Requires `EXPO_TOKEN`

### Triggering Builds

#### Automatic Builds

Builds run automatically on:
- Push to `main` branch
- Push to `develop` branch
- Push of version tags (e.g., `v1.0.0`)

#### Manual Builds

Trigger builds manually from GitHub Actions tab:

```bash
# Using GitHub CLI
gh workflow run build-apk.yml --ref main

# Or via web UI:
# 1. Go to Actions tab
# 2. Select workflow
# 3. Click "Run workflow"
```

### Workflow Configuration

#### Local Gradle Workflow

```yaml
# .github/workflows/build-apk.yml
name: Build APK (Local Gradle)

on:
  push:
    branches: [main, develop]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      - run: npm install -g pnpm
      - run: pnpm install
      - run: pnpm build
      - uses: actions/upload-artifact@v3
        with:
          name: apk
          path: dist/app-release.apk
```

#### EAS Workflow

```yaml
# .github/workflows/eas-build.yml
name: Build with EAS

on:
  push:
    branches: [main, develop]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v3
      - uses: actions/setup-node@v3
        with:
          node-version: '18'
      - run: npm install -g eas-cli
      - run: eas build --platform android --non-interactive
        env:
          EXPO_TOKEN: ${{ secrets.EXPO_TOKEN }}
```

### Downloading Builds

1. **From GitHub Actions**:
   - Go to Actions tab
   - Select the completed workflow run
   - Download artifact from "Artifacts" section

2. **From GitHub Releases** (if configured):
   - Go to Releases tab
   - Download APK from release assets

3. **Using GitHub CLI**:
   ```bash
   # List recent workflow runs
   gh run list --workflow build-apk.yml
   
   # Download artifact from specific run
   gh run download <run-id> -n apk
   ```

## Configuration

### App Configuration

Edit `app.config.ts` to customize:

```typescript
const env = {
  appName: "Wear OSM Map",           // App display name
  appSlug: "wear-osm-map",           // Unique identifier
  logoUrl: "",                       // S3 URL of app icon
  scheme: "manus20240115103045",     // Deep link scheme
  iosBundleId: "space.manus.wear.osm.map.t...",
  androidPackage: "space.manus.wear.osm.map.t...",
};
```

### Theme Customization

Edit `theme.config.js` to customize colors:

```javascript
const themeColors = {
  primary: { light: '#0a7ea4', dark: '#0a7ea4' },
  background: { light: '#ffffff', dark: '#151718' },
  surface: { light: '#f5f5f5', dark: '#1e2022' },
  // ... more colors
};
```

## Tile Sources

### Protomaps (Recommended)

- **Format**: PMTiles (single-file vector tiles)
- **License**: ODbL (noncommercial use)
- **Attribution**: © OpenStreetMap contributors
- **Regions**: 6 continents + 13 subregions
- **Size**: 500MB - 2.5GB per region
- **Source**: https://maps.protomaps.com/builds/

### Available Regions

| Region | Size | Zoom Levels |
|--------|------|-------------|
| North America | ~2.5GB | 0-14 |
| South America | ~1.2GB | 0-14 |
| Europe | ~1.8GB | 0-14 |
| Africa | ~1.5GB | 0-14 |
| Asia | ~2.0GB | 0-14 |
| Oceania | ~800MB | 0-14 |
| Western Europe | ~600MB | 0-14 |
| Central Europe | ~500MB | 0-14 |
| USA East | ~700MB | 0-14 |
| USA West | ~600MB | 0-14 |
| Southeast Asia | ~500MB | 0-14 |
| Japan | ~400MB | 0-14 |
| India | ~350MB | 0-14 |

## Usage

### Downloading Offline Maps

1. Open the app on your Wear OS device
2. Navigate to **Regions** tab
3. Select a region to download
4. Tap "Download" and wait for completion
5. Storage usage is displayed at the top

### Viewing Maps

1. Open **Home** tab to view the map
2. Use zoom buttons to zoom in/out
3. Tap location button to center on current GPS position
4. Maps automatically load from downloaded regions

### Managing GPX Files

1. Navigate to **GPX** tab
2. Tap "Import GPX File" to select a file
3. Select a GPX file from your device
4. Tap "Show" to display track on map
5. Tap "Delete" to remove from device

### Settings

- **Dark Mode**: Toggle dark/light theme
- **Location Updates**: Configure GPS update frequency
- **Storage**: View and manage cache

## Development

### Project Structure

- **app/**: Expo Router screens and layouts
- **lib/**: Core logic and state management
- **components/**: Reusable UI components
- **constants/**: Theme, OAuth, and constants
- **server/**: Backend API (optional, for cloud features)
- **tests/**: Unit and integration tests

### Running Tests

```bash
# Run all tests
pnpm test

# Run tests in watch mode
pnpm test --watch

# Run specific test file
pnpm test auth.logout.test.ts
```

### Type Checking

```bash
# Check TypeScript errors
pnpm check
```

### Linting

```bash
# Run ESLint
pnpm lint

# Format code with Prettier
pnpm format
```

## Troubleshooting

### App crashes on startup

- Clear app cache: Settings → Apps → Wear OSM Map → Clear Cache
- Reinstall app: `adb uninstall space.manus.wear.osm.map.t...`
- Check logs: `adb logcat | grep wear-osm-map`

### Maps not rendering

- Verify region is downloaded: Regions tab should show downloaded size
- Check storage space: Device must have at least 100MB free
- Restart app and try again

### GPS not working

- Enable location permissions: Settings → Apps → Wear OSM Map → Permissions
- Ensure device has GPS (not all Wear OS devices have GPS)
- Wait 30 seconds for GPS to acquire signal

### GitHub Actions build fails

- Check logs: Go to Actions tab → failed workflow → view logs
- Verify secrets are set correctly
- Ensure branch name matches workflow triggers
- Check Node.js version compatibility

## Performance Tips

- **Download smaller regions first** (e.g., Western Europe ~600MB) to test
- **Use simplified map layer** for better performance on watch
- **Limit GPX tracks** to 1-2 tracks per session
- **Close unused apps** to free up device memory
- **Disable background location** when not in use

## License

This project is licensed under the ODbL (Open Database License) for OpenStreetMap data. Attribution to © OpenStreetMap contributors is required.

## Contributing

Contributions are welcome! Please:

1. Fork the repository
2. Create a feature branch: `git checkout -b feature/your-feature`
3. Commit changes: `git commit -m "Add your feature"`
4. Push to branch: `git push origin feature/your-feature`
5. Open a Pull Request

## Support

For issues, questions, or suggestions:

- Open an issue on GitHub
- Check existing issues for solutions
- Review troubleshooting section above

## Roadmap

- [ ] Rotary input support for zoom and navigation
- [ ] Track recording (record live GPS as GPX)
- [ ] Offline search and routing
- [ ] Compass heading indicator
- [ ] Waypoint markers
- [ ] Multi-track comparison
- [ ] Cloud sync (optional)

## Changelog

### v1.0.0 (May 2026)

- Initial release
- MapLibre GL PMTiles rendering
- Protomaps region downloads
- GPX file support
- GPS location tracking
- Wear OS optimization
- GitHub Actions CI/CD

## Acknowledgments

- [OpenStreetMap](https://www.openstreetmap.org/) - Map data
- [Protomaps](https://protomaps.com/) - Vector tiles
- [MapLibre GL](https://maplibre.org/) - Map rendering
- [Expo](https://expo.dev/) - React Native framework
- [Samsung Galaxy Watch 7](https://www.samsung.com/us/mobile/wearables/smartwatches/galaxy-watch-7/) - Target device
