package org.aionlink.iccoa

import org.junit.Assert.*
import org.junit.Test

class NormalLinkReadinessTest {
    @Test fun anonymizedOrMissingAddressBlocksNormalModeBeforePinEntry() {
        listOf(null, "02:00:00:00:00:00", "", "ff:ff:ff:ff:ff:ff").forEach {
            assertEquals(NormalLinkReadiness.LOCAL_ADDRESS_UNAVAILABLE, normalLinkReadiness(it))
        }
    }
    @Test fun usableLocalMetadataPermitsTheNormalRoute() {
        assertEquals(NormalLinkReadiness.READY, normalLinkReadiness("02:11:22:33:44:66"))
    }
}
