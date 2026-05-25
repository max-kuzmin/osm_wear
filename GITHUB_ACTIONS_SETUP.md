# GitHub Actions Setup Guide

This guide explains how to set up GitHub Actions for automated APK builds.

## Why Manual Setup?

GitHub Actions workflows require special permissions that may not be enabled by default. This guide provides two options:

1. **Option A**: Create workflows via GitHub Web UI (recommended for first-time setup)
2. **Option B**: Push workflows via git (requires repository permissions)

## Option A: Create Workflows via GitHub Web UI (Recommended)

### Step 1: Enable Actions

1. Go to your repository: https://github.com/max-kuzmin/osm_wear
2. Click **Settings** tab
3. In left sidebar, click **Actions** → **General**
4. Under "Actions permissions", select **Allow all actions and reusable workflows**
5. Click **Save**

### Step 2: Create Local Gradle Build Workflow

1. Go to **Actions** tab
2. Click **New workflow** → **Set up a workflow yourself**
3. Name it `build-apk.yml`
4. Copy the workflow content below and paste it:

```yaml
name: Build APK (Local Gradle)

on:
  push:
    branches:
      - main
      - develop
  workflow_dispatch:
    inputs:
      build_type:
        description: 'Build type'
        required: true
        default: 'debug'
        type: choice
        options:
          - debug
          - release

jobs:
  build:
    runs-on: ubuntu-latest
    timeout-minutes: 60

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'pnpm'

      - name: Setup pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 9.12.0

      - name: Setup Java
        uses: actions/setup-java@v4
        with:
          distribution: 'temurin'
          java-version: '11'
          cache: 'gradle'

      - name: Install dependencies
        run: pnpm install --frozen-lockfile

      - name: Build APK (Debug)
        if: github.event.inputs.build_type == 'debug' || github.event_name == 'push'
        run: |
          cd android
          ./gradlew assembleDebug
          cd ..

      - name: Build APK (Release)
        if: github.event.inputs.build_type == 'release'
        run: |
          cd android
          ./gradlew assembleRelease
          cd ..

      - name: Upload Debug APK
        if: github.event.inputs.build_type == 'debug' || github.event_name == 'push'
        uses: actions/upload-artifact@v4
        with:
          name: app-debug.apk
          path: android/app/build/outputs/apk/debug/app-debug.apk
          retention-days: 30

      - name: Upload Release APK
        if: github.event.inputs.build_type == 'release'
        uses: actions/upload-artifact@v4
        with:
          name: app-release.apk
          path: android/app/build/outputs/apk/release/app-release.apk
          retention-days: 30

      - name: Create Release (on tag)
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v1
        with:
          files: |
            android/app/build/outputs/apk/release/app-release.apk
          draft: false
          prerelease: false
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Notify on failure
        if: failure()
        run: |
          echo "Build failed! Check logs above for details."
          exit 1
```

5. Click **Commit changes**

### Step 3: Create EAS Build Workflow (Optional)

1. Click **New workflow** → **Set up a workflow yourself**
2. Name it `eas-build.yml`
3. Copy the workflow content below:

```yaml
name: Build with EAS (Recommended for Production)

on:
  push:
    branches:
      - main
  workflow_dispatch:
    inputs:
      build_profile:
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
    timeout-minutes: 120

    steps:
      - name: Checkout code
        uses: actions/checkout@v4

      - name: Setup Node.js
        uses: actions/setup-node@v4
        with:
          node-version: '18'
          cache: 'pnpm'

      - name: Setup pnpm
        uses: pnpm/action-setup@v2
        with:
          version: 9.12.0

      - name: Install dependencies
        run: pnpm install --frozen-lockfile

      - name: Setup EAS CLI
        run: npm install -g eas-cli

      - name: Build with EAS (Preview)
        if: github.event.inputs.build_profile == 'preview' || github.event_name == 'push'
        run: eas build --platform android --profile preview --non-interactive
        env:
          EXPO_TOKEN: ${{ secrets.EXPO_TOKEN }}

      - name: Build with EAS (Production)
        if: github.event.inputs.build_profile == 'production'
        run: eas build --platform android --profile production --non-interactive
        env:
          EXPO_TOKEN: ${{ secrets.EXPO_TOKEN }}

      - name: Wait for build completion
        run: |
          echo "EAS build submitted. Check https://expo.dev for build status."
          echo "Build artifacts will be available in your Expo account."

      - name: Create Release (on tag)
        if: startsWith(github.ref, 'refs/tags/v')
        uses: softprops/action-gh-release@v1
        with:
          body: |
            EAS Build completed successfully!
            
            Build Profile: ${{ github.event.inputs.build_profile || 'preview' }}
            
            Download the APK from:
            https://expo.dev/accounts/[your-account]/projects/wear-osm-map/builds
            
            Or use EAS CLI:
            ```
            eas build:list --platform android
            ```
          draft: false
          prerelease: false
        env:
          GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}

      - name: Notify on failure
        if: failure()
        run: |
          echo "EAS build failed! Check logs above for details."
          echo "Ensure EXPO_TOKEN secret is set correctly."
          exit 1
```

4. Click **Commit changes**

### Step 4: Add Secrets (for EAS builds only)

1. Go to **Settings** → **Secrets and variables** → **Actions**
2. Click **New repository secret**
3. Add `EXPO_TOKEN`:
   - Name: `EXPO_TOKEN`
   - Value: Get from running `eas login` then `eas secrets:create`
4. Click **Add secret**

## Option B: Push Workflows via Git

If you have repository permissions, you can push workflows directly:

1. Create `.github/workflows/build-apk.yml` with the content above
2. Create `.github/workflows/eas-build.yml` with the content above
3. Push to repository:
   ```bash
   git add .github/workflows/
   git commit -m "Add GitHub Actions workflows"
   git push origin main
   ```

## Testing Workflows

### Trigger Local Gradle Build

1. Go to **Actions** tab
2. Select **Build APK (Local Gradle)**
3. Click **Run workflow**
4. Select build type: `debug` or `release`
5. Click **Run workflow**
6. Wait for build to complete
7. Download APK from artifacts

### Trigger EAS Build

1. Go to **Actions** tab
2. Select **Build with EAS (Recommended for Production)**
3. Click **Run workflow**
4. Select build profile: `preview` or `production`
5. Click **Run workflow**
6. Wait for build to complete (may take 10-20 minutes)
7. Check Expo dashboard for build status

## Automatic Builds

Workflows automatically trigger on:

- **Push to main branch**: Runs debug build (Local Gradle)
- **Push to develop branch**: Runs debug build (Local Gradle)
- **Push of version tags** (e.g., `v1.0.0`): Creates GitHub Release with APK

## Troubleshooting

### Build fails with "Permission denied"

- Ensure Java is installed: Check Actions logs
- Verify Gradle wrapper has execute permissions

### EAS build fails with "EXPO_TOKEN not found"

- Go to Settings → Secrets and add `EXPO_TOKEN`
- Ensure token is valid: Run `eas whoami`

### Workflow not showing in Actions tab

- Go to Settings → Actions → General
- Select "Allow all actions and reusable workflows"
- Wait a few minutes for GitHub to refresh

### APK not uploading

- Check artifact retention settings (default: 30 days)
- Verify build completed successfully in logs

## Next Steps

1. Set up workflows using Option A or B above
2. Add `EXPO_TOKEN` secret for EAS builds
3. Push code to main branch to trigger first build
4. Download APK from Actions artifacts
5. Install on Wear OS device and test

## Resources

- [GitHub Actions Documentation](https://docs.github.com/en/actions)
- [Expo EAS Build Documentation](https://docs.expo.dev/build/introduction/)
- [Android Gradle Build Documentation](https://developer.android.com/build)
