package org.aionlink.projection

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.io.IOException

/**
 * One pre-authentication version request, informed by aasdk ControlServiceChannel.
 * A version response is not authentication and does not activate projection.
 */
object AndroidAutoVersionProbe {
    enum class Status { RESPONSE, INVALID_FRAME, TOO_LARGE, CLOSED, TIMEOUT, IO_ERROR }
    data class Outcome(val status: Status, val major: Int? = null, val minor: Int? = null, val result: Int? = null, val phase: String? = null)
    private const val MAX_PAYLOAD = 256
    private const val PORT = 5277

    /** Only the same device, a single attempt and a short timeout; caller must run off main thread. */
    fun local(): Outcome {
        var phase = "CONNECTING"
        return try {
            Socket().use { socket ->
                socket.connect(InetSocketAddress(InetAddress.getByName("127.0.0.1"), PORT), 2500)
                socket.soTimeout = 2500
                phase = "CONNECTED"
                exchange(socket.getInputStream(), socket.getOutputStream()) { phase = "REQUEST_SENT" }.copy(phase = "RESPONSE")
            }
        } catch (_: SocketTimeoutException) { Outcome(Status.TIMEOUT, phase = phase) }
          catch (_: EOFException) { Outcome(Status.CLOSED, phase = phase) }
          catch (_: IOException) { Outcome(Status.IO_ERROR, phase = phase) }
    }

    /** Exposed for synthetic in-memory and loopback tests. No credential or TLS exchange occurs. */
    fun exchange(input: InputStream, output: OutputStream, onSent: () -> Unit = {}): Outcome {
        // Control channel, plain specific bulk; message ID 1 and observed request version 1.1.
        output.write(byteArrayOf(0, 3, 0, 6, 0, 1, 0, 1, 0, 1))
        output.flush()
        onSent()
        val data = DataInputStream(input)
        val channel = data.readUnsignedByte()
        val flags = data.readUnsignedByte()
        val length = data.readUnsignedShort()
        if (length > MAX_PAYLOAD) return Outcome(Status.TOO_LARGE)
        if (channel != 0 || flags != 3 || length != 8) return Outcome(Status.INVALID_FRAME)
        if (data.readUnsignedShort() != 2) return Outcome(Status.INVALID_FRAME)
        return Outcome(Status.RESPONSE, data.readUnsignedShort(), data.readUnsignedShort(), data.readUnsignedShort())
    }
}
