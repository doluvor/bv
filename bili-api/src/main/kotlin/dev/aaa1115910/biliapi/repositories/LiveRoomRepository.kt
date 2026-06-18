package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import io.github.oshai.kotlinlogging.KotlinLogging
import org.koin.core.annotation.Single

@Single
class LiveRoomRepository(
    private val authRepository: AuthRepository
) {
    private val logger = KotlinLogging.logger {}
    private val sessData get() = authRepository.sessionData ?: ""

    /** live_status: 0 未开播, 1 直播中, 2 轮播 */
    suspend fun getLiveStatus(roomId: Int): Int =
        BiliLiveHttpApi.getLiveRoomPlayInfo(roomId).data?.liveStatus ?: 0

    /** 解析 v2 playurl 为单个可播放地址；不可播放返回 null */
    suspend fun getPlayUrl(roomId: Int): ResolvedLivePlayUrl? {
        // 服务器对“登录”请求会忽略 qn 参数、直接返回账号允许的最高画质（原画 10000，
        // 码率可达 ~12Mbps，模拟器/低端盒子吃不消会卡顿/黑屏）。实测未登录请求会被
        // 封顶在 ~250（超清≈720P），因此这里刻意不带 SESSDATA，强制走未登录的低画质流。
        // TODO: 改为可设置的清晰度（登录态下用 app gRPC playurl 显式指定 qn）。
        val data = BiliLiveHttpApi.getLiveRoomPlayInfoV2(
            roomId,
            qn = PREFERRED_QN,
            sessData = ""
        ).getResponseData()
        // 记录可用流与最终选择，便于排查“某些直播间黑屏/无法播放”。
        val variants = data.playurlInfo?.playurl?.stream?.joinToString { s ->
            val fmts = s.format.joinToString { f ->
                val codecs = f.codec.joinToString { c -> "${c.codecName}/qn${c.currentQn}" }
                "${f.formatName}=[$codecs]"
            }
            "${s.protocolName}{$fmts}"
        } ?: "none"
        logger.info { "live playurl variants for room $roomId: $variants" }
        val resolved = LivePlayUrlResolver.resolve(data)
        logger.info { "live resolved for room $roomId: fmt=${resolved?.formatName} qn=${resolved?.qn}" }
        return resolved
    }

    companion object {
        // bilibili 直播 qn：10000=原画, 400=蓝光(1080P), 250=超清(≈720P), 150=高清, 80=流畅。
        // 原画码率可达 ~12Mbps，模拟器/低端盒子解码或带宽吃不消会卡顿/黑屏。
        private const val PREFERRED_QN = 250
    }
}
