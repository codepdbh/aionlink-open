package org.aionlink.iccoa

import org.junit.Assert.*
import org.junit.Test

class NetworkModeTest {
    @Test fun observedApResponseHasNoInventedDirectPrefixOrFrequency() {
        val info = ServerInfo.parse("""{"ssid":"test-car-ap","psk":"test-only-passphrase","mac":"02:11:22:33:44:66","freq":0,"port":57209,"type":1002}""".toByteArray())
        assertEquals(NetworkMode.ACCESS_POINT, info.mode)
        assertEquals(57209, info.port)
        assertEquals(0, info.frequency)
    }
    @Test fun absentTypeKeepsObservedP2pDefault() {
        val info = ServerInfo.parse("""{"ssid":"DIRECT-ab-test","psk":"test-only-passphrase","mac":"02:11:22:33:44:66","freq":2412,"port":"57209"}""".toByteArray())
        assertEquals(NetworkMode.P2P, info.mode)
    }
    @Test fun p2pCannotUseApOnlyParameters() {
        try {
            ServerInfo.parse("""{"ssid":"test-car-ap","psk":"test-only-passphrase","mac":"02:11:22:33:44:66","freq":0,"port":57209,"type":1001}""".toByteArray())
            fail()
        } catch (_: IllegalArgumentException) { }
    }
}
