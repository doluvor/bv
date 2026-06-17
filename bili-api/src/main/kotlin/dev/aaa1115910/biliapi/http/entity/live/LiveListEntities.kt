package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 直播分区与房间 DTO（对应经典接口 room/v1/Area，无需登录）。
 *
 * 注：早期版本使用了 xlive/web-interface 的 getList / second/getList，
 * 但这些接口在未登录/无风控 cookie 时返回 -400 / -352，故改用经典接口。
 */

@Serializable
data class LiveParentArea(
    val id: Int = 0,
    val name: String = "",
    val list: List<LiveArea> = emptyList()
)

@Serializable
data class LiveArea(
    // 经典接口的子分区 id / parent_id 是字符串（如 "86" / "2"）。
    val id: String = "",
    @SerialName("parent_id") val parentId: String = "",
    val name: String = "",
    val pic: String = ""
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
    // 经典 room/v1/Area/getRoomList 使用 area_id / parent_id（Int）。
    @SerialName("area_id") val area: Int = 0,
    @SerialName("parent_id") val parentAreaId: Int = 0
)
