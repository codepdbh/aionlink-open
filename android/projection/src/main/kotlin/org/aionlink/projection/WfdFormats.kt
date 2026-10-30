package org.aionlink.projection

/** Video mode chosen from the sink's wfd_video_formats (Wi-Fi Display CEA table). */
data class WfdVideoMode(val ceaIndex: Int, val width: Int, val height: Int, val fps: Int, val profile: Int, val level: Int) {
    /** M4 value: native = (index << 3) | CEA table 0; single-mode CEA bitmap; no VESA/HH modes. */
    fun m4Value(): String = "%02x 00 %02x %02x %08x 00000000 00000000 00 0000 0000 00 none none"
        .format((ceaIndex shl 3), profile, level, 1 shl ceaIndex)
}

object WfdFormats {
    /** Subset of the public Wi-Fi Display CEA resolution table that AionLink can encode. */
    private val cea = mapOf(0 to Triple(640, 480, 60), 5 to Triple(1280, 720, 30), 6 to Triple(1280, 720, 60),
        7 to Triple(1920, 1080, 30))
    private val preference = listOf(5, 6, 0)

    /** Returns null when the sink reports no parsable H.264 entry; CEA 640x480p60 is mandatory for sinks. */
    fun chooseVideo(sinkValue: String?): WfdVideoMode? {
        val value = sinkValue?.trim() ?: return null
        if (value.isEmpty() || value.equals("none", true)) return null
        val parts = value.split(Regex("\\s+"), limit = 3)
        if (parts.size < 3) return null
        val codecs = parts[2].split(",").map { it.trim().split(Regex("\\s+")) }.filter { it.size >= 5 }
        for (index in preference) for (codec in codecs) {
            val profile = codec[0].toIntOrNull(16) ?: continue
            val level = codec[1].toIntOrNull(16) ?: continue
            val ceaMask = codec[2].toLongOrNull(16) ?: continue
            if (ceaMask and (1L shl index) == 0L || profile == 0 || level == 0) continue
            val (w, h, fps) = cea.getValue(index)
            // Prefer Constrained Baseline (bit 0) because it is what Android encoders support most widely.
            val chosenProfile = if (profile and 1 != 0) 1 else Integer.lowestOneBit(profile)
            return WfdVideoMode(index, w, h, fps, chosenProfile, Integer.highestOneBit(level))
        }
        return null
    }

    /** Picks one audio codec the sink lists. Audio streaming itself is not implemented; the M4 value is declarative. */
    fun chooseAudio(sinkValue: String?): String? {
        val value = sinkValue?.trim() ?: return null
        if (value.isEmpty() || value.equals("none", true)) return null
        val entries = value.split(",").map { it.trim().split(Regex("\\s+")) }.filter { it.size >= 2 }
        entries.firstOrNull { it[0] == "LPCM" && (it[1].toLongOrNull(16) ?: 0) and 2L != 0L }?.let { return "LPCM 00000002 00" }
        entries.firstOrNull { it[0] == "AAC" && (it[1].toLongOrNull(16) ?: 0) and 1L != 0L }?.let { return "AAC 00000001 00" }
        return null
    }

    /** "RTP/AVP/UDP;unicast 19000 0 mode=play" → 19000. */
    fun clientRtpPort(sinkValue: String?): Int? {
        val parts = sinkValue?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size < 2 || !parts[0].startsWith("RTP/AVP/UDP")) return null
        return parts[1].toIntOrNull()?.takeIf { it in 1..65535 }
    }

    /** Printable, bounded copy for diagnostics; the value describes the car display, not the user. */
    fun safeExcerpt(value: String?): String = value?.filter { it in ' '..'~' }?.take(96) ?: "ausente"
}
