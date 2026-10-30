package org.aionlink.projection

import java.io.ByteArrayOutputStream

/**
 * Minimal MPEG-2 TS (ISO/IEC 13818-1) muxer for one H.264 stream and an optional AAC (ADTS) stream, carried as
 * RTP payload type 33 (RFC 2250) with up to seven TS packets per datagram, as used by Wi-Fi Display sources.
 * PIDs follow the Wi-Fi Display convention: PMT 0x100, video/PCR 0x1011, audio 0x1100.
 * Thread-safe: video and audio producers may call from different threads.
 */
class TsRtpStreamer(
    private val sendDatagram: (ByteArray) -> Unit,
    ssrc: Int = java.security.SecureRandom().nextInt(),
    private val audio: Boolean = false
) {
    companion object {
        const val TS_SIZE = 188
        const val PMT_PID = 0x100
        const val VIDEO_PID = 0x1011
        const val AUDIO_PID = 0x1100
        const val TS_PER_RTP = 7
        private const val PTS_DELAY_90K = 9000L // 100 ms between PCR and PTS for the sink's buffer.

        fun crc32Mpeg(data: ByteArray, from: Int, to: Int): Int {
            var crc = -1
            for (i in from until to) {
                crc = crc xor ((data[i].toInt() and 255) shl 24)
                repeat(8) { crc = if (crc and Int.MIN_VALUE != 0) (crc shl 1) xor 0x04C11DB7 else crc shl 1 }
            }
            return crc
        }

        /** 7-byte ADTS header (no CRC) for AAC-LC; sampling index 3 = 48 kHz. */
        fun adtsHeader(payloadBytes: Int, samplingIndex: Int = 3, channels: Int = 2): ByteArray {
            val length = payloadBytes + 7
            require(length < 8192)
            return byteArrayOf(0xFF.toByte(), 0xF1.toByte(),
                ((1 shl 6) or (samplingIndex shl 2) or (channels ushr 2)).toByte(),
                (((channels and 3) shl 6) or (length ushr 11)).toByte(),
                ((length ushr 3) and 0xFF).toByte(), (((length and 7) shl 5) or 0x1F).toByte(), 0xFC.toByte())
        }
    }

    private val continuity = IntArray(0x2000)
    private val ssrcValue = ssrc
    private var rtpSequence = java.security.SecureRandom().nextInt(65536)
    private val pendingTs = ByteArrayOutputStream()
    private var pendingCount = 0
    private var lastRtpTimestamp = 0L
    private var frames = 0L
    var datagramsSent = 0L; private set
    var accessUnits = 0L; private set
    var audioFrames = 0L; private set

    /** [annexB] holds a complete access unit with start codes; SPS/PPS must precede keyframes. */
    @Synchronized fun onAccessUnit(annexB: ByteArray, ptsUs: Long, keyframe: Boolean) {
        lastRtpTimestamp = ptsUs * 90 / 1000
        if (keyframe || frames % 30 == 0L) { addTs(section(0, pat())); addTs(section(PMT_PID, pmt())) }
        frames++
        writePes(VIDEO_PID, 0xE0, annexB, ptsUs * 90 / 1000 + PTS_DELAY_90K, ptsUs * 90 / 1000, keyframe, bounded = false)
        flush()
        accessUnits++
    }

    /** [adtsFrame] is one AAC frame including its ADTS header; ignored when the stream was created without audio. */
    @Synchronized fun onAudioFrame(adtsFrame: ByteArray, ptsUs: Long) {
        if (!audio || frames == 0L) return // PSI is emitted with the first video frame.
        writePes(AUDIO_PID, 0xC0, adtsFrame, ptsUs * 90 / 1000 + PTS_DELAY_90K, null, keyframe = false, bounded = true)
        flush()
        audioFrames++
    }

    private fun pat(): ByteArray = psi(0x00, 0x0001, byteArrayOf(0x00, 0x01, (0xE0 or (PMT_PID ushr 8)).toByte(), PMT_PID.toByte()))

    private fun pmt(): ByteArray {
        val streams = ByteArrayOutputStream()
        streams.write(byteArrayOf((0xE0 or (VIDEO_PID ushr 8)).toByte(), VIDEO_PID.toByte(), 0xF0.toByte(), 0x00)) // PCR PID
        streams.write(byteArrayOf(0x1B, (0xE0 or (VIDEO_PID ushr 8)).toByte(), VIDEO_PID.toByte(), 0xF0.toByte(), 0x00)) // H.264
        if (audio) streams.write(byteArrayOf(0x0F, (0xE0 or (AUDIO_PID ushr 8)).toByte(), AUDIO_PID.toByte(), 0xF0.toByte(), 0x00)) // AAC ADTS
        return psi(0x02, 0x0001, streams.toByteArray())
    }

    /** Long-form PSI section with CRC; [id] is transport_stream_id or program_number. */
    private fun psi(tableId: Int, id: Int, payload: ByteArray): ByteArray {
        val length = 5 + payload.size + 4
        val out = ByteArray(3 + length)
        out[0] = tableId.toByte(); out[1] = (0xB0 or (length ushr 8)).toByte(); out[2] = length.toByte()
        out[3] = (id ushr 8).toByte(); out[4] = id.toByte(); out[5] = 0xC1.toByte(); out[6] = 0; out[7] = 0
        System.arraycopy(payload, 0, out, 8, payload.size)
        val crc = crc32Mpeg(out, 0, out.size - 4)
        for (i in 0..3) out[out.size - 4 + i] = (crc ushr (24 - 8 * i)).toByte()
        return out
    }

    private fun section(pid: Int, table: ByteArray): ByteArray {
        val packet = ByteArray(TS_SIZE) { 0xFF.toByte() }
        header(packet, pid, start = true, adaptation = false)
        packet[4] = 0 // pointer_field
        System.arraycopy(table, 0, packet, 5, table.size)
        return packet
    }

    private fun header(packet: ByteArray, pid: Int, start: Boolean, adaptation: Boolean) {
        packet[0] = 0x47
        packet[1] = ((if (start) 0x40 else 0) or (pid ushr 8)).toByte()
        packet[2] = pid.toByte()
        packet[3] = ((if (adaptation) 0x30 else 0x10) or continuity[pid]).toByte()
        continuity[pid] = (continuity[pid] + 1) and 0x0F
    }

    private fun writePes(pid: Int, streamId: Int, es: ByteArray, pts90: Long, pcr90: Long?, keyframe: Boolean, bounded: Boolean) {
        val pes = ByteArrayOutputStream(es.size + 14)
        // PES_packet_length may be 0 (unbounded) only for video; audio PES carry their real length.
        val length = if (bounded) 8 + es.size else 0
        require(length <= 0xFFFF)
        pes.write(byteArrayOf(0, 0, 1, streamId.toByte(), (length ushr 8).toByte(), length.toByte(), 0x80.toByte(), 0x80.toByte(), 5))
        pes.write(timestamp(0x2, pts90))
        pes.write(es)
        val data = pes.toByteArray()
        var offset = 0
        var first = true
        while (offset < data.size) {
            val packet = ByteArray(TS_SIZE)
            val adaptation = ByteArrayOutputStream()
            if (first && pcr90 != null) {
                // adaptation flags: random_access (keyframes) + PCR; PCR base in 90 kHz, extension 0.
                adaptation.write(if (keyframe) 0x50 else 0x10)
                val base = pcr90 and 0x1FFFFFFFFL
                adaptation.write(byteArrayOf((base ushr 25).toByte(), (base ushr 17).toByte(), (base ushr 9).toByte(),
                    (base ushr 1).toByte(), (((base and 1) shl 7) or 0x7E).toInt().toByte(), 0))
            }
            val remaining = data.size - offset
            var fieldBytes = if (adaptation.size() > 0) 1 + adaptation.size() else 0
            val room = TS_SIZE - 4 - fieldBytes
            if (remaining < room) {
                // Pad with adaptation-field stuffing so the payload ends exactly at packet end.
                val stuffing = room - remaining
                if (fieldBytes == 0) {
                    fieldBytes = stuffing
                    if (stuffing >= 2) { adaptation.write(0x00); repeat(stuffing - 2) { adaptation.write(0xFF) } }
                } else { repeat(stuffing) { adaptation.write(0xFF) }; fieldBytes += stuffing }
            }
            header(packet, pid, first, fieldBytes > 0)
            var position = 4
            if (fieldBytes > 0) {
                packet[position++] = (fieldBytes - 1).toByte()
                val bytes = adaptation.toByteArray()
                System.arraycopy(bytes, 0, packet, position, bytes.size); position += bytes.size
            }
            val count = TS_SIZE - position
            System.arraycopy(data, offset, packet, position, count)
            offset += count
            first = false
            addTs(packet)
        }
    }

    private fun timestamp(prefix: Int, value: Long): ByteArray {
        val v = value and 0x1FFFFFFFFL
        return byteArrayOf(((prefix shl 4) or (((v ushr 30) and 7).toInt() shl 1) or 1).toByte(),
            (v ushr 22).toByte(), ((((v ushr 15) and 0x7F).toInt() shl 1) or 1).toByte(),
            (v ushr 7).toByte(), (((v and 0x7F).toInt() shl 1) or 1).toByte())
    }

    private fun addTs(packet: ByteArray) {
        pendingTs.write(packet); pendingCount++
        if (pendingCount == TS_PER_RTP) flush()
    }

    private fun flush() {
        if (pendingCount == 0) return
        val payload = pendingTs.toByteArray()
        val rtp = ByteArray(12 + payload.size)
        rtp[0] = 0x80.toByte(); rtp[1] = 33
        rtp[2] = (rtpSequence ushr 8).toByte(); rtp[3] = rtpSequence.toByte()
        val ts = lastRtpTimestamp
        for (i in 0..3) rtp[4 + i] = (ts ushr (24 - 8 * i)).toByte()
        for (i in 0..3) rtp[8 + i] = (ssrcValue ushr (24 - 8 * i)).toByte()
        System.arraycopy(payload, 0, rtp, 12, payload.size)
        rtpSequence = (rtpSequence + 1) and 0xFFFF
        pendingTs.reset(); pendingCount = 0
        sendDatagram(rtp)
        datagramsSent++
    }
}
