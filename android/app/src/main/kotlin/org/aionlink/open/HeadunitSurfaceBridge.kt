package org.aionlink.open

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.os.RemoteException
import android.view.Surface

/** Explicit opt-in bridge to a same-signature receiver. No display or shell privilege is used. */
class HeadunitSurfaceBridge(
    private val context: Context,
    private val surface: Surface,
    /** Optional: the receiver writes decoded sound here instead of the phone speaker (newer Open Headunit only). */
    private val audio: BridgeAudio? = null,
    private val report: (String) -> Unit,
) : AutoCloseable {
    private var bound = false
    private var closed = false
    private var remote: Messenger? = null
    private val reply = Messenger(Handler(Looper.getMainLooper()) { message ->
        if (message.what == 10 && !closed) {
            if (message.data.getBoolean("attached")) {
                val audioState = when {
                    audio == null -> ""
                    message.data.getBoolean("audio") -> "; audio por el puente aceptado"
                    else -> "; esta versión de Open Headunit no admite audio por el puente (sonará en el teléfono)"
                }
                report("Superficie aceptada$audioState; solicitando self mode. Vídeo y pantalla apagada sin verificar")
                OpenHeadunit.requestSelfModeWithoutUi(context)
            } else report("Open Headunit rechazó la superficie")
            true
        } else false
    })
    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            if (closed) return
            remote = Messenger(binder)
            try {
                remote?.send(Message.obtain(null, 1).apply {
                    replyTo = reply
                    data = Bundle().apply {
                        putParcelable("surface", surface)
                        audio?.let { putParcelable("audio", it.writeEnd) }
                    }
                })
            } catch (error: RemoteException) { report("Falló el envío de superficie: ${error.javaClass.simpleName}") }
            // The receiver holds its own duplicate now; ours must close so the pipe ends when the receiver stops.
            audio?.releaseWriteEnd()
        }
        override fun onServiceDisconnected(name: ComponentName) { remote = null; report("Puente de superficie desconectado") }
        override fun onBindingDied(name: ComponentName) { remote = null; report("El servicio de superficie terminó") }
        override fun onNullBinding(name: ComponentName) { report("Open Headunit no ofrece el puente de superficie") }
    }

    fun start() {
        val intent = Intent().setComponent(ComponentName(OpenHeadunit.PACKAGE,
            "com.andrerinas.openheadunit.aap.AionLinkSurfaceService"))
        try {
            bound = context.bindService(intent, connection, Context.BIND_AUTO_CREATE)
            if (!bound) report("No está disponible el servicio de superficie de Open Headunit")
        } catch (error: SecurityException) {
            report("El puente requiere versiones de ambas apps con la misma firma")
        }
    }

    /**
     * Car touch for Android Auto, in the encoded frame's pixels (the surface given to the receiver).
     * Bridge message 3: action (MotionEvent code), ids/xs/ys (IntArray, one entry per pointer), width/height of the frame.
     * The receiver translates it to an Android Auto touch event; returns false if the bridge is not connected.
     */
    fun touch(action: Int, ids: IntArray, xs: IntArray, ys: IntArray, width: Int, height: Int): Boolean {
        val target = remote ?: return false
        return try {
            target.send(Message.obtain(null, TOUCH).apply {
                replyTo = reply
                data = Bundle().apply {
                    putInt("action", action); putIntArray("ids", ids); putIntArray("xs", xs); putIntArray("ys", ys)
                    putInt("width", width); putInt("height", height)
                }
            })
            true
        } catch (_: RemoteException) { false }
    }

    private companion object { const val TOUCH = 3 }

    override fun close() {
        if (closed) return
        closed = true
        try { remote?.send(Message.obtain(null, 2).apply { replyTo = reply }) } catch (_: RemoteException) { }
        remote = null
        if (bound) { context.unbindService(connection); bound = false }
    }
}
