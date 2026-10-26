package org.aionlink.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.MacAddress
import android.net.wifi.WifiManager
import android.net.wifi.p2p.*
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.iccoa.ServerInfo
import org.aionlink.iccoa.validUnicastMac

enum class NetworkStage { IDLE, PREPARING, READY, CONNECTING, CONNECTED, ERROR, CANCELLED }
data class P2pStatus(val stage: NetworkStage = NetworkStage.IDLE, val message: String = "Red sin preparar") {
    val busy get() = stage in setOf(NetworkStage.PREPARING, NetworkStage.CONNECTING)
}
class LocalP2p(val address: String?, val supports5Ghz: Boolean) {
    override fun toString() = "LocalP2p(redacted)"
}

/** Public Wi-Fi Direct APIs only. Never changes global Wi-Fi mode or removes preexisting groups. */
@SuppressLint("MissingPermission")
class P2pConnector(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(WifiP2pManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var channel: WifiP2pManager.Channel? = null
    private var generation = 0
    private var timeout: Runnable? = null
    private var registered = false
    private class Target(val ssid: String, val mac: String)
    private var target: Target? = null
    private var submitted = false
    private var ownedGroup = false
    private val mutable = MutableStateFlow(P2pStatus())
    val state = mutable.asStateFlow()
    fun requiredPermissions() = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasPermissions() = requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun publish(stage: NetworkStage, message: String) { mutable.value = P2pStatus(stage, message) }
    private fun action(ticket: Int, success: () -> Unit) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() { if (ticket == generation) success() }
        override fun onFailure(reason: Int) { if (ticket == generation) fail("Wi-Fi Direct rechazó la operación ($reason)") }
    }
    fun prepare(onReady: (LocalP2p) -> Unit) {
        stop()
        if (!hasPermissions()) { fail("Falta permiso de dispositivos Wi-Fi cercanos"); return }
        if (wifi?.isWifiEnabled != true || manager == null) { fail("Activa Wi-Fi para preparar el enlace"); return }
        val ticket = generation
        publish(NetworkStage.PREPARING, "Solicitando identidad Wi-Fi Direct a Android")
        arm(10000, "Android no entregó los datos P2P a tiempo")
        try {
            val connection = manager.initialize(context, Looper.getMainLooper()) {
                if (ticket == generation) fail("Canal Wi-Fi Direct cerrado por Android")
            }
            channel = connection
            if (connection == null) { fail("Wi-Fi Direct no disponible"); return }
            manager.requestGroupInfo(connection) { existing ->
                if (ticket != generation) return@requestGroupInfo
                if (existing != null) { fail("Ya existe un grupo Wi-Fi Direct. Finalízalo antes de conectar AionLink"); return@requestGroupInfo }
                try {
                    manager.requestDeviceInfo(connection) { device ->
                        if (ticket != generation) return@requestDeviceInfo
                        val address = device?.deviceAddress
                        val usable = address?.takeIf(::validUnicastMac)
                        disarm()
                        publish(NetworkStage.READY, if (usable == null)
                            "Android oculta la dirección P2P local; sólo se podrá consultar ServerInfo legible"
                            else "Identidad P2P local preparada; sin enlace remoto")
                        onReady(LocalP2p(usable, wifi.is5GHzBandSupported))
                    }
                } catch (_: SecurityException) { fail("Permisos Wi-Fi revocados") }
            }
        } catch (_: SecurityException) { fail("Permisos Wi-Fi pendientes") }
        catch (_: IllegalStateException) { fail("Wi-Fi Direct no disponible") }
    }
    fun connect(info: ServerInfo) {
        val connection = channel
        val radio = manager
        if (connection == null || radio == null || mutable.value.stage != NetworkStage.READY) {
            fail("La preparación P2P ya no está disponible"); return
        }
        val ticket = generation
        target = Target(info.ssid, info.mac)
        publish(NetworkStage.CONNECTING, "Solicitando red Wi-Fi Direct del receptor; acepta el diálogo Android si aparece")
        arm(45000, "No se formó la red Wi-Fi Direct en 45 segundos")
        try {
            val filter = IntentFilter(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
            val config = WifiP2pConfig.Builder().setDeviceAddress(MacAddress.fromString(info.mac))
                .setNetworkName(info.ssid).setPassphrase(info.passphrase)
                .setGroupOperatingFrequency(info.frequency).enablePersistentMode(false).build()
            submitted = true
            radio.connect(connection, config, action(ticket) { checkConnection(ticket) })
        } catch (_: SecurityException) { fail("Permisos Wi-Fi revocados") }
        catch (_: IllegalArgumentException) { fail("Android no admite los parámetros P2P recibidos; contenido omitido") }
        catch (_: IllegalStateException) { fail("Wi-Fi Direct no disponible") }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // Ignore intent extras; ask the platform for the current group instead.
            if (intent.action == WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION) checkConnection(generation)
        }
    }
    private fun checkConnection(ticket: Int) {
        val radio = manager ?: return
        val connection = channel ?: return
        if (mutable.value.stage !in setOf(NetworkStage.CONNECTING, NetworkStage.CONNECTED)) return
        try {
            radio.requestConnectionInfo(connection) { link ->
                if (ticket != generation) return@requestConnectionInfo
                if (!link.groupFormed) {
                    if (mutable.value.stage == NetworkStage.CONNECTED) fail("La red del receptor se desconectó")
                    return@requestConnectionInfo
                }
                try {
                    radio.requestGroupInfo(connection) { group ->
                        if (ticket != generation) return@requestGroupInfo
                        val expected = target ?: return@requestGroupInfo
                        if (group == null || link.isGroupOwner || group.networkName != expected.ssid ||
                            group.owner?.deviceAddress?.equals(expected.mac, true) != true) {
                            fail("El grupo formado no coincide con el receptor seleccionado; no se declara conectado")
                            return@requestGroupInfo
                        }
                        ownedGroup = true
                        disarm()
                        publish(NetworkStage.CONNECTED, "Red del receptor conectada. Autenticación ICCOA y proyección pendientes")
                    }
                } catch (_: SecurityException) { fail("Permisos Wi-Fi revocados") }
            }
        } catch (_: SecurityException) { fail("Permisos Wi-Fi revocados") }
    }
    private fun arm(ms: Long, message: String) {
        disarm()
        val ticket = generation
        timeout = Runnable { if (ticket == generation) fail(message) }.also { handler.postDelayed(it, ms) }
    }
    private fun disarm() { timeout?.let(handler::removeCallbacks); timeout = null }
    private fun fail(message: String) { release(); publish(NetworkStage.ERROR, message) }
    fun stop(reason: String = "Preparación/red de AionLink detenida") {
        val wasActive = mutable.value.stage !in setOf(NetworkStage.IDLE, NetworkStage.ERROR, NetworkStage.CANCELLED)
        release()
        if (wasActive) publish(NetworkStage.CANCELLED, reason)
    }
    private fun release() {
        generation++
        disarm()
        if (registered) { context.unregisterReceiver(receiver); registered = false }
        val connection = channel
        try {
            if (connection != null && submitted) manager?.cancelConnect(connection, null)
            if (connection != null && ownedGroup) manager?.removeGroup(connection, null)
        } catch (_: SecurityException) { }
        // Closing our channel does not remove unrelated groups or change Wi-Fi settings.
        connection?.close(); channel = null
        target = null; submitted = false; ownedGroup = false
    }
}
