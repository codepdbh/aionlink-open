package org.aionlink.wifi

import com.google.protobuf.CodedInputStream
import com.google.protobuf.CodedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.math.BigInteger
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.nio.ByteBuffer
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import org.aionlink.iccoa.UCarFrameReader
import org.aionlink.iccoa.ExperimentalAuthCrypto
import org.junit.Assert.*
import org.junit.Test

class ExperimentalAuthExchangeTest {
    private fun encoded(key: ECPublicKey): ByteArray {
        fun coordinate(n: BigInteger): ByteArray {
            val raw = n.toByteArray(); val size = minOf(32, raw.size)
            return ByteArray(32).also { System.arraycopy(raw, raw.size - size, it, 32 - size, size) }
        }
        return coordinate(key.w.affineX) + coordinate(key.w.affineY)
    }
    private fun peer(socket: Socket, wrongPin: Boolean) {
        socket.soTimeout = 3000
        val stream = DataInputStream(socket.getInputStream())
        val header = ByteArray(20); stream.readFully(header)
        val decoded = UCarFrameReader.header(header)
        val body = ByteArray(decoded.totalBytes - 20); stream.readFully(body)
        val proto = CodedInputStream.newInstance(body)
        val values = mutableMapOf<Int, ByteArray>()
        var workModes = -1
        while (true) {
            val tag = proto.readTag(); if (tag == 0) break
            if (tag in listOf(18, 26, 34, 42, 50)) values[tag] = proto.readByteArray()
            else if (tag == 80) workModes = proto.readInt32() else proto.skipField(tag)
        }
        check(decoded.sequence == 1L && workModes == 257)
        val localAuth = values.getValue(18); val localAgreement = values.getValue(34); val localNonce = values.getValue(50)
        val clientKey = MessageDigest.getInstance("SHA-256").digest(localNonce + "123456".toByteArray())
        val expectedClientProof = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(clientKey, "HmacSHA256")); doFinal(localAuth) }
        check(MessageDigest.isEqual(expectedClientProof, values.getValue(26)))
        val clientAuth = ExperimentalAuthCrypto.decodePublicPoint(localAuth)
        check(Signature.getInstance("SHA256withECDSA").run {
            initVerify(clientAuth); update(localAgreement + localNonce); verify(values.getValue(42))
        })
        val generator = KeyPairGenerator.getInstance("EC").apply { initialize(ECGenParameterSpec("secp256r1")) }
        val auth = generator.generateKeyPair(); val agreement = generator.generateKeyPair()
        val peerAuth = encoded(auth.public as ECPublicKey); val peerAgreement = encoded(agreement.public as ECPublicKey)
        val peerNonce = ByteArray(32) { (it + 1).toByte() }
        val key = MessageDigest.getInstance("SHA-256").digest(peerNonce + localNonce + (if (wrongPin) "000000" else "123456").toByteArray())
        val proof = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(key, "HmacSHA256")); doFinal(peerAuth + localAuth) }
        val signature = Signature.getInstance("SHA256withECDSA").run {
            initSign(auth.private); update(peerAgreement + localAgreement + peerNonce + localNonce); sign()
        }
        val output = ByteArrayOutputStream(); val writer = CodedOutputStream.newInstance(output)
        writer.writeInt32(1, 1793); writer.writeByteArray(2, peerAuth); writer.writeByteArray(3, proof)
        writer.writeByteArray(4, peerAgreement); writer.writeByteArray(5, signature); writer.writeByteArray(6, peerNonce)
        // A successful default result may be omitted by a protobuf encoder.
        writer.flush()
        val replyBody = output.toByteArray()
        val reply = ByteBuffer.allocate(20 + replyBody.size).putInt(20 + replyBody.size).putInt(decoded.sequence.toInt())
            .putInt(1).put(18).put(3).putShort(2).putShort(0).putShort(0).put(replyBody).array()
        val crc = UCarFrameReader.crc16Arc(reply, 18); reply[18] = (crc ushr 8).toByte(); reply[19] = crc.toByte()
        socket.getOutputStream().write(reply)
        if (wrongPin) return
        // Independent peer-side derivation: ECDH, salt SHA-256(client nonce || peer nonce), HKDF info "session_key".
        val secret = KeyAgreement.getInstance("ECDH").run {
            init(agreement.private); doPhase(ExperimentalAuthCrypto.decodePublicPoint(localAgreement), true); generateSecret()
        }
        val salt = MessageDigest.getInstance("SHA-256").digest(localNonce + peerNonce)
        val prk = Mac.getInstance("HmacSHA256").run { init(SecretKeySpec(salt, "HmacSHA256")); doFinal(secret) }
        val sessionKey = Mac.getInstance("HmacSHA256").run {
            init(SecretKeySpec(prk, "HmacSHA256")); update("session_key".toByteArray()); doFinal(byteArrayOf(1))
        }.copyOf(16)
        val confirmHeader = ByteArray(20); stream.readFully(confirmHeader)
        val confirm = UCarFrameReader.header(confirmHeader)
        check(confirm.authMessageLabel == "AUTH_CONFIRM_STATIC_CANDIDATE" && confirm.source == 1 && confirm.sequence == 2L)
        val confirmBody = ByteArray(confirm.totalBytes - 20); stream.readFully(confirmBody)
        val confirmProto = CodedInputStream.newInstance(confirmBody)
        check(confirmProto.readTag() == 10)
        val envelope = ByteBuffer.wrap(confirmProto.readByteArray())
        val iv = ByteArray(envelope.int).also { envelope.get(it) }
        val sealed = ByteArray(envelope.int).also { envelope.get(it) }
        val plain = Cipher.getInstance("AES/GCM/NoPadding").run {
            init(Cipher.DECRYPT_MODE, SecretKeySpec(sessionKey, "AES"), GCMParameterSpec(128, iv)); doFinal(sealed)
        }
        check(String(plain) == socket.inetAddress.hostAddress + ":0")
    }
    @Test fun loopbackPeerAuthenticatesProofAndRejectsWrongPin() {
        for (wrong in listOf(false, true)) ServerSocket(0, 1, InetAddress.getLoopbackAddress()).use { server ->
            server.soTimeout = 3000
            val worker = Executors.newSingleThreadExecutor()
            try {
                val serverResult = worker.submit { server.accept().use { peer(it, wrong) } }
                val pin = "123456".toByteArray(); val id = "test-only".toByteArray()
                val result = ExperimentalAuthExchange.run(Socket(), InetAddress.getLoopbackAddress(), server.localPort, pin, id, "test-phone") { }
                assertEquals(if (wrong) "RESPONSE_BAD_PROOF; resultado=0; versión=EXPECTED; workModes=AUSENTE"
                    else "RESPONSE_VERIFIED_PROOF_AND_SIGNATURE; resultado=0; versión=EXPECTED; workModes=AUSENTE; " +
                        "CONFIRM_SENT; tras confirmación=CERRADO_POR_RECEPTOR", result)
                assertTrue(pin.all { it == 0.toByte() }); assertTrue(id.all { it == 0.toByte() })
                serverResult.get(4, TimeUnit.SECONDS)
            } finally { worker.shutdownNow() }
        }
    }
    @Test fun bindFailureDoesNotConnectAndClearsInputs() {
        val pin = "123456".toByteArray(); val id = "test-only".toByteArray()
        val result = ExperimentalAuthExchange.run(Socket(), InetAddress.getLoopbackAddress(), 1, pin, id, "test-phone") { throw java.io.IOException() }
        assertEquals("BIND_FAILED", result); assertTrue(pin.all { it == 0.toByte() }); assertTrue(id.all { it == 0.toByte() })
    }
}
