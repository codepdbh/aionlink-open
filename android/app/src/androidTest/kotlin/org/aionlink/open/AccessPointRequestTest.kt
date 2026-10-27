package org.aionlink.open

import android.net.NetworkCapabilities
import android.net.wifi.WifiNetworkSpecifier
import org.aionlink.iccoa.ServerInfo
import org.aionlink.wifi.accessPointRequest
import org.junit.Assert.*
import org.junit.Test

/** Constructs a fixture request only; never submits it to Android or connects a radio. */
class AccessPointRequestTest {
    @Test fun requestIsWifiLocalOnlyAndUsesTheFrameworkSpecifier() {
        val info = ServerInfo.parse("""{"ssid":"test-car-ap","psk":"test-only-passphrase","mac":"02:11:22:33:44:66","freq":0,"port":57209,"type":1002}""".toByteArray())
        val request = accessPointRequest(info)
        assertTrue(request.hasTransport(NetworkCapabilities.TRANSPORT_WIFI))
        assertFalse(request.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET))
        assertTrue(request.networkSpecifier is WifiNetworkSpecifier)
    }
    @Test fun aP2pResponseCannotBeSilentlyConvertedToAnApRequest() {
        val info = ServerInfo.parse("""{"ssid":"DIRECT-ab-test","psk":"test-only-passphrase","mac":"02:11:22:33:44:66","freq":2412,"port":"57209","type":1001}""".toByteArray())
        try { accessPointRequest(info); fail() } catch (_: IllegalArgumentException) { }
    }
}
