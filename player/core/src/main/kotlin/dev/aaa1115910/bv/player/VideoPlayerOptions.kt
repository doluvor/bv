package dev.aaa1115910.bv.player

data class VideoPlayerOptions(
    val userAgent: String? = null,
    val referer: String? = null,
    val enableFfmpegAudioRenderer: Boolean = false,
    /** 额外的默认请求头（会附加到每个媒体请求，例如直播需要的 Cookie）。 */
    val extraHeaders: Map<String, String> = emptyMap()
)
