package org.aionlink.iccoa

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Independently implemented from docs/protocol-research/UCAR_OBSERVED_FORMAT.md.
 * Offline analysis only. No encoder, socket, authentication, or session acceptance.
 */
data class ObservedUCarHeader(
    val totalBytes: Int, val sequence: Long, val timestampSeconds: Long,
    val source: Int, val format: Int, val messageType: Int,
    val category: Int, val method: Int, val reserved: Int, val unknownFlagBits: Int
) {
    val authMessageLabel: String? get() = if (category == 3 && format == 1 && unknownFlagBits == 0) when {
        method == 1 && messageType == 1 -> "AUTH_REQUEST_STATIC_CANDIDATE"
        method == 2 && messageType == 2 -> "AUTH_RESPONSE_STATIC_CANDIDATE"
        method == 3 && messageType == 0 -> "AUTH_CONFIRM_STATIC_CANDIDATE"
        else -> null
    } else null
}

class ObservedUCarFrame(val header: ObservedUCarHeader, body: ByteArray) {
    // Payload may contain private data; do not include it in toString/diagnostic exports.
    private val payload = body.copyOf()
    fun bodyCopy(): ByteArray = payload.copyOf()
}

object UCarFrameReader {
    const val HEADER_BYTES = 20
    const val DEFAULT_ANALYSIS_LIMIT = 1024 * 1024 // Local resource policy, not a protocol claim.

    fun crc16Arc(bytes: ByteArray, count: Int = bytes.size): Int {
        require(count in 0..bytes.size)
        var value = 0
        for (index in 0 until count) {
            value = value xor (bytes[index].toInt() and 255)
            repeat(8) { value = if (value and 1 != 0) (value ushr 1) xor 0xa001 else value ushr 1 }
        }
        return value and 65535
    }

    fun header(bytes: ByteArray, maxFrameBytes: Int = DEFAULT_ANALYSIS_LIMIT): ObservedUCarHeader {
        require(bytes.size == HEADER_BYTES) { "Expected exactly 20 header bytes" }
        require(maxFrameBytes >= HEADER_BYTES)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        val length = buffer.int.toLong() and 0xffffffffL
        require(length in HEADER_BYTES.toLong()..maxFrameBytes.toLong()) { "Frame length outside analysis limit" }
        val seq = buffer.int.toLong() and 0xffffffffL
        val time = buffer.int.toLong() and 0xffffffffL
        val flags = buffer.get().toInt() and 255
        val category = buffer.get().toInt() and 255
        val method = buffer.short.toInt() and 65535
        val reserved = buffer.short.toInt() and 65535
        val expected = buffer.short.toInt() and 65535
        require(crc16Arc(bytes, 18) == expected) { "Header CRC mismatch" }
        return ObservedUCarHeader(length.toInt(), seq, time, flags and 1,
            (flags ushr 1) and 3, (flags ushr 3) and 3, category, method, reserved, flags and 0xe0)
    }

    /** Reads one frame from a local stream. Null means clean EOF before the header. */
    fun read(input: InputStream, maxFrameBytes: Int = DEFAULT_ANALYSIS_LIMIT): ObservedUCarFrame? {
        require(maxFrameBytes >= HEADER_BYTES)
        val first = input.read()
        if (first == -1) return null
        val bytes = ByteArray(HEADER_BYTES)
        bytes[0] = first.toByte()
        fill(input, bytes, 1)
        val decoded = header(bytes, maxFrameBytes)
        val body = ByteArray(decoded.totalBytes - HEADER_BYTES)
        fill(input, body, 0)
        return ObservedUCarFrame(decoded, body)
    }

    private fun fill(input: InputStream, destination: ByteArray, start: Int) {
        var offset = start
        while (offset < destination.size) {
            val read = input.read(destination, offset, destination.size - offset)
            if (read < 0) throw EOFException("Truncated local frame")
            if (read == 0) {
                val single = input.read()
                if (single < 0) throw EOFException("Truncated local frame")
                destination[offset++] = single.toByte()
            } else offset += read
        }
    }
}
