package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data

/**
 * Picks one Media3-playable stream URL from a v2 playurl response.
 *
 * Order: protocol `http_hls` only (Media3 cannot play bilibili flv); within it,
 * format `fmp4` > `ts` — fmp4 lives on the gotcha207 edge which is reachable
 * overseas (the bilibili web player uses fmp4), whereas ts is on gotcha105 and
 * times out outside mainland China; prefer codec `avc` (many devices/emulators
 * lack HEVC decode), else highest `current_qn`; first `url_info` entry.
 * The URL is assembled as `url_info.host + base_url + url_info.extra`.
 * Returns null if no playable HLS stream exists.
 */
object LivePlayUrlResolver {
    private val HLS_FORMAT_PRIORITY = listOf("fmp4", "ts")

    fun resolve(data: LivePlayUrlV2Data): ResolvedLivePlayUrl? {
        val streams = data.playurlInfo?.playurl?.stream ?: return null
        val hls = streams.firstOrNull { it.protocolName == "http_hls" } ?: return null
        for (fmt in HLS_FORMAT_PRIORITY) {
            val format = hls.format.firstOrNull { it.formatName == fmt } ?: continue
            // 优先 AVC 系列（模拟器/部分电视盒子没有 HEVC 解码器，HEVC 流会黑屏）；
            // 在选定系列内仍取最高 current_qn，无 AVC 时回退到任意最高 qn。
            val pool = format.codec.filter { it.codecName == "avc" }.ifEmpty { format.codec }
            val codec = pool.maxByOrNull { it.currentQn } ?: continue
            val info = codec.urlInfo.firstOrNull() ?: continue
            if (info.host.isBlank() || codec.baseUrl.isBlank()) continue
            val url = info.host + codec.baseUrl + info.extra
            return ResolvedLivePlayUrl(
                url = url,
                qn = codec.currentQn,
                protocolName = "http_hls",
                formatName = fmt
            )
        }
        return null
    }
}
