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

    /** Value format as recorded in the owner's 0.28 diagnostic; written here as a test literal. */
    @Test fun parsesCarDisplayModeAndRejectsNonsense() {
        assertEquals(CarDisplayMode(0, 1920, 960, 160, 30), WfdFormats.parseCarDisplayMode("mode=0;display=1920:960;dpi=160;fps=30"))
        assertEquals(CarDisplayMode(0, 1280, 480, 160, 30), WfdFormats.parseCarDisplayMode("display=1280:480"))
        assertNull(WfdFormats.parseCarDisplayMode("synthetic"))
        assertNull(WfdFormats.parseCarDisplayMode("mode=0;display=99999:960;dpi=160;fps=30"))
        assertNull(WfdFormats.parseCarDisplayMode("mode=0;display=1920;dpi=160"))
        assertNull(WfdFormats.parseCarDisplayMode(null))
    }

    @Test fun highQualityEncodesAtCarScreenSizeWithinNegotiatedRate() {
        val cea = WfdVideoMode(5, 1280, 720, 30, 1, 1)
        val car = CarDisplayMode(0, 1920, 960, 160, 30)
        val high = WfdFormats.encodeTarget(cea, car, highQuality = true)
        assertEquals(EncodeTarget(1920, 960, 30, 11_059_200, "PANTALLA_DEL_COCHE"), high)
        assertEquals(EncodeTarget(1280, 720, 30, 5_529_600, "CEA"), WfdFormats.encodeTarget(cea, car, highQuality = false))
        assertEquals("CEA", WfdFormats.encodeTarget(cea, null, highQuality = true).source)
        // Odd sizes are rounded down to multiples of 16; a faster car screen never raises the negotiated rate.
        val odd = WfdFormats.encodeTarget(cea, CarDisplayMode(0, 1366, 770, 160, 60), highQuality = true)
        assertEquals(1360 to 768, odd.width to odd.height); assertEquals(30, odd.fps)
    }

    @Test fun bitrateScalesWithPixelsAndIsBounded() {
        assertEquals(4_000_000, WfdFormats.videoBitrate(640, 480, 30))
        assertEquals(16_000_000, WfdFormats.videoBitrate(3840, 2160, 60))
        assertTrue(WfdFormats.videoBitrate(1920, 960, 30) > 2 * WfdFormats.videoBitrate(1280, 720, 30) * 9 / 10)
    }
}
