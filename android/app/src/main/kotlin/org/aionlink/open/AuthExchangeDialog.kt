package org.aionlink.open

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation

@Composable
fun AuthExchangeDialog(onSubmit: (String) -> Unit, onDismiss: () -> Unit) {
    var pin by remember { mutableStateOf("") }
    fun cancel() { pin = ""; onDismiss() }
    AlertDialog(
        onDismissRequest = ::cancel,
        title = { Text("Solicitud AUTH experimental") },
        text = { Column {
            Text("Con tu vehículo estacionado, introduce de nuevo el código actual. Se enviará una solicitud experimental y se comprobará la respuesta. Puede fallar o desconectar el receptor. Aún no hay proyección ni confirmación de sesión.")
            OutlinedTextField(value = pin, onValueChange = { pin = it.filter { c -> c in '0'..'9' }.take(6) },
                label = { Text("Código del vehículo") }, singleLine = true, modifier = Modifier.testTag("auth-exchange-pin"),
                visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword))
        } },
        confirmButton = { TextButton(enabled = pin.length == 6, modifier = Modifier.testTag("auth-exchange-send"), onClick = {
            val submitted = pin; pin = ""; onSubmit(submitted)
        }) { Text("Enviar solicitud experimental") } },
        dismissButton = { TextButton(onClick = ::cancel) { Text("Cancelar") } }
    )
}
