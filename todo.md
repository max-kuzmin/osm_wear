# Wear OSM Map - Project TODO

## Core Features

### Map Engine & Rendering
- [x] Implement OSM tile downloader and cache manager
- [x] Create map canvas component with pan/zoom support
- [ ] Integrate rotary input for zoom control
- [ ] Implement tile rendering with offline cache lookup
- [ ] Add map attribution and tile source selection

### Real Tile Source Implementation (Protomaps)
- [x] Research and document real OSM tile sources
- [x] Create Protomaps region definitions with real download URLs
- [x] Implement download manager with progress tracking
- [x] Integrate MapLibre GL for PMTiles rendering (setup guide + components)
- [x] Create region downloader component with progress UI
- [x] Add download progress UI and error handling
- [x] Implement cache management and cleanup
- [x] Add attribution and licensing information
- [x] Install MapLibre dependencies (pnpm add @maplibre/maplibre-react-native maplibre-gl pmtiles)
- [x] Create working MapLibre canvas component (maplibre-map.tsx)
- [x] Initialize PMTiles protocol in app/_layout.tsx
- [x] Update home screen to use MapLibre rendering
- [x] Update regions screen with real Protomaps downloader
- [x] Create GPX manager component with file import
- [x] Update GPX screen with GPX manager
- [x] Update map context with GPX tracks support

### Offline Map Regions
- [x] Create region downloader UI (Map Regions screen)
- [x] Implement background tile download with progress tracking
- [x] Add region management (delete, view size)
- [x] Implement storage quota management
- [x] Create region selection and activation

### GPX File Support
- [x] Implement GPX file parser (XML parsing)
- [x] Create GPX file import UI (file picker)
- [x] Implement GPX track rendering on map (polyline overlay)
- [x] Add GPX statistics display (distance, points, elevation)
- [x] Create GPX file management (list, delete, import)

### GPS & Location
- [x] Integrate expo-location for GPS tracking
- [x] Implement current location indicator (blue dot)
- [ ] Add accuracy circle visualization
- [x] Create location update handler with configurable frequency
- [x] Implement recenter-on-location button

### Navigation & UI
- [x] Create tab-based navigation (Home, Regions, GPX, Settings)
- [x] Implement Home Screen (map view)
- [x] Implement Map Regions Screen
- [x] Implement GPX Files Screen
- [x] Implement Settings Screen
- [ ] Add rotary input support for navigation

### Wear OS Optimization
- [ ] Optimize for circular display (Samsung Galaxy Watch 7)
- [ ] Implement ambient mode support
- [ ] Add haptic feedback for interactions
- [ ] Optimize battery usage (GPS polling, tile caching)
- [ ] Test on actual Wear OS device

### Branding & Polish
- [x] Generate custom app logo/icon
- [x] Update app configuration (app name, branding)
- [ ] Implement theme switching (light/dark mode)
- [ ] Add splash screen
- [ ] Polish UI animations and transitions

## Testing & Validation

- [ ] Test map panning and zooming
- [ ] Test GPX file import and rendering
- [ ] Test GPS location tracking
- [ ] Test offline tile loading
- [ ] Test rotary input on Wear OS device
- [ ] Test battery drain during extended use
- [ ] Test on Samsung Galaxy Watch 7 (physical device)

## Known Issues & Bugs

(None identified yet)

## Notes

- Default to AsyncStorage for settings (no backend sync required)
- Focus on offline-first architecture
- Prioritize battery efficiency for watch device
- Support circular display constraints
