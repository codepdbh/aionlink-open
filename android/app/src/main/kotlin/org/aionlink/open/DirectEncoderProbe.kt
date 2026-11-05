package org.aionlink.open

import android.graphics.Color
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.SystemClock
import android.view.Surface
import java.util.concurrent.atomic.AtomicLong

/** Independent synthetic source. No screen capture, network, or user content. */
internal class DirectEncoderProbe(private val width: Int, private val height: Int, private val onError: (String) -> Unit) : AutoCloseable {
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var drawingThread: Thread? = null
    private var drainingThread: Thread? = null
    @Volatile private var running = false
    val produced = AtomicLong()
    val encoded = AtomicLong()
    val lastEncodedElapsed = AtomicLong()
    val timestampRegressions = AtomicLong()

    fun start() {
        check(codec == null)
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            encoder.configure(ScreenEncoder.format(width, height, 30), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface()
            surface = input
            encoder.start()
            running = true
            drainingThread = Thread({ drain(encoder) }, "AionLink-sonda-h264").apply { isDaemon = true; start() }
            drawingThread = Thread({ draw(input) }, "AionLink-sonda-h264-dibujo").apply { isDaemon = true; start() }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    private fun draw(input: Surface) {
        try {
            while (running) {
                val canvas = input.lockCanvas(null)
                val number = produced.get() + 1
                canvas.drawColor(if (number % 2L == 0L) Color.DKGRAY else Color.BLUE)
                input.unlockCanvasAndPost(canvas)
                produced.incrementAndGet()
                Thread.sleep(33)
            }
        } catch (_: InterruptedException) {
            Thread.currentThread().interrupt()
        } catch (error: Exception) {
            if (running) onError("DIBUJO_${error.javaClass.simpleName}")
        }
    }

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var previous = -1L
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                try {
                    if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                        if (previous >= 0 && info.presentationTimeUs <= previous) timestampRegressions.incrementAndGet()
                        previous = info.presentationTimeUs
                        encoded.incrementAndGet()
                        lastEncodedElapsed.set(SystemClock.elapsedRealtime())
                    }
                } finally { encoder.releaseOutputBuffer(index, false) }
            }
        } catch (error: Exception) {
            if (running) onError("H264_${error.javaClass.simpleName}")
        }
    }

    override fun close() {
        running = false
        drawingThread?.interrupt()
        drawingThread?.join(1000); drawingThread = null
        drainingThread?.join(1000); drainingThread = null
        surface?.release(); surface = null
        codec?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }
        codec = null
    }
}
