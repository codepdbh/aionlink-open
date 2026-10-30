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
}
