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
import android.view.Display
import org.aionlink.projection.ExperimentalEncodedBridge
import org.aionlink.projection.TsRtpStreamer
import java.util.concurrent.atomic.AtomicLong

/** Local, private display containing only our test pattern; no DeX, capture or network. */
class VirtualDisplayVideoProbe(
    private val context: Context, private val width: Int = 1920, private val height: Int = 960,
    private val report: (String) -> Unit = {}
) : AutoCloseable {
    private val handler = Handler(Looper.getMainLooper())
    private val drawn = AtomicLong()
    private val encoded = AtomicLong()
    private val lastOutput = AtomicLong()
    private val muxed = AtomicLong()
    private val packets = AtomicLong()
    private val rejected = AtomicLong()
    private val malformedPackets = AtomicLong()
    private var lastDrawn = 0L
    private var lastEncoded = 0L
    private var lastReportElapsed = 0L
    private var lastMuxed = 0L
    private var lastPackets = 0L
    private var bridge: ExperimentalEncodedBridge? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var surface: Surface? = null
    private var codec: MediaCodec? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var worker: Thread? = null
    @Volatile private var running = false
    val drawnFrames get() = drawn.get()
    val encodedFrames get() = encoded.get()
    val muxedFrames get() = muxed.get()
    val rtpPackets get() = packets.get()
    val invalidPackets get() = malformedPackets.get()

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val nowDrawn = drawn.get(); val nowEncoded = encoded.get()
            val nowMuxed = muxed.get(); val nowPackets = packets.get()
            val elapsed = lastOutput.get()
            val now = SystemClock.elapsedRealtime()
            val age = if (elapsed == 0L) -1 else now - elapsed
            val interval = now - lastReportElapsed
            val interactive = context.getSystemService(PowerManager::class.java).isInteractive
            val locked = context.getSystemService(KeyguardManager::class.java).isKeyguardLocked
            val physicalState = when (context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)?.state) {
                Display.STATE_ON -> "ON"
                Display.STATE_OFF -> "OFF"
                Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> "DOZE"
                else -> "OTHER_OR_UNKNOWN"
            }
            val battery = context.registerReceiver(null, android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED))
            val level = battery?.let { b ->
                val raw = b.getIntExtra(android.os.BatteryManager.EXTRA_LEVEL, -1); val scale = b.getIntExtra(android.os.BatteryManager.EXTRA_SCALE, -1)
                if (raw >= 0 && scale > 0) "${raw * 100 / scale}%" else "?"
            } ?: "?"
            val temperature = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
                ?.takeIf { it != Int.MIN_VALUE }?.let { "%.1f°C".format(it / 10f) } ?: "?"
            report("Pantalla virtual propia ${width}x$height: intervalo ms=$interval; dibujados=${nowDrawn - lastDrawn}; H264=${nowEncoded - lastEncoded}; TS/RTP cuadros=${nowMuxed - lastMuxed}; paquetes=${nowPackets - lastPackets}; descartados=${rejected.get()}; paquetes inválidos=${malformedPackets.get()}; último H264 hace ms=$age; estado al final: pantalla principal=$physicalState; interactivo=$interactive; bloqueado=$locked; batería=$level; temperatura=$temperature; servicio en primer plano=${VirtualProbeService.running.value}; no es DeX")
            lastDrawn = nowDrawn; lastEncoded = nowEncoded
            lastMuxed = nowMuxed; lastPackets = nowPackets
            lastReportElapsed = now
            handler.postDelayed(this, 5000)
        }
    }

    /** Main thread only; the probe uses a private OWN_CONTENT_ONLY display. */
    fun start() {
        check(codec == null)
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            encoder.configure(ScreenEncoder.format(width, height, 30), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface(); surface = input
            encoder.start()
            wakeLock = context.getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AionLink:virtual-probe").also { it.acquire(12 * 60_000L) }
            running = true
            lastReportElapsed = SystemClock.elapsedRealtime()
            val localBridge = ExperimentalEncodedBridge { audio ->
                TsRtpStreamer({ datagram ->
                    // Local in-memory sink: no IP, socket, vehicle or screen content is recorded.
                    val valid = datagram.size > 12 && (datagram.size - 12) % 188 == 0 &&
                        datagram[0].toInt() and 0xc0 == 0x80 && datagram[1].toInt() and 0x7f == 33 &&
                        (12 until datagram.size step 188).all { datagram[it].toInt() and 0xff == 0x47 }
                    if (!valid) malformedPackets.incrementAndGet()
                    packets.incrementAndGet()
                }, audio = audio)
            }
            bridge = localBridge
            val session = localBridge.start(audio = false)
            worker = Thread({
                val info = MediaCodec.BufferInfo()
                var codecConfig: ByteArray? = null
                try {
                    while (running) {
                        val index = encoder.dequeueOutputBuffer(info, 100_000)
                        if (index >= 0) {
                            try {
                                val buffer = encoder.getOutputBuffer(index)
                                if (buffer != null && info.size > 0) {
                                    val bytes = ByteArray(info.size)
                                    buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.get(bytes)
                                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) codecConfig = bytes
                                    else {
                                        encoded.incrementAndGet(); lastOutput.set(SystemClock.elapsedRealtime())
                                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                                        val config = codecConfig
                                        val payload = if (key && config != null && !startsWithSps(bytes)) config + bytes else bytes
                                        val result = localBridge.video(session, payload, info.presentationTimeUs, key)
                                        if (result == ExperimentalEncodedBridge.Result.ACCEPTED) muxed.incrementAndGet() else rejected.incrementAndGet()
                                    }
                                }
                            } finally {
                                encoder.releaseOutputBuffer(index, false)
                            }
                        }
                    }
                } catch (error: Exception) {
                    if (running) handler.post { if (running) report("Pantalla virtual propia: error H264 ${error.javaClass.simpleName}") }
                }
            }, "AionLink-virtual-h264").apply { isDaemon = true; start() }
            val virtual = context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "AionLink-virtual-probe", width, height, 160, input,
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

    private fun startsWithSps(bytes: ByteArray): Boolean {
        val offset = when {
            bytes.size > 4 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 1.toByte() -> 3
            bytes.size > 5 && bytes[0] == 0.toByte() && bytes[1] == 0.toByte() && bytes[2] == 0.toByte() && bytes[3] == 1.toByte() -> 4
            else -> return false
        }
        return bytes[offset].toInt() and 0x1f == 7
    }

    /** Main thread only. */
    override fun close() {
        running = false
        handler.removeCallbacks(tick)
        presentation?.dismiss(); presentation = null
        display?.release(); display = null
        worker?.join(1000); worker = null
        bridge?.close(); bridge = null
        surface?.release(); surface = null
        codec?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }; codec = null
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
    }
}
