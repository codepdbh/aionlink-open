package org.aionlink.iccoa

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

/** Synthetic fixtures only. These tests do not prove compatibility with an AION. */
class ExperimentalAuthWireTest {
    private fun request() = ExperimentalAuthWire.Request(1793, ByteArray(64) { 1 }, ByteArray(32) { 2 },
        ByteArray(64) { 3 }, ByteArray(8) { 4 }, ByteArray(32) { 5 }, "client".toByteArray(),
        "model", true, 1, "own-app")

    @Test fun requestUsesObservedTagsAndSupportsFragmentedOfflineRead() {
        val frame = ExperimentalAuthWire.requestFrame(request(), 42, 100)
        val source = ByteArrayInputStream(frame)
        val fragmented = object : java.io.InputStream() {
            override fun read() = source.read()
            override fun read(data: ByteArray, offset: Int, length: Int) = source.read(data, offset, minOf(length, 3))
        }
        val decoded = UCarFrameReader.read(fragmented)!!
        assertEquals("AUTH_REQUEST_STATIC_CANDIDATE", decoded.header.authMessageLabel)
        assertEquals(42L, decoded.header.sequence)
        val body = decoded.bodyCopy()
        assertArrayEquals(byteArrayOf(8, 0x81.toByte(), 0x0e, 0x12, 0x40), body.copyOfRange(0, 5))
        assertTrue(body.toList().windowed(2).any { it == listOf(0x48.toByte(), 1.toByte()) })
        assertFalse(request().toString().contains("client"))
    }

    @Test fun requestBodyCanBeReadByIndependentProtobufLibrary() {
        val input = com.google.protobuf.CodedInputStream.newInstance(ExperimentalAuthWire.requestBody(request()))
        assertEquals(8, input.readTag()); assertEquals(1793, input.readInt32())
        assertEquals(18, input.readTag()); assertArrayEquals(ByteArray(64) { 1 }, input.readByteArray())
        assertEquals(26, input.readTag()); assertArrayEquals(ByteArray(32) { 2 }, input.readByteArray())
        assertEquals(34, input.readTag()); assertArrayEquals(ByteArray(64) { 3 }, input.readByteArray())
        assertEquals(42, input.readTag()); assertArrayEquals(ByteArray(8) { 4 }, input.readByteArray())
        assertEquals(50, input.readTag()); assertArrayEquals(ByteArray(32) { 5 }, input.readByteArray())
        assertEquals(58, input.readTag()); assertArrayEquals("client".toByteArray(), input.readByteArray())
        assertEquals(66, input.readTag()); assertEquals("model", input.readStringRequireUtf8())
        assertEquals(72, input.readTag()); assertTrue(input.readBool())
        assertEquals(80, input.readTag()); assertEquals(1, input.readInt32())
        assertEquals(90, input.readTag()); assertEquals("own-app", input.readStringRequireUtf8())
        assertEquals(0, input.readTag())
    }

    @Test fun rejectsUnconfirmedOrOversizedInputBeforeEncoding() {
        val r = request()
        val bad = ExperimentalAuthWire.Request(r.version, r.authPoint, r.proof, r.agreementPoint,
            r.agreementSignature, r.nonce, r.clientId, r.model, false, r.workModes, r.productName)
        try { ExperimentalAuthWire.requestBody(bad); fail("no consent") } catch (_: IllegalArgumentException) { }
        try { ExperimentalAuthWire.requestFrame(r, -1, 0); fail("invalid sequence") } catch (_: IllegalArgumentException) { }
    }

    private fun syntheticResponse(sequence: Int, body: ByteArray): ByteArray {
        val result = java.nio.ByteBuffer.allocate(20 + body.size).putInt(20 + body.size).putInt(sequence).putInt(100)
            .put(18).put(3).putShort(2).putShort(0).putShort(0).put(body).array()
        val crc = UCarFrameReader.crc16Arc(result, 18)
        result[18] = (crc ushr 8).toByte(); result[19] = crc.toByte()
        return result
    }

    @Test fun responseCorrelationAndLengthChecksRejectUnexpectedFrames() {
        // Independently assembled synthetic response, not a vehicle capture.
        val body = byteArrayOf(8, 0x81.toByte(), 0x0e, 0x12, 2, 7, 8, 0x38, 0, 0x40, 1)
        val frame = syntheticResponse(42, body)
        val response = ExperimentalAuthWire.responseFrame(frame, 42)
        assertEquals(1793, response.version)
        assertArrayEquals(byteArrayOf(7, 8), response.authPoint)
        assertEquals(0, response.result)
        assertFalse(response.toString().contains("1793"))
        for (bad in listOf(frame.copyOf(frame.size - 1), syntheticResponse(43, body),
            syntheticResponse(42, body + byteArrayOf(8, 1)), syntheticResponse(42, byteArrayOf(0x12, 127)),
            syntheticResponse(42, byteArrayOf(0x10, 1)),
            syntheticResponse(42, byteArrayOf(0x38) + ByteArray(9) { 0xff.toByte() } + byteArrayOf(1)))) {
            try { ExperimentalAuthWire.responseFrame(bad, 42); fail("invalid response") }
            catch (_: Exception) { }
        }
    }

    @Test fun syntheticLoopbackReceiverCanReadRequestAndReturnCorrelatedResponse() {
        java.net.ServerSocket(0, 1, java.net.InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 3000
            val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<Boolean> {
                    server.accept().use { socket ->
                        socket.soTimeout = 3000
                        val request = UCarFrameReader.read(socket.getInputStream()) ?: return@submit false
                        if (request.header.authMessageLabel != "AUTH_REQUEST_STATIC_CANDIDATE") return@submit false
                        val reply = syntheticResponse(request.header.sequence.toInt(), byteArrayOf(8, 0x81.toByte(), 0x0e, 0x38, 0))
                        socket.getOutputStream().write(reply)
                        true
                    }
                }
                java.net.Socket(java.net.InetAddress.getLoopbackAddress(), server.localPort).use { client ->
                    client.soTimeout = 3000
                    client.getOutputStream().write(ExperimentalAuthWire.requestFrame(request(), 42, 100))
                    val input = client.getInputStream()
                    val header = input.readNBytes(20)
                    val length = java.nio.ByteBuffer.wrap(header).int
                    val frame = header + input.readNBytes(length - 20)
                    assertEquals(0, ExperimentalAuthWire.responseFrame(frame, 42).result)
                }
                assertTrue(received.get(4, java.util.concurrent.TimeUnit.SECONDS))
            } finally { executor.shutdownNow() }
        }
    }
}
