# Minimal Mode for TV App

**Date:** 2026-03-28
**Status:** Approved

## Overview

Add a `minimalMode` compile-time configuration flag to simplify the TV app. When enabled, the app hides UGC and PGC menu items from the left sidebar navigation drawer, providing a streamlined navigation experience. This generic approach allows for future simplifications beyond just menu items.

## Requirements

### Functional Requirements
- Add a `minimalMode` boolean field to `AppConfiguration.kt` (default: `false`)
- When `minimalMode = true`, hide UGC and PGC menu items from the navigation drawer
- When `minimalMode = false`, show all menu items (current behavior)
- Other menu items (Home, Search, Settings, User) must remain visible in both modes

### Non-Functional Requirements
- Configuration must be compile-time (BuildConfig-based)
- No runtime performance impact
- Must integrate with existing `AppConfiguration` system
- Flag should be generic for future extensibility

## Architecture

### Configuration Layer
**File:** `buildSrc/src/main/kotlin/AppConfiguration.kt`

Add the minimal mode flag:
```kotlin
val minimalMode: Boolean = false
```

### UI Layer
**File:** `app/tv/src/main/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt`

Conditionally include UGC and PGC menu items:
```kotlin
// Only show UGC/PGC when NOT in minimal mode
if (!AppConfiguration.minimalMode) {
    // UGC menu item
    // PGC menu item
}
```

### Data Flow
```
AppConfiguration.minimalMode
         ↓
DrawerContent: if (!minimalMode) include UGC/PGC
         ↓
Menu items either included (full mode) or excluded (minimal mode)
```

## Components

### 1. AppConfiguration.kt
- **Purpose:** Central build-time configuration
- **Change:** Add `minimalMode` boolean field
- **Default:** `false` (maintains current behavior)

### 2. DrawerContent.kt
- **Purpose:** Left sidebar navigation menu
- **Change:** Wrap UGC and PGC menu items with conditional check
- **Impact:** Menu list dynamically excludes items based on configuration

### 3. MainScreen.kt
- **Purpose:** Main screen with navigation routing
- **Change:** None (navigation mapping only used when items exist)

## Future Extensibility

The `minimalMode` flag is intentionally generic and can be reused for other simplifications:

- Hide advanced settings sections
- Simplify home screen layout
- Disable advanced video features
- Reduce content categories
- Streamline onboarding flow

## Testing Checklist

- [ ] Build with `minimalMode = false`: UGC and PGC visible (current behavior)
- [ ] Build with `minimalMode = true`: UGC and PGC hidden
- [ ] Other menu items (Home, Search, Settings, User) remain visible in both modes
- [ ] Focus navigation works correctly with reduced menu
- [ ] No compilation errors
- [ ] App launches and runs normally in both modes

## Implementation Notes

- Use `!AppConfiguration.minimalMode` for the condition (negative logic since we want to hide when in minimal mode)
- Default value must be `false` to avoid breaking existing behavior
- The change is isolated to menu rendering - no navigation logic changes needed
