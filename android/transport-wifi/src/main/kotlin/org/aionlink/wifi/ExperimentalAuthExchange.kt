package org.aionlink.wifi

import org.aionlink.iccoa.ExperimentalAuthWire
import org.aionlink.iccoa.ExperimentalNormalAuth
import org.aionlink.iccoa.PeerCheck
import org.aionlink.iccoa.UCarFrameReader
import java.io.DataInputStream
import java.io.EOFException
import java.io.IOException
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException

/** One bounded, user-authorized AUTH request and, after a verified response, the observed AuthConfirm.
 * No media; the receiver's acceptance of the confirmation is only observed, never assumed. */
object ExperimentalAuthExchange {
    fun run(socket: Socket, host: InetAddress, port: Int, pin: ByteArray, clientId: ByteArray,
            model: String, onAuthenticated: (Inet4Address, ByteArray) -> Unit = { _, key -> key.fill(0) },
            bind: (Socket) -> Unit): String {
        var frame: ByteArray? = null
        var reply: ByteArray? = null
        var confirm: ByteArray? = null
        try {
            ExperimentalNormalAuth(pin, clientId, model, ExperimentalAuthWire.OBSERVED_CLIENT_WORK_MODES).use { attempt ->
                val request = attempt.request()
                val sequence = ExperimentalAuthWire.FIRST_SEQUENCE
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
                val modes = response.workModes?.let { "0x" + Integer.toHexString(it) } ?: "AUSENTE"
                val check = attempt.verify(response)
                val summary = "RESPONSE_${check.name}; resultado=$outcome; versión=$version; workModes=$modes"
                if (check != PeerCheck.VERIFIED_PROOF_AND_SIGNATURE) return summary
                val local = socket.localAddress as? Inet4Address ?: return "$summary; CONFIRM_NOT_SENT_NO_IPV4"
                // Samsung starts its phone-side CONTROL/RTSP listeners right after confirmation; ours listen first.
                onAuthenticated(local, attempt.sessionKeyCopy())
                val cipher = attempt.confirmCipher(local.hostAddress ?: return "$summary; CONFIRM_NOT_SENT_NO_IPV4")
                try { confirm = ExperimentalAuthWire.confirmFrame(cipher, sequence + 1, System.currentTimeMillis() / 1000) }
                finally { cipher.fill(0) }
                socket.getOutputStream().write(confirm!!)
                socket.getOutputStream().flush()
                return "$summary; CONFIRM_SENT; tras confirmación=${observeAfterConfirm(socket, input)}"
            }
        } catch (_: SocketTimeoutException) { return "TIMEOUT" }
        catch (_: EOFException) { return "TRUNCATED_OR_CLOSED" }
        catch (_: IOException) { return "IO_FAILURE" }
        catch (_: IllegalArgumentException) { return "INVALID_FRAME_OR_INPUT" }
        catch (_: Exception) { return "LOCAL_ERROR" }
        finally {
            frame?.fill(0); reply?.fill(0); confirm?.fill(0); pin.fill(0); clientId.fill(0)
            try { socket.close() } catch (_: IOException) { }
        }
    }

    /** Header-only observation; payload is discarded and never exported. */
    private fun observeAfterConfirm(socket: Socket, input: DataInputStream): String = try {
        socket.soTimeout = 1500
        val header = ByteArray(20)
        input.readFully(header)
        val parsed = UCarFrameReader.header(header, 20 + ExperimentalAuthWire.MAX_BODY)
        "FRAME categoría=${parsed.category} método=${parsed.method} tipo=${parsed.messageType} origen=${parsed.source}"
    } catch (_: SocketTimeoutException) { "SIN_DATOS_1500MS" }
    catch (_: EOFException) { "CERRADO_POR_RECEPTOR" }
    catch (_: IOException) { "IO_FAILURE" }
    catch (_: IllegalArgumentException) { "TRAMA_NO_RECONOCIDA" }
}
