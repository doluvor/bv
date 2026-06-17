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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.aaa1115910.bv.player.BvVideoPlayer
import dev.aaa1115910.bv.player.VideoPlayerListener
import dev.aaa1115910.bv.viewmodel.live.LiveLoadState
import dev.aaa1115910.bv.viewmodel.live.LivePlayerViewModel
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

    // 播放器实例由 Activity 创建后赋值；未赋值时（理论上不会发生）不渲染 surface。
    val videoPlayer = viewModel.videoPlayer

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

        // Phase 4: AkDanmakuPlayer overlay + danmaku toggle

        when (viewModel.loadState) {
            LiveLoadState.Loading -> CenterHint("加载中…")
            LiveLoadState.NotLive -> CenterHint("未开播")
            LiveLoadState.Unplayable -> CenterHint("无法播放该直播间")
            LiveLoadState.Playing -> LiveBadge(
                modifier = Modifier.align(Alignment.TopStart),
                title = viewModel.title,
                uname = viewModel.uname
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
