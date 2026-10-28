package org.aionlink.open

import androidx.test.ext.junit.runners.AndroidJUnit4
import java.security.KeyPairGenerator
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import org.aionlink.iccoa.ExperimentalAuthCrypto
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AuthCryptoPlatformTest {
    @Test fun platformSupportsObservedPublicPointAndPinProofPrimitives() {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val key = generator.generateKeyPair().public as ECPublicKey
        fun coordinate(number: java.math.BigInteger): ByteArray {
            val raw = number.toByteArray(); val out = ByteArray(32); val n = minOf(raw.size, 32)
            System.arraycopy(raw, raw.size - n, out, 32 - n, n); return out
        }
        val encoded = coordinate(key.w.affineX) + coordinate(key.w.affineY)
        assertEquals(key.w, ExperimentalAuthCrypto.decodePublicPoint(encoded).w)
        val keyFixture = ByteArray(64) { it.toByte() }; val nonce = ByteArray(32) { it.toByte() }
        val expected = "a7a055f6fd2b94394336fa5519a93991ff584907b8090d9267c1c6ff6c5d458e"
            .chunked(2).map { it.toInt(16).toByte() }.toByteArray()
        assertArrayEquals(expected, ExperimentalAuthCrypto.pinProof(keyFixture, nonce, "123456".toByteArray()))
    }
}
