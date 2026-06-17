package dev.aaa1115910.biliapi.websocket

import dev.aaa1115910.biliapi.http.entity.live.DanmakuEvent
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

internal class LiveDataWebSocketTest {

    @Test
    fun connectLiveEvent() {
        runBlocking {
            LiveDataWebSocket.connectLiveEvent(5555) {
                println(it)
            }
            for (i in 1..10) {
                delay(1_000)
            }
        }
    }

    @Test
    fun `websocket emits danmaku events`() {
        Assertions.assertDoesNotThrow {
            runBlocking {
                var received = false
                withTimeout(15_000) {
                    // connectLiveEvent suspends for the life of the connection; collect briefly
                    launch {
                        LiveDataWebSocket.connectLiveEvent(roomId = 6) { event ->
                            if (event is DanmakuEvent) received = true
                        }
                    }
                    delay(12_000)
                }
                Assertions.assertTrue(received) { "expected at least one DANMU_MSG in 12s" }
            }
        }
    }
}
