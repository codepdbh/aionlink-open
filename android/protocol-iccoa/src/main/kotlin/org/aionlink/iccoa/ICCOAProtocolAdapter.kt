package org.aionlink.iccoa
import org.aionlink.protocol.*

/** Deliberately sends no bytes until an interoperable handshake is evidenced. */
class ICCOAProtocolAdapter : ProtocolAdapter {
    override val id = "iccoa-research"
    override suspend fun negotiate(peer: Peer) = NegotiationResult.Unsupported(
        "ICCOA: negociación y autenticación pendientes de investigación verificable"
    )
}
