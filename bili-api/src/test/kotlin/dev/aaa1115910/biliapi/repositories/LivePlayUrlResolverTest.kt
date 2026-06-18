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

    /** One stream (protocol) with the given (formatName -> codecs). */
    private fun data(protocol: String, vararg formatCodecs: Pair<String, List<LiveStreamCodec>>) =
        dataMulti(protocol to formatCodecs.toList())

    /** Multiple streams, each (protocol -> [(format, codecs)]). */
    private fun dataMulti(vararg streams: Pair<String, List<Pair<String, List<LiveStreamCodec>>>>) =
        LivePlayUrlV2Data(
            playurlInfo = PlayurlInfo(
                playurl = Playurl(
                    stream = streams.map { (proto, fmts) ->
                        LiveStream(
                            protocolName = proto,
                            format = fmts.map { (fn, cs) ->
                                LiveStreamFormat(formatName = fn, codec = cs)
                            }
                        )
                    }
                )
            )
        )

    @Test
    fun `prefers fmp4 over flv and ts`() {
        val data = dataMulti(
            "http_hls" to listOf("fmp4" to listOf(codec(10000, "/fmp4.m3u8")), "ts" to listOf(codec(10000, "/ts.m3u8"))),
            "http_stream" to listOf("flv" to listOf(codec(10000, "/x.flv")))
        )
        val resolved = LivePlayUrlResolver.resolve(data)
        assertEquals("$host/fmp4.m3u8", resolved?.url)
        assertEquals("fmp4", resolved?.formatName)
    }

    @Test
    fun `falls back to flv over ts when no fmp4`() {
        // 没有 fmp4 时优先 flv(gotcha07，海外可达)，而非 ts(gotcha105，大陆专属)。
        val data = dataMulti(
            "http_hls" to listOf("ts" to listOf(codec(10000, "/ts.m3u8"))),
            "http_stream" to listOf("flv" to listOf(codec(10000, "/x.flv")))
        )
        val resolved = LivePlayUrlResolver.resolve(data)
        assertEquals("$host/x.flv", resolved?.url)
        assertEquals("flv", resolved?.formatName)
        assertEquals("http_stream", resolved?.protocolName)
    }

    @Test
    fun `falls back to ts when no fmp4 or flv`() {
        val resolved = LivePlayUrlResolver.resolve(data("http_hls", "ts" to listOf(codec(10000, "/ts.m3u8"))))
        assertEquals("$host/ts.m3u8", resolved?.url)
        assertEquals("ts", resolved?.formatName)
    }

    @Test
    fun `picks highest current_qn`() {
        val data = data("http_hls", "fmp4" to listOf(codec(400, "/400.m3u8"), codec(10000, "/10k.m3u8")))
        assertEquals("$host/10k.m3u8", LivePlayUrlResolver.resolve(data)?.url)
        assertEquals(10000, LivePlayUrlResolver.resolve(data)?.qn)
    }

    @Test
    fun `prefers avc over higher-qn hevc`() {
        val data = data("http_hls", "fmp4" to listOf(
            codec(qn = 10000, baseUrl = "/hevc.m3u8", name = "hevc"),
            codec(qn = 400, baseUrl = "/avc.m3u8", name = "avc")
        ))
        val resolved = LivePlayUrlResolver.resolve(data)
        assertEquals("$host/avc.m3u8", resolved?.url)
        assertEquals(400, resolved?.qn)
    }

    @Test
    fun `appends url_info extra after base_url`() {
        val resolved = LivePlayUrlResolver.resolve(
            data("http_hls", "fmp4" to listOf(codec(10000, "/fmp4.m3u8?", extra = "expires=1&sign=abc")))
        )
        assertEquals("$host/fmp4.m3u8?expires=1&sign=abc", resolved?.url)
    }

    @Test
    fun `returns null when codec has empty url_info`() {
        val codecWithoutInfo = LiveStreamCodec(
            codecName = "avc", currentQn = 10000, baseUrl = "/fmp4.m3u8", urlInfo = emptyList()
        )
        assertNull(LivePlayUrlResolver.resolve(data("http_hls", "fmp4" to listOf(codecWithoutInfo))))
    }

    @Test
    fun `returns null for empty data`() {
        assertNull(LivePlayUrlResolver.resolve(LivePlayUrlV2Data()))
    }
}
