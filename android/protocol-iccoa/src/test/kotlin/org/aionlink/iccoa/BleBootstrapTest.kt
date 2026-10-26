package org.aionlink.iccoa

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class BleBootstrapTest {
    private fun server() = JSONObject().put("ssid", "DIRECT-ab-test").put("psk", "test-only-passphrase")
        .put("mac", "02:11:22:33:44:66").put("freq", 5180).put("port", "57209").put("type", 1001)
    private fun parse(json: JSONObject) = ServerInfo.parse(json.toString().toByteArray())
    @Test fun ownIdentityAndBandAreEncodedWithoutOemImpersonation() {
        val client = ClientInfo("A1B2C3", "Test \"model\"", "123456", "02:11:22:33:44:66", true)
        val json = JSONObject(String(client.encode()))
        assertEquals("AionLink Open", json.getString("name"))
        assertEquals("Test \"model\"", json.getString("model"))
        assertEquals(1, json.getInt("band")); assertEquals(1001, json.getInt("type"))
        assertFalse(client.toString().contains("123456"))
    }
    @Test fun placeholdersAndMulticastAddressesAreRejected() {
        listOf("02:00:00:00:00:00", "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff", "invalid").forEach {
            assertFalse(validUnicastMac(it))
        }
    }
    @Test fun liveServerParametersAreParsedButNeverPrinted() {
        val result = parse(server())
        assertEquals(5180, result.frequency); assertEquals(57209, result.port)
        assertEquals("DIRECT-ab-test", result.ssid)
        assertFalse(result.toString().contains("test-only-passphrase"))
    }
    @Test fun unsupportedTopologyIsRejectedWithoutLeakingContent() {
        try { parse(server().put("type", 1002)); fail() } catch (e: IllegalArgumentException) {
            assertFalse(e.message!!.contains("test-only-passphrase"))
        }
    }
    @Test fun malformedAndOversizedResponsesAreRejected() {
        listOf(byteArrayOf(0xc3.toByte(), 0x28), ByteArray(4097), "not JSON".toByteArray()).forEach {
            try { ServerInfo.parse(it); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun invalidNetworkAndEndpointParametersAreRejected() {
        listOf("ssid" to "unrelated-network", "psk" to "short", "port" to "0", "freq" to 0,
            "mac" to "01:11:22:33:44:55").forEach { (key, value) ->
            try { parse(server().put(key, value)); fail(key) } catch (_: IllegalArgumentException) { }
        }
    }
}
