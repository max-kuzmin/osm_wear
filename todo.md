# Wear OSM Map - Project TODO

## Core Features

### Map Engine & Rendering
- [x] Implement OSM tile downloader and cache manager
- [x] Create map canvas component with pan/zoom support
- [ ] Integrate rotary input for zoom control
- [ ] Implement tile rendering with offline cache lookup
- [ ] Add map attribution and tile source selection

### Offline Map Regions
- [ ] Create region downloader UI (Map Regions screen)
- [ ] Implement background tile download with progress tracking
- [ ] Add region management (delete, view size)
- [ ] Implement storage quota management
- [ ] Create region selection and activation

### GPX File Support
- [x] Implement GPX file parser (XML parsing)
- [ ] Create GPX file import UI (file picker)
- [ ] Implement GPX track rendering on map (polyline overlay)
- [ ] Add GPX statistics display (distance, points, elevation)
- [ ] Create GPX file management (list, delete, import)

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
