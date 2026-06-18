package dev.aaa1115910.biliapi.http

import dev.aaa1115910.biliapi.http.entity.BiliResponse
import dev.aaa1115910.biliapi.http.entity.live.DanmuInfoData
import dev.aaa1115910.biliapi.http.entity.live.HistoryDanmaku
import dev.aaa1115910.biliapi.http.entity.live.LiveParentArea
import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data
import dev.aaa1115910.biliapi.http.entity.live.LiveRoomInfo
import dev.aaa1115910.biliapi.http.entity.live.RoomPlayInfoData
import dev.aaa1115910.biliapi.http.plugins.BiliUserAgent
import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.URLProtocol
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json

object BiliLiveHttpApi {
    private var endPoint: String = ""
    private lateinit var client: HttpClient
    private val logger = KotlinLogging.logger { }

    init {
        createClient()
    }

    private fun createClient() {
        client = HttpClient(OkHttp) {
            BiliUserAgent()
            install(ContentNegotiation) {
                json(Json {
                    coerceInputValues = true
                    ignoreUnknownKeys = true
                    prettyPrint = true
                })
            }
            install(ContentEncoding) {
                deflate(1.0F)
                gzip(0.9F)
            }
            defaultRequest {
                url {
                    host = "api.live.bilibili.com"
                    protocol = URLProtocol.HTTPS
                }
            }
        }
    }

    /**
     * 获取直播间[roomId]的弹幕连接地址等信息，例如 token。
     * 该接口有风控(-352)，需要 buvid3（以及登录态 SESSDATA）才能通过。
     */
    suspend fun getLiveDanmuInfo(
        roomId: Int,
        sessData: String = "",
        buvid3: String = ""
    ): BiliResponse<DanmuInfoData> =
        client.get("/xlive/web-room/v1/index/getDanmuInfo") {
            parameter("id", roomId)
            val cookieParts = listOfNotNull(
                sessData.takeIf { it.isNotEmpty() }?.let { "SESSDATA=$it" },
                buvid3.takeIf { it.isNotEmpty() }?.let { "buvid3=$it" }
            )
            if (cookieParts.isNotEmpty()) header("Cookie", cookieParts.joinToString("; "))
        }.body()

    /**
     * 获取直播间[roomId]的信息
     */
    suspend fun getLiveRoomPlayInfo(roomId: Int): BiliResponse<RoomPlayInfoData> =
        client.get("/xlive/web-room/v1/index/getRoomPlayInfo") {
            parameter("room_id", roomId)
        }.body()

    /**
     * 获取直播间[roomId]的历史弹幕
     */
    suspend fun getLiveDanmuHistory(roomId: Int): BiliResponse<HistoryDanmaku> =
        client.get("/xlive/web-room/v1/dM/gethistory") {
            parameter("roomid", roomId)
        }.body()

    /** 直播分区目录（经典接口 room/v1/Area/getList，无需登录） */
    suspend fun getLiveAreaList(sessData: String = ""): BiliResponse<List<LiveParentArea>> =
        client.get("/room/v1/Area/getList") {
            if (sessData.isNotEmpty()) header("Cookie", "SESSDATA=$sessData;")
        }.body()

    /** 某子分区的直播间列表（分页，经典接口 room/v1/Area/getRoomList，无需登录） */
    suspend fun getLiveRoomList(
        parentAreaId: String,
        areaId: String,
        page: Int,
        pageSize: Int = 30,
        sessData: String = ""
    ): BiliResponse<List<LiveRoomInfo>> =
        client.get("/room/v1/Area/getRoomList") {
            parameter("parent_area_id", parentAreaId)
            parameter("area_id", areaId)
            parameter("page", page)
            parameter("page_size", pageSize)
            if (sessData.isNotEmpty()) header("Cookie", "SESSDATA=$sessData;")
        }.body()

    /** 直播间 v2 播放地址（结构化 playurl；v1 的 play_url 为 null 不可用） */
    suspend fun getLiveRoomPlayInfoV2(
        roomId: Int,
        qn: Int = 10000,
        sessData: String = ""
    ): BiliResponse<LivePlayUrlV2Data> =
        client.get("/xlive/web-room/v2/index/getRoomPlayInfo") {
            parameter("room_id", roomId)
            parameter("protocol", "0,1")
            parameter("format", "0,1,2")
            parameter("codec", "0,1")
            parameter("qn", qn)
            parameter("platform", "web")
            parameter("ptype", 16)
            parameter("dolby", 5)
            parameter("panorama", 1)
            if (sessData.isNotEmpty()) header("Cookie", "SESSDATA=$sessData;")
        }.body()

}
