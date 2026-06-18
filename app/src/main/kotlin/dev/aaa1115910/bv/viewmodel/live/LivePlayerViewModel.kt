package dev.aaa1115910.bv.viewmodel.live

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.lifecycle.ViewModel
import com.kuaishou.akdanmaku.data.DanmakuItemData
import com.kuaishou.akdanmaku.render.SimpleRenderer
import com.kuaishou.akdanmaku.ui.DanmakuPlayer
import dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent
import dev.aaa1115910.biliapi.repositories.LiveRoomRepository
import dev.aaa1115910.biliapi.websocket.LiveDataWebSocket
import dev.aaa1115910.bv.BVApp
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.util.Prefs
import dev.aaa1115910.bv.util.fError
import dev.aaa1115910.bv.util.fInfo
import dev.aaa1115910.bv.util.toast
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.annotation.KoinViewModel
import java.util.concurrent.atomic.AtomicLong

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

    /**
     * akdanmaku 弹幕引擎实例（Compose 包装 [AkDanmakuPlayer]，底层为 Kuaishou 的
     * [com.kuaishou.akdanmaku.ui.DanmakuView]，用于渲染直播弹幕覆盖层）。实例由本 VM 拥有：
     * 通过 `DanmakuPlayer(SimpleRenderer())` 构造，并在 [onCleared] 中 release。
     *
     * 注意：点播路径（[dev.aaa1115910.bv.viewmodel.VideoPlayerV3ViewModel] + 各端 BvPlayer）
     * 采用相同的引擎所有权与渲染方式，本类在此与其保持一致。
     *
     * 弹幕开关的显隐由 Screen 以 alpha 控制——切勿让 [AkDanmakuPlayer] 因开关而离开组合，
     * 否则其 DisposableEffect 的 onDispose 会 release 本实例，导致再次开启时引擎无法重启。
     */
    var danmakuPlayer: DanmakuPlayer? by mutableStateOf(null)
        private set

    /**
     * 直播弹幕 WebSocket 连接是否正常。连接建立失败时置 false，Screen 据此显示一个轻量提示。
     */
    var danmakuConnected by mutableStateOf(true)
        private set

    private var danmakuJob: Job? = null
    private val danmakuIdSeq = AtomicLong(0L)

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
            logger.fInfo { "Play live room $roomId (${
                resolved.protocolName
            }/${resolved.formatName}) -> $url" }
            withContext(Dispatchers.Main) {
                val player = videoPlayer
                if (player != null) {
                    // FLV(gotcha07) 走 progressive；fmp4/ts HLS 走 HlsMediaSource。
                    if (resolved.formatName == "flv") player.playFlvUrl(url) else player.playLiveUrl(url)
                    player.prepare()
                    player.start()
                }
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

    /**
     * 初始化弹幕引擎（镜像点播路径）。应在 Screen 首次渲染时调用一次，随后再调用 [startDanmaku]。
     * 必须在主线程构造 DanmakuPlayer（其内部依赖 HandlerThread/View），故切到 Main。
     */
    suspend fun initDanmakuPlayer() = withContext(Dispatchers.Main) {
        if (danmakuPlayer == null) {
            danmakuPlayer = DanmakuPlayer(SimpleRenderer())
        }
    }

    /**
     * 启动直播弹幕 WebSocket 订阅。非 suspend：内部启动一个受 [scope] 管理的子 Job。
     *
     * 注意：[LiveDataWebSocket.connectLiveEvent] 是一个 suspend 函数，但其内部用 `client.launch`
     * 启动 wss 循环后立即返回——因此取消调用方协程并不会真正关闭 WebSocket。这里通过 [scope]
     * 持有 Job 做尽力而为的取消；真正的资源释放在 [onCleared] 中对 danmakuPlayer 的 release。
     *
     * 重复调用会先取消上一次的订阅。
     */
    fun startDanmaku(scope: CoroutineScope) {
        danmakuJob?.cancel()
        val roomId = this.roomId
        if (roomId <= 0) return
        danmakuConnected = true
        // 启动弹幕引擎的帧循环（镜像点播 onPlay -> start）。此时 AkDanmakuPlayer 的
        // LaunchedEffect 已完成 bindView（onPlay 来自视频播放器，晚于首次组合）。
        runCatching { danmakuPlayer?.start() }
        danmakuJob = scope.launch(Dispatchers.IO) {
            // getDanmuInfo 有 -352 风控，需登录态 SESSDATA（+ buvid3）。
            val sessData = liveRoomRepository.sessionData
            val buvid3 = Prefs.buvid3
            runCatching {
                LiveDataWebSocket.connectLiveEvent(roomId, sessData, buvid3) { event ->
                    if (event is DanmakuEvent) sendDanmaku(event)
                }
            }.onFailure {
                logger.fError { "Live danmaku connect failed: ${it.stackTraceToString()}" }
                danmakuConnected = false
            }
        }
    }

    /**
     * 把一条直播弹幕推送给 akdanmaku 引擎即时渲染。
     *
     * 使用库的 ad-hoc 投递方法 [DanmakuPlayer.send]（字节码确认存在：
     * `send(data) -> obtainItem(data) -> DataSystem.addItem`，下一帧立即绘制）。
     * `position = 0` 保证条目时间 <= 引擎当前时钟，从而被立即渲染（直播为挂钟时间，
     * 无需像点播那样按视频时间轴定位）。
     */
    private fun sendDanmaku(event: DanmakuEvent) {
        val player = danmakuPlayer ?: return
        if (player.isReleased) return
        val data = DanmakuItemData(
            danmakuId = danmakuIdSeq.incrementAndGet(),
            position = 0L,
            content = event.content,
            mode = DanmakuItemData.DANMAKU_MODE_ROLLING,
            textSize = 25,
            textColor = Color.White.toArgb()
        )
        runCatching {
            // send() runs inline on the caller thread; safe to call from the WebSocket IO callback
            // because DanmakuPlayer's internal DataSystem.addItem is synchronized.
            player.send(data)
        }.onFailure {
            logger.fError { "send live danmaku failed: ${it.message}" }
        }
    }

    override fun onCleared() {
        super.onCleared()
        danmakuJob?.cancel()
        danmakuJob = null
        danmakuPlayer?.release()
        danmakuPlayer = null
        videoPlayer?.release()
        videoPlayer = null
    }
}

enum class LiveLoadState { Loading, Playing, NotLive, Unplayable }
