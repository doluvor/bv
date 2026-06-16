package dev.aaa1115910.bv.tv.screens.main

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.tv.material3.Text
import dev.aaa1115910.biliapi.entity.user.FollowedUser
import dev.aaa1115910.bv.entity.proxy.ProxyArea
import dev.aaa1115910.bv.tv.activities.video.VideoInfoActivity
import dev.aaa1115910.bv.tv.component.videocard.SmallVideoCard
import dev.aaa1115910.bv.tv.screens.user.UpCard
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.isDpadLeft
import dev.aaa1115910.bv.util.isKeyDown
import dev.aaa1115910.bv.viewmodel.user.FollowViewModel
import dev.aaa1115910.bv.viewmodel.user.UserSpaceViewModel
import org.koin.androidx.compose.koinViewModel

@Composable
fun FollowingContent(
    modifier: Modifier = Modifier,
    navFocusRequester: FocusRequester,
    followViewModel: FollowViewModel = koinViewModel(),
    userSpaceViewModel: UserSpaceViewModel = koinViewModel()
) {
    val context = LocalContext.current
    var selectedUser by remember { mutableStateOf<FollowedUser?>(null) }
    var focusInList by remember { mutableStateOf(false) }
    var gridIndex by remember { mutableIntStateOf(0) }

    //切换UP时重置并重新加载右侧视频列表
    LaunchedEffect(selectedUser?.mid) {
        selectedUser?.let { userSpaceViewModel.loadUser(it.mid, it.name) }
    }

    //从右侧网格返回时，把焦点拉回左侧列表（选中项）
    //必须在主线程请求焦点：带 scope 的扩展会在 Dispatchers.Default 上调用 requestFocus，
    //后台线程改动焦点/快照状态会与主线程的布局绘制竞争，抛出 multithreaded access 崩溃
    LaunchedEffect(focusInList) {
        if (focusInList) runCatching { navFocusRequester.requestFocus() }
    }

    if (Prefs.uid == 0L) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(text = "请先登录")
        }
        return
    }

    Row(modifier = modifier) {
        //左侧：关注的UP列表（复用 UpCard 行，固定 280×80 基本填满 weight(3f) 列）
        LazyColumn(
            modifier = Modifier
                .weight(3f)
                .fillMaxHeight()
                .onFocusChanged { focusInList = it.hasFocus },
            contentPadding = PaddingValues(24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(followViewModel.followedUsers, key = { it.mid }) { up ->
                UpCard(
                    modifier = if (up.mid == (selectedUser?.mid
                        ?: followViewModel.followedUsers.firstOrNull()?.mid)
                    ) Modifier.focusRequester(navFocusRequester) else Modifier,
                    face = up.avatar,
                    sign = up.sign,
                    username = up.name,
                    onFocusChange = {},
                    onClick = { selectedUser = up }
                )
            }
        }

        //右侧：选中UP的视频列表（grid 取自 UpSpaceScreen，含焦点触发的自动加载更多）
        Box(
            modifier = Modifier
                .weight(5f)
                .fillMaxSize()
        ) {
            if (selectedUser == null) {
                //未选中UP时右侧保持空白
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "请选择一位UP主",
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            } else {
                LazyVerticalGrid(
                    modifier = Modifier
                        .fillMaxSize()
                        .onPreviewKeyEvent { e ->
                            //处于第一列时按左键，焦点回到左侧列表
                            if (e.isDpadLeft() && e.isKeyDown() && gridIndex % 3 == 0) {
                                focusInList = true
                                true
                            } else false
                        },
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(24.dp),
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                    verticalArrangement = Arrangement.spacedBy(24.dp)
                ) {
                    itemsIndexed(
                        items = userSpaceViewModel.tvSpaceVideos,
                        key = { index, _ -> index }
                    ) { index, video ->
                        SmallVideoCard(
                            data = video,
                            onClick = {
                                VideoInfoActivity.actionStart(
                                    context = context,
                                    aid = video.avid,
                                    proxyArea = ProxyArea.checkProxyArea(video.title)
                                )
                            },
                            onFocus = {
                                gridIndex = index
                                if (index + 20 > userSpaceViewModel.tvSpaceVideos.size) {
                                    userSpaceViewModel.update()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}
