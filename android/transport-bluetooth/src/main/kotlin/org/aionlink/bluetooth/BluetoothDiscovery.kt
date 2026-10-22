package org.aionlink.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanSettings
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.*
import android.content.pm.PackageManager
import android.os.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID
import org.aionlink.iccoa.AdvertisementEvidence

/** Discovery only. Addresses remain in memory; IDs are random per search. */
class BluetoothDiscovery(context: Context) {
    private val context = context.applicationContext
    private val adapter get() = context.getSystemService(BluetoothManager::class.java)?.adapter
    private val mutableDevices = MutableStateFlow<List<DiscoveredDevice>>(emptyList())
    val devices = mutableDevices.asStateFlow()
    private val mutableScanning = MutableStateFlow(false)
    val isScanning = mutableScanning.asStateFlow()
    private val identities = mutableMapOf<String, String>()
    private val handles = mutableMapOf<String, BluetoothDevice>()
    fun observedBleDevice(id: String): BluetoothDevice? =
        if (mutableDevices.value.any { it.id == id && it.transport == "BLE" }) handles[id] else null
    private val handler = Handler(Looper.getMainLooper())
    private var registered = false
    private var scanning = false
    private var scanGeneration = 0
    private var scanCallback: ScanCallback? = null
    private var completion: ((String?) -> Unit)? = null
    private val timeout = Runnable { stop() }

    @SuppressLint("MissingPermission")
    private fun found(device: BluetoothDevice, transport: String, rssi: Int?, services: Set<String> = emptySet(), connectable: Boolean? = null) {
        if (!scanning) return
        try {
            val key = device.address + transport
            val id = identities.getOrPut(key) { UUID.randomUUID().toString().take(8) }
            if (transport == "BLE") handles[id] = device
            val item = DiscoveredDevice(id, device.name ?: "Sin nombre", transport, rssi,
                services.size, AdvertisementEvidence.recognizedServices(services), connectable)
            mutableDevices.value = mergeObservation(mutableDevices.value, item)
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
    private fun callbackFor(generation: Int) = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            // Export only allowlisted public protocol UUIDs, never raw service/manufacturer data.
            val services = result.scanRecord?.serviceUuids?.map { it.uuid.toString() }?.toSet() ?: emptySet()
            handler.post {
                if (generation == scanGeneration) found(result.device, "BLE", result.rssi, services, result.isConnectable)
            }
        }
        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach { onScanResult(0, it) }
        }
        override fun onScanFailed(errorCode: Int) { handler.post {
            if (generation == scanGeneration) stop("BLE: error $errorCode")
        } }
    }
    fun requiredPermissions(): Array<String> = if (Build.VERSION.SDK_INT >= 31)
        arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        else arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    fun hasPermissions() = requiredPermissions().all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    @SuppressLint("MissingPermission", "UnspecifiedRegisterReceiverFlag")
    fun start(iccoaOnly: Boolean = true, onFinished: (String?) -> Unit) {
        stop()
        if (!hasPermissions()) { onFinished("Permisos pendientes"); return }
        val radio = adapter
        if (radio == null) { onFinished("Bluetooth no disponible"); return }
        try {
            if (!radio.isEnabled) { onFinished("Activa Bluetooth desde los ajustes del teléfono"); return }
            mutableDevices.value = emptyList(); identities.clear(); handles.clear()
            completion = onFinished; scanning = true; mutableScanning.value = true
            // Bluetooth broadcasts originate from a privileged system component.
            val filter = IntentFilter(BluetoothDevice.ACTION_FOUND)
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
            val classic = if (iccoaOnly) false else radio.startDiscovery()
            val scanner = radio.bluetoothLeScanner
            val callback = callbackFor(scanGeneration)
            scanCallback = callback
            val filters = if (iccoaOnly) listOf(ScanFilter.Builder()
                .setServiceUuid(ParcelUuid.fromString(AdvertisementEvidence.ADVERTISED_SERVICE)).build()) else emptyList()
            val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0).build()
            scanner?.startScan(filters, settings, callback)
            if (!classic && scanner == null) { stop("No se pudo iniciar el descubrimiento"); return }
            handler.postDelayed(timeout, 15000)
        } catch (_: SecurityException) { stop("Permiso Bluetooth denegado") }
        catch (_: IllegalStateException) { stop("Bluetooth no disponible") }
    }
    @SuppressLint("MissingPermission")
    fun stop(error: String? = null) {
        handler.removeCallbacks(timeout)
        val wasScanning = scanning; scanning = false
        scanGeneration++
        mutableScanning.value = false
        val previousCallback = scanCallback; scanCallback = null
        if (wasScanning) try {
            adapter?.cancelDiscovery()
            previousCallback?.let { adapter?.bluetoothLeScanner?.stopScan(it) }
        } catch (_: SecurityException) { }
        catch (_: IllegalStateException) { }
        if (registered) { context.unregisterReceiver(receiver); registered = false }
        val done = completion; completion = null
        done?.invoke(error)
    }
}
