package org.aionlink.projection

import org.junit.Assert.*
import org.junit.Test

class ExperimentalEncodedBridgeTest {
    private val frame = byteArrayOf(0, 0, 0, 1, 0x65, 1, 2, 3) // synthetic Annex-B fixture
    private val audio = TsRtpStreamer.adtsHeader(2) + byteArrayOf(0, 0) // not decoded audio

    @Test fun syntheticSourceProducesRtpAndCannotLeakAcrossSessions() {
        val sent = mutableListOf<ByteArray>()
        val bridge = ExperimentalEncodedBridge { withAudio -> TsRtpStreamer({ sent.add(it) }, ssrc = 1, audio = withAudio) }
        val first = bridge.start(audio = true)
        assertEquals(ExperimentalEncodedBridge.Result.NEED_KEYFRAME, bridge.video(first, frame, 0, false))
        assertEquals(ExperimentalEncodedBridge.Result.NEED_KEYFRAME, bridge.audio(first, audio, 0))
        assertEquals(ExperimentalEncodedBridge.Result.ACCEPTED, bridge.video(first, frame, 0, true))
        assertEquals(ExperimentalEncodedBridge.Result.ACCEPTED, bridge.audio(first, audio, 0))
        assertTrue(sent.isNotEmpty())
        assertTrue(sent.all { (it[0].toInt() and 0xc0) == 0x80 && (it[1].toInt() and 0x7f) == 33 })
        assertTrue(sent.flatMap { rtp -> (12 until rtp.size step 188).map { rtp[it].toInt() and 0xff } }.all { it == 0x47 })
        val pids = sent.flatMap { rtp -> (12 until rtp.size step 188).map { pos ->
            ((rtp[pos + 1].toInt() and 0x1f) shl 8) or (rtp[pos + 2].toInt() and 0xff)
        } }
        assertTrue(pids.contains(TsRtpStreamer.VIDEO_PID))
        assertTrue(pids.contains(TsRtpStreamer.AUDIO_PID))
        assertEquals(ExperimentalEncodedBridge.Result.BAD_TIMESTAMP, bridge.video(first, frame, 0, true))
        val second = bridge.start(audio = false)
        val previousPackets = sent.size
        assertEquals(ExperimentalEncodedBridge.Result.STALE, bridge.video(first, frame, 1, true))
        bridge.stop(first)
        assertEquals(previousPackets, sent.size)
        assertEquals(ExperimentalEncodedBridge.Result.AUDIO_DISABLED, bridge.audio(second, audio, 1))
        assertEquals(ExperimentalEncodedBridge.Result.ACCEPTED, bridge.video(second, frame, 1, true))
        bridge.stop(second)
        assertEquals(ExperimentalEncodedBridge.Result.STALE, bridge.video(second, frame, 2, true))
        bridge.close()
    }

    @Test fun invalidOrForeignFramesAreRejected() {
        val packets = mutableListOf<ByteArray>()
        val bridge = ExperimentalEncodedBridge { withAudio -> TsRtpStreamer({ packets.add(it) }, audio = withAudio) }
        val foreign = ExperimentalEncodedBridge { withAudio -> TsRtpStreamer({}, audio = withAudio) }
        val token = bridge.start(audio = false)
        val other = foreign.start(audio = false)
        assertEquals(ExperimentalEncodedBridge.Result.STALE, bridge.video(other, frame, 1, true))
        assertEquals(ExperimentalEncodedBridge.Result.INVALID_SIZE, bridge.video(token, byteArrayOf(), 1, true))
        assertEquals(ExperimentalEncodedBridge.Result.BAD_TIMESTAMP, bridge.video(token, frame, -1, true))
        assertTrue(packets.isEmpty())
        bridge.close(); foreign.close()
    }
}
