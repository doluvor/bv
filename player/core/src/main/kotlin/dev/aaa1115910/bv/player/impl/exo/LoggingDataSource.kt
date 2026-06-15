package dev.aaa1115910.bv.player.impl.exo

import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec

/**
 * Forwarding DataSource that feeds notable transfer events into PlayerDiagnostics:
 * stream open (with byte range), a per-request summary flagged when the segment
 * came up short, and any IO exception. Normal successful reads are aggregated into
 * the summary rather than logged individually, so the trail stays readable.
 */
@OptIn(UnstableApi::class)
internal class LoggingDataSource(
    private val delegate: DataSource,
    private val diag: PlayerDiagnostics
) : DataSource by delegate {

    private var expectedLength: Long = -1
    private var bytesTransferred: Long = 0
    private var readCount: Int = 0
    private var open: Boolean = false

    override fun open(dataSpec: DataSpec): Long {
        return try {
            val result = delegate.open(dataSpec)
            expectedLength = dataSpec.length
            bytesTransferred = 0
            readCount = 0
            open = true
            diag.recordTransferOpen(dataSpec.uri.toString(), dataSpec.position, dataSpec.length)
            result
        } catch (e: Exception) {
            diag.recordTransferError(e)
            throw e
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return try {
            val start = SystemClock.elapsedRealtime()
            val read = delegate.read(buffer, offset, length)
            // DataSource.read returns the count, or C.RESULT_END_OF_INPUT (-1) at EOF.
            if (read > 0) {
                bytesTransferred += read
                readCount++
            }
            read
        } catch (e: Exception) {
            diag.recordTransferError(e)
            throw e
        }
    }

    override fun close() {
        try {
            delegate.close()
        } finally {
            if (open) {
                diag.recordTransferSummary(bytesTransferred, readCount, expectedLength)
                open = false
            }
        }
    }
}
