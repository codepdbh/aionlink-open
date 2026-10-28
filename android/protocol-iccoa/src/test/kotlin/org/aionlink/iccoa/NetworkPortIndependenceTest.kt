package org.aionlink.iccoa

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class NetworkPortIndependenceTest {
    private fun base() = JSONObject().put("ssid", "DIRECT-ab-test")
        .put("psk", "test-only-passphrase").put("mac", "02:11:22:33:44:66").put("freq", 2412)

    @Test fun unusableSessionPortDoesNotPreventValidNetworkParameters() {
        listOf("" to ServerPortStatus.EMPTY, "0" to ServerPortStatus.OUT_OF_RANGE,
            "65536" to ServerPortStatus.OUT_OF_RANGE, "opaque-test-marker" to ServerPortStatus.NON_INTEGER,
            "57209,57219" to ServerPortStatus.NON_INTEGER, true to ServerPortStatus.UNSUPPORTED_TYPE,
            57209.5 to ServerPortStatus.UNSUPPORTED_TYPE).forEach { (raw, expected) ->
            val bytes = base().put("port", raw).toString().toByteArray()
            val info = ServerInfo.parseForNetwork(bytes)
            assertNull(info.port)
            assertEquals(expected, info.portStatus)
            assertFalse(info.toString().contains("test-only-passphrase"))
            try { ServerInfo.parse(bytes); fail("Session endpoint must remain strict") }
            catch (error: ServerInfoException) { assertEquals(ServerInfoFault.PORT, error.fault) }
        }
    }

    @Test fun absentPortIsClassifiedWithoutCreatingAnEndpoint() {
        val info = ServerInfo.parseForNetwork(base().toString().toByteArray())
        assertNull(info.port)
        assertEquals(ServerPortStatus.MISSING, info.portStatus)
    }

    @Test fun validEndpointsArePreservedExactlyAsNumbers() {
        listOf(1, "57209", 65535L).forEach { raw ->
            val info = ServerInfo.parseForNetwork(base().put("port", raw).toString().toByteArray())
            assertEquals(raw.toString().toInt(), info.port)
            assertEquals(ServerPortStatus.VALID, info.portStatus)
        }
    }

    @Test fun deferredPortNeverSkipsNetworkValidation() {
        listOf("psk" to "short", "mac" to "02:00:00:00:00:00", "ssid" to "unrelated", "freq" to -1).forEach { (key, value) ->
            try {
                ServerInfo.parseForNetwork(base().put("port", "0").put(key, value).toString().toByteArray())
                fail("Invalid network must remain blocked")
            } catch (error: ServerInfoException) { assertNotEquals(ServerInfoFault.PORT, error.fault) }
        }
    }

    @Test fun numericOutOfRangeIsCategorizedWithoutKeepingItsValue() {
        listOf("0" to ServerPortRange.ZERO, "-1" to ServerPortRange.NEGATIVE,
            "65536" to ServerPortRange.ABOVE_MAX, "9223372036854775808" to ServerPortRange.OVERFLOW).forEach { (raw, expected) ->
            val info = ServerInfo.parseForNetwork(base().put("port", raw).toString().toByteArray())
            assertNull(info.port)
            assertEquals(ServerPortStatus.OUT_OF_RANGE, info.portStatus)
            assertEquals(expected, info.portRange)
        }
    }
}
