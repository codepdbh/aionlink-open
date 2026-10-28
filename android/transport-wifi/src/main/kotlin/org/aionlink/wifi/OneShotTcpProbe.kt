package org.aionlink.wifi

import java.io.IOException
import java.net.ConnectException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.NoRouteToHostException
import java.net.Socket
import java.net.SocketTimeoutException

enum class TcpProbeResult { OPEN, CONNECT_FAILED, TIMEOUT, NO_ROUTE, BIND_FAILED, IO_FAILURE }

/** Bounded single TCP connection; never reads or writes application bytes. */
object OneShotTcpProbe {
    fun check(socket: Socket, target: InetAddress, port: Int, timeoutMs: Int, bind: (Socket) -> Unit): TcpProbeResult {
        require(port in 1..65535 && timeoutMs in 1..5000)
        try {
            try { bind(socket) } catch (_: IOException) { return TcpProbeResult.BIND_FAILED }
            catch (_: SecurityException) { return TcpProbeResult.BIND_FAILED }
            catch (_: IllegalStateException) { return TcpProbeResult.BIND_FAILED }
            try {
                socket.connect(InetSocketAddress(target, port), timeoutMs)
                return TcpProbeResult.OPEN
            } catch (_: SocketTimeoutException) { return TcpProbeResult.TIMEOUT }
            catch (_: NoRouteToHostException) { return TcpProbeResult.NO_ROUTE }
            catch (_: ConnectException) { return TcpProbeResult.CONNECT_FAILED }
            catch (_: IOException) { return TcpProbeResult.IO_FAILURE }
            catch (_: SecurityException) { return TcpProbeResult.IO_FAILURE }
        } finally { try { socket.close() } catch (_: IOException) { } }
    }
}
