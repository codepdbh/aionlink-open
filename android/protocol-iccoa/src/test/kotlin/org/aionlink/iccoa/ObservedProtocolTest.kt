package org.aionlink.iccoa

import java.io.ByteArrayInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import org.junit.Assert.*
import org.junit.Test

/** Synthetic specification examples only; no receiver captures or interoperability claims. */
class ObservedProtocolTest {
    private fun syntheticHeader(length: Int = 20, flags: Int = 11, category: Int = 3, method: Int = 1): ByteArray {
        val data = ByteBuffer.allocate(20).putInt(length).putInt(-1).putInt(1234)
            .put(flags.toByte()).put(category.toByte()).putShort(method.toShort()).putShort(0).array()
        val crc = UCarFrameReader.crc16Arc(data, 18)
        data[18] = (crc ushr 8).toByte(); data[19] = crc.toByte()
        return data
    }
    @Test fun standardCrcVector() {
        assertEquals(0xbb3d, UCarFrameReader.crc16Arc("123456789".toByteArray(Charsets.US_ASCII)))
    }
    @Test fun unsignedFieldsAndAuthRequest() {
        val header = UCarFrameReader.header(syntheticHeader())
        assertEquals(4294967295L, header.sequence)
        assertEquals(1234L, header.timestampSeconds)
        assertEquals(1, header.source)
        assertEquals("AUTH_REQUEST_STATIC_CANDIDATE", header.authMessageLabel)
    }
    @Test fun corruptedHeaderIsRejected() {
        val bytes = syntheticHeader(); bytes[5] = 1
        try { UCarFrameReader.header(bytes); fail("CRC accepted corruption") } catch (_: IllegalArgumentException) { }
    }
    @Test fun invalidLengthsAreRejectedBeforePayloadAllocation() {
        for (length in listOf(0, 19, -1, Int.MAX_VALUE)) {
            try { UCarFrameReader.header(syntheticHeader(length)); fail("Unbounded length") } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun unknownValuesArePreservedWithoutAuthClaim() {
        val header = UCarFrameReader.header(syntheticHeader(flags = 255, category = 250, method = 65535))
        assertEquals(250, header.category); assertEquals(65535, header.method)
        assertEquals(224, header.unknownFlagBits); assertNull(header.authMessageLabel)
    }
    @Test fun unknownFlagsDoNotBecomeAuthenticationEvidence() {
        assertNull(UCarFrameReader.header(syntheticHeader(flags = 11 or 32)).authMessageLabel)
    }
    @Test fun readsFragmentedAndConcatenatedFrames() {
        val source = ByteArrayInputStream(syntheticHeader(23) + byteArrayOf(1, 2, 3) + syntheticHeader())
        val fragmented = object : InputStream() {
            override fun read() = source.read()
            override fun read(b: ByteArray, off: Int, len: Int) = source.read(b, off, minOf(1, len))
        }
        assertArrayEquals(byteArrayOf(1,2,3), UCarFrameReader.read(fragmented)!!.bodyCopy())
        assertEquals(20, UCarFrameReader.read(fragmented)!!.header.totalBytes)
        assertNull(UCarFrameReader.read(fragmented))
    }
    @Test fun truncatedHeaderAndBodyFail() {
        for (bytes in listOf(syntheticHeader().copyOf(7), syntheticHeader(25) + byteArrayOf(0))) {
            try { UCarFrameReader.read(ByteArrayInputStream(bytes)); fail("Accepted truncation") } catch (_: EOFException) { }
        }
    }
    @Test fun onlyObservedUuidsBecomeHints() {
        assertEquals(setOf(AdvertisementEvidence.ADVERTISED_SERVICE), AdvertisementEvidence.recognizedServices(
            listOf(AdvertisementEvidence.ADVERTISED_SERVICE.uppercase(), "private-other-service")))
        assertTrue(AdvertisementEvidence.recognizedServices(listOf("AION", "CarLink")).isEmpty())
    }
}
