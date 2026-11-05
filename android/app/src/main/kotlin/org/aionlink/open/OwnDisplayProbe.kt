package org.aionlink.open

import android.app.KeyguardManager
import android.app.Presentation
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.PowerManager
import android.view.Surface
import android.view.View
import java.util.concurrent.atomic.AtomicLong

/**
 * Feasibility probe for a screen of AionLink's own, reporting frames with the phone's screen and lock state. It compares
 * an animated view on a private virtual display, which follows the display's vsync, with a thread drawing straight
 * into a surface, as a stream's encoder input could be fed. It needs no capture consent and nothing goes to the car.
 */
class OwnDisplayProbe(
    private val context: Context, private val width: Int = 1280, private val height: Int = 720,
    private val target: Surface? = null, private val onReport: (String) -> Unit = {}
) : AutoCloseable {
    private companion object {
        const val REPORT_MS = 5000L
        const val DRAW_INTERVAL_MS = 33L
        const val WAKE_LOCK_MS = 12 * 60_000L
    }
    private val main = Handler(Looper.getMainLooper())
    private val frames = AtomicLong()
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var reportedFrames = 0L
    private val drawnFrames = AtomicLong()
    private var reportedDrawn = 0L
    private var drawReader: ImageReader? = null
    private var drawThread: Thread? = null
    private var drawCallbacks: HandlerThread? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var encoderProbe: DirectEncoderProbe? = null
    private var reportedEncoded = 0L
    private var reportedProduced = 0L
    @Volatile private var drawing = false
    val framesRendered get() = frames.get()
    val framesDrawn get() = drawnFrames.get()
    val framesEncoded get() = encoderProbe?.encoded?.get() ?: 0L
    val framesSubmitted get() = encoderProbe?.produced?.get() ?: 0L
    val timestampRegressions get() = encoderProbe?.timestampRegressions?.get() ?: 0L

    private val report = object : Runnable {
        override fun run() {
            val now = frames.get()
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val drawn = drawnFrames.get()
            val encoded = framesEncoded
            val produced = framesSubmitted
            val elapsed = encoderProbe?.lastEncodedElapsed?.get() ?: 0L
            val sinceLast = if (elapsed == 0L) -1 else android.os.SystemClock.elapsedRealtime() - elapsed
            onReport("Sonda de pantalla propia: en ${REPORT_MS / 1000}s vista=${now - reportedFrames}; dibujo directo=${drawn - reportedDrawn}; cuadros al encoder=${produced - reportedProduced}; H264=${encoded - reportedEncoded}; último H264 hace ms=$sinceLast; reloj H264 no creciente=$timestampRegressions; interactivo=$interactive; bloqueado=$locked")
            reportedFrames = now
            reportedDrawn = drawn
            reportedEncoded = encoded
            reportedProduced = produced
            main.postDelayed(this, REPORT_MS)
        }
    }

    /** Call on the main thread. */
    fun start() {
        val surface = target ?: ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2).also { images ->
            reader = images
            images.setOnImageAvailableListener({ source -> source.acquireLatestImage()?.close(); frames.incrementAndGet() }, main)
        }.surface
        val virtual = context.getSystemService(DisplayManager::class.java).createVirtualDisplay("AionLink-sonda", width, height, 160, surface,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION)
        display = virtual
        presentation = object : Presentation(context, virtual.display) {
            override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContentView(Animated(getContext())) }
        }.also { it.show() }
        if (target == null) {
            startDrawing()
            encoderProbe = DirectEncoderProbe(width, height) { error -> main.post { onReport("Sonda H264: $error") } }
            encoderProbe?.start()
        }
        main.postDelayed(report, REPORT_MS)
    }

    /** The wake lock keeps the CPU running with the screen off; a stream of this kind would need the same. */
    private fun startDrawing() {
        wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AionLink:sonda").also { it.acquire(WAKE_LOCK_MS) }
        val callbacks = HandlerThread("AionLink-sonda-lector").also { it.start() }
        drawCallbacks = callbacks
        val images = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        drawReader = images
        images.setOnImageAvailableListener({ source -> source.acquireLatestImage()?.close(); drawnFrames.incrementAndGet() }, Handler(callbacks.looper))
        val surface = images.surface
        drawing = true
        drawThread = Thread({
            var tick = 0
            try {
                while (drawing) {
                    val canvas = surface.lockCanvas(null)
                    canvas.drawColor(if (++tick % 2 == 0) Color.DKGRAY else Color.BLUE)
                    surface.unlockCanvasAndPost(canvas)
                    Thread.sleep(DRAW_INTERVAL_MS)
                }
            } catch (_: InterruptedException) { } catch (_: RuntimeException) { if (drawing) onReport("Sonda: el dibujo directo se detuvo con un error") }
        }, "AionLink-sonda-dibujo").apply { isDaemon = true; start() }
    }

    override fun close() {
        main.removeCallbacks(report)
        encoderProbe?.close(); encoderProbe = null
        presentation?.dismiss(); presentation = null
        display?.release(); display = null
        reader?.close(); reader = null
        drawing = false
        drawThread?.interrupt(); drawThread?.join(500); drawThread = null
        drawReader?.close(); drawReader = null
        drawCallbacks?.quitSafely(); drawCallbacks = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }

    private class Animated(context: Context) : View(context) {
        private var tick = 0
        override fun onDraw(canvas: Canvas) {
            tick++
            canvas.drawColor(if (tick % 2 == 0) Color.DKGRAY else Color.BLUE)
            postInvalidateOnAnimation()
        }
    }
}
