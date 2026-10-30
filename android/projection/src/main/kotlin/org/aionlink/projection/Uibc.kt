package org.aionlink.projection

import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** Events from the receiver's touchscreen/keys, in the video frame's coordinate space. */
sealed class UibcEvent {
    data class Pointer(val id: Int, val x: Int, val y: Int)
    /** [action] uses Android MotionEvent action codes (0 down, 1 up, 2 move, 5/6 pointer down/up). */
    data class Touch(val action: Int, val width: Int, val height: Int, val pointers: List<Pointer>) : UibcEvent()
    /** [action] uses Android KeyEvent actions (0 down, 1 up); [keyCode] uses Android key codes. */
    data class Key(val action: Int, val keyCode: Int, val metaState: Int) : UibcEvent()
}

/**
 * Parser for the receiver's UIBC stream as handled by Samsung (q5/f.java): concatenated flat JSON objects,
 * each ending with '}'. type 1 = touch {action,width,height,count,trackID<i>,x<i>,y<i>}; otherwise a key
 * {action,keycode,metaState}. Independent minimal parser for integer-valued fields only.
 */
class UibcParser {
    private val pending = StringBuilder()
    private val field = Regex("\"(\\w+)\"\\s*:\\s*(-?\\d+)")

    fun feed(chunk: String): List<UibcEvent> {
        pending.append(chunk)
        if (pending.length > 65536) pending.setLength(0)
        val events = mutableListOf<UibcEvent>()
        while (true) {
            val end = pending.indexOf("}")
            if (end < 0) break
            val start = pending.lastIndexOf("{", end)
            val text = if (start >= 0) pending.substring(start, end + 1) else ""
            pending.delete(0, end + 1)
            parse(text)?.let(events::add)
        }
        return events
    }

    fun parse(json: String): UibcEvent? {
        val values = field.findAll(json).associate { it.groupValues[1] to it.groupValues[2].toLongOrNull() }
        fun int(name: String) = values[name]?.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
        val action = int("action") ?: return null
        if (int("type") == 1) {
            val count = int("count")?.takeIf { it in 1..10 } ?: return null
            val pointers = (0 until count).map { i ->
                UibcEvent.Pointer(int("trackID$i") ?: i, int("x$i") ?: return null, int("y$i") ?: return null)
            }
            return UibcEvent.Touch(action, int("width") ?: 0, int("height") ?: 0, pointers)
        }
        return UibcEvent.Key(action, int("keycode") ?: return null, int("metaState") ?: 0)
    }
}

/** Maps a point of the W×H video frame back to the phone screen mirrored into it (scaled to fit, centered). */
object UibcMapper {
    fun map(x: Int, y: Int, videoWidth: Int, videoHeight: Int, screenWidth: Int, screenHeight: Int): Pair<Float, Float>? {
        if (videoWidth <= 0 || videoHeight <= 0 || screenWidth <= 0 || screenHeight <= 0) return null
        val scale = minOf(videoWidth.toFloat() / screenWidth, videoHeight.toFloat() / screenHeight)
        val offsetX = (videoWidth - screenWidth * scale) / 2f
        val offsetY = (videoHeight - screenHeight * scale) / 2f
        val px = (x - offsetX) / scale
        val py = (y - offsetY) / scale
        if (px < 0f || py < 0f || px >= screenWidth || py >= screenHeight) return null
        return px to py
    }
}

/**
 * Phone-side UIBC listener (Samsung binds TCP 4321 and accepts the receiver). Plain JSON by default; when the
 * receiver encrypts, [decrypt] may unwrap a session envelope. Event content is never exported.
 */
class UibcServer(
    private val local: InetAddress, private val port: Int = PORT,
    private val onEvent: (UibcEvent) -> Unit, private val onStatus: (String) -> Unit,
    private val decrypt: ((ByteArray) -> ByteArray?)? = null
) : AutoCloseable {
    companion object { const val PORT = 4321 }
    @Volatile private var server: ServerSocket? = null
    @Volatile private var client: Socket? = null
    @Volatile private var closed = false
    @Volatile var events = 0; private set

    fun start() {
        val listener = ServerSocket()
        listener.reuseAddress = true
        listener.bind(InetSocketAddress(local, port), 1)
        server = listener
        onStatus("LISTENING")
        Thread({ run(listener) }, "AionLink-uibc").apply { isDaemon = true; start() }
    }

    val boundPort: Int get() = server?.localPort ?: -1

    private fun run(listener: ServerSocket) {
        try {
            listener.soTimeout = 60000
            val socket = listener.accept()
            client = socket
            onStatus("CONNECTED")
            val parser = UibcParser()
            val input = socket.getInputStream()
            val buffer = ByteArray(3000)
            while (!closed) {
                val count = input.read(buffer)
                if (count < 0) { onStatus("CLOSED_BY_RECEIVER"); break }
                var data = buffer.copyOf(count)
                if (data.isNotEmpty() && data[0] != '{'.code.toByte()) decrypt?.invoke(data)?.let { data = it }
                parser.feed(String(data, Charsets.UTF_8)).forEach { events++; onEvent(it) }
            }
        } catch (_: java.net.SocketTimeoutException) { if (!closed) onStatus("NO_CONNECTION_60S") }
        catch (_: IOException) { if (!closed) onStatus("IO_FAILURE") }
        finally { close() }
    }

    override fun close() {
        if (closed) return
        closed = true
        try { client?.close() } catch (_: IOException) { }
        try { server?.close() } catch (_: IOException) { }
    }
}
