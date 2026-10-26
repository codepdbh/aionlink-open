package org.aionlink.open

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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

enum class CodeReadiness { IDLE, PREPARING, READY, BLOCKED }

/** Visible even when preflight is blocked. Entering a code never means it was transmitted. */
@Composable
fun ConnectionCodeDialog(readiness: CodeReadiness, detail: String, onSubmit: (String) -> Unit, onClose: () -> Unit) {
    var code by remember { mutableStateOf("") }
    fun close() { code = ""; onClose() }
    AlertDialog(onDismissRequest = ::close,
        title = { Text("Clave ICCOA de mi vehículo estacionado") },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(detail)
            if (readiness == CodeReadiness.PREPARING) LinearProgressIndicator()
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
        confirmButton = { TextButton(enabled = readiness == CodeReadiness.READY && code.length == 6,
            modifier = Modifier.testTag("submit-code"), onClick = {
                val submitted = code; code = ""; onSubmit(submitted)
            }) { Text("Enviar a mi receptor") } },
        dismissButton = { TextButton(onClick = ::close, modifier = Modifier.testTag("close-code")) { Text("Cerrar") } })
}
