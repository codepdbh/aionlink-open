package org.aionlink.iccoa

import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Offline-only AUTH request codec, independently authored from observed field numbers. */
object ExperimentalAuthWire {
    const val MAX_BODY = 4096
    /** Samsung ShareLinkService work modes: NORMAL_CAST (1) | MIRROR_WINDOW_CAST (256). Result 4 rejects modes. */
    const val OBSERVED_CLIENT_WORK_MODES = 1 or 256
    /** Samsung SequenceGenerator starts at 1; the peer's MessageHeader.updateSeqId rejects values <= 0. */
    const val FIRST_SEQUENCE = 1L

    class Request(
        val version: Int, val authPoint: ByteArray, val proof: ByteArray,
        val agreementPoint: ByteArray, val agreementSignature: ByteArray,
        val nonce: ByteArray, val clientId: ByteArray, val model: String,
        val confirmed: Boolean, val workModes: Int, val productName: String
    ) {
        override fun toString() = "AuthRequest(redacted)"
    }

    fun requestBody(value: Request): ByteArray {
        require(value.version > 0 && value.workModes >= 0)
        require(value.authPoint.size == 64 && value.proof.size == 32 && value.agreementPoint.size == 64)
        require(value.agreementSignature.size in 8..80 && value.nonce.size == 32)
        require(value.clientId.size in 1..64 && value.model.toByteArray(Charsets.UTF_8).size in 1..64)
        require(value.productName.toByteArray(Charsets.UTF_8).size in 1..64 && value.confirmed)
        val out = ByteArrayOutputStream()
        fun number(field: Int, n: Int) { varint(out, (field shl 3).toLong()); varint(out, n.toLong()) }
        fun blob(field: Int, data: ByteArray) {
            varint(out, ((field shl 3) or 2).toLong()); varint(out, data.size.toLong()); out.write(data)
        }
        number(1, value.version); blob(2, value.authPoint); blob(3, value.proof)
        blob(4, value.agreementPoint); blob(5, value.agreementSignature); blob(6, value.nonce)
        blob(7, value.clientId); blob(8, value.model.toByteArray(Charsets.UTF_8))
        number(9, 1); number(10, value.workModes); blob(11, value.productName.toByteArray(Charsets.UTF_8))
        return out.toByteArray().also { require(it.size <= MAX_BODY) }
    }

    /** Header values are sourced from static evidence, not receiver verification. */
    fun requestFrame(value: Request, sequence: Long, timestampSeconds: Long): ByteArray {
        val body = requestBody(value)
        try { return frame(0x0b, 1, body, sequence, timestampSeconds) } finally { body.fill(0) }
    }

    /** Phone-sourced PB3 SEND, AUTH category, method 3 (auth_confirm); field 1 holds the sealed cipher. */
    fun confirmFrame(cipher: ByteArray, sequence: Long, timestampSeconds: Long): ByteArray {
        require(cipher.size in 1..MAX_BODY - 8)
        val out = ByteArrayOutputStream()
        varint(out, (1 shl 3 or 2).toLong()); varint(out, cipher.size.toLong()); out.write(cipher)
        val body = out.toByteArray()
        try { return frame(0x03, 3, body, sequence, timestampSeconds) } finally { body.fill(0) }
    }

    private fun frame(flags: Int, method: Int, body: ByteArray, sequence: Long, timestampSeconds: Long): ByteArray =
        UCarFrameWriter.frame(flags, UCarFrameWriter.CATEGORY_AUTH, method, body, sequence, timestampSeconds)

    class Response internal constructor(val version: Int?, val authPoint: ByteArray?, val proof: ByteArray?,
        val agreementPoint: ByteArray?, val agreementSignature: ByteArray?, val nonce: ByteArray?,
        val result: Int?, val workModes: Int?) {
        override fun toString() = "AuthResponse(redacted)"
    }

    /** Decode only a correlated candidate AUTH response; cryptographic verification remains mandatory. */
    fun responseFrame(frame: ByteArray, expectedSequence: Long): Response {
        require(frame.size in UCarFrameReader.HEADER_BYTES..(UCarFrameReader.HEADER_BYTES + MAX_BODY))
        val input = ByteArrayInputStream(frame)
        val parsed = UCarFrameReader.read(input, UCarFrameReader.HEADER_BYTES + MAX_BODY) ?: error("Empty frame")
        val header = parsed.header
        require(input.available() == 0 && header.sequence == expectedSequence && header.source == 0 &&
            header.authMessageLabel == "AUTH_RESPONSE_STATIC_CANDIDATE") { "Unexpected AUTH response" }
        val fields = mutableMapOf<Int, Any>()
        val body = parsed.bodyCopy()
        try {
            val cursor = ByteArrayInputStream(body)
            while (cursor.available() > 0) {
                val tag = readVarint(cursor)
                val field = tag ushr 3
                val wire = (tag and 7).toInt()
                require(field in 1..0x1fffffffL) { "Invalid field" }
                if (field in 1L..8L) {
                    require(if (field in 2L..6L) wire == 2 else wire == 0) { "Unexpected AUTH field type" }
                }
                when (wire) {
                    0 -> {
                        val number = readVarint(cursor)
                        if (field in listOf(1L, 7L, 8L)) {
                            require(number >= 0 && number <= Int.MAX_VALUE && fields.put(field.toInt(), number.toInt()) == null)
                        }
                    }
                    2 -> {
                        val length = readVarint(cursor)
                        require(length >= 0 && length <= cursor.available().toLong() && length <= MAX_BODY)
                        val value = ByteArray(length.toInt())
                        require(cursor.read(value) == value.size || value.isEmpty())
                        if (field in 2L..6L) require(fields.put(field.toInt(), value) == null)
                        else value.fill(0)
                    }
                    1, 5 -> {
                        val count = if (wire == 1) 8 else 4
                        require(cursor.skip(count.toLong()) == count.toLong())
                    }
                    else -> throw IllegalArgumentException("Unsupported protobuf wire type")
                }
            }
            return Response(fields[1] as? Int, fields[2] as? ByteArray, fields[3] as? ByteArray,
                fields[4] as? ByteArray, fields[5] as? ByteArray, fields[6] as? ByteArray,
                fields[7] as? Int, fields[8] as? Int)
        } finally { body.fill(0) }
    }

    private fun readVarint(input: ByteArrayInputStream): Long {
        var value = 0L
        for (shift in 0..63 step 7) {
            val b = input.read()
            require(b >= 0 && (shift != 63 || b <= 1)) { "Invalid protobuf varint" }
            value = value or ((b and 127).toLong() shl shift)
            if (b and 128 == 0) return value
        }
        throw IllegalArgumentException("Invalid protobuf varint")
    }

    private fun varint(out: ByteArrayOutputStream, value: Long) {
        require(value >= 0)
        var rest = value
        while (rest >= 128) { out.write(((rest and 127) or 128).toInt()); rest = rest ushr 7 }
        out.write(rest.toInt())
    }
}
