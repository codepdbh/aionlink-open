package org.aionlink.iccoa

import java.nio.ByteBuffer
import java.nio.ByteOrder

data class UsbControlMetadata(val knownPort: Int?, val channelId: Int, val role: Int, val messageType: Int)
data class UsbPacketMetadata(val channel: Int, val declaredBytes: Int, val control: UsbControlMetadata?)

/** Independent incremental decoder of the static format documented in USB_AOA_OBSERVED.md.
 * Payload is never returned or logged. Limits are local policy, not receiver capabilities.
 */
class UsbMuxDecoder(private val maxPayload: Int = 1024 * 1024) {
    private var header = ByteArray(8)
    private var headerCount = 0
    private var channel = 0
    private var declared = 0
    private var wireRemaining = 0
    private var payloadCount = 0
    private val controlBytes = ByteArray(10)
    init { require(maxPayload >= 10) }
    val hasPartialFrame get() = headerCount != 0 || wireRemaining != 0

    fun accept(chunk: ByteArray, count: Int = chunk.size): List<UsbPacketMetadata> {
        require(count in 0..chunk.size)
        val output = mutableListOf<UsbPacketMetadata>()
        var position = 0
        while (position < count) {
            if (headerCount < 8) {
                val n = minOf(8 - headerCount, count - position)
                chunk.copyInto(header, headerCount, position, position + n)
                headerCount += n; position += n
                if (headerCount < 8) continue
                val fields = ByteBuffer.wrap(header).order(ByteOrder.BIG_ENDIAN)
                channel = fields.int
                val length = fields.int.toLong() and 0xffffffffL
                require(channel in 1..240) { "Invalid USB mux channel" }
                require(length <= maxPayload) { "USB mux payload exceeds local limit" }
                declared = length.toInt()
                wireRemaining = maxOf(declared, 8)
                payloadCount = 0
                controlBytes.fill(0)
            }
            val n = minOf(wireRemaining, count - position)
            if (channel == 1 && declared == 10 && payloadCount < 10) {
                val copy = minOf(n, 10 - payloadCount)
                chunk.copyInto(controlBytes, payloadCount, position, position + copy)
            }
            payloadCount += n; wireRemaining -= n; position += n
            if (wireRemaining == 0) {
                val control = if (channel == 1 && declared == 10) {
                    val fields = ByteBuffer.wrap(controlBytes).order(ByteOrder.BIG_ENDIAN)
                    val port = fields.int
                    UsbControlMetadata(port.takeIf { it in KNOWN_PORTS }, fields.int,
                        fields.get().toInt() and 255, fields.get().toInt() and 255)
                } else null
                output += UsbPacketMetadata(channel, declared, control)
                headerCount = 0
            }
        }
        return output
    }
    companion object {
        val KNOWN_PORTS = setOf(4321,7236,15550,57209,57219,57229,57239,57249,57259)
    }
}

object UsbAccessoryIdentity {
    fun isObservedIccoa(manufacturer: String?, model: String?, version: String?) =
        manufacturer == "ICCOA" && model == "CarLink" && version == "1.0.0"
}

/** Experimental startup bytes inferred from static client code, not a captured AION exchange. */
object UsbChannelStartup {
    fun request(port: Int): Pair<ByteArray, ByteArray> {
        require(port == 7236 || port == 4321) { "Only observed startup ports are supported" }
        val header = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putInt(1).putInt(10).array()
        val body = ByteBuffer.allocate(10).order(ByteOrder.BIG_ENDIAN).putInt(port).putInt(-1).put(1).put(2).array()
        return header to body
    }
}
