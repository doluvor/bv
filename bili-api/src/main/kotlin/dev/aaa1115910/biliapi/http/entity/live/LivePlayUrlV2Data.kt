package dev.aaa1115910.biliapi.http.entity.live

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class LivePlayUrlV2Data(
    @SerialName("playurl_info") val playurlInfo: PlayurlInfo? = null
)

@Serializable
data class PlayurlInfo(
    val playurl: Playurl? = null
)

@Serializable
data class Playurl(
    val stream: List<LiveStream> = emptyList(),
    @SerialName("g_qn_desc") val gQnDesc: List<QnDesc> = emptyList()
)

@Serializable
data class LiveStream(
    @SerialName("protocol_name") val protocolName: String = "",
    val format: List<LiveStreamFormat> = emptyList()
)

@Serializable
data class LiveStreamFormat(
    @SerialName("format_name") val formatName: String = "",
    val codec: List<LiveStreamCodec> = emptyList()
)

@Serializable
data class LiveStreamCodec(
    @SerialName("codec_name") val codecName: String = "",
    @SerialName("current_qn") val currentQn: Int = 0,
    @SerialName("base_url") val baseUrl: String = "",
    val url: List<String> = emptyList()
)

@Serializable
data class QnDesc(
    val qn: Int = 0,
    val desc: String = ""
)
