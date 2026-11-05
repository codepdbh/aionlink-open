package org.aionlink.projection

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

class AndroidAutoVersionProbeTest {
    @Test fun sendsKnownRequestAndParsesResponseAcrossShortReads() {
        val bytes = byteArrayOf(0, 3, 0, 8, 0, 2, 0, 1, 0, 7, 0, 0)
        val response = object : ByteArrayInputStream(bytes) {
            override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, minOf(len, 1))
        }
        val sent = ByteArrayOutputStream()
        val outcome = AndroidAutoVersionProbe.exchange(response, sent)
        assertEquals(AndroidAutoVersionProbe.Outcome(AndroidAutoVersionProbe.Status.RESPONSE, 1, 7, 0), outcome)
        assertArrayEquals(byteArrayOf(0, 3, 0, 6, 0, 1, 0, 1, 0, 1), sent.toByteArray())
    }
    @Test fun rejectsUnexpectedAndOversizedHeaders() {
        fun exchange(vararg bytes: Int) = AndroidAutoVersionProbe.exchange(ByteArrayInputStream(bytes.map(Int::toByte).toByteArray()), ByteArrayOutputStream())
        assertEquals(AndroidAutoVersionProbe.Status.TOO_LARGE, exchange(0, 3, 2, 0).status)
        assertEquals(AndroidAutoVersionProbe.Status.INVALID_FRAME, exchange(1, 3, 0, 8).status)
        assertEquals(AndroidAutoVersionProbe.Status.INVALID_FRAME, exchange(0, 7, 0, 8).status)
        assertThrows(EOFException::class.java) { exchange(0, 3, 0) }
    }
}
