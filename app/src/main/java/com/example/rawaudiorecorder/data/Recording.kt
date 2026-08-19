package com.example.rawaudiorecorder.data

import java.io.File
import java.io.RandomAccessFile

/** A saved recording on disk. */
data class Recording(
    val file: File,
    /** File name without the .wav extension — what the user sees and edits. */
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val recordedAt: Long
) {
    val path: String get() = file.absolutePath
}

/**
 * Reads duration straight out of the WAV header rather than going through
 * MediaMetadataRetriever — cheaper, and it fits an app that writes its own
 * 44-byte headers by hand.
 *
 * Layout of the canonical header this app produces:
 *   offset 0  "RIFF"
 *   offset 8  "WAVE"
 *   offset 28 byte rate      (little-endian int)
 *   offset 40 data chunk size (little-endian int)
 */
object WavInfo {

    private const val HEADER_BYTES = 44

    fun durationMs(file: File): Long {
        if (!file.isFile || file.length() <= HEADER_BYTES) return 0L
        return try {
            RandomAccessFile(file, "r").use { raf ->
                val header = ByteArray(HEADER_BYTES)
                if (raf.read(header) < HEADER_BYTES) return 0L
                if (!header.tagEquals(0, "RIFF") || !header.tagEquals(8, "WAVE")) return 0L

                val byteRate = header.leInt(28)
                if (byteRate <= 0) return 0L

                // Trust the declared data size, but never exceed the real file.
                val declared = header.leInt(40).toLong()
                val actual = file.length() - HEADER_BYTES
                val dataBytes = if (declared in 1..actual) declared else actual

                dataBytes * 1000L / byteRate
            }
        } catch (e: Exception) {
            0L
        }
    }

    private fun ByteArray.leInt(offset: Int): Int =
        (this[offset].toInt() and 0xFF) or
        ((this[offset + 1].toInt() and 0xFF) shl 8) or
        ((this[offset + 2].toInt() and 0xFF) shl 16) or
        ((this[offset + 3].toInt() and 0xFF) shl 24)

    private fun ByteArray.tagEquals(offset: Int, tag: String): Boolean {
        if (size < offset + tag.length) return false
        for (i in tag.indices) {
            if (this[offset + i].toInt().toChar() != tag[i]) return false
        }
        return true
    }
}
