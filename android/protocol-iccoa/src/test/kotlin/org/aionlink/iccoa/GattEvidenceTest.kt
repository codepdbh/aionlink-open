package org.aionlink.iccoa

import org.junit.Assert.*
import org.junit.Test

class GattEvidenceTest {
    @Test fun privateServicesAreNotExported() {
        val result = GattEvidence.summarize("temporary", listOf("private-service"), listOf("private-characteristic" to 2))
        assertEquals(GattStage.INSPECTED, result.stage)
        assertEquals(1, result.serviceCount)
        assertFalse(result.unionSharePresent)
        assertTrue(result.knownCharacteristics.isEmpty())
        assertFalse(result.toString().contains("private-service"))
    }
    @Test fun serviceAndCharacteristicMetadataAreSeparatedFromSession() {
        val result = GattEvidence.summarize("temporary", listOf(AdvertisementEvidence.GATT_SERVICE.uppercase()),
            listOf(AdvertisementEvidence.CLIENT_INFO to 8, "unknown" to 16))
        assertTrue(result.unionSharePresent)
        assertEquals(listOf(KnownGattCharacteristic(AdvertisementEvidence.CLIENT_INFO,8)),result.knownCharacteristics)
        assertEquals(GattStage.INSPECTED,result.stage)
    }
    @Test fun aCharacteristicOutsideUnionShareIsNotEvidence() {
        val result = GattEvidence.summarize("temporary", emptyList(), listOf(AdvertisementEvidence.CLIENT_INFO to 8))
        assertTrue(result.knownCharacteristics.isEmpty())
    }
}
