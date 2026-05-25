# Quick Start: GitHub Actions for APK Building

## 5-Minute Setup

### Step 1: Add Workflow Files

Copy one of the workflow files to your repository:

**For Local Gradle Builds (Fastest):**
```bash
mkdir -p .github/workflows
curl -o .github/workflows/build-apk.yml https://raw.githubusercontent.com/max-kuzmin/osm_wear/main/GITHUB_ACTIONS_SETUP.md
```

Or manually create `.github/workflows/build-apk.yml` with the content from `GITHUB_ACTIONS_SETUP.md` (Option A section).

**For EAS Builds (Recommended for Production):**
Create `.github/workflows/build-eas.yml` with the content from `GITHUB_ACTIONS_SETUP.md` (Option B section).

### Step 2: Commit and Push

```bash
git add .github/workflows/
git commit -m "Add GitHub Actions APK build workflow"
git push origin main
```

### Step 3: (Optional) Add Secrets for EAS

If using EAS builds:

1. Go to GitHub repository settings
2. Click "Secrets and variables" → "Actions"
3. Add `EXPO_TOKEN`:
   - Run locally: `eas login` then `eas secrets:create --scope project`
   - Copy the token to GitHub Secrets

### Step 4: Test

1. Go to "Actions" tab on GitHub
2. You should see the workflow running
3. Wait for completion and download APK from artifacts

## What Happens Next?

- **Every push to `main`:** Builds debug APK automatically
- **Every tag (e.g., `git tag v1.0.0`):** Creates a GitHub Release with APK
- **Manual trigger:** Go to Actions tab and click "Run workflow"

## Build Locally First

Before pushing to GitHub, test locally:

```bash
# Install dependencies
pnpm install

# Build debug APK
npx expo prebuild --clean --no-install
cd android
./gradlew assembleDebug

# APK location: android/app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Workflow doesn't appear | Push to `main` branch, check "Actions" tab |
| Build fails | Check workflow logs, run locally first |
| APK not in artifacts | Scroll down in workflow run details |
| EAS build fails | Verify `EXPO_TOKEN` is set correctly |

## Next Steps

- Read full guide: `GITHUB_ACTIONS_SETUP.md`
- Configure signing for release APKs
- Set up Play Store deployment
- Add more workflows (linting, testing, etc.)

---

**Repository:** https://github.com/max-kuzmin/osm_wear
