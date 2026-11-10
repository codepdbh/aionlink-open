package org.aionlink.projection

/** Video mode chosen from the sink's wfd_video_formats (Wi-Fi Display CEA table). */
data class WfdVideoMode(val ceaIndex: Int, val width: Int, val height: Int, val fps: Int, val profile: Int, val level: Int) {
    /** M4 value: native = (index << 3) | CEA table 0; single-mode CEA bitmap; no VESA/HH modes. */
    fun m4Value(): String = "%02x 00 %02x %02x %08x 00000000 00000000 00 0000 0000 00 none none"
        .format((ceaIndex shl 3), profile, level, 1 shl ceaIndex)
}

/** Port and transport the sink chose in wfd_client_rtp_ports. */
data class WfdClientRtp(val port: Int, val tcp: Boolean)

/** The car's own screen as reported in wfd_car_display_mode ("mode=0;display=1920:960;dpi=160;fps=30"). */
data class CarDisplayMode(val mode: Int, val width: Int, val height: Int, val dpi: Int, val fps: Int)

/** Size, rate and bitrate the phone actually encodes; may differ from the CEA mode declared in M4. */
data class EncodeTarget(val width: Int, val height: Int, val fps: Int, val bitrate: Int, val source: String)

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

    const val AAC_48K_STEREO = "AAC 00000001 00"
    const val LPCM_48K_STEREO = "LPCM 00000002 00"

    /** Samsung's library prefers AAC ("Using AAC audio."); AionLink streams AAC-LC 48 kHz stereo. LPCM is declared only. */
    fun chooseAudio(sinkValue: String?): String? {
        val value = sinkValue?.trim() ?: return null
        if (value.isEmpty() || value.equals("none", true)) return null
        val entries = value.split(",").map { it.trim().split(Regex("\\s+")) }.filter { it.size >= 2 }
        entries.firstOrNull { it[0] == "AAC" && (it[1].toLongOrNull(16) ?: 0) and 1L != 0L }?.let { return AAC_48K_STEREO }
        entries.firstOrNull { it[0] == "LPCM" && (it[1].toLongOrNull(16) ?: 0) and 2L != 0L }?.let { return LPCM_48K_STEREO }
        return null
    }

    /** Exact capability line found in Samsung's display library; sent in M4 when the sink reports UIBC. */
    const val UIBC_CAPABILITY = "input_category_list=GENERIC,HIDC;generic_cap_list=SingleTouch, MultiTouch;" +
        "hidc_cap_list=Keyboard/USB, Keyboard/BT;port=4321"

    fun sinkSupportsUibc(sinkValue: String?): Boolean =
        sinkValue?.trim()?.let { it.isNotEmpty() && !it.equals("none", true) } ?: false

    /**
     * "RTP/AVP/UDP;unicast 19000 0 mode=play" or "RTP/AVP/TCP;unicast 19000 0 mode=play" → port and transport.
     * The ucarhusdk sink in MiAuto always selects TCP unicast. Interleaved is not implemented and returns null.
     */
    fun clientRtpPorts(sinkValue: String?): WfdClientRtp? {
        val parts = sinkValue?.trim()?.split(Regex("\\s+")) ?: return null
        if (parts.size < 2) return null
        val tcp = when (parts[0]) { "RTP/AVP/UDP;unicast" -> false; "RTP/AVP/TCP;unicast" -> true; else -> return null }
        return parts[1].toIntOrNull()?.takeIf { it in 1..65535 }?.let { WfdClientRtp(it, tcp) }
    }

    /**
     * Parses the format Samsung's display library reads with "mode=%d;display=%d:%d;dpi=%d;fps=%d" before logging
     * "car display mode = %d,w=%d,h=%d,dpi=%d,fps=%d". Fields may appear in any order; out-of-range values → null.
     */
    fun parseCarDisplayMode(sinkValue: String?): CarDisplayMode? {
        val fields = sinkValue?.split(';')?.mapNotNull { part ->
            val index = part.indexOf('='); if (index <= 0) null else part.substring(0, index).trim() to part.substring(index + 1).trim()
        }?.toMap() ?: return null
        val size = fields["display"]?.split(':') ?: return null
        val width = size.getOrNull(0)?.toIntOrNull() ?: return null
        val height = size.getOrNull(1)?.toIntOrNull() ?: return null
        if (width !in 320..3840 || height !in 240..2160) return null
        val fps = fields["fps"]?.toIntOrNull()?.takeIf { it in 10..60 } ?: 30
        return CarDisplayMode(fields["mode"]?.toIntOrNull() ?: 0, width, height, fields["dpi"]?.toIntOrNull() ?: 160, fps)
    }

    /**
     * High quality encodes at the car's native screen size, as Samsung's library does after reading
     * wfd_car_display_mode ("change video_bitrate to %d as 1080P"); otherwise the negotiated CEA mode is kept.
     * Sizes are rounded down to multiples of 16 for hardware encoders. The rate never exceeds the negotiated one,
     * because the receiver's decoder queue grows when frames arrive faster than it renders (see LAG_SAVINGS_026).
     */
    fun encodeTarget(mode: WfdVideoMode, car: CarDisplayMode?, highQuality: Boolean): EncodeTarget {
        if (!highQuality || car == null) return EncodeTarget(mode.width, mode.height, mode.fps, videoBitrate(mode.width, mode.height, mode.fps), "CEA")
        val width = car.width / 16 * 16
        val height = car.height / 16 * 16
        val fps = minOf(car.fps, mode.fps)
        return EncodeTarget(width, height, fps, videoBitrate(width, height, fps), "PANTALLA_DEL_COCHE")
    }

    /**
     * About 0.2 bits per pixel per frame for screen content in Constrained Baseline, bounded to 4–16 Mbps:
     * 1280x720p30 → 5.5 Mbps, 1920x960p30 → 11 Mbps. Text and thin UI lines need far more than camera video.
     */
    fun videoBitrate(width: Int, height: Int, fps: Int): Int =
        (width.toLong() * height * fps / 5).coerceIn(4_000_000L, 16_000_000L).toInt()

    /**
     * Car mode content (Android Auto through OpenHeadunit Lite, or AionLink's panel): mostly static interface that
     * arrives already compressed (Android Auto sent about 0.8 Mbps in the 2026-11-10 measurement). About 0.07 bits per
     * pixel per frame, bounded to 2.5–6 Mbps: 1920x960p30 → 3.9 Mbps, a third of [videoBitrate], so the Wi-Fi radio
     * and the encoder work less. Mirror mode keeps [videoBitrate] because it can carry video.
     */
    fun relayBitrate(width: Int, height: Int, fps: Int): Int =
        (width.toLong() * height * fps / 14).coerceIn(2_500_000L, 6_000_000L).toInt()

    /** Printable, bounded copy for diagnostics; the value describes the car display, not the user. */
    fun safeExcerpt(value: String?): String = value?.filter { it in ' '..'~' }?.take(160) ?: "ausente"
}
