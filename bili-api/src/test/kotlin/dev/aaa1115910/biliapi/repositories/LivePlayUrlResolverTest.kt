package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data
import dev.aaa1115910.biliapi.http.entity.live.LiveStream
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamCodec
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamFormat
import dev.aaa1115910.biliapi.http.entity.live.LiveStreamUrlInfo
import dev.aaa1115910.biliapi.http.entity.live.Playurl
import dev.aaa1115910.biliapi.http.entity.live.PlayurlInfo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LivePlayUrlResolverTest {

    private val host = "https://h.test"

    /** codec with one url_info entry; resolved URL == host + baseUrl (+ extra). */
    private fun codec(qn: Int, baseUrl: String, name: String = "avc", extra: String = "") = LiveStreamCodec(
        codecName = name,
        currentQn = qn,
        baseUrl = baseUrl,
        urlInfo = listOf(LiveStreamUrlInfo(host = host, extra = extra))
    )

    /** Build a LivePlayUrlV2Data with one stream and the given (formatName -> codecs). */
    private fun data(protocol: String, vararg formatCodecs: Pair<String, List<LiveStreamCodec>>) =
        LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(
                playurl = Playurl(
                    stream = listOf(
                        LiveStream(
                            protocolName = protocol,
                            format = formatCodecs.map { (fn, cs) ->
                                LiveStreamFormat(formatName = fn, codec = cs)
                            }
                        )
                    )
                )
            )
        )

    @Test
    fun `returns assembled ts url when http_hls ts present`() {
        val resolved = LivePlayUrlResolver.resolve(data("http_hls", "ts" to listOf(codec(10000, "/ts.m3u8"))))
        assertEquals("$host/ts.m3u8", resolved?.url)
        assertEquals("ts", resolved?.formatName)
        assertEquals(10000, resolved?.qn)
    }

    @Test
    fun `prefers ts over fmp4`() {
        val data = data(
            "http_hls",
            "fmp4" to listOf(codec(10000, "/fmp4.m3u8")),
            "ts" to listOf(codec(10000, "/ts.m3u8"))
        )
        assertEquals("$host/ts.m3u8", LivePlayUrlResolver.resolve(data)?.url)
    }

    @Test
    fun `falls back to fmp4 when ts absent`() {
        val resolved = LivePlayUrlResolver.resolve(data("http_hls", "fmp4" to listOf(codec(10000, "/fmp4.m3u8"))))
        assertEquals("$host/fmp4.m3u8", resolved?.url)
        assertEquals("fmp4", resolved?.formatName)
    }

    @Test
    fun `picks highest current_qn`() {
        val data = data(
            "http_hls",
            "ts" to listOf(codec(400, "/400.m3u8"), codec(10000, "/10k.m3u8"))
        )
        assertEquals("$host/10k.m3u8", LivePlayUrlResolver.resolve(data)?.url)
        assertEquals(10000, LivePlayUrlResolver.resolve(data)?.qn)
    }

    @Test
    fun `prefers avc over higher-qn hevc`() {
        // 模拟器/部分电视盒子没有 HEVC 解码器：即使 HEVC 的 qn 更高，也应选 AVC。
        val data = data(
            "http_hls",
            "ts" to listOf(
                codec(qn = 10000, baseUrl = "/hevc.m3u8", name = "hevc"),
                codec(qn = 400, baseUrl = "/avc.m3u8", name = "avc")
            )
        )
        val resolved = LivePlayUrlResolver.resolve(data)
        assertEquals("$host/avc.m3u8", resolved?.url)
        assertEquals(400, resolved?.qn)
    }

    @Test
    fun `falls back to highest qn when no avc`() {
        val data = data(
            "http_hls",
            "ts" to listOf(
                codec(qn = 400, baseUrl = "/hevc400.m3u8", name = "hevc"),
                codec(qn = 10000, baseUrl = "/hevc10k.m3u8", name = "hevc")
            )
        )
        assertEquals("$host/hevc10k.m3u8", LivePlayUrlResolver.resolve(data)?.url)
    }

    @Test
    fun `appends url_info extra after base_url`() {
        val resolved = LivePlayUrlResolver.resolve(
            data("http_hls", "ts" to listOf(codec(10000, "/ts.m3u8?", extra = "expires=1&sign=abc")))
        )
        assertEquals("$host/ts.m3u8?expires=1&sign=abc", resolved?.url)
    }

    @Test
    fun `returns null when only flv http_stream available`() {
        assertNull(LivePlayUrlResolver.resolve(data("http_stream", "flv" to listOf(codec(10000, "/x.flv")))))
    }

    @Test
    fun `returns null when http_hls codec has empty url_info`() {
        val codecWithoutInfo = LiveStreamCodec(
            codecName = "avc", currentQn = 10000, baseUrl = "/ts.m3u8", urlInfo = emptyList()
        )
        assertNull(LivePlayUrlResolver.resolve(data("http_hls", "ts" to listOf(codecWithoutInfo))))
    }

    @Test
    fun `returns null for empty data`() {
        assertNull(LivePlayUrlResolver.resolve(LivePlayUrlV2Data()))
    }
}
