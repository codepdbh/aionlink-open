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
        val expected = bytes("a7a055f6fd2b94394336fa5519a93991ff584907b8090d9267c1c6ff6c5d458e")
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
        val expected = bytes("095a02282fb8703677d169ba35b8e74ff5a2871e0bfff6f0718dfa1fba4779de")
        val pin = "123456".toByteArray()
        assertTrue(ExperimentalAuthCrypto.verifyPeerProof(peer, local, peerNonce, localNonce, pin, expected))
        local[0] = 0
        assertFalse(ExperimentalAuthCrypto.verifyPeerProof(peer, local, peerNonce, localNonce, pin, expected))
    }

    @Test fun hkdfMatchesRfc5869TestCaseOne() {
        val okm = ExperimentalAuthCrypto.hkdfSha256(ByteArray(22) { 0x0b }, bytes("000102030405060708090a0b0c"),
            bytes("f0f1f2f3f4f5f6f7f8f9"), 42)
        assertArrayEquals(bytes("3cb25f25faacd57a90434f64d0362f2a2d2d0a90cf1a5a4c5db02d56ecc4c5bf34007208d5b887185865"), okm)
    }
    @Test fun sessionEnvelopeRoundTripsAndRejectsTampering() {
        val key = ByteArray(16) { it.toByte() }
        val sealed = ExperimentalAuthCrypto.sessionSeal(key, "192.168.49.2:0".toByteArray())
        assertEquals(12, java.nio.ByteBuffer.wrap(sealed).int)
        assertEquals("192.168.49.2:0", String(ExperimentalAuthCrypto.sessionOpen(key, sealed)))
        sealed[sealed.lastIndex] = (sealed.last().toInt() xor 1).toByte()
        try { ExperimentalAuthCrypto.sessionOpen(key, sealed); fail("tampered envelope accepted") }
        catch (_: javax.crypto.AEADBadTagException) { }
    }
}
