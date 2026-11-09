package org.aionlink.open

import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.IOException

/**
 * Receives the decoded Android Auto sound that a compatible Open Headunit writes into a pipe (48 kHz stereo PCM16,
 * continuous, silence included) instead of playing it on the phone, and encodes it with [AacPump] for the car.
 * [writeEnd] is handed to Open Headunit with the video surface; AionLink keeps only the read end.
 */
class BridgeAudio(
    onFrame: (ByteArray, Long) -> Unit,
    onError: (String) -> Unit,
    onLevel: (Int) -> Unit = {},
) : AutoCloseable {
    private val pipe = ParcelFileDescriptor.createPipe()
    private val input = FileInputStream(pipe[0].fileDescriptor)
    private var chunk = ByteArray(4096)
    @Volatile private var closed = false
    @Volatile var bytesReceived = 0L
        private set
    val writeEnd: ParcelFileDescriptor get() = pipe[1]

    private val pump = AacPump({ buffer, size -> read(buffer, size) }, onFrame, { if (!closed) onError(it) }, onLevel)

    fun start() = pump.start()

    /** Fills whole stereo frames (4 bytes) so samples never split across encoder buffers. */
    private fun read(buffer: java.nio.ByteBuffer, size: Int): Int {
        val wanted = size - size % 4
        if (chunk.size < wanted) chunk = ByteArray(wanted)
        return try {
            var count = input.read(chunk, 0, wanted)
            if (count < 0) return -1
            while (count % 4 != 0) {
                val more = input.read(chunk, count, 4 - count % 4)
                if (more < 0) return -1
                count += more
            }
            buffer.put(chunk, 0, count)
            bytesReceived += count
            count
        } catch (_: IOException) { -1 }
    }

    /** After the descriptor was sent, the sender's copy must be closed so the reader sees the end of stream. */
    fun releaseWriteEnd() { try { pipe[1].close() } catch (_: IOException) { } }

    override fun close() {
        closed = true
        pump.close()
        releaseWriteEnd()
        try { pipe[0].close() } catch (_: IOException) { }
    }
}
