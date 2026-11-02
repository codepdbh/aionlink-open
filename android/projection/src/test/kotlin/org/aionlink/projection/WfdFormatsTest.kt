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
        assertEquals("AAC 00000001 00", WfdFormats.chooseAudio("LPCM 00000003 00, AAC 00000001 00"))
        assertEquals("LPCM 00000002 00", WfdFormats.chooseAudio("LPCM 00000003 00"))
        assertTrue(WfdFormats.sinkSupportsUibc("input_category_list=GENERIC;generic_cap_list=SingleTouch;port=none"))
        assertFalse(WfdFormats.sinkSupportsUibc("none")); assertFalse(WfdFormats.sinkSupportsUibc(null))
        assertEquals("AAC 00000001 00", WfdFormats.chooseAudio("AAC 0000000f 00"))
        assertNull(WfdFormats.chooseAudio("none"))
        assertEquals(WfdClientRtp(19000, false), WfdFormats.clientRtpPorts("RTP/AVP/UDP;unicast 19000 0 mode=play"))
        assertEquals(WfdClientRtp(19000, true), WfdFormats.clientRtpPorts("RTP/AVP/TCP;unicast 19000 0 mode=play"))
        assertNull(WfdFormats.clientRtpPorts("RTP/AVP/TCP;interleaved mode=play"))
        assertNull(WfdFormats.clientRtpPorts("RTP/AVP/TCP;unicast 70000 0 mode=play"))
        assertNull(WfdFormats.clientRtpPorts(null))
        assertEquals("1920x720", WfdFormats.safeExcerpt("1920x720\u0000"))
    }
}
