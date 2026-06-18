package dev.aaa1115910.bv.tv.activities.live

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import dev.aaa1115910.bv.R
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.player.impl.exo.ExoPlayerFactory
import dev.aaa1115910.bv.tv.screens.LivePlayerScreen
import dev.aaa1115910.bv.ui.theme.BVTheme
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.viewmodel.live.LivePlayerViewModel
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel

/**
 * 直播播放页 Activity。镜像 [dev.aaa1115910.bv.tv.activities.video.VideoPlayerV3Activity]：
 * 在 onCreate 中创建 ExoPlayer 实例并赋值给 ViewModel，再调用 [LivePlayerViewModel.load] 拉取直播流。
 * 直播使用 Web API，故固定采用 user_agent_http / referer（与 VideoPlayerV3Activity 的 Web 分支一致）。
 */
class LivePlayerActivity : ComponentActivity() {
    companion object {
        private val logger = KotlinLogging.logger {}

        fun actionStart(
            context: Context,
            roomId: Int,
            title: String,
            uname: String
        ) {
            context.startActivity(
                Intent(
                    context,
                    dev.aaa1115910.bv.tv.activities.live.LivePlayerActivity::class.java
                ).apply {
                    putExtra("roomId", roomId)
                    putExtra("title", title)
                    putExtra("uname", uname)
                }
            )
        }
    }

    private val viewModel: LivePlayerViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initPlayer()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            BVTheme(forceDark = true) {
                LivePlayerScreen(viewModel = viewModel)
            }
        }
        getParamsFromIntent()
    }

    override fun onPause() {
        super.onPause()
        viewModel.videoPlayer?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun initPlayer() {
        logger.info { "Init live player" }
        val roomId = intent.getIntExtra("roomId", 0)
        // 直播 CDN 要求直播域 Referer 与 buvid3 反爬 Cookie；缺失会被静默丢连接（表现为 socket 超时）。
        val options = VideoPlayerOptions(
            userAgent = getString(R.string.video_player_user_agent_http),
            referer = "https://live.bilibili.com/$roomId",
            enableFfmpegAudioRenderer = Prefs.enableFfmpegAudioRenderer,
            extraHeaders = mapOf("Cookie" to "buvid3=${Prefs.buvid3}")
        )
        // ViewModel 的 onCleared() 会负责 release，这里只负责创建与赋值。
        viewModel.videoPlayer = ExoPlayerFactory().create(this, options)
    }

    private fun getParamsFromIntent() {
        val roomId = intent.getIntExtra("roomId", 0)
        val title = intent.getStringExtra("title") ?: "直播"
        val uname = intent.getStringExtra("uname") ?: ""
        logger.fInfo { "Launch live parameter: [roomId=$roomId]" }
        lifecycleScope.launch {
            viewModel.load(roomId = roomId, title = title, uname = uname)
        }
    }
}
