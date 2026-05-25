# GitHub Actions Setup Guide

This guide explains how to set up GitHub Actions for automated APK building and deployment.

## Prerequisites

1. **GitHub Repository Access:** You must have admin access to the repository to configure Actions
2. **Expo Account:** Required for EAS builds (optional for local builds)
3. **Android Keystore:** Required for release APK signing (optional)

## Setup Instructions

### Step 1: Enable GitHub Actions

1. Go to your repository on GitHub
2. Click on the "Actions" tab
3. GitHub Actions should be enabled by default; if not, enable it

### Step 2: Add GitHub Actions Workflows

The repository includes two workflow files that need to be added to `.github/workflows/`:

#### Option A: Local Gradle Build (Recommended for testing)

Create `.github/workflows/build-apk.yml`:

```yaml
name: Build APK

on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main, develop ]
  workflow_dispatch:

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'pnpm'

      - name: Install pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 9.12.0

      - name: Install dependencies
        run: pnpm install --frozen-lockfile

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '11'

      - name: Setup Android SDK
        uses: android-actions/setup-android@v3

      - name: Prebuild native code
        run: npx expo prebuild --clean --no-install

      - name: Build APK (debug)
        run: |
          cd android
          ./gradlew assembleDebug

      - name: Build APK (release)
        run: |
          cd android
          ./gradlew assembleRelease
        continue-on-error: true

      - name: Upload debug APK artifact
        uses: actions/upload-artifact@v4
        with:
          name: app-debug
          path: android/app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30

      - name: Upload release APK artifact
        uses: actions/upload-artifact@v4
        if: always()
        with:
          name: app-release
          path: android/app/build/outputs/apk/release/app-release.apk
          retention-days: 30

      - name: Create Release
        if: startsWith(github.ref, 'refs/tags/')
        uses: softprops/action-gh-release@v1
        with:
          files: |
            android/app/build/outputs/apk/debug/app-debug.apk
            android/app/build/outputs/apk/release/app-release.apk
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

#### Option B: EAS Build (Recommended for production)

Create `.github/workflows/build-eas.yml`:

```yaml
name: Build with EAS

on:
  push:
    branches: [ main ]
    tags: [ 'v*' ]
  workflow_dispatch:
    inputs:
      profile:
        description: 'EAS build profile'
        required: true
        default: 'preview'
        type: choice
        options:
          - preview
          - production

jobs:
  build:
    runs-on: ubuntu-latest
    
    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'pnpm'

      - name: Install pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 9.12.0

      - name: Install dependencies
        run: pnpm install --frozen-lockfile

      - name: Setup Expo
        uses: expo/expo-github-action@v8
        with:
          eas-version: latest
          token: ${{ secrets.EXPO_TOKEN }}

      - name: Build with EAS (preview)
        if: github.event.inputs.profile == 'preview' || !github.event.inputs.profile
        run: eas build --platform android --profile preview --non-interactive

      - name: Build with EAS (production)
        if: github.event.inputs.profile == 'production' || startsWith(github.ref, 'refs/tags/')
        run: eas build --platform android --profile production --non-interactive

      - name: Upload build artifacts
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: eas-build-logs
          path: ~/.eas/logs/
          retention-days: 7
```

### Step 3: Configure Secrets (for EAS builds)

1. Go to your repository settings
2. Click on "Secrets and variables" → "Actions"
3. Click "New repository secret"
4. Add the following secrets:

| Secret Name | Description | How to Get |
|-------------|-------------|-----------|
| `EXPO_TOKEN` | Expo authentication token | Run `eas login` and `eas secrets:create --scope project` |
| `PLAY_STORE_SERVICE_ACCOUNT` | Google Play service account JSON (optional) | Create in Google Cloud Console |

### Step 4: Test the Workflow

1. Push a commit to the `main` branch
2. Go to the "Actions" tab on GitHub
3. You should see the workflow running
4. Wait for it to complete and check the logs

## Triggering Builds

### Automatic Triggers

- **On Push:** Builds automatically when you push to `main` or `develop` branches
- **On Pull Request:** Builds automatically for pull requests
- **On Tags:** Creates a GitHub Release with APK when you push a tag (e.g., `v1.0.0`)

### Manual Trigger

1. Go to the "Actions" tab
2. Select the workflow you want to run
3. Click "Run workflow"
4. Select the branch and any options
5. Click "Run workflow"

## Downloading APKs

### From Artifacts

1. Go to the "Actions" tab
2. Click on a completed workflow run
3. Scroll down to "Artifacts"
4. Download `app-debug` or `app-release`

### From Releases

1. Go to the "Releases" section
2. Find the release corresponding to your tag
3. Download the APK from the release assets

## Troubleshooting

### Build Fails with "Permission denied"

**Solution:** Make sure your GitHub token has the necessary permissions. For EAS builds, ensure your `EXPO_TOKEN` is valid.

### APK Not Generated

**Solution:** Check the workflow logs for errors. Common issues:
- Java version mismatch
- Android SDK not installed
- Missing dependencies

### EAS Build Fails

**Solution:** 
1. Ensure `EXPO_TOKEN` is set correctly
2. Run `eas build --platform android --profile preview` locally to test
3. Check EAS logs: `eas build:list`

## Advanced Configuration

### Signing Release APKs

To automatically sign release APKs:

1. Generate a keystore:
   ```bash
   keytool -genkey -v -keystore my-release-key.keystore -keyalg RSA -keysize 2048 -validity 10000 -alias my-key-alias
   ```

2. Encode the keystore to base64:
   ```bash
   base64 my-release-key.keystore | tr -d '\n'
   ```

3. Add as a GitHub secret: `ANDROID_KEYSTORE`

4. Update the workflow to use it in the gradle build step

### Deploying to Google Play Store

Add this step to your EAS workflow:

```yaml
- name: Upload to Play Store
  uses: r0adkll/upload-google-play@v1
  with:
    serviceAccountJsonPlainText: ${{ secrets.PLAY_STORE_SERVICE_ACCOUNT }}
    packageName: space.manus.wear.osm.map
    releaseFiles: android/app/build/outputs/apk/release/app-release.apk
    track: internal
    status: draft
```

## Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Expo EAS Build Documentation](https://docs.expo.dev/build/introduction/)
- [Android Gradle Build Documentation](https://developer.android.com/build)
- [React Native Build Guide](https://reactnative.dev/docs/signed-apk-android)

## Support

For issues with GitHub Actions, check:
1. Workflow logs on GitHub
2. Local build: `pnpm build` and `cd android && ./gradlew assembleDebug`
3. EAS logs: `eas build:list --platform android`
