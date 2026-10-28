package org.aionlink.iccoa

import java.security.KeyPairGenerator
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import org.junit.Assert.*
import org.junit.Test

class ExperimentalAuthCryptoTest {
    private fun bytes(hex: String) = hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    private fun fixed(value: java.math.BigInteger): ByteArray {
        val bytes = value.toByteArray()
        val result = ByteArray(32)
        val length = minOf(bytes.size, result.size)
        System.arraycopy(bytes, bytes.size - length, result, result.size - length, length)
        return result
    }
    private fun point(key: ECPublicKey) = fixed(key.w.affineX) + fixed(key.w.affineY)

    @Test fun hmacMatchesIndependentlyCalculatedVectorAndRejectsWrongPin() {
        val key = ByteArray(64) { it.toByte() }; val nonce = ByteArray(32) { it.toByte() }
        val pin = "123456".toByteArray()
        val expected = bytes("f1d5154dd1f2ebbeecb8bc129cad9849c834caa0a118d54d6e18a6825ecc38e0")
        assertArrayEquals(expected, ExperimentalAuthCrypto.pinProof(key, nonce, pin))
        assertTrue(ExperimentalAuthCrypto.verifyPinProof(key, nonce, pin, expected))
        assertFalse(ExperimentalAuthCrypto.verifyPinProof(key, nonce, "123457".toByteArray(), expected))
        assertFalse(ExperimentalAuthCrypto.verifyPinProof(key, nonce, pin, ByteArray(31)))
    }

    @Test fun validatesP256PointsAndSignaturesAndRejectsTampering() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val pair = generator.generateKeyPair()
        val encoded = point(pair.public as ECPublicKey)
        val decoded = ExperimentalAuthCrypto.decodePublicPoint(encoded)
        assertEquals((pair.public as ECPublicKey).w, decoded.w)
        val nonce = ByteArray(32) { it.toByte() }; val localNonce = ByteArray(32) { (31 - it).toByte() }
        val localPoint = point(generator.generateKeyPair().public as ECPublicKey)
        val sign = Signature.getInstance("SHA256withECDSA")
        sign.initSign(pair.private); sign.update(encoded); sign.update(localPoint); sign.update(nonce); sign.update(localNonce)
        val signature = sign.sign()
        assertTrue(ExperimentalAuthCrypto.verifyPeerAgreementSignature(decoded, encoded, localPoint, nonce, localNonce, signature))
        nonce[0] = 1
        assertFalse(ExperimentalAuthCrypto.verifyPeerAgreementSignature(decoded, encoded, localPoint, nonce, localNonce, signature))
        try { ExperimentalAuthCrypto.decodePublicPoint(ByteArray(64)); fail("invalid point") }
        catch (_: IllegalArgumentException) { }
        try { ExperimentalAuthCrypto.decodePublicPoint(ByteArray(63)); fail("invalid length") }
        catch (_: IllegalArgumentException) { }
    }

    @Test fun peerProofBindsBothPublicKeysAndBothNonces() {
        val peer = ByteArray(64) { it.toByte() }; val local = ByteArray(64) { (63 - it).toByte() }
        val peerNonce = ByteArray(32) { it.toByte() }; val localNonce = ByteArray(32) { (31 - it).toByte() }
        val expected = bytes("ab8d4111ddd07a4d78dc95602543de4a1402b22f27f53a8c4615a0b25ff84436")
        val pin = "123456".toByteArray()
        assertTrue(ExperimentalAuthCrypto.verifyPeerProof(peer, local, peerNonce, localNonce, pin, expected))
        local[0] = 0
        assertFalse(ExperimentalAuthCrypto.verifyPeerProof(peer, local, peerNonce, localNonce, pin, expected))
    }
}
