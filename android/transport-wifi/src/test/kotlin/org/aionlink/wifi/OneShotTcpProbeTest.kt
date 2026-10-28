package org.aionlink.wifi

import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Test

class OneShotTcpProbeTest {
    @Test fun opensOnlyOneLoopbackConnectionAndSendsNoPayload() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 3000
            val executor = Executors.newSingleThreadExecutor()
            try {
                val received = executor.submit<Int> {
                    server.accept().use { socket -> socket.soTimeout = 3000; socket.getInputStream().read() }
                }
                var bound = false
                val result = OneShotTcpProbe.check(Socket(), InetAddress.getLoopbackAddress(), server.localPort, 2000) { bound = true }
                assertTrue(bound)
                assertEquals(TcpProbeResult.OPEN, result)
                assertEquals(-1, received.get(3, TimeUnit.SECONDS).toInt())
            } finally { executor.shutdownNow() }
        }
    }

    @Test fun failedNetworkBindingNeverConnects() {
        ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 250
            val socket = Socket()
            val result = OneShotTcpProbe.check(socket, InetAddress.getLoopbackAddress(), server.localPort, 1000) {
                throw java.io.IOException("synthetic private marker")
            }
            assertEquals(TcpProbeResult.BIND_FAILED, result)
            assertTrue(socket.isClosed)
            try { server.accept(); fail("Unexpected connection") } catch (_: java.net.SocketTimeoutException) { }
        }
    }

    @Test fun invalidTimeoutAndPortAreRejected() {
        for ((port, timeout) in listOf(0 to 1000, 1234 to 0, 1234 to 5001)) {
            Socket().use { socket ->
                try { OneShotTcpProbe.check(socket, InetAddress.getLoopbackAddress(), port, timeout) {}; fail("Invalid input") }
                catch (_: IllegalArgumentException) { }
            }
        }
    }
}
