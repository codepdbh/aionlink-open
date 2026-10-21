package org.aionlink.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class DiscoveredDevice(val id: String, val name: String, val transport: String, val rssi: Int?)

/** Discovery only. Addresses remain in memory; IDs are random per search. */
class BluetoothDiscovery(context: Context) {
    private val context = context.applicationContext
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val identities = mutableMapOf<String, String>()
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var scanning = false
    private var completion: ((String?) -> Unit)? = null
    private val timeout = Runnable { stop() }

    @SuppressLint("MissingPermission")
    private fun found(device: BluetoothDevice, transport: String, rssi: Int?) {
        if (!scanning) return
        try {
            val key = device.address + transport
            val id = identities.getOrPut(key) { UUID.randomUUID().toString().take(8) }
            val item = DiscoveredDevice(id, device.name ?: "Sin nombre", transport, rssi)
            mutableDevices.value = mutableDevices.value.filterNot { it.id == id } + item
        } catch (_: SecurityException) { stop("Permiso Bluetooth revocado") }
    }
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == BluetoothDevice.ACTION_FOUND) {
                @Suppress("DEPRECATION") val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                device?.let { found(it, "Bluetooth Classic", intent.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).takeIf { it != Short.MIN_VALUE }?.toInt()) }
            }
        }
    }
    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handler.post { found(result.device, "BLE", result.rssi) }
        }
        override fun onScanFailed(errorCode: Int) { handler.post { stop("BLE: error $errorCode") } }
    }
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions() = requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun start(onFinished: (String?) -> Unit) {
        stop()
        if (!hasPermissions()) { onFinished("Permisos pendientes"); return }
        val radio = adapter
        if (radio == null) { onFinished("Bluetooth no disponible"); return }
        try {
            if (!radio.isEnabled) { onFinished("Activa Bluetooth desde los ajustes del teléfono"); return }
            mutableDevices.value = emptyList(); identities.clear()
            completion = onFinished; scanning = true
            // Bluetooth broadcasts originate from a privileged system component.
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
            val classic = radio.startDiscovery()
            val scanner = radio.bluetoothLeScanner
            scanner?.startScan(callback)
            if (!classic && scanner == null) { stop("No se pudo iniciar el descubrimiento"); return }
            handler.postDelayed(timeout, 15000)
        } catch (_: SecurityException) { stop("Permiso Bluetooth denegado") }
        catch (_: IllegalStateException) { stop("Bluetooth no disponible") }
    }
    @SuppressLint("MissingPermission")
    fun stop(error: String? = null) {
        handler.removeCallbacks(timeout)
        val wasScanning = scanning; scanning = false
        if (wasScanning) try { adapter?.cancelDiscovery(); adapter?.bluetoothLeScanner?.stopScan(callback) } catch (_: SecurityException) { }
        catch (_: IllegalStateException) { }
        if (registered) { context.unregisterReceiver(receiver); registered = false }
        val done = completion; completion = null
        done?.invoke(error)
    }
}
