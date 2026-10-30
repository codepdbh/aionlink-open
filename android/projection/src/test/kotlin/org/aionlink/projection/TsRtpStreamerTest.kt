package org.aionlink.projection

import org.junit.Assert.*
import org.junit.Test

class TsRtpStreamerTest {
    private fun pid(ts: ByteArray) = ((ts[1].toInt() and 0x1F) shl 8) or (ts[2].toInt() and 255)

    @Test fun keyframeProducesValidRtpTsWithPsiAndPes() {
        val datagrams = mutableListOf<ByteArray>()
        val streamer = TsRtpStreamer({ datagrams += it }, ssrc = 0x01020304)
        val au = byteArrayOf(0, 0, 0, 1, 0x67, 1, 2, 3, 0, 0, 0, 1, 0x68, 4, 0, 0, 0, 1, 0x65) + ByteArray(1000) { 7 }
        streamer.onAccessUnit(au, 1_000_000, true)
        assertTrue(datagrams.isNotEmpty())
        val packets = mutableListOf<ByteArray>()
        var lastSeq = -1
        for (d in datagrams) {
            assertEquals(0x80, d[0].toInt() and 255); assertEquals(33, d[1].toInt())
            val seq = ((d[2].toInt() and 255) shl 8) or (d[3].toInt() and 255)
            if (lastSeq >= 0) assertEquals((lastSeq + 1) and 0xFFFF, seq)
            lastSeq = seq
            assertEquals(0, (d.size - 12) % 188); assertTrue((d.size - 12) / 188 in 1..7)
            for (o in 12 until d.size step 188) { assertEquals(0x47, d[o].toInt()); packets += d.copyOfRange(o, o + 188) }
        }
        assertEquals(0, pid(packets[0])); assertEquals(TsRtpStreamer.PMT_PID, pid(packets[1]))
        for (psi in packets.take(2)) {
            val length = ((psi[6].toInt() and 0x0F) shl 8) or (psi[7].toInt() and 255)
            assertEquals(0, TsRtpStreamer.crc32Mpeg(psi, 5, 8 + length)) // CRC over a section including its CRC is zero.
        }
        val video = packets.drop(2)
        assertTrue(video.all { pid(it) == TsRtpStreamer.VIDEO_PID })
        assertEquals(0x40, video[0][1].toInt() and 0x40)
        val afLength = video[0][4].toInt() and 255
        assertEquals(0x50, video[0][5].toInt() and 0x50) // random access + PCR
        val pes = 5 + afLength
        assertArrayEquals(byteArrayOf(0, 0, 1, 0xE0.toByte()), video[0].copyOfRange(pes, pes + 4))
        video.forEachIndexed { i, p -> assertEquals(i and 0x0F, p[3].toInt() and 0x0F) }
        // Reassemble the TS payload and confirm the elementary stream survives intact after the 14-byte PES header.
        val payload = java.io.ByteArrayOutputStream()
        video.forEach { p ->
            val start = if (p[3].toInt() and 0x20 != 0) 5 + (p[4].toInt() and 255) else 4
            payload.write(p, start, 188 - start)
        }
        val bytes = payload.toByteArray()
        assertArrayEquals(au, bytes.copyOfRange(14, bytes.size))
        assertEquals(1, streamer.accessUnits)
    }

    @Test fun audioTrackIsDeclaredAndCarriedInBoundedPes() {
        val datagrams = mutableListOf<ByteArray>()
        val streamer = TsRtpStreamer({ datagrams += it }, audio = true)
        val aac = ByteArray(300) { 9 }
        val frame = TsRtpStreamer.adtsHeader(aac.size) + aac
        streamer.onAudioFrame(frame, 0) // ignored until PSI has been sent with video
        assertEquals(0, streamer.audioFrames)
        streamer.onAccessUnit(byteArrayOf(0, 0, 0, 1, 0x65, 1), 0, true)
        datagrams.clear()
        streamer.onAudioFrame(frame, 20_000)
        val packets = datagrams.flatMap { d -> (12 until d.size step 188).map { d.copyOfRange(it, it + 188) } }
        assertTrue(packets.all { pid(it) == TsRtpStreamer.AUDIO_PID })
        val start = if (packets[0][3].toInt() and 0x20 != 0) 5 + (packets[0][4].toInt() and 255) else 4
        assertArrayEquals(byteArrayOf(0, 0, 1, 0xC0.toByte()), packets[0].copyOfRange(start, start + 4))
        val pesLength = ((packets[0][start + 4].toInt() and 255) shl 8) or (packets[0][start + 5].toInt() and 255)
        assertEquals(8 + frame.size, pesLength)
        assertEquals(1, streamer.audioFrames)
    }

    @Test fun adtsHeaderEncodesAacLc48kStereo() {
        val header = TsRtpStreamer.adtsHeader(100)
        assertEquals(0xFFF1, ((header[0].toInt() and 255) shl 8) or (header[1].toInt() and 255))
        assertEquals(1, (header[2].toInt() and 0xC0) ushr 6)       // AAC LC
        assertEquals(3, (header[2].toInt() and 0x3C) ushr 2)       // 48 kHz
        val channels = ((header[2].toInt() and 1) shl 2) or ((header[3].toInt() and 0xC0) ushr 6)
        assertEquals(2, channels)
        val length = ((header[3].toInt() and 3) shl 11) or ((header[4].toInt() and 255) shl 3) or ((header[5].toInt() and 0xE0) ushr 5)
        assertEquals(107, length)
    }
}
