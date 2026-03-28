# Minimal Mode Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add compile-time configuration to simplify the TV app by hiding UGC and PGC menu items when in minimal mode.

**Architecture:** Add a `minimalMode` boolean field to `AppConfiguration.kt` and conditionally filter UGC/PGC menu items in `DrawerContent.kt` based on this flag.

**Tech Stack:** Kotlin, Android Gradle Plugin (buildSrc), Jetpack Compose

---

## File Structure

**Files to modify:**
1. `buildSrc/src/main/kotlin/AppConfiguration.kt` - Add `minimalMode` configuration field
2. `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt` - Conditionally include UGC/PGC menu items

---

## Task 1: Add minimalMode Configuration Field

**Files:**
- Modify: `buildSrc/src/main/kotlin/AppConfiguration.kt`

- [ ] **Step 1: Add minimalMode field to AppConfiguration**

Add the `minimalMode` field after the existing configuration fields (around line 26):

```kotlin
const val minimalMode: Boolean = false
```

This should be added after the `blacklistUrl` line and before the `init` block.

**Full context of where to add (lines 24-30):**
```kotlin
    var googleServicesAvailable = true
    const val blacklistUrl =
        "https://raw.githubusercontent.com/aaa1115910/bv-blacklist/main/blacklist.bin"
    const val minimalMode: Boolean = false

    init {
        initConfigurations()
    }
```

- [ ] **Step 2: Verify build configuration compiles**

Run: `./gradlew buildSrc:build --console=plain`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit the configuration change**

```bash
git add buildSrc/src/main/kotlin/AppConfiguration.kt
git commit -m "feat: add minimalMode configuration field

Add compile-time flag to control minimal mode for TV app simplification.
Defaults to false to maintain current behavior."
```

---

## Task 2: Conditionally Hide UGC and PGC Menu Items

**Files:**
- Modify: `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt:142-147`

- [ ] **Step 1: Add import for AppConfiguration**

Add the import at the top of the file (after line 1):

```kotlin
import dev.aaa1115910.bv.AppConfiguration
```

**Import section should look like (lines 1-2):**
```kotlin
package dev.aaa1115910.bv.tv.screens.main

import dev.aaa1115910.bv.AppConfiguration
```

- [ ] **Step 2: Modify menu item list to filter UGC/PGC based on minimalMode**

Replace the `listOf` call at lines 142-147 with a conditional build:

**Original code (lines 142-147):**
```kotlin
            listOf(
                DrawerItem.Search,
                DrawerItem.Home,
                DrawerItem.UGC,
                DrawerItem.PGC,
            ).forEach { item ->
```

**Replace with:**
```kotlin
            buildList {
                add(DrawerItem.Search)
                add(DrawerItem.Home)
                if (!AppConfiguration.minimalMode) {
                    add(DrawerItem.UGC)
                    add(DrawerItem.PGC)
                }
            }.forEach { item ->
```

- [ ] **Step 3: Verify the change compiles**

Run: `./gradlew :app:tv:compileDebugKotlin --console=plain`

Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit the UI change**

```bash
git add app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt
git commit -m "feat: conditionally hide UGC/PGC menu items in minimal mode

When minimalMode is enabled in AppConfiguration, UGC and PGC menu items
are hidden from the navigation drawer, providing a simplified experience."
```

---

## Task 3: Verify Implementation

**Files:**
- None (verification only)

- [ ] **Step 1: Build the app in normal mode (minimalMode = false)**

Run: `./gradlew :app:tv:assembleDebug --console=plain`

Expected: BUILD SUCCESSFUL

- [ ] **Step 2: Build the app in minimal mode**

First, temporarily change `minimalMode` to `true` in `buildSrc/src/main/kotlin/AppConfiguration.kt`:

```kotlin
const val minimalMode: Boolean = true
```

Then build: `./gradlew :app:tv:assembleDebug --console=plain`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Revert minimalMode to default (false)**

Restore: `buildSrc/src/main/kotlin/AppConfiguration.kt`

```kotlin
const val minimalMode: Boolean = false
```

- [ ] **Step 4: Final commit**

```bash
git add buildSrc/src/main/kotlin/AppConfiguration.kt
git commit -m "test: verify minimal mode builds successfully

Verified that the app builds successfully in both normal mode and
minimal mode configurations."
```

---

## Testing Checklist

After implementation, verify:

- [ ] With `minimalMode = false`: UGC and PGC menu items are visible in the drawer
- [ ] With `minimalMode = true`: UGC and PGC menu items are hidden
- [ ] Other menu items (Home, Search, Settings, User) remain visible in both modes
- [ ] Focus navigation works correctly with the reduced menu
- [ ] No compilation errors or warnings

---

## Usage

To enable minimal mode for a build:

1. Edit `buildSrc/src/main/kotlin/AppConfiguration.kt`
2. Set `const val minimalMode: Boolean = true`
3. Build the app: `./gradlew :app:tv:assembleDebug`

To disable minimal mode (default):

1. Edit `buildSrc/src/main/kotlin/AppConfiguration.kt`
2. Set `const val minimalMode: Boolean = false`
3. Build the app: `./gradlew :app:tv:assembleDebug`
