package org.aionlink.projection

import org.junit.Assert.*
import org.junit.Test

/** Synthetic sink values written for these tests; not captures from a vehicle. */
class WfdFormatsTest {
    @Test fun prefers720p30AndConstrainedBaseline() {
        val mode = WfdFormats.chooseVideo("00 00 03 10 000101ff 00000000 00000000 00 0000 0000 00 none none")!!
        assertEquals(1280 to 720, mode.width to mode.height); assertEquals(30, mode.fps)
        assertEquals("28 00 01 10 00000020 00000000 00000000 00 0000 0000 00 none none", mode.m4Value())
    }
    @Test fun fallsBackToMandatoryVgaAndRejectsNone() {
        assertEquals(0, WfdFormats.chooseVideo("00 00 01 01 00000001 00000000 00000000 00 0000 0000 00 none none")!!.ceaIndex)
        assertNull(WfdFormats.chooseVideo("none")); assertNull(WfdFormats.chooseVideo(null))
    }
    @Test fun audioAndPorts() {
        assertEquals("LPCM 00000002 00", WfdFormats.chooseAudio("LPCM 00000003 00, AAC 00000001 00"))
        assertEquals("AAC 00000001 00", WfdFormats.chooseAudio("AAC 0000000f 00"))
        assertNull(WfdFormats.chooseAudio("none"))
        assertEquals(19000, WfdFormats.clientRtpPort("RTP/AVP/UDP;unicast 19000 0 mode=play"))
        assertNull(WfdFormats.clientRtpPort("RTP/AVP/TCP;interleaved 0 0 mode=play"))
        assertEquals("1920x720", WfdFormats.safeExcerpt("1920x720\u0000"))
    }
}
