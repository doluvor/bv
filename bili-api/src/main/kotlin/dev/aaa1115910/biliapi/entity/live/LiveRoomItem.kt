package dev.aaa1115910.biliapi.entity.live

import dev.aaa1115910.biliapi.http.entity.live.LiveRoomInfo

data class LiveRoomItem(
    val roomId: Int,
    val uid: Long,
    val uname: String,
    val title: String,
    val cover: String,
    val online: Int,
    val areaName: String,
    val parentAreaId: Int,
    val areaId: Int
) {
    companion object {
        fun fromRoomInfo(info: LiveRoomInfo) = LiveRoomItem(
            roomId = info.roomid,
            uid = info.uid,
            uname = info.uname,
            title = info.title,
            cover = info.userCover.ifBlank { info.cover },
            online = info.online,
            areaName = info.areaName,
            parentAreaId = info.parentAreaId,
            areaId = info.area
        )
    }
}
