package org.aionlink.open

import android.app.Presentation
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaFormat
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.Surface
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

/**
 * Car mode: AionLink's own dashboard drawn on a private virtual display and encoded to H.264, with no screen capture.
 * The 6 Nov 2026 test showed such a display keeps drawing and encoding for 10 minutes with the phone screen off and
 * locked. Experimental external-content mode uses the same private display without the dashboard; access and
 * visibility of another app require Android approval and remain unverified. Touches are implemented for the dashboard only.
 */
class CarModeDisplay(
    private val context: Context, private val width: Int, private val height: Int, private val fps: Int, private val bitrate: Int,
    private val onAccessUnit: (ByteArray, Long, Boolean) -> Unit, private val onError: (String) -> Unit,
    private val externalContent: Boolean = false
) : AutoCloseable {
    companion object {
        /** Larger than the car's 160 dpi so text and buttons are comfortable at arm's length (1920x960 → 1280x640 dp). */
        const val DENSITY = 240
    }

    private val main = Handler(Looper.getMainLooper())
    private var codec: MediaCodec? = null
    private var surface: Surface? = null
    private var display: VirtualDisplay? = null
    private var presentation: Presentation? = null
    private var relay: SurfaceRelay? = null
    private var dashboard: CarDashboardView? = null
    private var downTime = 0L
    @Volatile private var running = false

    /** Main thread only. */
    fun start() {
        try {
            val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            codec = encoder
            encoder.configure(ScreenEncoder.format(width, height, fps, bitrate, repeatFrames = !externalContent), null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            val input = encoder.createInputSurface(); surface = input
            encoder.start()
            running = true
            Thread({ drain(encoder) }, "AionLink-car-h264").apply { isDaemon = true; start() }
            // In direct mode the other app is the sole producer for the encoder's Surface.
            // External content: the producer draws into a GL relay that rescales into the encoder surface.
            if (externalContent) { relay = SurfaceRelay(input, width, height); return }
            val virtual = context.getSystemService(DisplayManager::class.java).createVirtualDisplay(
                "AionLink-car", width, height, DENSITY, input,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY or DisplayManager.VIRTUAL_DISPLAY_FLAG_PRESENTATION
            ) ?: error("No virtual display")
            display = virtual
            presentation = if (!externalContent) object : Presentation(context, virtual.display) {
                override fun onCreate(savedInstanceState: Bundle?) {
                    super.onCreate(savedInstanceState)
                    setContentView(CarDashboardView(getContext()).also { dashboard = it })
                }
                override fun onStop() { dashboard?.release(); super.onStop() }
            }.also { it.show() }
            // External content: a black placeholder window makes AionLink present on the display while the other
            // app's activity is opened; it is removed afterwards because presentation windows sit above activities.
            else Presentation(context, virtual.display).apply {
                setContentView(View(context).apply { setBackgroundColor(android.graphics.Color.BLACK) })
                show()
            }
        } catch (error: Exception) {
            close()
            throw error
        }
    }

    /** Only the display owner can request launching a consenting activity on this private display. */
    fun externalDisplayId(): Int? = if (externalContent) display?.display?.displayId else null

    fun externalInputSurface(): Surface? = if (externalContent) relay?.input else null

    /** Frames the GL relay passed to the encoder (external content only). */
    val relayedFrames: Long get() = relay?.framesDrawn ?: 0
    fun isExternalContent(): Boolean = externalContent

    /** External content only: drop the placeholder once the other app's activity is on the display. Main thread. */
    fun removePlaceholder() {
        if (externalContent) { presentation?.dismiss(); presentation = null }
    }

    fun requestKeyframe() {
        codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
    }

    /** UIBC touch in the encoded frame, which is the virtual display itself. Main thread only. */
    fun touch(action: Int, x: Float, y: Float) {
        val root = presentation?.window?.decorView ?: return
        val now = SystemClock.uptimeMillis()
        if (action and 0xFF == MotionEvent.ACTION_DOWN) downTime = now
        val event = MotionEvent.obtain(downTime, now, action and 0xFF, x, y, 0)
        event.source = android.view.InputDevice.SOURCE_TOUCHSCREEN
        root.dispatchTouchEvent(event)
        event.recycle()
    }

    /** The car's back key closes nothing yet; it is accepted so it never reaches the phone. */
    fun key(keyCode: Int) = Unit

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        var config: ByteArray? = null
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                try {
                    val buffer = encoder.getOutputBuffer(index)
                    if (buffer != null && info.size > 0) {
                        val bytes = ByteArray(info.size)
                        buffer.position(info.offset); buffer.limit(info.offset + info.size); buffer.get(bytes)
                        if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) config = bytes
                        else {
                            val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                            val csd = config
                            onAccessUnit(if (key && csd != null && !startsWithSps(bytes)) csd + bytes else bytes, info.presentationTimeUs, key)
                        }
                    }
                } finally { encoder.releaseOutputBuffer(index, false) }
            }
        } catch (error: Exception) { if (running) onError("CAR_ENCODER_${error.javaClass.simpleName}") }
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
        relay?.close(); relay = null
        presentation?.dismiss(); presentation = null
        dashboard = null
        display?.release(); display = null
        surface?.release(); surface = null
        codec?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }; codec = null
    }
}

/** The car dashboard: clock, battery, media with controls and the current navigation instruction. Plain Views. */
class CarDashboardView(context: Context) : LinearLayout(context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private val clock = text(110f, bold = true)
    private val date = text(30f)
    private val battery = text(26f, color = 0xFF9FB3C8.toInt())
    private val mediaApp = text(22f, color = 0xFF9FB3C8.toInt())
    private val mediaTitle = text(40f, bold = true)
    private val mediaArtist = text(28f, color = 0xFFCBD5E1.toInt())
    private val playButton = button("▶") { CarInfoListener.playPause() }
    private val navIcon = ImageView(context).apply { layoutParams = LayoutParams(dp(120), dp(120)) }
    private val navTitle = text(40f, bold = true)
    private val navText = text(28f, color = 0xFFCBD5E1.toInt())
    private val tick = object : Runnable {
        override fun run() { updateClock(); postDelayed(this, 1000) }
    }

    init {
        orientation = HORIZONTAL
        setBackgroundColor(0xFF0B0F14.toInt())
        setPadding(dp(32), dp(32), dp(32), dp(32))
        addView(column(1f, clock, date, battery, text(20f, color = 0xFF64748B.toInt()).apply { text = "AionLink · Modo coche" }))
        addView(card(1.3f).apply {
            addView(mediaApp); addView(mediaTitle); addView(mediaArtist)
            addView(LinearLayout(context).apply {
                orientation = HORIZONTAL; gravity = Gravity.CENTER; setPadding(0, dp(24), 0, 0)
                addView(button("⏮") { CarInfoListener.previous() }); addView(playButton); addView(button("⏭") { CarInfoListener.next() })
            })
        })
        addView(card(1.3f).apply { addView(navIcon); addView(navTitle); addView(navText) })
        post(tick)
        scope.launch { CarInfoListener.info.collect(::render) }
    }

    fun release() { removeCallbacks(tick); scope.cancel() }

    private fun render(info: CarInfo) {
        val access = CarInfoListener.granted(context)
        mediaApp.text = info.mediaApp ?: "Música"
        mediaTitle.text = info.mediaTitle ?: if (access) "Nada en reproducción" else "Activa el acceso a notificaciones en AionLink"
        mediaArtist.text = info.mediaArtist ?: ""
        playButton.text = if (info.mediaPlaying) "⏸" else "▶"
        navTitle.text = info.navTitle ?: "Sin navegación activa"
        navText.text = info.navText ?: if (access) "Inicia una ruta en Maps o Waze en el teléfono" else ""
        navIcon.setImageBitmap(info.navIcon)
        navIcon.visibility = if (info.navIcon != null) View.VISIBLE else View.GONE
    }

    private fun updateClock() {
        val now = Date()
        clock.text = DateFormat.getTimeInstance(DateFormat.SHORT).format(now)
        date.text = DateFormat.getDateInstance(DateFormat.FULL).format(now)
        val status = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = status?.let {
            val raw = it.getIntExtra(BatteryManager.EXTRA_LEVEL, -1); val scale = it.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
            if (raw >= 0 && scale > 0) raw * 100 / scale else null
        }
        val charging = status?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)?.let { it != 0 } == true
        battery.text = level?.let { "Teléfono: $it %" + if (charging) " · cargando" else "" } ?: ""
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    private fun text(size: Float, bold: Boolean = false, color: Int = Color.WHITE) = TextView(context).apply {
        textSize = size; setTextColor(color); maxLines = 2
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun button(label: String, action: () -> Unit) = TextView(context).apply {
        text = label; textSize = 52f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
        background = GradientDrawable().apply { cornerRadius = dp(48).toFloat(); setColor(0xFF1E293B.toInt()) }
        layoutParams = LayoutParams(dp(130), dp(110)).apply { setMargins(dp(12), 0, dp(12), 0) }
        isClickable = true
        setOnClickListener { action() }
    }

    private fun column(weight: Float, vararg views: View) = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER_VERTICAL
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight)
        views.forEach(::addView)
    }

    private fun card(weight: Float) = LinearLayout(context).apply {
        orientation = VERTICAL; gravity = Gravity.CENTER
        setPadding(dp(28), dp(28), dp(28), dp(28))
        background = GradientDrawable().apply { cornerRadius = dp(28).toFloat(); setColor(0xFF111827.toInt()) }
        layoutParams = LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, weight).apply { setMargins(dp(16), 0, 0, 0) }
    }
}
