package org.aionlink.open

import android.app.Application
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.bluetooth.BluetoothDiscovery
import org.aionlink.bluetooth.GattInspector
import org.aionlink.core.*
import org.aionlink.iccoa.ICCOAProtocolAdapter
import org.aionlink.iccoa.GattStage
import org.aionlink.vehicles.VehicleProfiles
import org.aionlink.usb.UsbAccessoryClient

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    val discovery = BluetoothDiscovery(application)
    val gatt = GattInspector(application)
    val session = SessionCoordinator(ICCOAProtocolAdapter())
    private val mutableEvents = MutableStateFlow(listOf("Cliente de investigación iniciado. Compatibilidad sin verificar."))
    val events = mutableEvents.asStateFlow()
    val usb = UsbAccessoryClient(application, ::event)
    fun event(message: String) { mutableEvents.value = (mutableEvents.value + "${java.time.Instant.now()} $message").takeLast(100) }
    fun scan(iccoaOnly: Boolean = true) {
        gatt.cancel()
        discovery.stop()
        session.startDiscovery(); event("Modo: ${if (iccoaOnly) "ICCOA FCFB" else "general Classic/BLE"}"); event("Descubrimiento Bluetooth solicitado por el usuario")
        discovery.start(iccoaOnly) { error ->
            if (error != null) { session.transportError(); event(error) }
            else { session.discoveryFinished(); event("Candidatos UUID: ${discovery.devices.value.count { it.recognizedServices.isNotEmpty() }}"); event("Descubrimiento finalizado: ${discovery.devices.value.size} observaciones") }
        }
    }
    fun stop() { discovery.stop(); gatt.cancel(); usb.stop(); session.disconnect(); event("Búsqueda/enlace detenido") }
    fun inspect(peerId: String) {
        val peer = discovery.observedBleDevice(peerId)
        if (peer == null) { event("Dispositivo no disponible; realiza una nueva búsqueda"); return }
        discovery.stop()
        event("Conexión GATT solicitada al ID temporal $peerId")
        gatt.inspect(peerId, peer) { result ->
            if (result.stage == GattStage.INSPECTED) {
                event("GATT comprobado y cerrado: servicios=${result.serviceCount}; UnionShare=${result.unionSharePresent}; sin sesión ICCOA")
            } else event("Inspección GATT: ${result.stage}; ${result.error ?: "sin detalle"}")
        }
    }
    fun denied() { session.permissionDenied(); event("Permisos de descubrimiento denegados") }
    fun export(): String = buildString {
        appendLine("AionLink Open 0.5.0-research")
        appendLine("Android API ${Build.VERSION.SDK_INT}")
        appendLine("Estado: ${session.state.value}")
        appendLine("Permisos: ${discovery.hasPermissions()}")
        appendLine("Búsqueda activa: ${discovery.isScanning.value}")
        appendLine("ICCOA: sin sesión verificada")
        appendLine("Observaciones actuales: ${discovery.devices.value.size} (no equivale a dispositivos únicos)")
        appendLine("Candidatos UUID: ${discovery.devices.value.count { it.recognizedServices.isNotEmpty() }}")
        discovery.devices.value.forEach { peer ->
            appendLine("ID temporal=${peer.id}; transporte=${peer.transport}; RSSI=${peer.rssi}; conectable=${peer.connectable}; servicios anunciados=${peer.observedServiceCount}")
            appendLine("Indicios UUID estáticos=${peer.recognizedServices.sorted().joinToString().ifEmpty { "ninguno" }}; compatibilidad=DESCONOCIDA")
        }
        appendLine("No se incluyen nombres, MAC, PIN ni datos crudos de anuncios.")
        val inspection = gatt.state.value
        appendLine("Última inspección GATT: ${inspection.stage}; ID temporal=${inspection.peerId}; servicios=${inspection.serviceCount}; UnionShare=${inspection.unionSharePresent}; código=${inspection.statusCode}")
        inspection.knownCharacteristics.forEach {
            appendLine("Característica conocida=${it.uuid}; propiedades=${it.properties}; sin lectura de valor")
        }
        appendLine("Resultado GATT no equivale a sesión ICCOA; enlace de inspección se cierra al terminar.")
        val cable = usb.state.value
        appendLine("USB: etapa=${cable.stage}; accesorios=${cable.accessories.size}; dispositivos en modo host=${cable.hostDevices}; alimentación reportada USB=${cable.usbPower}")
        cable.accessories.forEach { appendLine("Accesorio temporal=${it.id}; identificador ICCOA observado=${it.observedIccoa}; permiso=${it.permission}") }
        appendLine("USB recibidos=${cable.bytesRead}; frames=${cable.frames}; solicitudes experimentales enviadas=${cable.requestsSent}; puertos conocidos=${cable.knownPorts.sorted()}")
        appendLine("USB detalle=${cable.message ?: "sin incidencias registradas"}")
        appendLine("No se incluye serial USB ni payload. USB abierto no implica sesión ICCOA.")
        append(events.value.joinToString("\n"))
    }
    init { viewModelScope.launch { discovery.devices.collect { devices ->
        if (devices.isNotEmpty() && session.state.value in setOf(ConnectionState.DISCOVERING, ConnectionState.DEVICE_FOUND)) session.deviceFound()
    } } }
    override fun onCleared() { discovery.stop(); gatt.cancel(); usb.close() }
}

class MainActivity : ComponentActivity() {
    private lateinit var diagnostics: DiagnosticsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = ViewModelProvider(this)[DiagnosticsViewModel::class.java]
        setContent { MaterialTheme { AionLinkScreen(diagnostics) } }
    }
    override fun onResume() { super.onResume(); if (::diagnostics.isInitialized) diagnostics.usb.refresh() }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); diagnostics.usb.refresh() }
    override fun onStop() { if (::diagnostics.isInitialized) diagnostics.usb.stop(); super.onStop() }
}

@Composable fun AionLinkScreen(model: DiagnosticsViewModel = viewModel()) {
    val state by model.session.state.collectAsStateWithLifecycle()
    val devices by model.discovery.devices.collectAsStateWithLifecycle()
    val events by model.events.collectAsStateWithLifecycle()
    val scanning by model.discovery.isScanning.collectAsStateWithLifecycle()
    val inspection by model.gatt.state.collectAsStateWithLifecycle()
    val cable by model.usb.state.collectAsStateWithLifecycle()
    val gattBusy = inspection.stage in setOf(GattStage.CONNECTING, GattStage.LINK_CONNECTED, GattStage.DISCOVERING_SERVICES)
    var showOthers by remember { mutableStateOf(false) }
    val candidates = devices.filter { it.recognizedServices.isNotEmpty() }
    var pendingPeer by remember { mutableStateOf<String?>(null) }
    var pendingUsb by remember { mutableStateOf<Pair<String,Boolean>?>(null) }
    var requestedIccoaOnly by remember { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (model.discovery.hasPermissions()) model.scan(requestedIccoaOnly) else model.denied()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        uri?.let { try {
            val stream = context.contentResolver.openOutputStream(it) ?: error("No output stream")
            stream.bufferedWriter().use { writer -> writer.write(model.export()) }
            model.event("Diagnóstico exportado sin nombres ni direcciones Bluetooth")
        } catch (_: Exception) { model.event("No se pudo exportar el diagnóstico") } }
    }
    DisposableEffect(model) { onDispose { model.stop() } }
    pendingPeer?.let { peerId ->
        AlertDialog(
            onDismissRequest = { pendingPeer = null },
            title = { Text("Comprobar el enlace con tu vehículo") },
            text = { Text("Selecciona únicamente tu vehículo, estacionado. Se abrirá una conexión BLE para descubrir servicios y se cerrará al terminar. No se envía PIN ni autenticación y todavía no hay proyección.") },
            confirmButton = { TextButton(onClick = { pendingPeer = null; model.inspect(peerId) }) { Text("Conectar a mi vehículo") } },
            dismissButton = { TextButton(onClick = { pendingPeer = null }) { Text("Cancelar") } }
        )
    }
    pendingUsb?.let { (id,bootstrap) ->
        AlertDialog(onDismissRequest = { pendingUsb = null },
            title = { Text(if (bootstrap) "Inicio USB experimental" else "Abrir accesorio USB") },
            text = { Text(if (bootstrap)
                "Confirma que el accesorio es tu vehículo estacionado. Se enviarán dos solicitudes de canal observadas en Samsung, para RTSP y UIBC, y se leerán respuestas durante 15 segundos. Aún no hay autenticación ni proyección. No ha sido validado con el AION."
                else "Confirma que es tu vehículo estacionado. Android pedirá autorización si hace falta. Se abrirá el canal AOA y se observarán datos durante 15 segundos, sin enviar mensajes. No se guardará el contenido.") },
            confirmButton = { TextButton(onClick = { pendingUsb = null; model.usb.request(id,bootstrap) }) { Text("Continuar con mi vehículo") } },
            dismissButton = { TextButton(onClick = { pendingUsb = null }) { Text("Cancelar") } })
    }
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AionLink Open", style = MaterialTheme.typography.headlineMedium)
            Text("Cliente USB + Bluetooth experimental • v0.5.0")
            Text("Estado: $state", style = MaterialTheme.typography.titleMedium)
            Text("ICCOA: compatibilidad sin verificar. No hay una sesión negociada ni proyección disponible.")
            Text("Buscar receptor ICCOA", style = MaterialTheme.typography.titleLarge)
            Button(onClick = { requestedIccoaOnly = true; if (model.discovery.hasPermissions()) model.scan() else permission.launch(model.discovery.requiredPermissions()) }, enabled = !scanning && !gattBusy, modifier = Modifier.fillMaxWidth()) { Text("Buscar ICCOA CarLink") }
            if (scanning) { LinearProgressIndicator(Modifier.fillMaxWidth()); Text("Buscando durante 15 segundos...") }
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Candidatos ICCOA: ${candidates.size}", style = MaterialTheme.typography.titleLarge)
                    if (candidates.isEmpty()) Text(if (scanning) "Esperando anuncios del receptor..." else "Sin indicios ICCOA. Abre ICCOA en el coche estacionado y busca de nuevo. Si no aparece, prueba la b\u00fasqueda general.")
                    candidates.forEach { device ->
                        Text(device.name, style = MaterialTheme.typography.titleMedium)
                        Text("${device.transport} | RSSI ${device.rssi ?: "?"} | ID ${device.id}")
                        Text("UUID compatible observado; identidad del veh\u00edculo sin confirmar.")
                        Button(onClick = { pendingPeer = device.id }, enabled = !gattBusy && device.connectable != false, modifier = Modifier.fillMaxWidth()) { Text("Comprobar este receptor") }
                        if (device.connectable == false) Text("El anuncio actual no permite conexi\u00f3n BLE.")
                        HorizontalDivider()
                    }
                }
            }
            Text("Comprobaci\u00f3n BLE: ${inspection.stage}")
            OutlinedButton(onClick = model::stop) { Text("Detener b\u00fasqueda / enlace") }
            OutlinedButton(onClick = { requestedIccoaOnly = false; if (model.discovery.hasPermissions()) model.scan(false) else permission.launch(model.discovery.requiredPermissions()) }, enabled = !scanning && !gattBusy) { Text("B\u00fasqueda general Classic + BLE") }
            TextButton(onClick = { showOthers = !showOthers }) { Text("${if (showOthers) "Ocultar" else "Mostrar"} otras observaciones (${devices.size - candidates.size})") }
            if (showOthers) devices.filter { it.recognizedServices.isEmpty() }.forEach { device ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text("${device.transport} | ID ${device.id} | RSSI ${device.rssi ?: "?"}")
                    Text("Sin UUID ICCOA reconocido. El nombre no identifica el protocolo.")
                    if (device.transport == "BLE") OutlinedButton(onClick = { pendingPeer = device.id }, enabled = !gattBusy && device.connectable != false) { Text("Comprobar servicios BLE") }
                } }
            }
            OutlinedButton(onClick = { export.launch("aionlink-diagnostics.txt") }) { Text("Guardar diagn\u00f3stico") }
            Text("Conexión por cable", style = MaterialTheme.typography.titleLarge)
            Text("USB: ${cable.stage} • Accesorios AOA: ${cable.accessories.size}")
            Text("Alimentación reportada USB: ${cable.usbPower} • Dispositivos modo host: ${cable.hostDevices}")
            OutlinedButton(onClick = model.usb::refresh) { Text("Actualizar USB") }
            if (cable.accessories.isEmpty()) Text("Esperando accesorio AOA. Cargar el teléfono o tener ADB por cable no demuestra un enlace ICCOA; el receptor debe iniciar el modo accesorio.")
            cable.accessories.forEach { accessory ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(accessory.label)
                    Text("ID temporal ${accessory.id} • Permiso: ${accessory.permission}")
                    OutlinedButton(onClick = { pendingUsb = accessory.id to false }, enabled = !cable.busy) { Text("Abrir USB y observar") }
                    if (accessory.observedIccoa) OutlinedButton(onClick = { pendingUsb = accessory.id to true }, enabled = !cable.busy) { Text("Iniciar canales USB (experimental)") }
                } }
            }
            if (cable.busy) OutlinedButton(onClick = { model.usb.stop("Cancelado por el usuario") }) { Text("Cerrar USB") }
            Text("USB: ${cable.bytesRead} bytes recibidos; ${cable.frames} frames; ${cable.requestsSent} solicitudes enviadas")
            cable.message?.let { Text(it) }
            Text("Conexión ICCOA", style = MaterialTheme.typography.titleLarge)
            Text("Enlace GATT: ${inspection.stage}")
            if (inspection.stage == GattStage.INSPECTED) {
                Text("Enlace BLE comprobado y cerrado. Servicios: ${inspection.serviceCount}. UnionShare presente: ${inspection.unionSharePresent}.")
                inspection.knownCharacteristics.forEach { Text("${it.uuid} • propiedades ${it.properties}", style = MaterialTheme.typography.bodySmall) }
                Text("Negociación ICCOA pendiente. Esta inspección no habilita proyección.")
            }
            inspection.error?.let { Text(it) }
            Text("Objetivo: interfaz propia sobre ICCOA. Puente Android Auto y Huawei Car sin implementar; CarbitLink no identificado en este vehículo.")
            Text("Perfil de investigación: ${VehicleProfiles.aionI60.brand} ${VehicleProfiles.aionI60.model} — NOT YET VERIFIED")
            Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
            Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nPermisos concedidos: ${model.discovery.hasPermissions()}\nTransporte: descubrimiento Classic/BLE; sin canal de sesión")
            OutlinedButton(onClick = { export.launch("aionlink-diagnostics.txt") }) { Text("Exportar diagnóstico anonimizado") }
            events.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
