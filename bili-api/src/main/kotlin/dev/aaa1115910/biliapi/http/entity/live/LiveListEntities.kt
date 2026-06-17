package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LiveAreaListData(
    @SerialName("game_list") val gameList: List<LiveParentArea> = emptyList(),
    @SerialName("live_list") val liveList: List<LiveRoomInfo> = emptyList()
)

@Serializable
data class LiveParentArea(
    val id: Int = 0,
    val name: String = "",
    val list: List<LiveArea> = emptyList()
)

@Serializable
data class LiveArea(
    val id: Int = 0,
    @SerialName("parent_id") val parentId: Int = 0,
    val name: String = "",
    val pic: String = ""
)

@Serializable
data class LiveRoomListData(
    val list: List<LiveRoomInfo> = emptyList(),
    val count: Int = 0,
    @SerialName("has_more") val hasMore: Int = 0
)

@Serializable
data class LiveRoomInfo(
    val roomid: Int = 0,
    val uid: Long = 0,
    val uname: String = "",
    val title: String = "",
    val cover: String = "",
    @SerialName("user_cover") val userCover: String = "",
    val online: Int = 0,
    @SerialName("area_name") val areaName: String = "",
    val area: Int = 0,
    @SerialName("parent_area_id") val parentAreaId: Int = 0,
    @SerialName("live_status") val liveStatus: Int = 0
)
