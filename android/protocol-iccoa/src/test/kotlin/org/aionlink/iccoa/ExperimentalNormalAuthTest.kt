package org.aionlink.iccoa

import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import org.junit.Assert.*
import org.junit.Test

class ExperimentalNormalAuthTest {
    private fun point(key: ECPublicKey): ByteArray {
        fun part(number: BigInteger): ByteArray {
            val bytes = number.toByteArray(); val size = minOf(bytes.size, 32)
            return ByteArray(32).also { System.arraycopy(bytes, bytes.size - size, it, 32 - size, size) }
        }
        return part(key.w.affineX) + part(key.w.affineY)
    }
    private fun peer(req: ExperimentalAuthWire.Request, wrongPin: Boolean = false, corruptSignature: Boolean = false): ExperimentalAuthWire.Response {
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val auth = generator.generateKeyPair(); val agreement = generator.generateKeyPair()
        val authBytes = point(auth.public as ECPublicKey); val agreementBytes = point(agreement.public as ECPublicKey)
        val peerNonce = ByteArray(32) { (it + 1).toByte() }
        val key = MessageDigest.getInstance("SHA-256").digest(peerNonce + req.nonce + (if (wrongPin) "000000" else "123456").toByteArray())
        val proof = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(authBytes + req.authPoint) }
        val signer = Signature.getInstance("SHA256withECDSA").apply {
            initSign(auth.private); update(agreementBytes + req.agreementPoint + peerNonce + req.nonce)
        }
        val signature = signer.sign()
        if (corruptSignature) signature[signature.lastIndex] = (signature.last().toInt() xor 1).toByte()
        key.fill(0)
        return ExperimentalAuthWire.Response(1793, authBytes, proof, agreementBytes, signature, peerNonce, 0, 1)
    }
    @Test fun independentPeerValidatesNormalProofAndSignature() {
        ExperimentalNormalAuth("123456".toByteArray(), "own-id".toByteArray(), "test-phone", 1).use { attempt ->
            val request = attempt.request()
            val publicKey = ExperimentalAuthCrypto.decodePublicPoint(request.authPoint)
            val checker = Signature.getInstance("SHA256withECDSA").apply { initVerify(publicKey); update(request.agreementPoint + request.nonce) }
            assertTrue(checker.verify(request.agreementSignature))
            assertEquals(PeerCheck.VERIFIED_PROOF_AND_SIGNATURE, attempt.verify(peer(request)))
            try { attempt.request(); fail("attempt reused") } catch (_: IllegalStateException) { }
            assertTrue(attempt.confirmCipher("192.168.49.2").isNotEmpty())
            try { attempt.confirmCipher("192.168.49.2"); fail("confirmation reused") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun wrongPinAndTamperedSignatureAreRejectedAndAttemptConsumed() {
        for (mode in 0..1) ExperimentalNormalAuth("123456".toByteArray(), "own-id".toByteArray(), "test-phone", 1).use { attempt ->
            val request = attempt.request()
            val response = peer(request, wrongPin = mode == 0, corruptSignature = mode == 1)
            assertEquals(if (mode == 0) PeerCheck.BAD_PROOF else PeerCheck.BAD_SIGNATURE, attempt.verify(response))
            try { attempt.confirmCipher("192.168.49.2"); fail("unverified confirmation") } catch (_: IllegalStateException) { }
            try { attempt.verify(response); fail("attempt reused") } catch (_: IllegalStateException) { }
        }
    }
    @Test fun missingFieldsCannotBecomeAuthenticated() {
        ExperimentalNormalAuth("123456".toByteArray(), "own-id".toByteArray(), "test-phone", 1).use { attempt ->
            attempt.request()
            assertEquals(PeerCheck.MISSING_OR_INVALID_FIELDS,
                attempt.verify(ExperimentalAuthWire.Response(1793, null, null, null, null, null, 0, 1)))
        }
    }
}
