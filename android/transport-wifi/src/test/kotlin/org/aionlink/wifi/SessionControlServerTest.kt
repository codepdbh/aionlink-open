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
}
