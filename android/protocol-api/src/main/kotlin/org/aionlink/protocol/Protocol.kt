package org.aionlink.protocol

enum class TransportKind { BLUETOOTH_CLASSIC, BLE, USB, WIFI }
data class Peer(val ephemeralId: String, val transport: TransportKind)
sealed interface NegotiationResult {
    data class Unsupported(val reason: String) : NegotiationResult
}
interface ProtocolAdapter {
    val id: String
    suspend fun negotiate(peer: Peer): NegotiationResult
}
interface Transport {
    val kind: TransportKind
    suspend fun close()
}
