package org.aionlink.open

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.aionlink.iccoa.ServerInfo
import org.aionlink.iccoa.ServerPortStatus
import org.aionlink.iccoa.ServerInfoException
import org.aionlink.iccoa.ServerInfoFault
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Android JSON implementation; synthetic fixtures only, no radio operations. */
@RunWith(AndroidJUnit4::class)
class NetworkBootstrapParsingTest {
    private fun base() = JSONObject().put("ssid", "DIRECT-ab-test")
        .put("psk", "test-only-passphrase").put("mac", "02:11:22:33:44:66").put("freq", 2412)

    @Test fun androidJsonSupportsDeferredPortWithoutInventingEndpoint() {
        listOf("0" to ServerPortStatus.OUT_OF_RANGE, "" to ServerPortStatus.EMPTY,
            "opaque-marker" to ServerPortStatus.NON_INTEGER, false to ServerPortStatus.UNSUPPORTED_TYPE).forEach { (raw, status) ->
            val info = ServerInfo.parseForNetwork(base().put("port", raw).toString().toByteArray())
            assertNull(info.port)
            assertEquals(status, info.portStatus)
        }
        assertEquals(57209, ServerInfo.parseForNetwork(base().put("port", "57209").toString().toByteArray()).port)
    }

    @Test fun androidJsonStillRejectsInvalidNetworkWithDeferredPort() {
        try {
            ServerInfo.parseForNetwork(base().put("port", "0").put("psk", "short").toString().toByteArray())
            fail("Invalid credentials must remain blocked")
        } catch (error: ServerInfoException) { assertEquals(ServerInfoFault.PSK, error.fault) }
    }
}
