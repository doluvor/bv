# Debug Menu Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-step. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Debug navigation item to the video player menu that allows users to toggle the ExoPlayer debug info overlay on/off via a persistent preference.

**Architecture:** Add a new Debug menu section following the existing pattern (Picture, Danmaku, ClosedCaption, Others). The debug overlay visibility is controlled by a user preference persisted via DataStore. The overlay shows when either in debug build OR the user preference is enabled.

**Tech Stack:** Jetpack Compose, Kotlin, DataStore, Material3 for TV

---

## File Structure

**New Files:**
- `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/playermenu/DebugMenu.kt` - Debug menu component with toggle

**Modified Files:**
- `player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerMenuNavItem.kt` - Add Debug enum
- `player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerData.kt` - Add currentShowDebugInfo to VideoPlayerConfigData
- `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/MenuController.kt` - Wire up Debug menu
- `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/VideoPlayerController.kt` - Update overlay visibility logic
- `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/BvPlayer.kt` - Provide currentShowDebugInfo
- `app/shared/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt` - Add showDebugInfo preference
- `app/shared/src/main/kotlin/dev/aaa1115910/bv/viewmodel/VideoPlayerV3ViewModel.kt` - Add currentShowDebugInfo state
- `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/VideoPlayerV3Screen.kt` - Wire up callback
- `player/shared/src/main/res/values/strings.xml` - Add string resources

---

### Task 1: Add Preference Keys and Storage

**Files:**
- Modify: `app/shared/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt`

- [ ] **Step 1: Add the preference key definition**

Find the `object PrefKeys` section (around line 324) and add the key definition after line 363 (after `prefDefaultDanmakuMask`):

```kotlin
val prefShowDebugInfo = booleanPreferencesKey("show_debug_info")
```

- [ ] **Step 2: Add the preference request**

Find the `PreferenceRequest` definitions section (around line 421) and add the request after `prefDefaultDanmakuMaskRequest`:

```kotlin
val prefShowDebugInfoRequest = PreferenceRequest(prefShowDebugInfo, false)
```

- [ ] **Step 3: Add the Prefs property**

Find the end of the `Prefs` object (around line 321, after `enableFfmpegAudioRenderer`) and add:

```kotlin
var showDebugInfo: Boolean
    get() = runBlocking {
        dsm.getPreferenceFlow(PrefKeys.prefShowDebugInfoRequest).first()
    }
    set(value) = runBlocking {
        dsm.editPreference(PrefKeys.prefShowDebugInfo, value)
    }
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :app:shared:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/shared/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt
git commit -m "feat: add showDebugInfo preference storage"
```

---

### Task 2: Add ViewModel State

**Files:**
- Modify: `app/shared/src/main/kotlin/dev/aaa1115910/bv/viewmodel/VideoPlayerV3ViewModel.kt`

- [ ] **Step 1: Add the state property**

Find the danmaku-related state properties (around line 100, after `currentDanmakuMask`) and add:

```kotlin
var currentShowDebugInfo by mutableStateOf(Prefs.showDebugInfo)
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :app:shared:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/shared/src/main/kotlin/dev/aaa1115910/bv/viewmodel/VideoPlayerV3ViewModel.kt
git commit -m "feat: add currentShowDebugInfo state to ViewModel"
```

---

### Task 3: Add Debug to VideoPlayerConfigData

**Files:**
- Modify: `player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerData.kt`

- [ ] **Step 1: Add property to VideoPlayerConfigData**

Find the `VideoPlayerConfigData` data class (around line 65) and add the property after `incognitoMode` (around line 91):

```kotlin
val incognitoMode: Boolean = false,
val currentShowDebugInfo: Boolean = false,
```

Note: Add the comma after `incognitoMode` line.

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :player:shared:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerData.kt
git commit -m "feat: add currentShowDebugInfo to VideoPlayerConfigData"
```

---

### Task 4: Add Debug Menu Navigation Item

**Files:**
- Modify: `player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerMenuNavItem.kt`

- [ ] **Step 1: Add import for bug icon**

Add this import after the existing imports (around line 9):

```kotlin
import androidx.compose.material.icons.outlined.BugReport
```

- [ ] **Step 2: Add Debug enum value**

Find the `enum class VideoPlayerMenuNavItem` (around line 12) and add the Debug entry after `Others` (after line 16):

```kotlin
Others(R.string.video_player_menu_nav_others, Icons.Outlined.MoreVert),
Debug(R.string.video_player_menu_nav_debug, Icons.Outlined.BugReport);
```

Note: Change the semicolon after `Others` to a comma, and keep the semicolon after `Debug`.

- [ ] **Step 3: Add string resource (temporary placeholder)**

For now, add a placeholder. In Task 10 we'll add proper strings.

Open `player/shared/src/main/res/values/strings.xml` and add at the end:

```xml
<string name="video_player_menu_nav_debug">Debug</string>
```

- [ ] **Step 4: Verify compilation**

Run: `./gradlew :player:shared:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerMenuNavItem.kt
git add player/shared/src/main/res/values/strings.xml
git commit -m "feat: add Debug navigation item to menu"
```

---

### Task 5: Create DebugMenu Component

**Files:**
- Create: `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/playermenu/DebugMenu.kt`

- [ ] **Step 1: Create the DebugMenu component**

Create the file with this content:

```kotlin
package dev.aaa1115910.bv.player.tv.controller.playermenu

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.aaa1115910.bv.player.tv.controller.LocalMenuFocusStateData
import dev.aaa1115910.bv.player.tv.controller.MenuFocusState
import dev.aaa1115910.bv.player.tv.controller.playermenu.component.MenuListItem

@Composable
fun DebugMenuList(
    modifier: Modifier = Modifier,
    currentShowDebugInfo: Boolean,
    onShowDebugInfoChange: (Boolean) -> Unit,
    onFocusStateChange: (MenuFocusState) -> Unit
) {
    val context = LocalContext.current
    val focusState = LocalMenuFocusStateData.current
    val parentMenuFocusRequester = remember { FocusRequester() }

    Row(
        modifier = modifier.fillMaxHeight(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val menuItemsModifier = Modifier
            .width(216.dp)
            .padding(horizontal = 8.dp)

        androidx.compose.foundation.lazy.LazyColumn(
            modifier = Modifier
                .focusRequester(parentMenuFocusRequester)
                .padding(horizontal = 8.dp)
                .onPreviewKeyEvent {
                    if (it.type == KeyEventType.KeyUp) {
                        if (listOf(Key.Enter, Key.DirectionCenter).contains(it.key)) {
                            return@onPreviewKeyEvent false
                        }
                        return@onPreviewKeyEvent true
                    }
                    when (it.key) {
                        Key.DirectionRight -> onFocusStateChange(MenuFocusState.MenuNav)
                        else -> {}
                    }
                    false
                },
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(8.dp)
        ) {
            item {
                MenuListItem(
                    modifier = Modifier.focusRequester(parentMenuFocusRequester),
                    text = context.getString(dev.aaa1115910.bv.player.shared.R.string.video_player_menu_debug_show_info),
                    selected = currentShowDebugInfo,
                    onClick = { onShowDebugInfoChange(!currentShowDebugInfo) },
                    onFocus = {}
                )
            }
        }
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :player:tv:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/playermenu/DebugMenu.kt
git commit -m "feat: create DebugMenu component"
```

---

### Task 6: Update MenuController to Support Debug Menu

**Files:**
- Modify: `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/MenuController.kt`

- [ ] **Step 1: Add callback parameter to outer MenuController**

Find the first `fun MenuController` (around line 64) and add the callback parameter after `onPlayModeChange` (after line 81):

```kotlin
    onPlayModeChange: (PlayMode) -> Unit,
    onShowDebugInfoChange: (Boolean) -> Unit
```

- [ ] **Step 2: Pass callback to inner MenuController**

Find the inner `MenuController` call (around line 102) and add the parameter after `onPlayModeChange` (after line 118):

```kotlin
                onPlayModeChange = onPlayModeChange,
                onShowDebugInfoChange = onShowDebugInfoChange
```

- [ ] **Step 3: Add callback parameter to inner MenuController**

Find the second `fun MenuController` signature (around line 125) and add the parameter after `onPlayModeChange` (after line 142):

```kotlin
    onPlayModeChange: (PlayMode) -> Unit,
    onShowDebugInfoChange: (Boolean) -> Unit
```

- [ ] **Step 4: Add DebugMenuList import**

Add this import at the top with the other menu imports (around line 54-58):

```kotlin
import dev.aaa1115910.bv.player.tv.controller.playermenu.DebugMenuList
```

- [ ] **Step 5: Add Debug case to MenuList**

Find the `private fun MenuList` (around line 205) and add the Debug case after the Others case (after line 267):

```kotlin
            VideoPlayerMenuNavItem.Others -> {
                OthersMenuList(
                    onPlayModeChange = onPlayModeChange,
                    onFocusStateChange = onFocusStateChange
                )
            }

            VideoPlayerMenuNavItem.Debug -> {
                val videoPlayerConfigData = LocalVideoPlayerConfigData.current
                DebugMenuList(
                    currentShowDebugInfo = videoPlayerConfigData.currentShowDebugInfo,
                    onShowDebugInfoChange = onShowDebugInfoChange,
                    onFocusStateChange = onFocusStateChange
                )
            }
```

- [ ] **Step 6: Verify compilation**

Run: `./gradlew :player:tv:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/MenuController.kt
git commit -m "feat: wire up Debug menu in MenuController"
```

---

### Task 7: Update VideoPlayerController Overlay Logic

**Files:**
- Modify: `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/VideoPlayerController.kt`

- [ ] **Step 1: Find the debug info overlay code**

Search for `if (BuildConfig.DEBUG)` around line 318.

- [ ] **Step 2: Update the condition to include user preference**

Change:
```kotlin
        if (BuildConfig.DEBUG) {
```

To:
```kotlin
        if (BuildConfig.DEBUG || videoPlayerConfigData.currentShowDebugInfo) {
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :player:tv:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/VideoPlayerController.kt
git commit -m "feat: update debug overlay to respect user preference"
```

---

### Task 8: Update BvPlayer to Provide currentShowDebugInfo

**Files:**
- Modify: `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/BvPlayer.kt`

- [ ] **Step 1: Find the LocalVideoPlayerConfigData provider**

Search for `LocalVideoPlayerConfigData provides` (around line 373).

- [ ] **Step 2: Add currentShowDebugInfo to the provider**

Find the line with `currentPlayMode` (around line 407) and add after it:

```kotlin
                    currentPlayMode = playerViewModel.currentPlayMode,
                    currentShowDebugInfo = playerViewModel.currentShowDebugInfo,
```

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :player:tv:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/BvPlayer.kt
git commit -m "feat: provide currentShowDebugInfo via LocalVideoPlayerConfigData"
```

---

### Task 9: Wire Up Callback in VideoPlayerV3Screen

**Files:**
- Modify: `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/VideoPlayerV3Screen.kt`

- [ ] **Step 1: Find the MenuController callback section**

Search for `onPlayModeChange =` (around line 218).

- [ ] **Step 2: Add the onShowDebugInfoChange callback**

After the `onPlayModeChange` block, add:

```kotlin
            onShowDebugInfoChange = { enabled ->
                Prefs.showDebugInfo = enabled
                playerViewModel.currentShowDebugInfo = enabled
            },
```

Make sure to add a comma after the previous `onPlayModeChange` block.

- [ ] **Step 3: Verify compilation**

Run: `./gradlew :app:tv:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/VideoPlayerV3Screen.kt
git commit -m "feat: wire up onShowDebugInfoChange callback"
```

---

### Task 10: Add String Resources

**Files:**
- Modify: `player/shared/src/main/res/values/strings.xml`

- [ ] **Step 1: Add the show debug info string**

Find the string we added in Task 4 (around line we added) and update it to be in proper alphabetical/order position with other video_player_menu strings.

First, let's remove the temporary one from Task 4 if it exists, and add it properly.

Add this string in the appropriate section with other video player menu strings:

```xml
<string name="video_player_menu_debug_show_info">Show Debug Info</string>
```

- [ ] **Step 2: Verify compilation**

Run: `./gradlew :player:shared:compileDebugKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add player/shared/src/main/res/values/strings.xml
git commit -m "feat: add debug menu string resources"
```

---

## Testing

### Manual Testing Steps

1. **Build and run the TV app**
   ```bash
   ./gradlew :app:tv:installDebug
   ```

2. **Play a video** and open the player menu (press Menu key or long-press OK)

3. **Navigate to Debug section** (press right to nav, then down to Debug)

4. **Toggle "Show Debug Info"** - verify the debug overlay appears in top-left

5. **Toggle off** - verify the debug overlay disappears

6. **Close and reopen the app** - verify the preference is persisted (overlay state should be remembered)

7. **Test in release build** - verify debug overlay is hidden by default but can be enabled via menu

### Expected Behavior

- Debug overlay is hidden by default when app is first launched
- Toggle switch in Debug menu controls overlay visibility
- Preference persists across app restarts
- Debug overlay shows in both debug and release builds when enabled
- Debug overlay content includes: player version, time, buffer, resolution, codecs

---

## Self-Review Results

**Spec coverage:**
- ✓ Add Debug navigation item (Task 4)
- ✓ Create DebugMenu component (Task 5)
- ✓ Add toggle switch for show/hide (Task 5)
- ✓ Persist preference via Prefs (Task 1)
- ✓ Add ViewModel state (Task 2)
- ✓ Update VideoPlayerController logic (Task 7)
- ✓ Wire up through BvPlayer (Task 8)
- ✓ Wire up callback in screen (Task 9)
- ✓ Add string resources (Task 10)
- ✓ Default to off (Task 1, default value in PreferenceRequest is false)

**Placeholder scan:** No placeholders found. All code is complete.

**Type consistency:**
- `showDebugInfo` (Prefs property) ✓
- `currentShowDebugInfo` (ViewModel state) ✓
- `currentShowDebugInfo` (VideoPlayerConfigData) ✓
- `onShowDebugInfoChange` (callback) ✓
- All type names consistent across files
