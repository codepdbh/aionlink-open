package org.aionlink.iccoa

import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.KeyAgreement

enum class PeerCheck { VERIFIED_PROOF_AND_SIGNATURE, BAD_VERSION_OR_RESULT, MISSING_OR_INVALID_FIELDS, BAD_PROOF, BAD_SIGNATURE }

/** Single-use candidate for the normal key-negotiation stage. A verified response yields a session key
 * used only for the observed AuthConfirm; acceptance by the receiver is not implied. */
class ExperimentalNormalAuth(pinInput: ByteArray, clientId: ByteArray, model: String, workModes: Int) : AutoCloseable {
    private var pin: ByteArray? = null
    private var authPair: KeyPair? = null
    private var agreementPair: KeyPair? = null
    private val nonce = ByteArray(32)
    private val id = clientId.copyOf()
    private val phoneModel = model
    private val modes = workModes
    private var issued = false
    private var sessionKey: ByteArray? = null
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
            sessionKey = deriveSessionKey(peerAgreement, peerNonce)
            return PeerCheck.VERIFIED_PROOF_AND_SIGNATURE
        } finally { wipeNegotiation() }
    }

    /** Samsung KeyNegotiator.agreeOnSessionKey: HKDF-SHA256(ECDH, SHA-256(local || peer nonce), "session_key", 16). */
    private fun deriveSessionKey(peerAgreement: ByteArray, peerNonce: ByteArray): ByteArray {
        val agreement = KeyAgreement.getInstance("ECDH")
        agreement.init(agreementPair!!.private)
        agreement.doPhase(ExperimentalAuthCrypto.decodePublicPoint(peerAgreement), true)
        val secret = agreement.generateSecret()
        val salt = MessageDigest.getInstance("SHA-256").run { update(nonce); digest(peerNonce) }
        try { return ExperimentalAuthCrypto.hkdfSha256(secret, salt, "session_key".toByteArray(Charsets.UTF_8), 16) }
        finally { secret.fill(0); salt.fill(0) }
    }

    /** Copy of the derived session key for the caller's encrypted channels; the caller must wipe it. */
    fun sessionKeyCopy(): ByteArray = (sessionKey ?: throw IllegalStateException("Session key unavailable")).copyOf()

    /** Observed AuthConfirm cipher: session-sealed UTF-8 "<local group address>:0". Single use. */
    fun confirmCipher(localAddress: String): ByteArray {
        val key = sessionKey ?: throw IllegalStateException("Session key unavailable")
        require(localAddress.isNotEmpty() && localAddress.length <= 64)
        val plain = "$localAddress:0".toByteArray(Charsets.UTF_8)
        try { return ExperimentalAuthCrypto.sessionSeal(key, plain) }
        finally { plain.fill(0); key.fill(0); sessionKey = null }
    }

    private fun wipeNegotiation() {
        pin?.fill(0); pin = null; nonce.fill(0); id.fill(0)
        authPair = null; agreementPair = null
    }
    override fun close() {
        wipeNegotiation()
        sessionKey?.fill(0); sessionKey = null
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
