package org.aionlink.wifi

import java.net.InetAddress
import org.junit.Assert.*
import org.junit.Test

class P2pSourceAddressTest {
    private fun ip(value: String) = InetAddress.getByName(value)
    private fun candidate(value: String, prefix: Int = 24) = P2pSourceAddress.Candidate(ip(value), prefix)
    @Test fun selectsOnlyUniqueLocalAddressInOwnerSubnet() {
        val owner = ip("192.0.2.1")
        assertEquals(ip("192.0.2.4"), P2pSourceAddress.select(owner, listOf(candidate("192.0.2.4"), candidate("198.51.100.7"))))
        assertNull(P2pSourceAddress.select(owner, listOf(candidate("198.51.100.7"))))
        assertNull(P2pSourceAddress.select(owner, listOf(candidate("192.0.2.4"), candidate("192.0.2.5"))))
        assertNull(P2pSourceAddress.select(owner, listOf(candidate("192.0.2.1"))))
    }
    @Test fun rejectsUnusableAddressesAndInvalidPrefixes() {
        assertNull(P2pSourceAddress.select(ip("127.0.0.1"), listOf(candidate("127.0.0.2"))))
        assertNull(P2pSourceAddress.select(ip("192.0.2.1"), listOf(candidate("192.0.2.2", 0))))
        assertNull(P2pSourceAddress.select(ip("192.0.2.1"), listOf(candidate("192.0.2.2", 8))))
        assertNull(P2pSourceAddress.select(ip("192.0.2.1"), listOf(candidate("192.0.2.2", 32))))
        assertNull(P2pSourceAddress.select(ip("::1"), listOf(candidate("192.0.2.2"))))
        assertFalse(candidate("192.0.2.2").toString().contains("192.0.2.2"))
    }
}
