package org.aionlink.wifi

/**
 * Allowlisted view of GetUCarConfigResponse (UCarProto field numbers in MiAuto and Samsung). The car's Bluetooth
 * address (field 1) and custom field (16) are skipped on purpose; only display, codec and SDK metadata leave here.
 */
object CarConfigSummary {
    private val numeric = mapOf(2 to "pantallaAncho", 3 to "pantallaAlto", 4 to "dpi", 5 to "vídeoAncho", 6 to "vídeoAlto",
        7 to "fps", 13 to "mic", 14 to "bajaLatencia", 18 to "workModes", 23 to "hevc")
    private const val SDK_VERSION = 17
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH = 2
    private const val WIRE_FIXED32 = 5

    fun of(message: ByteArray): String {
        val values = linkedMapOf<String, String>()
        var offset = 0
        fun varint(): Long {
            var result = 0L; var shift = 0
            while (true) {
                require(offset < message.size && shift < 64)
                val byte = message[offset++].toInt()
                result = result or ((byte and 0x7F).toLong() shl shift)
                if (byte and 0x80 == 0) return result
                shift += 7
            }
        }
        try {
            while (offset < message.size) {
                val tag = varint()
                val field = (tag ushr 3).toInt()
                when ((tag and 7).toInt()) {
                    WIRE_VARINT -> { val value = varint(); numeric[field]?.let { values[it] = value.toString() } }
                    WIRE_LENGTH -> {
                        val size = varint()
                        require(size in 0..(message.size - offset).toLong())
                        if (field == SDK_VERSION) {
                            values["sdk"] = String(message, offset, size.toInt(), Charsets.US_ASCII).filter { it in ' '..'~' }.take(40)
                        }
                        offset += size.toInt()
                    }
                    WIRE_FIXED64 -> { require(message.size - offset >= 8); offset += 8 }
                    WIRE_FIXED32 -> { require(message.size - offset >= 4); offset += 4 }
                    else -> throw IllegalArgumentException("wire type")
                }
            }
        } catch (_: IllegalArgumentException) { values["truncado"] = "sí" }
        return if (values.isEmpty()) "respuesta sin campos conocidos" else values.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}
