package org.aionlink.wifi

/**
 * Allowlisted view of GetUCarConfigResponse (UCarProto field numbers in MiAuto and Samsung). The car's Bluetooth
 * address (field 1) and custom field (16) are skipped on purpose; only display, codec and SDK metadata leave here.
 */
object CarConfigSummary {
    private val numeric = mapOf(2 to "pantallaAncho", 3 to "pantallaAlto", 4 to "dpi", 5 to "vídeoAncho", 6 to "vídeoAlto",
        7 to "fps", 13 to "mic", 14 to "bajaLatencia", 18 to "workModes", 23 to "hevc")
    private const val SDK_VERSION = 17

    fun of(message: ByteArray): String {
        val values = linkedMapOf<String, String>()
        try {
            ProtoScan.walk(message,
                onVarint = { field, value -> numeric[field]?.let { values[it] = value.toString() } },
                onBytes = { field, offset, size ->
                    if (field == SDK_VERSION) {
                        values["sdk"] = String(message, offset, size, Charsets.US_ASCII).filter { it in ' '..'~' }.take(40)
                    }
                })
        } catch (_: IllegalArgumentException) { values["truncado"] = "sí" }
        return if (values.isEmpty()) "respuesta sin campos conocidos" else values.entries.joinToString("; ") { "${it.key}=${it.value}" }
    }
}

/** Bounded protobuf field walker; throws IllegalArgumentException on malformed input. */
internal object ProtoScan {
    private const val WIRE_VARINT = 0
    private const val WIRE_FIXED64 = 1
    private const val WIRE_LENGTH = 2
    private const val WIRE_FIXED32 = 5

    fun walk(message: ByteArray, onVarint: (Int, Long) -> Unit = { _, _ -> }, onBytes: (Int, Int, Int) -> Unit = { _, _, _ -> }) {
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
        while (offset < message.size) {
            val tag = varint()
            val field = (tag ushr 3).toInt()
            when ((tag and 7).toInt()) {
                WIRE_VARINT -> onVarint(field, varint())
                WIRE_LENGTH -> {
                    val size = varint()
                    require(size in 0..(message.size - offset).toLong())
                    onBytes(field, offset, size.toInt())
                    offset += size.toInt()
                }
                WIRE_FIXED64 -> { require(message.size - offset >= 8); offset += 8 }
                WIRE_FIXED32 -> { require(message.size - offset >= 4); offset += 4 }
                else -> throw IllegalArgumentException("wire type")
            }
        }
    }
}
