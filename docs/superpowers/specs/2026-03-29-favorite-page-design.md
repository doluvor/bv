# Favorite Page Left Sidebar Tab Design

**Date:** 2026-03-29
**Author:** Claude
**Status:** Approved

## Overview

Add a Favorite page to the left side navigation drawer, positioned under the History tab. The favorite content will be displayed as embedded content in the main screen, showing favorite videos organized by folders with tabs for switching between folders.

## Requirements

### Functional Requirements

1. **Navigation Integration**
   - Add "Favorite" (收藏) as a drawer item in the left sidebar
   - Position the Favorite item immediately after History in the drawer
   - Route to Favorite content when drawer item is selected

2. **Content Display**
   - Display favorite videos in a 4-column grid layout (matching History)
   - Show folder tabs at the top for switching between favorite folders
   - Display video cards with title, cover, uploader name, and duration
   - Support preloading when approaching the end of the list

3. **Data Management**
   - Load favorite folders on component initialization
   - Load videos for the currently selected folder
   - Clear and reload when switching between folders
   - Handle pagination for folders with many videos

4. **User Interaction**
   - Click on video card to navigate to VideoInfoActivity
   - Switch between favorite folders using tabs
   - Focus management for D-pad navigation

### Non-Functional Requirements

1. **Consistency**
   - Follow the same patterns as HistoryContent
   - Use the same video card component (SmallVideoCard)
   - Match the visual style of other content pages

2. **Performance**
   - Implement lazy loading with pagination
   - Preload next page when approaching end of list
   - Clear data when switching folders to manage memory

3. **Error Handling**
   - Handle unauthenticated users (favorites require login)
   - Handle API failures gracefully
   - Show loading and empty states appropriately

## Architecture

### Component Structure

```
MainScreen
├── NavigationDrawer
│   ├── User/Login
│   ├── Search
│   ├── Home
│   ├── History
│   ├── Favorite (NEW)
│   ├── UGC (conditional)
│   ├── PGC (conditional)
│   └── Settings
└── Content Area
    ├── HomeContent
    ├── HistoryContent
    ├── FavoriteContent (NEW)
    ├── UgcContent
    ├── PgcContent
    └── SearchInputScreen
```

### New Components

**FavoriteContent.kt**
- Location: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/FavoriteContent.kt`
- Purpose: Display favorite videos with folder tabs in the main screen
- Based on: Existing `FavoriteScreen.kt` (adapted for embedded use)

### Modified Components

**DrawerContent.kt**
- Add `Favorite` to `DrawerItem` enum
- Add Favorite drawer item to the list
- Use appropriate icon (e.g., `Icons.Default.Favorite`)

**MainScreen.kt**
- Add `favoriteViewModel` parameter
- Add `favoriteFocusRequester` for focus management
- Add Favorite case to content routing
- Update `onFocusToContent` handler

**strings.xml**
- Add display name for Favorite drawer item (if needed)

## Data Flow

1. **Initialization**
   - MainScreen renders with Favorite selected
   - FavoriteContent composable is created
   - FavoriteViewModel loads favorite folders
   - First folder is selected by default
   - Videos for first folder are loaded

2. **Folder Switch**
   - User selects a different folder tab
   - `currentFavoriteFolderMetadata` is updated
   - Existing video list is cleared
   - Pagination is reset
   - Videos for new folder are loaded

3. **Video Selection**
   - User clicks on a video card
   - VideoInfoActivity is launched with video avid
   - Main screen remains in background

4. **Preloading**
   - User scrolls near end of list (index + 20 > size)
   - Next page of videos is loaded automatically
   - New videos are appended to existing list

## Implementation Details

### DrawerItem Enum

```kotlin
enum class DrawerItem(
    val displayName: String,
    val displayIcon: ImageVector
) {
    User("点击登录", Icons.Default.AccountCircle),
    Search("搜索", Icons.Default.Search),
    Home("首页", Icons.Default.Home),
    History("历史记录", Icons.Default.History),
    Favorite("收藏", Icons.Default.Favorite), // NEW
    UGC("UGC", Icons.Default.OndemandVideo),
    PGC("PGC", Icons.Default.Movie),
    Settings("设置", Icons.Default.Settings)
}
```

### Focus Management

- Add `favoriteFocusRequester` to MainScreen
- Route focus to favorite content when switching from drawer
- Handle focus restoration when returning to Favorite tab

### MINIMAL_MODE Consideration

- Favorite should be available in MINIMAL_MODE (unlike UGC/PGC)
- Favorites are a core user feature, not optional content

### Login Requirement

- Favorites require user authentication
- Handle case where user is not logged in
- May need to show login prompt or empty state

## Success Criteria

1. Favorite appears in left sidebar under History
2. Selecting Favorite displays favorite content in main area
3. Folder tabs allow switching between favorite folders
4. Videos display in 4-column grid with proper cards
5. Clicking video navigates to video info
6. Preloading works when scrolling
7. Focus management works correctly with D-pad
8. No crashes or errors in normal usage

## Open Questions

1. Should Favorite be hidden in MINIMAL_MODE?
   - **Decision:** No, favorites are a core user feature

2. What to show when user is not logged in?
   - **Decision:** Show empty state or prompt to login (to be determined in implementation)

3. Should we reuse existing FavoriteScreen or create new FavoriteContent?
   - **Decision:** Create new FavoriteContent adapted from FavoriteScreen for embedded use

## Related Files

- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt`
- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/HistoryContent.kt`
- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/user/FavoriteScreen.kt`
- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt`
- `app/src/main/kotlin/dev/aaa1115910/bv/viewmodel/user/FavoriteViewModel.kt`
- `app/src/main/res/values/strings.xml`
