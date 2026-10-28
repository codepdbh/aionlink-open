package org.aionlink.wifi

import android.Manifest
import android.annotation.SuppressLint
import android.content.*
import android.content.pm.PackageManager
import android.net.MacAddress
import android.net.ConnectivityManager
import android.net.Network
import android.net.wifi.WifiManager
import android.net.wifi.p2p.*
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.iccoa.ServerInfo
import org.aionlink.iccoa.validUnicastMac
import org.aionlink.iccoa.PlatformP2pAddress
import org.aionlink.iccoa.AddressDisclosure
import org.aionlink.iccoa.NetworkMode
import java.net.InetAddress
import java.net.Socket

enum class NetworkStage { IDLE, PREPARING, READY, CONNECTING, CONNECTED, ERROR, CANCELLED }
data class P2pStatus(val stage: NetworkStage = NetworkStage.IDLE, val message: String = "Red sin preparar", val mode: NetworkMode? = null) {
    val busy get() = stage in setOf(NetworkStage.PREPARING, NetworkStage.CONNECTING)
    val handoffActive get() = stage in setOf(NetworkStage.CONNECTING, NetworkStage.CONNECTED)
}
enum class AuthProbeStage { IDLE, RUNNING, FINISHED, UNAVAILABLE, CANCELLED }
data class AuthProbeStatus(val stage: AuthProbeStage = AuthProbeStage.IDLE, val detail: String = "Sin comprobar", val elapsedMs: Long? = null) {
    val busy get() = stage == AuthProbeStage.RUNNING
}
class LocalP2p(val platformAddress: PlatformP2pAddress, val supports5Ghz: Boolean) {
    val address get() = platformAddress.realValueOrNull()
    override fun toString() = "LocalP2p(redacted)"
}

/** Public Wi-Fi Direct APIs only. Never changes global Wi-Fi mode or removes preexisting groups. */
@SuppressLint("MissingPermission")
class P2pConnector(context: Context) {
    private val context = context.applicationContext
    private val manager = context.getSystemService(WifiP2pManager::class.java)
    private val wifi = context.getSystemService(WifiManager::class.java)
    private val connectivity = context.getSystemService(ConnectivityManager::class.java)
    private var apCallback: ConnectivityManager.NetworkCallback? = null
    private var apNetwork: Network? = null
    val systemWifiHandoff get() = mutable.value.handoffActive
    private val handler = Handler(Looper.getMainLooper())
    private var channel: WifiP2pManager.Channel? = null
    private var generation = 0
    private var timeout: Runnable? = null
    private var registered = false
    private class Target(val ssid: String, val mac: String)
    private var target: Target? = null
    private var submitted = false
    private var ownedGroup = false
    private class VerifiedEndpoint(val address: InetAddress, val interfaceName: String)
    private var verifiedEndpoint: VerifiedEndpoint? = null
    private var probeSocket: Socket? = null
    private var probeGeneration = 0
    private val mutableProbe = MutableStateFlow(AuthProbeStatus())
    val authProbe = mutableProbe.asStateFlow()
    private val mutable = MutableStateFlow(P2pStatus())
    val state = mutable.asStateFlow()
    fun requiredPermissions() = if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.NEARBY_WIFI_DEVICES)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
    fun hasPermissions() = requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }
    private fun publish(stage: NetworkStage, message: String) { mutable.value = P2pStatus(stage, message, mutable.value.mode) }
    private fun action(ticket: Int, success: () -> Unit) = object : WifiP2pManager.ActionListener {
        override fun onSuccess() { if (ticket == generation) success() }
        override fun onFailure(reason: Int) {
            if (ticket != generation) return
            val detail = when (reason) {
                WifiP2pManager.BUSY -> "BUSY; Android está ocupado, espera antes de reintentar"
                WifiP2pManager.P2P_UNSUPPORTED -> "P2P_UNSUPPORTED; función no disponible"
                WifiP2pManager.ERROR -> "ERROR; fallo interno de Android"
                else -> "código $reason"
            }
            fail("Wi-Fi Direct no inició la operación: $detail")
        }
    }
    fun prepare(onReady: (LocalP2p) -> Unit) {
        stop()
        mutableProbe.value = AuthProbeStatus()
        if (!hasPermissions()) { fail("Falta permiso de dispositivos Wi-Fi cercanos"); return }
        if (wifi?.isWifiEnabled != true || manager == null) { fail("Activa Wi-Fi para preparar el enlace"); return }
        val ticket = generation
        mutable.value = P2pStatus(NetworkStage.PREPARING, "Solicitando identidad Wi-Fi Direct a Android")
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
                        val identity = PlatformP2pAddress.fromPlatform(address)
                        disarm()
                        publish(NetworkStage.READY, when (identity.disclosure) {
                            AddressDisclosure.AVAILABLE -> "Identidad P2P local preparada; sin enlace remoto"
                            AddressDisclosure.ANDROID_ANONYMIZED -> "Android devolvió un dato anonimizado; experimento posible sólo con consentimiento"
                            AddressDisclosure.UNAVAILABLE -> "Android no devolvió metadatos P2P utilizables"
                        })
                        onReady(LocalP2p(identity, wifi.is5GHzBandSupported))
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
        mutable.value = mutable.value.copy(mode = info.mode)
        if (info.mode == NetworkMode.ACCESS_POINT) { connectAccessPoint(info, ticket); return }
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
    private fun connectAccessPoint(info: ServerInfo, ticket: Int) {
        val service = connectivity ?: return fail("Servicio de red no disponible")
        publish(NetworkStage.CONNECTING, "El receptor indicó modo AP; acepta la conexión Wi-Fi solicitada por Android")
        arm(45000, "La red AP del receptor no se obtuvo en 45 segundos")
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                if (ticket != generation || apCallback !== this) return
                apNetwork = network; disarm()
                publish(NetworkStage.CONNECTED, "Android concedió la red AP solicitada para este receptor. Autenticación ICCOA pendiente")
            }
            override fun onUnavailable() {
                if (ticket == generation && apCallback === this) fail("Android no autorizó o no pudo obtener la red AP solicitada")
            }
            override fun onLost(network: Network) {
                if (ticket == generation && apCallback === this && apNetwork == network) fail("La red AP concedida se desconectó")
            }
        }
        apCallback = callback
        try { service.requestNetwork(accessPointRequest(info), callback, handler, 45000) }
        catch (_: SecurityException) { fail("Android no autorizó la solicitud de red AP") }
        catch (_: IllegalArgumentException) { fail("Parámetros de red AP no admitidos; contenido omitido") }
        catch (_: IllegalStateException) { fail("Solicitud de red AP no disponible") }
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
                        val ownerAddress = link.groupOwnerAddress
                        val interfaceName = group.`interface`
                        verifiedEndpoint = if (ownerAddress != null && !ownerAddress.isAnyLocalAddress &&
                            !ownerAddress.isLoopbackAddress && !ownerAddress.isMulticastAddress && !interfaceName.isNullOrBlank())
                            VerifiedEndpoint(ownerAddress, interfaceName) else null
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
    /** Explicit user action, restricted to the selected, verified P2P group. */
    fun probeAuthChannel() {
        if (mutableProbe.value.busy) return
        if (mutable.value.stage != NetworkStage.CONNECTED || mutable.value.mode != NetworkMode.P2P) {
            mutableProbe.value = AuthProbeStatus(AuthProbeStage.UNAVAILABLE, "P2P del receptor no conectado")
            return
        }
        val endpoint = verifiedEndpoint
        if (endpoint == null) {
            mutableProbe.value = AuthProbeStatus(AuthProbeStage.UNAVAILABLE, "Android no proporcionó destino e interfaz del grupo")
            return
        }
        val service = connectivity
        if (service == null) {
            mutableProbe.value = AuthProbeStatus(AuthProbeStage.UNAVAILABLE, "Servicio de red no disponible")
            return
        }
        val matchedNetwork = try {
            @Suppress("DEPRECATION")
            service.allNetworks.firstOrNull { service.getLinkProperties(it)?.interfaceName == endpoint.interfaceName }
        } catch (_: SecurityException) { null }
        if (matchedNetwork == null) {
            mutableProbe.value = AuthProbeStatus(AuthProbeStage.UNAVAILABLE, "Android no expuso una red asociada a la interfaz P2P")
            return
        }
        val ticket = ++probeGeneration
        val socket = Socket()
        probeSocket = socket
        mutableProbe.value = AuthProbeStatus(AuthProbeStage.RUNNING, "Comprobando un canal TCP observado; sin enviar datos")
        val started = SystemClock.elapsedRealtime()
        Thread {
            val result = OneShotTcpProbe.check(socket, endpoint.address, 57209, 2500) { matchedNetwork.bindSocket(it) }
            val elapsed = SystemClock.elapsedRealtime() - started
            handler.post {
                if (ticket != probeGeneration) return@post
                probeSocket = null
                mutableProbe.value = AuthProbeStatus(AuthProbeStage.FINISHED, result.name, elapsed)
            }
        }.apply { name = "AionLink-auth-check"; isDaemon = true; start() }
    }
    private fun fail(message: String) { release(); publish(NetworkStage.ERROR, message) }
    fun stop(reason: String = "Preparación/red de AionLink detenida") {
        val wasActive = mutable.value.stage !in setOf(NetworkStage.IDLE, NetworkStage.ERROR, NetworkStage.CANCELLED)
        release()
        if (wasActive) publish(NetworkStage.CANCELLED, reason)
    }
    private fun release() {
        generation++
        probeGeneration++
        try { probeSocket?.close() } catch (_: java.io.IOException) { }
        probeSocket = null
        if (mutableProbe.value.busy) mutableProbe.value = AuthProbeStatus(AuthProbeStage.CANCELLED, "La red o el intento se detuvo")
        verifiedEndpoint = null
        disarm()
        val callback = apCallback; apCallback = null; apNetwork = null
        if (callback != null) try { connectivity?.unregisterNetworkCallback(callback) }
        catch (_: IllegalArgumentException) { }
        catch (_: SecurityException) { }
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
