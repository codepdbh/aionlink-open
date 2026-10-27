package org.aionlink.iccoa

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class PlatformP2pAddressTest {
    @Test fun platformAnonymizationIsNotARealAddress() {
        val source = PlatformP2pAddress.fromPlatform("02:00:00:00:00:00")
        assertEquals(AddressDisclosure.ANDROID_ANONYMIZED, source.disclosure)
        assertNull(source.realValueOrNull())
        try { source.forClientInfo(false); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun explicitExperimentPreservesTheActualReturnedValueAndSchema() {
        val returned = "02:00:00:00:00:00" // Android's documented redaction, not a fixture car identity.
        val client = ClientInfo("A1B2C3", "Test phone", "123456", PlatformP2pAddress.fromPlatform(returned), true, true)
        val json = JSONObject(String(client.encode()))
        assertEquals(returned, json.getString("mac"))
        assertEquals(1001, json.getInt("type"))
        assertEquals(setOf("id", "model", "name", "pinCodeOrAuthentication", "band", "mac", "type"), json.keySet())
        assertTrue(client.usesAnonymizedMetadata)
    }
    @Test fun missingOrInvalidPlatformDataCannotBeReplacedEvenWithConsent() {
        listOf(null, "", "00:00:00:00:00:00", "ff:ff:ff:ff:ff:ff", "invalid").forEach {
            val source = PlatformP2pAddress.fromPlatform(it)
            assertEquals(AddressDisclosure.UNAVAILABLE, source.disclosure)
            try { source.forClientInfo(true); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun realMetadataIsUnchangedAndNotPrinted() {
        val address = "02:11:22:33:44:66"
        val source = PlatformP2pAddress.fromPlatform(address)
        assertEquals(address, source.forClientInfo(false))
        assertFalse(source.toString().contains(address))
    }
    @Test fun consentDoesNotRelaxRemoteEndpointValidation() {
        val bytes = """{"ssid":"DIRECT-ab-test","psk":"test-only-passphrase","mac":"02:00:00:00:00:00","freq":2412,"port":"57209"}""".toByteArray()
        try { ServerInfo.parse(bytes); fail() } catch (_: IllegalArgumentException) { }
    }
}
