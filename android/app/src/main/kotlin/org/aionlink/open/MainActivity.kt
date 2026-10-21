package org.aionlink.open

import android.app.Application
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.aionlink.bluetooth.BluetoothDiscovery
import org.aionlink.core.*
import org.aionlink.iccoa.ICCOAProtocolAdapter
import org.aionlink.vehicles.VehicleProfiles

class DiagnosticsViewModel(application: Application) : AndroidViewModel(application) {
    val discovery = BluetoothDiscovery(application)
    val session = SessionCoordinator(ICCOAProtocolAdapter())
    private val mutableEvents = MutableStateFlow(listOf("Cliente de investigación iniciado. Compatibilidad sin verificar."))
    val events = mutableEvents.asStateFlow()
    fun event(message: String) { mutableEvents.value = (mutableEvents.value + message).takeLast(100) }
    fun scan() {
        discovery.stop()
        session.startDiscovery(); event("Descubrimiento Bluetooth solicitado por el usuario")
        discovery.start { error ->
            if (error != null) { session.transportError(); event(error) }
            else { session.discoveryFinished(); event("Descubrimiento finalizado: ${discovery.devices.value.size} observaciones") }
        }
    }
    fun stop() { discovery.stop(); session.disconnect(); event("Descubrimiento detenido") }
    fun denied() { session.permissionDenied(); event("Permisos de descubrimiento denegados") }
    fun export(): String = "AionLink Open 0.1.0-research\nAndroid API ${Build.VERSION.SDK_INT}\nEstado: ${session.state.value}\nPermisos: ${discovery.hasPermissions()}\nICCOA: sin sesión verificada\n" + events.value.joinToString("\n")
    init { viewModelScope.launch { discovery.devices.collect { devices ->
        if (devices.isNotEmpty() && session.state.value in setOf(ConnectionState.DISCOVERING, ConnectionState.DEVICE_FOUND)) session.deviceFound()
    } } }
    override fun onCleared() { discovery.stop() }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { AionLinkScreen() } }
    }
}

@Composable fun AionLinkScreen(model: DiagnosticsViewModel = viewModel()) {
    val state by model.session.state.collectAsStateWithLifecycle()
    val devices by model.discovery.devices.collectAsStateWithLifecycle()
    val events by model.events.collectAsStateWithLifecycle()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (model.discovery.hasPermissions()) model.scan() else model.denied()
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
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().systemBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("AionLink Open", style = MaterialTheme.typography.headlineMedium)
            Text("Investigación de conectividad • v0.1.0")
            Text("Estado: $state", style = MaterialTheme.typography.titleMedium)
            Text("ICCOA: compatibilidad sin verificar. No hay una sesión negociada ni proyección disponible.")
            Text("Permisos requeridos al buscar: dispositivos cercanos (Android 12+) o ubicación (Android 10–11).")
            Button(onClick = { if (model.discovery.hasPermissions()) model.scan() else permission.launch(model.discovery.requiredPermissions()) }, enabled = state != ConnectionState.DISCOVERING) { Text("Buscar dispositivos Bluetooth") }
            OutlinedButton(onClick = model::stop) { Text("Detener búsqueda") }
            Text("Dispositivos observados", style = MaterialTheme.typography.titleLarge)
            if (devices.isEmpty()) Text("Ningún dispositivo observado en esta búsqueda.")
            devices.forEach { device ->
                Card(Modifier.fillMaxWidth()) { Column(Modifier.padding(12.dp)) {
                    Text(device.name, style = MaterialTheme.typography.titleMedium)
                    Text("${device.transport} • ID temporal ${device.id}")
                    device.rssi?.let { Text("Señal: $it dBm") }
                    Text("Compatibilidad desconocida: el nombre anunciado no identifica el protocolo.")
                } }
            }
            Text("Conexión ICCOA", style = MaterialTheme.typography.titleLarge)
            Text("Pendiente: identificar descubrimiento, negociación y autenticación del receptor. No se envían mensajes de protocolo ni se solicita un PIN sin evidencia.")
            Text("Perfil de investigación: ${VehicleProfiles.aionI60.brand} ${VehicleProfiles.aionI60.model} — NOT YET VERIFIED")
            Text("Diagnóstico", style = MaterialTheme.typography.titleLarge)
            Text("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})\nPermisos concedidos: ${model.discovery.hasPermissions()}\nTransporte: descubrimiento Classic/BLE; sin canal de sesión")
            OutlinedButton(onClick = { export.launch("aionlink-diagnostics.txt") }) { Text("Exportar diagnóstico anonimizado") }
            events.forEach { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}
