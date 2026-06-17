package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data
import dev.aaa1115910.biliapi.http.entity.live.LiveStream
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamCodec
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamFormat
import dev.aaa1115910.biliapi.http.entity.live.Playurl
import dev.aaa1115910.biliapi.http.entity.live.PlayurlInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LivePlayUrlResolverTest {

    private fun hlsTs(qn: Int, vararg urls: String) = LivePlayUrlV2Data(
        playurlInfo = PlayurlInfo(
            playurl = Playurl(
                stream = listOf(
                    LiveStream(
                        protocolName = "http_hls",
                        format = listOf(
                            LiveStreamFormat(
                                formatName = "ts",
                                codec = listOf(LiveStreamCodec(currentQn = qn, url = urls.toList()))
                            )
                        )
                    )
                )
            )
        )
    )

    @Test
    fun `returns ts url when http_hls ts present`() {
        val data = hlsTs(qn = 10000, "https://hls/ts.m3u8")
        val resolved = LivePlayUrlResolver.resolve(data)
        assertEquals("https://hls/ts.m3u8", resolved?.url)
        assertEquals("ts", resolved?.formatName)
        assertEquals(10000, resolved?.qn)
    }

    @Test
    fun `prefers ts over fmp4`() {
        val data = LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(playurl = Playurl(stream = listOf(
                LiveStream(protocolName = "http_hls", format = listOf(
                    LiveStreamFormat(formatName = "fmp4",
                        codec = listOf(LiveStreamCodec(currentQn = 10000, url = listOf("https://hls/fmp4.m3u8")))),
                    LiveStreamFormat(formatName = "ts",
                        codec = listOf(LiveStreamCodec(currentQn = 10000, url = listOf("https://hls/ts.m3u8"))))
                ))
            )))
        )
        assertEquals("https://hls/ts.m3u8", LivePlayUrlResolver.resolve(data)?.url)
    }

    @Test
    fun `picks highest current_qn`() {
        val data = LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(playurl = Playurl(stream = listOf(
                LiveStream(protocolName = "http_hls", format = listOf(
                    LiveStreamFormat(formatName = "ts", codec = listOf(
                        LiveStreamCodec(currentQn = 400, url = listOf("https://hls/400.m3u8")),
                        LiveStreamCodec(currentQn = 10000, url = listOf("https://hls/10k.m3u8"))
                    ))
                ))
            )))
        )
        assertEquals("https://hls/10k.m3u8", LivePlayUrlResolver.resolve(data)?.url)
        assertEquals(10000, LivePlayUrlResolver.resolve(data)?.qn)
    }

    @Test
    fun `returns null when only flv http_stream available`() {
        val data = LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(playurl = Playurl(stream = listOf(
                LiveStream(protocolName = "http_stream", format = listOf(
                    LiveStreamFormat(formatName = "flv",
                        codec = listOf(LiveStreamCodec(currentQn = 10000, url = listOf("https://flv/x.flv"))))
                ))
            )))
        )
        assertNull(LivePlayUrlResolver.resolve(data))
    }

    @Test
    fun `returns null for empty data`() {
        assertNull(LivePlayUrlResolver.resolve(LivePlayUrlV2Data()))
    }
}
