package dev.aaa1115910.bv.player.impl.exo

import android.os.SystemClock
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Deque
import java.util.Locale

/**
 * Continuous diagnostic recorder for intermittent playback failures.
 *
 * "invalid nal length" surfaces at a random time in a random video, so its cause
 * is almost always a *precursor* event (a seek, a truncated network read, a run of
 * recoverable codec errors) that happened moments before onError fired. By the
 * time onError fires those precursors are gone unless something was recording.
 *
 * This keeps a rolling window of timestamped events so that when a fatal error
 * finally surfaces, the trail leading into it is still available. Single-threaded:
 * ExoPlayer delivers listener callbacks and our seek/transfer hooks on the
 * application thread, so no locking is needed.
 */
internal class PlayerDiagnostics(
    private val maxEvents: Int = 500
) {
    private data class Event(
        val tMs: Long,         // SystemClock.elapsedRealtime() at record time
        val category: String,  // seek | codec | transfer | state | error
        val text: String
    )

    private val events: Deque<Event> = ArrayDeque(maxEvents)
    private var t0: Long = SystemClock.elapsedRealtime()

    private fun add(category: String, text: String) {
        if (events.size >= maxEvents) events.pollFirst()
        events.addLast(Event(SystemClock.elapsedRealtime(), category, text))
    }

    private fun rel(now: Long = SystemClock.elapsedRealtime()): String =
        String.format(Locale.US, "%07.2f", (now - t0) / 1000.0)

    // ---- seek ----
    fun recordSeek(fromMs: Long, toMs: Long) {
        add("seek", "$fromMs ms -> $toMs ms")
    }

    // ---- codec errors (includes recoverable ones Media3 silently retries) ----
    fun recordCodecError(
        diagnosticInfo: String?,
        errorCode: Int,
        recoverable: Boolean,
        transient: Boolean
    ) {
        add(
            "codec",
            "diag=${diagnosticInfo ?: "null"} code=$errorCode " +
                "recoverable=$recoverable transient=$transient"
        )
    }

    // ---- transfer / network ----
    fun recordTransferOpen(uri: String, position: Long, length: Long) {
        val lenStr = if (length < 0) "unset" else length.toString()
        add("transfer", "open ${shortUri(uri)} @ $position len=$lenStr")
    }

    /**
     * One line per byte-range request (i.e. per open/close). Flags a truncated
     * segment — the prime suspect for mid-stream NAL errors — when the bytes
     * actually transferred fall short of the length requested at open.
     */
    fun recordTransferSummary(bytesTransferred: Long, readCount: Int, expectedLength: Long) {
        val expected = if (expectedLength < 0) "unset" else expectedLength.toString()
        val short = expectedLength >= 0 && bytesTransferred < expectedLength
        val flag = if (short) " ** SHORT (truncated?) **" else ""
        add("transfer", "got $bytesTransferred B (expected $expected) in $readCount reads$flag")
    }

    fun recordTransferError(e: Throwable) {
        add("transfer", "io error: ${e.javaClass.simpleName}: ${e.message}")
    }

    // ---- playback state ----
    fun recordState(state: String) {
        add("state", state)
    }

    // ---- output ----

    /**
     * Events newest last, each line tagged by category. If [recentCount] > 0 only
     * that many most-recent events are emitted (with a marker noting how many
     * earlier ones were dropped) — use this for bounded surfaces like the on-screen
     * tip; pass 0 (the default) for the full history written to the log file.
     */
    fun dump(recentCount: Int = 0): String {
        if (events.isEmpty()) return "(no events recorded)"
        val all = events.toList()
        val start = if (recentCount > 0 && all.size > recentCount) all.size - recentCount else 0
        val now = SystemClock.elapsedRealtime()
        return buildString {
            if (start > 0) {
                appendLine("... ($start earlier events — see player_errors.log) ...")
            }
            for (i in start until all.size) {
                val e = all[i]
                append('[').append(rel(e.tMs)).append("] ")
                append(e.category.padEnd(8))
                append(' ').appendLine(e.text)
            }
            append("[").append(rel(now)).append("] ---- dump end ----")
        }
    }

    fun reset() {
        events.clear()
        t0 = SystemClock.elapsedRealtime()
    }

    private fun shortUri(uri: String): String {
        val noQuery = uri.substringBefore('?')
        return noQuery.substringAfterLast('/').ifBlank { noQuery }
    }

    companion object {
        private const val TAG = "BvPlayerDiagnostics"
        private val DATE_FMT = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)

        /**
         * Append the dump to a crash-trail file so an intermittent error leaves
         * evidence even when nobody is watching logcat. Returns the file path,
         * or null on failure.
         */
        fun persist(dir: File, header: String, body: String): String? {
            return try {
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "player_errors.log")
                file.appendText(
                    buildString {
                        append("==== ").append(DATE_FMT.format(Date())).append(" ====\n")
                        append(header).append('\n')
                        append(body).append('\n')
                        append('\n')
                    }
                )
                file.absolutePath
            } catch (e: Exception) {
                Log.w(TAG, "failed to persist diagnostics", e)
                null
            }
        }
    }
}
