package dev.aaa1115910.bv.tv.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.FilterChip
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.http.entity.live.LiveArea
import dev.aaa1115910.bv.util.OnBottomReached
import dev.aaa1115910.bv.util.isDpadLeft
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.tv.activities.live.LivePlayerActivity
import dev.aaa1115910.bv.viewmodel.live.LiveViewModel
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

/**
 * 直播发现页：左侧分区目录（各父分区下的子分区），
 * 右侧直播间网格。布局与 D-pad 焦点交互沿用 [FollowingContent] 的约定。
 *
 * 进入后 [LiveViewModel.loadHome] 加载分区并自动选中第一个子分区；
 * 选中分区调用 [LiveViewModel.selectArea]，列表触底调用 [LiveViewModel.loadMore]。
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun LiveContent(
    navFocusRequester: FocusRequester,
    liveViewModel: LiveViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var focusInList by remember { mutableStateOf(false) }
    var gridIndex by remember { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()

    //目录中第一个子分区，作为默认焦点锚点（loadHome 会自动选中它）。
    val firstArea: LiveArea? = remember(liveViewModel.parentAreas) {
        liveViewModel.parentAreas.firstNotNullOfOrNull { it.list.firstOrNull() }
    }

    //首次进入加载分区目录与第一个子分区的直播间
    LaunchedEffect(Unit) {
        if (liveViewModel.parentAreas.isEmpty()) liveViewModel.loadHome()
    }

    //右侧网格触底自动加载更多；列表为空时不触发，避免失败时无限重试 + toast 刷屏。
    gridState.OnBottomReached(loading = liveViewModel.loading) {
        if (liveViewModel.rooms.isNotEmpty()) scope.launch { liveViewModel.loadMore() }
    }

    //从右侧网格按左键回到左侧目录时，把焦点拉回目录选中项
    //（与 FollowingContent 一致：runCatching 保护焦点请求）
    LaunchedEffect(focusInList) {
        if (focusInList) runCatching { navFocusRequester.requestFocus() }
    }

    Row(modifier = Modifier.fillMaxSize()) {
        //左侧：分区目录。依次列出每个 parentArea 下的子分区（带父分区名作为分组标题）。
        LazyColumn(
            modifier = Modifier
                .weight(1.5f)
                .fillMaxHeight()
                .onFocusChanged { focusInList = it.hasFocus },
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            liveViewModel.parentAreas.forEach { parentArea ->
                item(key = "header-${parentArea.id}") {
                    Text(
                        modifier = Modifier
                            .width(200.dp)
                            .padding(start = 12.dp, top = 8.dp, bottom = 4.dp),
                        text = parentArea.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
                items(
                    items = parentArea.list,
                    key = { area -> "area-${area.id}" }
                ) { area ->
                    val isFirst = area.id == firstArea?.id
                    FilterChip(
                        modifier = Modifier
                            .width(200.dp)
                            .then(if (isFirst) Modifier.focusRequester(navFocusRequester) else Modifier),
                        selected = liveViewModel.selectedArea?.area == area,
                        onClick = {
                            scope.launch { liveViewModel.selectArea(parentArea, area) }
                        }
                    ) {
                        Text(
                            modifier = Modifier.padding(vertical = 4.dp),
                            text = area.name
                        )
                    }
                }
            }
        }

        //右侧：直播间网格
        Box(
            modifier = Modifier
                .weight(4f)
                .fillMaxSize()
        ) {
            if (liveViewModel.rooms.isEmpty() && liveViewModel.loading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "加载中…",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { e ->
                            //处于第一列时按左键，焦点回到左侧目录
                            if (e.isDpadLeft() && e.isKeyDown() && gridIndex % 4 == 0) {
                                focusInList = true
                                true
                            } else false
                        },
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(
                        items = liveViewModel.rooms,
                        key = { index, room -> "${room.roomId}-$index" }
                    ) { index, room ->
                        LiveRoomCard(
                            room = room,
                            onFocus = { gridIndex = index },
                            onClick = {
                                LivePlayerActivity.actionStart(
                                    context = context,
                                    roomId = room.roomId,
                                    title = room.title,
                                    uname = room.uname
                                )
                            }
                        )
                    }
                }
            }
        }
    }
}
