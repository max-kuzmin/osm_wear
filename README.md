# OSM Wear

A native **Wear OS** application for **Samsung Galaxy Watch 7** (and compatible Wear OS 3+ devices) that provides offline OpenStreetMap navigation, GPX track management, and real-time GPS location tracking.

---

## Features

| Feature | Description |
|---|---|
| **Offline Maps** | Download regional `.map` files from the official Mapsforge server and render them fully offline |
| **Map Rendering** | Vector tile rendering via [Mapsforge](https://github.com/mapsforge/mapsforge) — fast, smooth, and battery-efficient |
| **Zoom Controls** | On-screen +/− buttons; rotary crown input supported natively by Wear OS |
| **GPS Location** | Real-time blue-dot location marker using the watch's built-in GPS (standalone, no phone required) |
| **GPX Tracks** | Import `.gpx` files from the watch storage, display tracks as coloured polylines on the map |
| **GPX Manager** | Toggle track visibility, view distance statistics, and delete tracks |
| **Dark UI** | Optimised for AMOLED displays — dark background saves battery on round watch faces |

---

## Tech Stack

| Component | Library / Version |
|---|---|
| Language | Kotlin 2.1 |
| UI | Jetpack Compose for Wear OS (Material 3) |
| Navigation | Wear Compose Navigation (SwipeDismissableNavHost) |
| Map Rendering | [Mapsforge 0.28](https://github.com/mapsforge/mapsforge) (`mapsforge-map-android`) |
| Map Data | [download.mapsforge.org](https://download.mapsforge.org/maps/v5/) — free OSM-based `.map` files |
| GPX Parsing | [android-gpx-parser 2.3.1](https://github.com/ticofab/android-gpx-parser) |
| GPS | Android FusedLocationProviderClient (Wear OS standalone GPS) |
| Network | OkHttp 4.12 (map downloads with resume support) |
| Persistence | AndroidX DataStore Preferences |
| Min SDK | 30 (Wear OS 3.0 — Galaxy Watch 4 and newer) |
| Target SDK | 35 |

---

## Project Structure

```
app/src/main/kotlin/com/osm/wear/
├── OsmWearApp.kt                        # Application class (Mapsforge init)
├── domain/model/
│   └── Models.kt                        # MapRegion, GpxTrack, UserLocation, …
├── data/
│   ├── map/
│   │   ├── MapRegionCatalog.kt          # 30+ downloadable regions with real URLs
│   │   └── MapDownloadManager.kt        # OkHttp downloader with resume + progress
│   ├── gpx/
│   │   └── GpxRepository.kt            # GPX import, parsing, storage, visibility
│   └── location/
│       └── LocationRepository.kt        # FusedLocationProviderClient flow
└── presentation/
    ├── MainActivity.kt                  # Entry point, permission request
    ├── theme/Theme.kt                   # Wear OS Material3 theme
    ├── navigation/Navigation.kt         # SwipeDismissableNavHost routes
    └── screens/
        ├── MapViewModel.kt              # Shared ViewModel (map, GPS, GPX, downloads)
        ├── MapScreen.kt                 # Mapsforge MapView + GPS dot + GPX polylines
        ├── DownloadScreen.kt            # Region browser + download progress
        ├── GpxScreen.kt                 # GPX file manager
        └── MenuScreen.kt               # Main menu
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
2. Create a new virtual device → **Wear OS** → Galaxy Watch 7 profile
3. Start the emulator, then run the app from Android Studio

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

## GPX Track Import

1. Transfer your `.gpx` file to the watch (via **Galaxy Wearable** app, ADB, or cloud sync)
2. Open **OSM Wear → GPX Tracks → +**
3. Select the file from the system file picker
4. The track appears as an orange polyline on the map

---

## Attribution

Map data © [OpenStreetMap contributors](https://www.openstreetmap.org/copyright), licensed under [ODbL](https://opendatacommons.org/licenses/odbl/).
Map rendering by [Mapsforge](https://github.com/mapsforge/mapsforge), Apache 2.0 license.
GPX parsing by [android-gpx-parser](https://github.com/ticofab/android-gpx-parser), Apache 2.0 license.

---

## License

MIT License
