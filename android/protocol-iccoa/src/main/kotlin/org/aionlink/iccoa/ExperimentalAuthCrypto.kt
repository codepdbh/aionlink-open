package org.aionlink.iccoa

import java.math.BigInteger
import java.security.AlgorithmParameters
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECFieldFp
import java.security.spec.ECGenParameterSpec
import java.security.spec.ECParameterSpec
import java.security.spec.ECPoint
import java.security.spec.ECPublicKeySpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Offline building blocks for the observed normal authentication flow, not a session implementation. */
object ExperimentalAuthCrypto {
    private const val COORDINATE_BYTES = 32
    private const val NONCE_BYTES = 32
    private fun p256(): ECParameterSpec = AlgorithmParameters.getInstance("EC").run {
        init(ECGenParameterSpec("secp256r1"))
        getParameterSpec(ECParameterSpec::class.java)
    }

    /** Validates an uncompressed X || Y public point before asking a provider to use it. */
    fun decodePublicPoint(encoded: ByteArray): ECPublicKey {
        require(encoded.size == 2 * COORDINATE_BYTES) { "Invalid public point size" }
        val params = p256()
        val field = params.curve.field as ECFieldFp
        val prime = field.p
        val x = BigInteger(1, encoded.copyOfRange(0, COORDINATE_BYTES))
        val y = BigInteger(1, encoded.copyOfRange(COORDINATE_BYTES, encoded.size))
        require(x < prime && y < prime) { "Public point outside field" }
        val lhs = y.multiply(y).mod(prime)
        val rhs = x.multiply(x).multiply(x).add(params.curve.a.multiply(x)).add(params.curve.b).mod(prime)
        require(lhs == rhs) { "Public point is not on P-256" }
        return KeyFactory.getInstance("EC").generatePublic(ECPublicKeySpec(ECPoint(x, y), params)) as ECPublicKey
    }

    /** Synthetic fixtures only until the full wire handshake is verified. The caller owns the PIN buffer. */
    fun pinProof(publicPoint: ByteArray, nonce: ByteArray, pin: ByteArray): ByteArray {
        require(publicPoint.size == 64 && nonce.size == NONCE_BYTES) { "Invalid authentication input size" }
        return proof(arrayOf(publicPoint), arrayOf(nonce), pin)
    }

    /** Observed normal response ties both identities and both nonces to the PIN. */
    fun verifyPeerProof(peerPoint: ByteArray, localPoint: ByteArray, peerNonce: ByteArray,
                        localNonce: ByteArray, pin: ByteArray, peerProof: ByteArray): Boolean {
        require(peerPoint.size == 64 && localPoint.size == 64 && peerNonce.size == 32 && localNonce.size == 32)
        if (peerProof.size != 32) return false
        val expected = proof(arrayOf(peerPoint, localPoint), arrayOf(peerNonce, localNonce), pin)
        return try { MessageDigest.isEqual(expected, peerProof) } finally { expected.fill(0) }
    }

    private fun proof(points: Array<ByteArray>, nonces: Array<ByteArray>, pin: ByteArray): ByteArray {
        require(pin.size == 6 && pin.all { it in 48..57 }) { "PIN must contain six ASCII digits" }
        val digest = MessageDigest.getInstance("SHA-256")
        nonces.forEach(digest::update)
        val derivedKey = digest.digest(pin)
        try {
            val mac = Mac.getInstance("HmacSHA256")
            mac.init(SecretKeySpec(derivedKey, "HmacSHA256"))
            points.forEach(mac::update)
            return mac.doFinal()
        } finally { derivedKey.fill(0) }
    }

    fun verifyPinProof(publicPoint: ByteArray, nonce: ByteArray, pin: ByteArray, proof: ByteArray): Boolean {
        if (proof.size != 32) return false
        val expected = pinProof(publicPoint, nonce, pin)
        return try { MessageDigest.isEqual(expected, proof) } finally { expected.fill(0) }
    }

    fun verifyPeerAgreementSignature(authKey: ECPublicKey, peerAgreement: ByteArray, localAgreement: ByteArray,
                                     peerNonce: ByteArray, localNonce: ByteArray, signature: ByteArray): Boolean {
        require(peerAgreement.size == 64 && localAgreement.size == 64 && peerNonce.size == 32 && localNonce.size == 32) {
            "Invalid agreement input size"
        }
        require(signature.size in 8..80) { "Invalid signature size" }
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(authKey)
        verifier.update(peerAgreement)
        verifier.update(localAgreement)
        verifier.update(peerNonce)
        verifier.update(localNonce)
        return try { verifier.verify(signature) } catch (_: java.security.SignatureException) { false }
    }
}
