package dev.aaa1115910.bv.tv.screens

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Switch
import androidx.tv.material3.Text
import dev.aaa1115910.bv.player.AkDanmakuPlayer
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerListener
import dev.aaa1115910.bv.viewmodel.live.LiveLoadState
import dev.aaa1115910.bv.viewmodel.live.LivePlayerViewModel
import dev.aaa1115910.bv.util.fInfo
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * 直播播放页 Composable。v1 保持极简：
 * - 视频画面通过 [BvVideoPlayer] 渲染（surface 在底层）。
 * - 由 [LivePlayerViewModel.loadState] 驱动覆盖层（加载中 / 未开播 / 无法播放 / 播放中）。
 * - 不复用 VOD 的 [dev.aaa1115910.bv.player.tv.controller.VideoPlayerController]
 *   （那是为点播的进度条/历史/下一个视频设计的，不适合直播）。
 * - 按 BACK 退出 Activity。
 * 弹幕覆盖层留待 Phase 4。
 */
@Composable
fun LivePlayerScreen(viewModel: LivePlayerViewModel) {
    val context = LocalContext.current
    val logger = KotlinLogging.logger("LivePlayerScreen")
    val scope = rememberCoroutineScope()

    // 播放器实例由 Activity 创建后赋值；未赋值时（理论上不会发生）不渲染 surface。
    val videoPlayer = viewModel.videoPlayer

    // 弹幕开关（默认开启）。关闭时仅隐藏覆盖层，不影响 WebSocket 订阅。
    var danmakuEnabled by remember { mutableStateOf(true) }
    val toggleFocusRequester = remember { FocusRequester() }

    // 初始化弹幕引擎（构造 DanmakuPlayer）。引擎由 VM 持有，Screen 只负责绑定 View。
    LaunchedEffect(Unit) {
        runCatching { viewModel.initDanmakuPlayer() }
            .onFailure { logger.fInfo { "init danmaku player failed: ${it.message}" } }
    }

    // 播放就绪后将焦点落到弹幕开关上，便于遥控器直接操作。
    LaunchedEffect(viewModel.loadState) {
        if (viewModel.loadState == LiveLoadState.Playing) {
            runCatching { toggleFocusRequester.requestFocus() }
        }
    }

    val playerListener = remember(viewModel) {
        object : VideoPlayerListener {
            override fun onError(error: Exception) {
                logger.info { "live onError: $error" }
            }

            override fun onReady() {
                logger.info { "live onReady" }
            }

            override fun onPlay() {
                logger.info { "live onPlay" }
                // 播放开始后订阅直播弹幕。重复 onPlay 由 startDanmaku 内部去重（先 cancel 旧 job）。
                viewModel.startDanmaku(scope)
            }

            override fun onPause() {
                logger.info { "live onPause" }
            }

            override fun onBuffering() {
                logger.info { "live onBuffering" }
            }

            override fun onEnd() {
                logger.info { "live onEnd" }
            }

            override fun onIdle() {}

            override fun onSeekBack(seekBackIncrementMs: Long) {}

            override fun onSeekForward(seekForwardIncrementMs: Long) {}
        }
    }

    BackHandler(enabled = true) {
        (context as? Activity)?.finish()
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        // 视频画面（底层）
        if (videoPlayer != null) {
            BvVideoPlayer(
                modifier = Modifier.fillMaxSize(),
                videoPlayer = videoPlayer,
                playerListener = playerListener
            )
        }

        // 弹幕覆盖层：始终保留在组合中（避免 onDispose 释放 VM 持有的 DanmakuPlayer），
        // 关闭时仅以 alpha(0f) 隐藏，不影响 WebSocket 订阅。位于画面之上、状态覆盖层之下。
        AkDanmakuPlayer(
            modifier = Modifier
                .fillMaxSize()
                .alpha(if (danmakuEnabled) 1f else 0f),
            danmakuPlayer = viewModel.danmakuPlayer
        )

        when (viewModel.loadState) {
            LiveLoadState.Loading -> CenterHint("加载中…")
            LiveLoadState.NotLive -> CenterHint("未开播")
            LiveLoadState.Unplayable -> CenterHint("无法播放该直播间")
            LiveLoadState.Playing -> {
                LiveBadge(
                    modifier = Modifier.align(Alignment.TopStart),
                    title = viewModel.title,
                    uname = viewModel.uname
                )
                // 弹幕开关 + 连接失败提示（右上角，D-pad 可聚焦）
                DanmakuControl(
                    modifier = Modifier.align(Alignment.TopEnd),
                    enabled = danmakuEnabled,
                    onToggle = { danmakuEnabled = it },
                    connected = viewModel.danmakuConnected,
                    focusRequester = toggleFocusRequester
                )
            }
        }
    }
}

/**
 * 直播弹幕开关控件 + 连接状态提示。极简实现：一个可聚焦的 [Switch]（带“弹幕”标签），
 * 连接失败时在下方显示“弹幕连接失败”。
 */
@Composable
private fun DanmakuControl(
    modifier: Modifier = Modifier,
    enabled: Boolean,
    onToggle: (Boolean) -> Unit,
    connected: Boolean,
    focusRequester: FocusRequester
) {
    Column(
        modifier = modifier
            .padding(24.dp),
        horizontalAlignment = Alignment.End
    ) {
        Row(
            modifier = Modifier
                .focusRequester(focusRequester)
                .background(
                    Color.Black.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(8.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "弹幕",
                color = Color.White,
                style = MaterialTheme.typography.labelLarge
            )
            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )
        }
        if (!connected) {
            Text(
                modifier = Modifier.padding(top = 6.dp),
                text = "弹幕连接失败",
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CenterHint(text: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White.copy(alpha = 0.8f),
            style = MaterialTheme.typography.titleMedium
        )
    }
}

@Composable
private fun LiveBadge(modifier: Modifier = Modifier, title: String, uname: String) {
    Column(
        modifier = modifier
            .padding(24.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .background(Color.Red, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "● LIVE",
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                text = title.ifBlank { "直播" },
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
        if (uname.isNotBlank()) {
            Text(
                modifier = Modifier.padding(top = 4.dp),
                text = uname,
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
