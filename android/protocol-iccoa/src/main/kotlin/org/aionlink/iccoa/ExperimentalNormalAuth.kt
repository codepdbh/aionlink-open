package org.aionlink.iccoa

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec

enum class PeerCheck { VERIFIED_PROOF_AND_SIGNATURE, BAD_VERSION_OR_RESULT, MISSING_OR_INVALID_FIELDS, BAD_PROOF, BAD_SIGNATURE }

/** Single-use offline candidate for the normal key-negotiation stage. Does not confirm a session. */
class ExperimentalNormalAuth(pinInput: ByteArray, clientId: ByteArray, model: String, workModes: Int) : AutoCloseable {
    private var pin: ByteArray? = null
    private var authPair: KeyPair? = null
    private var agreementPair: KeyPair? = null
    private val nonce = ByteArray(32)
    private val id = clientId.copyOf()
    private val phoneModel = model
    private val modes = workModes
    private var issued = false
    init {
        require(pinInput.size == 6 && pinInput.all { it in 48..57 })
        require(id.size in 1..64 && phoneModel.toByteArray(Charsets.UTF_8).size in 1..64 && modes >= 0)
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        authPair = generator.generateKeyPair()
        agreementPair = generator.generateKeyPair()
        SecureRandom().nextBytes(nonce)
        pin = pinInput.copyOf()
    }
    fun request(): ExperimentalAuthWire.Request {
        check(!issued && pin != null) { "Attempt unavailable" }
        issued = true
        try {
            val auth = authPair ?: error("Attempt unavailable")
            val agreement = agreementPair ?: error("Attempt unavailable")
            val authPublic = encode(auth.public as ECPublicKey)
            val agreementPublic = encode(agreement.public as ECPublicKey)
            val signer = Signature.getInstance("SHA256withECDSA")
            signer.initSign(auth.private)
            signer.update(agreementPublic); signer.update(nonce)
            return ExperimentalAuthWire.Request(1793, authPublic, ExperimentalAuthCrypto.pinProof(authPublic, nonce, pin!!),
                agreementPublic, signer.sign(), nonce.copyOf(), id.copyOf(), phoneModel, true, modes, "AionLink Open")
        } catch (error: Exception) { close(); throw error }
    }

    /** Consumes the attempt and deletes the PIN copy regardless of outcome. */
    fun verify(response: ExperimentalAuthWire.Response): PeerCheck {
        check(issued && pin != null) { "Attempt unavailable" }
        try {
            if (response.version != 1793 || (response.result ?: 0) != 0) return PeerCheck.BAD_VERSION_OR_RESULT
            val peerAuth = response.authPoint ?: return PeerCheck.MISSING_OR_INVALID_FIELDS
            val peerProof = response.proof ?: return PeerCheck.MISSING_OR_INVALID_FIELDS
            val peerAgreement = response.agreementPoint ?: return PeerCheck.MISSING_OR_INVALID_FIELDS
            val peerSignature = response.agreementSignature ?: return PeerCheck.MISSING_OR_INVALID_FIELDS
            val peerNonce = response.nonce ?: return PeerCheck.MISSING_OR_INVALID_FIELDS
            if (peerAuth.size != 64 || peerAgreement.size != 64 || peerNonce.size != 32 || peerProof.size != 32 || peerSignature.size !in 8..80)
                return PeerCheck.MISSING_OR_INVALID_FIELDS
            val localAuth = encode(authPair!!.public as ECPublicKey)
            val localAgreement = encode(agreementPair!!.public as ECPublicKey)
            if (!ExperimentalAuthCrypto.verifyPeerProof(peerAuth, localAuth, peerNonce, nonce, pin!!, peerProof)) return PeerCheck.BAD_PROOF
            val authPublic = try { ExperimentalAuthCrypto.decodePublicPoint(peerAuth) } catch (_: Exception) { return PeerCheck.MISSING_OR_INVALID_FIELDS }
            try { ExperimentalAuthCrypto.decodePublicPoint(peerAgreement) } catch (_: Exception) { return PeerCheck.MISSING_OR_INVALID_FIELDS }
            if (!ExperimentalAuthCrypto.verifyPeerAgreementSignature(authPublic, peerAgreement, localAgreement, peerNonce, nonce, peerSignature))
                return PeerCheck.BAD_SIGNATURE
            return PeerCheck.VERIFIED_PROOF_AND_SIGNATURE
        } finally { close() }
    }
    override fun close() {
        pin?.fill(0); pin = null; nonce.fill(0); id.fill(0)
        authPair = null; agreementPair = null
    }
    override fun toString() = "ExperimentalNormalAuth(redacted)"
    private fun encode(key: ECPublicKey): ByteArray = coordinate(key.w.affineX) + coordinate(key.w.affineY)
    private fun coordinate(n: BigInteger): ByteArray {
        val value = n.toByteArray()
        require(value.size <= 33 && (value.size != 33 || value[0] == 0.toByte()))
        val count = minOf(32, value.size)
        return ByteArray(32).also { System.arraycopy(value, value.size - count, it, 32 - count, count) }
    }
}
