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

    /** 直播弹幕 WebSocket 需要 SESSDATA（getDanmuInfo 接口有 -352 风控，需登录态）。 */
    val sessionData: String get() = sessData

    /** live_status: 0 未开播, 1 直播中, 2 轮播 */
    suspend fun getLiveStatus(roomId: Int): Int =
        BiliLiveHttpApi.getLiveRoomPlayInfo(roomId).data?.liveStatus ?: 0

    /** 解析 v2 playurl 为单个可播放地址；不可播放返回 null */
    suspend fun getPlayUrl(roomId: Int): ResolvedLivePlayUrl? {
        val data = BiliLiveHttpApi.getLiveRoomPlayInfoV2(roomId, sessData = sessData).getResponseData()
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
}
