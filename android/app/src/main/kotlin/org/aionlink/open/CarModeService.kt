package org.aionlink.open

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager

/**
 * Keeps the process, CPU and car sockets alive while car mode streams with the phone screen off. Type connectedDevice,
 * as validated by the 10-minute screen-off probe. The partial wake lock is bounded so a forgotten session cannot drain
 * the battery indefinitely; Stop or the car's disconnect ends it earlier.
 */
class CarModeService : Service() {
    companion object {
        private const val CHANNEL = "aionlink-car-mode"
        private const val MAX_WAKE_MS = 4 * 60 * 60_000L
        @Volatile var running = false; private set

        fun start(context: Context) { context.startForegroundService(Intent(context, CarModeService::class.java)) }
        fun stop(context: Context) {
            if (!running) return
            val intent = Intent(context, CarModeService::class.java)
            context.stopService(intent)
        }
    }

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Modo coche", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL).setContentTitle("AionLink: modo coche")
            .setContentText("Enviando el panel de AionLink al coche; puedes apagar la pantalla").setSmallIcon(android.R.drawable.ic_menu_upload)
            .setOngoing(true).build()
        try {
            startForeground(3, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (_: RuntimeException) { stopSelf(); return START_NOT_STICKY }
        if (wakeLock == null) wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AionLink:car-mode").also { it.acquire(MAX_WAKE_MS) }
        running = true
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        wakeLock?.let { if (it.isHeld) it.release() }; wakeLock = null
        running = false
        super.onDestroy()
    }
}
