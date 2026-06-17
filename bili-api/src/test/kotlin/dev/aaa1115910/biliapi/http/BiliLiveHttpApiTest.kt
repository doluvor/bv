package dev.aaa1115910.biliapi.http

import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

class BiliLiveHttpApiTest {
    @Test
    fun `get history live room danmaku`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                val response = BiliLiveHttpApi.getLiveDanmuHistory(roomId = 22739471)
                println(response)
            }
        }
    }

    @Test
    fun `get live event websocket connect url and token`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                val response = BiliLiveHttpApi.getLiveDanmuInfo(roomId = 22739471)
                println(response)
            }
        }
    }

    @Test
    fun `get live room info`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                val response = BiliLiveHttpApi.getLiveRoomPlayInfo(roomId = 22739471)
                println(response)
            }
        }
    }

    @Test
    fun `get live area list`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                val response = BiliLiveHttpApi.getLiveAreaList()
                println(response)
            }
        }
    }

    @Test
    fun `get live room list`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                val response = BiliLiveHttpApi.getLiveRoomList(parentAreaId = 2, areaId = 21, page = 1)
                println(response)
            }
        }
    }

    @Test
    fun `get live room play info v2`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                val response = BiliLiveHttpApi.getLiveRoomPlayInfoV2(roomId = 6)
                println(response)
            }
        }
    }

}