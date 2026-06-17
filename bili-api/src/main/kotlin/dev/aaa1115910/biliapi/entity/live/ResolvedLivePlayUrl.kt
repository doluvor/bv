package dev.aaa1115910.biliapi.entity.live

/** The single playable stream URL chosen by LivePlayUrlResolver. */
data class ResolvedLivePlayUrl(
    val url: String,
    val qn: Int,
    val protocolName: String,
    val formatName: String
)
