package dev.aaa1115910.biliapi.http.util

import io.ktor.utils.io.core.use
import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

fun ByteArray.brotliDecompress(): ByteArray {
    val outputStream = ByteArrayOutputStream()
    return outputStream.use {
        BrotliInputStream(ByteArrayInputStream(this)).use { stream ->
            stream.copyTo(it)
        }
        it.toByteArray()
    }
}
