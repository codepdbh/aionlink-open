package org.aionlink.open

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.projection.TsRtpStreamer
import org.aionlink.projection.WfdPlay
import org.aionlink.projection.WfdSource
import org.aionlink.projection.WfdStatus
import org.aionlink.wifi.ControlChannelStatus
import org.aionlink.wifi.SessionControlServer
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetSocketAddress

data class StreamStatus(val stage: String = "IDLE", val detail: String = "", val accessUnits: Long = 0, val datagrams: Long = 0)

/**
 * Post-AUTH session observed in Samsung: phone-side CONTROL listener (57219) and Wi-Fi Display source
 * (RTSP 7236, RTP server port 15550). Video comes from the owner-approved MediaProjection, if granted.
 * Status values are exported without addresses, keys or payloads.
 */
class ProjectionSession(private val context: Context, private val event: (String) -> Unit) {
    private val mutableControl = MutableStateFlow(ControlChannelStatus())
    val control = mutableControl.asStateFlow()
    private val mutableRtsp = MutableStateFlow(WfdStatus())
    val rtsp = mutableRtsp.asStateFlow()
    private val mutableStream = MutableStateFlow(StreamStatus())
    val stream = mutableStream.asStateFlow()
    @Volatile private var controlServer: SessionControlServer? = null
    @Volatile private var source: WfdSource? = null
    @Volatile private var encoder: ScreenEncoder? = null
    @Volatile private var datagrams: DatagramSocket? = null
    val active get() = controlServer != null || source != null

    /** Called on the AUTH thread before AuthConfirm, so both listeners exist when the receiver connects. */
    fun onAuthenticated(local: Inet4Address, sessionKey: ByteArray) {
        stop(null)
        localAddress = local
        try {
            SessionControlServer(local, sessionKey) { status ->
                if (mutableControl.value.stage != status.stage) event("Canal CONTROL: ${status.stage}")
                mutableControl.value = status
            }.also { it.start(); controlServer = it }
        } catch (_: java.io.IOException) { sessionKey.fill(0); mutableControl.value = ControlChannelStatus("BIND_FAILED") }
        try {
            WfdSource(local, onStatus = { status ->
                if (mutableRtsp.value.stage != status.stage) event("RTSP WFD: ${status.stage} ${status.detail}".trim())
                mutableRtsp.value = status
            }, onPlay = ::play, onIdrRequest = { encoder?.requestKeyframe() }, onStop = { reason -> stopStream("RTSP_$reason") })
                .also { it.start(); source = it }
        } catch (_: java.io.IOException) { mutableRtsp.value = WfdStatus("BIND_FAILED") }
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
            val streamer = TsRtpStreamer({ bytes ->
                try { socket.send(DatagramPacket(bytes, bytes.size, target)) } catch (_: java.io.IOException) { }
            })
            var last = 0L
            val mode = play.mode
            ScreenEncoder(granted, mode.width, mode.height, mode.fps, { unit, ptsUs, key ->
                synchronized(streamer) { streamer.onAccessUnit(unit, ptsUs, key) }
                val now = System.currentTimeMillis()
                if (now - last > 1000) {
                    last = now
                    mutableStream.value = StreamStatus("STREAMING", "${mode.width}x${mode.height}p${mode.fps}",
                        streamer.accessUnits, streamer.datagramsSent)
                }
            }, { error -> stopStream(error) }).also { encoder = it; it.start(context.resources.displayMetrics.densityDpi) }
            mutableStream.value = StreamStatus("STARTING", "${mode.width}x${mode.height}p${mode.fps}")
            event("Vídeo iniciado hacia el receptor: ${mode.width}x${mode.height}p${mode.fps}")
        } catch (error: Exception) { stopStream("START_${error.javaClass.simpleName}") }
    }

    @Volatile private var localAddress: Inet4Address? = null

    private fun stopStream(reason: String) {
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
        source?.close(); source = null
        if (reason != null && mutableRtsp.value.stage != "IDLE") event("Sesión de proyección cerrada: $reason")
    }
}
