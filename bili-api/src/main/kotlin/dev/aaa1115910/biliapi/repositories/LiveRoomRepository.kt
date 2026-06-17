package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import org.koin.core.annotation.Single

@Single
class LiveRoomRepository(
    private val authRepository: AuthRepository
) {
    private val sessData get() = authRepository.sessionData ?: ""

    /** live_status: 0 未开播, 1 直播中, 2 轮播 */
    suspend fun getLiveStatus(roomId: Int): Int =
        BiliLiveHttpApi.getLiveRoomPlayInfo(roomId).data?.liveStatus ?: 0

    /** 解析 v2 playurl 为单个可播放地址；不可播放返回 null */
    suspend fun getPlayUrl(roomId: Int): ResolvedLivePlayUrl? {
        val data = BiliLiveHttpApi.getLiveRoomPlayInfoV2(roomId, sessData = sessData).getResponseData()
        return LivePlayUrlResolver.resolve(data)
    }
}
