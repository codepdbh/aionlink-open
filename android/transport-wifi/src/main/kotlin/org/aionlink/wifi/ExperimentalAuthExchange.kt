package org.aionlink.wifi

import org.aionlink.iccoa.ExperimentalAuthWire
import org.aionlink.iccoa.ExperimentalNormalAuth
import org.aionlink.iccoa.UCarFrameReader
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** One bounded, user-authorized AUTH request. No session confirmation or media. */
object ExperimentalAuthExchange {
    fun run(socket: Socket, host: InetAddress, port: Int, pin: ByteArray, clientId: ByteArray,
            model: String, bind: (Socket) -> Unit): String {
        var frame: ByteArray? = null
        var reply: ByteArray? = null
        try {
            ExperimentalNormalAuth(pin, clientId, model, 0).use { attempt ->
                val request = attempt.request()
                val sequence = java.security.SecureRandom().nextInt().toLong() and 0xffffffffL
                try { frame = ExperimentalAuthWire.requestFrame(request, sequence, System.currentTimeMillis() / 1000) }
                finally { request.proof.fill(0); request.nonce.fill(0); request.clientId.fill(0) }
                try { bind(socket) } catch (_: IOException) { return "BIND_FAILED" }
                catch (_: SecurityException) { return "BIND_FAILED" }
                socket.connect(InetSocketAddress(host, port), 2500)
                socket.soTimeout = 4000
                socket.getOutputStream().write(frame!!)
                socket.getOutputStream().flush()
                val input = DataInputStream(socket.getInputStream())
                val header = ByteArray(20)
                input.readFully(header)
                val parsed = UCarFrameReader.header(header, 20 + ExperimentalAuthWire.MAX_BODY)
                reply = ByteArray(parsed.totalBytes)
                System.arraycopy(header, 0, reply!!, 0, 20)
                input.readFully(reply!!, 20, parsed.totalBytes - 20)
                val response = ExperimentalAuthWire.responseFrame(reply!!, sequence)
                val outcome = when (val code = response.result ?: 0) { in 0..4 -> code.toString(); else -> "OTHER" }
                val version = if (response.version == 1793) "EXPECTED" else "OTHER_OR_MISSING"
                return "RESPONSE_${attempt.verify(response).name}; resultado=$outcome; versión=$version"
            }
        } catch (_: SocketTimeoutException) { return "TIMEOUT" }
        catch (_: EOFException) { return "TRUNCATED_OR_CLOSED" }
        catch (_: IOException) { return "IO_FAILURE" }
        catch (_: IllegalArgumentException) { return "INVALID_FRAME_OR_INPUT" }
        catch (_: Exception) { return "LOCAL_ERROR" }
        finally {
            frame?.fill(0); reply?.fill(0); pin.fill(0); clientId.fill(0)
            try { socket.close() } catch (_: IOException) { }
        }
    }
}
