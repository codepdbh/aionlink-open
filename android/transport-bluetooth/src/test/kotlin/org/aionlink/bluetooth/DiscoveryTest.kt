package org.aionlink.bluetooth

import org.aionlink.iccoa.AdvertisementEvidence
import org.junit.Assert.*
import org.junit.Test

class DiscoveryTest {
    private fun peer(id: String) = DiscoveredDevice(id, "Sin nombre", "BLE", -51)
    @Test fun repeatedAdvertisementsDoNotMoveCards() {
        val first = peer("first")
        val second = peer("second")
        val result = mergeObservation(listOf(first, second), first.copy(rssi = -42))
        assertEquals(listOf("first", "second"), result.map { it.id })
        assertEquals(-42, result.first().rssi)
    }
    @Test fun candidateEvidenceSurvivesAnAdvertisementWithoutUuids() {
        val candidate = peer("candidate").copy(recognizedServices = setOf(AdvertisementEvidence.ADVERTISED_SERVICE), observedServiceCount = 1, connectable = true)
        val result = mergeObservation(listOf(candidate), peer("candidate")).single()
        assertEquals(candidate.recognizedServices, result.recognizedServices)
        assertEquals(1, result.observedServiceCount)
        assertEquals(true, result.connectable)
    }
    @Test fun latestNonConnectableAdvertisementDisablesAction() {
        val candidate = peer("candidate").copy(connectable = true)
        assertEquals(false, mergeObservation(listOf(candidate), candidate.copy(connectable = false)).single().connectable)
    }
    @Test fun unrelatedObservationDoesNotBecomeCandidate() {
        val result = mergeObservation(listOf(peer("one")), peer("two"))
        assertEquals(2, result.size)
        assertTrue(result.all { it.recognizedServices.isEmpty() })
    }
}
