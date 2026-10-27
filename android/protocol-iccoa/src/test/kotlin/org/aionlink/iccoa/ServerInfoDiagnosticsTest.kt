package org.aionlink.iccoa

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class ServerInfoDiagnosticsTest {
    private fun base() = JSONObject().put("ssid", "DIRECT-ab-test").put("psk", "test-private-marker")
        .put("mac", "02:11:22:33:44:66").put("freq", 2412).put("port", "57209")
    private fun fault(bytes: ByteArray): ServerInfoFault {
        try { ServerInfo.parse(bytes); fail("Expected controlled parse failure") }
        catch (error: ServerInfoException) {
            assertFalse(error.message!!.contains("test-private-marker"))
            return error.fault
        }
        error("Unreachable")
    }
    @Test fun invalidFieldsProduceSafeReasonsInsteadOfCredentialValues() {
        listOf(Triple("ssid", "", ServerInfoFault.SSID), Triple("psk", "short", ServerInfoFault.PSK),
            Triple("mac", "02:00:00:00:00:00", ServerInfoFault.MAC), Triple("freq", -1, ServerInfoFault.FREQUENCY),
            Triple("port", "0", ServerInfoFault.PORT), Triple("type", 9999, ServerInfoFault.MODE)).forEach { (key, value, expected) ->
            assertEquals(expected, fault(base().put(key, value).toString().toByteArray()))
        }
    }
    @Test fun syntaxAndSizeFailuresAreDistinguished() {
        assertEquals(ServerInfoFault.SIZE, fault(ByteArray(4097)))
        assertEquals(ServerInfoFault.UTF8, fault(byteArrayOf(0xc3.toByte(), 0x28)))
        assertEquals(ServerInfoFault.JSON, fault("not JSON".toByteArray()))
    }
    @Test fun missingParametersAreNotMistakenForAnAuthenticationRejection() {
        val json = base(); json.remove("psk")
        assertEquals(ServerInfoFault.REQUIRED_FIELDS, fault(json.toString().toByteArray()))
    }
}
