package org.aionlink.wifi

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
