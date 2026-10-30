package org.aionlink.open

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaCodec
import android.media.MediaCodecInfo
import android.media.MediaFormat
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.IBinder

/**
 * Foreground service required by Android for screen capture. Holds the MediaProjection granted by the
 * owner for this session only; the token is never persisted. Capture starts only after the receiver's PLAY.
 */
class ProjectionService : Service() {
    companion object {
        private const val CHANNEL = "aionlink-projection"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"
        @Volatile var projection: MediaProjection? = null; private set
        @Volatile var lastError: String? = null; private set

        fun start(context: Context, resultCode: Int, data: Intent) {
            val intent = Intent(context, ProjectionService::class.java).putExtra(EXTRA_CODE, resultCode).putExtra(EXTRA_DATA, data)
            context.startForegroundService(intent)
        }
        fun stop(context: Context) {
            val intent = Intent(context, ProjectionService::class.java)
            context.stopService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Proyección AionLink", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL).setContentTitle("AionLink Open")
            .setContentText("Captura de pantalla preparada para el vehículo").setSmallIcon(android.R.drawable.ic_menu_upload).build()
        startForeground(1, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val code = intent?.getIntExtra(EXTRA_CODE, 0) ?: 0
        @Suppress("DEPRECATION") val data = intent?.getParcelableExtra<Intent>(EXTRA_DATA)
        if (data == null) { lastError = "SIN_CONSENTIMIENTO"; stopSelf(); return START_NOT_STICKY }
        try {
            projection?.stop()
            val granted = getSystemService(MediaProjectionManager::class.java).getMediaProjection(code, data)
            granted?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { projection = null }
            }, Handler(Looper.getMainLooper()))
            projection = granted; lastError = if (granted == null) "PROYECCION_NULA" else null
        } catch (_: SecurityException) { lastError = "SECURITY_EXCEPTION"; stopSelf() }
        return START_NOT_STICKY
    }

    override fun onDestroy() { projection?.stop(); projection = null; super.onDestroy() }
}

/** Hardware H.264 encoder fed by a mirrored virtual display; delivers Annex-B access units. */
class ScreenEncoder(
    private val projection: MediaProjection, private val width: Int, private val height: Int, private val fps: Int,
    private val onAccessUnit: (ByteArray, Long, Boolean) -> Unit, private val onError: (String) -> Unit
) : AutoCloseable {
    private var codec: MediaCodec? = null
    private var display: VirtualDisplay? = null
    @Volatile private var running = false
    private var csd: ByteArray? = null

    fun start(densityDpi: Int) {
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            setInteger(MediaFormat.KEY_BIT_RATE, if (width >= 1280) 6_000_000 else 2_500_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, fps)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
            setInteger(MediaFormat.KEY_PROFILE, MediaCodecInfo.CodecProfileLevel.AVCProfileBaseline)
            setInteger(MediaFormat.KEY_LEVEL, if (width >= 1280) MediaCodecInfo.CodecProfileLevel.AVCLevel31 else MediaCodecInfo.CodecProfileLevel.AVCLevel3)
            setLong(MediaFormat.KEY_REPEAT_PREVIOUS_FRAME_AFTER, 1_000_000L / fps)
            setInteger(MediaFormat.KEY_PREPEND_HEADER_TO_SYNC_FRAMES, 1)
        }
        val encoder = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        encoder.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
        val surface = encoder.createInputSurface()
        encoder.start()
        codec = encoder
        display = projection.createVirtualDisplay("AionLink", width, height, densityDpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, surface, null, null)
        running = true
        Thread({ drain(encoder) }, "AionLink-encoder").apply { isDaemon = true; start() }
    }

    fun requestKeyframe() {
        codec?.setParameters(Bundle().apply { putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0) })
    }

    private fun drain(encoder: MediaCodec) {
        val info = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = encoder.dequeueOutputBuffer(info, 100_000)
                if (index < 0) continue
                val buffer = encoder.getOutputBuffer(index)
                if (buffer != null && info.size > 0) {
                    val data = ByteArray(info.size)
                    buffer.position(info.offset); buffer.get(data)
                    if (info.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) csd = data
                    else {
                        val key = info.flags and MediaCodec.BUFFER_FLAG_KEY_FRAME != 0
                        val config = csd
                        val unit = if (key && config != null && !startsWithSps(data)) config + data else data
                        onAccessUnit(unit, info.presentationTimeUs, key)
                    }
                }
                encoder.releaseOutputBuffer(index, false)
            }
        } catch (error: Exception) { if (running) onError("ENCODER_${error.javaClass.simpleName}") }
    }

    private fun startsWithSps(data: ByteArray): Boolean {
        val offset = when { data.size > 4 && data[2].toInt() == 1 -> 3; data.size > 5 && data[3].toInt() == 1 -> 4; else -> return false }
        return data[offset].toInt() and 0x1F == 7
    }

    override fun close() {
        running = false
        display?.release(); display = null
        codec?.let { try { it.stop() } catch (_: IllegalStateException) { }; it.release() }
        codec = null
    }
}
