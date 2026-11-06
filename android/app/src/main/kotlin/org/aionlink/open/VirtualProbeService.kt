package org.aionlink.open

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Runs the virtual-display probe inside a foreground service, the same way a future screen-off car mode would
 * run, so that a screen-off result measures the display/encoder path rather than Android freezing a background
 * activity. Type connectedDevice matches that future car-link use; the probe itself still sends nothing to the car.
 * Results go to the private journal, which survives the activity and is included in the exported diagnostic.
 */
class VirtualProbeService : Service() {
    companion object {
        private const val CHANNEL = "aionlink-probe"
        private const val ACTION_STOP = "org.aionlink.open.STOP_VIRTUAL_PROBE"
        /** Long enough for ten minutes with the screen off plus time to switch it off and back on. */
        const val MAX_MS = 12 * 60_000L
        private val mutableRunning = MutableStateFlow(false)
        val running = mutableRunning.asStateFlow()
        @Volatile var onReport: ((String) -> Unit)? = null

        fun start(context: Context) { context.startForegroundService(Intent(context, VirtualProbeService::class.java)) }
        fun stop(context: Context) {
            if (running.value) context.startService(Intent(context, VirtualProbeService::class.java).setAction(ACTION_STOP))
        }
    }

    private val handler = Handler(Looper.getMainLooper())
    private var probe: VirtualDisplayVideoProbe? = null
    private lateinit var journal: VirtualProbeJournal
    private val deadline = Runnable { finish("límite de 12 minutos") }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { finish("detenida por el propietario"); return START_NOT_STICKY }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL, "Prueba de pantalla apagada", NotificationManager.IMPORTANCE_LOW))
        val notification = Notification.Builder(this, CHANNEL).setContentTitle("AionLink: prueba de pantalla apagada")
            .setContentText("Patrón propio en pantalla virtual; no se envía nada al coche").setSmallIcon(android.R.drawable.ic_menu_view)
            .setOngoing(true).build()
        try {
            startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
        } catch (error: RuntimeException) {
            onReport?.invoke("Sonda virtual: servicio en primer plano rechazado (${error.javaClass.simpleName})")
            stopSelf(); return START_NOT_STICKY
        }
        if (probe != null) return START_NOT_STICKY
        journal = VirtualProbeJournal(this)
        journal.start()
        try {
            probe = VirtualDisplayVideoProbe(applicationContext) { message -> journal.sample(message); onReport?.invoke(message) }
                .also { it.start() }
            mutableRunning.value = true
            journal.sample("Servicio en primer plano activo; tipo connectedDevice; máximo 12 minutos")
            handler.postDelayed(deadline, MAX_MS)
        } catch (error: Exception) {
            val message = "Sonda virtual falló al iniciar: ${error.javaClass.simpleName}"
            journal.finish(message); onReport?.invoke(message)
            probe = null; stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun finish(reason: String) {
        handler.removeCallbacks(deadline)
        probe?.let {
            val summary = "Pantalla virtual propia terminada ($reason); dibujados=${it.drawnFrames}; H264=${it.encodedFrames}; " +
                "TS/RTP cuadros=${it.muxedFrames}; paquetes=${it.rtpPackets}; paquetes inválidos=${it.invalidPackets}; no es DeX"
            journal.finish(summary); onReport?.invoke(summary); it.close()
        }
        probe = null
        mutableRunning.value = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (probe != null) finish("servicio destruido por el sistema")
        super.onDestroy()
    }
}
