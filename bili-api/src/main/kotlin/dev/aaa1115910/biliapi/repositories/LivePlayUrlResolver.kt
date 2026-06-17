package dev.aaa1115910.biliapi.repositories

import dev.aaa1115910.biliapi.entity.live.ResolvedLivePlayUrl
import dev.aaa1115910.biliapi.http.entity.live.LivePlayUrlV2Data

/**
 * Picks one Media3-playable stream URL from a v2 playurl response.
 *
 * Order: protocol `http_hls` only (Media3 cannot play bilibili flv); within it,
 * format `ts` > `fmp4`; highest `current_qn`; first `url`. Returns null if no
 * playable HLS stream exists.
 */
object LivePlayUrlResolver {
    private val HLS_FORMAT_PRIORITY = listOf("ts", "fmp4")

    fun resolve(data: LivePlayUrlV2Data): ResolvedLivePlayUrl? {
        val streams = data.playurlInfo?.playurl?.stream ?: return null
        val hls = streams.firstOrNull { it.protocolName == "http_hls" } ?: return null
        for (fmt in HLS_FORMAT_PRIORITY) {
            val format = hls.format.firstOrNull { it.formatName == fmt } ?: continue
            val codec = format.codec.maxByOrNull { it.currentQn } ?: continue
            val url = codec.url.firstOrNull() ?: continue
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
