package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.LiveRoomItem
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import dev.aaa1115910.biliapi.http.entity.live.LiveParentArea
import org.koin.core.annotation.Single

data class LiveRoomPage(
    val list: List<LiveRoomItem>,
    val nextPage: Int,
    val noMore: Boolean
)

@Single
class LiveAreaRepository(
    private val authRepository: AuthRepository
) {
    private val sessData get() = authRepository.sessionData ?: ""

    /** 直播分区目录 */
    suspend fun getAreas(): List<LiveParentArea> =
        BiliLiveHttpApi.getLiveAreaList(sessData).getResponseData()

    /** 子分区分页直播间。[parentAreaId]/[areaId] 为经典接口返回的字符串分区 id */
    suspend fun getRooms(parentAreaId: String, areaId: String, page: Int): LiveRoomPage {
        val list = BiliLiveHttpApi.getLiveRoomList(
            parentAreaId = parentAreaId,
            areaId = areaId,
            page = page,
            sessData = sessData
        ).getResponseData()
        // 经典接口无 has_more 字段：不足一页即视为到底。
        return LiveRoomPage(
            list = list.map { LiveRoomItem.fromRoomInfo(it) },
            nextPage = page + 1,
            noMore = list.size < 30
        )
    }
}
