package dev.aaa1115910.bv.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import dev.aaa1115910.biliapi.repositories.LiveRoomRepository
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel

/**
 * 直播播放页 ViewModel。播放器实例由 Activity 创建后赋值给 [videoPlayer]。
 * v1：每个 Activity 实例只加载一个直播间（不支持切房）；Phase 4 将在此接入直播弹幕。
 */
@KoinViewModel
class LivePlayerViewModel(
    private val liveRoomRepository: LiveRoomRepository
) : ViewModel() {
    private val logger = KotlinLogging.logger {}

    var videoPlayer: AbstractVideoPlayer? = null
    var roomId by mutableStateOf(0)
    var title by mutableStateOf("")
    var uname by mutableStateOf("")

    var loadState by mutableStateOf(LiveLoadState.Loading)
        private set

    // TODO: 加入切房/重入保护（loadJob?.cancel()）当支持切换直播间时
    suspend fun load(roomId: Int, title: String, uname: String) {
        this.roomId = roomId
        this.title = title
        this.uname = uname
        loadState = LiveLoadState.Loading
        runCatching {
            val status = withContext(Dispatchers.IO) { liveRoomRepository.getLiveStatus(roomId) }
            if (status != 1) {
                loadState = LiveLoadState.NotLive
                return@runCatching
            }
            val resolved = withContext(Dispatchers.IO) { liveRoomRepository.getPlayUrl(roomId) }
            val url = resolved?.url
            if (url.isNullOrBlank()) {
                loadState = LiveLoadState.Unplayable
                return@runCatching
            }
            logger.fInfo { "Play live room $roomId -> $url" }
            withContext(Dispatchers.Main) {
                videoPlayer?.playLiveUrl(url)
                videoPlayer?.prepare()
                videoPlayer?.start()
            }
            loadState = if (videoPlayer != null) LiveLoadState.Playing else LiveLoadState.Unplayable
        }.onFailure {
            logger.fError { "Load live playurl failed: ${it.stackTraceToString()}" }
            withContext(Dispatchers.Main) {
                "加载直播失败: ${it.localizedMessage}".toast(BVApp.context)
            }
            loadState = LiveLoadState.Unplayable
        }
    }

    override fun onCleared() {
        super.onCleared()
        videoPlayer?.release()
        videoPlayer = null
    }
}

enum class LiveLoadState { Loading, Playing, NotLive, Unplayable }
