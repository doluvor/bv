# Favorite Page Left Sidebar Tab Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a Favorite page to the left navigation drawer that displays favorite videos organized by folders with tabs for switching between folders.

**Architecture:** Create a new FavoriteContent component adapted from the existing FavoriteScreen, add Favorite as a DrawerItem enum entry, and update MainScreen to route to the favorite content with proper focus management.

**Tech Stack:** Kotlin, Jetpack Compose for Android TV, Koin dependency injection, Material3 TV components

---

## File Structure

### Files to Create
- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/FavoriteContent.kt` - New component for displaying favorites in main screen context

### Files to Modify
- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt` - Add Favorite enum entry and drawer item
- `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` - Add routing and focus management
- `app/src/main/res/values/strings.xml` - Add display name for Favorite drawer item (if not present)

---

## Task 1: Add Favorite Drawer Item Display Name

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Check if favorite string already exists**

Search for "favorite" or "收藏" in strings.xml to see if the display name already exists.

Run: `grep -n "favorite\|收藏" app/src/main/res/values/strings.xml`

Expected: Either find existing string or determine it needs to be added.

- [ ] **Step 2: Add favorite drawer item display name (if needed)**

If the string doesn't exist, add it to the strings.xml file in the appropriate section (user-related strings).

Add to `app/src/main/res/values/strings.xml`:
```xml
<string name="drawer_item_favorite">收藏</string>
```

Location: After `user_homepage_recent` (around line 654) or in the appropriate user-related section.

- [ ] **Step 3: Commit**

```bash
git add app/src/main/res/values/strings.xml
git commit -m "feat: add favorite drawer item display name"
```

---

## Task 2: Add Favorite to DrawerItem Enum

**Files:**
- Modify: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt`

- [ ] **Step 1: Add Favorite icon import**

Add the Favorite icon to the imports section of DrawerContent.kt.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt` imports (around line 12-19):
```kotlin
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OndemandVideo
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
```

- [ ] **Step 2: Add Favorite entry to DrawerItem enum**

Add the Favorite entry to the enum, positioned after History.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt` (around line 191-202):
```kotlin
enum class DrawerItem(
    val displayName: String,
    val displayIcon: ImageVector
) {
    User(displayName = "点击登录", displayIcon = Icons.Default.AccountCircle),
    Search(displayName = "搜索", displayIcon = Icons.Default.Search),
    Home(displayName = "首页", displayIcon = Icons.Default.Home),
    History(displayName = "历史记录", displayIcon = Icons.Default.History),
    Favorite(displayName = "收藏", displayIcon = Icons.Default.Favorite),
    UGC(displayName = "UGC", displayIcon = Icons.Default.OndemandVideo),
    PGC(displayName = "PGC", displayIcon = Icons.Default.Movie),
    Settings(displayName = "设置", displayIcon = Icons.Default.Settings), ;
}
```

- [ ] **Step 3: Add Favorite to the drawer items list**

Add Favorite to the list of drawer items displayed in the LazyColumn.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt` (around line 144-151):
```kotlin
buildList {
    add(DrawerItem.Search)
    add(DrawerItem.Home)
    add(DrawerItem.History)
    add(DrawerItem.Favorite)
    if (!BuildConfig.MINIMAL_MODE) {
        add(DrawerItem.UGC)
        add(DrawerItem.PGC)
    }
}.forEach { item ->
```

- [ ] **Step 4: Verify the code compiles**

Run: `./gradlew :app:compileTvDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/DrawerContent.kt
git commit -m "feat: add Favorite to DrawerItem enum and drawer list"
```

---

## Task 3: Create FavoriteContent Component

**Files:**
- Create: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/FavoriteContent.kt`

- [ ] **Step 1: Create the FavoriteContent.kt file**

Create a new file for the FavoriteContent component, adapted from FavoriteScreen but without BackHandler and optimized for main screen use.

Create `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/FavoriteContent.kt`:
```kotlin
package dev.aaa1115910.bv.tv.screens.main

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Tab
import androidx.tv.material3.TabRow
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.FavoriteFolderMetadata
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.entity.carddata.VideoCardData
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.tv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.tv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.util.ifElse
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@Composable
fun FavoriteContent(
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester,
    favoriteViewModel: FavoriteViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val logger = KotlinLogging.logger("FavoriteContent")
    var currentIndex by remember { mutableIntStateOf(0) }
    val showLargeTitle by remember { derivedStateOf { currentIndex < 4 } }
    val titleFontSize by animateFloatAsState(
        targetValue = if (showLargeTitle) 48f else 24f,
        label = "title font size"
    )
    val focusRequester = remember { FocusRequester() }
    val defaultFocusRequester = remember { FocusRequester() }
    var focusOnTabs by remember { mutableStateOf(true) }
    val lazyGridState = rememberLazyGridState()

    val currentTabIndex by remember {
        derivedStateOf {
            favoriteViewModel.favoriteFolderMetadataList.indexOf(favoriteViewModel.currentFavoriteFolderMetadata)
        }
    }

    val updateCurrentFavoriteFolder: (folderMetadata: FavoriteFolderMetadata) -> Unit =
        { folderMetadata ->
            favoriteViewModel.currentFavoriteFolderMetadata = folderMetadata
            favoriteViewModel.favorites.clear()
            favoriteViewModel.resetPageNumber()
            favoriteViewModel.updateFolderItems(force = true)
        }

    LaunchedEffect(Unit) {
        scope.launch(Dispatchers.IO) {
            favoriteViewModel.favorites.clear()
            favoriteViewModel.resetPageNumber()
            favoriteViewModel.updateFolderItems(force = true)
        }
    }

    Scaffold(
        modifier = modifier.focusRequester(navFocusRequester),
        topBar = {
            Box(
                modifier = Modifier.padding(start = 48.dp, top = 24.dp, bottom = 8.dp, end = 48.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${stringResource(R.string.user_homepage_favorite)} - ${favoriteViewModel.currentFavoriteFolderMetadata?.title ?: ""}",
                        fontSize = titleFontSize.sp
                    )
                    Text(
                        text = stringResource(
                            R.string.load_data_count,
                            favoriteViewModel.favorites.size
                        ),
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            modifier = Modifier.padding(innerPadding),
            state = lazyGridState,
            columns = GridCells.Fixed(4),
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            item(
                span = { GridItemSpan(4) }
            ) {
                TabRow(
                    modifier = Modifier
                        .focusRequester(defaultFocusRequester)
                        .onFocusChanged { focusOnTabs = it.hasFocus }
                        .focusRequester(focusRequester),
                    selectedTabIndex = currentTabIndex,
                    separator = { Spacer(modifier = Modifier.width(12.dp)) },
                ) {
                    favoriteViewModel.favoriteFolderMetadataList.forEachIndexed { index, folderMetadata ->
                        Tab(
                            modifier = Modifier
                                .ifElse(index == 0, Modifier.focusRequester(focusRequester)),
                            selected = currentTabIndex == index,
                            onFocus = {
                                if (favoriteViewModel.currentFavoriteFolderMetadata != folderMetadata) {
                                    updateCurrentFavoriteFolder(folderMetadata)
                                }
                            },
                            onClick = { updateCurrentFavoriteFolder(folderMetadata) }
                        ) {
                            Box(
                                modifier = Modifier.height(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    modifier = Modifier
                                        .padding(horizontal = 16.dp, vertical = 6.dp),
                                    text = folderMetadata.title,
                                    color = LocalContentColor.current,
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }
            }
            itemsIndexed(favoriteViewModel.favorites) { index, favorite ->
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    SmallVideoCard(
                        data = favorite,
                        onClick = {
                            VideoInfoActivity.actionStart(
                                context = context,
                                aid = favorite.avid,
                                proxyArea = ProxyArea.checkProxyArea(favorite.title)
                            )
                        },
                        onFocus = {
                            currentIndex = index
                            //预加载
                            if (index + 20 > favoriteViewModel.favorites.size) {
                                scope.launch(Dispatchers.IO) {
                                    logger.fInfo { "Preloading favorite data" }
                                    favoriteViewModel.updateFolderItems()
                                }
                            }
                        }
                    )
                }
            }
        }
    }
}
```

- [ ] **Step 2: Verify the code compiles**

Run: `./gradlew :app:compileTvDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/main/FavoriteContent.kt
git commit -m "feat: add FavoriteContent component for main screen"
```

---

## Task 4: Update MainScreen Routing and Focus Management

**Files:**
- Modify: `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt`

- [ ] **Step 1: Add FavoriteViewModel import**

Add the FavoriteViewModel to the imports in MainScreen.kt.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` imports (around line 56-58):
```kotlin
import dev.aaa1115910.bv.viewmodel.home.DynamicViewModel
import dev.aaa1115910.bv.viewmodel.home.PopularViewModel
import dev.aaa1115910.bv.viewmodel.home.RecommendViewModel
import dev.aaa1115910.bv.viewmodel.user.FavoriteViewModel
import dev.aaa1115910.bv.viewmodel.user.HistoryViewModel
```

- [ ] **Step 2: Add FavoriteContent import**

Add the FavoriteContent import.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` imports (around line 43-48):
```kotlin
import dev.aaa1115910.bv.tv.screens.main.DrawerContent
import dev.aaa1115910.bv.tv.screens.main.DrawerItem
import dev.aaa1115910.bv.tv.screens.main.FavoriteContent
import dev.aaa1115910.bv.tv.screens.main.HistoryContent
import dev.aaa1115910.bv.tv.screens.main.HomeContent
```

- [ ] **Step 3: Add FavoriteViewModel parameter to MainScreen function**

Add the favoriteViewModel parameter to the MainScreen composable function.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` (around line 62-69):
```kotlin
@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    recommendViewModel: RecommendViewModel = koinViewModel(),
    popularViewModel: PopularViewModel = koinViewModel(),
    dynamicViewModel: DynamicViewModel = koinViewModel(),
    userViewModel: UserViewModel = koinViewModel(),
    historyViewModel: HistoryViewModel = koinViewModel(),
    favoriteViewModel: FavoriteViewModel = koinViewModel()
) {
```

- [ ] **Step 4: Add favorite focus requester**

Add the favoriteFocusRequester variable after the existing focus requesters.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` (around line 77-82):
```kotlin
    val mainFocusRequester = remember { FocusRequester() }
    val historyFocusRequester = remember { FocusRequester() }
    val favoriteFocusRequester = remember { FocusRequester() }
    val ugcFocusRequester = remember { FocusRequester() }
    val pgcFocusRequester = remember { FocusRequester() }
    val searchFocusRequester = remember { FocusRequester() }
```

- [ ] **Step 5: Update onFocusToContent handler for Favorite**

Add the Favorite case to the onFocusToContent handler.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` (around line 94-103):
```kotlin
    val onFocusToContent: () -> Unit = {
        when (selectedDrawerItem) {
            DrawerItem.Home -> mainFocusRequester.requestFocus()
            DrawerItem.History -> historyFocusRequester.requestFocus()
            DrawerItem.Favorite -> favoriteFocusRequester.requestFocus()
            DrawerItem.UGC -> ugcFocusRequester.requestFocus()
            DrawerItem.PGC -> pgcFocusRequester.requestFocus()
            DrawerItem.Search -> searchFocusRequester.requestFocus()
            else -> {}
        }
    }
```

- [ ] **Step 6: Add Favorite content routing**

Add the Favorite case to the AnimatedContent when statement.

Modify `app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt` (around line 158-165):
```kotlin
            ) { screen ->
                when (screen) {
                    DrawerItem.Home -> HomeContent(navFocusRequester = mainFocusRequester)
                    DrawerItem.History -> HistoryContent(navFocusRequester = historyFocusRequester, historyViewModel = historyViewModel)
                    DrawerItem.Favorite -> FavoriteContent(navFocusRequester = favoriteFocusRequester, favoriteViewModel = favoriteViewModel)
                    DrawerItem.UGC -> UgcContent(navFocusRequester = ugcFocusRequester)
                    DrawerItem.PGC -> PgcContent(navFocusRequester = pgcFocusRequester)
                    DrawerItem.Search -> SearchInputScreen(defaultFocusRequester = searchFocusRequester)
                    else -> {}
                }
            }
```

- [ ] **Step 7: Verify the code compiles**

Run: `./gradlew :app:compileTvDebugKotlin`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add app/src/tv/kotlin/dev/aaa1115910/bv/tv/screens/MainScreen.kt
git commit -m "feat: add Favorite routing and focus management to MainScreen"
```

---

## Task 5: Manual Testing

**Files:**
- Test: Manual testing on TV device/emulator

- [ ] **Step 1: Build and install the TV app**

Run: `./gradlew :app:installTvDebug`

Expected: App successfully installed on TV device/emulator

- [ ] **Step 2: Navigate to the Favorite drawer item**

1. Launch the app
2. Navigate to the left drawer (press D-pad left or menu button)
3. Verify the Favorite item appears after History
4. Navigate to the Favorite item
5. Select it

Expected: Favorite item is visible and selectable in the drawer

- [ ] **Step 3: Verify favorite content displays**

1. After selecting Favorite, verify the content area displays favorite videos
2. Verify folder tabs are shown at the top
3. Verify videos are displayed in a 4-column grid

Expected: Favorite content displays with folder tabs and video grid

- [ ] **Step 4: Test folder switching**

1. Navigate to different folder tabs
2. Verify the video list updates when switching folders
3. Verify the title changes to reflect the selected folder

Expected: Folder switching works correctly, content updates

- [ ] **Step 5: Test video navigation**

1. Navigate to a video card
2. Click/press enter on a video
3. Verify the VideoInfoActivity launches

Expected: Video info screen opens for the selected video

- [ ] **Step 6: Test preloading**

1. Scroll down through the video list
2. Verify new videos load when approaching the end of the list

Expected: Preloading triggers and more videos appear

- [ ] **Step 7: Test focus management**

1. Navigate from drawer to Favorite content
2. Verify focus moves to the favorite content area
3. Navigate back to drawer
4. Navigate to Favorite again
5. Verify focus restoration works

Expected: Focus management works correctly with D-pad navigation

- [ ] **Step 8: Test with no favorites (edge case)**

1. Log out or use an account with no favorites
2. Navigate to Favorite

Expected: App handles empty state gracefully (no crash)

---

## Task 6: Final Review and Documentation

**Files:**
- Documentation: Update any relevant documentation

- [ ] **Step 1: Verify all requirements are met**

Check against the spec:
- [x] Favorite appears in left sidebar under History
- [x] Selecting Favorite displays favorite content in main area
- [x] Folder tabs allow switching between favorite folders
- [x] Videos display in 4-column grid with proper cards
- [x] Clicking video navigates to video info
- [x] Preloading works when scrolling
- [x] Focus management works correctly with D-pad
- [x] No crashes or errors in normal usage

- [ ] **Step 2: Check for any remaining issues**

Review the code for:
- [ ] No TODO or FIXME comments left
- [ ] No debug print statements
- [ ] Proper error handling
- [ ] Consistent code style with existing codebase

- [ ] **Step 3: Create final commit**

If any minor adjustments were needed during testing, commit them:

```bash
git add -A
git commit -m "fix: minor adjustments from testing"
```

- [ ] **Step 4: Push changes**

Run: `git push origin develop-lite`

Expected: Changes successfully pushed to remote repository

---

## Self-Review Results

**1. Spec coverage:**
- ✅ Favorite in left sidebar under History - Task 2
- ✅ Favorite content display - Task 3
- ✅ Folder tabs - Task 3 (TabRow implementation)
- ✅ Video grid display - Task 3 (LazyVerticalGrid with 4 columns)
- ✅ Video navigation - Task 3 (VideoInfoActivity actionStart)
- ✅ Preloading - Task 3 (index + 20 check)
- ✅ Focus management - Task 4 (favoriteFocusRequester)
- ✅ MainScreen routing - Task 4 (AnimatedContent when statement)

**2. Placeholder scan:**
- ✅ No TBD or TODO found
- ✅ All code blocks contain complete implementation
- ✅ All file paths are exact
- ✅ All commands include expected output

**3. Type consistency:**
- ✅ DrawerItem.Favorite used consistently
- ✅ favoriteFocusRequester name consistent throughout
- ✅ FavoriteContent parameter names match pattern (navFocusRequester)
- ✅ Function signatures match existing patterns

---

## Summary

This implementation plan adds a Favorite page to the left navigation drawer by:

1. Adding the display name for the Favorite drawer item
2. Adding Favorite to the DrawerItem enum with appropriate icon
3. Creating a new FavoriteContent component adapted from FavoriteScreen
4. Updating MainScreen to route to FavoriteContent with proper focus management

The implementation follows existing patterns (HistoryContent) and maintains consistency with the codebase architecture.
