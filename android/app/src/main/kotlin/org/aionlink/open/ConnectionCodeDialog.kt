package org.aionlink.open

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

enum class CodeReadiness { IDLE, PREPARING, READY, ANONYMIZED_EXPERIMENT, BLOCKED }
const val ANONYMIZED_EXPERIMENT_DETAIL = "Android mantiene oculta la dirección local. Esta prueba es opcional y su compatibilidad con el AION no está verificada."

/** Visible even when preflight is blocked. Entering a code never means it was transmitted. */
@Composable
fun ConnectionCodeDialog(readiness: CodeReadiness, detail: String, onSubmit: (String, Boolean) -> Unit, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    val experimental = readiness == CodeReadiness.ANONYMIZED_EXPERIMENT
    fun close() { code = ""; consent = false; onClose() }
    AlertDialog(onDismissRequest = ::close,
        title = { Text("Conectar con mi ICCOA") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail)
            Text("Sólo tu vehículo propio y estacionado.", style = MaterialTheme.typography.bodySmall)
            if (readiness == CodeReadiness.PREPARING) LinearProgressIndicator()
            if (experimental) {
                Text("Se usará el dato anonimizado de Android. Puede ser rechazado; no revela tu dirección real.")
                Row {
                    Checkbox(checked = consent, onCheckedChange = { consent = it }, modifier = Modifier.testTag("anonymous-consent"))
                    Text("Autorizo esta prueba con mi vehículo")
                }
            }
            OutlinedTextField(value = code,
                onValueChange = { code = it.filter { c -> c in '0'..'9' }.take(6) },
                label = { Text("Clave de seis dígitos") }, singleLine = true,
                modifier = Modifier.testTag("connection-code"),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation())
            Text(if (code.isEmpty()) "La clave no se guarda ni se exporta."
                else "Clave introducida sólo en memoria; todavía no se ha enviado.")
            if (readiness == CodeReadiness.BLOCKED) Text("Envío bloqueado. Introducir la clave no resuelve el requisito pendiente. Cerrar la descarta.")
            Text("Autenticación ICCOA y proyección todavía pendientes de implementar.")
        } },
        confirmButton = { TextButton(enabled = (readiness == CodeReadiness.READY || experimental && consent) && code.length == 6,
            modifier = Modifier.testTag("submit-code"), onClick = {
                val submitted = code; val optedIn = experimental && consent
                code = ""; consent = false; onSubmit(submitted, optedIn)
            }) { Text(if (experimental) "Probar conexión experimental" else "Enviar a mi receptor") } },
        dismissButton = { TextButton(onClick = ::close, modifier = Modifier.testTag("close-code")) { Text("Cerrar") } })
}
