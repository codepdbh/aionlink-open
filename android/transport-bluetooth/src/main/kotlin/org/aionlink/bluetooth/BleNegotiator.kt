package org.aionlink.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.iccoa.*
import java.util.UUID

enum class BootstrapStage { IDLE, CONNECTING, MTU, SERVICES, SUBSCRIBING, WRITING, WAITING_SERVER, READING_SERVER, SERVER_RECEIVED, ERROR, CANCELLED }
data class BootstrapStatus(val stage: BootstrapStage = BootstrapStage.IDLE, val mtu: Int = 23,
                           val bytesSent: Int = 0, val bytesReceived: Int = 0, val message: String = "Sin intercambio",
                           val mode: String = "NONE", val writeConfirmed: Boolean = false) {
    val busy get() = stage in setOf(BootstrapStage.CONNECTING, BootstrapStage.MTU, BootstrapStage.SERVICES,
        BootstrapStage.SUBSCRIBING, BootstrapStage.WRITING, BootstrapStage.WAITING_SERVER, BootstrapStage.READING_SERVER)
}

/** One bounded, user-authorized normal bootstrap. No OEM code, auth credentials or raw logging. */
@SuppressLint("MissingPermission")
class BleNegotiator(context: Context) {
    private val context = context.applicationContext
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(BootstrapStatus())
    val state = mutable.asStateFlow()
    private var generation = 0
    private var active: BluetoothGatt? = null
    private var payload: ByteArray? = null
    private var output: ((ServerInfo) -> Unit)? = null
    private var client: BluetoothGattCharacteristic? = null
    private var server: BluetoothGattCharacteristic? = null
    private val delivery = BootstrapDelivery()
    private var timeout: Runnable? = null
    private val cccd = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb") // Bluetooth SIG CCCD

    fun start(device: BluetoothDevice, info: ClientInfo, onServer: (ServerInfo) -> Unit) {
        startInternal(device, info, onServer)
    }
    /** A public read of the characteristic observed as READ, without an invented ClientInfo MAC.
     * The receiver may return empty/not-ready. This does not perform or bypass authentication.
     */
    fun readAvailableServerInfo(device: BluetoothDevice, onServer: (ServerInfo) -> Unit) {
        startInternal(device, null, onServer)
    }
    private fun startInternal(device: BluetoothDevice, info: ClientInfo?, onServer: (ServerInfo) -> Unit) {
        check(Looper.myLooper() == Looper.getMainLooper())
        cancel()
        val ticket = generation
        payload = info?.encode(); output = onServer
        mutable.value = BootstrapStatus(BootstrapStage.CONNECTING, message = "Abriendo enlace BLE",
            mode = when {
                info == null -> "READ_ONLY_NO_PIN"
                info.usesAnonymizedMetadata -> "CLIENT_INFO_PLATFORM_ANONYMIZED_EXPERIMENT"
                else -> "NORMAL_CLIENT_INFO"
            })
        fun dispatch(gatt: BluetoothGatt, block: () -> Unit) { handler.post {
            if (generation == ticket && active === gatt) try { block() }
            catch (_: SecurityException) { fail("Permisos Bluetooth revocados") }
            catch (_: IllegalArgumentException) { fail("Parámetros BLE no admitidos; datos omitidos") }
            catch (_: IllegalStateException) { fail("Bluetooth no disponible") }
        } }
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) = dispatch(gatt) {
                if (status != BluetoothGatt.GATT_SUCCESS) fail("Error de enlace GATT ($status)")
                else if (newState == BluetoothProfile.STATE_CONNECTED && mutable.value.stage == BootstrapStage.CONNECTING) {
                    update(BootstrapStage.MTU, "Negociando tamaño GATT")
                    if (!gatt.requestMtu(512)) discover(gatt)
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) fail("El receptor cerró BLE durante el intercambio")
            }
            override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) = dispatch(gatt) {
                if (mutable.value.stage == BootstrapStage.MTU) {
                    if (status == BluetoothGatt.GATT_SUCCESS) mutable.value = mutable.value.copy(mtu = mtu)
                    discover(gatt)
                }
            }
            override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) = dispatch(gatt) {
                if (mutable.value.stage != BootstrapStage.SERVICES) return@dispatch
                if (status != BluetoothGatt.GATT_SUCCESS) { fail("Descubrimiento GATT falló ($status)"); return@dispatch }
                val service = gatt.getService(UUID.fromString(AdvertisementEvidence.GATT_SERVICE))
                client = service?.getCharacteristic(UUID.fromString(AdvertisementEvidence.CLIENT_INFO))
                server = service?.getCharacteristic(UUID.fromString(AdvertisementEvidence.SERVER_INFO))
                val tx = client; val rx = server
                if (info == null) {
                    if (rx == null || rx.properties and BluetoothGattCharacteristic.PROPERTY_READ == 0) {
                        fail("ServerInfo no permite lectura; hace falta ClientInfo con datos P2P locales reales"); return@dispatch
                    }
                    update(BootstrapStage.READING_SERVER, "Consultando ServerInfo legible; no se envía ClientInfo ni PIN")
                    timeout?.let(handler::removeCallbacks)
                    timeout = Runnable {
                        if (ticket == generation) fail("ServerInfo no respondió a la lectura en 15 segundos. No se envió ClientInfo ni PIN")
                    }.also { handler.postDelayed(it, 15000) }
                    if (!gatt.readCharacteristic(rx)) fail("Android no inició lectura ServerInfo")
                    return@dispatch
                }
                if (tx == null || rx == null || tx.properties and BluetoothGattCharacteristic.PROPERTY_WRITE == 0 ||
                    rx.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY == 0) {
                    fail("Faltan características UnionShare de escritura/notificación"); return@dispatch
                }
                if ((payload?.size ?: 0) > minOf(512, mutable.value.mtu - 3)) {
                    fail("MTU insuficiente para ClientInfo; no se fragmenta sin protocolo verificado"); return@dispatch
                }
                update(BootstrapStage.SUBSCRIBING, "Preparando respuesta ServerInfo")
                if (!gatt.setCharacteristicNotification(rx, true)) { fail("No se habilitó notificación local"); return@dispatch }
                val descriptor = rx.getDescriptor(cccd)
                if (descriptor == null) write(gatt) // OEM inspected client also enables local notifications only.
                else {
                    val ok = if (Build.VERSION.SDK_INT >= 33) gatt.writeDescriptor(descriptor, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) == BluetoothStatusCodes.SUCCESS
                    else { @Suppress("DEPRECATION")
                        descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                        @Suppress("DEPRECATION") gatt.writeDescriptor(descriptor)
                    }
                    if (!ok) fail("Android rechazó la solicitud de suscripción")
                }
            }
            override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) = dispatch(gatt) {
                if (mutable.value.stage == BootstrapStage.SUBSCRIBING && descriptor.uuid == cccd) {
                    if (status == BluetoothGatt.GATT_SUCCESS) write(gatt) else fail("Suscripción GATT falló ($status)")
                }
            }
            override fun onCharacteristicWrite(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) = dispatch(gatt) {
                if (mutable.value.stage != BootstrapStage.WRITING || characteristic.uuid != client?.uuid) return@dispatch
                if (status != BluetoothGatt.GATT_SUCCESS) { fail("ClientInfo no fue confirmado ($status)"); return@dispatch }
                mutable.value = mutable.value.copy(writeConfirmed = true)
                payload?.fill(0); payload = null
                update(BootstrapStage.WAITING_SERVER, "ClientInfo confirmado; esperando respuesta del receptor")
                delivery.acknowledge()?.let { accept(it) }
            }
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                receive(gatt, characteristic, value)
            }
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                readResult(gatt, characteristic, value, status)
            }
            @Deprecated("Legacy Android callback")
            override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
                if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION")
                    readResult(gatt, characteristic, characteristic.value ?: byteArrayOf(), status)
                }
            }
            private fun readResult(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray, status: Int) {
                if (characteristic.uuid != UUID.fromString(AdvertisementEvidence.SERVER_INFO)) return
                if (value.size > 4096) { dispatch(gatt) { fail("ServerInfo excede el límite") }; return }
                val copy = value.copyOf()
                handler.post {
                    try {
                        if (ticket != generation || active !== gatt || mutable.value.stage != BootstrapStage.READING_SERVER) return@post
                        if (status != BluetoothGatt.GATT_SUCCESS) { fail("Lectura ServerInfo rechazada ($status); no se intenta eludirla"); return@post }
                        mutable.value = mutable.value.copy(bytesReceived = copy.size)
                        val parsed = try { ServerInfo.parse(copy) } catch (error: IllegalArgumentException) {
                            val reason = (error as? ServerInfoException)?.fault?.name ?: "UNKNOWN"
                            fail("ServerInfo no v\u00e1lido en READ ($reason); no se envi\u00f3 ClientInfo ni PIN. Contenido omitido")
                            return@post
                        }
                        accept(parsed)
                    } finally { copy.fill(0) }
                }
            }
            @Deprecated("Legacy Android callback")
            override fun onCharacteristicChanged(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic) {
                if (Build.VERSION.SDK_INT < 33) { @Suppress("DEPRECATION")
                    receive(gatt, characteristic, characteristic.value ?: byteArrayOf())
                }
            }
            private fun receive(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, value: ByteArray) {
                if (characteristic.uuid != UUID.fromString(AdvertisementEvidence.SERVER_INFO)) return
                if (value.size > 4096) { dispatch(gatt) { fail("ServerInfo excede el límite") }; return }
                val copy = value.copyOf()
                handler.post {
                    try {
                        if (generation != ticket || active !== gatt || mutable.value.stage !in setOf(BootstrapStage.WRITING, BootstrapStage.WAITING_SERVER)) return@post
                        mutable.value = mutable.value.copy(bytesReceived = copy.size)
                        val parsed = try { ServerInfo.parse(copy) } catch (error: IllegalArgumentException) {
                            val reason = (error as? ServerInfoException)?.fault?.name ?: "UNKNOWN"
                            fail("ServerInfo rechazado por nuestro parser ($reason); contenido omitido"); return@post
                        }
                        delivery.response(parsed)?.let { accept(it) }
                    } finally { copy.fill(0) }
                }
            }
        }
        timeout = Runnable { if (ticket == generation) fail("Intercambio BLE agotó 30 segundos; no prueba rechazo de autenticación") }
            .also { handler.postDelayed(it, 30000) }
        try {
            active = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            if (active == null) fail("Android no creó el enlace BLE")
        } catch (_: SecurityException) { fail("Permisos Bluetooth pendientes") }
        catch (_: IllegalArgumentException) { fail("Dispositivo BLE no válido") }
        catch (_: IllegalStateException) { fail("Bluetooth no disponible") }
    }
    private fun discover(gatt: BluetoothGatt) {
        update(BootstrapStage.SERVICES, "Buscando características UnionShare")
        if (!gatt.discoverServices()) fail("No se inició descubrimiento GATT")
    }
    private fun write(gatt: BluetoothGatt) {
        val tx = client ?: return fail("ClientInfo no disponible")
        val bytes = payload ?: return fail("Mensaje inicial no disponible")
        update(BootstrapStage.WRITING, "Enviando ClientInfo propio")
        mutable.value = mutable.value.copy(bytesSent = bytes.size)
        val ok = if (Build.VERSION.SDK_INT >= 33) gatt.writeCharacteristic(tx, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        else { @Suppress("DEPRECATION")
            tx.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            tx.value = bytes
            @Suppress("DEPRECATION") gatt.writeCharacteristic(tx)
        }
        if (!ok) fail("Android no aceptó la escritura ClientInfo")
    }
    private fun accept(info: ServerInfo) {
        val next = output
        cleanup()
        update(BootstrapStage.SERVER_RECEIVED, "ServerInfo recibido; BLE cerrado. Autenticación ICCOA pendiente")
        next?.invoke(info)
    }
    private fun update(stage: BootstrapStage, message: String) { mutable.value = mutable.value.copy(stage = stage, message = message) }
    private fun fail(message: String) { cleanup(); update(BootstrapStage.ERROR, message) }
    fun reset() {
        cancel("Nuevo intento")
        mutable.value = BootstrapStatus(message = "Intercambio del nuevo intento todavía no iniciado")
    }
    fun cancel(reason: String = "Cancelación solicitada") {
        check(Looper.myLooper() == Looper.getMainLooper())
        val busy = mutable.value.busy
        cleanup()
        if (busy) update(BootstrapStage.CANCELLED, "$reason; etapa anterior=${mutable.value.stage}; modo=${mutable.value.mode}")
    }
    private fun cleanup() {
        generation++
        timeout?.let(handler::removeCallbacks); timeout = null
        payload?.fill(0); payload = null; output = null; delivery.clear()
        client = null; server = null
        val link = active; active = null
        try { link?.disconnect() } catch (_: SecurityException) { }
        try { link?.close() } catch (_: SecurityException) { }
    }
}
