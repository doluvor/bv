# Product Flavor Migration Design

**Date:** 2026-03-28
**Author:** Claude Code
**Status:** Approved

## Overview

Convert the BV app from a multi-module structure (app/shared, app/mobile, app/tv) to a single-module product flavor structure. This will significantly improve TV build performance by only compiling TV-specific code when building TV variants.

## Motivation

**Current Problem:**
- The main `app` module depends on both `:app:mobile` and `:app:tv` library modules
- Every build compiles ALL code (mobile + tv + shared), even when only one platform is needed
- TV builds are unnecessarily slow because mobile code is always compiled

**Desired Outcome:**
- TV builds only compile TV-specific code
- Mobile builds only compile mobile-specific code
- Shared code is compiled once per build (as expected)
- TV app ID remains unchanged

## Current Structure

```
app/
├── build.gradle.kts          (application module, depends on mobile+tv)
├── src/main/                 (empty except for AndroidManifest.xml)
├── mobile/                   (library module - 76 Kotlin files)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/dev/aaa1115910/bv/mobile/
├── tv/                       (library module - 137 Kotlin files)
│   └── src/main/
│       ├── AndroidManifest.xml
│       └── kotlin/dev/aaa1115910/bv/tv/
└── shared/                   (library module - 112 Kotlin files)
    └── src/main/
        └── kotlin/dev/aaa1115910/bv/
```

## Target Structure

```
app/
├── build.gradle.kts          (updated with productFlavors)
├── src/
│   ├── main/                 (shared code - from app/shared)
│   │   ├── java/
│   │   │   └── dev/aaa1115910/bv/
│   │   ├── res/              (shared resources)
│   │   └── AndroidManifest.xml (merged manifest)
│   ├── mobile/               (mobile-specific code - from app/mobile)
│   │   ├── java/
│   │   │   └── dev/aaa1115910/bv/mobile/
│   │   ├── res/              (mobile resources)
│   │   └── AndroidManifest.xml (mobile activities)
│   └── tv/                   (tv-specific code - from app/tv)
│       ├── java/
│       │   └── dev/aaa1115910/bv/tv/
│       ├── res/              (tv resources)
│       └── AndroidManifest.xml (tv activities, leanback)
```

## Build Configuration

### Product Flavors

```kotlin
android {
    flavorDimensions.add("platform")

    productFlavors {
        create("mobile") {
            dimension = "platform"
            applicationIdSuffix = ".mobile"
            versionNameSuffix = "-mobile"
        }
        create("tv") {
            dimension = "platform"
            // No suffix - keeps the original app ID
        }
    }

    // Keep existing flavorDimensions.add("channel") for lite/default
}
```

### Build Variants

The combination of `platform` (mobile/tv) and `channel` (lite/default) and `buildType` (debug/release) produces:

- `mobileLiteDebug`, `mobileLiteRelease`
- `mobileDebug`, `mobileRelease`
- `tvLiteDebug`, `tvLiteRelease`
- `tvDebug`, `tvRelease`

### App IDs

- **TV**: `dev.aaa1115910.bv` (unchanged)
- **Mobile**: `dev.aaa1115910.bv.mobile` (different for side-by-side testing)

### Build Commands

```bash
./gradlew assembleTvDebug              # TV debug APK
./gradlew assembleMobileDebug          # Mobile debug APK
./gradlew assembleTvRelease            # TV release APK
```

## Migration Steps

### Phase 1: Prepare New Structure

1. Create new source directories:
   ```bash
   mkdir -p app/src/mobile
   mkdir -p app/src/tv
   ```

2. Move code from library modules:
   ```bash
   # Move shared code to main
   mv app/shared/src/main/* app/src/main/

   # Move mobile code to mobile flavor
   mv app/mobile/src/main/* app/src/mobile/

   # Move tv code to tv flavor
   mv app/tv/src/main/* app/src/tv/
   ```

3. Update package imports:
   - Shared code packages stay as `dev.aaa1115910.bv.*`
   - Mobile packages stay as `dev.aaa1115910.bv.mobile.*`
   - TV packages stay as `dev.aaa1115910.bv.tv.*`

### Phase 2: Update Build Configuration

4. Update `app/build.gradle.kts`:
   - Add `flavorDimensions.add("platform")`
   - Add `productFlavors` blocks for `mobile` and `tv`
   - Copy all dependencies from `app/shared/build.gradle.kts`
   - Remove `implementation(project(":app:mobile"))`
   - Remove `implementation(project(":app:tv"))`
   - Copy BuildConfig fields from `app/shared/build.gradle.kts`

5. Update `settings.gradle.kts`:
   - Remove `:app:mobile`
   - Remove `:app:tv`
   - Remove `:app:shared`

### Phase 3: Cleanup

6. Delete old module directories:
   ```bash
   rm -rf app/mobile
   rm -rf app/tv
   rm -rf app/shared
   ```

7. Sync Gradle and test builds

## Dependency Management

All dependencies from `app/shared/build.gradle.kts` will be moved to `app/build.gradle.kts`. The `api()` declarations will remain as-is to maintain transitive dependency behavior.

Platform-specific dependencies can use flavor-specific configurations:

```kotlin
dependencies {
    // Shared dependencies
    implementation(androidx.compose.ui)
    // ... all other shared dependencies

    // Platform-specific (if needed)
    mobileImplementation("some.mobile.library")
    tvImplementation("some.tv.library")
}
```

## Potential Issues & Mitigations

### Issue 1: Package Name Conflicts
- **Risk**: If shared, mobile, and tv use overlapping package structures
- **Mitigation**: Current structure uses distinct packages (`.shared`, `.mobile`, `.tv`), so shared code will move to base package without conflict

### Issue 2: Resource Conflicts
- **Risk**: Mobile and TV might define resources with the same name
- **Mitigation**: Android's build system handles this automatically - flavor resources override main resources

### Issue 3: Manifest Merging
- **Risk**: Multiple AndroidManifest.xml files need to merge correctly
- **Mitigation**: Android's manifest merger handles this; we'll verify the merged manifest

### Issue 4: BuildConfig Changes
- **Risk**: `app/shared` has BuildConfig definitions that need to move
- **Mitigation**: Copy BuildConfig fields to main `app/build.gradle.kts`

## Testing & Verification

### Build Verification

1. **TV Build:**
   ```bash
   ./gradlew clean assembleTvDebug
   ```
   - APK location: `app/build/outputs/apk/tv/debug/`
   - App ID: `dev.aaa1115910.bv`
   - Install and test on TV device/emulator

2. **Mobile Build:**
   ```bash
   ./gradlew clean assembleMobileDebug
   ```
   - APK location: `app/build/outputs/apk/mobile/debug/`
   - App ID: `dev.aaa1115910.bv.mobile`
   - Install and test on mobile device/emulator

3. **Android Studio:**
   - Open "Build Variants" tab
   - Select `tvDebug` - verify only TV code is compiled
   - Select `mobileDebug` - verify only mobile code is compiled
   - Run button should deploy selected variant

### Functional Testing Checklist

- [ ] TV app launches correctly
- [ ] Mobile app launches correctly
- [ ] Video playback works on both platforms
- [ ] Login flow works on both platforms
- [ ] Settings work on both platforms
- [ ] Navigation works on both platforms
- [ ] No build errors or warnings
- [ ] Proguard/R8 works for release builds
- [ ] Leanback launcher works for TV
- [ ] Mobile launcher works for mobile

## Rollback Plan

If migration fails:
1. Git revert changes to `app/build.gradle.kts` and `settings.gradle.kts`
2. Restore `app/mobile/`, `app/tv/`, `app/shared/` from backup
3. Delete `app/src/mobile/` and `app/src/tv/`

**Pre-migration backup:**
```bash
git add .
git commit -m "Pre-migration state"
git tag -a pre-flavor-migration -m "Before product flavor migration"
```

## Files That Change

### Modified
- `app/build.gradle.kts` - Add product flavors, update dependencies
- `settings.gradle.kts` - Remove old module references

### Deleted
- `app/mobile/build.gradle.kts`
- `app/tv/build.gradle.kts`
- `app/shared/build.gradle.kts`

### Moved/Reorganized
- All source files from `app/mobile/src/main/` → `app/src/mobile/`
- All source files from `app/tv/src/main/` → `app/src/tv/`
- All source files from `app/shared/src/main/` → `app/src/main/`

## Benefits

1. **Faster TV Builds**: Only TV code is compiled when building TV variants
2. **Standard Approach**: Uses Android's recommended product flavor pattern
3. **Better IDE Support**: Android Studio properly isolates variants
4. **Side-by-Side Testing**: Different app IDs allow both platforms on same device
5. **No Breaking Changes**: TV app ID remains the same

## Estimated Effort

- File moves and reorganization: ~30 minutes
- Build configuration updates: ~30 minutes
- Gradle sync and build testing: ~30 minutes
- Functional testing: ~30 minutes

**Total**: ~2 hours

## Success Criteria

- [ ] TV builds compile only TV code (verified by build output)
- [ ] TV app ID is unchanged (`dev.aaa1115910.bv`)
- [ ] Both TV and mobile variants build successfully
- [ ] Both platforms function correctly after migration
- [ ] Build time for TV is significantly reduced
