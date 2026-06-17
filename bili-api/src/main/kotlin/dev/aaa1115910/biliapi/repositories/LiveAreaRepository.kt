package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.LiveRoomItem
import dev.aaa1115910.biliapi.http.BiliLiveHttpApi
import dev.aaa1115910.biliapi.http.entity.live.LiveParentArea
import org.koin.core.annotation.Single

data class LiveAreaHome(
    val areas: List<LiveParentArea>,
    val recommendedRooms: List<LiveRoomItem>
)

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

    /** 首页：分区树 + 推荐直播间（同一次 getList 调用） */
    suspend fun getAreaHome(): LiveAreaHome {
        val data = BiliLiveHttpApi.getLiveAreaList(sessData).getResponseData()
        return LiveAreaHome(
            areas = data.gameList,
            recommendedRooms = data.liveList.map { LiveRoomItem.fromRoomInfo(it) }
        )
    }

    /** 子分区分页直播间 */
    suspend fun getRooms(parentAreaId: Int, areaId: Int, page: Int): LiveRoomPage {
        val data = BiliLiveHttpApi.getLiveRoomList(
            parentAreaId = parentAreaId,
            areaId = areaId,
            page = page,
            sessData = sessData
        ).getResponseData()
        return LiveRoomPage(
            list = data.list.map { LiveRoomItem.fromRoomInfo(it) },
            nextPage = page + 1,
            noMore = data.hasMore == 0
        )
    }
}
