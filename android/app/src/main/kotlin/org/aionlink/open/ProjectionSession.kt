package org.aionlink.open

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.view.WindowManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.iccoa.ExperimentalAuthCrypto
import org.aionlink.projection.TsRtpStreamer
import org.aionlink.projection.UibcEvent
import org.aionlink.projection.UibcMapper
import org.aionlink.projection.UibcServer
import org.aionlink.projection.WfdFormats
import org.aionlink.projection.WfdPlay
import org.aionlink.projection.WfdSource
import org.aionlink.projection.WfdStatus
import org.aionlink.wifi.ControlChannelStatus
import org.aionlink.wifi.SessionControlServer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress

data class StreamStatus(val stage: String = "IDLE", val detail: String = "", val accessUnits: Long = 0,
                        val datagrams: Long = 0, val audioFrames: Long = 0, val audio: String = "IDLE")
data class TouchStatus(val stage: String = "IDLE", val events: Int = 0, val injected: Int = 0, val outside: Int = 0)

/**
 * Post-AUTH session observed in Samsung: phone-side CONTROL listener (57219), passive MEDIA (57229) and
 * SENSOR (57239) listeners, UIBC touch input (4321) and a Wi-Fi Display source (RTSP 7236, RTP server port
 * 15550). Video/audio come from the owner-approved MediaProjection. Status values are exported without
 * addresses, keys, coordinates or payloads.
 */
class ProjectionSession(private val context: Context, private val event: (String) -> Unit) {
    private val mutableControl = MutableStateFlow(ControlChannelStatus())
    val control = mutableControl.asStateFlow()
    private val mutableAux = MutableStateFlow("MEDIA IDLE • SENSOR IDLE")
    val auxiliary = mutableAux.asStateFlow()
    private val mutableRtsp = MutableStateFlow(WfdStatus())
    val rtsp = mutableRtsp.asStateFlow()
    private val mutableStream = MutableStateFlow(StreamStatus())
    val stream = mutableStream.asStateFlow()
    private val mutableTouch = MutableStateFlow(TouchStatus())
    val touch = mutableTouch.asStateFlow()
    private val main = Handler(Looper.getMainLooper())
    @Volatile private var controlServer: SessionControlServer? = null
    @Volatile private var mediaServer: SessionControlServer? = null
    @Volatile private var sensorServer: SessionControlServer? = null
    @Volatile private var uibcServer: UibcServer? = null
    @Volatile private var source: WfdSource? = null
    @Volatile private var encoder: ScreenEncoder? = null
    @Volatile private var audioCapture: AudioCapture? = null
    @Volatile private var datagrams: DatagramSocket? = null
    @Volatile private var localAddress: Inet4Address? = null
    @Volatile private var videoWidth = 0
    @Volatile private var videoHeight = 0
    @Volatile private var uibcKey: ByteArray? = null
    @Volatile private var mediaStage = "IDLE"
    @Volatile private var sensorStage = "IDLE"
    val active get() = controlServer != null || source != null

    /** Called on the AUTH thread before AuthConfirm, so all listeners exist when the receiver connects. */
    fun onAuthenticated(local: Inet4Address, sessionKey: ByteArray) {
        stop(null)
        localAddress = local
        uibcKey = sessionKey.copyOf()
        fun passive(port: Int, label: String, assign: (SessionControlServer) -> Unit) = try {
            SessionControlServer(local, sessionKey.copyOf(), port, active = false) { status ->
                if (label == "MEDIA") mediaStage = status.stage else sensorStage = status.stage
                val text = "MEDIA $mediaStage • SENSOR $sensorStage"
                if (mutableAux.value != text) { mutableAux.value = text; event("Canal $label: ${status.stage}") }
            }.also { it.start(); assign(it) }
        } catch (_: java.io.IOException) { event("Canal $label: BIND_FAILED") }
        passive(SessionControlServer.MEDIA_PORT, "MEDIA") { mediaServer = it }
        passive(SessionControlServer.SENSOR_PORT, "SENSOR") { sensorServer = it }
        try {
            SessionControlServer(local, sessionKey, onStatus = { status ->
                if (mutableControl.value.stage != status.stage) event("Canal CONTROL: ${status.stage}")
                mutableControl.value = status
            }).also { it.start(); controlServer = it }
        } catch (_: java.io.IOException) { sessionKey.fill(0); mutableControl.value = ControlChannelStatus("BIND_FAILED") }
        try {
            UibcServer(local, onEvent = ::onUibc, onStatus = { stage ->
                if (mutableTouch.value.stage != stage) event("Táctil UIBC: $stage")
                mutableTouch.value = mutableTouch.value.copy(stage = stage)
            }, decrypt = { data -> uibcKey?.let { key -> try { ExperimentalAuthCrypto.sessionOpen(key, data) } catch (_: Exception) { null } } })
                .also { it.start(); uibcServer = it }
        } catch (_: java.io.IOException) { mutableTouch.value = TouchStatus("BIND_FAILED") }
        try {
            WfdSource(local, onStatus = { status ->
                if (mutableRtsp.value.stage != status.stage) event("RTSP WFD: ${status.stage} ${status.detail}".trim())
                mutableRtsp.value = status
            }, onPlay = ::play, onIdrRequest = { encoder?.requestKeyframe() }, onStop = { reason -> stopStream("RTSP_$reason") })
                .also { it.start(); source = it }
        } catch (_: java.io.IOException) { mutableRtsp.value = WfdStatus("BIND_FAILED") }
    }

    private fun onUibc(input: UibcEvent) {
        val counted = mutableTouch.value.copy(events = mutableTouch.value.events + 1)
        mutableTouch.value = counted
        main.post {
            val service = TouchInjectionService.instance
            if (service == null) { mutableTouch.value = mutableTouch.value.copy(stage = "SIN_ACCESIBILIDAD"); return@post }
            when (input) {
                is UibcEvent.Key -> service.key(input)
                is UibcEvent.Touch -> {
                    val pointer = input.pointers.firstOrNull() ?: return@post
                    // The car reports coordinates in the negotiated video frame; fall back to its stated size.
                    val width = videoWidth.takeIf { it > 0 } ?: input.width
                    val height = videoHeight.takeIf { it > 0 } ?: input.height
                    val metrics = android.util.DisplayMetrics()
                    @Suppress("DEPRECATION")
                    context.getSystemService(WindowManager::class.java).defaultDisplay.getRealMetrics(metrics)
                    val mapped = UibcMapper.map(pointer.x, pointer.y, width, height, metrics.widthPixels, metrics.heightPixels)
                    if (mapped == null) { mutableTouch.value = mutableTouch.value.copy(outside = mutableTouch.value.outside + 1); return@post }
                    service.touch(input.action, mapped.first, mapped.second)
                    mutableTouch.value = mutableTouch.value.copy(injected = mutableTouch.value.injected + 1)
                }
            }
        }
    }

    private fun play(play: WfdPlay) {
        val granted = ProjectionService.projection
        if (granted == null) {
            mutableStream.value = StreamStatus("SIN_CAPTURA", "Pulsa 'Preparar captura de pantalla' antes de conectar")
            return
        }
        try {
            val local = localAddress ?: return
            val socket = DatagramSocket(null).apply { reuseAddress = true }
            try { socket.bind(InetSocketAddress(local, WfdSource.RTP_SERVER_PORT)) }
            catch (_: java.net.SocketException) { socket.bind(InetSocketAddress(local, 0)) }
            datagrams = socket
            val target = InetSocketAddress(play.sink, play.rtpPort)
            val audioPermitted = context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
            val withAudio = play.audioCodec == WfdFormats.AAC_48K_STEREO && audioPermitted
            val streamer = TsRtpStreamer({ bytes ->
                try { socket.send(DatagramPacket(bytes, bytes.size, target)) } catch (_: java.io.IOException) { }
            }, audio = withAudio)
            val audioState = when {
                withAudio -> "AAC"
                play.audioCodec == null -> "RECEPTOR_SIN_AUDIO"
                !audioPermitted -> "SIN_PERMISO_AUDIO"
                else -> "LPCM_NO_IMPLEMENTADO"
            }
            var last = 0L
            val mode = play.mode
            videoWidth = mode.width; videoHeight = mode.height
            fun report() {
                val now = System.currentTimeMillis()
                if (now - last > 1000) {
                    last = now
                    mutableStream.value = StreamStatus("STREAMING", "${mode.width}x${mode.height}p${mode.fps}",
                        streamer.accessUnits, streamer.datagramsSent, streamer.audioFrames, mutableStream.value.audio)
                }
            }
            ScreenEncoder(granted, mode.width, mode.height, mode.fps, { unit, ptsUs, key ->
                streamer.onAccessUnit(unit, ptsUs, key); report()
            }, { error -> stopStream(error) }).also { encoder = it; it.start(context.resources.displayMetrics.densityDpi) }
            mutableStream.value = StreamStatus("STARTING", "${mode.width}x${mode.height}p${mode.fps}", audio = audioState)
            if (withAudio) try {
                AudioCapture(granted, { frame, ptsUs -> streamer.onAudioFrame(frame, ptsUs) }, { error ->
                    mutableStream.value = mutableStream.value.copy(audio = error); event("Audio detenido: $error")
                }).also { audioCapture = it; it.start() }
            } catch (error: Exception) { mutableStream.value = mutableStream.value.copy(audio = "START_${error.javaClass.simpleName}") }
            event("Vídeo iniciado hacia el receptor: ${mode.width}x${mode.height}p${mode.fps}; audio=$audioState; táctil=${play.uibc}")
        } catch (error: Exception) { stopStream("START_${error.javaClass.simpleName}") }
    }

    private fun stopStream(reason: String) {
        audioCapture?.close(); audioCapture = null
        encoder?.close(); encoder = null
        datagrams?.close(); datagrams = null
        if (mutableStream.value.stage != "IDLE") {
            mutableStream.value = mutableStream.value.copy(stage = "STOPPED", detail = reason)
            event("Vídeo detenido: $reason")
        }
    }

    fun stop(reason: String?) {
        stopStream(reason ?: "NUEVA_SESION")
        controlServer?.close(); controlServer = null
        mediaServer?.close(); mediaServer = null
        sensorServer?.close(); sensorServer = null
        uibcServer?.close(); uibcServer = null
        source?.close(); source = null
        uibcKey?.fill(0); uibcKey = null
        if (reason != null && mutableRtsp.value.stage != "IDLE") event("Sesión de proyección cerrada: $reason")
    }
}
