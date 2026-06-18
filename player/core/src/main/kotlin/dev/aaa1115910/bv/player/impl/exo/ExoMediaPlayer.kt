package dev.aaa1115910.bv.player.impl.exo

import android.content.Context
import android.media.MediaCodec
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.MergingMediaSource
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy
import dev.aaa1115910.bv.player.AbstractVideoPlayer
import dev.aaa1115910.bv.player.OkHttpUtil
import dev.aaa1115910.bv.player.VideoPlayerOptions
import dev.aaa1115910.bv.util.formatHourMinSec

@OptIn(UnstableApi::class)
class ExoMediaPlayer(
    private val context: Context,
    private val options: VideoPlayerOptions
) : AbstractVideoPlayer(), Player.Listener {
    var mPlayer: ExoPlayer? = null
    protected var mMediaSource: MediaSource? = null

    private val diagnostics = PlayerDiagnostics()

    /** Populated in onPlayerError; read by AbstractVideoPlayer.errorDiagnostics for the UI. */
    private var lastErrorDump: String? = null
    private var malformedRetryCount = 0

    @OptIn(UnstableApi::class)
    private val okHttpFactory =
        OkHttpDataSource.Factory(OkHttpUtil.generateCustomSslOkHttpClient(context)).apply {
            options.userAgent?.let { setUserAgent(it) }
            val headers = buildMap {
                options.referer?.let { put("referer", it) }
                options.extraHeaders.forEach { (k, v) -> put(k, v) }
            }
            if (headers.isNotEmpty()) setDefaultRequestProperties(headers)
        }

    @OptIn(UnstableApi::class)
    private val dataSourceFactory = DataSource.Factory {
        LoggingDataSource(okHttpFactory.createDataSource(), diagnostics)
    }

    // Create the robust retry policy
    @OptIn(UnstableApi::class)
    private val customLoadErrorHandlingPolicy = object : DefaultLoadErrorHandlingPolicy() {
        override fun getMinimumLoadableRetryCount(dataType: Int): Int {
            // If it's a media segment chunk (like your failing .m4s file), retry up to 6 times
            return if (dataType == C.DATA_TYPE_MEDIA) {
                6
            } else {
                super.getMinimumLoadableRetryCount(dataType)
            }
        }
    }

    /**
     * Captures recoverable codec errors (which never reach onPlayerError) plus
     * playback-state transitions, feeding them into the diagnostics trail.
     * Declared before initPlayer() so it is initialized before registration.
     */
    private val analyticsListener = object : AnalyticsListener {
        override fun onPlaybackStateChanged(
            eventTime: AnalyticsListener.EventTime,
            state: Int
        ) {
            diagnostics.recordState(stateName(state))
        }

        override fun onVideoCodecError(
            eventTime: AnalyticsListener.EventTime,
            codecError: Exception
        ) {
            // Media3 delivers the codec failure as a plain Exception here; extract
            // the richer fields when it is actually a MediaCodec.CodecException.
            val ce = codecError as? MediaCodec.CodecException
            diagnostics.recordCodecError(
                diagnosticInfo = ce?.diagnosticInfo ?: codecError.message,
                errorCode = ce?.errorCode ?: -1,
                recoverable = ce?.isRecoverable ?: false,
                transient = ce?.isTransient ?: false
            )
            Log.w("BvPlayer", "video codec error: ${codecError.message}")
        }
    }

    init {
        initPlayer()
    }

    @OptIn(UnstableApi::class)
    override fun initPlayer() {
        val renderersFactory = DefaultRenderersFactory(context).apply {
            setExtensionRendererMode(
                when (options.enableFfmpegAudioRenderer) {
                    true -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                    false -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                }
            )
        }
        mPlayer = ExoPlayer
            .Builder(context)
            .setRenderersFactory(renderersFactory)
            .setSeekForwardIncrementMs(1000 * 10)
            .setSeekBackIncrementMs(1000 * 5)
            .build()

        initListener()
    }

    private fun initListener() {
        mPlayer?.addListener(this)
        mPlayer?.addAnalyticsListener(analyticsListener)
    }

    @OptIn(UnstableApi::class)
    override fun setHeader(headers: Map<String, String>) {

    }

    @OptIn(UnstableApi::class)
    override fun playUrl(videoUrl: String?, audioUrl: String?) {
        malformedRetryCount = 0
        val videoMediaSource = videoUrl?.let {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
                .createMediaSource(MediaItem.fromUri(it))
        }
        val audioMediaSource = audioUrl?.let {
            ProgressiveMediaSource.Factory(dataSourceFactory)
                .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
                .createMediaSource(MediaItem.fromUri(it))
        }

        val mediaSources = listOfNotNull(videoMediaSource, audioMediaSource)
        mMediaSource = MergingMediaSource(*mediaSources.toTypedArray())
    }

    @OptIn(UnstableApi::class)
    override fun playLiveUrl(videoUrl: String) {
        malformedRetryCount = 0
        mMediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .setLoadErrorHandlingPolicy(customLoadErrorHandlingPolicy)
            .createMediaSource(MediaItem.fromUri(videoUrl))
    }

    @OptIn(UnstableApi::class)
    override fun prepare() {
        mPlayer?.setMediaSource(mMediaSource!!)
        mPlayer?.prepare()
    }

    override fun start() {
        mPlayer?.play()
    }

    override fun pause() {
        mPlayer?.pause()
    }

    override fun stop() {
        mPlayer?.stop()
    }

    override fun reset() {
        TODO("Not yet implemented")
    }

    override val isPlaying: Boolean
        get() = mPlayer?.isPlaying == true

    override fun seekTo(time: Long) {
        diagnostics.recordSeek(currentPosition, time)
        mPlayer?.seekTo(time)
    }

    override fun release() {
        mPlayer?.release()
    }

    override val currentPosition: Long
        get() = mPlayer?.currentPosition ?: 0
    override val duration: Long
        get() = mPlayer?.duration ?: 0
    override val bufferedPercentage: Int
        get() = mPlayer?.bufferedPercentage ?: 0

    override fun setOptions() {
        mPlayer?.playWhenReady = true
    }

    override var speed: Float
        get() = mPlayer?.playbackParameters?.speed ?: 1f
        set(value) {
            mPlayer?.setPlaybackSpeed(value)
        }
    override val tcpSpeed: Long
        get() = 0L

    override fun onPlaybackStateChanged(playbackState: Int) {
        when (playbackState) {
            Player.STATE_IDLE -> mPlayerEventListener?.onIdle()
            Player.STATE_BUFFERING -> mPlayerEventListener?.onBuffering()
            Player.STATE_READY -> {
                mPlayerEventListener?.onReady()
                malformedRetryCount = 0
            }
            Player.STATE_ENDED -> mPlayerEventListener?.onEnd()
        }
    }

    override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
            mPlayerEventListener?.onPlay()
        } else {
            mPlayerEventListener?.onPause()
        }
    }

    override fun onSeekBackIncrementChanged(seekBackIncrementMs: Long) {
        mPlayerEventListener?.onSeekBack(seekBackIncrementMs)
    }

    override fun onSeekForwardIncrementChanged(seekForwardIncrementMs: Long) {
        mPlayerEventListener?.onSeekForward(seekForwardIncrementMs)
    }

    override val debugInfo: String
        get() {
            return """
                player: ${androidx.media3.common.MediaLibraryInfo.VERSION_SLASHY}
                time: ${currentPosition.formatHourMinSec()} / ${duration.formatHourMinSec()}
                buffered: $bufferedPercentage%
                resolution: ${mPlayer?.videoSize?.width} x ${mPlayer?.videoSize?.height}
                audio: ${mPlayer?.audioFormat?.bitrate ?: 0} kbps
                video codec: ${mPlayer?.videoFormat?.sampleMimeType ?: "null"}
                audio codec: ${mPlayer?.audioFormat?.sampleMimeType ?: "null"} (${getAudioRendererName()})
            """.trimIndent()
        }

    private fun getAudioRendererName(): String {
        val rendererCount = mPlayer?.rendererCount ?: return "UnknownRenderer"
        for (i in 0 until rendererCount) {
            val renderer = mPlayer!!.getRenderer(i)
            if (renderer.trackType == C.TRACK_TYPE_AUDIO && renderer.state == Renderer.STATE_STARTED) {
                return renderer.name
            }
        }
        return "UnknownRenderer"
    }

    override val videoWidth: Int
        get() = mPlayer?.videoSize?.width ?: 0
    override val videoHeight: Int
        get() = mPlayer?.videoSize?.height ?: 0

    override val errorDiagnostics: String
        get() = lastErrorDump ?: ""

    override fun onPlayerError(error: PlaybackException) {
        val header =
            "PlaybackException: ${PlaybackException.getErrorCodeName(error.errorCode)} (${error.errorCode})"
        val snapshot = buildSnapshot(error)
        // On-screen tip: keep it short — only the events right before the failure
        // (the precursors are what reveal the cause). File/logcat get everything.
        val uiDump = snapshot + "\n== recent events (last 10) ==\n" + diagnostics.dump(10)
        val fullDump = snapshot + "\n== full event trail ==\n" + diagnostics.dump()

        lastErrorDump = uiDump
        Log.e("BvPlayer", "PlaybackException dump:\n$fullDump")
        context.getExternalFilesDir(null)?.let { dir ->
            PlayerDiagnostics.persist(dir, header, fullDump)
        }

        // Auto-recover from a malformed container by seeking past the corrupt region.
        // Capped at MAX_MALFORMED_RETRIES: once that many consecutive attempts fail
        // without reaching STATE_READY, give up and surface the error. The counter is
        // reset on STATE_READY (every recovered region gets a fresh budget) and in playUrl().
        val mediaSource = mMediaSource
        // Recover only when we actually have a source to recover with and haven't hit
        // the cap; otherwise fall through and surface the error so the UI isn't left
        // without a tip (previously the early return swallowed the error here).
        if (error.errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED
            && mediaSource != null
            && malformedRetryCount < MAX_MALFORMED_RETRIES
        ) {
            malformedRetryCount++
            val seekPos = (mPlayer?.currentPosition ?: 0) + SEEK_FORWARD_MS
            Log.w(
                "BvPlayer",
                "Malformed container error. Retry $malformedRetryCount/$MAX_MALFORMED_RETRIES, seeking to $seekPos"
            )
            // Re-use the full MediaSource to preserve headers, SSL, and track merging
            mPlayer?.setMediaSource(mediaSource, seekPos)
            mPlayer?.prepare()
            mPlayer?.play()
            return // suppress the error tip while auto-recovery is in progress
        }

        malformedRetryCount = 0
        mPlayerEventListener?.onError(error)
    }

    private fun buildSnapshot(error: PlaybackException): String = buildString {
        appendLine("== snapshot ==")
        append("errorCode: ").append(PlaybackException.getErrorCodeName(error.errorCode))
        append(" (").append(error.errorCode).appendLine(")")
        append("position: ").append(currentPosition).append(" / ").append(duration).appendLine(" ms")
        append("buffered: ").append(bufferedPercentage).appendLine("%")
        append("isPlaying: ").appendLine(mPlayer?.isPlaying)
        append("videoSize: ").append(mPlayer?.videoSize).appendLine()

        mPlayer?.videoFormat?.let { f -> appendFormat("video", f) }
        mPlayer?.audioFormat?.let { f -> appendFormat("audio", f) }

        appendLine("== cause chain ==")
        var t: Throwable? = error
        var depth = 0
        while (t != null && depth < 8) {
            append("  [").append(depth).append("] ").append(t.javaClass.name)
            t.message?.let { append(": ").append(it) }
            appendLine()
            t = t.cause
            depth++
        }
        findCodecException(error)?.let { ce ->
            append("codec diagnostic: ").append(ce.diagnosticInfo).appendLine()
            append("codec errorCode: ").append(ce.errorCode)
            append(" recoverable=").append(ce.isRecoverable)
            append(" transient=").appendLine(ce.isTransient)
        }
    }

    private fun StringBuilder.appendFormat(label: String, f: Format) {
        append(label).append(" codec: ").append(f.sampleMimeType ?: "null")
        append("  codecs=").appendLine(f.codecs ?: "null")
        val csd = f.initializationData
        append("csd entries: ").append(csd?.size ?: 0)
        csd?.forEachIndexed { i, b ->
            append("\n  csd[").append(i).append("] len=").append(b.size)
            append(" head=").append(hexHead(b))
        }
        appendLine()
    }

    private fun findCodecException(t: Throwable?): MediaCodec.CodecException? {
        var cur = t
        while (cur != null) {
            if (cur is MediaCodec.CodecException) return cur
            cur = cur.cause
        }
        return null
    }

    private fun hexHead(b: ByteArray, max: Int = 16): String {
        val n = minOf(b.size, max)
        val sb = StringBuilder(n * 3)
        for (i in 0 until n) {
            if (i > 0) sb.append(' ')
            sb.append(String.format("%02x", b[i].toInt() and 0xff))
        }
        if (b.size > max) sb.append(" …(").append(b.size).append(')')
        return sb.toString()
    }

    private fun stateName(state: Int): String = when (state) {
        Player.STATE_IDLE -> "idle"
        Player.STATE_BUFFERING -> "buffering"
        Player.STATE_READY -> "ready"
        Player.STATE_ENDED -> "ended"
        else -> "state($state)"
    }

    companion object {
        // Max auto-recovery attempts for a malformed container before surfacing the error.
        private const val MAX_MALFORMED_RETRIES = 20
        // How far to jump forward on each retry to skip past the corrupt region.
        private const val SEEK_FORWARD_MS = 500L
    }
}
