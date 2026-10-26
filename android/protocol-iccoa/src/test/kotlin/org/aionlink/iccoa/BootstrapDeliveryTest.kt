package org.aionlink.iccoa

import org.junit.Assert.*
import org.junit.Test

class BootstrapDeliveryTest {
    private fun reply() = ServerInfo.parse("""{"ssid":"DIRECT-ab-test","psk":"test-only-passphrase","mac":"02:11:22:33:44:66","freq":2412,"port":"57209"}""".toByteArray())
    @Test fun earlyReplyDoesNotAdvanceNetworkBeforeWriteAcknowledgement() {
        val gate = BootstrapDelivery(); val info = reply()
        assertNull(gate.response(info)); assertSame(info, gate.acknowledge())
        assertNull(gate.acknowledge()); assertNull(gate.response(info))
    }
    @Test fun acknowledgedWriteStillWaitsForAResponse() {
        val gate = BootstrapDelivery(); val info = reply()
        assertNull(gate.acknowledge()); assertSame(info, gate.response(info))
        assertNull(gate.response(info))
    }
    @Test fun cancellationDropsThePendingResponse() {
        val gate = BootstrapDelivery()
        gate.response(reply()); gate.clear()
        assertNull(gate.acknowledge())
    }
}
