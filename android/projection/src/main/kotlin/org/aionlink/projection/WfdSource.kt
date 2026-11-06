package org.aionlink.projection

import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** Parameters agreed in M4 and confirmed by SETUP/PLAY. */
data class WfdPlay(val sink: InetAddress, val rtpPort: Int, val mode: WfdVideoMode, val audioCodec: String?, val uibc: Boolean,
                   val rtpTcp: Socket? = null, val carDisplay: CarDisplayMode? = null)

data class WfdStatus(val stage: String = "IDLE", val detail: String = "", val carDisplayMode: String = "ausente",
                     val sinkVideoFormats: String = "ausente", val sinkAudioCodecs: String = "ausente",
                     val sinkUibc: String = "ausente", val sinkRtpPorts: String = "ausente",
                     val sinkOther: String = "ausente")

/**
 * Independent Wi-Fi Display (Miracast) source RTSP session, following the public WFD message sequence
 * (M1 OPTIONS → M2 → M3 GET_PARAMETER → M4/M5 SET_PARAMETER → M6 SETUP → M7 PLAY).
 * The phone listens on its group address: CarWith listens on ChannelType.RTSP (7236); the receiver connects.
 * The car's sink additionally reports wfd_car_display_mode (string observed in Samsung's display library).
 */
class WfdSource(
    private val local: InetAddress,
    private val port: Int = RTSP_PORT,
    private val rtpServerPort: Int = RTP_SERVER_PORT,
    private val onStatus: (WfdStatus) -> Unit,
    private val onPlay: (WfdPlay) -> Unit,
    private val onIdrRequest: () -> Unit = {},
    private val onStop: (String) -> Unit = {},
    private val onLog: (String) -> Unit = {}
) : AutoCloseable {
    companion object {
        const val RTSP_PORT = 7236
        const val RTP_SERVER_PORT = 15550
        private const val RTP_TCP_CONNECT_MS = 2500
        private const val MAX_SINK_LOG_LINES = 40
        val M3_PARAMETERS = listOf("wfd_video_formats", "wfd_audio_codecs", "wfd_client_rtp_ports",
            "wfd_uibc_capability", "wfd_car_display_mode")
    }

    class Message(val startLine: String, val headers: Map<String, String>, val body: String) {
        val isResponse get() = startLine.startsWith("RTSP/")
        val method get() = startLine.substringBefore(' ')
        val cseq get() = headers["cseq"]?.trim()?.toIntOrNull()
        val status get() = if (isResponse) startLine.split(' ').getOrNull(1)?.toIntOrNull() else null
        fun parameters(): Map<String, String> = body.lines().mapNotNull { line ->
            val index = line.indexOf(':'); if (index <= 0) null else line.substring(0, index).trim() to line.substring(index + 1).trim()
        }.toMap()
    }

    @Volatile private var server: ServerSocket? = null
    @Volatile private var sink: Socket? = null
    @Volatile private var rtpTcp: Socket? = null
    @Volatile private var closed = false
    private var cseq = 1
    private val pending = mutableMapOf<Int, String>()
    private var status = WfdStatus()
    private var mode: WfdVideoMode? = null
    private var clientPort: Int? = null
    private var audioCodec: String? = null
    private var uibc = false
    private var carDisplay: CarDisplayMode? = null
    private val sessionId = (java.security.SecureRandom().nextInt() and 0x7fffffff).coerceAtLeast(1)
    private var keepAlive: Thread? = null
    private var sinkLogLines = 0

    /** Method and parameter names only: values may describe the session and stay out of the export. */
    private fun logSinkRequest(message: Message) {
        if (sinkLogLines >= MAX_SINK_LOG_LINES) return
        sinkLogLines++
        // Bare lines count too: wfd_idr_request and GET_PARAMETER bodies carry no colon.
        val names = message.body.lines().map { it.substringBefore(':').trim() }.filter { it.isNotEmpty() }
        onLog("RTSP del receptor: ${message.method}; parámetros=${names.map(WfdFormats::safeExcerpt)}")
    }

    private fun publish(stage: String, detail: String = "") { status = status.copy(stage = stage, detail = detail); onStatus(status) }

    /** Binds synchronously so the sink can connect as soon as AUTH is confirmed. */
    fun start() {
        val listener = ServerSocket()
        listener.reuseAddress = true
        listener.bind(InetSocketAddress(local, port), 1)
        server = listener
        publish("LISTENING")
        Thread({ run(listener) }, "AionLink-rtsp").apply { isDaemon = true; start() }
    }

    val boundPort: Int get() = server?.localPort ?: -1

    private fun run(listener: ServerSocket) {
        var reason = "CLOSED"
        try {
            listener.soTimeout = 30000
            val socket = listener.accept()
            sink = socket
            socket.tcpNoDelay = true
            publish("SINK_CONNECTED")
            val input = BufferedInputStream(socket.getInputStream())
            val output = socket.getOutputStream()
            request(output, "OPTIONS * RTSP/1.0", listOf("Require: org.wfa.wfd1.0"), "", "M1")
            while (!closed) {
                val message = read(input) ?: run { reason = "CLOSED_BY_SINK"; null } ?: break
                if (message.isResponse) onResponse(output, message) else onRequest(output, socket, message)
            }
        } catch (_: java.net.SocketTimeoutException) { reason = if (sink == null) "NO_SINK_30S" else "TIMEOUT" }
        catch (_: IOException) { reason = "IO_FAILURE" }
        catch (_: IllegalArgumentException) { reason = "INVALID_RTSP" }
        finally {
            if (!closed) { publish("STOPPED", reason); onStop(reason) }
            close()
        }
    }

    private fun onResponse(output: OutputStream, message: Message) {
        val label = message.cseq?.let { synchronized(pending) { pending.remove(it) } } ?: return
        val ok = message.status == 200
        when (label) {
            "M1" -> publish("M1_OK", "status=${message.status}")
            "M3" -> {
                if (!ok) { publish("M3_FAILED", "status=${message.status}"); return }
                val values = message.parameters()
                carDisplay = WfdFormats.parseCarDisplayMode(values["wfd_car_display_mode"])
                status = status.copy(carDisplayMode = WfdFormats.safeExcerpt(values["wfd_car_display_mode"]),
                    sinkVideoFormats = WfdFormats.safeExcerpt(values["wfd_video_formats"]),
                    sinkAudioCodecs = WfdFormats.safeExcerpt(values["wfd_audio_codecs"]),
                    sinkUibc = WfdFormats.safeExcerpt(values["wfd_uibc_capability"]),
                    sinkRtpPorts = WfdFormats.safeExcerpt(values["wfd_client_rtp_ports"]),
                    sinkOther = WfdFormats.safeExcerpt(values.filterKeys { it !in M3_PARAMETERS && it != "wfd_presentation_URL" }
                        .entries.joinToString("; ") { "${it.key}=${it.value}" }))
                val video = WfdFormats.chooseVideo(values["wfd_video_formats"])
                val rtp = WfdFormats.clientRtpPorts(values["wfd_client_rtp_ports"])
                if (video == null || rtp == null) {
                    publish("M3_UNSUPPORTED", "vídeo=${video != null}; puertoRTP=${rtp != null}"); return
                }
                mode = video; clientPort = rtp.port
                publish("M3_OK", "modo=${video.width}x${video.height}p${video.fps}; transporte=${if (rtp.tcp) "TCP" else "UDP"}")
                val body = buildString {
                    append("wfd_video_formats: ").append(video.m4Value()).append("\r\n")
                    audioCodec = WfdFormats.chooseAudio(values["wfd_audio_codecs"])
                    audioCodec?.let { append("wfd_audio_codecs: ").append(it).append("\r\n") }
                    uibc = WfdFormats.sinkSupportsUibc(values["wfd_uibc_capability"])
                    if (uibc) append("wfd_uibc_capability: ").append(WfdFormats.UIBC_CAPABILITY).append("\r\n")
                    append("wfd_presentation_URL: rtsp://${local.hostAddress}/wfd1.0/streamid=0 none\r\n")
                    append("wfd_client_rtp_ports: ").append(values.getValue("wfd_client_rtp_ports")).append("\r\n")
                }
                request(output, "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", listOf("Content-Type: text/parameters"), body, "M4")
            }
            "M4" -> {
                if (!ok) { publish("M4_REJECTED", "status=${message.status}"); return }
                publish("M4_OK")
                request(output, "SET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", listOf("Content-Type: text/parameters"),
                    "wfd_trigger_method: SETUP\r\n", "M5")
            }
            "M5" -> publish(if (ok) "M5_OK" else "M5_REJECTED", "status=${message.status}")
            "KEEPALIVE" -> onLog("RTSP keepalive: status=${message.status}")
        }
    }

    private fun onRequest(output: OutputStream, socket: Socket, message: Message) {
        val seq = message.cseq ?: throw IllegalArgumentException("Missing CSeq")
        logSinkRequest(message)
        when (message.method) {
            "OPTIONS" -> {
                respond(output, seq, listOf("Public: org.wfa.wfd1.0, SETUP, TEARDOWN, PLAY, PAUSE, GET_PARAMETER, SET_PARAMETER"))
                publish("M2_OK")
                request(output, "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", listOf("Content-Type: text/parameters"),
                    M3_PARAMETERS.joinToString("") { "$it\r\n" }, "M3")
            }
            "SETUP" -> {
                val transport = message.headers["transport"] ?: ""
                if (transport.contains("interleaved")) {
                    publish("M6_UNSUPPORTED", "interleaved"); respond(output, seq, emptyList(), 461, "Unsupported Transport"); return
                }
                val ports = Regex("client_port=(\\d+)(?:-(\\d+))?").find(transport)
                val rtp = ports?.groupValues?.get(1)?.toIntOrNull() ?: clientPort
                if (rtp == null || mode == null) { respond(output, seq, emptyList(), 400, "Bad Request"); return }
                clientPort = rtp
                val rtcp = ports?.groupValues?.getOrNull(2)?.takeIf { it.isNotEmpty() }
                val clientPart = if (rtcp != null) "$rtp-$rtcp" else "$rtp"
                val tcp = transport.startsWith("RTP/AVP/TCP")
                // The sink listens before sending SETUP; AOSP-derived sources connect to it while handling SETUP.
                if (tcp && !connectRtpTcp(socket.inetAddress, rtp)) {
                    publish("M6_TCP_FAILED", "puertoRTP=$rtp"); respond(output, seq, emptyList(), 500, "Internal Server Error"); return
                }
                val profile = if (tcp) "TCP" else "UDP"
                // MiAuto's sink rejects anything other than server_port=<even>-<even+1>, whatever client_port had.
                respond(output, seq, listOf("Session: $sessionId;timeout=30",
                    "Transport: RTP/AVP/$profile;unicast;client_port=$clientPart;server_port=$rtpServerPort-${rtpServerPort + 1}"))
                publish("M6_SETUP", "puertoRTP=$rtp; transporte=$profile")
            }
            "PLAY" -> {
                respond(output, seq, listOf("Session: $sessionId", "Range: npt=now-"))
                val chosen = mode; val rtp = clientPort
                if (chosen != null && rtp != null) {
                    publish("PLAYING", "${chosen.width}x${chosen.height}p${chosen.fps}")
                    onPlay(WfdPlay(socket.inetAddress, rtp, chosen, audioCodec, uibc, rtpTcp, carDisplay))
                    startKeepAlive(output)
                }
            }
            "SET_PARAMETER" -> {
                respond(output, seq, emptyList())
                if (message.body.contains("wfd_idr_request")) onIdrRequest()
            }
            "TEARDOWN" -> { respond(output, seq, emptyList()); publish("TEARDOWN"); onStop("TEARDOWN"); close() }
            "GET_PARAMETER", "PAUSE" -> respond(output, seq, emptyList())
            else -> respond(output, seq, emptyList(), 405, "Method Not Allowed")
        }
    }

    private fun connectRtpTcp(address: InetAddress, port: Int): Boolean {
        try { rtpTcp?.close() } catch (_: IOException) { }
        rtpTcp = null
        val socket = Socket()
        return try {
            socket.bind(InetSocketAddress(local, 0))
            socket.tcpNoDelay = true
            socket.connect(InetSocketAddress(address, port), RTP_TCP_CONNECT_MS)
            rtpTcp = socket
            true
        } catch (_: IOException) {
            try { socket.close() } catch (_: IOException) { }
            false
        }
    }

    private fun startKeepAlive(output: OutputStream) {
        if (keepAlive != null) return
        keepAlive = Thread({
            try {
                while (!closed) {
                    Thread.sleep(25000)
                    request(output, "GET_PARAMETER rtsp://localhost/wfd1.0 RTSP/1.0", listOf("Session: $sessionId"), "", "KEEPALIVE")
                }
            } catch (_: InterruptedException) { } catch (_: IOException) { }
        }, "AionLink-rtsp-keepalive").apply { isDaemon = true; start() }
    }

    private fun request(output: OutputStream, line: String, headers: List<String>, body: String, label: String) {
        val seq = synchronized(pending) { cseq++.also { pending[it] = label } }
        write(output, line, listOf("CSeq: $seq") + headers, body)
    }

    private fun respond(output: OutputStream, seq: Int, headers: List<String>, code: Int = 200, reason: String = "OK") =
        write(output, "RTSP/1.0 $code $reason", listOf("CSeq: $seq") + headers, "")

    private fun write(output: OutputStream, line: String, headers: List<String>, body: String) {
        val bytes = body.toByteArray(Charsets.US_ASCII)
        val text = buildString {
            append(line).append("\r\n")
            append("Date: ").append(java.time.format.DateTimeFormatter.RFC_1123_DATE_TIME.format(java.time.ZonedDateTime.now(java.time.ZoneOffset.UTC))).append("\r\n")
            append("Server: AionLinkOpen/0.28\r\n")
            headers.forEach { append(it).append("\r\n") }
            if (bytes.isNotEmpty()) append("Content-Length: ${bytes.size}\r\n")
            append("\r\n")
        }
        synchronized(output) { output.write(text.toByteArray(Charsets.US_ASCII)); output.write(bytes); output.flush() }
    }

    /** Reads one RTSP message; null at clean EOF. Bounded to protect against malformed peers. */
    internal fun read(input: InputStream): Message? {
        val head = ByteArrayOutputStream()
        var matched = 0
        while (matched < 4) {
            val b = input.read()
            if (b < 0) { if (head.size() == 0) return null; throw IOException("Truncated RTSP") }
            head.write(b)
            require(head.size() <= 8192) { "RTSP header too large" }
            matched = when { b == '\r'.code && matched % 2 == 0 -> matched + 1; b == '\n'.code && matched % 2 == 1 -> matched + 1
                b == '\r'.code -> 1; else -> 0 }
        }
        val lines = head.toString(Charsets.US_ASCII.name()).split("\r\n").filter { it.isNotEmpty() }
        require(lines.isNotEmpty())
        val headers = lines.drop(1).mapNotNull { line ->
            val index = line.indexOf(':'); if (index <= 0) null else line.substring(0, index).trim().lowercase() to line.substring(index + 1).trim()
        }.toMap()
        val length = headers["content-length"]?.toIntOrNull() ?: 0
        require(length in 0..65536)
        val body = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val count = input.read(body, offset, length - offset)
            if (count < 0) throw IOException("Truncated RTSP body")
            offset += count
        }
        return Message(lines[0], headers, String(body, Charsets.US_ASCII))
    }

    /** Synchronized so a caller racing the RTSP thread's own close returns only after the sockets are really closed. */
    @Synchronized override fun close() {
        if (closed) return
        closed = true
        keepAlive?.interrupt()
        try { rtpTcp?.close() } catch (_: IOException) { }
        try { sink?.close() } catch (_: IOException) { }
        try { server?.close() } catch (_: IOException) { }
    }
}
