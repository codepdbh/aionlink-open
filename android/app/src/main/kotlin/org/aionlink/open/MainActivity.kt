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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
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
import org.aionlink.bluetooth.BleNegotiator
import org.aionlink.iccoa.ClientInfo
import org.aionlink.iccoa.AddressDisclosure
import org.aionlink.iccoa.NormalLinkReadiness
import org.aionlink.iccoa.normalLinkReadiness
import org.aionlink.wifi.LocalP2p
import org.aionlink.wifi.P2pConnector
import org.aionlink.wifi.NetworkStage
import org.aionlink.core.*
import org.aionlink.iccoa.ICCOAProtocolAdapter
import org.aionlink.iccoa.GattStage
import org.aionlink.vehicles.VehicleProfiles
import org.aionlink.usb.UsbAccessoryClient

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    val discovery = BluetoothDiscovery(application)
    val gatt = GattInspector(application)
    val bootstrap = BleNegotiator(application)
    val network = P2pConnector(application)
    val session = SessionCoordinator(ICCOAProtocolAdapter())
    private val mutableEvents = MutableStateFlow(listOf("Cliente de investigación iniciado. Compatibilidad sin verificar."))
    val events = mutableEvents.asStateFlow()
    val usb = UsbAccessoryClient(application, ::event)
    fun event(message: String) {
        val next = mutableEvents.value + "${java.time.Instant.now()} $message"
        droppedEvents += (next.size - 500).coerceAtLeast(0)
        mutableEvents.value = next.takeLast(500)
    }
    private val exportCapture = DiagnosticExport()
    private val mutablePinRequest = MutableStateFlow<String?>(null)
    val pinRequest = mutablePinRequest.asStateFlow()
    private val mutableCodeReadiness = MutableStateFlow(CodeReadiness.IDLE)
    val codeReadiness = mutableCodeReadiness.asStateFlow()
    private val runId = java.util.UUID.randomUUID().toString().take(8)
    private var droppedEvents = 0
    private val mutableConnectionNotice = MutableStateFlow("Comprueba los requisitos antes de introducir un c\u00f3digo")
    val connectionNotice = mutableConnectionNotice.asStateFlow()
    private class Prepared(val peerId: String, val local: LocalP2p)
    private var prepared: Prepared? = null
    private var attemptNumber = 0
    private val prepareHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private val backgroundExpiry = Runnable { stopAll("Tiempo máximo del enlace en segundo plano agotado (50 s)") }
    private val prepareExpiry = Runnable { cancelPrepared("La preparaci\u00f3n para introducir el c\u00f3digo caduc\u00f3") }
    private fun operationBusy() = bootstrap.state.value.busy || network.state.value.busy || usb.state.value.busy ||
        network.authProbe.value.busy ||
        gatt.state.value.stage in setOf(GattStage.CONNECTING, GattStage.LINK_CONNECTED, GattStage.DISCOVERING_SERVICES)
    fun captureExport(): Boolean {
        if (operationBusy()) { event("Exportaci\u00f3n aplazada: espera el resultado o pulsa Detener"); return false }
        event("Exportaci\u00f3n solicitada: snapshot previo al selector Android")
        exportCapture.capture { export() }
        return true
    }
    fun consumeExport() = exportCapture.consume()
    fun scan(iccoaOnly: Boolean = true) {
        cancelPrepared(); gatt.cancel(); bootstrap.cancel("Nueva b\u00fasqueda"); network.stop("Nueva b\u00fasqueda")
        discovery.stop()
        session.startDiscovery(); event("Modo: ${if (iccoaOnly) "ICCOA FCFB" else "general Classic/BLE"}"); event("Descubrimiento Bluetooth solicitado por el usuario")
        discovery.start(iccoaOnly) { error ->
            if (error != null) { session.transportError(); event(error) }
            else { session.discoveryFinished(); event("Candidatos UUID: ${discovery.devices.value.count { it.recognizedServices.isNotEmpty() }}"); event("Descubrimiento finalizado: ${discovery.devices.value.size} observaciones") }
        }
    }
    fun stop() { stopAll("Detenido por el usuario") }
    private fun stopAll(reason: String) {
        prepareHandler.removeCallbacks(backgroundExpiry)
        cancelPrepared(reason); discovery.stop(); gatt.cancel(); bootstrap.cancel(reason); network.stop(reason)
        usb.stop(); session.disconnect(); event(reason)
    }
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
    fun prepareConnection(peerId: String, readOnly: Boolean = false) {
        if (discovery.observedBleDevice(peerId) == null) { event("Receptor no disponible; busca de nuevo"); return }
        cancelPrepared(); discovery.stop(); gatt.cancel(); bootstrap.reset()
        attemptNumber++; event("Intento local #$attemptNumber; modo=${if (readOnly) "READ_ONLY_NO_PIN" else "NORMAL_CLIENT_INFO"}")
        event(if (readOnly) "Consulta de lectura solicitada: no usa PIN" else "Comprobaci\u00f3n local antes de solicitar PIN")
        mutableConnectionNotice.value = "Comprobando Wi-Fi Direct local..."
        if (!readOnly) {
            mutableCodeReadiness.value = CodeReadiness.PREPARING
            mutablePinRequest.value = peerId
        }
        network.prepare { local ->
            if (readOnly) {
                mutableConnectionNotice.value = "Consulta de diagn\u00f3stico sin PIN ni ClientInfo; no es autenticaci\u00f3n"
                val peer = discovery.observedBleDevice(peerId)
                if (peer == null) { network.stop(); return@prepare }
                bootstrap.readAvailableServerInfo(peer) { info ->
                    event("ServerInfo obtenido por READ; red=${info.mode}; puerto=${info.portStatus}; rango=${info.portRange}; sin sesión ICCOA")
                    network.connect(info)
                }
            } else if (local.platformAddress.disclosure == AddressDisclosure.UNAVAILABLE) {
                val message = "Android no devolvi\u00f3 un dato P2P local utilizable. No se fabricar\u00e1 uno ni se enviar\u00e1 la clave sin ese dato."
                mutableCodeReadiness.value = CodeReadiness.BLOCKED
                mutableConnectionNotice.value = message; event(message); network.stop("Requisito local no disponible")
            } else {
                prepared = Prepared(peerId, local)
                val anonymous = local.platformAddress.disclosure == AddressDisclosure.ANDROID_ANONYMIZED
                mutableCodeReadiness.value = if (anonymous) CodeReadiness.ANONYMIZED_EXPERIMENT else CodeReadiness.READY
                mutablePinRequest.value = peerId
                mutableConnectionNotice.value = if (anonymous)
                    ANONYMIZED_EXPERIMENT_DETAIL
                    else "Requisitos locales disponibles. Introduce el c\u00f3digo de tu receptor"
                event("Metadatos locales: ${local.platformAddress.disclosure}; valor omitido")
                prepareHandler.postDelayed(prepareExpiry, 60000)
            }
        }
    }
    fun cancelPrepared(reason: String = "Entrada de c\u00f3digo cancelada") {
        prepareHandler.removeCallbacks(prepareExpiry)
        val existed = prepared != null
        val hadDialog = mutablePinRequest.value != null
        val wasPreparing = mutableCodeReadiness.value == CodeReadiness.PREPARING
        prepared = null; mutablePinRequest.value = null; mutableCodeReadiness.value = CodeReadiness.IDLE
        if (existed || hadDialog) network.stop(reason)
        if (existed || wasPreparing) { mutableConnectionNotice.value = reason; event(reason) }
    }
    fun submitPin(pin: String, consentToAnonymized: Boolean) {
        val request = prepared ?: return
        val disclosure = request.local.platformAddress.disclosure
        val permitted = disclosure == AddressDisclosure.AVAILABLE && mutableCodeReadiness.value == CodeReadiness.READY ||
            disclosure == AddressDisclosure.ANDROID_ANONYMIZED && mutableCodeReadiness.value == CodeReadiness.ANONYMIZED_EXPERIMENT && consentToAnonymized
        if (!permitted || network.state.value.stage != NetworkStage.READY) {
            cancelPrepared("La preparaci\u00f3n de red ya no est\u00e1 disponible; c\u00f3digo descartado")
            return
        }
        prepareHandler.removeCallbacks(prepareExpiry)
        prepared = null; mutablePinRequest.value = null; mutableCodeReadiness.value = CodeReadiness.IDLE
        val peer = discovery.observedBleDevice(request.peerId)
        if (peer == null || disclosure == AddressDisclosure.UNAVAILABLE) { network.stop(); return }
        try {
            val preferences = getApplication<Application>().getSharedPreferences("own-client", 0)
            val id = preferences.getString("id", null) ?: java.util.UUID.randomUUID().toString().replace("-", "").take(6).also {
                preferences.edit().putString("id", it).apply()
            }
            val hello = ClientInfo(id, Build.MODEL, pin, request.local.platformAddress, request.local.supports5Ghz, consentToAnonymized)
            mutableConnectionNotice.value = "Intentando ClientInfo normal; espera confirmaci\u00f3n de escritura"
            event("Identidad local=$disclosure; consentimiento experimental=$consentToAnonymized; sin valor de direcci\u00f3n")
            event("PIN entregado al intercambio normal; valor omitido, env\u00edo todav\u00eda sin confirmar")
            bootstrap.start(peer, hello) { info ->
                mutableConnectionNotice.value = "Respuesta BLE recibida; red ${info.mode}; puerto=${info.portStatus}; autenticación pendiente"
                event("ServerInfo recibido tras ClientInfo; solicitando red ${info.mode}; puerto=${info.portStatus}; rango=${info.portRange}")
                network.connect(info)
            }
        } catch (_: IllegalArgumentException) {
            network.stop(); mutableConnectionNotice.value = "ClientInfo no v\u00e1lido; no se enviaron datos"
            event(mutableConnectionNotice.value)
        }
    }
    fun resumed() { prepareHandler.removeCallbacks(backgroundExpiry) }
    fun background() {
        if (preserveBackgroundHandoff(bootstrap.state.value.busy, network.systemWifiHandoff)) {
            discovery.stop(); gatt.cancel(); usb.stop()
            event("Enlace BLE o red en curso; se conserva hasta 50 s en segundo plano, sin sesión ICCOA")
            prepareHandler.removeCallbacks(backgroundExpiry)
            prepareHandler.postDelayed(backgroundExpiry, 50000)
        } else stopAll("Activity en segundo plano (puede incluir selector de archivos o cambio de app)")
    }
    fun denied() { session.permissionDenied(); event("Permisos de descubrimiento denegados") }
    fun export(): String = buildString {
        appendLine("AionLink Open 0.12.0-research")
        appendLine("Snapshot UTC: ${java.time.Instant.now()}; capturado antes del selector de archivos")
        appendLine("Android API ${Build.VERSION.SDK_INT}")
        appendLine("Estado: ${session.state.value}")
        appendLine("Permisos: ${discovery.hasPermissions()}")
        appendLine("Búsqueda activa: ${discovery.isScanning.value}")
        appendLine("ICCOA: sin sesión verificada")
        appendLine("Observaciones actuales: ${discovery.devices.value.size} (no equivale a dispositivos únicos)")
        appendLine("Proceso local: $runId; eventos=${mutableEvents.value.size}; eventos anteriores omitidos=$droppedEvents")
        appendLine("Formulario: ${mutableCodeReadiness.value}; solicitud visible=${mutablePinRequest.value != null}; valor de clave omitido")
        appendLine("Intento local: $attemptNumber")
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
        val negotiation = bootstrap.state.value
        appendLine("Intercambio BLE: ${negotiation.stage}; MTU=${negotiation.mtu}; enviados=${negotiation.bytesSent}; recibidos=${negotiation.bytesReceived}")
        appendLine("Intercambio modo=${negotiation.mode}; escritura confirmada=${negotiation.writeConfirmed}")
        appendLine("Intercambio detalle=${negotiation.message}")
        appendLine("Preparaci\u00f3n: ${mutableConnectionNotice.value}")
        appendLine("Red: ${network.state.value.stage}; modo=${network.state.value.mode}; ${network.state.value.message}")
        appendLine("Prueba TCP AUTH: ${network.authProbe.value.stage}; resultado=${network.authProbe.value.detail}; duración ms=${network.authProbe.value.elapsedMs}; sin payload")
        appendLine("PIN, MAC, SSID, PSK y contenido ServerInfo omitidos. Red conectada no implica autenticaci\u00f3n ICCOA.")
        val cable = usb.state.value
        appendLine("USB: etapa=${cable.stage}; accesorios=${cable.accessories.size}; dispositivos en modo host=${cable.hostDevices}; alimentación reportada USB=${cable.usbPower}")
        cable.accessories.forEach { appendLine("Accesorio temporal=${it.id}; identificador ICCOA observado=${it.observedIccoa}; permiso=${it.permission}") }
        appendLine("USB recibidos=${cable.bytesRead}; frames=${cable.frames}; solicitudes experimentales enviadas=${cable.requestsSent}; puertos conocidos=${cable.knownPorts.sorted()}")
        appendLine("USB detalle=${cable.message ?: "sin incidencias registradas"}")
        appendLine("No se incluye serial USB ni payload. USB abierto no implica sesión ICCOA.")
        append(events.value.joinToString("\n"))
    }
    init {
        viewModelScope.launch { bootstrap.state.collect {
            event("Intercambio BLE: ${it.stage}; ${it.message}")
            if (it.stage in setOf(org.aionlink.bluetooth.BootstrapStage.ERROR, org.aionlink.bluetooth.BootstrapStage.CANCELLED) && network.state.value.stage == NetworkStage.READY) network.stop()
        } }
        viewModelScope.launch { network.state.collect {
            event("Red: ${it.stage}; ${it.message}")
            if (it.stage == NetworkStage.ERROR && mutablePinRequest.value != null) {
                prepareHandler.removeCallbacks(prepareExpiry); prepared = null
                mutableCodeReadiness.value = CodeReadiness.BLOCKED
                mutableConnectionNotice.value = it.message
            }
        } }
        viewModelScope.launch { network.authProbe.collect {
            event("Prueba TCP AUTH: ${it.stage}; ${it.detail}; duración ms=${it.elapsedMs}; sin payload ni sesión ICCOA")
        } }
        viewModelScope.launch { discovery.devices.collect { devices ->
        if (devices.isNotEmpty() && session.state.value in setOf(ConnectionState.DISCOVERING, ConnectionState.DEVICE_FOUND)) session.deviceFound()
    } } }
    override fun onCleared() { stopAll("ViewModel finalizado"); usb.close() }
}

class MainActivity : ComponentActivity() {
    private lateinit var diagnostics: DiagnosticsViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        diagnostics = ViewModelProvider(this)[DiagnosticsViewModel::class.java]
        setContent { MaterialTheme { AionLinkScreen(diagnostics) } }
    }
    override fun onResume() { super.onResume(); if (::diagnostics.isInitialized) diagnostics.let { it.resumed(); it.usb.refresh() } }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); diagnostics.usb.refresh() }
    override fun onStop() { if (::diagnostics.isInitialized) diagnostics.background(); super.onStop() }
}

@Composable fun AionLinkScreen(model: DiagnosticsViewModel = viewModel()) {
    val state by model.session.state.collectAsStateWithLifecycle()
    val devices by model.discovery.devices.collectAsStateWithLifecycle()
    val events by model.events.collectAsStateWithLifecycle()
    val scanning by model.discovery.isScanning.collectAsStateWithLifecycle()
    val inspection by model.gatt.state.collectAsStateWithLifecycle()
    val cable by model.usb.state.collectAsStateWithLifecycle()
    val negotiation by model.bootstrap.state.collectAsStateWithLifecycle()
    val network by model.network.state.collectAsStateWithLifecycle()
    val authProbe by model.network.authProbe.collectAsStateWithLifecycle()
    val operationBusy = negotiation.busy || network.busy || authProbe.busy || cable.busy || inspection.stage in setOf(GattStage.CONNECTING, GattStage.LINK_CONNECTED, GattStage.DISCOVERING_SERVICES)
    val gattBusy = negotiation.busy || network.busy || network.stage == NetworkStage.CONNECTED || inspection.stage in setOf(GattStage.CONNECTING, GattStage.LINK_CONNECTED, GattStage.DISCOVERING_SERVICES)
    var showOthers by remember { mutableStateOf(false) }
    val candidates = devices.filter { it.recognizedServices.isNotEmpty() }
    var pendingPeer by remember { mutableStateOf<String?>(null) }
    val pendingConnect by model.pinRequest.collectAsStateWithLifecycle()
    val codeReadiness by model.codeReadiness.collectAsStateWithLifecycle()
    val connectionNotice by model.connectionNotice.collectAsStateWithLifecycle()
    val scroll = rememberScrollState()
    LaunchedEffect(connectionNotice) { scroll.animateScrollTo(0) }
    var pendingRead by remember { mutableStateOf<String?>(null) }
    var pendingAuthProbe by remember { mutableStateOf(false) }
    var authorizedConnect by remember { mutableStateOf<Pair<String, Boolean>?>(null) }
    val networkPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        val request = authorizedConnect; authorizedConnect = null
        if (request != null && model.network.hasPermissions()) model.prepareConnection(request.first, request.second)
        else model.event("Permisos Wi-Fi no concedidos; intercambio no iniciado")
    }
    var pendingUsb by remember { mutableStateOf<Pair<String,Boolean>?>(null) }
    var requestedIccoaOnly by remember { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (model.discovery.hasPermissions()) model.scan(requestedIccoaOnly) else model.denied()
    }
    val context = androidx.compose.ui.platform.LocalContext.current
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        val frozen = model.consumeExport()
        if (uri != null && frozen == null) {
            model.event("La captura se perdi\u00f3 al recrear el proceso; vuelve a exportar. Archivo no abierto")
        } else if (uri != null && frozen != null) try {
            val stream = context.contentResolver.openOutputStream(uri) ?: error("No output stream")
            stream.bufferedWriter().use { writer -> writer.write(frozen) }
            model.event("Diagn\u00f3stico previo al selector exportado sin nombres ni direcciones Bluetooth")
        } catch (_: Exception) { model.event("No se pudo exportar el diagn\u00f3stico") }

    }
    pendingPeer?.let { peerId ->
        AlertDialog(
            onDismissRequest = { pendingPeer = null },
            title = { Text("Comprobar el enlace con tu vehículo") },
            text = { Text("Selecciona únicamente tu vehículo, estacionado. Se abrirá una conexión BLE para descubrir servicios y se cerrará al terminar. No se envía PIN ni autenticación y todavía no hay proyección.") },
            confirmButton = { TextButton(onClick = { pendingPeer = null; model.inspect(peerId) }) { Text("Conectar a mi vehículo") } },
            dismissButton = { TextButton(onClick = { pendingPeer = null }) { Text("Cancelar") } }
        )
    }
    if (pendingAuthProbe) AuthProbeDialog(
        onConfirm = { pendingAuthProbe = false; model.network.probeAuthChannel() },
        onDismiss = { pendingAuthProbe = false }
    )
    pendingConnect?.let {
        ConnectionCodeDialog(codeReadiness, connectionNotice, onSubmit = model::submitPin,
            onClose = { model.cancelPrepared() })
    }
    pendingRead?.let { peerId ->
        AlertDialog(onDismissRequest = { pendingRead = null },
            title = { Text("Consulta de diagn\u00f3stico a mi veh\u00edculo") },
            text = { Text("Confirma que es tu veh\u00edculo estacionado. Se leer\u00e1 ServerInfo durante un m\u00e1ximo de 15 segundos, sin usar PIN ni enviar ClientInfo. La prueba anterior no obtuvo respuesta. Si devuelve par\u00e1metros v\u00e1lidos se intentar\u00e1 formar su red; esto no autentica ICCOA.") },
            confirmButton = { TextButton(onClick = {
                pendingRead = null
                if (model.network.hasPermissions()) model.prepareConnection(peerId, true)
                else { authorizedConnect = peerId to true; networkPermission.launch(model.network.requiredPermissions()) }
            }) { Text("Consultar mi receptor") } },
            dismissButton = { TextButton(onClick = { pendingRead = null }) { Text("Cancelar") } })
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
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(scroll).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AionLink Open", style = MaterialTheme.typography.headlineMedium)
            Text("Cliente USB + Bluetooth experimental • v0.12.0")
            Text("Estado: $state", style = MaterialTheme.typography.titleMedium)
            Text("ICCOA: compatibilidad sin verificar. No hay una sesión negociada ni proyección disponible.")
            Text(connectionNotice, style = MaterialTheme.typography.titleMedium)
            if (network.stage == NetworkStage.CONNECTED && network.mode == org.aionlink.iccoa.NetworkMode.P2P) {
                Button(onClick = { pendingAuthProbe = true }, enabled = !authProbe.busy) { Text("Comprobar canal AUTH") }
            }
            Text("Canal AUTH: ${authProbe.stage} • ${authProbe.detail}")
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
                        Button(onClick = {
                            if (model.network.hasPermissions()) model.prepareConnection(device.id)
                            else { authorizedConnect = device.id to false; networkPermission.launch(model.network.requiredPermissions()) }
                        }, enabled = !gattBusy && device.connectable != false, modifier = Modifier.fillMaxWidth()) { Text("Preparar conexi\u00f3n normal") }
                        OutlinedButton(onClick = { pendingPeer = device.id }, enabled = !gattBusy && device.connectable != false, modifier = Modifier.fillMaxWidth()) { Text("Solo comprobar servicios BLE") }
                        if (device.connectable == false) Text("El anuncio actual no permite conexi\u00f3n BLE.")
                        TextButton(onClick = { pendingRead = device.id }, enabled = !gattBusy && device.connectable != false) { Text("Lectura experimental sin PIN") }
                        HorizontalDivider()
                    }
                }
            }
            Text("Comprobaci\u00f3n BLE: ${inspection.stage}")
            Text("Intercambio: ${negotiation.stage} | Red: ${network.stage}", style = MaterialTheme.typography.titleMedium)
            Text(negotiation.message)
            Text(network.message)
            if (negotiation.busy || network.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (operationBusy) Text("Espera el resultado para guardar el diagn\u00f3stico, o pulsa Detener para cancelar el intento.")
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
            OutlinedButton(enabled = !operationBusy, onClick = { if (model.captureExport()) export.launch("aionlink-diagnostics.txt") }) { Text("Guardar diagn\u00f3stico") }
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
            OutlinedButton(enabled = !operationBusy, onClick = { if (model.captureExport()) export.launch("aionlink-diagnostics.txt") }) { Text("Exportar diagnóstico anonimizado") }
            events.takeLast(20).forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
