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
        runBlocking {
            val response = BiliLiveHttpApi.getLiveAreaList()
            println(response)
            Assertions.assertEquals(0, response.code, "area list code: ${response.message}")
            Assertions.assertTrue(
                response.data.orEmpty().isNotEmpty(),
                "area list should not be empty"
            )
        }
    }

    @Test
    fun `get live room list`() {
        runBlocking {
            val response = BiliLiveHttpApi.getLiveRoomList(parentAreaId = "2", areaId = "86", page = 1)
            println(response)
            Assertions.assertEquals(0, response.code, "room list code: ${response.message}")
            Assertions.assertTrue(
                response.data.orEmpty().isNotEmpty(),
                "room list should not be empty"
            )
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