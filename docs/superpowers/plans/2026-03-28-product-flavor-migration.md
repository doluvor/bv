# Product Flavor Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert BV app from multi-module structure (app/shared, app/mobile, app/tv) to single-module product flavor structure for faster TV builds.

**Architecture:** Consolidate three library modules into one application module with product flavors. Shared code goes to `main/` source set, platform-specific code goes to flavor source sets (`mobile/`, `tv/`).

**Tech Stack:** Gradle Kotlin DSL, Android Gradle Plugin, product flavors, source sets

---

## Task 1: Create Pre-Migration Backup

**Files:**
- None (git operation)

- [ ] **Step 1: Commit any uncommitted changes**

Run: `git status`
If there are changes, commit them:
```bash
git add .
git commit -m "Pre-migration cleanup"
```

- [ ] **Step 2: Create pre-migration tag**

Run: `git tag -a pre-flavor-migration -m "Before product flavor migration"`

- [ ] **Step 3: Verify tag was created**

Run: `git tag -l | grep pre-flavor-migration`
Expected: `pre-flavor-migration`

- [ ] **Step 4: Push tag to remote (optional)**

Run: `git push origin pre-flavor-migration`

---

## Task 2: Create New Source Set Directories

**Files:**
- Create: `app/src/mobile/`
- Create: `app/src/tv/`

- [ ] **Step 1: Create mobile source set directory**

Run: `mkdir -p app/src/mobile`

- [ ] **Step 2: Create tv source set directory**

Run: `mkdir -p app/src/tv`

- [ ] **Step 3: Verify directories were created**

Run: `ls -la app/src/`
Expected output should include `main/`, `mobile/`, `tv/`

- [ ] **Step 4: Commit directory structure**

```bash
git add app/src/
git commit -m "feat: create mobile and tv source set directories"
```

---

## Task 3: Move Shared Code to Main Source Set

**Files:**
- Move: `app/shared/src/main/` → `app/src/main/`

- [ ] **Step 1: Check what's in shared/src/main/**

Run: `ls -la app/shared/src/main/`

- [ ] **Step 2: Copy shared code to main source set**

Run: `cp -r app/shared/src/main/* app/src/main/`

- [ ] **Step 3: Verify files were copied**

Run: `ls -la app/src/main/`
Expected: Should contain `java/`, `res/` (if any)

- [ ] **Step 4: Verify Kotlin files are in correct location**

Run: `find app/src/main -name "*.kt" | head -5`
Expected: Should list Kotlin files from shared module

- [ ] **Step 5: Commit shared code move**

```bash
git add app/src/main/
git commit -m "feat: move shared code to main source set"
```

---

## Task 4: Move Mobile Code to Mobile Source Set

**Files:**
- Move: `app/mobile/src/main/` → `app/src/mobile/`

- [ ] **Step 1: Check what's in mobile/src/main/**

Run: `ls -la app/mobile/src/main/`

- [ ] **Step 2: Copy mobile code to mobile source set**

Run: `cp -r app/mobile/src/main/* app/src/mobile/`

- [ ] **Step 3: Verify files were copied**

Run: `ls -la app/src/mobile/`
Expected: Should contain `java/`, `res/`, `AndroidManifest.xml`

- [ ] **Step 4: Verify Kotlin files are in correct location**

Run: `find app/src/mobile -name "*.kt" | head -5`
Expected: Should list mobile Kotlin files

- [ ] **Step 5: Commit mobile code move**

```bash
git add app/src/mobile/
git commit -m "feat: move mobile code to mobile source set"
```

---

## Task 5: Move TV Code to TV Source Set

**Files:**
- Move: `app/tv/src/main/` → `app/src/tv/`

- [ ] **Step 1: Check what's in tv/src/main/**

Run: `ls -la app/tv/src/main/`

- [ ] **Step 2: Copy tv code to tv source set**

Run: `cp -r app/tv/src/main/* app/src/tv/`

- [ ] **Step 3: Verify files were copied**

Run: `ls -la app/src/tv/`
Expected: Should contain `java/`, `res/`, `AndroidManifest.xml`

- [ ] **Step 4: Verify Kotlin files are in correct location**

Run: `find app/src/tv -name "*.kt" | head -5`
Expected: Should list TV Kotlin files

- [ ] **Step 5: Commit tv code move**

```bash
git add app/src/tv/
git commit -m "feat: move tv code to tv source set"
```

---

## Task 6: Read and Analyze Shared Build Configuration

**Files:**
- Read: `app/shared/build.gradle.kts`

- [ ] **Step 1: Read shared build.gradle.kts content**

This is a read-only step to understand what dependencies need to be moved.

Run: `cat app/shared/build.gradle.kts`

Note: You should see ~70 `api()` dependencies and BuildConfig fields that need to be copied to the main app module.

---

## Task 7: Update Main Build Configuration - Add Product Flavors

**Files:**
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Read current app/build.gradle.kts**

Read the file to understand current structure.

- [ ] **Step 2: Add flavor dimension to android block**

Find the `android {` block in `app/build.gradle.kts` and add the flavor dimension after the existing `flavorDimensions.add("channel")` line:

```kotlin
android {
    // ... existing code ...

    flavorDimensions.add("channel")

    // ADD THIS:
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

    // ... rest of android block ...
}
```

- [ ] **Step 3: Remove old module dependencies**

In the `dependencies` block, remove these lines:
```kotlin
implementation(project(":app:mobile"))
implementation(project(":app:tv"))
```

- [ ] **Step 4: Commit product flavor configuration**

```bash
git add app/build.gradle.kts
git commit -m "feat: add product flavors for mobile and tv"
```

---

## Task 8: Copy Shared Dependencies to Main Build Configuration

**Files:**
- Modify: `app/build.gradle.kts`
- Reference: `app/shared/build.gradle.kts`

- [ ] **Step 1: Copy dependencies from shared to app**

Copy ALL dependencies from `app/shared/build.gradle.kts` (lines 95-170) to the `dependencies` block in `app/build.gradle.kts`.

The shared module has these dependencies that need to be added:
- All `api()` declarations become `implementation()` or stay as `api()` if transitive exposure is needed
- All `ksp()` and annotation processor dependencies
- Test dependencies

Add them to the `dependencies` block in `app/build.gradle.kts`:

```kotlin
dependencies {
    // ... existing dependencies ...

    // ADD ALL DEPENDENCIES FROM app/shared/build.gradle.kts
    annotationProcessor(androidx.room.compiler)
    ksp(androidx.room.compiler)
    ksp(libs.koin.ksp.compiler)
    api(platform("${libs.firebase.bom.get()}"))
    api(androidx.activity.compose)
    // ... continue with all ~70 dependencies from shared module
}
```

- [ ] **Step 2: Copy BuildConfig fields**

Copy these BuildConfig fields from `app/shared/build.gradle.kts` (in the `defaultConfig` block) to `app/build.gradle.kts`:

```kotlin
defaultConfig {
    // ... existing config ...

    // ADD THESE FROM app/shared/build.gradle.kts:
    buildConfigField("int", "VERSION_CODE", "${AppConfiguration.versionCode}")
    buildConfigField("String", "VERSION_NAME", "\"${AppConfiguration.versionName}\"")
    buildConfigField("String", "APPLICATION_ID", "\"${AppConfiguration.appId}\"")
    buildConfigField("String", "BLACKLIST_URL", "\"${AppConfiguration.blacklistUrl}\"")
}
```

- [ ] **Step 3: Add protobuf configuration (if not present)**

If `app/build.gradle.kts` doesn't have protobuf configuration, add it from `app/shared/build.gradle.kts`:

```kotlin
protobuf {
    protoc {
        artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
    }
    generateProtoTasks {
        all().forEach { task ->
            task.builtins {
                create("java")
                create("kotlin")
            }
        }
    }
}
```

- [ ] **Step 4: Add blacklist download task (if not present)**

Copy from `app/shared/build.gradle.kts`:

```kotlin
tasks.register("downloadBlacklist") {
    val assetsDir = file("src/main/res/raw")
    val resourceUrl = AppConfiguration.blacklistUrl
    val outputFile = File(assetsDir, "blacklist.bin")

    if (outputFile.exists()) return@register

    doLast {
        if (!assetsDir.exists()) {
            assetsDir.mkdirs()
        }
        println("Downloading resource from $resourceUrl to ${outputFile.absolutePath}")
        java.net.URI(resourceUrl).toURL().openStream().use { input ->
            outputFile.outputStream().use { output ->
                input.copyTo(output)
            }
            println("Download complete: ${outputFile.absolutePath}")
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn("downloadBlacklist")
}
```

- [ ] **Step 5: Commit dependency updates**

```bash
git add app/build.gradle.kts
git commit -m "feat: copy dependencies and config from shared module"
```

---

## Task 9: Update settings.gradle.kts

**Files:**
- Modify: `settings.gradle.kts`

- [ ] **Step 1: Read current settings.gradle.kts**

Run: `cat settings.gradle.kts`

- [ ] **Step 2: Remove old module references**

Remove these lines from `settings.gradle.kts`:
```kotlin
include(":app:shared")
include(":app:mobile")
include(":app:tv")
```

Note: The main `:app` module should remain included.

- [ ] **Step 3: Verify settings.gradle.kts**

Run: `cat settings.gradle.kts`
Expected: Should NOT include `:app:shared`, `:app:mobile`, or `:app:tv`

- [ ] **Step 4: Commit settings update**

```bash
git add settings.gradle.kts
git commit -m "feat: remove old module references from settings"
```

---

## Task 10: Sync Gradle and Resolve Build Errors

**Files:**
- None (gradle sync)

- [ ] **Step 1: Clean build**

Run: `./gradlew clean`

- [ ] **Step 2: Sync Gradle**

If using Android Studio: Click "Sync Project with Gradle Files"
Or run: `./gradlew tasks --all`

- [ ] **Step 3: Check for build errors**

Run: `./gradlew assembleTvDebug`

Expected: May have errors related to missing resources, imports, or manifest merging - we'll fix these in next tasks.

- [ ] **Step 4: Note any errors for next tasks**

Write down any errors you see. Common issues:
- Missing resources in main source set
- Manifest merge conflicts
- Missing namespace declarations

- [ ] **Step 5: Commit if sync successful**

If sync succeeded without errors:
```bash
git add .
git commit -m "chore: gradle sync successful"
```

---

## Task 11: Verify Namespace Declarations

**Files:**
- Check: `app/src/main/AndroidManifest.xml`
- Check: `app/src/mobile/AndroidManifest.xml`
- Check: `app/src/tv/AndroidManifest.xml`

- [ ] **Step 1: Check main AndroidManifest.xml**

Run: `cat app/src/main/AndroidManifest.xml`

If it's just an empty manifest or doesn't have a namespace, that's OK - the namespace is defined in build.gradle.kts.

- [ ] **Step 2: Verify mobile manifest has correct namespace**

The mobile manifest should reference mobile activities correctly. Check that the package references are correct.

- [ ] **Step 3: Verify tv manifest has correct namespace**

The TV manifest should reference TV activities correctly. Check that:
- Leanback features are declared
- TV launcher intent filter is present

- [ ] **Step 4: Commit any manifest fixes**

```bash
git add app/src/*/AndroidManifest.xml
git commit -m "fix: correct namespace declarations in manifests"
```

---

## Task 12: Build TV Variant and Fix Issues

**Files:**
- Build output

- [ ] **Step 1: Attempt TV build**

Run: `./gradlew assembleTvDebug`

- [ ] **Step 2: Review any build errors**

Common issues to fix:
- Missing resources: Copy from `app/mobile/src/main/res/` or `app/tv/src/main/res/` to `app/src/main/res/`
- Import errors: Fix package references in moved files
- Missing BuildConfig: Ensure BuildConfig is enabled in build features

- [ ] **Step 3: Fix resource conflicts (if any)**

If there are resource conflicts between mobile and TV:
- Keep conflicting resources in flavor source sets
- Move truly shared resources to main source set

- [ ] **Step 4: Rebuild until successful**

Run: `./gradlew assembleTvDebug`

Repeat until build succeeds.

- [ ] **Step 5: Commit fixes**

```bash
git add .
git commit -m "fix: resolve TV build issues"
```

---

## Task 13: Build Mobile Variant and Fix Issues

**Files:**
- Build output

- [ ] **Step 1: Attempt mobile build**

Run: `./gradlew assembleMobileDebug`

- [ ] **Step 2: Review any build errors**

Fix similar issues as Task 12 but for mobile variant.

- [ ] **Step 3: Rebuild until successful**

Run: `./gradlew assembleMobileDebug`

Repeat until build succeeds.

- [ ] **Step 4: Commit fixes**

```bash
git add .
git commit -m "fix: resolve mobile build issues"
```

---

## Task 14: Verify TV App ID is Unchanged

**Files:**
- Check: Build output APK

- [ ] **Step 1: Check TV APK app ID**

Run: `./gradlew assembleTvDebug && aapt dump badging app/build/outputs/apk/tv/debug/*.apk | grep package`

Expected output should show:
`package: name='dev.aaa1115910.bv' ...`

- [ ] **Step 2: Verify no applicationIdSuffix**

The app ID should be exactly `dev.aaa1115910.bv` with no suffix.

- [ ] **Step 3: Check mobile APK app ID**

Run: `aapt dump badging app/build/outputs/apk/mobile/debug/*.apk | grep package`

Expected output should show:
`package: name='dev.aaa1115910.bv.mobile' ...`

- [ ] **Step 4: Document verified app IDs**

```bash
echo "TV App ID: dev.aaa1115910.bv" >> MIGRATION_NOTES.md
echo "Mobile App ID: dev.aaa1115910.bv.mobile" >> MIGRATION_NOTES.md
git add MIGRATION_NOTES.md
git commit -m "docs: record verified app IDs after migration"
```

---

## Task 15: Functional Testing - TV Variant

**Files:**
- Test on device/emulator

- [ ] **Step 1: Install TV APK on device/emulator**

Run: `adb install app/build/outputs/apk/tv/debug/*.apk`

- [ ] **Step 2: Launch TV app**

Run: `adb shell am start -n dev.aaa1115910.bv.tv.activities.MainActivity`

- [ ] **Step 3: Test basic functionality**

Test these features:
- [ ] App launches successfully
- [ ] Video playback works
- [ ] Login works
- [ ] Settings work
- [ ] Navigation works

- [ ] **Step 4: Check for runtime errors**

Run: `adb logcat | grep -i "dev.aaa1115910"`

- [ ] **Step 5: Document test results**

```bash
echo "TV Functional Testing: PASSED" >> MIGRATION_NOTES.md
git add MIGRATION_NOTES.md
git commit -m "test: TV variant functional testing passed"
```

---

## Task 16: Functional Testing - Mobile Variant

**Files:**
- Test on device/emulator

- [ ] **Step 1: Install mobile APK on device/emulator**

Run: `adb install app/build/outputs/apk/mobile/debug/*.apk`

- [ ] **Step 2: Launch mobile app**

Run: `adb shell am start -n dev.aaa1115910.bv.mobile.activities.MainActivity`

- [ ] **Step 3: Test basic functionality**

Test these features:
- [ ] App launches successfully
- [ ] Video playback works
- [ ] Login works
- [ ] Settings work
- [ ] Navigation works

- [ ] **Step 4: Check for runtime errors**

Run: `adb logcat | grep -i "dev.aaa1115910.bv"`

- [ ] **Step 5: Document test results**

```bash
echo "Mobile Functional Testing: PASSED" >> MIGRATION_NOTES.md
git add MIGRATION_NOTES.md
git commit -m "test: mobile variant functional testing passed"
```

---

## Task 17: Clean Up Old Module Directories

**Files:**
- Delete: `app/mobile/`
- Delete: `app/tv/`
- Delete: `app/shared/`

- [ ] **Step 1: Verify everything works before deletion**

Run: `./gradlew clean assembleTvDebug assembleMobileDebug`

Expected: Both builds should succeed.

- [ ] **Step 2: Delete old mobile module**

Run: `rm -rf app/mobile`

- [ ] **Step 3: Delete old tv module**

Run: `rm -rf app/tv`

- [ ] **Step 4: Delete old shared module**

Run: `rm -rf app/shared`

- [ ] **Step 5: Verify builds still work**

Run: `./gradlew clean assembleTvDebug assembleMobileDebug`

Expected: Both builds should still succeed.

- [ ] **Step 6: Commit cleanup**

```bash
git add -A
git commit -m "chore: remove old module directories"
```

---

## Task 18: Verify Build Time Improvement

**Files:**
- Build timing data

- [ ] **Step 1: Time a clean TV build**

Run: `time ./gradlew clean assembleTvDebug`

Note the time taken.

- [ ] **Step 2: Document build time**

```bash
echo "TV clean build time: [record time from above]" >> MIGRATION_NOTES.md
```

- [ ] **Step 3: Create summary of migration**

Create final summary in MIGRATION_NOTES.md:

```markdown
# Product Flavor Migration Summary

## Completed Tasks
- [x] Created backup tag: pre-flavor-migration
- [x] Moved shared code to main source set
- [x] Moved mobile code to mobile source set
- [x] Moved tv code to tv source set
- [x] Added product flavors to build configuration
- [x] Updated dependencies
- [x] Removed old module references
- [x] Fixed build issues
- [x] Verified app IDs (TV: dev.aaa1115910.bv, Mobile: dev.aaa1115910.bv.mobile)
- [x] Functional testing passed

## Build Time Improvement
TV clean build time: [fill in]

## Final Structure
app/src/
├── main/     (shared code)
├── mobile/   (mobile-specific code)
└── tv/       (tv-specific code)
```

- [ ] **Step 4: Commit final documentation**

```bash
git add MIGRATION_NOTES.md
git commit -m "docs: complete migration summary"
```

---

## Task 19: Final Verification and Tag

**Files:**
- Git tag

- [ ] **Step 1: Run all tests**

Run: `./gradlew test`

- [ ] **Step 2: Build all variants**

Run: `./gradlew assembleDebug`

Expected: All debug variants should build successfully.

- [ ] **Step 3: Create post-migration tag**

```bash
git tag -a post-flavor-migration -m "After product flavor migration - all tests passing"
```

- [ ] **Step 4: Push tags and changes**

```bash
git push origin develop-lite
git push origin pre-flavor-migration
git push origin post-flavor-migration
```

- [ ] **Step 5: Verify in Android Studio**

Open Android Studio and verify:
- Build Variants tab shows mobile and tv variants
- Selecting tvDebug and running works
- Selecting mobileDebug and running works

---

## Task 20: Update Documentation

**Files:**
- Update: Any relevant README or documentation

- [ ] **Step 1: Check for README files**

Run: `find . -name "README*" -o -name "CONTRIBUTING*"`

- [ ] **Step 2: Update build instructions in README**

If there's a README with build instructions, update it to reflect the new structure:

Old:
```bash
./gradlew assembleDebug
```

New:
```bash
./gradlew assembleTvDebug      # Build TV variant
./gradlew assembleMobileDebug  # Build mobile variant
```

- [ ] **Step 3: Commit documentation updates**

```bash
git add README*
git commit -m "docs: update build instructions for product flavors"
```

---

## Rollback Instructions

If at any point you need to rollback:

```bash
# Reset to pre-migration state
git reset --hard pre-flavor-migration

# Or if you need to keep some commits
git revert --no-commit post-flavor-migration^..HEAD
git commit -m "Rollback: product flavor migration"
```

---

## Testing Checklist

After completing all tasks, verify:

- [ ] `./gradlew assembleTvDebug` succeeds
- [ ] `./gradlew assembleMobileDebug` succeeds
- [ ] `./gradlew assembleTvRelease` succeeds
- [ ] `./gradlew assembleMobileRelease` succeeds
- [ ] TV APK has correct app ID: `dev.aaa1115910.bv`
- [ ] Mobile APK has correct app ID: `dev.aaa1115910.bv.mobile`
- [ ] TV app installs and runs correctly
- [ ] Mobile app installs and runs correctly
- [ ] Video playback works on both platforms
- [ ] Login works on both platforms
- [ ] Settings work on both platforms
- [ ] No lint errors or warnings
- [ ] Build time for TV is noticeably faster

---

## Completion Criteria

Migration is complete when:
1. All 20 tasks are checked off
2. Both TV and mobile variants build successfully
3. Functional testing passes for both platforms
4. TV app ID is unchanged
5. Build time improvement is verified
6. Documentation is updated
7. All tests pass
