package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data

/**
 * Picks one playable stream URL from a v2 playurl response.
 *
 * Candidate priority (matches the bilibili web player, and the overseas-reachable
 * CDN edges): `http_hls/fmp4` (gotcha207) > `http_stream/flv` (gotcha07) >
 * `http_hls/ts` (gotcha105, mainland-only — times out outside China).
 *
 * Within a candidate: prefer codec `avc` (many devices/emulators lack HEVC decode),
 * else highest `current_qn`; first `url_info` entry. The URL is assembled as
 * `url_info.host + base_url + url_info.extra`. Returns null if nothing playable.
 *
 * The returned [ResolvedLivePlayUrl.protocolName]/[ResolvedLivePlayUrl.formatName]
 * tell the caller how to play it: `http_hls` → HLS; `http_stream`/`flv` → progressive FLV.
 */
object LivePlayUrlResolver {
    private val CANDIDATES = listOf(
        "http_hls" to "fmp4",
        "http_stream" to "flv",
        "http_hls" to "ts"
    )

    fun resolve(data: LivePlayUrlV2Data): ResolvedLivePlayUrl? {
        val streams = data.playurlInfo?.playurl?.stream ?: return null
        for ((proto, fmt) in CANDIDATES) {
            val stream = streams.firstOrNull { it.protocolName == proto } ?: continue
            val format = stream.format.firstOrNull { it.formatName == fmt } ?: continue
            // 优先 AVC 系列（模拟器/部分电视盒子没有 HEVC 解码器）；选定系列内取最高 current_qn。
            val pool = format.codec.filter { it.codecName == "avc" }.ifEmpty { format.codec }
            val codec = pool.maxByOrNull { it.currentQn } ?: continue
            val info = codec.urlInfo.firstOrNull() ?: continue
            if (info.host.isBlank() || codec.baseUrl.isBlank()) continue
            return ResolvedLivePlayUrl(
                url = info.host + codec.baseUrl + info.extra,
                qn = codec.currentQn,
                protocolName = proto,
                formatName = fmt
            )
        }
        return null
    }
}
