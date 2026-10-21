package org.aionlink.core

import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.aionlink.protocol.*
import org.junit.Assert.*
import org.junit.Test

class SessionTest {
    private fun adapter(block: suspend () -> NegotiationResult) = object : ProtocolAdapter {
        override val id = "test-only"
        override suspend fun negotiate(peer: Peer) = block()
    }
    private val peer = Peer("temporary-1", TransportKind.BLE)
    @Test fun discoveryIsNeverSession() {
        val session = SessionCoordinator(adapter { error("must not negotiate") })
        session.startDiscovery(); session.deviceFound(); session.discoveryFinished()
        assertEquals(ConnectionState.DEVICE_FOUND, session.state.value)
    }
    @Test fun cannotNegotiateWithoutDiscovery() = runTest {
        val session = SessionCoordinator(adapter { error("must not negotiate") })
        try { session.negotiate(peer); fail("invalid transition accepted") } catch (_: IllegalStateException) { }
    }
    @Test fun unsupportedDoesNotCreateSession() = runTest {
        val session = SessionCoordinator(adapter { NegotiationResult.Unsupported("unknown wire format") })
        session.startDiscovery(); session.deviceFound(); session.negotiate(peer)
        assertEquals(ConnectionState.PROTOCOL_UNSUPPORTED, session.state.value)
    }
    @Test fun stalledNegotiationTimesOut() = runTest {
        val session = SessionCoordinator(adapter { delay(10000); NegotiationResult.Unsupported("late") }, 100)
        session.startDiscovery(); session.deviceFound(); session.negotiate(peer)
        assertEquals(ConnectionState.TIMEOUT, session.state.value)
    }
    @Test fun transportFailureIsExplicit() = runTest {
        val session = SessionCoordinator(adapter { throw java.io.IOException("disconnected") })
        session.startDiscovery(); session.deviceFound(); session.negotiate(peer)
        assertEquals(ConnectionState.TRANSPORT_ERROR, session.state.value)
    }
}
