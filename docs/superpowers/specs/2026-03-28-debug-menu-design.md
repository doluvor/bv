# Debug Menu Section Design

**Date:** 2026-03-28
**Status:** Approved

## Overview

Add a new "Debug" navigation item to the video player menu that allows users to toggle the ExoPlayer debug info overlay on/off. This provides a user-accessible way to view player technical information without requiring a debug build.

## Requirements

### Functional Requirements
- Add a "Debug" section to the video player menu navigation
- Provide a toggle switch to show/hide the debug info overlay
- Persist the user's preference across app restarts
- Default to "off" (debug info hidden)

### Non-Functional Requirements
- Follow existing menu component patterns
- Maintain consistency with current preference storage approach
- Support both TV and mobile platforms (future consideration)

## Architecture

### Components

#### 1. VideoPlayerMenuNavItem Enum Extension
**File:** `player/shared/src/main/kotlin/dev/aaa1115910/bv/player/entity/VideoPlayerMenuNavItem.kt`

Add new enum value:
```kotlin
Debug(R.string.video_player_menu_nav_debug, Icons.Outlined.BugReport)
```

#### 2. DebugMenu Component
**File:** `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/playermenu/DebugMenu.kt` (new)

Create component following the pattern of `OthersMenu.kt`:
- Display toggle switch for "Show Debug Info"
- Handle focus state management
- Provide callback for state changes

#### 3. Preference Storage
**File:** `app/shared/src/main/kotlin/dev/aaa1115910/bv/util/Prefs.kt`

Add preference property:
```kotlin
var showDebugInfo: Boolean
    get() = runBlocking { dsm.getPreferenceFlow(PrefKeys.prefShowDebugInfoRequest).first() }
    set(value) = runBlocking { dsm.editPreference(PrefKeys.prefShowDebugInfoKey, value) }
```

Add to PrefKeys:
```kotlin
const val prefShowDebugInfoKey = "show_debug_info"
val prefShowDebugInfoRequest = prefShowDebugInfoKey.toRequest()
```

#### 4. State Management
**File:** `app/shared/src/main/kotlin/dev/aaa1115910/bv/viewmodel/VideoPlayerV3ViewModel.kt`

Add state property:
```kotlin
var currentShowDebugInfo by mutableStateOf(Prefs.showDebugInfo)
```

#### 5. Menu Controller Integration
**File:** `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/MenuController.kt`

- Add `onShowDebugInfoChange: (Boolean) -> Unit` callback parameter
- Add `Debug` case to `MenuList` when clause
- Pass callback through component hierarchy

#### 6. Debug Overlay Display Logic
**File:** `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/controller/VideoPlayerController.kt`

Modify debug info visibility condition:
```kotlin
if (BuildConfig.DEBUG || videoPlayerConfigData.currentShowDebugInfo) {
    // debug info overlay
}
```

#### 7. Local Data Provider
**File:** `player/tv/src/main/kotlin/dev/aaa1115910/bv/player/tv/BvPlayer.kt`

Add to `LocalVideoPlayerConfigData`:
```kotlin
currentShowDebugInfo = playerViewModel.currentShowDebugInfo
```

#### 8. Screen Integration
**File:** `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/VideoPlayerV3Screen.kt`

Add callback handler:
```kotlin
onShowDebugInfoChange = { enabled ->
    Prefs.showDebugInfo = enabled
    playerViewModel.currentShowDebugInfo = enabled
}
```

### Data Flow

```
User toggles switch in DebugMenu
    ↓
onShowDebugInfoChange callback invoked
    ↓
Update Prefs.showDebugInfo (persisted)
    ↓
Update playerViewModel.currentShowDebugInfo
    ↓
VideoPlayerController recomposes
    ↓
Debug overlay visibility updates
```

## String Resources

Add to `player/shared/src/main/res/strings.xml`:
```xml
<string name="video_player_menu_nav_debug">Debug</string>
<string name="video_player_menu_debug_show_info">Show Debug Info</string>
```

## Implementation Order

1. Add preference storage (Prefs.kt, PrefKeys)
2. Add menu navigation item (VideoPlayerMenuNavItem)
3. Create DebugMenu component
4. Update MenuController integration
5. Update VideoPlayerController display logic
6. Update BvPlayer data provider
7. Update VideoPlayerV3Screen callback
8. Add string resources

## Testing Considerations

- Verify toggle switches debug overlay on/off
- Verify preference persists across app restarts
- Verify default state is "off"
- Verify debug overlay displays correctly when enabled
- Verify menu navigation works with new Debug item

## Future Enhancements

- Consider adding mobile platform support
- Consider adding more debug information options
- Consider adding performance metrics display
