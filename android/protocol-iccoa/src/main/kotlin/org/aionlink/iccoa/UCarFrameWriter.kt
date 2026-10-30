package org.aionlink.iccoa

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Independently written encoder for the header described in docs/protocol-research/UCAR_OBSERVED_FORMAT.md. */
object UCarFrameWriter {
    const val SOURCE_PHONE = 1
    const val FORMAT_RAW = 0
    const val FORMAT_PB3 = 1
    const val TYPE_SEND = 0
    const val TYPE_REQ = 1
    const val TYPE_RES = 2
    const val TYPE_SEND_SYNC = 3
    const val CATEGORY_CONTROL = 1
    const val CATEGORY_AUTH = 3
    const val CATEGORY_ACK = 7

    fun flags(source: Int, format: Int, type: Int): Int = (source and 1) or ((format and 3) shl 1) or ((type and 3) shl 3)

    fun frame(flags: Int, category: Int, method: Int, body: ByteArray, sequence: Long, timestampSeconds: Long): ByteArray {
        require(sequence in 1..0x7fffffffL && timestampSeconds in 0..0xffffffffL)
        require(flags in 0..255 && category in 0..255 && method in 0..65535)
        val result = ByteBuffer.allocate(UCarFrameReader.HEADER_BYTES + body.size).order(ByteOrder.BIG_ENDIAN)
            .putInt(UCarFrameReader.HEADER_BYTES + body.size).putInt(sequence.toInt()).putInt(timestampSeconds.toInt())
            .put(flags.toByte()).put(category.toByte()).putShort(method.toShort()).putShort(0).putShort(0).put(body).array()
        val crc = UCarFrameReader.crc16Arc(result, 18)
        result[18] = (crc ushr 8).toByte(); result[19] = crc.toByte()
        return result
    }

    /** Minimal protobuf helpers for independently defined messages. */
    fun varintField(field: Int, value: Long): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        varint(out, (field shl 3).toLong()); varint(out, value)
        return out.toByteArray()
    }

    fun varint(out: java.io.ByteArrayOutputStream, value: Long) {
        var rest = value
        while (rest and 0x7fL.inv() != 0L) { out.write(((rest and 127) or 128).toInt()); rest = rest ushr 7 }
        out.write(rest.toInt())
    }
}
