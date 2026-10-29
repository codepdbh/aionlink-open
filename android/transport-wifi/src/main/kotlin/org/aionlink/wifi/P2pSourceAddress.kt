package org.aionlink.wifi

import java.net.Inet4Address
import java.net.InetAddress

/** Offline selection of a single local IPv4 in the reported P2P interface subnet. */
object P2pSourceAddress {
    data class Candidate(val address: InetAddress, val prefixLength: Int) {
        override fun toString() = "Candidate(redacted)"
    }
    fun select(owner: InetAddress, candidates: List<Candidate>): InetAddress? {
        if (owner !is Inet4Address || owner.isAnyLocalAddress || owner.isLoopbackAddress || owner.isMulticastAddress) return null
        val target = owner.address
        val matches = candidates.filter { candidate ->
            val local = candidate.address
            if (local !is Inet4Address || local.isAnyLocalAddress || local.isLoopbackAddress ||
                local.isMulticastAddress || local == owner || candidate.prefixLength !in 16..30) return@filter false
            val bytes = local.address
            (0 until candidate.prefixLength).all { bit ->
                val mask = 1 shl (7 - bit % 8)
                (target[bit / 8].toInt() and mask) == (bytes[bit / 8].toInt() and mask)
            }
        }.map { it.address }.distinct()
        return matches.singleOrNull()
    }
}
