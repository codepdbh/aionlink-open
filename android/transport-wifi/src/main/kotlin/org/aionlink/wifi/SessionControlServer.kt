package org.aionlink.wifi

import org.aionlink.iccoa.ExperimentalAuthCrypto
import org.aionlink.iccoa.UCarFrameReader
import org.aionlink.iccoa.UCarFrameWriter
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

data class ControlChannelStatus(
    val stage: String = "IDLE", val received: Int = 0, val heartbeatsReceived: Int = 0,
    val heartbeatsSent: Int = 0, val acksSent: Int = 0, val lastMethods: List<Int> = emptyList(),
    val decryptFailures: Int = 0
)

/**
 * Phone-side CONTROL listener observed in Samsung (r5/f.java: SocketChannel(CONTROL, server=true, encrypted=true),
 * bound to the phone's group address on port 57219). Headers are clear; bodies use the AUTH session envelope.
 * Sends the observed 2 s heartbeat (method 1) and an empty BluetoothMacInfo (method 22, SEND_SYNC) on connect,
 * and acknowledges SEND_SYNC frames. Payloads are never exported.
 */
class SessionControlServer(
    private val local: InetAddress, sessionKey: ByteArray, private val port: Int = PORT,
    private val onStatus: (ControlChannelStatus) -> Unit
) : AutoCloseable {
    companion object { const val PORT = 57219 }
    private val key = sessionKey.copyOf().also { sessionKey.fill(0) }
    private val sequence = AtomicLong(1)
    private val received = AtomicInteger(); private val heartbeatsIn = AtomicInteger()
    private val heartbeatsOut = AtomicInteger(); private val acks = AtomicInteger(); private val failures = AtomicInteger()
    private val methods = java.util.Collections.synchronizedList(mutableListOf<Int>())
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var closed = false
    @Volatile private var stage = "IDLE"

    private fun publish(next: String? = null) {
        if (next != null) stage = next
        onStatus(ControlChannelStatus(stage, received.get(), heartbeatsIn.get(), heartbeatsOut.get(), acks.get(),
            synchronized(methods) { methods.takeLast(8) }, failures.get()))
    }

    /** Binds synchronously so the listener exists before AuthConfirm is sent. */
    fun start() {
        val socket = ServerSocket()
        socket.reuseAddress = true
        socket.bind(InetSocketAddress(local, port), 1)
        server = socket
        publish("LISTENING")
        Thread({ acceptLoop(socket) }, "AionLink-control").apply { isDaemon = true; start() }
    }

    val boundPort: Int get() = server?.localPort ?: -1

    private fun acceptLoop(listener: ServerSocket) {
        try {
            listener.soTimeout = 30000
            val peer = listener.accept()
            client = peer
            peer.tcpNoDelay = true; peer.keepAlive = true
            publish("CONNECTED")
            val output = peer.getOutputStream()
            // Samsung r5/f.onConnect: BluetoothMacInfo{op=ADD(0), mac=""} when no adapter address is available.
            send(output, UCarFrameWriter.flags(1, UCarFrameWriter.FORMAT_PB3, UCarFrameWriter.TYPE_SEND_SYNC), 22, ByteArray(0))
            Thread({ heartbeatLoop(output) }, "AionLink-control-heartbeat").apply { isDaemon = true; start() }
            readLoop(DataInputStream(peer.getInputStream()), output)
        } catch (_: java.net.SocketTimeoutException) { if (!closed) publish("NO_CONNECTION_30S") }
        catch (_: EOFException) { if (!closed) publish("CLOSED_BY_RECEIVER") }
        catch (_: IOException) { if (!closed) publish("IO_FAILURE") }
        catch (_: IllegalArgumentException) { if (!closed) publish("INVALID_FRAME") }
        finally { close() }
    }

    private fun readLoop(input: DataInputStream, output: OutputStream) {
        val header = ByteArray(UCarFrameReader.HEADER_BYTES)
        while (!closed) {
            input.readFully(header)
            val parsed = UCarFrameReader.header(header, 1 shl 20)
            val body = ByteArray(parsed.totalBytes - UCarFrameReader.HEADER_BYTES)
            input.readFully(body)
            received.incrementAndGet()
            synchronized(methods) { methods += parsed.category * 1000 + parsed.method }
            if (body.isNotEmpty()) {
                try { ExperimentalAuthCrypto.sessionOpen(key, body).fill(0) }
                catch (_: Exception) { failures.incrementAndGet() }
                body.fill(0)
            }
            if (parsed.category == UCarFrameWriter.CATEGORY_CONTROL && parsed.method == 1) heartbeatsIn.incrementAndGet()
            if (parsed.messageType == UCarFrameWriter.TYPE_SEND_SYNC) {
                // SocketChannel.onMessage → UCarMessage.obtainAckMessage(seq): RAW, RES, category ACK, method 0.
                val ack = UCarFrameWriter.frame(UCarFrameWriter.flags(1, UCarFrameWriter.FORMAT_RAW, UCarFrameWriter.TYPE_RES),
                    UCarFrameWriter.CATEGORY_ACK, 0, ByteArray(0), parsed.sequence, System.currentTimeMillis() / 1000)
                synchronized(output) { output.write(ack); output.flush() }
                acks.incrementAndGet()
            }
            publish()
        }
    }

    private fun heartbeatLoop(output: OutputStream) {
        try {
            while (!closed) {
                // Heartbeat{timestamp(1) = epoch milliseconds}, CONTROL method 1, PB3 SEND.
                send(output, UCarFrameWriter.flags(1, UCarFrameWriter.FORMAT_PB3, UCarFrameWriter.TYPE_SEND), 1,
                    UCarFrameWriter.varintField(1, System.currentTimeMillis()))
                heartbeatsOut.incrementAndGet(); publish()
                Thread.sleep(2000)
            }
        } catch (_: IOException) { } catch (_: InterruptedException) { }
    }

    private fun send(output: OutputStream, flags: Int, method: Int, plain: ByteArray) {
        val body = if (plain.isEmpty()) plain else ExperimentalAuthCrypto.sessionSeal(key, plain)
        val seq = sequence.getAndIncrement().let { if (it > 0x7fffffffL) { sequence.set(2); 1L } else it }
        val frame = UCarFrameWriter.frame(flags, UCarFrameWriter.CATEGORY_CONTROL, method, body, seq, System.currentTimeMillis() / 1000)
        synchronized(output) { output.write(frame); output.flush() }
        plain.fill(0)
    }

    override fun close() {
        if (closed) return
        closed = true
        try { client?.close() } catch (_: IOException) { }
        try { server?.close() } catch (_: IOException) { }
        key.fill(0)
        if (stage in setOf("LISTENING", "CONNECTED")) publish("CLOSED")
    }
}
