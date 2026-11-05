package org.aionlink.open

import android.app.KeyguardManager
import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import android.view.Surface
import android.view.View
import java.util.concurrent.atomic.AtomicLong

/** Local, private display containing only our test pattern; no DeX, capture or network. */
class VirtualDisplayVideoProbe(private val context: Context, private val report: (String) -> Unit = {}) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val drawn = AtomicLong()
    private val encoded = AtomicLong()
    private val lastOutput = AtomicLong()
    private var lastDrawn = 0L
    private var lastEncoded = 0L
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    val drawnFrames get() = drawn.get()
    val encodedFrames get() = encoded.get()

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val nowDrawn = drawn.get(); val nowEncoded = encoded.get()
            val elapsed = lastOutput.get()
            val age = if (elapsed == 0L) -1 else SystemClock.elapsedRealtime() - elapsed
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            report("Pantalla virtual propia: intervalo=5s; dibujados=${nowDrawn - lastDrawn}; H264=${nowEncoded - lastEncoded}; último H264 hace ms=$age; interactivo=$interactive; bloqueado=$locked; no es DeX")
            lastDrawn = nowDrawn; lastEncoded = nowEncoded
            handler.postDelayed(this, 5000)
        }
    }

    /** Main thread only; the probe uses a private OWN_CONTENT_ONLY display. */
    fun start() {
        check(codec == null)
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            encoder.configure(ScreenEncoder.format(1280, 720, 30), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface(); surface = input
            encoder.start()
            wakeLock = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AionLink:virtual-probe").also { it.acquire(12 * 60_000L) }
            running = true
            worker = Thread({
                val info = MediaCodec.BufferInfo()
                try {
                    while (running) {
                        val index = encoder.dequeueOutputBuffer(info, 100_000)
                        if (index >= 0) {
                            if (info.size > 0 && info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG == 0) {
                                encoded.incrementAndGet(); lastOutput.set(SystemClock.elapsedRealtime())
                            }
                            encoder.releaseOutputBuffer(index, false)
                        }
                    }
                } catch (error: Exception) {
                    if (running) handler.post { if (running) report("Pantalla virtual propia: error H264 ${error.javaClass.simpleName}") }
                }
            }, "AionLink-virtual-h264").apply { isDaemon = true; start() }
            val virtual = context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "AionLink-virtual-probe", 1280, 720, 160, input,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            ) ?: error("No virtual display")
            display = virtual
            presentation = object : Presentation(context, virtual.display) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(object : View(getContext()) {
                        override fun onDraw(canvas: Canvas) {
                            canvas.drawColor(if (drawn.incrementAndGet() % 2L == 0L) Color.BLUE else Color.DKGRAY)
                            postInvalidateOnAnimation()
                        }
                    })
                }
            }
            presentation?.show()
            handler.postDelayed(tick, 5000)
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    /** Main thread only. */
    override fun close() {
        running = false
        handler.removeCallbacks(tick)
        presentation?.dismiss(); presentation = null
        display?.release(); display = null
        worker?.join(1000); worker = null
        surface?.release(); surface = null
        codec?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }; codec = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }
}
