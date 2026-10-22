package org.aionlink.iccoa

import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Synthetic format examples, not receiver replies or interoperability fixtures. */
class UsbMuxTest {
    private fun header(channel: Int, size: Int) = ByteBuffer.allocate(8).putInt(channel).putInt(size).array()
    @Test fun startupMatchesDocumentedByteLayout() {
        val (h,b) = UsbChannelStartup.request(7236)
        assertArrayEquals(byteArrayOf(0,0,0,1,0,0,0,10),h)
        assertArrayEquals(byteArrayOf(0,0,28,68,-1,-1,-1,-1,1,2),b)
    }
    @Test fun fragmentedControlAndConcatenatedFrames() {
        val (h,b) = UsbChannelStartup.request(4321)
        val bytes = h + b + header(2,3) + byteArrayOf(10,20,30,0,0,0,0,0)
        val parser = UsbMuxDecoder()
        val packets = bytes.flatMap { parser.accept(byteArrayOf(it)) }
        assertEquals(2,packets.size)
        assertEquals(4321,packets[0].control?.knownPort)
        assertEquals(-1,packets[0].control?.channelId)
        assertEquals(3,packets[1].declaredBytes)
        assertFalse(parser.hasPartialFrame)
    }
    @Test fun badChannelAndOversizedLengthRejected() {
        for (bytes in listOf(header(0,10),header(241,10),header(1,-1),header(1,1025))) {
            try { UsbMuxDecoder(1024).accept(bytes); fail("invalid allocation accepted") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun paddingAndTruncationAreNotConfused() {
        val parser = UsbMuxDecoder()
        assertTrue(parser.accept(header(2,0)+ByteArray(7)).isEmpty())
        assertTrue(parser.hasPartialFrame)
        assertEquals(0,parser.accept(byteArrayOf(0)).single().declaredBytes)
    }
    @Test fun unknownPortAndPayloadAreNotExported() {
        val body = ByteBuffer.allocate(10).putInt(60000).putInt(4).put(1).put(2).array()
        val result = UsbMuxDecoder().accept(header(1,10)+body).single()
        assertNull(result.control?.knownPort)
        assertFalse(result.toString().contains("60000"))
    }
    @Test fun unknownAccessoriesAndPortsDoNotTriggerObservedBootstrap() {
        assertFalse(UsbAccessoryIdentity.isObservedIccoa("ICCOA","CarLink","2.0"))
        assertFalse(UsbAccessoryIdentity.isObservedIccoa("Carbit","CarbitDriverModel","1.0"))
        assertTrue(UsbAccessoryIdentity.isObservedIccoa("ICCOA","CarLink","1.0.0"))
        try { UsbChannelStartup.request(9999); fail("unobserved request") } catch (_: IllegalArgumentException) { }
    }
}
