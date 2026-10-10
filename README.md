# OSM Wear

A native **Wear OS** application for **Samsung Galaxy Watch 7** (and compatible Wear OS 3+ devices) that provides offline OpenStreetMap navigation, GeoJSON route track management, real-time GPS location tracking, and synchronized turn-by-turn navigation guidance.

---

## Features

| Feature | Description |
|---|---|
| **Offline Maps** | Download regional `.map` files from the official Mapsforge server and render them fully offline |
| **Map Rendering** | Vector tile rendering via [Mapsforge](https://github.com/mapsforge/mapsforge) — fast, smooth, and battery-efficient |
| **Zoom Controls** | On-screen +/− buttons; rotary crown input supported natively by Wear OS |
| **GPS Location** | Real-time blue-dot location marker using the watch's built-in GPS (standalone, no phone required) |
| **Battery-Efficient GPS** | Three GPS modes — High Accuracy (1 s), Balanced (5 s, 10 m), Low Power (30 s, 50 m) — to match use-case and battery life |
| **GeoJSON Tracks** | Import `.geojson` and `.json` files from watch storage or system intents, displaying routes on the map |
| **Track Manager** | View distance statistics, select active tracks, save computed routes, and delete tracks |
| **Turn-by-Turn Navigation** | Follow any route with dedicated navigation guidance showing direction arrow, distance to next turn, and synchronized voice alerts |
| **Vibration Alarms** | Watch vibrates with a double pulse before each turn and a long pulse on arrival |
| **Dark UI** | Optimised for AMOLED displays — dark background saves battery on round watch faces |

---

## Tech Stack

| Component | Library / Version |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose for Wear OS (Material 3) |
| Navigation | Wear Compose Navigation (`SwipeDismissableNavHost`) |
| Map Rendering | [Mapsforge 0.28](https://github.com/mapsforge/mapsforge) (`mapsforge-map-android`) |
| Map Data | [download.mapsforge.org](https://download.mapsforge.org/maps/v5/) — free OSM-based `.map` files |
| Route Architecture | Native RFC 7946 GeoJSON route parser & serializer |
| GPS | Android `FusedLocationProviderClient` (Wear OS standalone GPS) |
| Network | OkHttp 4.12 (map downloads with resume support) |
| Persistence | AndroidX DataStore Preferences |
| Splash Screen | AndroidX Core SplashScreen 1.0.1 |
| Min SDK | 30 (Wear OS 3.0 — Galaxy Watch 4 and newer) |
| Target SDK | 35 |

---

## Project Structure

app/src/main/kotlin/com/osm/wear/
├── OsmWearApp.kt                        # Application class (Hilt / Mapsforge init)
├── AppConfig.kt                         # App configuration constants
├── data_sources/                        # Device hardware and local/remote data sources
│   ├── DeviceAlertsDataSource.kt        # TTS, vibrator & system notification alerts
│   ├── DeviceLocationDataSource.kt      # FusedLocationProviderClient GPS tracking
│   ├── LocalFileDataSource.kt           # GeoJSON route and map file system I/O
│   ├── LocalPreferencesDataSource.kt    # SharedPreferences / DataStore
│   ├── RemoteRegionDataSource.kt        # Mapsforge server map catalog & download
│   ├── RemoteGeocodingDataSource.kt     # Reverse geocoding & OSRM routing
│   ├── geojson/                         # RFC 7946 GeoJSON route parser & serializer
│   │   └── GeoJsonRouteParser.kt        # RFC 7946 route parser and serializer
│   └── osrm/                            # OSRM turn-by-turn routing & step parsing
│       └── OsrmStepParser.kt            # Native OSRM GeoJSON step & maneuver parser
├── repositories/                        # Repository layer (interfaces & implementations)
│   ├── AlertsRepository.kt              # Navigation alerts & speech mode coordination
│   ├── BillingRepository.kt             # Play Billing / in-app subscriptions
│   ├── CursorRepository.kt             # Location and heading provider
│   ├── GeocodingRepository.kt          # Nominatim / offline address lookup & OSRM routing
│   ├── RouteRepository.kt              # GeoJSON track import, parsing, listing, removal
│   ├── MarkersRepository.kt            # Saved waypoints / map bookmarks
│   ├── PreferencesRepository.kt        # User preferences (GPS mode, theme, rotation)
│   └── RegionRepository.kt             # Downloaded .map regions management
├── services/                            # Domain use cases and background services
│   ├── BuildInitialNavigationStateUseCase.kt # Step-based navigation state builder
│   ├── BuildRouteToMarkerUseCase.kt     # Native discrete step route generation to marker
│   ├── NavigationTrackingService.kt     # GPS stream coordinator for navigation
│   ├── NavigationForegroundService.kt   # Android foreground service with WakeLock
│   ├── RegionValidatorService.kt        # Map region entitlement & file check
│   ├── ScanTracksFoldersUseCase.kt      # Scans /Download/ for GeoJSON tracks
│   ├── UpdateNavigationStateUseCase.kt  # Discrete step tracking, voice & alarm engine
│   └── UiRouter.kt                      # In-app navigation router
├── models/                              # Domain data classes and enums
│   ├── Bookmark.kt, DownloadedRegion.kt, DownloadState.kt, GeoPoint.kt, RouteFile.kt
│   ├── Maneuver.kt                      # RouteStep, Maneuver, ManeuverType, NavigationRoute
│   ├── NavigationState.kt, UserLocation.kt
│   └── enums/                           # GpsBatteryMode, MapRotationMode, MapTheme, NavigationMode, ...
├── view_models/                         # MVI ViewModels, Intents, Effects & UiStates
│   ├── TracksViewModel.kt, MainMenuViewModel.kt, MapViewModel.kt
│   ├── MarkersViewModel.kt, PreferencesViewModel.kt, RegionsViewModel.kt, SearchAddressViewModel.kt
└── presentation/
    ├── MainActivity.kt                  # Entry point, permission requests, NavHost
    ├── components/                      # BackButton, NavigationButton, RemoveButton
    ├── navigation/AppNavGraph.kt        # Wear Compose navigation graph
    ├── map/layers/                      # Mapsforge custom layers (LocationArrowLayer, MarkerLayer, ...)
    ├── theme/                           # Color, Dimen, Theme, Type
    └── screens/
        ├── MainMapScreen.kt             # Mapsforge MapView + GPS dot + turn guidance overlay
        ├── MainMenuScreen.kt            # Main wearable menu
        ├── TracksScreen.kt              # Tracks manager + Navigate button
        ├── MarkersScreen.kt             # Saved bookmarks list
        ├── PreferencesScreen.kt         # GPS mode, rotation, and theme settings
        ├── RegionsScreen.kt             # Offline map downloader & regional picker
        └── SearchAddressScreen.kt       # Address search & pin placement
```

---

## Building Locally

### Prerequisites

- **Android Studio** Ladybug (2024.2) or newer
- **JDK 17** (bundled with Android Studio or install separately)
- **Android SDK** with API 35

### Steps

```bash
# 1. Clone the repository
git clone https://github.com/max-kuzmin/osm_wear.git
cd osm_wear

# 2. Build a debug APK
./gradlew assembleDebug

# The APK will be at:
# app/build/outputs/apk/debug/app-debug.apk

# 3. Install on a connected Galaxy Watch 7 (ADB over Wi-Fi or USB)
adb install app/build/outputs/apk/debug/app-debug.apk
```

### Build a release APK (unsigned)

```bash
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release-unsigned.apk
```

### Run on Wear OS Emulator

1. Open **Android Studio → Device Manager**
2. Create a new virtual device → **Wear OS** → Galaxy Watch 7 profile (or similar Wear OS profile)
3. Run the helper script `start-emulator.bat` at the root of the project to automatically list and launch the emulator from the command line.
4. Run the app directly from Android Studio using the pre-configured **app** Run/Debug configuration.

---

## Building with GitHub Actions

### Automatic builds

Every push to `main` or `develop` automatically triggers a debug APK build.
Every push of a version tag (e.g. `v1.0.0`) builds a release APK and creates a GitHub Release.

### Setup

1. **Enable Actions** in your repository:
   `Settings → Actions → General → Allow all actions`

2. The workflow file is already included at `.github/workflows/build-apk.yml`.
   No additional secrets are required for debug builds.

3. **Download the APK** after a successful build:
   - Go to `Actions` tab → select a workflow run → scroll to **Artifacts**
   - Download `osm-wear-debug-<run_number>.zip`

### Creating a release

```bash
git tag v1.0.0
git push origin v1.0.0
```

This triggers the release job which uploads a release APK as a GitHub Release asset.

---

## Downloading Maps

Maps are downloaded from the **official Mapsforge download server**:

```
https://download.mapsforge.org/maps/v5/
```

Available regions include:

| Continent | Example regions |
|---|---|
| Europe | Germany, France, Great Britain, Italy, Spain, Poland, Ukraine, … |
| Asia | Japan, China, India, South Korea, Thailand, Indonesia |
| North America | USA (Northeast/South/Midwest/West), Canada, Mexico |
| South America | Brazil, Argentina |
| Africa | South Africa, Egypt |
| Oceania | Australia, New Zealand |
| Russia | European Russia, Asian Russia |

Maps are stored in the app's private storage (`/data/data/com.osm.wear/files/maps/`).

---

## Track Import (GeoJSON)

1. Transfer your `.geojson` or `.json` file to the watch `/Download/` directory (or via ADB, cloud sync, or share intent)
2. Open **OSM Wear → Menu → Tracks**
3. Tracks from `/Download/` are automatically scanned and listed
4. Tap a track to select it, then tap **Start Navigation** from the main menu

---

## Turn-by-Turn Navigation

1. Open **Menu → Tracks** (or tap on the map to set a marker and compute a route).
2. Select a track, then return to the main menu and tap **Start Navigation**.
3. Turn-by-turn guidance runs with background tracking and WakeLock:
   - **Direction arrow** on map overlay shows upcoming turn maneuver (left/right/straight).
   - **Distance to next turn** (metres or km) calculated along route waypoints.
   - **Natural voice announcements** (e.g. *"In 300 meters, turn left onto Main St"*, *"In 1 kilometer, turn right"*), suppressing repetitive phrases and guaranteeing no *"In now..."* utterances.
   - **Turn alarm & haptic feedback** vibrating with a distinct pulse pattern ~30 m before each turn and upon arrival.
   - **Remaining distance** to destination.
   - **Off-track warning** (`⚠`) when deviating from the route.
4. Tap **Stop Nav** or reach destination to end guidance cleanly.

---

## Battery Usage

GPS power consumption is controlled by the **GPS Mode** setting (accessible from the map screen):

| Mode | Update Interval | Min Distance | Use Case |
|---|---|---|---|
| **High Accuracy** | 1 second | 2 m | Active recording / navigation |
| **Balanced** | 5 seconds | 10 m | Casual map browsing |
| **Low Power** | 30 seconds | 50 m | Background tracking / long hikes |

The app automatically switches to **High Accuracy** during recording and navigation, and reverts to the user's preferred mode otherwise.

---

## Attribution

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), licensed under [ODbL](https://opendatacommons.org/licenses/odbl/).
Map rendering by [Mapsforge](https://github.com/mapsforge/mapsforge), Apache 2.0 license.
Route architecture built on native RFC 7946 GeoJSON specification with discrete step maneuvers.

---

## License

MIT License
