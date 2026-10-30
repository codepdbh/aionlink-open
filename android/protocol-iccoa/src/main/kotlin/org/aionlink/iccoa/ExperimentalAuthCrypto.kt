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

    /** RFC 5869 HKDF-SHA256; the observed session derivation uses salt = SHA-256(local nonce || peer nonce). */
    fun hkdfSha256(ikm: ByteArray, salt: ByteArray, info: ByteArray, length: Int): ByteArray {
        require(length in 1..255 * 32)
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256"))
        val prk = mac.doFinal(ikm)
        try {
            mac.init(SecretKeySpec(prk, "HmacSHA256"))
            val out = ByteArray(length)
            var block = ByteArray(0)
            var offset = 0
            var counter = 1
            while (offset < length) {
                mac.update(block); mac.update(info); mac.update(counter++.toByte())
                block.fill(0); block = mac.doFinal()
                val count = minOf(block.size, length - offset)
                System.arraycopy(block, 0, out, offset, count); offset += count
            }
            block.fill(0)
            return out
        } finally { prk.fill(0) }
    }

    /** Observed session envelope: ivLength(BE32) || iv || cipherLength(BE32) || AES-GCM ciphertext+tag. */
    fun sessionSeal(key: ByteArray, plain: ByteArray): ByteArray {
        require(key.size == 16)
        val iv = ByteArray(12).also(java.security.SecureRandom()::nextBytes)
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        val sealed = cipher.doFinal(plain)
        return java.nio.ByteBuffer.allocate(8 + iv.size + sealed.size)
            .putInt(iv.size).put(iv).putInt(sealed.size).put(sealed).array().also { sealed.fill(0) }
    }

    /** Inverse of [sessionSeal] for a single envelope; throws on tampering. */
    fun sessionOpen(key: ByteArray, envelope: ByteArray): ByteArray {
        require(key.size == 16)
        val buffer = java.nio.ByteBuffer.wrap(envelope)
        val ivSize = buffer.int
        require(ivSize in 12..16 && buffer.remaining() >= ivSize + 4)
        val iv = ByteArray(ivSize).also { buffer.get(it) }
        val size = buffer.int
        require(size in 16..buffer.remaining() && size == buffer.remaining())
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(javax.crypto.Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), javax.crypto.spec.GCMParameterSpec(128, iv))
        return cipher.doFinal(envelope, buffer.position(), size)
    }
}
