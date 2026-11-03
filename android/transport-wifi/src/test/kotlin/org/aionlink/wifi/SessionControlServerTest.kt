package org.aionlink.wifi

import com.google.protobuf.CodedInputStream
import java.io.DataInputStream
import java.net.InetAddress
import java.net.Socket
import org.aionlink.iccoa.ExperimentalAuthCrypto
import org.aionlink.iccoa.ObservedUCarHeader
import org.aionlink.iccoa.UCarFrameReader
import org.aionlink.iccoa.UCarFrameWriter
import org.junit.Assert.*
import org.junit.Test

/** Synthetic car client on loopback; frames and key are generated for this test only. */
class SessionControlServerTest {
    @Test fun sendsMacInfoAndHeartbeatAndAcknowledgesSendSync() {
        val key = ByteArray(16) { (it * 3).toByte() }
        val testKey = key.copyOf()
        val stages = java.util.Collections.synchronizedList(mutableListOf<String>())
        SessionControlServer(InetAddress.getLoopbackAddress(), key, port = 0) { stages += it.stage }.use { server ->
            server.start()
            assertTrue(key.all { it == 0.toByte() })
            Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { car ->
                car.soTimeout = 3000
                val input = DataInputStream(car.getInputStream())
                fun next(): Pair<ObservedUCarHeader, ByteArray> {
                    val header = ByteArray(20); input.readFully(header)
                    val parsed = UCarFrameReader.header(header)
                    return parsed to ByteArray(parsed.totalBytes - 20).also { input.readFully(it) }
                }
                val frames = listOf(next(), next())
                val mac = frames.first { it.first.method == 22 }
                assertEquals(UCarFrameWriter.TYPE_SEND_SYNC, mac.first.messageType); assertEquals(1, mac.first.category)
                assertEquals(0, mac.second.size)
                val heartbeat = frames.first { it.first.method == 1 }
                assertEquals(UCarFrameWriter.TYPE_SEND, heartbeat.first.messageType); assertEquals(1, heartbeat.first.source)
                val plain = ExperimentalAuthCrypto.sessionOpen(testKey, heartbeat.second)
                assertEquals(0x08, plain[0].toInt())
                val sealed = ExperimentalAuthCrypto.sessionSeal(testKey, UCarFrameWriter.varintField(1, 42))
                // Car-sourced PB3 SEND_SYNC heartbeat: flags = source 0 | PB3 << 1 | SEND_SYNC << 3.
                car.getOutputStream().write(UCarFrameWriter.frame(UCarFrameWriter.flags(0, 1, 3), 1, 1, sealed, 77, 0))
                var ack: ObservedUCarHeader? = null
                repeat(4) { if (ack == null) next().first.takeIf { it.category == UCarFrameWriter.CATEGORY_ACK }?.let { ack = it } }
                assertEquals(77L, ack!!.sequence); assertEquals(UCarFrameWriter.TYPE_RES, ack!!.messageType); assertEquals(20, ack!!.totalBytes)
            }
        }
        assertTrue(stages.containsAll(listOf("LISTENING", "CONNECTED")))
    }

    @Test fun requestsConfigOnceAndReportsCarFrames() {
        val key = ByteArray(16) { 7 }
        val testKey = key.copyOf()
        val carFrames = java.util.concurrent.LinkedBlockingQueue<CarFrame>()
        val configs = java.util.concurrent.LinkedBlockingQueue<String>()
        SessionControlServer(InetAddress.getLoopbackAddress(), key, port = 0, onFrame = { carFrames += it }) {
            if (it.carConfig != "sin respuesta") configs += it.carConfig
        }.use { server ->
            server.start()
            Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { car ->
                car.soTimeout = 3000
                val input = DataInputStream(car.getInputStream())
                val methods = mutableListOf<ObservedUCarHeader>()
                repeat(3) {
                    val header = ByteArray(20); input.readFully(header)
                    val parsed = UCarFrameReader.header(header)
                    input.readFully(ByteArray(parsed.totalBytes - 20))
                    methods += parsed
                }
                val request = methods.single { it.method == SessionControlServer.METHOD_GET_CONFIG_REQUEST }
                assertEquals(UCarFrameWriter.TYPE_REQ, request.messageType); assertEquals(20, request.totalBytes)
                val response = byteArrayOf(0x0A, 2, 0x11, 0x22) + UCarFrameWriter.varintField(5, 1920) +
                    byteArrayOf(0x8A.toByte(), 0x01, 2, 'v'.code.toByte(), '1'.code.toByte())
                car.getOutputStream().write(UCarFrameWriter.frame(UCarFrameWriter.flags(0, 1, UCarFrameWriter.TYPE_RES), 1,
                    SessionControlServer.METHOD_GET_CONFIG_RESPONSE, ExperimentalAuthCrypto.sessionSeal(testKey, response), request.sequence, 0))
                val summary = configs.poll(3, java.util.concurrent.TimeUnit.SECONDS)
                assertEquals("vídeoAncho=1920; sdk=v1", summary)
                car.getOutputStream().write(UCarFrameWriter.frame(UCarFrameWriter.flags(0, 1, UCarFrameWriter.TYPE_SEND), 1,
                    SessionControlServer.METHOD_DISCONNECT, ByteArray(0), 90, 0))
                var last: CarFrame? = null
                repeat(2) { last = carFrames.poll(3, java.util.concurrent.TimeUnit.SECONDS) }
                assertEquals(CarFrame(1, SessionControlServer.METHOD_DISCONNECT, UCarFrameWriter.TYPE_SEND), last)
            }
        }
        assertEquals("disconnect", SessionControlServer.methodName(23))
        assertEquals("desconocido", SessionControlServer.methodName(99))
    }

    @Test fun answersFusionAppRequestsUnderTheRequestSequence() {
        val key = ByteArray(16) { 9 }
        val testKey = key.copyOf()
        SessionControlServer(InetAddress.getLoopbackAddress(), key, port = 0) { }.use { server ->
            server.start()
            Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { car ->
                car.soTimeout = 3000
                val input = DataInputStream(car.getInputStream())
                fun until(method: Int): Pair<ObservedUCarHeader, ByteArray> {
                    while (true) {
                        val header = ByteArray(20); input.readFully(header)
                        val parsed = UCarFrameReader.header(header)
                        val body = ByteArray(parsed.totalBytes - 20).also { input.readFully(it) }
                        if (parsed.method == method) return parsed to ExperimentalAuthCrypto.sessionOpen(testKey, body)
                    }
                }
                fun carSend(type: Int, method: Int, body: ByteArray, seq: Long) = car.getOutputStream().write(UCarFrameWriter.frame(
                    UCarFrameWriter.flags(0, 1, type), 1, method, body, seq, 0))
                carSend(UCarFrameWriter.TYPE_REQ, SessionControlServer.METHOD_GET_APP_LIST_REQUEST, ByteArray(0), 40)
                val list = until(SessionControlServer.METHOD_GET_APP_LIST_RESPONSE)
                assertEquals(40L, list.first.sequence); assertEquals(UCarFrameWriter.TYPE_RES, list.first.messageType)
                assertEquals(1, list.first.source); assertArrayEquals(CarAppMessages.appList(), list.second)
                carSend(UCarFrameWriter.TYPE_REQ, SessionControlServer.METHOD_GET_APP_DETAIL_REQUEST, ByteArray(0), 41)
                val detail = until(SessionControlServer.METHOD_GET_APP_DETAIL_RESPONSE)
                assertEquals(41L, detail.first.sequence); assertArrayEquals(CarAppMessages.appDetail(), detail.second)
                // InvokeApp{id=0, START, displayMode=0} is all defaults, so its body is empty.
                carSend(UCarFrameWriter.TYPE_SEND_SYNC, SessionControlServer.METHOD_INVOKE_APP, ByteArray(0), 42)
                val state = until(SessionControlServer.METHOD_APP_STATE_CHANGED)
                assertEquals(UCarFrameWriter.TYPE_SEND, state.first.messageType); assertNotEquals(42L, state.first.sequence)
                val fields = CodedInputStream.newInstance(state.second)
                assertEquals(16, fields.readTag()); assertTrue(fields.readBool()) // isNeedShow; id, display and STARTED are 0.
                assertEquals(40, fields.readTag()); assertTrue(fields.readInt64() > 0)
            }
        }
    }

    @Test fun appMessagesMatchTheFieldsTheCarValidates() {
        val list = CodedInputStream.newInstance(CarAppMessages.appList())
        assertEquals(8, list.readTag()); assertEquals(1, list.readInt32()) // total must equal the entries sent.
        assertEquals(18, list.readTag())
        val app = CodedInputStream.newInstance(list.readByteArray())
        assertEquals(18, app.readTag()); assertEquals("AionLink", app.readString())
        assertEquals(24, app.readTag()); assertEquals(1, app.readInt32())
        assertTrue(list.isAtEnd)
        val detail = CodedInputStream.newInstance(CarAppMessages.appDetail())
        assertEquals(8, detail.readTag()); assertEquals(2, detail.readInt32())
        assertEquals(18, detail.readTag()); val first = detail.readByteArray()
        assertEquals(18, detail.readTag()); val product = CodedInputStream.newInstance(detail.readByteArray())
        assertTrue(detail.isAtEnd)
        assertTrue(first.isNotEmpty())
        assertEquals(8, product.readTag()); assertEquals(CarAppMessages.PRODUCT_ID, product.readInt32())
        val icon = java.util.Base64.getDecoder().decode(CarAppMessages.ICON_PNG_BASE64)
        assertArrayEquals(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte()), icon.copyOf(4))
    }

    @Test fun appStateFollowsTheInvocationAndIgnoresBadInput() {
        fun fields(body: ByteArray): Map<Int, Long> {
            val out = mutableMapOf<Int, Long>()
            ProtoScan.walk(body, onVarint = { field, value -> out[field] = value })
            return out
        }
        // id=7, RESUME, displayMode=1.
        val resume = UCarFrameWriter.varintField(1, 7) + UCarFrameWriter.varintField(3, 3) + UCarFrameWriter.varintField(4, 1)
        assertEquals(mapOf(1 to 7L, 2 to 1L, 3 to 1L, 4 to 3L, 5 to 100L), fields(CarAppMessages.appState(resume, 100)!!))
        // STOP without display mode: the car sends -1 and must not show anything.
        val stop = UCarFrameWriter.varintField(3, 1) + UCarFrameWriter.varintField(4, -1)
        assertEquals(mapOf(3 to -1L, 4 to 1L, 5 to 100L), fields(CarAppMessages.appState(stop, 100)!!))
        // UPDATE only changes the display mode of a visible app.
        assertEquals(3L, fields(CarAppMessages.appState(UCarFrameWriter.varintField(3, 4), 100)!!)[4])
        assertNull(CarAppMessages.appState(UCarFrameWriter.varintField(3, 9), 100))
        assertNull(CarAppMessages.appState(byteArrayOf(0x12, 0x7F), 100))
    }

    @Test fun passiveChannelDoesNotAnswerAppRequests() {
        val key = ByteArray(16) { 6 }
        SessionControlServer(InetAddress.getLoopbackAddress(), key, port = 0, active = false) { }.use { server ->
            server.start()
            Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { car ->
                car.soTimeout = 500
                car.getOutputStream().write(UCarFrameWriter.frame(UCarFrameWriter.flags(0, 1, UCarFrameWriter.TYPE_REQ), 1,
                    SessionControlServer.METHOD_GET_APP_LIST_REQUEST, ByteArray(0), 3, 0))
                assertThrows(java.net.SocketTimeoutException::class.java) { car.getInputStream().read() }
            }
        }
    }

    @Test fun truncatedConfigIsMarkedAndNeverThrows() {
        assertEquals("vídeoAlto=960; truncado=sí", CarConfigSummary.of(UCarFrameWriter.varintField(6, 960) + byteArrayOf(0x12, 0x7F)))
        assertEquals("respuesta sin campos conocidos", CarConfigSummary.of(byteArrayOf()))
    }

    @Test fun passiveChannelOnlyAcknowledges() {
        val key = ByteArray(16) { 5 }
        val testKey = key.copyOf()
        SessionControlServer(InetAddress.getLoopbackAddress(), key, port = 0, active = false) { }.use { server ->
            server.start()
            Socket(InetAddress.getLoopbackAddress(), server.boundPort).use { car ->
                car.soTimeout = 3000
                val sealed = ExperimentalAuthCrypto.sessionSeal(testKey, byteArrayOf(8, 1))
                car.getOutputStream().write(UCarFrameWriter.frame(UCarFrameWriter.flags(0, 1, 3), 2, 1, sealed, 9, 0))
                val header = ByteArray(20); DataInputStream(car.getInputStream()).readFully(header)
                val first = UCarFrameReader.header(header)
                // No BluetoothMacInfo or heartbeat precedes the ACK on a passive channel.
                assertEquals(UCarFrameWriter.CATEGORY_ACK, first.category); assertEquals(9L, first.sequence)
            }
        }
    }
}
